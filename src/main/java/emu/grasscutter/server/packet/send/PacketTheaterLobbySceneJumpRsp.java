package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.TheaterLobbySceneJumpRspOuterClass;

public class PacketTheaterLobbySceneJumpRsp extends BasePacket {

    public PacketTheaterLobbySceneJumpRsp() {
        super(PacketOpcodes.TheaterLobbySceneJumpRsp);

        var proto = TheaterLobbySceneJumpRspOuterClass.TheaterLobbySceneJumpRsp.newBuilder();

        this.setData(proto.build());
    }
}
