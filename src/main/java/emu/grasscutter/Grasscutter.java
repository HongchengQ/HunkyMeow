package emu.grasscutter;

import static emu.grasscutter.config.Configuration.SERVER;
import static emu.grasscutter.utils.lang.Language.translate;

import ch.qos.logback.classic.*;
import emu.grasscutter.auth.*;
import emu.grasscutter.command.*;
import emu.grasscutter.config.ConfigContainer;
import emu.grasscutter.config.ConfigHelper;
import emu.grasscutter.data.ResourceLoader;
import emu.grasscutter.database.*;
import emu.grasscutter.game.player.GlobalOnlinePlayer;
import emu.grasscutter.plugin.PluginManager;
import emu.grasscutter.plugin.api.ServerHelper;
import emu.grasscutter.server.dispatch.DispatchServer;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.http.HttpServer;
import emu.grasscutter.server.http.api.ApiHandler;
import emu.grasscutter.server.http.dispatch.*;
import emu.grasscutter.server.http.documentation.*;
import emu.grasscutter.server.http.handlers.*;
import emu.grasscutter.tools.Tools;
import emu.grasscutter.utils.*;
import emu.grasscutter.utils.lang.Language;
import io.netty.util.concurrent.FastThreadLocalThread;
import java.io.*;
import java.util.ArrayDeque;
import java.util.Calendar;
import java.util.Deque;
import java.util.concurrent.*;
import javax.annotation.Nullable;
import lombok.*;
import org.jetbrains.annotations.NotNull;
import org.jline.reader.*;
import org.jline.terminal.*;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;
import org.slf4j.LoggerFactory;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import emu.grasscutter.utils.MonitorServer;
import emu.grasscutter.scripts.SceneScriptManager;

public final class Grasscutter {
    public static final File configFile = new File("./config.json");
    @Getter private static final Logger logger = (Logger) LoggerFactory.getLogger(Grasscutter.class);

    public static final Reflections reflector;
    @Getter public static ConfigContainer config;

    @Getter @Setter private static Language language;
    @Getter @Setter private static String preferredLanguage;

    @Getter private static int currentDayOfWeek;
    @Setter private static ServerRunMode runModeOverride = null; // Config override for run mode
    @Setter private static boolean noConsole = false;

    @Getter private static HttpServer httpServer;
    @Getter private static GameServer gameServer;
    @Getter private static DispatchServer dispatchServer;
    @Getter private static PluginManager pluginManager;
    @Getter private static CommandMap commandMap;

    @Getter @Setter private static AuthenticationSystem authenticationSystem;
    @Getter @Setter private static PermissionHandler permissionHandler;

    private static LineReader consoleLineReader = null;

    static ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    static ScheduledExecutorService schedulerCPU = Executors.newSingleThreadScheduledExecutor();
    static ScheduledExecutorService schedulerDB = Executors.newSingleThreadScheduledExecutor();

    @Getter
    private static final ExecutorService threadPool =
            new ThreadPoolExecutor(
                    6,
                    8,
                    60,
                    TimeUnit.SECONDS,
                    new LinkedBlockingDeque<>(),
                    FastThreadLocalThread::new,
                    new ThreadPoolExecutor.AbortPolicy());

    static {
        // Declare logback configuration.
        System.setProperty("logback.configurationFile", "src/main/resources/logback.xml");

        // Disable the MongoDB logger.
        var mongoLogger = (Logger) LoggerFactory.getLogger("org.mongodb.driver");
        mongoLogger.setLevel(Level.OFF);

        // Configure the reflector.
        reflector = new Reflections(
            new ConfigurationBuilder()
                .forPackage("emu.grasscutter")
                .filterInputsBy(new FilterBuilder()
                    .includePackage("emu.grasscutter")
                    .excludePackage("emu.grasscutter.net.proto"))
        );

        // Load server configuration.
        Grasscutter.loadConfig();
        // Attempt to update configuration.
        ConfigContainer.updateConfig();

        Grasscutter.getLogger().info("Loading Grasscutter...");

        // Load translation files.
        Grasscutter.loadLanguage();

        // Check server structure.
        Utils.startupCheck();
    }

