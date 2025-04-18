package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ProfilePictureOuterClass.ProfilePicture;
import emu.grasscutter.net.proto.SetPlayerHeadImageRspOuterClass.SetPlayerHeadImageRsp;

public class PacketSetPlayerHeadImageRsp extends BasePacket {

    public PacketSetPlayerHeadImageRsp(Player player) {
        super(PacketOpcodes.SetPlayerHeadImageRsp);

        SetPlayerHeadImageRsp proto =
            SetPlayerHeadImageRsp.newBuilder()
                .setProfilePicture(ProfilePicture.newBuilder().setProfilePictureId(player.getHeadImage()))
                .build();

        this.setData(proto);
    }
}
