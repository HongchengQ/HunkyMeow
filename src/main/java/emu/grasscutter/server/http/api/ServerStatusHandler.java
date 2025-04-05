package emu.grasscutter.server.http.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import emu.grasscutter.GameConstants;
import emu.grasscutter.game.player.GlobalOnlinePlayer;
import emu.grasscutter.utils.MonitorInfoModel;
import emu.grasscutter.utils.MonitorServer;
import io.javalin.http.Context;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static emu.grasscutter.config.Configuration.ACCOUNT;
import static emu.grasscutter.server.http.api.ApiHandler.ERROR_RET_CODE;
import static emu.grasscutter.server.http.api.ApiHandler.SUCCESS_RET_CODE;

public class ServerStatusHandler  {
    // 所有服务器的在线玩家
    private static List<Integer> AllOnlinePlayers = GlobalOnlinePlayer.getAllOnlinePlayers();

    public static void serverStatusV2(Context ctx) {
        ctx.contentType("application/json; charset=UTF-8");
        // 用于转为 json 对象
        final ObjectMapper objectMapper = new ObjectMapper();
        // 用于合并块
        final Map<String, Object> response = new LinkedHashMap<>();
        // 每一个小块
        final Map<String, Object> gameStatus = new LinkedHashMap<>();
        final Map<String, Object> jvmStatus = new LinkedHashMap<>();
        final Map<String, Object> systemStatus = new LinkedHashMap<>();

        MonitorInfoModel infoModel = new MonitorServer().monitor();     // 硬件状态
        AllOnlinePlayers = GlobalOnlinePlayer.getAllOnlinePlayers();

        // todo jvm和系统状态应该连接整个分组 否则只能看到dispatch服务器的状态
        try {
            // 游戏状态
            gameStatus.put("玩家数量", AllOnlinePlayers.size());
            gameStatus.put("玩家上限", ACCOUNT.maxPlayer);
            gameStatus.put("支持游戏版本", GameConstants.VERSION);
            // jvm 状态
            jvmStatus.put("JVM进程CPU使用率", infoModel.getProcessCpuLoadInfoDouble());
            jvmStatus.put("最大堆内存", infoModel.getMaxHeapMemoryInfo());
            jvmStatus.put("使用中的堆内存", infoModel.getUsedHeapMemoryInfo());
            jvmStatus.put("最大非堆内存", infoModel.getMaxNonHeapMemoryInfo());
            jvmStatus.put("使用中的非堆内存", infoModel.getUsedNonHeapMemoryInfo());
            // 系统状态
            systemStatus.put("系统架构", infoModel.getArch());
            systemStatus.put("系统名称", infoModel.getName());
            systemStatus.put("系统总CPU使用率", infoModel.getCpuLoadInfoDouble());
            systemStatus.put("系统总内存", infoModel.getTotalMemoryInfo());
            systemStatus.put("内存使用率", infoModel.getMemoryUseRatioInfo());
            systemStatus.put("使用中的内存", infoModel.getUseMemoryInfo());
            systemStatus.put("系统总交换内存", infoModel.getTotalSwapSpaceInfo());
            systemStatus.put("交换内存使用率", infoModel.getSwapUseRatioInfo());
            systemStatus.put("使用中的交换内存", infoModel.getUseSwapSpaceInfo());

            // 整合
            response.put("retcode", SUCCESS_RET_CODE);      // 返回状态码
            response.put("gameStatus", gameStatus);         // 游戏状态
            response.put("jvmStatus", jvmStatus);           // jvm 状态
            response.put("systemStatus", systemStatus);     // 系统状态

            ctx.result(objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            ctx.result("{\"retcode\":" + ERROR_RET_CODE + ",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    public static void serverStatusV1(Context ctx) {
        AllOnlinePlayers = GlobalOnlinePlayer.getAllOnlinePlayers();
        ctx.result(
            "{\"retcode\":0,\"status\":{\"playerCount\":"
                + AllOnlinePlayers.size()
                + ",\"maxPlayer\":"
                + ACCOUNT.maxPlayer
                + ",\"version\":\""
                + GameConstants.VERSION
                + "\"}}");
    }

    public static void getAllApiName(Context ctx) {
        ctx.result("""
                /api/status
                /status/server
                /api/playerInfo/{uid}
                /api/list/playersInfo
                """);
    }
}