    public static void main(String[] args) throws Exception {
        Crypto.loadKeys(); // Load keys from buffers.

        // Parse start-up arguments.
        if (StartupArguments.parse(args)) {
            System.exit(0); // Exit early.
        }

        // Get the server run mode.
        var runMode = Grasscutter.getRunMode();

        // Create command map.
        commandMap = new CommandMap(true);

        // Initialize server.
        logger.info(translate("messages.status.starting"));
        logger.info(translate("messages.status.game_version", GameConstants.VERSION));
        logger.info(translate("messages.status.version", BuildConfig.VERSION, BuildConfig.GIT_HASH));

        // Initialize database.
        DatabaseManager.initialize();

        // Initialize the default systems.
        authenticationSystem = new DefaultAuthentication();
        permissionHandler = new DefaultPermissionHandler();

        // Create server instances.
        if (runMode == ServerRunMode.HYBRID || runMode == ServerRunMode.GAME_ONLY)
            Grasscutter.gameServer = new GameServer();
        if (runMode == ServerRunMode.HYBRID || runMode == ServerRunMode.DISPATCH_ONLY)
            Grasscutter.httpServer = new HttpServer();

        // Create a server hook instance with both servers.
        new ServerHelper(gameServer, httpServer);

        // Create plugin manager instance.
        pluginManager = new PluginManager();

        if (runMode != ServerRunMode.GAME_ONLY) {
            // Add HTTP routes after loading plugins.
            httpServer.addRouter(HttpServer.UnhandledRequestRouter.class);
            httpServer.addRouter(HttpServer.DefaultRequestRouter.class);
            httpServer.addRouter(RegisterHandler.registerRouter.class);
            httpServer.addRouter(RegionHandler.class);
            httpServer.addRouter(LogHandler.class);
            httpServer.addRouter(GenericHandler.class);
            httpServer.addRouter(ApiHandler.class);
            httpServer.addRouter(AnnouncementsHandler.class);
            httpServer.addRouter(AuthenticationHandler.class);
            httpServer.addRouter(GachaHandler.class);
            httpServer.addRouter(HandbookHandler.class);
            if (Grasscutter.getRunMode() == ServerRunMode.HYBRID)
                httpServer.addRouter(DocumentationServerHandler.class);
        }

        // Check if the HTTP server should start.
        var started = config.server.http.startImmediately;
        if (started) {
            Grasscutter.getLogger().info("HTTP server is starting...");
            Grasscutter.startDispatch();

            Grasscutter.getLogger().info("Game server is starting...");
        }

        // Load resources.
        if (runMode != ServerRunMode.DISPATCH_ONLY) {
            // Load all resources.
            Grasscutter.updateDayOfWeek();
            ResourceLoader.loadAll();

            // Generate handbooks.
            Tools.createGmHandbooks(false);
            // Generate gacha mappings.
            Tools.generateGachaMappings();
        }

        // Start servers.
        if (runMode == ServerRunMode.HYBRID) {
            if (!started) Grasscutter.startDispatch();
            gameServer.start();
        } else if (runMode == ServerRunMode.DISPATCH_ONLY) {
            if (!started) Grasscutter.startDispatch();
        } else if (runMode == ServerRunMode.GAME_ONLY) {
            gameServer.start();
        } else {
            logger.error(translate("messages.status.run_mode_error", runMode));
            logger.error(translate("messages.status.run_mode_help"));
            logger.error(translate("messages.status.shutdown"));
            System.exit(1);
        }

        // 线程死锁检测
        scheduledExecutorService.scheduleAtFixedRate(Grasscutter::deadlockDetector, 30, 10, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(scheduledExecutorService::shutdownNow)); // 确保在应用程序关闭时停止定时任务服务

        // cpu占用检测
        schedulerCPU.scheduleAtFixedRate(cpuLoadMonitor(schedulerCPU), 30, 1, TimeUnit.SECONDS); // 每秒检测一次cpu占用
        Runtime.getRuntime().addShutdownHook(new Thread(schedulerCPU::shutdownNow)); // 确保在应用程序关闭时停止定时任务服务

        // 数据库线程检测
        schedulerDB.scheduleAtFixedRate(Grasscutter::BDThreadPollMonitor, 0, 3, TimeUnit.MINUTES); // 每三分钟检测一次cpu占用
        Runtime.getRuntime().addShutdownHook(new Thread(schedulerDB::shutdownNow)); // 确保在应用程序关闭时停止定时任务服务

        GlobalOnlinePlayer.removeNotOnlinePlayers(false);

        // Enable all plugins.
        pluginManager.enablePlugins();

        // Hook into shutdown event.
        Runtime.getRuntime().addShutdownHook(new Thread(Grasscutter::onShutdown));

        // Start database heartbeat.
        Database.startSaveThread();

        if (gameServer != null) {
            // 添加 LoginLuaShell
            LuaShell.addLoginLuaShell();

            // 初始化场景缓存
            // 默认场景资源最多最耗时 所以要在启动时初始化 否则会在玩家进入游戏时阻塞主线程或玩家大世界没有资源
            SceneScriptManager.initAllSceneCaches();
        }

        // Open console.
        Grasscutter.startConsole();
    }

