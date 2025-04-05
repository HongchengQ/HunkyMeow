package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.GetQuickswapWidgetsRspOuterClass;

public class PacketGetQuickswapWidgetsRsp extends BasePacket {

    public PacketGetQuickswapWidgetsRsp(int a, int b) {
        super(PacketOpcodes.GetQuickswapWidgetsRsp);

        var proto = GetQuickswapWidgetsRspOuterClass.GetQuickswapWidgetsRsp.newBuilder();

        proto
            .setRetcode(0)
            .addQuickswapWidgetIdList(b)
            .setQuickWidgetIndex(a);


        this.setData(proto);
    }
}
