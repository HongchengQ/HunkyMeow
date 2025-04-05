package emu.grasscutter.command.commands;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.*;
import emu.grasscutter.game.player.Player;
import java.util.List;
import java.util.Objects;

import static emu.grasscutter.config.Configuration.HTTP_ENCRYPTION;

@Command(
        label = "coop",
        usage = {"[<Password> <host UID>]"},
        permission = "server.coop",
        permissionTargeted = "server.coop.others")
public final class CoopCommand implements CommandHandler {

    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        Player host = sender;
        switch (args.size()) {
            case 0: // Summon target to self
                if (sender == null) { // Console doesn't have a self to summon to
                    sendUsageMessage(null);
                    return;
                }
                break;
            case 1:
                CommandHandler.sendMessage(sender, "请输入密钥");
                return;
            case 2: // Summon target to argument
                if (!Objects.equals(args.get(0), HTTP_ENCRYPTION.keystorePassword)) {
                    CommandHandler.sendMessage(sender, "密钥输入错误");
                    return;
                }
                try {
                    int hostId = Integer.parseInt(args.get(1));
                    host = Grasscutter.getGameServer().getPlayerByUid(hostId);
                    if (host == null) {
                        CommandHandler.sendTranslatedMessage(sender, "commands.execution.player_offline_error");
                        return;
                    }
                    break;
                } catch (NumberFormatException ignored) {
                    CommandHandler.sendTranslatedMessage(sender, "commands.generic.invalid.uid");
                    return;
                }
            default:
                sendUsageMessage(sender);
                return;
        }

        // There's no target==host check but this just places them in multiplayer in their own world
        // which seems fine.
        if (targetPlayer.isInMultiplayer()) {
            targetPlayer.getServer().getMultiplayerSystem().leaveCoop(targetPlayer);
        }
        host.getServer().getMultiplayerSystem().applyEnterMp(targetPlayer, host.getUid());
        targetPlayer
                .getServer()
                .getMultiplayerSystem()
                .applyEnterMpReply(host, targetPlayer.getUid(), true);
        CommandHandler.sendTranslatedMessage(
                sender, "commands.coop.success", targetPlayer.getNickname(), host.getNickname());
    }
}