    /** CPU 占用率检测 */
    private static final double CPU_LOAD_THRESHOLD = SERVER.cpuLoadThreshold;           // cpu 占用率阈值
    private static final byte HIGH_CPU_COUNT_THRESHOLD = SERVER.highCpuCountThreshold;  // cpu 占用超过阈值次数上限
    private static final int MAX_CPU_RECORDS = 60;                                      // cpu 记录时长 单位: 秒
    @NotNull private static Runnable cpuLoadMonitor(ScheduledExecutorService scheduler) {
        Logger logger = Grasscutter.getLogger();
        Deque<Double> cpuLoads = new ArrayDeque<>(MAX_CPU_RECORDS);
        return () -> {
            MonitorServer monitorServer = new MonitorServer();
            Double cpuLoad = monitorServer.monitor().getProcessCpuLoadInfoDouble();

            // 更新 CPU 负载队列
            if (cpuLoads.size() >= MAX_CPU_RECORDS) {
                cpuLoads.pollFirst(); // 移除最早的记录
            }
            cpuLoads.addLast(cpuLoad); // 添加当前的记录

            // 检查队列中超过阈值的次数
            byte highCpuCount = 0;
            for (Double load : cpuLoads) {
                if (load > CPU_LOAD_THRESHOLD) {
                    highCpuCount++;
                }
            }

            if (highCpuCount >= HIGH_CPU_COUNT_THRESHOLD) {
                if (SERVER.cpuLoadMonitorEnhancement) {
                    Database.saveAll();
                    logger.error("检测到队列中至少有 {} 次 CPU 使用率超过 {}%，程序正在退出...", HIGH_CPU_COUNT_THRESHOLD, CPU_LOAD_THRESHOLD);
                    scheduler.shutdownNow(); // 终止调度器
                    System.exit(1); // 退出程序
                } else {
                    logger.error("检测到队列中至少有 {} 次 CPU 使用率超过 {}%，有程序崩溃风险 请注意...", HIGH_CPU_COUNT_THRESHOLD, CPU_LOAD_THRESHOLD);
                }
            }
        };
    }

    /** DB线程池状态检测 */
    public static void BDThreadPollMonitor() {
        // 获取所有线程池实例
        var defaultExecutor = (ThreadPoolExecutor) DatabaseHelper.getEventExecutor();
        var accountExecutor = (ThreadPoolExecutor) DatabaseHelper.getEventExecutorAccount();
        var itemExecutor = (ThreadPoolExecutor) DatabaseHelper.getEventExecutorItem();
        var groupExecutor = (ThreadPoolExecutor) DatabaseHelper.getEventExecutorGroup();

        // 格式化输出每个线程池的详细状态
        Grasscutter.getLogger().info("------------------------------ 线程池状态监控 ------------------------------");
        printThreadPoolDetails(defaultExecutor, "Default");
        printThreadPoolDetails(accountExecutor, "Account");
        printThreadPoolDetails(itemExecutor, "Item");
        printThreadPoolDetails(groupExecutor, "Group");
        Grasscutter.getLogger().info("--------------------------------------------------------------------------------");
    }

    private static void printThreadPoolDetails(ThreadPoolExecutor executor, String name) {
        BlockingQueue<Runnable> queue = executor.getQueue();
        int queueSize = queue.size();
        int activeCount = executor.getActiveCount();
        int corePoolSize = executor.getCorePoolSize();
        int maxPoolSize = executor.getMaximumPoolSize();
        long completedTasks = executor.getCompletedTaskCount();
        long totalTasks = executor.getTaskCount();

        Grasscutter.getLogger().info(
            """
                线程池 [{}] 状态：
                  - 活跃线程数：{} / 当前线程数：{} / 核心线程数：{} / 最大线程数：{}\s
                  - 排队任务量：{}\s
                  - 已完成任务数：{} / 总提交任务数：{}""",
            name,
            activeCount,
            executor.getPoolSize(),
            corePoolSize,
            maxPoolSize,
            queueSize,
            completedTasks,
            totalTasks
        );
    }

    /** 线程死锁检测 */
    public static void deadlockDetector() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        long[] deadlockedThreads = threadMXBean.findDeadlockedThreads();

