package emu.grasscutter.server.game;

import static emu.grasscutter.config.Configuration.*;
import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.*;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.battlepass.BattlePassSystem;
import emu.grasscutter.game.chat.ChatSystem;
import emu.grasscutter.game.chat.ChatSystemHandler;
import emu.grasscutter.game.combine.CombineManger;
import emu.grasscutter.game.drop.DropSystem;
import emu.grasscutter.game.drop.DropSystemLegacy;
import emu.grasscutter.game.dungeons.DungeonSystem;
import emu.grasscutter.game.expedition.ExpeditionSystem;
import emu.grasscutter.game.gacha.GachaSystem;
import emu.grasscutter.game.home.HomeWorld;
import emu.grasscutter.game.home.HomeWorldMPSystem;
import emu.grasscutter.game.managers.cooking.CookingCompoundManager;
import emu.grasscutter.game.managers.cooking.CookingManager;
import emu.grasscutter.game.managers.energy.EnergyManager;
import emu.grasscutter.game.managers.stamina.StaminaManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.QuestSystem;
import emu.grasscutter.game.shop.ShopSystem;
import emu.grasscutter.game.systems.AnnouncementSystem;
import emu.grasscutter.game.systems.InventorySystem;
import emu.grasscutter.game.systems.MultiplayerSystem;
import emu.grasscutter.game.talk.TalkSystem;
import emu.grasscutter.game.tower.TowerSystem;
import emu.grasscutter.game.world.World;
import emu.grasscutter.game.world.WorldDataSystem;
import emu.grasscutter.net.INetworkTransport;
import emu.grasscutter.net.impl.NetworkTransportImpl;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.proto.SocialDetailOuterClass.SocialDetail;
import emu.grasscutter.server.dispatch.DispatchClient;
import emu.grasscutter.server.event.game.ServerTickEvent;
import emu.grasscutter.server.event.internal.ServerStartEvent;
import emu.grasscutter.server.event.internal.ServerStopEvent;
import emu.grasscutter.server.event.types.ServerEvent;
import emu.grasscutter.server.scheduler.ServerTaskScheduler;
import emu.grasscutter.task.TaskMap;
import emu.grasscutter.utils.Utils;
import it.unimi.dsi.fastutil.ints.*;
import java.net.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.*;

@Getter
@Slf4j
public final class GameServer implements Iterable<Player> {
    /**
     * This can be set by plugins to change the network transport implementation.
     */
    @Setter private static Class<? extends INetworkTransport> transport = NetworkTransportImpl.class;

    // Game server base
    private final InetSocketAddress address;
    private final INetworkTransport netTransport;

    private final GameServerPacketHandler packetHandler;
    private final Map<Integer, Player> players;
    private final Set<World> worlds;
    private final Int2ObjectMap<HomeWorld> homeWorlds;

    @Setter private DispatchClient dispatchClient;

    // Server systems
    private final InventorySystem inventorySystem;
    private final GachaSystem gachaSystem;
    private final ShopSystem shopSystem;
    private final MultiplayerSystem multiplayerSystem;
    private final HomeWorldMPSystem homeWorldMPSystem;
    private final DungeonSystem dungeonSystem;
    private final ExpeditionSystem expeditionSystem;
    private final DropSystem dropSystem;
    private final DropSystemLegacy dropSystemLegacy;
    private final WorldDataSystem worldDataSystem;
    private final BattlePassSystem battlePassSystem;
    private final CombineManger combineSystem;
    private final TowerSystem towerSystem;
    private final AnnouncementSystem announcementSystem;
    private final QuestSystem questSystem;
    private final TalkSystem talkSystem;

    // Extra
    private final ServerTaskScheduler scheduler;
    private final TaskMap taskMap;

    private ChatSystemHandler chatManager;

    private static final ScheduledExecutorService schedulerExecutor =
        Executors.newSingleThreadScheduledExecutor();

    /**
     * @return The URI for the dispatch server.
     */
    @SneakyThrows
    public static URI getDispatchUrl() {
        return new URI(DISPATCH_INFO.dispatchUrl);
    }

    public GameServer() {
        this(getAdapterInetSocketAddress());
    }

