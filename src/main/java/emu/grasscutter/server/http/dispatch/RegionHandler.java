package emu.grasscutter.server.http.dispatch;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.protobuf.ByteString;
import emu.grasscutter.GameConstants;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.net.proto.QueryCurrRegionHttpRspOuterClass.QueryCurrRegionHttpRsp;
import emu.grasscutter.net.proto.QueryRegionListHttpRspOuterClass.QueryRegionListHttpRsp;
import emu.grasscutter.net.proto.RegionInfoOuterClass.RegionInfo;
import emu.grasscutter.net.proto.RegionSimpleInfoOuterClass.RegionSimpleInfo;
import emu.grasscutter.scripts.SceneScriptManager;
import emu.grasscutter.server.event.dispatch.QueryAllRegionsEvent;
import emu.grasscutter.server.event.dispatch.QueryCurrentRegionEvent;
import emu.grasscutter.server.http.Router;
import emu.grasscutter.server.http.objects.QueryCurRegionRspJson;
import emu.grasscutter.utils.Crypto;
import emu.grasscutter.utils.JsonUtils;
import emu.grasscutter.utils.Utils;
import io.javalin.Javalin;
import io.javalin.http.Context;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static emu.grasscutter.config.Configuration.*;
import static emu.grasscutter.utils.lang.Language.translate;

/** Handles requests related to region queries. */
public final class RegionHandler implements Router {
    private static final Map<String, RegionData> regions = new ConcurrentHashMap<>();
    private static String regionListResponse;
    private static String regionListResponseCN;

    public RegionHandler() {
        try { // Read and initialize region data.
            this.initialize();
        } catch (Exception exception) {
            Grasscutter.getLogger().error("Failed to initialize region data.", exception);
        }
    }

