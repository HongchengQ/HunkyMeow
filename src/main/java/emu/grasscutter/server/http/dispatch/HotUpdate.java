package emu.grasscutter.server.http.dispatch;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import emu.grasscutter.GameConstants;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.proto.RegionInfoOuterClass.RegionInfo;
import emu.grasscutter.net.proto.ResVersionConfigOuterClass;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static emu.grasscutter.config.Configuration.GAME_INFO;
import static emu.grasscutter.config.Configuration.lr;

public class HotUpdate {
    public static RegionInfo getHotUpdate(String versionName) {
        // noLeak
        return null;
    }
}
