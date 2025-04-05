package emu.grasscutter.config;

import ch.qos.logback.classic.Level;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.utils.*;
import lombok.NoArgsConstructor;

import java.util.*;

import static emu.grasscutter.Grasscutter.*;

/**
 * *when your JVM fails*
 */
public class ConfigContainer {
    /*
     * Configuration changes:
     * Version  5 - 'questing' has been changed from a boolean
     *              to a container of options ('questOptions').
     *              This field will be removed in future versions.
     * Version  6 - 'questing' has been fully replaced with 'questOptions'.
     *              The field for 'legacyResources' has been removed.
     * Version  7 - 'regionKey' is being added for authentication
     *              with the new dispatch server.
     * Version  8 - 'server' is being added for enforcing handbook server
     *              addresses.
     * Version  9 - 'limits' was added for handbook requests.
     * Version 10 - 'trialCostumes' was added for enabling costumes
     *              on trial avatars.
     * Version 11 - 'server.fastRequire' was added for disabling the new
     *              Lua script require system if performance is a concern.
     * Version 12 - 'http.startImmediately' was added to control whether the
     *              HTTP server should start immediately.
     * Version 13 - 'game.useUniquePacketKey' was added to control whether the
     *              encryption key used for packets is a constant or randomly generated.
     * Version 14 - 'game.timeout' was added to control the UDP client timeout.
     */
    private static int version() {
        return 14;
    }

    /**
     * Attempts to update the server's existing configuration.
     */
    public static void updateConfig() {
        try { // Check if the server is using a legacy config.
            var configObject = JsonUtils.loadToClass(Grasscutter.configFile.toPath(), JsonObject.class);
            if (!configObject.has("version")) {
                Grasscutter.getLogger().info("Updating legacy config...");
                Grasscutter.saveConfig(null);
            }
        } catch (Exception ignored) { }

        var existing = config.version;
        var latest = version();

        if (existing == latest)
            return;

        // Create a new configuration instance.
        var updated = new ConfigContainer();
        // Update all configuration fields.
        var fields = ConfigContainer.class.getDeclaredFields();
        Arrays.stream(fields).forEach(field -> {
            try {
                field.set(updated, field.get(config));
            } catch (Exception exception) {
                Grasscutter.getLogger().error("Failed to update a configuration field.", exception);
            }
        }); updated.version = version();

        try { // Save configuration and reload.
            Grasscutter.saveConfig(updated);
            Grasscutter.loadConfig();
        } catch (Exception exception) {
            Grasscutter.getLogger().warn("Failed to save the updated configuration.", exception);
        }
    }

    public Structure folderStructure = new Structure();
    public Database databaseInfo = new Database();
    public Language language = new Language();
    public Account account = new Account();
    public Server server = new Server();

    // DO NOT. TOUCH. THE VERSION NUMBER.
    public int version = version();

    /* Option containers. */

    public static class Database {
        public DataStore server = new DataStore();
        public DataStore game = new DataStore();

        public static class DataStore {
            public String connectionUri = "mongodb://localhost:27017";
            public String collection = "grasscutter";
        }
    }

    public static class Structure {
        public String resources = "./resources/";
        public String data = "./data/";
        public String packets = "./packets/";
        public String scripts = "resources:Scripts/";
        public String plugins = "./plugins/";
        public String cache = "./cache/";

        // UNUSED (potentially added later?)
        // public String dumps = "./dumps/";
    }

    public static class Server {
        public boolean enableHotUpdate = true;
        public String gameVersion = "4.0.0";
        public boolean isDevServer = false;

        /* 当 cpu 负载过高时是否自动关闭程序 */
        public boolean cpuLoadMonitorEnhancement = true;
        public double cpuLoadThreshold = 70.0;  // cpu 占用率阈值
        public byte highCpuCountThreshold = 5;  // cpu 占用超过阈值次数上限

