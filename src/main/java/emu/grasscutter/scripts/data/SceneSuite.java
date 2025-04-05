package emu.grasscutter.scripts.data;

import java.util.*;
import lombok.*;
import org.jetbrains.annotations.NotNull;

@ToString
@Setter
public class SceneSuite {
    // make it refer the default empty list to avoid NPE caused by some group

    // 我不知道为什么 triggers 会同时传入 String 和 Integer 两种类型导致程序崩溃
    // 所以只能用 Object 来存储了
    // 这里正常来说应该是 String
    public List<@NotNull Object> triggers = List.of();
    public List<Integer> monsters = List.of();
    public List<Integer> gadgets = List.of();
    public List<Integer> regions = List.of();
    public int rand_weight;

    public boolean ban_refresh = false;

    public transient List<SceneMonster> sceneMonsters = List.of();
    public transient List<SceneGadget> sceneGadgets = List.of();
    public transient List<SceneTrigger> sceneTriggers = List.of();
    public transient List<SceneRegion> sceneRegions = List.of();

    public void init(SceneGroup sceneGroup) {
        if (sceneGroup.monsters != null) {
            this.sceneMonsters =
                    new ArrayList<>(
                            this.monsters.stream()
                                    .filter(sceneGroup.monsters::containsKey)
                                    .map(sceneGroup.monsters::get)
                                    .toList());
        }

        if (sceneGroup.gadgets != null) {
            this.sceneGadgets =
                    new ArrayList<>(
                            this.gadgets.stream()
                                    .filter(sceneGroup.gadgets::containsKey)
                                    .map(sceneGroup.gadgets::get)
                                    .toList());
        }

        if (sceneGroup.triggers != null) {
            this.sceneTriggers = new ArrayList<>();
            for (@NotNull Object key : this.triggers) {
                // 确保 key 是 String 类型
                if (!(key instanceof String)) {
                    continue;
                }
                if (sceneGroup.triggers.containsKey(key)) {
                    this.sceneTriggers.add(sceneGroup.triggers.get(key));
                }
            }
        }

        if (sceneGroup.regions != null) {
            this.sceneRegions =
                    new ArrayList<>(
                            this.regions.stream()
                                    .filter(sceneGroup.regions::containsKey)
                                    .map(sceneGroup.regions::get)
                                    .toList());
        }
    }
}
