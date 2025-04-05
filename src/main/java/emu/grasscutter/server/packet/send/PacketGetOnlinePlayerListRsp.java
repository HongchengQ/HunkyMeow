package emu.grasscutter.server.packet.send;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.GlobalOnlinePlayer;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetOnlinePlayerListRspOuterClass.GetOnlinePlayerListRsp;

import java.util.List;
import java.util.ArrayList;

public class PacketGetOnlinePlayerListRsp extends BasePacket {
    private static final int maxRetcodePlayers = 50;

    public PacketGetOnlinePlayerListRsp(Player session) {
        super(PacketOpcodes.GetOnlinePlayerListRsp);

        List<Player> players = new ArrayList<>(Grasscutter.getGameServer().getPlayers().values()
            .stream().limit(maxRetcodePlayers)    // 返回50个玩家
            .toList());

        // 当前节点返回的玩家不足50个时 从其他节点寻找
        if (players.size() < maxRetcodePlayers) {
            List<Integer> otherNodePlayers = new ArrayList<>(GlobalOnlinePlayer.getOnlinePlayersNotInCurrentNode()
                    .stream().limit(maxRetcodePlayers - players.size())
                    .toList());

            for(int uid : otherNodePlayers) {
                if (players.size() >= maxRetcodePlayers) continue;
                Player otherNodePlayer = Grasscutter.getGameServer().getPlayerByUid(uid, true);
                players.add(otherNodePlayer);
            }
        }

        GetOnlinePlayerListRsp.Builder proto = GetOnlinePlayerListRsp.newBuilder();

        if (!players.isEmpty()) {
            for (Player player : players) {
                if (player.getUid() == session.getUid()) continue;

                proto.addPlayerInfoList(player.getOnlinePlayerInfo());
            }
        }

        this.setData(proto);
    }
}