        public Set<Integer> debugWhitelist = Set.of();
        public Set<Integer> debugBlacklist = Set.of();
        public ServerRunMode runMode = ServerRunMode.HYBRID;
        public boolean logCommands = true;

        /**
         * 如果启用，'require' Lua 函数会将脚本的编译变体加载到上下文中。（更快;效果不佳）
         * 如果禁用，则所有 'require' 调用都将替换为引用脚本的源。（更慢;效果更好）
         */
        public boolean fastRequire = false;

        public HTTP http = new HTTP();
        public Game game = new Game();

        public Dispatch dispatch = new Dispatch();
        public DebugMode debugMode = new DebugMode();
    }

    public static class Language {
        public Locale language = Locale.getDefault();
        public Locale fallback = Locale.US;
        public String document = "EN";
    }

    public static class Account {
        public boolean autoCreate = false;                  // 自动创建账户
        public boolean EXPERIMENTAL_RealPassword = false;   // 使用真实密码
        public boolean useIntegrationPassword = true;       // 使用一体化密码
        public String[] defaultPermissions = {"*"};         // 默认权限
        public int maxPlayer = -1;                          // 可承载最大玩家 -1为无限
        public int loginMaxConnNum = 2;                     // 每秒登录并发最大值
        public int ipBlackListTimeWindow = 600000;          // 黑名单ip检测时间窗口 1000为1秒
        public int ipBlackListCount = 5;                    // 单ip在时间窗口内最大连接次数
    }

    /* Server options. */

    public static class HTTP {
        /* 这会在游戏服务器之前启动 HTTP 服务器。 */
        public boolean startImmediately = true;

        public String bindAddress = "0.0.0.0";
        public int bindPort = 1145;

        /* 这是 URL 中使用的地址。 */
        public String accessAddress = "127.0.0.1";
        /* 这是 URL 中使用的端口。 */
        public int accessPort = 0;

        public Encryption encryption = new Encryption();
        public Policies policies = new Policies();
        public Files files = new Files();
    }

    public static class Game {
        public String bindAddress = "0.0.0.0";
        public int bindPort = 22102;

        /* 这是默认 region 中使用的地址。 */
        public String accessAddress = "127.0.0.1";
        /* 这是默认 region 中使用的端口。 */
        public int accessPort = 0;

        /* 节点名称 用于同步全局在线玩家 */
        public String nodeRegion = "01";

        /* 启用此选项将为每个玩家生成唯一的数据包加密密钥。 */
        public boolean useUniquePacketKey = true;

        /* 使用安迪补丁要 false */
        public boolean useXorEncryption = true;

        /* 将为玩家加载特定范围内的实体 */
        public int loadEntitiesForPlayerRange = 300;
        /* 开启大世界lua脚本. */
        public boolean enableScriptInBigWorld = true;
        /* 在打开大世界时使用Spawns.json */
        public boolean enableScriptInSpawnsJson = true;

        public boolean enableConsole = true;

        /*用于控制是否默认解锁所有地图*/
        public boolean defaultUnlockAllMap = true;

        /*用于控制是否默认解锁所有开放状态*/
        public boolean enabledOpenStateAllMap = true;

        /* Kcp 内部工作间隔（毫秒） */
        public int kcpInterval = 20;
        /* Time to wait (in seconds) before terminating a connection. */
        public long timeout = 30;

        /* 控制是否应在控制台中记录数据包 */
        public ServerDebugMode logPackets = ServerDebugMode.NONE;
        /* 在控制台中显示数据包 payload 或不显示（在任何情况下，payload 都显示在加密视图中） */
        public boolean isShowPacketPayload = false;
        /* 显示烦人的循环数据包或不显示 */
        public boolean isShowLoopPackets = false;

        /* 玩家每次进入场景都重新初始化缓存 */
        // 这简直是纯2b选项 作者设计这个选项的时候就没想到玩家进入场景时会阻塞主线程长达一分半吗？
        public boolean cacheSceneEntitiesEveryRun = false;

