package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PlayerPreEnterMpNotifyOuterClass;
import emu.grasscutter.net.proto.StateOuterClass;

public class PacketPlayerPreEnterMpNotify extends BasePacket {
    public PacketPlayerPreEnterMpNotify(Player player) {
        super(PacketOpcodes.PlayerPreEnterMpNotify);

        this.setData(
                PlayerPreEnterMpNotifyOuterClass.PlayerPreEnterMpNotify.newBuilder()
                        .setUid(player.getUid())
                        .setNickname(player.getNickname())
                        .setState(StateOuterClass.State.State_START));
    }
}