    /** Configures region data according to configuration. */
    private void initialize() {
        var dispatchDomain =
                "http"
                        + (HTTP_ENCRYPTION.useInRouting ? "s" : "")
                        + "://"
                        + lr(HTTP_INFO.accessAddress, HTTP_INFO.bindAddress)
                        + ":"
                        + lr(HTTP_INFO.accessPort, HTTP_INFO.bindPort);

        // Create regions.
        var servers = new ArrayList<RegionSimpleInfo>();
        var usedNames = new ArrayList<String>(); // List to check for potential naming conflicts.

        var configuredRegions = new ArrayList<>(DISPATCH_INFO.regions);
        if (Grasscutter.getRunMode() != ServerRunMode.HYBRID && configuredRegions.isEmpty()) {
            Grasscutter.getLogger()
                    .error(
                            "[Dispatch] There are no game servers available. Exiting due to unplayable state.");
            System.exit(1);
        } else if (configuredRegions.isEmpty())
            configuredRegions.add(
                    new Region(
                            "os_usa",
                            DISPATCH_INFO.defaultName,
                            lr(GAME_INFO.accessAddress, GAME_INFO.bindAddress),
                            lr(GAME_INFO.accessPort, GAME_INFO.bindPort)));

        configuredRegions.forEach(
                region -> {
                    if (usedNames.contains(region.Name)) {
                        Grasscutter.getLogger().error("Region name already in use.");
                        return;
                    }

                    // Create a region identifier.
                    var identifier =
                            RegionSimpleInfo.newBuilder()
                                    .setName(region.Name)
                                    .setTitle(region.Title)
                                    .setType("DEV_PUBLIC")
                                    .setDispatchUrl(dispatchDomain + "/query_cur_region/" + region.Name)
                                    .build();
                    usedNames.add(region.Name);
                    servers.add(identifier);

                    // Create a region info object.
                    var regionInfo =
                            RegionInfo.newBuilder()
                                    .setGateserverIp(region.Ip)
                                    .setGateserverPort(region.Port)
                                    .build();
                    // Create an updated region query.
                    var updatedQuery =
                            QueryCurrRegionHttpRsp.newBuilder()
                                    .setRegionInfo(regionInfo)
                                    .setClientSecretKey(ByteString.copyFrom(Crypto.DISPATCH_SEED))
                                    .build();
                    regions.put(
                            region.Name,
                            new RegionData(
                                    updatedQuery, Utils.base64Encode(updatedQuery.toByteString().toByteArray())));
                });

        // Determine config settings.
        var hiddenIcons = new JsonArray();
        hiddenIcons.add(40);
        var codeSwitch = new JsonArray();
        codeSwitch.add(4334); // 4.6 及以上版本
//        codeSwitch.add(3628);   // 4.0 及以下版本

        // Create a config object.
        var customConfig = new JsonObject();
        customConfig.addProperty("sdkenv", "2");
        customConfig.addProperty("checkdevice", "false");
        customConfig.addProperty("loadPatch", "false");
        customConfig.addProperty("showexception", String.valueOf(GameConstants.DEBUG));
        customConfig.addProperty("regionConfig", "pm|fk|add");
        customConfig.addProperty("downloadMode", "0");
        customConfig.add("codeSwitch", codeSwitch);
        customConfig.add("coverSwitch", hiddenIcons);

        // XOR the config with the key.
        var encodedConfig = JsonUtils.encode(customConfig).getBytes();
        Crypto.xor(encodedConfig, Crypto.DISPATCH_KEY);

        // Create an updated region list.
        var updatedRegionList =
                QueryRegionListHttpRsp.newBuilder()
                        .addAllRegionList(servers)
                        .setClientSecretKey(ByteString.copyFrom(Crypto.DISPATCH_SEED))
                        .setClientCustomConfigEncrypted(ByteString.copyFrom(encodedConfig))
                        .setEnableLoginPc(true)
                        .build();

        // Set the region list response.
        regionListResponse = Utils.base64Encode(updatedRegionList.toByteString().toByteArray());

        // CN
        // Modify the existing config option.
        customConfig.addProperty("sdkenv", "0");
        // XOR the config with the key.
        encodedConfig = JsonUtils.encode(customConfig).getBytes();
        Crypto.xor(encodedConfig, Crypto.DISPATCH_KEY);

        // Create an updated region list.
        var updatedRegionListCN =
                QueryRegionListHttpRsp.newBuilder()
                        .addAllRegionList(servers)
                        .setClientSecretKey(ByteString.copyFrom(Crypto.DISPATCH_SEED))
                        .setClientCustomConfigEncrypted(ByteString.copyFrom(encodedConfig))
                        .setEnableLoginPc(true)
                        .build();

        // Set the region list response.
        regionListResponseCN = Utils.base64Encode(updatedRegionListCN.toByteString().toByteArray());
    }

    @Override
    public void applyRoutes(Javalin javalin) {
        // javalin.get("/query_region_list", RegionHandler::queryRegionList);
        javalin.get("/*n_l*/", RegionHandler::queryRegionList); // 为安卓端专门更改的路由 由于字数限制只能这么改了
        javalin.get("/query_cur_region/{region}", RegionHandler::queryCurrentRegion);
    }

