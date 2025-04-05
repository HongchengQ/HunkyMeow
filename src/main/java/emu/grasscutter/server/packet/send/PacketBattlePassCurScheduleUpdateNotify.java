package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.BattlePassCurScheduleUpdateNotifyOuterClass.BattlePassCurScheduleUpdateNotify;

import static emu.grasscutter.config.Configuration.SERVER;

public class PacketBattlePassCurScheduleUpdateNotify extends BasePacket {

    public PacketBattlePassCurScheduleUpdateNotify(Player player) {
        super(PacketOpcodes.BattlePassCurScheduleUpdateNotify);

        var proto = BattlePassCurScheduleUpdateNotify.newBuilder();

        // 只在dev环境显示纪行 (目前纪行没有完全修好 会卡住 所以干脆直接让客户端屏蔽了)
        // 注意 第一次进入游戏时会走到这里 后续进入游戏会走 BattlePassAllDataNotify
        if (SERVER.isDevServer) proto
            .setHaveCurSchedule(true)
//            .setIsViewed(true)
            .setCurSchedule(player.getBattlePassManager().getScheduleProto())
            .build();

        setData(proto.build());
    }
}