        if (deadlockedThreads != null) {
            Database.saveAll();
            Grasscutter.getLogger().error("检测到死锁，涉及的线程ID如下：");
            for (long threadId : deadlockedThreads) {
                Grasscutter.getLogger().error("线程ID: {}", threadId);
            }
            // 如果直接退出程序 死锁的原因就没了
//            Grasscutter.getLogger().error("正在退出程序...");
//            System.exit(1);
        }
    }

    /** Server shutdown event. */
    private static void onShutdown() {
        // Save all data.
        Database.saveAll();

        // Disable all plugins.
        if (pluginManager != null) pluginManager.disablePlugins();
        // Shutdown the game server.
        if (gameServer != null) gameServer.onServerShutdown();

        try {
            // Wait for Grasscutter's thread pool to finish.
            var executor = Grasscutter.getThreadPool();
            executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }

            // Wait for database operations to finish.
            var dbExecutor = DatabaseHelper.getEventExecutor();
            dbExecutor.shutdown();
            if (!dbExecutor.awaitTermination(20, TimeUnit.SECONDS)) {
                dbExecutor.shutdownNow();
            }
        } catch (InterruptedException ignored) {
        }
    }

    /** Utility method for starting the: - SDK server - Dispatch server */
    public static void startDispatch() throws Exception {
        httpServer.start(); // Start the SDK/HTTP server.

        if (Grasscutter.getRunMode() == ServerRunMode.DISPATCH_ONLY) {
            dispatchServer = new DispatchServer("0.0.0.0", 1111); // Create the dispatch server.
            dispatchServer.start(); // Start the dispatch server.
        }
    }

    /*
     * Methods for the language system component.
     */

    public static void loadLanguage() {
        var locale = config.language.language;
        language = Language.getLanguage(Utils.getLanguageCode(locale));
    }

    /*
     * Methods for the configuration system component.
     */

    /** Attempts to load the configuration from a file. */
    public static void loadConfig() {
        ConfigHelper.createConfigHelp();

        // Check if config.json exists. If not, we generate a new config.
        if (!configFile.exists()) {
            getLogger().info("config.json could not be found. Generating a default configuration ...");
            config = new ConfigContainer();
            Grasscutter.saveConfig(config);
            return;
        }

        // If the file already exists, we attempt to load it.
        try {
            config = JsonUtils.loadToClass(configFile.toPath(), ConfigContainer.class);
        } catch (Exception exception) {
            getLogger()
                    .error(
                            "There was an error while trying to load the configuration from config.json. Please make sure that there are no syntax errors. If you want to start with a default configuration, delete your existing config.json.");
            System.exit(1);
        }
    }

    /**
     * Saves the provided server configuration.
     *
     * @param config The configuration to save, or null for a new one.
     */
    public static void saveConfig(@Nullable ConfigContainer config) {
        if (config == null) config = new ConfigContainer();

        try (FileWriter file = new FileWriter(configFile)) {
            file.write(JsonUtils.encode(config));
        } catch (IOException ignored) {
            logger.error("Unable to write to config file.");
        } catch (Exception e) {
            logger.error("Unable to save config file.", e);
        }
    }

    /*
     * Getters for the various server components.
     */

    public static Language getLanguage(String langCode) {
        return Language.getLanguage(langCode);
    }

    public static ServerRunMode getRunMode() {
        return Grasscutter.runModeOverride != null ? Grasscutter.runModeOverride : SERVER.runMode;
    }

    public static LineReader getConsole() {
        if (consoleLineReader == null) {
            Terminal terminal = null;
            try {
                terminal = TerminalBuilder.builder().jna(true).build();
            } catch (Exception e) {
                try {
                    // Fallback to a dumb jline terminal.
                    terminal = TerminalBuilder.builder().dumb(true).build();
                } catch (Exception ignored) {
                    // When dumb is true, build() never throws.
                }
            }

            consoleLineReader = LineReaderBuilder.builder().terminal(terminal).build();
        }

        return consoleLineReader;
    }

    /*
     * Utility methods.
     */

    public static void updateDayOfWeek() {
        Calendar calendar = Calendar.getInstance();
        Grasscutter.currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        logger.debug("Set day of week to {}", currentDayOfWeek);
    }

    public static void startConsole() {
        // Console should not start in dispatch only mode.
        if (Grasscutter.getRunMode() == ServerRunMode.DISPATCH_ONLY && Grasscutter.noConsole) {
            logger.info(translate("messages.dispatch.no_commands_error"));
            return;
        } else {
            logger.info(translate("messages.status.done"));
        }

        String input = null;
        var isLastInterrupted = false;
        while (config.server.game.enableConsole) {
            try {
                input = consoleLineReader.readLine("> ");
            } catch (UserInterruptException e) {
                if (!isLastInterrupted) {
                    isLastInterrupted = true;
                    logger.info("Press Ctrl-C again to shutdown.");
                    continue;
                } else {
                    Runtime.getRuntime().exit(0);
                }
            } catch (EndOfFileException e) {
                logger.info("EOF detected.");
                continue;
            } catch (IOError e) {
                logger.error("An IO error occurred while trying to read from console.", e);
                return;
            }

            isLastInterrupted = false;

            try {
                commandMap.invoke(null, null, input);
            } catch (Exception e) {
                logger.error(translate("messages.game.command_error"), e);
            }
        }
    }

    /*
     * Enums for the configuration.
     */

    public enum ServerRunMode {
        HYBRID,
        DISPATCH_ONLY,
        GAME_ONLY
    }

    public enum ServerDebugMode {
        ALL,
        MISSING,
        WHITELIST,
        BLACKLIST,
        NONE
    }
}