    /**
     * Handle query region list request.
     *
     * @param ctx The context object for handling the request.
     * &#064;route  /query_region_list
     */
    private static void queryRegionList(Context ctx) {
        if (ctx.queryParamMap().containsKey("version") && ctx.queryParamMap().containsKey("platform")) {
            String versionName = ctx.queryParam("version");
            if (versionName == null) {
                Grasscutter.getLogger().error("客户端访问 query_region_list 时发生错误, versionName 为空; fullUrl -> {}",
                    ctx.fullUrl());
                return;
            }

            String versionCode;
            try {
                versionCode = versionName.substring(0, 8);
            } catch (Exception e) {
                Grasscutter.getLogger().error("客户端访问 query_region_list 时发生错误, versionCode 异常; fullUrl -> {}",
                    ctx.fullUrl());
                Grasscutter.getLogger().debug(String.valueOf(e));
                return;
            }

            // Determine the region list to use based on the version and platform.
            if ("CNRELiOS".equals(versionCode)
                    || "CNRELWin".equals(versionCode)
                    || "CNRELAnd".equals(versionCode)) {
                // Use the CN region list.
                QueryAllRegionsEvent event = new QueryAllRegionsEvent(regionListResponseCN);
                event.call();

                // Respond with the event result.
                ctx.result(event.getRegionList());
            } else if ("OSRELiOS".equals(versionCode)
                    || "OSRELWin".equals(versionCode)
                    || "OSRELAnd".equals(versionCode)) {
                // Use the OS region list.
                QueryAllRegionsEvent event = new QueryAllRegionsEvent(regionListResponse);
                event.call();

                // Respond with the event result.
                ctx.result(event.getRegionList());
            } else {
                /*
                 * String regionListResponse = "CP///////////wE=";
                 * QueryAllRegionsEvent event = new QueryAllRegionsEvent(regionListResponse);
                 * event.call();
                 * ctx.result(event.getRegionList());
                 * return;
                 */
                // Use the default region list.
                QueryAllRegionsEvent event = new QueryAllRegionsEvent(regionListResponse);
                event.call();

                // Respond with the event result.
                ctx.result(event.getRegionList());
            }
        } else {
            // Use the default region list.
            QueryAllRegionsEvent event = new QueryAllRegionsEvent(regionListResponse);
            event.call();

            // Respond with the event result.
            ctx.result(event.getRegionList());
        }
        // Log the request to the console.
        Grasscutter.getLogger().info("[Dispatch] Client {} request: query_region_list", Utils.address(ctx));
    }

