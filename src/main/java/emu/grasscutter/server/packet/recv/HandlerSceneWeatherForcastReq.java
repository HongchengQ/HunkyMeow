package emu.grasscutter.server.packet.recv;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.EnterWorldAreaReqOuterClass;
import emu.grasscutter.net.proto.PacketHeadOuterClass;
import emu.grasscutter.net.proto.ReformFireworksReqOuterClass;
import emu.grasscutter.net.proto.SceneWeatherForcastReqOuterClass;
import emu.grasscutter.net.proto.SceneWeatherForcastReqOuterClass.SceneWeatherForcastReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketEnterWorldAreaRsp;

@Opcodes(PacketOpcodes.SceneWeatherForcastReq)
public class HandlerSceneWeatherForcastReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        Grasscutter.getLogger().debug("客户端发出 HandlerSceneWeatherForcastReq");
    }
}
