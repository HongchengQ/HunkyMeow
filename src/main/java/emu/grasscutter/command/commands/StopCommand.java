package emu.grasscutter.command.commands;

import static emu.grasscutter.config.Configuration.HTTP_ENCRYPTION;
import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.*;
import emu.grasscutter.game.player.Player;
import lombok.val;

import java.util.List;
import java.util.Objects;

@Command(
        label = "stop",
        usage = {"<key>"},  // 用法: stop <keystorePassword> (keystorePassword是配置文件服务端证书那里的密码)
        aliases = {"shutdown"},
        permission = "server.stop",
        targetRequirement = Command.TargetRequirement.NONE)
public final class StopCommand implements CommandHandler {

    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        if (args.isEmpty()) {
            sendUsageMessage(sender);
            return;
        }
        final String Password = args.getFirst();
        if (Objects.equals(Password, HTTP_ENCRYPTION.keystorePassword)) {
            CommandHandler.sendMessage(null, translate("commands.stop.success"));
            if (Grasscutter.getGameServer() != null) {
                for (Player p : Grasscutter.getGameServer().getPlayers().values()) {
                    CommandHandler.sendMessage(p, translate(p, "commands.stop.success"));
                }
            }
            System.exit(1000);
        } else {
            CommandHandler.sendMessage(targetPlayer, "密钥输入错误");
        }
    }
}