        /* 每次程序启动时重新初始化所有场景缓存 确保 lua 新增 scene 的 group 时都能正确更新 */
        public boolean initAllSceneGridEveryRun = true;

        public GameOptions gameOptions = new GameOptions();
        public JoinOptions joinOptions = new JoinOptions();
        public ConsoleAccount serverAccount = new ConsoleAccount();

        public VisionOptions[] visionOptions = new VisionOptions[] {
            new VisionOptions("VISION_LEVEL_NORMAL"         , 80    , 20),
            new VisionOptions("VISION_LEVEL_LITTLE_REMOTE"  , 16    , 40),
            new VisionOptions("VISION_LEVEL_REMOTE"         , 1000  , 250),
            new VisionOptions("VISION_LEVEL_SUPER"          , 4000  , 1000),
            new VisionOptions("VISION_LEVEL_NEARBY"         , 40    , 20),
            new VisionOptions("VISION_LEVEL_SUPER_NEARBY"   , 20    , 20)
        };
    }

    /* Data containers. */

    public static class Dispatch {
        /* An array of servers. */
        public List<Region> regions = List.of();

        /* The URL used to make HTTP requests to the dispatch server. */
        public String dispatchUrl = "ws://127.0.0.1:1111";
        /* A unique key used for encryption. */
        public byte[] encryptionKey = Crypto.createSessionKey(32);
        /* A unique key used for authentication. */
        public String dispatchKey = Utils.base64Encode(
            Crypto.createSessionKey(32));

        public String defaultName = "Grasscutter";

        /* Controls whether http requests should be logged in console or not */
        public ServerDebugMode logRequests = ServerDebugMode.NONE;
    }

    /* Debug options container, used when jar launch argument is -debug | -debugall and override default values
     *  (see StartupArguments.enableDebug) */
    public static class DebugMode {
        /* Log level of the main server code (works only with -debug arg) */
        public Level serverLoggerLevel = Level.DEBUG;

        /* Log level of the third-party services (works only with -debug arg):
           javalin, quartz, reflections, jetty, mongodb.driver */
        public Level servicesLoggersLevel = Level.INFO;

        /* Controls whether packets should be logged in console or not */
        public ServerDebugMode logPackets = ServerDebugMode.ALL;

        /* Show packet payload in console or no (in any case the payload is shown in encrypted view) */
        public boolean isShowPacketPayload = false;

        /* Show annoying loop packets or no */
        public boolean isShowLoopPackets = false;

        /* Controls whether http requests should be logged in console or not */
        public ServerDebugMode logRequests = ServerDebugMode.ALL;
    }

    public static class Encryption {
        public boolean useEncryption = false;
        /* Should 'https' be appended to URLs? */
        public boolean useInRouting = true;
        public String keystore = "./keystore.p12";
        public String keystorePassword = "123456";
    }

    public static class Policies {
        public Policies.CORS cors = new Policies.CORS();

        public static class CORS {
            public boolean enabled = true;
            public String[] allowedOrigins = new String[]{"*"};
        }
    }

    public static class GameOptions {
        public InventoryLimits inventoryLimits = new InventoryLimits();
        public AvatarLimits avatarLimits = new AvatarLimits();
        public static RecvPacketOptions recvPacketOptions = new RecvPacketOptions();
        public int sceneEntityLimit = 4000; // 场景实体上限. TODO: Implement.

        public boolean watchGachaConfig = true;
        public boolean enableShopItems = true;
        public boolean staminaUsage = true;
        public boolean energyUsage = true;
        public boolean fishhookTeleport = true; // 鱼钩传送
        public boolean trialCostumes = false;   // 服装自动装备 不管有没有

        @SerializedName(value = "questing", alternate = "questOptions")
        public Questing questing = new Questing();
        public ResinOptions resinOptions = new ResinOptions();
        public Rates rates = new Rates();

