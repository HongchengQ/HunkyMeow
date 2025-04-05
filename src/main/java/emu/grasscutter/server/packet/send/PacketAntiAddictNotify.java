package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.AntiAddictNotifyOuterClass.AntiAddictNotify;

public class PacketAntiAddictNotify extends BasePacket {
    public PacketAntiAddictNotify(int MsgType, String Msg) {
        super(PacketOpcodes.AntiAddictNotify);

        AntiAddictNotify proto = AntiAddictNotify.newBuilder()
            .setMsgType(MsgType) // It seems to be correct when MsgType = 2.
//            .setLevel("123") // I don't know what it's for.
            .setMsg(Msg)
            .build();

        this.setData(proto);
    }
}
