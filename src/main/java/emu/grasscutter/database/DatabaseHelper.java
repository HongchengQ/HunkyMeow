package emu.grasscutter.database;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Projections.include;

import dev.morphia.query.*;
import dev.morphia.query.experimental.filters.Filters;
import emu.grasscutter.*;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.achievement.Achievements;
import emu.grasscutter.game.activity.PlayerActivityData;
import emu.grasscutter.game.activity.musicgame.MusicGameBeatmap;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.battlepass.BattlePassManager;
import emu.grasscutter.game.friends.Friendship;
import emu.grasscutter.game.gacha.GachaRecord;
import emu.grasscutter.game.home.GameHome;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.mail.Mail;
import emu.grasscutter.game.player.GlobalOnlinePlayer;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.GameMainQuest;
import emu.grasscutter.game.world.SceneBuild;
import emu.grasscutter.game.world.SceneGroupInstance;
import emu.grasscutter.utils.objects.Returnable;
import io.netty.util.concurrent.FastThreadLocalThread;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

public final class DatabaseHelper {
    public static final int AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors();  // 可用处理器
    private static final int DEFAULT_KEEP_ALIVE = 600;                                          // 线程空闲存活时间（秒）

    // 主线程池最大容量
    public static final int DEFAULT_QUEUE_CAPACITY = AVAILABLE_PROCESSORS * 1000;
    // 账户线程池最大容量
    public static final int ACCOUNT_QUEUE_CAPACITY = AVAILABLE_PROCESSORS * 50;
    // 物品线程池最大容量
    public static final int ITEM_QUEUE_CAPACITY = AVAILABLE_PROCESSORS * 8000;
    // 大世界组线程池最大容量
    public static final int GROUP_QUEUE_CAPACITY = AVAILABLE_PROCESSORS * 4000;