    /**
     * &#064;route  /query_cur_region/{region}
     */
    private static void queryCurrentRegion(Context ctx) throws Exception {
        // Get region to query.
        String regionName = ctx.pathParam("region");
        String versionName = ctx.queryParam("version");
        var region = regions.get(regionName);

        // 安迪补丁专用 不敢动
        if (!Grasscutter.getConfig().server.game.useXorEncryption) {
            if (versionName == null) {
                Grasscutter.getLogger().error("客户端版本号获取异常");
                ctx.result("{\"content\":\"Gw9hSPdjoJe34l9OC58Q+/qfzt5R8DkJYkg1plgG0ZAGUkrZbJhnPD9htFxBTR0tXSQJA3chVLsIgr55GY+J27k8P3HKkj6sQsZ2isRTqqQnLZKruHCLZmrWmbhmx3ioh3mbo7OHJd1V+s7W/HSHe7mknUC9xKKqYHBnpYKBE5m5afUc3mlUqxFbwfZpOIItlnBWqxiXJqI3M7ux1Go4Fs0ZSvstvxw4lGTYUyLtBG1b/tMpahUAazG0G+WJbU22JOY7JRGlXXU+LaJPTqAHksFF7Hj1tOdQnw0clNbe62nyjObdixJxkRhKsxBory1OJQqZVA8z5ZeZn7hXzzkeRQ==\",\"sign\":\"AW6F2n8+EobiwQ3ZHeW3xHR9krFDpseGewxTA9yfreWLCmoXG5bXRDp60oO464VSeEFT885yOqwidLeE2umA4TGXcRu0aJBGT/7Olse6l5M4LyltuL/xIlPMUC/pq0dMmG3fY13NfgFFfd9rQ3vuY3QuH8J+XsKBkicFrpyqd9QvtxiQe3GHqIW7AobARmIYzdhuBG5fFtxe/scKUxTh1UUtbT8BTpUO7VcaXFBxXqva5ghUiKa1a7QoEMVL232D8t6nz74KOeZKZCcn8bGbO4PzkbcfkDmGL0fuEbhgh5e2+w+NQpAsX5TdO7NCYR8dKP+alTHnaeCdqXPkcauAAg==\"}");
            } else {
                if (versionName.startsWith("CN")) {
                    ctx.result("{\"content\":\"v4ODcIe6c8I+UGyDOIzudWuTk+DcH4znJAdvK7oksJg77KetHm9hbFXmUc5mDUy72ovfOCNoW+Ln8GRwitL/6fliDFSP/6P92wv+5b+/u2Yk6ShuGMi7XBDMZkVbun1bTqck+2hOb/zEKMfYmlbCpnTvIfFIU23PxNDEgVvGKFfdiFnQP24SK2/iN6tpzaKVnT/c26GJD6mZv76ipcUtS9+agWv5ntaiYYd1BW7VmWp6Me1ujS9wViTs/1WsPDOezhKtn2fisweFCscko5WW4rkuUXaJ+qeCVUnV/+tgkLPA7J7NwwleE2IbkmLpXnOmHAsxUzwnSFt9QKTAitJ49A==\",\"sign\":\"ZF7e0DvBxozhNCQ4zNvfO3dOgN5X3vn4/FuPSEWYY9bfmuJQL39alQPZgX+l9WI62haIswUBk1NjzwMIOo074mxv8pAbJsEGzwHdF7cwS5Mv3yRvdNsX20mClkrpNd9VWjbyXquX8LxZ52lDigAsHkEBoITcWHVt28uFWRSuhq9GznGS7A7Fo2XLbNi8qqNvtIRDCRk+p7/qFDyABXayr4rVDot8NtOrspmt5P77OCm870IxXUhMwfpiBd3mbS/cC8FbnztU8LxdyhHAmO2aaJcLobBuwtmnki3PyYOddOwBPu8Vi7wQaeEVq1uDUWTOXE30tVXZkFnAEV+YGFzStQ==\"}");
                } else { // OS
                    ctx.result("{\"content\":\"Gw9hSPdjoJe34l9OC58Q+/qfzt5R8DkJYkg1plgG0ZAGUkrZbJhnPD9htFxBTR0tXSQJA3chVLsIgr55GY+J27k8P3HKkj6sQsZ2isRTqqQnLZKruHCLZmrWmbhmx3ioh3mbo7OHJd1V+s7W/HSHe7mknUC9xKKqYHBnpYKBE5m5afUc3mlUqxFbwfZpOIItlnBWqxiXJqI3M7ux1Go4Fs0ZSvstvxw4lGTYUyLtBG1b/tMpahUAazG0G+WJbU22JOY7JRGlXXU+LaJPTqAHksFF7Hj1tOdQnw0clNbe62nyjObdixJxkRhKsxBory1OJQqZVA8z5ZeZn7hXzzkeRQ==\",\"sign\":\"AW6F2n8+EobiwQ3ZHeW3xHR9krFDpseGewxTA9yfreWLCmoXG5bXRDp60oO464VSeEFT885yOqwidLeE2umA4TGXcRu0aJBGT/7Olse6l5M4LyltuL/xIlPMUC/pq0dMmG3fY13NfgFFfd9rQ3vuY3QuH8J+XsKBkicFrpyqd9QvtxiQe3GHqIW7AobARmIYzdhuBG5fFtxe/scKUxTh1UUtbT8BTpUO7VcaXFBxXqva5ghUiKa1a7QoEMVL232D8t6nz74KOeZKZCcn8bGbO4PzkbcfkDmGL0fuEbhgh5e2+w+NQpAsX5TdO7NCYR8dKP+alTHnaeCdqXPkcauAAg==\"}");
                }
            }
            // Log to console.
            Grasscutter.getLogger()
                .info("Client {} request: query_cur_region/{}", Utils.address(ctx), regionName);
            return;
        }

        // Get region data.
        String regionData = "CAESGE5vdCBGb3VuZCB2ZXJzaW9uIGNvbmZpZw==";
        if (!ctx.queryParamMap().isEmpty()) {
            if (region != null) regionData = region.getBase64();
        }

        // Invoke event.
        QueryCurrentRegionEvent event = new QueryCurrentRegionEvent(regionData);
        event.call();

        String key_id = ctx.queryParam("key_id");

        String clientVersion;

        if (versionName == null || ctx.queryParam("dispatchSeed") == null || key_id == null) {
            // 这里参数为空是正常的
            Grasscutter.getLogger()
                .debug("客户端访问 query_cur_region/{} 时版本号为空; fullUrl -> {}", regionName, ctx.fullUrl());

            var rsp_sign = new QueryCurRegionRspJson();

            rsp_sign.content = event.getRegionInfo();
            rsp_sign.sign = "TW9yZSBsb3ZlIGZvciBVQSBQYXRjaCBwbGF5ZXJz";

            ctx.json(rsp_sign);
            return;
        } else {
            clientVersion = versionName.replaceAll(Pattern.compile("[a-zA-Z]").pattern(), "");
        }

        String[] versionCode = clientVersion.split("\\.");

        // 大版本号 -> 中版本号 -> 小版本号
        int versionMajor = Integer.parseInt(versionCode[0]);
        int versionMinor = Integer.parseInt(versionCode[1]);
        int _versionFix   = Integer.parseInt(versionCode[2]);

        // The 'fix' or 'patch' version is not checked because it is only used
        // when miHoYo is desperate and fucks up big time.
        if (versionMajor != GameConstants.VERSION_PARTS[0] || versionMinor != GameConstants.VERSION_PARTS[1]) {
            // Reject clients when there is a version mismatch
            String msg = "客户端与服务端版本不一致";
            String url = "https://pd.qq.com/s/5ziog9k4w";

            boolean updateClient = GameConstants.VERSION.compareTo(clientVersion) > 0;
            String contentMsg = updateClient
                ? "\n客户端版本落后 需要客户端升级 点击确定下载对应安装包! \n\n服务端版本: %s\n客户端版本: %s"
                    .formatted(GameConstants.VERSION, clientVersion)
                : "\n服务端版本落后 需要客户端降级 点击确定下载对应安装包! \n\n服务端版本: %s\n客户端版本: %s"
                    .formatted(GameConstants.VERSION, clientVersion);

            QueryCurrRegionHttpRsp rsp = QueryCurrRegionHttpError.retErrorRsp(msg, contentMsg, url);

            Grasscutter.getLogger()
                .debug("Connection denied for {} due to {}.",
                    Utils.address(ctx), updateClient ? "outdated client!" : "outdated server!");

            ctx.json(Crypto.encryptAndSignRegionData(rsp.toByteArray(), key_id));
            return;
        }

        byte[] regionInfo;
        if (SERVER.enableHotUpdate)
            regionInfo = QueryCurrRegionHttpRsp.newBuilder()
                .setRegionInfo(HotUpdate.getHotUpdate(versionName)) // 热更新
                .setClientSecretKey(ByteString.copyFrom(Crypto.DISPATCH_SEED))
                .build()
                .toByteArray();
         else
            regionInfo = Utils.base64Decode(event.getRegionInfo());

        ctx.json(Crypto.encryptAndSignRegionData(regionInfo, key_id));

        // Log to console.
        Grasscutter.getLogger()
            .info("Client {} request: query_cur_region/{}", Utils.address(ctx), regionName);
    }

    /** Region data container. */
    @Getter
    public static class RegionData {
        private final QueryCurrRegionHttpRsp regionQuery;
        private final String base64;

        public RegionData(QueryCurrRegionHttpRsp prq, String b64) {
            this.regionQuery = prq;
            this.base64 = b64;
        }

    }

    /**
     * Gets the current region query.
     *
     * @return A {@link QueryCurrRegionHttpRsp} object.
     */
    public static QueryCurrRegionHttpRsp getCurrentRegion() {
        return Grasscutter.getRunMode() == ServerRunMode.HYBRID
            ? regions.get("os_usa").getRegionQuery()
            : null;
    }
}
