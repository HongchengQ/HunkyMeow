package emu.grasscutter.database;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.GlobalOnlinePlayer;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.utils.objects.DatabaseObject;
import org.slf4j.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * Complicated manager of the MongoDB database.
 * Handles caching, data operations, and more.
 */
public interface Database {
    Logger logger = LoggerFactory.getLogger("Database");
    Map<Integer, List<DatabaseObject<?>>> playerDataMap = new ConcurrentHashMap<>();
    // 针对 players 集合增加数据版本号，每次回写自动+1, 用来防止因回写顺序引起的回档; k:uid v:版本
    Map<Integer, Integer> playerLatestVersion = new ConcurrentHashMap<>();

    /**
     * Queues an object to be saved.
     * 将需要定时存库的数据添加至集合
     * 如果不需要定时存库就马上进行保存
     *
     * @param object The object to save.
     */
    static void save(DatabaseObject<?> object) {
        if (object == null) return;

        // 是否应该立即保存此对象
        if (object.saveImmediately()) {
            // 版本号+1
            object.updateDataVersion();
            object.save();
            return;
        }

        int playerId = object.getObjectPlayerUid();
        if (playerId == 0) {
            logger.error("数据库列表尝试存入非法数据 uid为0");
            return;
        }

        // 检查版本号是否有效（新版本必须大于当前版本）
        if (!checkIsNewData(playerId, object)) {
            return; // 跳过保存
        }

        // 将数据添加至数据集合
        playerDataMap.computeIfAbsent(playerId, k -> new CopyOnWriteArrayList<>()).add(object);
    }

    // 检查版本是否合法
    static boolean checkIsNewData(int playerId, DatabaseObject<?> object) {
//        int currentVersion = getPlayerDataVersionByUid(playerId); // 这边对数据库读取压力太大了
        // 获取该玩家当前的最新版本号
        int currentVersion = playerLatestVersion.getOrDefault(playerId, 0);
        int newVersion = object.getDataVersion();

        // 检查版本号是否有效（新版本必须大于当前版本） 版本号小于0时代表不需要版本或者是已经循环过一次
        if (newVersion < currentVersion && newVersion > 0) {
            logger.warn("skip save {} for player_uid={}, data_version={}, because cur_data_version in queue is {}",
                object.getClass(), playerId, newVersion, currentVersion);
            return false;
        }

        return true;
    }

    // 更新版本集合中回写版本号
    static boolean updateDataVersion(int playerId, DatabaseObject<?> object) {
        int newVersion = object.updateDataVersion();
        if (newVersion != -1)
            playerLatestVersion.put(playerId, newVersion);

        return true;
    }

    /**
     * 执行所有延迟对象的批量保存。
     */
    static void saveAll() {
        // 收集所有玩家的数据
        ArrayList<Map.Entry<Integer, List<DatabaseObject<?>>>> entries =
            new ArrayList<>(playerDataMap.entrySet());
        playerDataMap.clear();
        for (Map.Entry<Integer, List<DatabaseObject<?>>> entry : entries) {
            // 拆分成每一个玩家对象去保存
            savePlayerData(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 定时延迟保存全服玩家数据
     * 单位毫秒
     * 真端是 180s
     */
    static void startSaveThread() {
        var timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                // 当运行模式是GAME_ONLY时不应该对全局操作 因为game服务器通常会同时运行好几个 把这个操作交给dispatch更好
                if (Grasscutter.getRunMode() != Grasscutter.ServerRunMode.GAME_ONLY)
                    GlobalOnlinePlayer.removeNotOnlinePlayers(true);

                Database.saveAll();
            }
        }, 0, 1000 * 180);
    }

    /**
     * 玩家下线时调用
     */
    static void handlePlayerOffline(Player player) {
        int playerId = player.getObjectPlayerUid();
        // 获取并移除指定玩家的所有待保存数据
        List<DatabaseObject<?>> playerObjects = playerDataMap.remove(playerId);
        playerLatestVersion.remove(playerId); // 清理版本号记录
        if (playerObjects != null && !playerObjects.isEmpty()) {
            // 保存该玩家的所有相关数据
            savePlayerData(playerId, playerObjects);
        }
    }

    /**
     * 向数据库写入
     */
    static void savePlayerData(int playerId, List<DatabaseObject<?>> objects) {
        // 引用默认线程池
        var executor = DatabaseHelper.getEventExecutor();

         var gameObjects = new ArrayList<>(objects.stream()
             .filter(DatabaseObject::isGameObject)
             .filter(o -> checkIsNewData(playerId, o))
             .filter(o -> updateDataVersion(playerId, o))
             .toList());
        var accountObjects = objects.stream()
            .filter(o -> !o.isGameObject())
            .toList();

        Player playerObj = null;

        // 确保只有一个player对象
        for (var o : gameObjects) {
            if (!(o instanceof Player)) continue;

            // 确保第一次写入
            if (playerObj == null) {
                playerObj = (Player) o;
            } else if (o.getDataVersion() >= playerObj.getDataVersion() || ((Player) o).getPlayerGameTime() >= playerObj.getPlayerGameTime()) {
                playerObj = (Player) o;
            }
        }

        gameObjects.removeIf(o -> o instanceof Player);

        if (playerObj != null)
            gameObjects.add(playerObj);

        if (Grasscutter.getRunMode() != Grasscutter.ServerRunMode.DISPATCH_ONLY && !gameObjects.isEmpty()) {
            executor.submit(() -> {
                try {
                    DatabaseManager.getGameDatastore().save(gameObjects);
                } catch (Exception e) {
                    logger.error("-1 保存玩家数据时发生错误", e);
                }
            });
        }

        if (Grasscutter.getRunMode() != Grasscutter.ServerRunMode.GAME_ONLY && !accountObjects.isEmpty()) {
            executor.submit(() -> {
                try {
                    DatabaseManager.getAccountDatastore().save(accountObjects);
                } catch (Exception e) {
                    logger.error("-2 保存玩家数据时发生错误", e);
                }
            });
        }
    }
}
