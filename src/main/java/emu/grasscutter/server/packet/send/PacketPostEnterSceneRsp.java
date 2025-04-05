package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PostEnterSceneRspOuterClass.PostEnterSceneRsp;

public class PacketPostEnterSceneRsp extends BasePacket {

    public PacketPostEnterSceneRsp(Player player) {
        super(PacketOpcodes.PostEnterSceneRsp);

        PostEnterSceneRsp p =
                PostEnterSceneRsp.newBuilder().setEnterSceneToken(player.getEnterSceneToken()).build();

        // 移动到新场景时：
        // 解冻此场景中已解锁的地牢入口点。
        player.unfreezeUnlockedScenePoints();

        this.setData(p);
    }
}
