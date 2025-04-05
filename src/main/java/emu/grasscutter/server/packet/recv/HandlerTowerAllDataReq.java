package emu.grasscutter.server.packet.recv;

import emu.grasscutter.BuildConfig;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketAntiAddictNotify;
import emu.grasscutter.server.packet.send.PacketTowerAllDataRsp;

import java.util.Objects;

@Opcodes(PacketOpcodes.TowerAllDataReq)
public class HandlerTowerAllDataReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        // session.send(new PacketAntiAddictNotify(1, "<color=#FFC0FF>Welcome To Funina's stage!</color>"));

        Player player = session.getPlayer();    // 读取玩家数据

        /* 出生弹窗 */
        if (player.isNewAccountEnterGame) {
            String nickname = player.getNickname();
            String gender;

            /* 读取数据库判断主角性别 */
            if (player.getMainCharacterId() == 10000007) {
                gender = "女";
            } else if (player.getMainCharacterId() == 10000005) {
                gender = "男";
            } else {
                gender = "男娘";  // 在程序设计或数据库没有问题时不会走到这里
                Grasscutter.getLogger().warn("读取数据库判断主角性别失败");
            }

            /* 发送数据包 */
            session.getPlayer().sendPacket(
                new PacketAntiAddictNotify(
                    1, "我重生了，这一世我要把我失去的全都拿回来。上一世，我苦练0+0妮露，曾无数次跟别人对立，最终惨遭深渊和剧情背刺。"
                    + "在一群人的奸笑和嘲笑中我饮恨西北，再次醒来却发现自己毫发无伤，并且觉醒了系统。"
                    + "原来我重生了，耳边突然响起“叮 宿主名字:" + nickname + " 性别" + gender + " 已为您激活GM指令系统 您可以使用GM指令做任何事”"
                    + "这次，我会把我失去的和我应得的通通拿回来！"
                ));
            // 将 isNewAccountEnterGame 改为false 确保后续不再弹窗
            player.isNewAccountEnterGame = false;
        } else if (!Objects.equals(player.playerLastBuildConfigGitHash, BuildConfig.GIT_HASH)) { // 如果玩家进入游戏时的 GitHash 值与上次不一样
            session.getPlayer().sendPacket(
                new PacketAntiAddictNotify(1, "尊敬的玩家 在您消失的这段时间服务端已至少更新过一次 如果遇到困难欢迎进入官方社群寻找解决方案(*^▽^*)"));
        }
        player.playerLastBuildConfigGitHash = BuildConfig.GIT_HASH; // 更新玩家的 GitHash

        session.send(new PacketTowerAllDataRsp(session.getServer().getTowerSystem(), session.getPlayer().getTowerManager()));
    }
}
