package emu.grasscutter.server.packet.recv;

import static emu.grasscutter.config.Configuration.ACCOUNT;
import static emu.grasscutter.database.DatabaseHelper.DEFAULT_QUEUE_CAPACITY;
import static emu.grasscutter.database.DatabaseHelper.ACCOUNT_QUEUE_CAPACITY;
import static emu.grasscutter.database.DatabaseHelper.ITEM_QUEUE_CAPACITY;
import static emu.grasscutter.database.DatabaseHelper.GROUP_QUEUE_CAPACITY;

import emu.grasscutter.*;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetPlayerTokenReqOuterClass.GetPlayerTokenReq;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.scripts.SceneScriptManager;
import emu.grasscutter.server.event.game.PlayerCreationEvent;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.game.GameSession.SessionState;
import emu.grasscutter.server.packet.send.PacketGetPlayerTokenRsp;
import emu.grasscutter.utils.*;
import emu.grasscutter.utils.helpers.ByteHelper;

import java.nio.ByteBuffer;
import java.security.Signature;
import java.util.concurrent.ThreadPoolExecutor;
import javax.crypto.Cipher;

@Opcodes(PacketOpcodes.GetPlayerTokenReq)
public class HandlerGetPlayerTokenReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = GetPlayerTokenReq.parseFrom(payload);

        // Fetch the account from the ID and token.
        var accountId = req.getAccountUid();
        var account = DispatchUtils.authenticate(accountId, req.getAccountToken());

        // 检查帐户。
        if (account == null && !DebugConstants.ACCEPT_CLIENT_TOKEN) {
            session.close();
            return;
        }
        if (account == null && DebugConstants.ACCEPT_CLIENT_TOKEN) {
            account = DispatchUtils.getAccountById(accountId);
            if (account == null) {
                session.close();
                return;
            }
        }

        // Set account
        session.setAccount(account);

        // 检查服务器中是否存在玩家对象
        // 注意：CHECKING 必须位于此处（在 getPlayerByUid 之前）！因为首先要保存，其次要加载
        var existingPlayer = Grasscutter.getGameServer().getPlayerByAccountId(accountId);
        if (existingPlayer != null) {
            var existingSession = existingPlayer.getSession();
            if (existingSession != session) { // 没有自踢
                existingPlayer.onLogout(); // 必须立即保存，否则下面将加载旧数据
                existingSession.close();
                Grasscutter.getLogger().warn(
                    "Player {} was kicked due to duplicated login",
                    account.getUsername() != null ? account.getUsername() : "unknown"
                );
                return;
            }
        }

        // Call creation event.
        var event = new PlayerCreationEvent(session, Player.class);
        event.call();

        // Get player.
        var player = DatabaseHelper.getPlayerByAccount(account, event.getPlayerClass());

        if (player == null) {
            var nextPlayerUid =
                    DatabaseHelper.getNextPlayerId(session.getAccount().getReservedPlayerUid());

            // Create player instance from event.
            player =
                    event.getPlayerClass().getDeclaredConstructor(GameSession.class).newInstance(session);

            // Save to db
            DatabaseHelper.generatePlayerUid(player, nextPlayerUid);
        }

        // Set player object for session
        session.setPlayer(player);

        // Checks if the player is banned
        if (session.getAccount().isBanned()) {
            session.setState(SessionState.ACCOUNT_BANNED);
            session.send(
                new PacketGetPlayerTokenRsp(
                    session, Retcode.RET_BLACK_UID_VALUE, "FORBID_CHEATING_PLUGINS", session.getAccount().getBanEndTime()));
            return;
        }

        // 当场景初始化未完成时阻止玩家进入
        if ((SceneScriptManager.initSceneCacheGridsOkMap != null) && (!SceneScriptManager.initAllCacheGroupGridsOk)) {
            session.setState(SessionState.WAITING_SERVER_LUA);
            session.send(new PacketGetPlayerTokenRsp(session, "服务器场景初始化未完成 请重试"));
            Grasscutter.getLogger().info("玩家尝试登录失败 服务端场景初始化未完成 uid: {}", session.getPlayer().getUid());
            return;
        }

        // GameServer人数超载
        if ((Grasscutter.getGameServer().getPlayers().size() >= ACCOUNT.maxPlayer) && (ACCOUNT.maxPlayer > -1)) {
            session.setState(SessionState.SERVER_MAX_PLAYER_OVERFLOW);
            session.send(new PacketGetPlayerTokenRsp(session, Retcode.RET_MP_ALLOW_ENTER_PLAYER_FULL));
            Grasscutter.getLogger().info("服务器在线人数已满, uid: {} 无法进入", session.getPlayer().getUid());
            return;
        }

        // 获取所有线程池实例
        var defaultExecutor = (ThreadPoolExecutor) DatabaseHelper.getEventExecutor();
        var accountExecutor = (ThreadPoolExecutor) DatabaseHelper.getEventExecutorAccount();
        var itemExecutor = (ThreadPoolExecutor) DatabaseHelper.getEventExecutorItem();
        var groupExecutor = (ThreadPoolExecutor) DatabaseHelper.getEventExecutorGroup();

        // 检查所有线程池负载
        boolean isOverloaded =
                DatabaseHelper.isThreadPoolOverloaded(defaultExecutor, DEFAULT_QUEUE_CAPACITY) ||
                DatabaseHelper.isThreadPoolOverloaded(accountExecutor, ACCOUNT_QUEUE_CAPACITY) ||
                DatabaseHelper.isThreadPoolOverloaded(itemExecutor, ITEM_QUEUE_CAPACITY) ||
                DatabaseHelper.isThreadPoolOverloaded(groupExecutor, GROUP_QUEUE_CAPACITY);

        // 有线程池超负荷
        if (isOverloaded) {
            session.setState(SessionState.DB_OVERLOAD);
            session.send(new PacketGetPlayerTokenRsp(session, "服务器负载过高！请稍后重试"));
            Grasscutter.getLogger().warn(
                "服务器负载过高！拒绝玩家登录（uid: {}），线程池状态：常规队列排队数量{}队列上限{}，账户队列排队数量{}队列上限{}，物品队列排队数量{}队列上限{}，场景队列排队数量{}队列上限{}",
                session.getPlayer().getUid(),
                defaultExecutor.getQueue().size(), defaultExecutor.getTaskCount(),
                accountExecutor.getQueue().size(), accountExecutor.getTaskCount(),
                itemExecutor.getQueue().size(), itemExecutor.getTaskCount(),
                groupExecutor.getQueue().size(), groupExecutor.getTaskCount()
            );
            return;
        }

        // Load player from database
        player.loadFromDatabase();

        if (!Grasscutter.getConfig().server.game.useXorEncryption) {
            // Set session state
            // session.setUseSecretKey(true);
            session.setState(SessionState.WAITING_FOR_LOGIN);

            session.send(new PacketGetPlayerTokenRsp(session, req.getKeyId()));
            return;
        }

        // Set session state
        session.setUseSecretKey(true);
        session.setState(SessionState.WAITING_FOR_LOGIN);

        // Only >= 2.7.50 has this
        if (req.getKeyId() > 0) {
            var encryptSeed = session.getEncryptSeed();
            try {
                var cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                cipher.init(Cipher.DECRYPT_MODE, Crypto.CUR_SIGNING_KEY);

                var clientSeedEncrypted = Utils.base64Decode(req.getClientRandKey());
                var clientSeed = ByteBuffer.wrap(cipher.doFinal(clientSeedEncrypted)).getLong();

                var seedBytes = ByteBuffer.wrap(new byte[8]).putLong(encryptSeed ^ clientSeed).array();

                cipher.init(Cipher.ENCRYPT_MODE, Crypto.EncryptionKeys.get(req.getKeyId()));
                var seedEncrypted = cipher.doFinal(seedBytes);

                var privateSignature = Signature.getInstance("SHA256withRSA");
                privateSignature.initSign(Crypto.CUR_SIGNING_KEY);
                privateSignature.update(seedBytes);

                session.send(
                        new PacketGetPlayerTokenRsp(
                                session,
                                Utils.base64Encode(seedEncrypted),
                                Utils.base64Encode(privateSignature.sign())));
            } catch (Exception ignored) {
                // Only UA Patch users will have exception
                var clientBytes = Utils.base64Decode(req.getClientRandKey());
                var seed = ByteHelper.longToBytes(encryptSeed);
                Crypto.xor(clientBytes, seed);

                var base64str = Utils.base64Encode(clientBytes);
                session.send(new PacketGetPlayerTokenRsp(session, base64str, "bm90aGluZyBoZXJl"));
            }
        } else {
            // Send packet
            session.send(new PacketGetPlayerTokenRsp(session));
        }
    }
}
