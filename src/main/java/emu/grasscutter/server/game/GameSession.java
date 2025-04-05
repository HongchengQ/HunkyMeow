package emu.grasscutter.server.game;

import static emu.grasscutter.config.Configuration.*;
import static emu.grasscutter.utils.lang.Language.translate;

import com.google.protobuf.Message;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.IKcpSession;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.event.game.SendPacketEvent;
import emu.grasscutter.server.packet.send.PacketAntiAddictNotify;
import emu.grasscutter.utils.*;
import io.netty.buffer.*;

import java.net.InetSocketAddress;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.*;
import org.slf4j.Logger;

public class GameSession implements IGameSession {
    @Getter private final GameServer server;
    private IKcpSession session;

    @Getter @Setter private Account account;
    @Getter private Player player;

    @Getter private long encryptSeed = Crypto.ENCRYPT_SEED;
    private byte[] encryptKey = Crypto.ENCRYPT_KEY;

    @Setter private boolean useSecretKey;
    @Getter @Setter private SessionState state;

    @Getter private int clientTime;
    @Getter private long lastPingTime;
    private int lastClientSeq = 10;

    private final AtomicInteger RecvPacketCount = new AtomicInteger(0);

    private Timer recvPacketResetTimer;

    public GameSession(GameServer server, IKcpSession session) {
        this.server = server;
        this.session = session;

        this.state = SessionState.WAITING_FOR_TOKEN;
        this.lastPingTime = System.currentTimeMillis();

        if (GAME_INFO.useUniquePacketKey) {
            this.encryptKey = new byte[4096];
            this.encryptSeed = Crypto.generateEncryptKeyAndSeed(this.encryptKey);
        }

        /* 每x秒重置计数器 */
        this.recvPacketResetTimer = new Timer(true);
        this.recvPacketResetTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                RecvPacketCount.set(0);
            }
        }, 1000, GameOptions.recvPacketOptions.recvPacketCheckIntervalTime);   // 第一个参数为启动延时 第二个参数为计时器时间
    }

    public void incrementRecvPacketCounter() {
        RecvPacketCount.incrementAndGet();
    }

    public int getRecvPacketCount() {
        return RecvPacketCount.get();
    }

    public InetSocketAddress getAddress() {
        return this.session.getAddress();
    }

    public Logger getLogger() {
        return this.session.getLogger();
    }

    public synchronized void setPlayer(Player player) {
        this.player = player;
        this.player.setSession(this);
        this.player.setAccount(this.getAccount());
    }

    public boolean isLoggedIn() {
        return this.getPlayer() != null;
    }

    public void updateLastPingTime(int clientTime) {
        this.clientTime = clientTime;
        this.lastPingTime = System.currentTimeMillis();
    }

    public int getNextClientSequence() {
        return ++lastClientSeq;
    }

    // 收包日志
    public void logPacket(String sendOrRecv, int opcode, byte[] payload) {
        this.session.getLogger().info("{}: {} ({})", sendOrRecv, PacketOpcodesUtils.getOpcodeName(opcode), opcode);
        if (GAME_INFO.isShowPacketPayload) this.session.getLogger().info(Utils.bytesToHex(payload));
    }

    // 发包日志
    public void logPacket(String sendOrRecv, int opcode, Message message, byte[] payload) {
        if (payload == null || !GAME_INFO.isShowPacketPayload) {
            // 只显示 sendOrRecv + 包名 + cmdId
            this.session.getLogger().info("{}: {} ({}) ", sendOrRecv, PacketOpcodesUtils.getOpcodeName(opcode), opcode);
            return;
        } else if (message == null) {
            // 显示 sendOrRecv + 包名 + cmdId + payload
            this.session.getLogger().info("{}: {} ({})\n{}",
                sendOrRecv, PacketOpcodesUtils.getOpcodeName(opcode), opcode, Utils.bytesToHex(payload));
            return;
        }

        // 显示 sendOrRecv + 包名 + cmdId + message + payload
        this.session.getLogger().info("{}: {} ({})\n{}{}",
            sendOrRecv, PacketOpcodesUtils.getOpcodeName(opcode), opcode, message, Utils.bytesToHex(payload));
    }


    public void send(BasePacket packet) {
        if (this.session == null) {
            Grasscutter.getLogger().debug("发送数据包失败 因为session为空");
            return;
        }
        // Test
        if (packet.getOpcode() <= 0) {
            this.session.getLogger().warn("Attempted to send packet with unknown ID!");
            return;
        }

        // Header
        if (packet.shouldBuildHeader()) {
            packet.buildHeader(this.getNextClientSequence());
        }

        // Log
        switch (GAME_INFO.logPackets) {
            case ALL -> {
                if (!PacketOpcodesUtils.LOOP_PACKETS.contains(packet.getOpcode())
                        || GAME_INFO.isShowLoopPackets) {
                    logPacket("SEND", packet.getOpcode(), packet.getMessage(), packet.getData());
                }
            }
            case WHITELIST -> {
                if (SERVER.debugWhitelist.contains(packet.getOpcode())) {
                    logPacket("SEND", packet.getOpcode(), packet.getMessage(), packet.getData());
                }
            }
            case BLACKLIST -> {
                if (!SERVER.debugBlacklist.contains(packet.getOpcode())) {
                    logPacket("SEND", packet.getOpcode(), packet.getMessage(), packet.getData());
                }
            }
            default -> {}
        }

        // Invoke event.
        SendPacketEvent event = new SendPacketEvent(this, packet);
        event.call();
        if (!event.isCanceled()) { // If event is not cancelled, continue.
            try {
                packet = event.getPacket();
                var bytes = packet.build();
                if (packet.shouldEncrypt && Grasscutter.getConfig().server.game.useXorEncryption) {
                    Crypto.xor(bytes, packet.useDispatchKey() ? Crypto.DISPATCH_KEY : this.encryptKey);
                }
                this.session.send(bytes);
            } catch (Exception ex) {
                this.session.getLogger().debug("Unable to send packet to client.", ex);
            }
        }
    }

    @Override
    public void onConnected() {
        Grasscutter.getLogger().info(translate("messages.game.connect", this.getAddress().toString()));
    }

    @Override
    public void onReceived(byte[] bytes) {
        if (this.session == null) {
            Grasscutter.getLogger().debug("接收数据包失败 因为session为空");
            return;
        }

        // Decrypt and turn back into a packet
        if (Grasscutter.getConfig().server.game.useXorEncryption) {
            Crypto.xor(bytes, this.useSecretKey ? this.encryptKey : Crypto.DISPATCH_KEY);
        }
        ByteBuf packet = Unpooled.wrappedBuffer(bytes);

        try {
            incrementRecvPacketCounter();   // 递增数据包计数器
            // 检查数据包计数是否超过限制
            if ((getRecvPacketCount() > GameOptions.recvPacketOptions.recvPacketMaxFreq) && !SERVER.isDevServer) {
                String Msg = "客户端发包太频繁，连接断开";
                Grasscutter.getLogger().error(Msg);
                getPlayer().sendPacket(new PacketAntiAddictNotify(1, Msg));
                close(); return;
            }

            while (packet.readableBytes() > 0) {
                // Length
                if (packet.readableBytes() < 12) {
                    return;
                }
                // Packet sanity check
                int const1 = packet.readShort();
                if (const1 != 17767) {
                    this.session.getLogger().debug("Invalid packet header received: got {}, expected 17767", const1);
                    return; // Bad packet
                }

                // Data
                int opcode = packet.readShort();
                int headerLength = packet.readShort();
                int payloadLength = packet.readInt();
                byte[] header = new byte[headerLength];
                byte[] payload = new byte[payloadLength];

                packet.readBytes(header);
                packet.readBytes(payload);
                // Sanity check #2
                int const2 = packet.readShort();
                if (const2 != -30293) {
                    this.session.getLogger().debug("Invalid packet footer received: got {}, expected -30293", const2);
                    return; // Bad packet
                }

                // Log packet
                switch (GAME_INFO.logPackets) {
                    case ALL -> {
                        if ((!PacketOpcodesUtils.LOOP_PACKETS.contains(opcode) || GAME_INFO.isShowLoopPackets)
                            && !PacketOpcodesUtils.LOOPUPDATE_PACKETS.contains(opcode)) {
                            logPacket("RECV", opcode, payload);
                        }
                    }
                    case WHITELIST -> {
                        if (SERVER.debugWhitelist.contains(opcode)) {
                            logPacket("RECV", opcode, payload);
                        }
                    }
                    case BLACKLIST -> {
                        if (!(SERVER.debugBlacklist.contains(opcode))) {
                            logPacket("RECV", opcode, payload);
                        }
                    }
                    default -> {}
                }

                // Handle
                getServer().getPacketHandler().handle(this, opcode, header, payload);
            }
        } catch (Exception ex) {
            this.session.getLogger().warn("Unable to process packet.", ex);
        } finally {
            packet.release();
        }
    }

    public void clearTimerTask() {
        if (this.recvPacketResetTimer != null) {
            this.recvPacketResetTimer.cancel();
            this.recvPacketResetTimer = null;
        }
    }

    @Override
    public void onDisconnected() {
        clearTimerTask();
        setState(SessionState.INACTIVE);
        // send disconnection pack in case of reconnection
        Grasscutter.getLogger()
                .info(translate("messages.game.disconnect", this.getAddress().toString()));
        // Save after disconnecting
        if (this.isLoggedIn()) {
            Player player = getPlayer();
            // Call logout event.
            player.onLogout();
        }
        try {
            this.send(new BasePacket(PacketOpcodes.ServerDisconnectClientNotify));
        } catch (Throwable ex) {
            this.session.getLogger().warn("Failed to disconnect client.", ex);
        }

        this.session = null;
    }

    public void close() {
        this.session.close();
    }

    public boolean isActive() {
        return this.getState() == SessionState.ACTIVE;
    }

    public enum SessionState {
        NONE,
        INACTIVE,
        WAITING_FOR_TOKEN,
        WAITING_FOR_LOGIN,
        PICKING_CHARACTER,
        ACTIVE,
        ACCOUNT_BANNED,
        WAITING_SERVER_LUA,
        SERVER_MAX_PLAYER_OVERFLOW,
        DB_OVERLOAD
    }
}
