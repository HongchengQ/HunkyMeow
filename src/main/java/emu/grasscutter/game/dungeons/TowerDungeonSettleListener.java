package emu.grasscutter.game.dungeons;

import emu.grasscutter.game.dungeons.dungeon_results.BaseDungeonResult;
import emu.grasscutter.game.dungeons.dungeon_results.BaseDungeonResult.DungeonEndReason;
import emu.grasscutter.game.dungeons.dungeon_results.TowerResult;
import emu.grasscutter.server.packet.send.*;

public class TowerDungeonSettleListener implements DungeonSettleListener {

    @Override
    public void onDungeonSettle(DungeonManager dungeonManager, DungeonEndReason endReason) {
        var scene = dungeonManager.getScene();

        var dungeonData = dungeonManager.getDungeonData();
        if (scene.getLoadedGroups().stream()
                .anyMatch(
                        g -> {
                            var variables = scene.getScriptManager().getVariables(g.id);
                            return variables != null
                                    && variables.containsKey("stage")
                                    && variables.get("stage") == 1;
                        })) {
            return;
        }

        var towerManager = scene.getPlayers().get(0).getTowerManager();
        var stars = towerManager.getCurLevelStars();

        if (endReason == DungeonEndReason.COMPLETED) {
            // Update star record only when challenge completes successfully.
            towerManager.notifyCurLevelRecordChangeWhenDone(stars);
            scene.broadcastPacket(
                    new PacketTowerFloorRecordChangeNotify(
                            towerManager.getCurrentFloorId(), stars, towerManager.canEnterScheduleFloor()));
        }

        var challenge = scene.getChallenge();
        var finishedTime = challenge != null ? challenge.getFinishedTime() : 0;
        var dungeonStats =
                new DungeonEndStats(scene.getKilledMonsterCount(), finishedTime, 0, endReason);
        var result =
                endReason == DungeonEndReason.COMPLETED
                        ? new TowerResult(dungeonData, dungeonStats, towerManager, challenge, stars)
                        : new BaseDungeonResult(dungeonData, dungeonStats);

        scene.broadcastPacket(new PacketDungeonSettleNotify(result));
    }
}
