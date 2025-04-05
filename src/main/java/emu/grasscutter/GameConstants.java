package emu.grasscutter;

import emu.grasscutter.game.world.Position;
import emu.grasscutter.utils.Utils;
import emu.grasscutter.utils.objects.SparseSet;
import static emu.grasscutter.config.Configuration.SERVER;

import java.util.Arrays;

public final class GameConstants {
    public static String VERSION = SERVER.gameVersion;
    public static int[] VERSION_PARTS;
    public static boolean DEBUG = false;

    public static final int DEFAULT_TEAMS = 4;
    public static final int MAX_TEAMS = 10;
    public static final int MAIN_CHARACTER_MALE = 10000005;
    public static final int MAIN_CHARACTER_FEMALE = 10000007;
    public static final Position START_POSITION = new Position(2747, 194, -1719);
    public static final int MAX_FRIENDS = 60;
    public static final int MAX_FRIEND_REQUESTS = 50;
    public static final int SERVER_CONSOLE_UID = 99; // The UID of the server console's "player".
    public static final int BATTLE_PASS_MAX_LEVEL = 50;
    public static final int BATTLE_PASS_POINT_PER_LEVEL = 1000;
    public static final int BATTLE_PASS_POINT_PER_WEEK = 10000;
    public static final int BATTLE_PASS_LEVEL_PRICE = 150;
    public static final int BATTLE_PASS_CURRENT_INDEX = 2;

    static {
        String[] versionParts = VERSION.split("\\.");
        if (versionParts.length != 3) {
            throw new IllegalArgumentException("版本字符串格式不正确: " + VERSION);
        }
        try {
            VERSION_PARTS = new int[]{
                Integer.parseInt(versionParts[0]),
                Integer.parseInt(versionParts[1]),
                Integer.parseInt(versionParts[2])
            };
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("版本字符串包含非数字字符: " + VERSION, e);
        }
    }

    // Default entity ability hashes.
    public static final String[] DEFAULT_ABILITY_STRINGS = {
        "Avatar_DefaultAbility_VisionReplaceDieInvincible", //角色死亡导致切换，下一个上场的角色短暂无敌
        "Avatar_DefaultAbility_AvartarInShaderChange",
        "Avatar_SprintBS_Invincible",                       // 冲刺时的无敌帧
        "Avatar_Freeze_Duration_Reducer",                   // 角色固定对冰冻效果时间减免（试做）
        "Avatar_Attack_ReviveEnergy",                       // 角色普攻与重击回能( 表现方面的配置需要等回能表现统一优化)
        "Avatar_Component_Initializer",                     // 3.0钩爪用
        "Avatar_FallAnthem_Achievement_Listener",
        "GrapplingHookSkill_Ability",
        "Avatar_PlayerBoy_DiveStamina_Reduction",
        "Ability_Avatar_Dive_SealEcho",
        "Absorb_SealEcho_Bullet_01",
        "Absorb_SealEcho_Bullet_02",
        "Ability_Avatar_Dive_CrabShield",
        "ActivityAbility_Absorb_Shoot",
        "SceneAbility_DiveVolume"
    };
    public static final String[] DEFAULT_TEAM_ABILITY_STRINGS = {
            "Ability_Avatar_Dive_Team"
    };
    public static final SparseSet ILLEGAL_WEAPONS = new SparseSet("""
        10000-10008, 11411, 11506-11508, 12505, 12506, 12508, 12509,
        13503, 13506, 14411, 14503, 14505, 14508, 15504-15506
        """);
    public static final SparseSet ILLEGAL_RELICS = new SparseSet("""
        20001, 23300-23340, 23383-23385, 78310-78554, 99310-99554
        """);
    public static final SparseSet ILLEGAL_ITEMS = new SparseSet("""
        100086, 100087, 100100-101000, 101106-101110, 101306, 101500-104000,
        105001, 105004, 106000-107000, 107011, 108000, 109000-110000,
        115000-130000, 200200-200899, 220050, 220054
        """);
    public static final int[] DEFAULT_ABILITY_HASHES =
            Arrays.stream(DEFAULT_ABILITY_STRINGS).mapToInt(Utils::abilityHash).toArray();
    public static final int DEFAULT_ABILITY_NAME = Utils.abilityHash("Default");
}