        public HandbookOptions handbook = new HandbookOptions();

        public static class RecvPacketOptions {
            public int recvPacketCheckIntervalTime = 5; // 检查客户端发包间隔
            public int recvPacketMaxFreq = 2000;        // 检查客户端发包频率
        }

        public static class InventoryLimits {
            public int weapons = 100000;
            public int relics = 100000;
            public int materials = 100000;
            public int furniture = 100000;
            public int all = 800000;
        }

        public static class AvatarLimits {
            public int singlePlayerTeam = 4;
            public int multiplayerTeam = 4;
        }

        public static class Rates {
            public float adventureExp = 1.0f;
            public float mora = 1.0f;
            public float leyLines = 1.0f;
        }

        public static class ResinOptions {
            public boolean resinUsage = false;
            public int cap = 160;
            public int rechargeTime = 480;
        }

        public static class Questing {
            /* Should questing behavior be used? */
            public boolean enabled = false;
            public boolean enabledBornQuest = true;
        }

        public static class HandbookOptions {
            public boolean enable = false;
            public boolean allowCommands = true;

            public Limits limits = new Limits();
            public Server server = new Server();

            public static class Limits {
                /* Are rate limits checked? */
                public boolean enabled = false;
                /* The time for limits to expire. */
                public int interval = 3;

                /* The maximum amount of normal requests. */
                public int maxRequests = 10;
                /* The maximum amount of entities to be spawned in one request. */
                public int maxEntities = 25;
            }

            public static class Server {
                /* Are the server settings sent to the handbook? */
                public boolean enforced = false;
                /* The default server address for the handbook's authentication. */
                public String address = "127.0.0.1";
                /* The default server port for the handbook's authentication. */
                public int port = 1145;
                /* Should the defaults be enforced? */
                public boolean canChange = true;
            }
        }
    }

    public static class VisionOptions {
        public String name;
        public int visionRange;
        public int gridWidth;

        public VisionOptions(String name, int visionRange, int gridWidth) {
            this.name = name;
            this.visionRange = visionRange;
            this.gridWidth = gridWidth;
        }
    }

    public static class JoinOptions {
        public int[] welcomeEmotes = {2007, 1002, 4010};
        public String welcomeMessage = "Welcome to a Grasscutter server.";
        public JoinOptions.Mail welcomeMail = new JoinOptions.Mail();

        public static class Mail {
            public String title = "Welcome to Grasscutter!";
            public String content = """
                    Hi there!\r
                    First of all, welcome to Grasscutter. If you have any issues, please let us know so that Lawnmower can help you! \r
                    \r
                    Check out our:\r
                    <type="browser" text="Discord" href="https://discord.gg/T5vZU6UyeG"/>
                    """;
            public String sender = "Lawnmower";
            public emu.grasscutter.game.mail.Mail.MailItem[] items = {
                new emu.grasscutter.game.mail.Mail.MailItem(13509, 1, 1),
                new emu.grasscutter.game.mail.Mail.MailItem(201, 99999, 1)
            };
        }
    }

    public static class ConsoleAccount {
        public int avatarId = 10000007;
        public int nameCardId = 210001;
        public int adventureRank = 1;
        public int worldLevel = 0;

        public String nickName = "Server";
        public String signature = "Welcome to Grasscutter!";
    }

    public static class Files {
        public String indexFile = "./index.html";
        public String errorFile = "./404.html";
    }

    /* Objects. */

    @NoArgsConstructor
    public static class Region {
        public String Name = "os_usa";
        public String Title = "Grasscutter";
        public String Ip = "127.0.0.1";
        public int Port = 22102;

        public Region(
            String name, String title,
            String address, int port
        ) {
            this.Name = name;
            this.Title = title;
            this.Ip = address;
            this.Port  = port;
        }
    }
}