    // 线程工厂（自定义名称）
    private static ThreadFactory namedThreadFactory(String namePrefix) {
        return new ThreadFactory() {
            private final AtomicInteger count = new AtomicInteger(1);
            @Override
            public Thread newThread(@NotNull Runnable r) {
                Thread thread = new FastThreadLocalThread(r);
                thread.setName(namePrefix + "-thread-" + count.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        };
    }

    // 主线程池（默认） 存储player和其他对象
    @Getter
    private static final ExecutorService eventExecutor = new ThreadPoolExecutor(
        AVAILABLE_PROCESSORS / 3 + 1, // 核心线程数
        AVAILABLE_PROCESSORS, // 最大线程数：应对突发流量
        DEFAULT_KEEP_ALIVE, TimeUnit.SECONDS,
        new LinkedBlockingDeque<>(DEFAULT_QUEUE_CAPACITY), // 队列大小：CPU核心数 × 乘数
        namedThreadFactory("Database-Default"), // 自定义线程名
        new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略：提交者执行
    );

    // 账户操作线程池（低频但关键） 存储账号信息
    @Getter
    private static final ExecutorService eventExecutorAccount = new ThreadPoolExecutor(
        AVAILABLE_PROCESSORS / 4 + 1, // 核心线程数
        AVAILABLE_PROCESSORS,
        DEFAULT_KEEP_ALIVE, TimeUnit.SECONDS,
        new LinkedBlockingDeque<>(ACCOUNT_QUEUE_CAPACITY),
        namedThreadFactory("Database-Account"),
        new ThreadPoolExecutor.CallerRunsPolicy()
    );

    // 物品存储线程池（高频）
    @Getter
    private static final ExecutorService eventExecutorItem = new ThreadPoolExecutor(
        AVAILABLE_PROCESSORS / 3 + 1, // 核心线程数
        AVAILABLE_PROCESSORS / 2 + 1, // 最大线程数
        DEFAULT_KEEP_ALIVE, TimeUnit.SECONDS,
        new LinkedBlockingDeque<>(ITEM_QUEUE_CAPACITY),
        namedThreadFactory("Database-Item"),
        new ThreadPoolExecutor.CallerRunsPolicy()
    );

    // 场景实例线程池（高频）
    @Getter
    private static final ExecutorService eventExecutorGroup = new ThreadPoolExecutor(
        AVAILABLE_PROCESSORS / 3 + 1, // 核心线程数
        AVAILABLE_PROCESSORS / 2 + 1, // 最大线程数
        DEFAULT_KEEP_ALIVE, TimeUnit.SECONDS,
        new LinkedBlockingDeque<>(GROUP_QUEUE_CAPACITY),
        namedThreadFactory("Database-Group"),
        new ThreadPoolExecutor.CallerRunsPolicy()
    );

    public static boolean isThreadPoolOverloaded(ThreadPoolExecutor executor, int maxCount) {
        int queueSize = executor.getQueue().size();
        return queueSize > maxCount * 0.7f;
    }


    // 测试线程池资源被耗尽
    public static void dataBaseLockTest() throws RuntimeException {
        DatabaseHelper.eventExecutor.submit(() -> {
            for (int i = 0; i < 10; i++) {
                DatabaseHelper.eventExecutorItem.submit(() -> {
                    try {
                        while (true) {
                            Thread.sleep(999999999);
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        });
    }

    /**
     * Saves an object on the account datastore.
     *
     * @param object The object to save.
     */
    public static void saveAccountAsync(Object object) {
        DatabaseHelper.eventExecutorAccount.submit(() -> DatabaseManager.getAccountDatastore().save(object));
    }

    /**
     * Saves an object on the game datastore.
     *
     * @param object The object to save.
     */
    public static void saveGameAsync(Object object) {
        if (object == null) return;

        switch (object) {
            case GameItem gameItem -> DatabaseHelper.eventExecutorItem.submit(() -> {
                // 物品存储线程池（高频）
                try {
                    DatabaseManager.getGameDatastore().save(gameItem);
                } catch (Exception e) {
                    Grasscutter.getLogger().debug("Failed to saveGameItem", e);
                }
            });
            case SceneGroupInstance sceneGroupInstance -> DatabaseHelper.eventExecutorGroup.submit(() -> {
                // 场景实例线程池（高频）
                try {
                    DatabaseManager.getGameDatastore().save(sceneGroupInstance);
                } catch (Exception e) {
                    Grasscutter.getLogger().debug("Failed to saveSceneGroupInstance", e);
                }

            });
            case Account account -> DatabaseHelper.eventExecutorAccount.submit(() -> {
                // 账户操作线程池（低频但关键）
                try {
                    DatabaseManager.getGameDatastore().save(account);
                } catch (Exception e) {
                    Grasscutter.getLogger().debug("Failed to saveAccountDB", e);
                }
            });
            default -> DatabaseHelper.eventExecutor.submit(() -> {
                // 主线程池（默认）
                try {
                    DatabaseManager.getGameDatastore().save(object);
                } catch (Exception exception) {
                    Grasscutter.getLogger().error("Failed to saveGameAsync", exception);
                }
            });
        }
    }

    /**
     * Runs a runnable on the event executor. Should be limited to database-related operations.
     *
     * @param runnable The runnable to run.
     */
    public static void asyncOperation(Runnable runnable) {
        DatabaseHelper.eventExecutor.submit(runnable);
    }
    public static void asyncOperationAccount(Runnable runnable) {
        DatabaseHelper.eventExecutor.submit(runnable);
    }

    /**
     * Fetches an object asynchronously.
     *
     * @param task The task to run.
     * @return The future.
     */
    public static <T> CompletableFuture<T> fetchAsync(Returnable<T> task) {
        var future = new CompletableFuture<T>();

        // Run the task on the event executor.
        DatabaseHelper.eventExecutor.submit(
                () -> {
                    try {
                        future.complete(task.invoke());
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                });

        return future;
    }

    public static Account createAccount(String username) {
        return createAccountWithUid(username, 0);
    }

    public static Account createAccountWithUid(String username, int reservedUid) {
        // Unique names only
        if (DatabaseHelper.checkIfAccountExists(username)) {
            return null;
        }

        // Make sure there are no id collisions
        if (reservedUid > 0) {
            // Cannot make account with the same uid as the server console
            if (reservedUid == GameConstants.SERVER_CONSOLE_UID) {
                return null;
            }

            if (DatabaseHelper.checkIfAccountExists(reservedUid)) {
                return null;
            }

            // Make sure no existing player already has this id.
            if (DatabaseHelper.checkIfPlayerExists(reservedUid)) {
                return null;
            }
        }

        // Account
        @SuppressWarnings("deprecation")
        Account account = new Account();
        account.setUsername(username);
        account.setId(Integer.toString(DatabaseManager.getNextId(account)));

        if (reservedUid > 0) {
            account.setReservedPlayerUid(reservedUid);
        }

        DatabaseHelper.saveAccount(account);
        return account;
    }

    public static Account createAccountWithPassword(String username, String password, String email) {
        // Unique names only
        Account exists = DatabaseHelper.getAccountByName(username);
        if (exists != null) {
            return null;
        }

        // Account
        @SuppressWarnings("deprecation")
        Account account = new Account();
        account.setId(Integer.toString(DatabaseManager.getNextId(account)));
        account.setUsername(username);
        account.setEmail(email);
        account.setPassword(password);
        DatabaseHelper.saveAccount(account);
        return account;
    }

    public static void saveAccount(Account account) {
        DatabaseHelper.saveAccountAsync(account);
    }

    public static Account getAccountByName(String username) {
        return DatabaseManager.getAccountDatastore()
                .find(Account.class)
                .filter(Filters.eq("username", username))
                .first();
    }

    public static Account getAccountByToken(String token) {
        if (token == null) return null;
        return DatabaseManager.getAccountDatastore()
                .find(Account.class)
                .filter(Filters.eq("token", token))
                .first();
    }

    public static Account getAccountBySessionKey(String sessionKey) {
        if (sessionKey == null) return null;
        return DatabaseManager.getAccountDatastore()
                .find(Account.class)
                .filter(Filters.eq("sessionKey", sessionKey))
                .first();
    }

    public static Account getAccountById(String uid) {
        return DatabaseManager.getAccountDatastore()
                .find(Account.class)
                .filter(Filters.eq("_id", uid))
                .first();
    }

    public static Account getAccountByPlayerId(int playerId) {
        return DatabaseManager.getAccountDatastore()
                .find(Account.class)
                .filter(Filters.eq("reservedPlayerId", playerId))
                .first();
    }

    public static boolean checkIfAccountExists(String name) {
        return DatabaseManager.getAccountDatastore()
                        .find(Account.class)
                        .filter(Filters.eq("username", name))
                        .count()
                > 0;
    }

    public static boolean checkIfAccountExists(int reservedUid) {
        return DatabaseManager.getAccountDatastore()
                        .find(Account.class)
                        .filter(Filters.eq("reservedPlayerId", reservedUid))
                        .count()
                > 0;
    }

    public static synchronized void deleteAccount(Account target) {
        // To delete an account, we need to also delete all the other documents in the database that
        // reference the account.
        // This should optimally be wrapped inside a transaction, to make sure an error thrown mid-way
        // does not leave the
        // database in an inconsistent state, but unfortunately Mongo only supports that when we have a
        // replica set ...

        Player player = Grasscutter.getGameServer().getPlayerByAccountId(target.getId());

        // Close session first
        if (player != null) {
            player.getSession().close();
        } else {
            player = getPlayerByAccount(target);
            if (player == null) return;
        }
        int uid = player.getUid();

        DatabaseHelper.asyncOperationAccount(
                () -> {
                    // Delete data from collections
                    DatabaseManager.getGameDatabase()
                            .getCollection("achievements")
                            .deleteMany(eq("uid", uid));
                    DatabaseManager.getGameDatabase().getCollection("activities").deleteMany(eq("uid", uid));
                    DatabaseManager.getGameDatabase().getCollection("homes").deleteMany(eq("ownerUid", uid));
                    DatabaseManager.getGameDatabase().getCollection("mail").deleteMany(eq("ownerUid", uid));
                    DatabaseManager.getGameDatabase().getCollection("avatars").deleteMany(eq("ownerId", uid));
                    DatabaseManager.getGameDatabase().getCollection("gachas").deleteMany(eq("ownerId", uid));
                    DatabaseManager.getGameDatabase().getCollection("items").deleteMany(eq("ownerId", uid));
                    DatabaseManager.getGameDatabase().getCollection("quests").deleteMany(eq("ownerUid", uid));
                    DatabaseManager.getGameDatabase()
                            .getCollection("battlepass")
                            .deleteMany(eq("ownerUid", uid));

                    // Delete friendships.
                    // Here, we need to make sure to not only delete the deleted account's friendships,
                    // but also all friendship entries for that account's friends.
                    DatabaseManager.getGameDatabase()
                            .getCollection("friendships")
                            .deleteMany(eq("ownerId", uid));
                    DatabaseManager.getGameDatabase()
                            .getCollection("friendships")
                            .deleteMany(eq("friendId", uid));

                    // Delete the player last.
                    DatabaseManager.getGameDatastore()
                            .find(Player.class)
                            .filter(Filters.eq("id", uid))
                            .delete();

                    // Finally, delete the account itself.
                    DatabaseManager.getAccountDatastore()
                            .find(Account.class)
                            .filter(Filters.eq("id", target.getId()))
                            .delete();
                });
    }

    public static <T> Stream<T> getByGameClass(Class<T> classType) {
        return DatabaseManager.getGameDatastore().find(classType).stream();
    }

    @Deprecated(forRemoval = true)
    public static List<Player> getAllPlayers() {
        return DatabaseManager.getGameDatastore().find(Player.class).stream().toList();
    }

    public static Player getPlayerByUid(int id) {
        return DatabaseManager.getGameDatastore()
                .find(Player.class)
                .filter(Filters.eq("_id", id))
                .first();
    }

    public static int getPlayerDataVersionByUid(int uid) {
        Player player = DatabaseManager.getGameDatastore()
            .find(Player.class)
            .filter(Filters.eq("_id", uid))
            .iterator(new FindOptions()
                .projection().include("dataVersion")
                .limit(1))
            .toList().getFirst();
        return player.getDataVersion(); // 返回字段值或null
    }

    @Deprecated
    public static Player getPlayerByAccount(Account account) {
        return DatabaseManager.getGameDatastore()
                .find(Player.class)
                .filter(Filters.eq("accountId", account.getId()))
                .first();
    }

    public static Player getPlayerByAccount(Account account, Class<? extends Player> playerClass) {
        return DatabaseManager.getGameDatastore()
                .find(playerClass)
                .filter(Filters.eq("accountId", account.getId()))
                .first();
    }

    /**
     * Use {@link DatabaseHelper#getPlayerByAccount(Account, Class)} for creating a real player. This
     * method is used for fetching the player's data.
     *
     * @param accountId The account's ID.
     * @return The player.
     */
    public static Player getPlayerByAccount(String accountId) {
        return DatabaseManager.getGameDatastore()
                .find(Player.class)
                .filter(Filters.eq("accountId", accountId))
                .first();
    }

    public static boolean checkIfPlayerExists(int uid) {
        return DatabaseManager.getGameDatastore()
                        .find(Player.class)
                        .filter(Filters.eq("_id", uid))
                        .count()
                > 0;
    }

    public static synchronized void generatePlayerUid(Player character, int reservedId) {
        // Check if reserved id
        int id;
        if (reservedId > 0 && !checkIfPlayerExists(reservedId)) {
            id = reservedId;
            character.setUid(id);
        } else {
            do {
                id = DatabaseManager.getNextId(character);
            } while (checkIfPlayerExists(id));
            character.setUid(id);
        }

        // Save to database
        DatabaseHelper.saveAccountAsync(character);
    }

    public static synchronized int getNextPlayerId(int reservedId) {
        // Check if reserved id
        int id;
        if (reservedId > 0 && !checkIfPlayerExists(reservedId)) {
            id = reservedId;
        } else {
            do {
                id = DatabaseManager.getNextId(Player.class);
            } while (checkIfPlayerExists(id));
        }
        return id;
    }

    public static void savePlayer(Player character) {
        DatabaseHelper.saveGameAsync(character);
    }

    public static void saveAvatar(Avatar avatar) {
        DatabaseHelper.saveGameAsync(avatar);
    }

    /**
     * Fetches all avatars of a player.
     *
     * @param player The player.
     * @return The list of avatars.
     */
    public static List<Avatar> getAvatars(Player player) {
        return DatabaseManager.getGameDatastore()
                .find(Avatar.class)
                .filter(Filters.eq("ownerId", player.getUid()))
                .stream()
                .toList();
    }

    public static void saveItem(GameItem item) {
        DatabaseHelper.saveGameAsync(item);
    }

    public static void deleteItem(GameItem item) {
        DatabaseHelper.asyncOperation(() -> DatabaseManager.getGameDatastore().delete(item));
    }

    /**
     * Fetches all items of a player.
     *
     * @param player The player.
     * @return The list of items.
     */
    public static List<GameItem> getInventoryItems(Player player) {
        return DatabaseManager.getGameDatastore()
                .find(GameItem.class)
                .filter(Filters.eq("ownerId", player.getUid()))
                .stream()
                .toList();
    }

    public static List<Friendship> getFriends(Player player) {
        return DatabaseManager.getGameDatastore()
                .find(Friendship.class)
                .filter(Filters.eq("ownerId", player.getUid()))
                .stream()
                .toList();
    }

    public static List<Friendship> getReverseFriends(Player player) {
        return DatabaseManager.getGameDatastore()
                .find(Friendship.class)
                .filter(Filters.eq("friendId", player.getUid()))
                .stream()
                .toList();
    }

    public static void saveFriendship(Friendship friendship) {
        DatabaseHelper.saveGameAsync(friendship);
    }

    public static void deleteFriendship(Friendship friendship) {
        DatabaseHelper.asyncOperation(() -> DatabaseManager.getGameDatastore().delete(friendship));
    }

    public static Friendship getReverseFriendship(Friendship friendship) {
        return DatabaseManager.getGameDatastore()
                .find(Friendship.class)
                .filter(
                        Filters.and(
                                Filters.eq("ownerId", friendship.getFriendId()),
                                Filters.eq("friendId", friendship.getOwnerId())))
                .first();
    }

    public static List<GachaRecord> getGachaRecords(int ownerId, int page, int gachaType) {
        return getGachaRecords(ownerId, page, gachaType, 10);
    }

    public static List<GachaRecord> getGachaRecords(
            int ownerId, int page, int gachaType, int pageSize) {
        return DatabaseManager.getGameDatastore()
                .find(GachaRecord.class)
                .filter(Filters.eq("ownerId", ownerId), Filters.eq("gachaType", gachaType))
                .iterator(
                        new FindOptions()
                                .sort(Sort.descending("transactionDate"))
                                .skip(pageSize * page)
                                .limit(pageSize))
                .toList();
    }

    public static long getGachaRecordsMaxPage(int ownerId, int page, int gachaType) {
        return getGachaRecordsMaxPage(ownerId, page, gachaType, 10);
    }

    public static long getGachaRecordsMaxPage(int ownerId, int page, int gachaType, int pageSize) {
        long count =
                DatabaseManager.getGameDatastore()
                        .find(GachaRecord.class)
                        .filter(Filters.eq("ownerId", ownerId), Filters.eq("gachaType", gachaType))
                        .count();
        return count / 10 + (count % 10 > 0 ? 1 : 0);
    }

    public static void saveGachaRecord(GachaRecord gachaRecord) {
        DatabaseHelper.saveGameAsync(gachaRecord);
    }

    public static List<Mail> getAllMail(Player player) {
        return DatabaseManager.getGameDatastore()
                .find(Mail.class)
                .filter(Filters.eq("ownerUid", player.getUid()))
                .stream()
                .toList();
    }

    public static void saveMail(Mail mail) {
        DatabaseHelper.saveGameAsync(mail);
    }

    public static void deleteMail(Mail mail) {
        DatabaseHelper.asyncOperation(() -> DatabaseManager.getGameDatastore().delete(mail));
    }

    public static List<GameMainQuest> getAllQuests(Player player) {
        return DatabaseManager.getGameDatastore()
                .find(GameMainQuest.class)
                .filter(Filters.eq("ownerUid", player.getUid()))
                .stream()
                .toList();
    }

    public static void saveQuest(GameMainQuest quest) {
        try {
            DatabaseHelper.saveGameAsync(quest);
        } catch(Exception exception){
            Grasscutter.getLogger().error("Failed to saveQuest",exception);
        }
    }

    public static void deleteQuest(GameMainQuest quest) {
        DatabaseHelper.asyncOperation(() -> DatabaseManager.getGameDatastore().delete(quest));
    }

    public static GameHome getHomeByUid(int id) {
        return DatabaseManager.getGameDatastore()
                .find(GameHome.class)
                .filter(Filters.eq("ownerUid", id))
                .first();
    }

    public static void saveHome(GameHome gameHome) {
        DatabaseHelper.saveGameAsync(gameHome);
    }

    public static BattlePassManager loadBattlePass(Player player) {
        BattlePassManager manager =
                DatabaseManager.getGameDatastore()
                        .find(BattlePassManager.class)
                        .filter(Filters.eq("ownerUid", player.getUid()))
                        .first();
        if (manager == null) {
            manager = new BattlePassManager(player);
            manager.save();
        } else {
            manager.setPlayer(player);
        }
        return manager;
    }

    public static void saveBattlePass(BattlePassManager manager) {
        DatabaseHelper.saveGameAsync(manager);
    }

    public static PlayerActivityData getPlayerActivityData(int uid, int activityId) {
        return DatabaseManager.getGameDatastore()
                .find(PlayerActivityData.class)
                .filter(Filters.and(Filters.eq("uid", uid), Filters.eq("activityId", activityId)))
                .first();
    }

    public static void savePlayerActivityData(PlayerActivityData playerActivityData) {
        DatabaseHelper.saveGameAsync(playerActivityData);
    }

    public static MusicGameBeatmap getMusicGameBeatmap(long musicShareId) {
        return DatabaseManager.getGameDatastore()
                .find(MusicGameBeatmap.class)
                .filter(Filters.eq("musicShareId", musicShareId))
                .first();
    }

    public static void saveMusicGameBeatmap(MusicGameBeatmap musicGameBeatmap) {
        DatabaseHelper.saveGameAsync(musicGameBeatmap);
    }

    @Nullable public static Achievements getAchievementData(int uid) {
        try {
            return DatabaseManager.getGameDatastore()
                    .find(Achievements.class)
                    .filter(Filters.and(Filters.eq("uid", uid)))
                    .first();
        } catch (IllegalArgumentException e) {
            Grasscutter.getLogger()
                    .debug("Error occurred while getting uid " + uid + "'s achievement data", e);
            DatabaseManager.getGameDatabase().getCollection("achievements").deleteMany(eq("uid", uid));
            return null;
        }
    }

    public static void saveAchievementData(Achievements achievements) {
        DatabaseHelper.saveGameAsync(achievements);
    }

    public static void saveGroupInstance(SceneGroupInstance instance) {
        DatabaseHelper.saveGameAsync(instance);
    }

    public static SceneGroupInstance loadGroupInstance(int groupId, Player owner) {
        return DatabaseManager.getGameDatastore()
                .find(SceneGroupInstance.class)
                .filter(Filters.and(Filters.eq("ownerUid", owner.getUid()), Filters.eq("groupId", groupId)))
                .first();
    }

    public static void saveSceneBuild(SceneBuild sb) {
        DatabaseHelper.saveGameAsync(sb);
    }

    public static void deleteSceneBuild(SceneBuild sb) {
        DatabaseHelper.asyncOperation(() -> DatabaseManager.getGameDatastore().delete(sb));
    }

    /**
     * 返回玩家所有在大世界的建筑（尘歌壶功能除外）
     *
     * @param player 这个玩家.
     * @return 建筑列表.
     */
    public static List<SceneBuild> getPlayerSceneBuild(Player player) {
        return DatabaseManager.getGameDatastore()
            .find(SceneBuild.class)
            .filter(Filters.eq("ownerUid", player.getUid()))
            .stream()
            .toList();
    }

    public static void saveGlobalOnlinePlayer(GlobalOnlinePlayer gop) {
        DatabaseHelper.saveGameAsync(gop);
    }

    public static void deleteGlobalOnlinePlayer(GlobalOnlinePlayer gop) {
        DatabaseHelper.asyncOperation(() -> DatabaseManager.getGameDatastore().delete(gop));
    }

    public static List<GlobalOnlinePlayer> getGlobalOnlinePlayers() {
        return DatabaseManager.getGameDatastore()
            .find(GlobalOnlinePlayer.class)
            .stream()
            .toList();
    }

    public static GlobalOnlinePlayer getGlobalOnlinePlayerByUid(int uid) {
        return DatabaseManager.getGameDatastore()
            .find(GlobalOnlinePlayer.class)
            .filter(Filters.eq("_id", uid))
            .first();
    }
}
