package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.command.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.SceneBuild;
import emu.grasscutter.game.world.SceneBuild.SpawnParameters;

import java.util.List;
import java.util.Objects;


/**
 * 大世界建造指令
 * 参数:
 * 1: 建筑 id
 * 2:posx 3:posy 4:posz
 * 5:rotx 6:roty 7:rotz
 * 8: 是否持久化 （默认true）
 * <p>
 * 删除建筑指令：RemoveBuildCommand
 */
@Command(
    label = "build",
    aliases = {"b"},
    permission = "player.build",
    permissionTargeted = "player.build.others")
public class BuildCommand implements CommandHandler {
    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        SpawnParameters param = new SpawnParameters();

        boolean toDB = true;

        Position pos = new Position(targetPlayer.getPosition());
        Position rot = new Position(targetPlayer.getRotation());

        switch (args.size()) {
            // 是否存数据库
            case 8:
                toDB = Boolean.parseBoolean(args.get(7));

                // XYZ顺序方向
            case 7: {
                try {
                    rot.setX(CommandHelpers.parseRelative(args.get(4), rot.getX()));
                    rot.setY(CommandHelpers.parseRelative(args.get(5), rot.getY()));
                    rot.setZ(CommandHelpers.parseRelative(args.get(6), rot.getZ()));
                } catch (NumberFormatException ignored) {
                    CommandHandler.sendMessage(
                        sender, translate(sender, "commands.execution.argument_error"));
                }
            }

            // XYZ顺序位置
            case 4: {
                try {
                    pos = CommandHelpers.parsePosition(args.get(1), args.get(2), args.get(3), pos, rot);
                } catch (NumberFormatException ignored) {
                    CommandHandler.sendMessage(
                        sender, translate(sender, "commands.execution.argument_error"));
                }
            }

            // 非0值：建造物 id
            // 0: 查询上一个建造物的id
            case 1: {
                try {
                    if (!Objects.equals(args.get(0), "0")) {
                        // 参数不等于 0
                        param.id = Integer.parseInt(args.get(0));
                        break;
                    }

                    // 参数为 0 时
                    SceneBuild obj = targetPlayer.getSceneBuilds().getLastSceneBuildObj();
                    if (obj == null) {
                        CommandHandler.sendMessage(targetPlayer, "您没有建筑");
                        return;
                    }
                    CommandHandler.sendMessage(targetPlayer, "您的上一个建筑编号为：" + obj.getSelfIncrementId());
                    return;
                } catch (NumberFormatException ignored) {
                    CommandHandler.sendMessage(
                        sender, translate(sender, "commands.generic.invalid.entityId"));
                }
                break;
            }
            // 查询所有建筑编号
            case 0: {
                CommandHandler.sendMessage(targetPlayer, "您的所有建筑编号为：" +
                    targetPlayer.getSceneBuilds().getAllSceneBuildSerials());
                return;
            }
        }

        param.pos = pos;
        param.rot = rot;
        param.scene = targetPlayer.getScene();

        BuildGadget(param, targetPlayer, toDB);
    }

    private void BuildGadget(SpawnParameters param, Player player, boolean toDB) {
        GadgetData gadgetData = GameData.getGadgetDataMap().get(param.id);
        if (gadgetData == null) {
            CommandHandler.sendMessage(player, translate(player, "commands.generic.invalid.entityId"));
            return;
        }

        double maxRadius = Math.sqrt(0.2 / Math.PI);
        param.pos = GetRandomPositionInCircle(param.pos, maxRadius);

        String str = "正在生成 已保存至数据库 下次启动游戏将保留这个建筑";
        if (toDB)
            new SceneBuild(player, param);                               // 保存至数据库
        else
            str = "正在生成 您已选择不保存数据库 下次启动游戏将删除这个建筑";      // 这里用到的频率特别少 所以就不优化了

        // 发送通知
        CommandHandler.sendMessage(player, str);
        CommandHandler.sendMessage(player, "建筑已建造完成");
    }

    private Position GetRandomPositionInCircle(Position origin, double radius) {
        Position target = origin.clone();
        double angle = Math.random() * 360;
        double r = Math.sqrt(Math.random() * radius * radius);
        target.addX((float) (r * Math.cos(angle))).addZ((float) (r * Math.sin(angle)));
        return target;
    }
}
