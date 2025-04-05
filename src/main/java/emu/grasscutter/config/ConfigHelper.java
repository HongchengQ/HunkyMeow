package emu.grasscutter.config;

import emu.grasscutter.Grasscutter;

import java.io.FileWriter;
import java.io.IOException;

public class ConfigHelper {
    public static void createConfigHelp() {
        try (FileWriter fileWriter = new FileWriter("configHelp.jsonc")) {
            fileWriter.write(generateConfigHelp());
        } catch (IOException e) {
            Grasscutter.getLogger().error("{}", String.valueOf(e));
        }
    }

    private static String generateConfigHelp() {
        return """
            {
                // 文件夹结构
                "folderStructure": {
                    "resources": "./resources/",    // res 来自官方
                    "data": "./data/",              // gc 自己写的游戏数值控制
                    "packets": "./packets/",        // 一般情况下用不到
                    "scripts": "resources:Scripts/",// Scripts (lua文件 来自官方)
                    "plugins": "./plugins/",        // 插件
                    "cache": "./cache/"             // 缓存
                },

                // 数据库配置
                "databaseInfo": {
                    "server": {
                        "connectionUri": "mongodb://localhost:27017",
                        "collection": "grasscutter"
                    },
                    "game": {
                        "connectionUri": "mongodb://localhost:27017",
                        "collection": "grasscutter"
                    }
                },

                // 语言
                "language": {
                    "language": "zh_CN",
                    "fallback": "en_US",
                    "document": "EN"
                },

                // 账户
                "account": {
                    "autoCreate": true,                 // 自动创建账户 不用注册
                    "EXPERIMENTAL_RealPassword": false, // 使用真实密码
                    "useIntegrationPassword": true,     // 使用一体化密码
                    "defaultPermissions": ["*"],        // 默认权限
                    "maxPlayer": -1,                    // 可承载最大玩家 -1为无限
                    "loginMaxConnNum": 99999,           // 每秒登录并发最大值
                    "ipBlackListTimeWindow": 3000,      // 黑名单ip检测时间窗口 1000为1秒
                    "ipBlackListCount": 9999999         // 单ip在时间窗口内最大连接次数
                },

                // 服务器配置
                "server": {
                    "gameVersion": "4.0.0",             // 游戏版本
                    "isDevServer": false,               // 是否为开发环境
                    "cpuLoadMonitorEnhancement": true,  // 是否开启 cpu 占用率检测
                    "cpuLoadThreshold": 70.0,           // cpu 占用上报阈值
                    "highCpuCountThreshold": 5,         // 阈值安全超过次数
                    "debugWhitelist": [1,2],            // debug 白名单 cmdId
                    "debugBlacklist": [3,4],            // debug 黑名单 cmdId
                    "runMode": "HYBRID",                // 运行模式 HYBRID DISPATCH_ONLY GAME_ONLY  HYBRID:dispatch和gameserver同时开启
                    "logCommands": true,                // 日志显示指令使用
                    "fastRequire": false,               // 如果启用，'require' Lua 函数会将脚本的编译变体加载到上下文中。（更快;效果不佳） 如果禁用，则所有 'require' 调用都将替换为引用脚本的源。（更慢;效果更好）
                    "http": {
                        "startImmediately": true,           // 这会在游戏服务器之前启动 HTTP 服务器 推荐打开
                        "bindAddress": "0.0.0.0",           // 地址 默认即可
                        "bindPort": 1145,                   // 端口 改为自己的
                        "accessAddress": "192.168.0.114",   // 地址 改为自己的
                        "accessPort": 0,                    // 端口 默认即可
                        // ssl 加密 一般情况下不要开这个 容易出问题
                        "encryption": {
                            "useEncryption": false,
                            "useInRouting": false,
                            "keystore": "./keystore.p12",
                            "keystorePassword": "123456"
                        },
                        "policies": {
                            // 跨域
                            "cors": {
                                "enabled": true,
                                "allowedOrigins": ["*"]
                            }
                        },
                        "files": {
                            "indexFile": "./index.html",    // 浏览器打开你的地址加端口时显示的页面
                            "errorFile": "./404.html"
                        }
                    },
                    "game": {
                        "bindAddress": "0.0.0.0",           // 地址 默认即可
                        "bindPort": 22103,                  // 端口 改成你自己的
                        "accessAddress": "192.168.0.114",   // 地址 改成你自己的
                        "accessPort": 0,                    // 端口 默认即可
                        "nodeRegion": "01",                 // 节点名称 用于同步全局在线玩家 每新增一个GameServer都要避免此字段重复
                        "useUniquePacketKey": true,         // 启用此选项将为每个玩家生成唯一的数据包加密密钥。
                        "useXorEncryption": true,           // 使用安迪补丁要 false (不敢动)
                        "loadEntitiesForPlayerRange": 300,  // 将为玩家加载特定范围内的实体
                        "enableScriptInBigWorld": true,     // 开启大世界脚本(来自官方的lua)
                        "enableScriptInSpawnsJson": false,  // 在大世界脚本开启后此选项才有用：同时使用 data 文件夹内的 spawn.json 生成怪物
                        "enableConsole": true,              // 是否在控制台显示输出
                        "defaultUnlockAllMap": true,        // 默认解锁全图
                        "enabledOpenStateAllMap": true,     // 默认解锁全部 State
                        "kcpInterval": 1,                   // kcp 刷新间隔
                        "timeout": 30,                      // kcp 超时时间
                        "logPackets": "NONE",               // ALL MISSING WHITELIST BLACKLIST NONE ALL:显示所有收发包 NONE:不显示任何包
                        "isShowPacketPayload": false,       // 显示收发包内容
                        "isShowLoopPackets": false,         // 显示烦人的数据包
                        "cacheSceneEntitiesEveryRun": false,// 玩家每次进入场景都重新初始化缓存 这简直是纯2b选项 作者设计这个选项的时候就没想到玩家进入场景时会阻塞主线程长达一分半吗？
                        "initAllSceneGridEveryRun": false,  // 每次程序启动时重新初始化所有场景缓存 确保 lua 新增 scene 的 group 时都能正确更新
                        "gameOptions": {
                            // 玩家背包上限
                            "inventoryLimits": {
                                "weapons": 9999999,     // 武器
                                "relics": 9999999,      // 圣遗物
                                "materials": 9999999,   // 材料
                                "furniture": 9999999,   // 家具
                                "all": 9999999          // 所有
                            },
                            // 玩家队伍容量
                            "avatarLimits": {
                                "singlePlayerTeam": 4,  // 单人
                                "multiplayerTeam": 4    // 组队
                            },
                            "sceneEntityLimit": 4000,   // 场景实体上限
                            "watchGachaConfig": true,   // 卡池文件更改后马上生效
                            "enableShopItems": true,    // 开启商店物品
                            "staminaUsage": true,       // 启用冲刺体力条 关闭为无限
                            "energyUsage": true,        // 启用大招能量值 关闭为无限
                            "fishhookTeleport": true,   // 启用鱼钩传送
                            "trialCostumes": true,      // 服装自动装备 不管玩家有没有
                            // 任务 剧情
                            "questing": {
                                "enabled": false,           // 是否打开
                                "enabledBornQuest": false   // 是否加载开局浪费时间的那段CG
                            },
                            // 树脂选项
                            "resinOptions": {
                                "resinUsage": false,// 是否启用 关闭为无限
                                "cap": 200,         // 上限
                                "rechargeTime": 480 // 恢复时间
                            },
                            // 调控倍率
                            "rates": {
                                "adventureExp": 1.0,    // 经验
                                "mora": 1.0,            // 摩拉
                                "leyLines": 1.0         // 地脉
                            },
                            // 没什么用
                            "handbook": {
                                "enable": false,
                                "allowCommands": true,
                                "limits": {
                                    "enabled": false,
                                    "interval": 3,
                                    "maxRequests": 10,
                                    "maxEntities": 25
                                },
                                "server": {
                                    "enforced": false,
                                    "address": "192.168.0.114",
                                    "port": 1145,
                                    "canChange": true
                                }
                            }
                        },
                        // 玩家进入加载选项
                        "joinOptions": {
                            // 欢迎表情
                            "welcomeEmotes": [1001, 1002],
                            "welcomeMessage": "1",
                            // 注册后发的邮件
                            "welcomeMail": {
                                // 标题
                                "title": "Welcome to a LunaGC 4.6.0",
                                // 内容
                                "content": "Hi there!\\r\\nFirst of all, welcome to Grasscutter. If you have any issues, please let us know so that Lawnmower can help you! \\r\\n\\r\\nCheck out our:\\r\\n<type=\\"browser\\" text=\\"Discord\\" href=\\"https://discord.gg/T5vZU6UyeG\\"/>\\n",
                                // 发送者
                                "sender": "欢迎来到杀神帮 杀杀杀杀杀",
                                // 送的东西
                                "items": [
                                    {
                                        "itemId": 13509,
                                        "itemCount": 1,
                                        "itemLevel": 1
                                    },
                                    {
                                        "itemId": 201,
                                        "itemCount": 99999,
                                        "itemLevel": 1
                                    }
                                ]
                            }
                        },
                        // 服务器机器人设置
                        "serverAccount": {
                            "avatarId": 10000002,       // 头像
                            "nameCardId": 210082,       // 名片
                            "adventureRank": 5201314,   // 等级
                            "worldLevel": 520,          // 世界等级
                            "nickName": "<b><color=#F8C7FF>E</color><color=#EFC3FF>R</color><color=#E6C0FF>t</color><color=#DDBCFF>h</color><color=#D4B9FF>e</color><color=#CBB5FF>r</color><color=#C2B2FF>e</color><color=#B9AEFF>a</color><color=#B0ABFF>l</color></b>",
                            "signature": "欢迎来到杀神帮 杀杀杀杀杀"
                        },
                        // 视距
                        "visionOptions": [
                            {
                                "name": "VISION_LEVEL_NORMAL",
                                "visionRange": 80,
                                "gridWidth": 20
                            },
                            {
                                "name": "VISION_LEVEL_LITTLE_REMOTE",
                                "visionRange": 16,
                                "gridWidth": 40
                            },
                            {
                                "name": "VISION_LEVEL_REMOTE",
                                "visionRange": 1000,
                                "gridWidth": 250
                            },
                            {
                                "name": "VISION_LEVEL_SUPER",
                                "visionRange": 4000,
                                "gridWidth": 1000
                            },
                            {
                                "name": "VISION_LEVEL_NEARBY",
                                "visionRange": 40,
                                "gridWidth": 20
                            },
                            {
                                "name": "VISION_LEVEL_SUPER_NEARBY",
                                "visionRange": 20,
                                "gridWidth": 20
                            }
                        ]
                    },
                    "dispatch": {
                        "regions": [
                            /* 可写可不写 当分区数量大于等于2时 玩家在登录时就可以像国际服一样选择不同分区 */
                            // {
                            //   "Name": "os_usa",      // 玩家不可见
                            //   "Title": "USA",        // 玩家可见
                            //   "Ip": "192.168.0.114", // 玩家不可见
                            //   "Port": 22103          // 玩家不可见
                            // },
                            // {
                            //   "Name": "cn_gf01",
                            //   "Title": "天空岛",
                            //   "Ip": "192.168.0.115",
                            //   "Port": 22104
                            // }
                        ],
                        "dispatchUrl": "ws://192.168.0.114:1111",
                        "encryptionKey": "iGHkBenyKPqaGzW6OFxhok5/5YaAI9OKTmZuSeMP48s=",
                        "dispatchKey": "KkhoskaJ4RqkGHfPY2GwDehWXjvoSVvuGf5bOCPQt+I=",
                        // 默认给玩家展示的分区名字
                        "defaultName": "<b><color=#F8C7FF>E</color><color=#EFC3FF>R</color><color=#E6C0FF>t</color><color=#DDBCFF>h</color><color=#D4B9FF>e</color><color=#CBB5FF>r</color><color=#C2B2FF>e</color><color=#B9AEFF>a</color><color=#B0ABFF>l</color></b>",
                        "logRequests": "NONE"
                    },
                    "debugMode": {
                        "serverLoggerLevel": {
                            "levelInt": 10000,
                            "levelStr": "DEBUG"
                        },
                        "servicesLoggersLevel": {
                            "levelInt": 20000,
                            "levelStr": "INFO"
                        },
                        "logPackets": "ALL",
                        "isShowPacketPayload": true,
                        "isShowLoopPackets": true,
                        "logRequests": "ALL"
                    }
                },
                "version": 14
            }

            """;
    }
}
