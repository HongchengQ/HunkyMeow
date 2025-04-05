package emu.grasscutter.game.world;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.player.Player;
import lombok.Getter;
import lombok.Setter;
import org.bson.types.ObjectId;

import java.util.Collection;


@Getter
@Entity(value = "sceneBuild", useDiscriminator = false)
public class SceneBuild {
    // 持久化到数据库
    @Id ObjectId id;            // 主id 字段随机 所有对象的主id都在这个范围里
    int selfIncrementId;        // 副id 字段自增 每个玩家都从1开始计数
    int ownerUid;               // 玩家 uid
    int gadgetId;               // Gadgat Id
    Position positionPos;       // 坐标
    Position positionRot;       // 朝向
    int sceneId;                // scene id

    public SceneBuild(){}

    // 在添加建筑时调用
    public SceneBuild(Player player, SpawnParameters param) {
        this.selfIncrementId = player.getSceneBuilds().getMaxKey() + 1;
        this.ownerUid = player.getUid();
        this.gadgetId = param.id;
        this.positionPos = param.pos;
        this.positionRot = param.rot;
        this.sceneId = param.scene.getId();

        createGadget(player, this);
        player.getSceneBuilds().addSceneBuild(this);
    }

    // 生成建筑 在添加建筑和生成时调用
    public void createGadget(Player targetPlayer, SceneBuild sb) {
        if (targetPlayer.getScene().getId() != sb.sceneId){
            Grasscutter.getLogger().error("sceneId与玩家当前id不同");
            return;
        } else if (sb.ownerUid != targetPlayer.getUid()) {
            Grasscutter.getLogger().error("提交的uid与玩家当前uid不同");
            return;
        }

        EntityGadget entity = new EntityGadget(targetPlayer.getScene(), sb.gadgetId, sb.positionPos, sb.positionRot);
        targetPlayer.getScene().addEntity(entity);  // 在大世界创建建筑
    }

    public void createAllGadget(Player player){
        Collection<SceneBuild> allSceneBuilds = player.getSceneBuilds().getAllSceneBuilds();
        for (SceneBuild build : allSceneBuilds) {
            if(build == null) continue;
            // 获取到数据库里的每一个建筑信息 进行建造
            this.createGadget(player, build);
        }
    }

    @Setter
    public static class SpawnParameters {
        public int id;
        public int amount = 1;  // 生成数量
        public int blockId = -1;
        public int groupId = -1;
        public int configId = -1;
        public int state = -1;
        public int hp = -1;
        public int atk = -1;
        public int def = -1;
        public Position pos = null;
        public Position rot = null;
        public Scene scene = null;
    }

    // 保存至数据库
    public void save() {
        DatabaseHelper.saveSceneBuild(this);
    }

    public void delete() {
        DatabaseHelper.deleteSceneBuild(this);
    }
}
