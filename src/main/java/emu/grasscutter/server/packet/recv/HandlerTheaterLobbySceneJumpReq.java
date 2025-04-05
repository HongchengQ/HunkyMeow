package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketTheaterLobbySceneJumpRsp;

@Opcodes(PacketOpcodes.TheaterLobbySceneJumpReq)
public class HandlerTheaterLobbySceneJumpReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        session.getPlayer().sendPacket(new PacketTheaterLobbySceneJumpRsp());
    }
}