    public GameServer(InetSocketAddress address) {
        // Check if we are in dispatch only mode.
        if (Grasscutter.getRunMode() == ServerRunMode.DISPATCH_ONLY) {
            // Set all the systems to null.
            this.scheduler = null;
            this.taskMap = null;

            this.address = null;
            this.netTransport = null;
            this.packetHandler = null;
            this.dispatchClient = null;
            this.players = null;
            this.worlds = null;
            this.homeWorlds = null;

            this.inventorySystem = null;
            this.gachaSystem = null;
            this.shopSystem = null;
            this.multiplayerSystem = null;
            this.homeWorldMPSystem = null;
            this.dungeonSystem = null;
            this.expeditionSystem = null;
            this.dropSystem = null;
            this.dropSystemLegacy = null;
            this.worldDataSystem = null;
            this.battlePassSystem = null;
            this.combineSystem = null;
            this.towerSystem = null;
            this.announcementSystem = null;
            this.questSystem = null;
            this.talkSystem = null;
            return;
        }

        // Create the network transport.
        INetworkTransport transport;
        try {
            transport = GameServer.transport
                .getDeclaredConstructor()
                .newInstance();
        } catch (Exception ex) {
            log.error("Failed to create network transport.", ex);
            transport = new NetworkTransportImpl();
        }

        // Initialize the transport.
        this.netTransport = transport;
        this.netTransport.start(this.address = address);

        EnergyManager.initialize();
        StaminaManager.initialize();
        CookingManager.initialize();
        CookingCompoundManager.initialize();
        CombineManger.initialize();

        // Game Server base
        this.packetHandler = new GameServerPacketHandler(PacketHandler.class);
        this.dispatchClient = new DispatchClient(GameServer.getDispatchUrl());
        this.players = new ConcurrentHashMap<>();
        this.worlds = Collections.synchronizedSet(new HashSet<>());
        this.homeWorlds = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());

        // Extra
        this.scheduler = new ServerTaskScheduler();
        this.taskMap = new TaskMap(true);

        // Create game systems
        this.inventorySystem = new InventorySystem(this);
        this.gachaSystem = new GachaSystem(this);
        this.shopSystem = new ShopSystem(this);
        this.multiplayerSystem = new MultiplayerSystem(this);
        this.homeWorldMPSystem = new HomeWorldMPSystem(this);
        this.dungeonSystem = new DungeonSystem(this);
        this.dropSystem = new DropSystem(this);
        this.dropSystemLegacy = new DropSystemLegacy(this);
        this.expeditionSystem = new ExpeditionSystem(this);
        this.combineSystem = new CombineManger(this);
        this.towerSystem = new TowerSystem(this);
        this.worldDataSystem = new WorldDataSystem(this);
        this.battlePassSystem = new BattlePassSystem(this);
        this.announcementSystem = new AnnouncementSystem(this);
        this.questSystem = new QuestSystem(this);
        this.talkSystem = new TalkSystem(this);

