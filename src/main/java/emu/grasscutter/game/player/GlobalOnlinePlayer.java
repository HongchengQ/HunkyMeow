package emu.grasscutter.game.player;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import dev.morphia.annotations.Indexed;
import emu.grasscutter.GameConstants;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.utils.Utils;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import static emu.grasscutter.config.Configuration.*;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

// 可以迁移到redis
@Entity(value = "globalOnlinePlayers", useDiscriminator = false)
public class GlobalOnlinePlayer {
    @Id @Indexed @Getter private int uid;
    long currentSecondsTime = 0L;   // 玩家上一次在线的时间戳 查询是否在线用 (单位秒)
    String nodeRegion;              // 当时更新的组 用于区分是不是当前GameServer组玩家 (因为历史原因 联机不能跨GameServer进程)

    public GlobalOnlinePlayer(Player player) {
       updatePlayerGlobalOnlineInfo(player);
       this.save(); // 立刻异步保存进数据库
    }

    public void updatePlayerGlobalOnlineInfo(Player player) {
        this.uid = player.getUid();
        this.currentSecondsTime = Utils.getCurrentSeconds();
        this.nodeRegion = GAME_INFO.nodeRegion;

        this.save(); // 立刻异步保存进数据库
    }

    /**
     * 检查玩家是否在线
     * 通过 gop
     */
    public static boolean checkIsOnline(GlobalOnlinePlayer gop) {
        return gop.currentSecondsTime >= Utils.getCurrentSeconds() - 12;
    }

    /**
     * 检查玩家是否在线
     * 通过 uid
     */
    public static boolean checkIsOnline(int uid) {
        GlobalOnlinePlayer gop = DatabaseHelper.getGlobalOnlinePlayerByUid(uid);
        return gop.currentSecondsTime >= Utils.getCurrentSeconds() - 12;
    }

    /**
     * 移除单个不在线的玩家
     * 通过 uid 寻找
     */
    public static void removeNotOnlinePlayer(int uid) {
        GlobalOnlinePlayer player = DatabaseHelper.getGlobalOnlinePlayerByUid(uid);
        if (player == null) return;
        DatabaseHelper.deleteGlobalOnlinePlayer(player);
        Grasscutter.getLogger().debug("已删除uid: {} 在全局在线数据库的信息", uid);
    }

    /**
     * 移除一组不在线的玩家
     */
    public static void removeNotOnlinePlayers(boolean isCheckAllNodes) {
        List<GlobalOnlinePlayer> allNodesPlayers = DatabaseHelper.getGlobalOnlinePlayers();
        // 获取所有节点不在线的玩家
        List<GlobalOnlinePlayer> allNodesNotOnlinePlayers = allNodesPlayers.stream()
            .filter(gop -> !checkIsOnline(gop))
            .toList();

        if (isCheckAllNodes) {
            // 对所有节点操作 (不应该频繁使用)
            allNodesNotOnlinePlayers.forEach(DatabaseHelper::deleteGlobalOnlinePlayer);
            Grasscutter.getLogger().debug("已删除全局节点中过时的在线玩家");
        } else {
            // 对当前节点操作
            allNodesNotOnlinePlayers.stream()
                .filter(gop -> !checkIsCurrNode(gop))
                .forEach(DatabaseHelper::deleteGlobalOnlinePlayer);
            Grasscutter.getLogger().debug("已删除当前节点中过时的在线玩家");
        }
    }

    /**
     *  检查是否在当前节点
     *  通过 uid 寻找
     */
    public static boolean checkIsCurrNode(int uid) {
        GlobalOnlinePlayer player = DatabaseHelper.getGlobalOnlinePlayerByUid(uid);
        return checkIsCurrNode(player);
    }

    /**
     *  检查是否在当前节点
     *  通过 gop 寻找
     */
    public static boolean checkIsCurrNode(GlobalOnlinePlayer gop) {
        return Objects.equals(gop.nodeRegion, GAME_INFO.nodeRegion);
    }

    /**
     *  返回所有节点在线的玩家 UID 列表
     */
    private static List<Integer> filterPlayers(Predicate<GlobalOnlinePlayer> filter) {
        return DatabaseHelper.getGlobalOnlinePlayers()
            .stream()
            .filter(filter.and(GlobalOnlinePlayer::checkIsOnline)) // 先过滤在线状态
            .map(GlobalOnlinePlayer::getUid)
            .collect(Collectors.toList());
    }

    /**
     * 获取所有不处于当前节点且在线的玩家 UID 列表
     */
    public static List<Integer> getOnlinePlayersNotInCurrentNode() {
        return filterPlayers(gop -> !checkIsCurrNode(gop));
    }

    /**
     * 获取所有节点在线的玩家 UID 列表
     */
    public static List<Integer> getAllOnlinePlayers() {
        return filterPlayers(gop -> true); // 不添加额外过滤条件
    }

    /**
     * 立刻异步保存进数据库
     */
    public void save() {
        if (this.uid == 0) return;
        DatabaseHelper.saveGlobalOnlinePlayer(this);
    }
}
