package emu.grasscutter.server.http.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import io.javalin.http.Context;

import java.util.Map;

import static emu.grasscutter.server.http.api.PlayerInfoHandler.getPlayerInfo;

public class PlayerInfoListHandler {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 所有在线玩家的信息
     */
    public static void playerInfoList(Context ctx) {
        ctx.contentType("application/json; charset=UTF-8");

        // 获取所有在线玩家
        Map<Integer, Player> players = Player.getAllOnlinePlayersMap();

        // 创建 JSON 数组节点
        ArrayNode playerInfoArray = objectMapper.createArrayNode();

        for (Player player : players.values()) {
            // 获取玩家信息
            String playerInfo = getPlayerInfo(player.getUid());
            // 将玩家信息解析为 JSON 对象节点并添加到数组中
            try {
                ObjectNode playerInfoNode = (ObjectNode) objectMapper.readTree(playerInfo);
                playerInfoArray.add(playerInfoNode);
            } catch (Exception e) {
                Grasscutter.getLogger().error("无法解析 UID 的玩家信息: {}", player.getUid(), e);
            }
        }

        // 将 JSON 数组节点转换为字符串并返回
        // 如果返回的只是一个 [] 那么就代表没有玩家在线
        ctx.result(playerInfoArray.toString());
    }
}
