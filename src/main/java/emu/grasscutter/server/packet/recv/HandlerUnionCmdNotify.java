package emu.grasscutter.server.packet.recv;

import static emu.grasscutter.config.Configuration.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerDebugMode;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.UnionCmdNotifyOuterClass.UnionCmdNotify;
import emu.grasscutter.net.proto.UnionCmdOuterClass.UnionCmd;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.UnionCmdNotify)
public class HandlerUnionCmdNotify extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        UnionCmdNotify req = UnionCmdNotify.parseFrom(payload);
        for (UnionCmd cmd : req.getCmdListList()) {
            int cmdOpcode = cmd.getMessageId();
            byte[] cmdPayload = cmd.getBody().toByteArray();
            if (GAME_INFO.logPackets == ServerDebugMode.WHITELIST
                    && SERVER.debugWhitelist.contains(cmd.getMessageId())) {
                session.logPacket("RECV in Union", cmdOpcode, cmdPayload);
            } else if (GAME_INFO.logPackets == ServerDebugMode.BLACKLIST
                    && !SERVER.debugBlacklist.contains(cmd.getMessageId())) {
                session.logPacket("RECV in Union", cmdOpcode, cmdPayload);
            }

            // 只处理特定数据包 防止递归处理 UnionCmdNotify 数据包 避免栈溢出炸服
            if (cmdOpcode != PacketOpcodes.UnionCmdNotify) {
                session
                    .getServer()
                    .getPacketHandler()
                    .handle(session, cmd.getMessageId(), EMPTY_BYTE_ARRAY, cmd.getBody().toByteArray());
            } else {
                // 正常来说不会走到这里 一些炸服器会发送特殊的 UnionCmdNotify 数据包 使服务端栈溢出导致炸服
                Grasscutter.getLogger().warn("检测到 UnionCmdNotify 为防止栈溢出导致炸服 已屏蔽");
            }
        }

        // Update
        session.getPlayer().getCombatInvokeHandler().update(session.getPlayer());
        session.getPlayer().getAbilityInvokeHandler().update(session.getPlayer());

        // Handle attack results last
        while (!session.getPlayer().getAttackResults().isEmpty()) {
            session.getPlayer().getScene().handleAttack(session.getPlayer().getAttackResults().poll());
        }
    }
}
