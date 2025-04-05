package emu.grasscutter.server.http.api;

import emu.grasscutter.GameConstants;
import emu.grasscutter.game.player.GlobalOnlinePlayer;
import emu.grasscutter.server.http.Router;
import io.javalin.Javalin;
import io.javalin.http.Context;

import static emu.grasscutter.config.Configuration.ACCOUNT;

public class ApiHandler implements Router {
    public static final int
        ERROR_RET_CODE = -1,    // 异常状态码
        SUCCESS_RET_CODE = 0;   // 正常状态码

    @Override
    public void applyRoutes(Javalin javalin) {
        javalin.get("/api/help", ServerStatusHandler::getAllApiName);
        javalin.get("/api/status", ServerStatusHandler::serverStatusV2);
        javalin.get("/status/server", ServerStatusHandler::serverStatusV1);   //为了兼容以前的软件
        javalin.get("/api/playerInfo/{uid}", PlayerInfoHandler::playerInfo);
        javalin.get("/api/list/playersInfo", PlayerInfoListHandler::playerInfoList);
    }
}

