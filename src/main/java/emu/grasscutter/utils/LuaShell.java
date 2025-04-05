package emu.grasscutter.utils;

import emu.grasscutter.BuildConfig;
import emu.grasscutter.GameConstants;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketWindSeedClientNotify;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

public class LuaShell {
    // main 启动时调用
    public static void addLoginLuaShell() {
        Thread.startVirtualThread(LuaShell::getLoginLuaShell);
    }

    public static void getLoginLuaShell() {
        byte[] luaFile;
        try {
            // jar 相对路径
            luaFile = Files.readAllBytes(Paths.get(".").toAbsolutePath().normalize().resolve("lua/login.luac"));
        } catch (IOException e) {
            // 异常时 使用默认
            luaFile = luaShell;
        }

        // 如果文件不符合要求或等于默认值 返回默认 luaShell
        if (Arrays.equals(luaFile, new byte[0]) || luaFile.length < 6 || luaFile == luaShell) {
            updateLuaShellWithGameVersion(GameConstants.VERSION);
            updateLuaShellWithGitHash(BuildConfig.GIT_HASH);
            return;
        }

        // 更新默认值
        luaShell = luaFile;
    }

    // 通用 lua 发送
    public static void sendLuaShell(GameSession session, byte[] Shell) {
        session.send(new PacketWindSeedClientNotify(Shell));
    }

    // 发送登录 luaShell 一般用于美化 uid
    public static void sendLoginLuaShell(GameSession session) {
        sendLuaShell(session, luaShell);
    }

    /**
     * 更新 luaShell 中从索引 340 - 1 到 344 的内容为 gameVersion
     * @param gameVersion 长度为小于 5 的 gameVersion 字符串
     */
    public static void updateLuaShellWithGameVersion(String gameVersion) {
        if (gameVersion == null) return;

        // 长度不足 5 补 0
        if (gameVersion.length() < 5){
            gameVersion += "00000";
        }

        // 将 gameVersion 转换为字节数组
        byte[] gameVersionBytes = gameVersion.getBytes();

        // 更新 luaShell 中从索引 340 - 1 到 344 的内容
        System.arraycopy(gameVersionBytes, 0, luaShell, 340 - 1, 5);
    }

    /**
     * 更新 luaShell 中从索引 346 - 1 到 353 的内容为 GIT_HASH
     * @param gitHash 长度为 9 的 GIT_HASH 字符串
     */
    public static void updateLuaShellWithGitHash(String gitHash) {
        if (gitHash == null) {
            Grasscutter.getLogger().error("GIT_HASH 为空");
            return;
        }

        // 长度不足 9 补 0
        if (gitHash.length() < 9) {
            gitHash += "000000000";
        }

        // 将 GIT_HASH 转换为字节数组
        byte[] gitHashBytes = gitHash.getBytes();

        // 更新 luaShell 中从索引 346 - 1 到 354 的内容
        System.arraycopy(gitHashBytes, 0, luaShell, 346 - 1, 9);
    }

    // 获取 luac 源文件 (项目文件内置 res)
    public static byte[] getInternalLuaShellFile() {
        return FileUtils.readResource("/lua/login.luac");
    }


    /*
    uidobj = CS.UnityEngine.GameObject.Find("/BetaWatermarkCanvas(Clone)/Panel/TxtUID"):GetComponent("Text");
    uid = uidobj.text;
    uid = uid:gsub("UID:", "<color=#5cffb8>BanXiaCore_0.0.0_000000000</color> <color=#c9fde6>|</color>")
    uidobj.text = "<color=#5cffb8>"..uid.."</color>"
*/
    static byte[] luaShell = {  // 默认 luaShell
        27, 76, 117, 97, 83, 1, 25, -109, 13, 10, 26, 10, 4, 4, 8, 8, 120, 86, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 40, 119,
        64, 1, 27, 64, 67, 58, 92, 66, 92, 80, 83, 92, 108, 117, 97, 92, 98, 117, 105, 108, 100, 92, 117, 105, 100, 46,
        108, 117, 97, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 4, 26, 0, 0, 0, 36, 64, 64, 0, 41, -128, 64, 0, 41, -64, 64, 0, 41,
        0, 65, 0, 86, 64, 1, 0, 44, -128, 0, 1, 29, -128, 65, 0, -106, -64, 1, 0, 44, -128, -128, 1, 34, 0, 0, -128, 36,
        0, 64, 0, 41, 64, 66, 0, 34, 0, 0, -124, 36, 0, 66, 0, 29, -128, 66, 0, -106, -64, 2, 0, -42, 0, 3, 0, 44, -128,
        0, 2, 34, 0, 0, -124, 36, 0, 64, 0, 86, 64, 3, 0, -92, 0, 66, 0, -42, -128, 3, 0, 80, -64, -128, 0, 31, 64, -128,
        -124, 25, 0, -128, 0, 15, 0, 0, 0, 4, 7, 117, 105, 100, 111, 98, 106, 4, 3, 67, 83, 4, 12, 85, 110, 105, 116, 121,
        69, 110, 103, 105, 110, 101, 4, 11, 71, 97, 109, 101, 79, 98, 106, 101, 99, 116, 4, 5, 70, 105, 110, 100, 4, 41,
        47, 66, 101, 116, 97, 87, 97, 116, 101, 114, 109, 97, 114, 107, 67, 97, 110, 118, 97, 115, 40, 67, 108, 111, 110,
        101, 41, 47, 80, 97, 110, 101, 108, 47, 84, 120, 116, 85, 73, 68, 4, 13, 71, 101, 116, 67, 111, 109, 112, 111, 110,
        101, 110, 116, 4, 5, 84, 101, 120, 116, 4, 4, 117, 105, 100, 4, 5, 116, 101, 120, 116, 4, 5, 103, 115, 117, 98,
        4, 5, 85, 73, 68, 58, 20, 75, 60, 99, 111, 108, 111, 114, 61, 35, 53, 99, 102, 102, 98, 56, 62,
        66, 97, 110, 88, 105, 97, 67, 111, 114, 101, 95,    // 位置329~338+1          BanXiaCore_
        48, 46, 48, 46, 48, 95,                             // 位置340~344+1 游戏版本号 0.0.0_
        48, 48, 48, 48, 48, 48, 48, 48, 48,                 // 位置346~353   GIT_HASH 000000000
        60, 47, 99, 111, 108, 111, 114, 62, 32, 60, 99, 111, 108, 111, 114, 61, 35, 99, 57, 102, 100, 101, 54, 62, 124,
        60, 47, 99, 111, 108, 111, 114, 62, 4, 16, 60, 99, 111, 108, 111, 114, 61, 35, 53, 99, 102, 102, 98, 56, 62, 4,
        9, 60, 47, 99, 111, 108, 111, 114, 62, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 26, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0,
        0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 2, 0, 0, 0, 2, 0, 0,
        0, 2, 0, 0, 0, 3, 0, 0, 0, 3, 0, 0, 0, 3, 0, 0, 0, 3, 0, 0, 0, 3, 0, 0, 0, 3, 0, 0, 0, 4, 0, 0, 0, 4, 0, 0, 0,
        4, 0, 0, 0, 4, 0, 0, 0, 4, 0, 0, 0, 4, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 5, 95, 69, 78, 86
    };
}
