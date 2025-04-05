package emu.grasscutter.command.commands;

import emu.grasscutter.command.*;
import emu.grasscutter.game.player.Player;
import lombok.val;

import java.util.List;
import java.util.Objects;

import static emu.grasscutter.config.Configuration.HTTP_ENCRYPTION;

@Command(
        label = "kick",
        aliases = {"restart"},
        permissionTargeted = "server.kick")
public final class KickCommand implements CommandHandler {

    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {

        if (args.isEmpty()) {
            sendUsageMessage(sender);
            return;
        }
        val Password = args.get(0);
        if (Objects.equals(Password, HTTP_ENCRYPTION.keystorePassword)) {
            if (sender != null) {
                CommandHandler.sendTranslatedMessage(
                    sender,
                    "commands.kick.player_kick_player",
                    sender.getUid(),
                    sender.getAccount().getUsername(),
                    targetPlayer.getUid(),
                    targetPlayer.getAccount().getUsername());

            } else {
                CommandHandler.sendTranslatedMessage(
                    sender,
                    "commands.kick.server_kick_player",
                    targetPlayer.getUid(),
                    targetPlayer.getAccount().getUsername());
            }
        } else {
            CommandHandler.sendMessage(sender, "密钥输入错误");
            return;
        }

        targetPlayer.getSession().close();
    }
}
