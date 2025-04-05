package emu.grasscutter.command.commands;


import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.game.player.Player;

import java.util.List;
import java.util.Objects;

/**
 * 删除建筑指令
 * <p>
 * 建造指令：build
 */
@Command(
    label = "removeBuild",
    aliases = {"rb"},
    permission = "player.build",
    permissionTargeted = "player.build.others")
public class RemoveBuildCommand implements CommandHandler {
    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        boolean successfullyDeleted = true;

        switch (args.size()) {
            case 1 -> {
                String firstArge = args.get(0);

                if(Objects.equals(firstArge, "all")) {
                    targetPlayer.getSceneBuilds().removeAllSceneBuilds(targetPlayer);
                    CommandHandler.sendMessage(targetPlayer, "已删除所有建筑");
                } else {
                    targetPlayer.getSceneBuilds().removeSceneBuild(Integer.parseInt(firstArge));
                    CommandHandler.sendMessage(targetPlayer, "已尝试删除建筑" + firstArge);
                }
            }
            case 0 -> {
                targetPlayer.getSceneBuilds().removeLastSceneBuild(targetPlayer);
                CommandHandler.sendMessage(targetPlayer, "已删除上一个建造的建筑");
            }
        }

        if (!successfullyDeleted) return;

        kickPlayer(targetPlayer);   // 删除建筑物需要重新登录才能生效
    }

    public void kickPlayer(Player player) {
        CommandHandler.sendMessage(player, "需要重新登录生效 正在断开服务器连接等待重连中 如果等待重连时间过长可以手动退出游戏再连接");
        player.getSession().close();
    }
}