        // Chata manager
        this.chatManager = new ChatSystem(this);
    }

    private static InetSocketAddress getAdapterInetSocketAddress() {
        InetSocketAddress inetSocketAddress;
        if (GAME_INFO.bindAddress.isEmpty()) {
            inetSocketAddress = new InetSocketAddress(GAME_INFO.bindPort);
        } else {
            inetSocketAddress = new InetSocketAddress(GAME_INFO.bindAddress, GAME_INFO.bindPort);
        }
        return inetSocketAddress;
    }

    @Deprecated
    public ChatSystemHandler getChatManager() {
        return chatManager;
    }

    @Deprecated
    public void setChatManager(ChatSystemHandler chatManager) {
        this.chatManager = chatManager;
    }

    public ChatSystemHandler getChatSystem() {
        return chatManager;
    }

    public void setChatSystem(ChatSystemHandler chatManager) {
        this.chatManager = chatManager;
    }

    public void registerPlayer(Player player) {
        getPlayers().put(player.getUid(), player);
    }

    @Nullable public Player getPlayerByUid(int id) {
        return this.getPlayerByUid(id, false);
    }

    @Nullable public Player getPlayerByUid(int id, boolean allowOfflinePlayers) {
        // Console check
        if (id == GameConstants.SERVER_CONSOLE_UID) {
            return null;
        }

        // Get from online players
        Player player = this.getPlayers().get(id);

        if (!allowOfflinePlayers) {
            return player;
        }

        // Check database if character isnt here
        if (player == null) {
            player = DatabaseHelper.getPlayerByUid(id);
        }

        return player;
    }

    public Player getPlayerByAccountId(String accountId) {
        Optional<Player> playerOpt =
                getPlayers().values().stream()
                        .filter(player -> player.getAccount().getId().equals(accountId))
                        .findFirst();
        return playerOpt.orElse(null);
    }

    /**
     * Tries to find a player with the matching IP address.
     *
     * @param ipAddress The IP address. This should just be numbers without a port.
     * @return The player, or null if one could not be found.
     */
    public Player getPlayerByIpAddress(String ipAddress) {
        return this.getPlayers().values().stream()
                .map(Player::getSession)
                .filter(
                        session -> session != null && session.getAddress().getHostString().equals(ipAddress))
                .map(GameSession::getPlayer)
                .findFirst()
                .orElse(null);
    }

    public SocialDetail.Builder getSocialDetailByUid(int id) {
        // Get from online players
        Player player = this.getPlayerByUid(id, true);

        if (player == null) {
            return null;
        }

        return player.getSocialDetail();
    }

    public Account getAccountByName(String username) {
        Optional<Player> playerOpt =
                getPlayers().values().stream()
                        .filter(player -> player.getAccount().getUsername().equals(username))
                        .findFirst();
        if (playerOpt.isPresent()) {
            return playerOpt.get().getAccount();
        }
        return DatabaseHelper.getAccountByName(username);
    }

    public void onTick() {
        Instant tickStart = Instant.now();
        final long timeoutMillis = 2000; // 允许的最大执行时间 单位毫秒

        try {
            // Tick worlds and home worlds.
            this.worlds.removeIf(World::onTick);

            // Tick players.
            this.players.values().forEach(Player::onTick);

            // Tick scheduler.
            this.getScheduler().runTasks();

        } catch (Exception e) {
            Grasscutter.getLogger().warn("World/Player onTick 发生异常", e);
        } finally {
            var duration = Duration.between(tickStart, Instant.now()).toMillis();
            if (duration > timeoutMillis) {
                Grasscutter.getLogger().warn(
                    "onTick()执行时间超过 {}ms，实际耗时 {}ms，当前世界对象 {}，当前玩家对象 {}",
                    timeoutMillis, duration, this.worlds.size(), this.players.size());
            }
        }

        // 触发事件
        ServerTickEvent event = new ServerTickEvent(tickStart, Instant.now());
        event.call();
    }


    public void registerWorld(World world) {
        this.getWorlds().add(world);
    }

    public void deregisterWorld(World world) {
        // TODO Auto-generated method stub
        world.save(); // Save the player's world
    }

    public HomeWorld getHomeWorldOrCreate(Player owner) {
        return this.getHomeWorlds()
                .computeIfAbsent(owner.getUid(), (uid) -> new HomeWorld(this, owner));
    }

    public void start() {
        if (Grasscutter.getRunMode() == ServerRunMode.GAME_ONLY) {
            // Connect to dispatch server.
            this.dispatchClient.connect();
        }

        schedulerExecutor.scheduleAtFixedRate(() -> {
            try {
                onTick();
            } catch (Exception e) {
                Grasscutter.getLogger().error(translate("messages.game.game_update_error"), e);
            }
        }, 0, 1, TimeUnit.SECONDS);

        Grasscutter.getLogger()
                .info(translate("messages.game.address_bind", GAME_INFO.accessAddress, address.getPort()));
        ServerStartEvent event = new ServerStartEvent(ServerEvent.Type.GAME, OffsetDateTime.now());
        event.call();
    }

    public void onServerShutdown() {
        var event = new ServerStopEvent(ServerEvent.Type.GAME, OffsetDateTime.now());
        event.call();

        // Save players & the world.
        this.getPlayers().forEach((uid, player) -> player.getSession().close());
        this.getWorlds().forEach(World::save);

        Utils.sleep(1000L); // Wait 1 second for operations to finish.
    }

    @NotNull @Override
    public Iterator<Player> iterator() {
        return this.getPlayers().values().iterator();
    }
}
