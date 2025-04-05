package emu.grasscutter.server.packet.recv;

import emu.grasscutter.GameConstants;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PlayerLoginReqOuterClass.PlayerLoginReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketPlayerLoginRsp;
import emu.grasscutter.server.packet.send.PacketWindSeedClientNotify;
import emu.grasscutter.utils.LuaShell;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

@Opcodes(PacketOpcodes.PlayerLoginReq) // Sends initial data packets
public class HandlerPlayerLoginReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        // Check
        if (session.getAccount() == null) {
            session.close();
            return;
        }

        // Parse request
        PlayerLoginReq req = PlayerLoginReq.parseFrom(payload);

        // Authenticate session
        if (!req.getToken().equals(session.getAccount().getToken())) {
            session.close();
            return;
        }

        // Load character from db
        Player player = session.getPlayer();

        // Show opening cutscene if player has no avatars
        if (player.getAvatars().getAvatarCount() == 0) {
            // Pick character (Disabled due to opcode issue)
            if (GAME_OPTIONS.questing.enabledBornQuest) {
                session.setState(GameSession.SessionState.PICKING_CHARACTER);
                session.send(new BasePacket(PacketOpcodes.DoSetPlayerBornDataNotify));
            } else {
                // Hardcode default mc and nickname
                int avatarId = GameConstants.MAIN_CHARACTER_FEMALE;
                int fatherSkillId = 704;
                int BornHeadImage = 2;

                /* 随机生成性别 */
                boolean isBoy = new Random().nextBoolean();
                if (isBoy) {    // 如果 true 就是哥哥
                    avatarId = GameConstants.MAIN_CHARACTER_MALE;
                    fatherSkillId = 504;
                    BornHeadImage = 1;
                }

                Avatar mainCharacter = new Avatar(avatarId);

                // 开局给主角解锁技能 这里在开启剧情的情况下直接给会导致主角能量条一直是满的
                // 但是有时候任务给主角元素会失效 所以还是要开启这个
                mainCharacter.setSkillDepotData(GameData.getAvatarSkillDepotDataMap().get(fatherSkillId));

                // Manually handle adding to team
                player.addAvatar(mainCharacter, false);
                player.setMainCharacterId(avatarId);
                player.setHeadImage(BornHeadImage);
                player
                    .getTeamManager()
                    .getCurrentSinglePlayerTeamInfo()
                    .getAvatars()
                    .add(mainCharacter.getAvatarId());
                player.save(); // TODO save player team in different object

                // Login done
                session.getPlayer().onLogin();
            }
        } else {
            // Login done
            session.getPlayer().onLogin();
        }

        LuaShell.sendLoginLuaShell(session);

        // Final packet to tell client logging in is done
        session.send(new PacketPlayerLoginRsp(session));
    }
}
