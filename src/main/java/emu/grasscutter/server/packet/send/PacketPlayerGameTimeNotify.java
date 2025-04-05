package emu.grasscutter.server.packet.send;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.World;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PlayerGameTimeNotifyOuterClass.PlayerGameTimeNotify;

public class PacketPlayerGameTimeNotify extends BasePacket {

    public PacketPlayerGameTimeNotify(Player player) {
        super(PacketOpcodes.PlayerGameTimeNotify);
        World getPlayerWorld = player.getWorld();

        if (getPlayerWorld == null) {
            Grasscutter.getLogger().error("uid: {} PacketPlayerGameTimeNotify: 获取 playerWorld 失败 值为 null",
                player.getUid());
            return;
        }

        PlayerGameTimeNotify proto =
                PlayerGameTimeNotify.newBuilder()
                        .setGameTime((int) getPlayerWorld.getTotalGameTimeMinutes())
                        .setUid(player.getUid())
                        .build();

        this.setData(proto);
    }
}
