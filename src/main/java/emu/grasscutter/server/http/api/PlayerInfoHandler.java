package emu.grasscutter.server.http.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import emu.grasscutter.game.player.GlobalOnlinePlayer;
import emu.grasscutter.game.player.Player;
import io.javalin.http.Context;

import java.util.LinkedHashMap;
import java.util.Map;

import static emu.grasscutter.server.http.api.ApiHandler.ERROR_RET_CODE;
import static emu.grasscutter.server.http.api.ApiHandler.SUCCESS_RET_CODE;

public class PlayerInfoHandler {
    public static void playerInfo(Context ctx) {
        ctx.contentType("application/json; charset=UTF-8");

        // 从路径参数中获取 uid 例如: http://127.0.0.1:1145/api/playerInfo/10001
        String uidStr = ctx.pathParam("uid");
        int uid = Integer.parseInt(uidStr);

        // 获取返回对象
        String rsp = getPlayerInfo(uid);
        if (rsp == null) {
            ctx.result("服务器内部错误");
            return;
        }
        ctx.result(rsp);
    }

    // 返回单个玩家信息
    public static String getPlayerInfo(int uid) {
        // 用于转为 json 对象
        final ObjectMapper objectMapper = new ObjectMapper();
        // 用于合并块
        final Map<String, Object> response = new LinkedHashMap<>();
        // 每一个小块
        final Map<String, Object> nameCard = new LinkedHashMap<>();  // 玩家名片 (uid 昵称 签名 头像...)
        final Map<String, Object> worldInfo = new LinkedHashMap<>(); // 世界信息 (世界等级...)

        try {
            // 根据 uid 查询玩家信息
            Player player = Player.getDBPlayerByUid(uid);
            if (player == null) {
                // 空号的意思是只在外部注册了账号 但从未进入过游戏
                throw new Exception("玩家未找到 没有此账号或为空号");
            }

            // 玩家名片
            nameCard.put("Uid", player.getUid());
            nameCard.put("Nickname", player.getNickname());
            nameCard.put("Signature", player.getSignature());
            nameCard.put("HeadImage", player.getHeadImage());
            nameCard.put("NameCardId", player.getNameCardId());
            nameCard.put("Level", player.getLevel());
            nameCard.put("isOnline", GlobalOnlinePlayer.checkIsOnline(uid));    // 这里查找的是全局数据库
            // 世界信息
            worldInfo.put("WorldLevel", player.getWorldLevel());

            // 整合
            response.put("retcode", SUCCESS_RET_CODE);      // 返回状态码
            response.put("NameCard", nameCard);             // 玩家名片
            response.put("WorldInfo", worldInfo);           // 世界信息

            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return "{\"retcode\":" + ERROR_RET_CODE + ",\"message\":\"" + e.getMessage() + "\"}";
        }
    }
}
