package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PlayerApplyEnterMpRspOuterClass.PlayerApplyEnterMpRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

public class PacketPlayerApplyEnterMpRsp extends BasePacket {

    public PacketPlayerApplyEnterMpRsp(int targetUid) {
        super(PacketOpcodes.PlayerApplyEnterMpRsp);

        PlayerApplyEnterMpRsp proto =
                PlayerApplyEnterMpRsp.newBuilder()
                    .setTargetUid(targetUid)
                    .setRetcode(Retcode.RET_MATCH_APPLYING_ENTER_MP_VALUE)
                    .build();

        this.setData(proto);
    }
}
