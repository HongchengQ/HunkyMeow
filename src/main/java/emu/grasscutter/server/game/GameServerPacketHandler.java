package emu.grasscutter.server.game;

import static emu.grasscutter.config.Configuration.GAME_INFO;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerDebugMode;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.event.game.ReceivePacketEvent;
import emu.grasscutter.server.game.GameSession.SessionState;
import it.unimi.dsi.fastutil.ints.*;
import lombok.val;

public final class GameServerPacketHandler {
    private final Int2ObjectMap<PacketHandler> handlers;

    public GameServerPacketHandler(Class<? extends PacketHandler> handlerClass) {
        this.handlers = new Int2ObjectOpenHashMap<>();

        this.registerHandlers(handlerClass);
    }

    public void registerPacketHandler(Class<? extends PacketHandler> handlerClass) {
        try {
            var opcode = handlerClass.getAnnotation(Opcodes.class);
            if (opcode == null || opcode.disabled() || opcode.value() <= 0) {
                return;
            }

            var packetHandler = handlerClass.getDeclaredConstructor().newInstance();
            this.handlers.put(opcode.value(), packetHandler);
        } catch (Exception e) {
            Grasscutter.getLogger()
                    .warn("Unable to register handler {}.", handlerClass.getSimpleName(), e);
        }
    }

    public void registerHandlers(Class<? extends PacketHandler> handlerClass) {
        var handlerClasses = Grasscutter.reflector.getSubTypesOf(handlerClass);
        for (var obj : handlerClasses) {
            this.registerPacketHandler(obj);
        }

        // Debug
        Grasscutter.getLogger()
            .debug("Registered {} {}s", this.handlers.size(), handlerClass.getSimpleName());
    }
    public void handle(GameSession session, int opcode, byte[] header, byte[] payload) {
        PacketHandler handler = this.handlers.get(opcode);

        if (handler != null) {
            try {
                SessionState state = session.getState();

                // 优先检查需要立即关闭会话的状态
                // 不要滥用 session.close() 因为客户端会重连 所以很多时候会导致客户端弹窗堆积
                if (state == SessionState.ACCOUNT_BANNED) {
                    session.close();
                    Grasscutter.getLogger().debug("客户端连接拒绝 因为账号处于黑名单中");
                    return;
                } else if (state == SessionState.WAITING_SERVER_LUA || state == SessionState.SERVER_MAX_PLAYER_OVERFLOW) {
                    Grasscutter.getLogger().debug("客户端连接拒绝 因为服务端未初始化完成或人数到达上限");
                    return;
                } else if (state == SessionState.DB_OVERLOAD) {
                    Grasscutter.getLogger().debug("客户端连接拒绝 因为服务端数据库排队列表过多");
                    return;
                }

                // 其次根据传来的 CMD_ID 判断是否符合相应的条件
                if (opcode == PacketOpcodes.PingReq) {
                    // Always continue if packet is ping request
                    if (state == SessionState.NONE) System.out.println("???");; // 这里只是为了不让编辑器报警告
                } else if (opcode == PacketOpcodes.GetPlayerTokenReq) {
                    if (state != SessionState.WAITING_FOR_TOKEN){
                        Grasscutter.getLogger().debug("-1 WAITING_FOR_TOKEN 客户端连接拒绝 因为 state 错误");
                        return;
                    }
                } else if (opcode == PacketOpcodes.PlayerLoginReq) {
                    if (state != SessionState.WAITING_FOR_LOGIN) {
                        Grasscutter.getLogger().debug("-2 WAITING_FOR_LOGIN 客户端连接拒绝 因为 state 错误");
                        return;
                    }
                } else if (opcode == PacketOpcodes.SetPlayerBornDataReq) {
                    if (state != SessionState.PICKING_CHARACTER) {
                        Grasscutter.getLogger().debug("-3 PICKING_CHARACTER 客户端连接拒绝 因为 state 错误");
                        return;
                    }
                } else {
                    if (state != SessionState.ACTIVE) {
                        Grasscutter.getLogger().debug("-4 ACTIVE 客户端连接拒绝 因为 state 错误");
                        return;
                    }
                }

                // 调用事件处理
                ReceivePacketEvent event = new ReceivePacketEvent(session, opcode, payload);
                event.call();
                if (!event.isCanceled()) // If event is not canceled, continue.
                    handler.handle(session, header, event.getPacketData());
            } catch (Exception ex) {
                val player = session.getPlayer();
                int sessionUid = 0;

                if (player != null) sessionUid = player.getUid();

                Grasscutter.getLogger().error("Error handling packet (opcode: {}, uid: {})", opcode, sessionUid, ex);
            }
            return; // Packet successfully handled
        }

        // 记录未处理的包
        if ((GAME_INFO.logPackets == ServerDebugMode.MISSING || GAME_INFO.logPackets == ServerDebugMode.ALL)
            && !PacketOpcodesUtils.LOOPUPDATE_PACKETS.contains(opcode)) {
            Grasscutter.getLogger().info(
                "Unhandled packet ({}): {}",
                opcode,
                PacketOpcodesUtils.getOpcodeName(opcode)
            );
        }
    }
}
