package emu.grasscutter.game.world;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.player.Player;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.Getter;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public class SceneBuildStorage {
    private final Int2ObjectMap<SceneBuild> sceneBuilds;
    private int maxKey = 0;

    public SceneBuildStorage() {
        this.sceneBuilds = new Int2ObjectOpenHashMap<>();
    }


    // 留着备用
    public void syncPlayerSceneBuildStorage(Player player){
        player.setSceneBuilds(this);
    }

    // 返回玩家建筑物数量
    public int getSceneBuildCount() {
        return this.sceneBuilds.size();
    }

    // 返回玩家所有建筑对象
    public Collection<SceneBuild> getAllSceneBuilds() {
        // 返回对象如果直接打印的话只会出现地址值
        return this.sceneBuilds.values();
    }

    // 返回建筑对象 通过key找value
    public SceneBuild getSceneBuild(int key) {
        return this.sceneBuilds.get(key);
    }

    // 返回玩家所有建筑物的编号
    public List<Integer> getAllSceneBuildSerials() {
        return this.getAllSceneBuilds().stream()
            .map(SceneBuild::getSelfIncrementId)
            .collect(Collectors.toList());
    }

    public void loadFromDatabase(Player player) {
        List<SceneBuild> sceneBuilds = DatabaseHelper.getPlayerSceneBuild(player);

        for (SceneBuild build : sceneBuilds) {
            if (build.getId() == null) {
                Grasscutter.getLogger().warn("SceneBuild ID 为 null，跳过加载: {}", build);
                continue;
            }

            // Add to SceneBuild storage
            this.sceneBuilds.put(build.getSelfIncrementId(), build);
            Grasscutter.getLogger().debug("加载 SceneBuild: {}", build);
        }
    }

    public void addSceneBuild(SceneBuild build) {
        this.sceneBuilds.put(build.getSelfIncrementId(), build);
        build.save();
    }

    public void removeSceneBuild(Player player, SceneBuild sceneBuild) {
        if (sceneBuild == null) {
            throw new IllegalArgumentException("SceneBuild cannot be null");
        }

        Integer id = sceneBuild.getSelfIncrementId();
        if (id == null || id == 0) {
            throw new IllegalArgumentException("SelfIncrementId cannot be null");
        }

        boolean removed = this.sceneBuilds.remove(id, sceneBuild);
        if (!removed) {
            // 日志记录或抛出自定义异常，根据业务需求选择合适的处理方式
            throw new IllegalStateException("Failed to remove SceneBuild with ID: " + id);
        }

        try {
            sceneBuild.delete();
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete SceneBuild with ID: " + id, e);
        }

//        try {
//            this.syncPlayerSceneBuildStorage(player);
//        } catch (Exception e) {
//            throw new RuntimeException("同步失败", e);
//        }
    }


    public void removeSceneBuild(int key) {
        SceneBuild sb = this.getSceneBuild(key);

        if (sb == null) {
            Grasscutter.getLogger().error("SceneBuild 对象为 null");
            return;
        }

        synchronized (this.sceneBuilds) {
            this.sceneBuilds.remove(key, sb);
            try {
                sb.delete();
            } catch (Exception e) {
                Grasscutter.getLogger().error("Failed to delete scene build: {}", e.getMessage());
            }
        }

    }

    public void removeLastSceneBuild(Player player) {
        SceneBuild lastSceneBuildObj = this.getLastSceneBuildObj();

        this.removeSceneBuild(player, lastSceneBuildObj);
    }

    public void removeAllSceneBuilds(Player player) {
        for (SceneBuild sb : this.getAllSceneBuilds()) {
            this.removeSceneBuild(player, sb);
        }
    }

    // 返回最后建造的建筑物的自增id 也就是最大的key
    public int getMaxKey() {
        int sceneBuildCount = this.getSceneBuildCount();

        switch (sceneBuildCount){
            case 0 -> {
                return 0;
            }
            case Integer.MAX_VALUE -> {
                Grasscutter.getLogger().error("玩家 {} 建筑编号超过int最大值", this.sceneBuilds.get(0).getOwnerUid());
                throw new IllegalStateException();
            }
        }

        for (int key : this.sceneBuilds.keySet()) {
            if (key > maxKey) {
                maxKey = key;
            }
        }
        return maxKey;
    }

    // 返回最后建造的建筑对象
    public SceneBuild getLastSceneBuildObj() {
        int lastKey = this.getMaxKey();

        if (lastKey == 0) return null;

        return this.getSceneBuild(lastKey);
    }
}
