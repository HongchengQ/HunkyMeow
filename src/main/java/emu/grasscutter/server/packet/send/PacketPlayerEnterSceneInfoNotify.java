package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AbilitySyncStateInfoOuterClass.AbilitySyncStateInfo;
import emu.grasscutter.net.proto.AvatarEnterSceneInfoOuterClass.AvatarEnterSceneInfo;
import emu.grasscutter.net.proto.MPLevelEntityInfoOuterClass.MPLevelEntityInfo;
import emu.grasscutter.net.proto.PlayerEnterSceneInfoNotifyOuterClass.PlayerEnterSceneInfoNotify;
import emu.grasscutter.net.proto.TeamEnterSceneInfoOuterClass.TeamEnterSceneInfo;

import java.util.Objects;

public class PacketPlayerEnterSceneInfoNotify extends BasePacket {

    public PacketPlayerEnterSceneInfoNotify(Player player) {
        super(PacketOpcodes.PlayerEnterSceneInfoNotify);

        AbilitySyncStateInfo empty = AbilitySyncStateInfo.newBuilder().build();

        PlayerEnterSceneInfoNotify.Builder proto =
                PlayerEnterSceneInfoNotify.newBuilder()
                        .setCurAvatarEntityId(Objects.requireNonNull(player.getTeamManager().getCurrentAvatarEntity()).getId())
                        .setEnterSceneToken(player.getEnterSceneToken());

        proto.setTeamEnterInfo(
                TeamEnterSceneInfo.newBuilder()
                        .setTeamEntityId(player.getTeamManager().getEntity().getId()) // 150995833
                        .setTeamAbilityInfo(empty)
                        .setAbilityControlBlock(player.getTeamManager().getAbilityControlBlock()));
        proto.setMpLevelEntityInfo(
                MPLevelEntityInfo.newBuilder()
                        .setEntityId(player.getWorld().getLevelEntityId()) // 184550274
                        .setAuthorityPeerId(player.getWorld().getHostPeerId())
                        .setAbilityInfo(empty));

        for (EntityAvatar avatarEntity : player.getTeamManager().getActiveTeam()) {
            GameItem weapon = avatarEntity.getAvatar().getWeapon();
            long weaponGuid = weapon != null ? weapon.getGuid() : 0;

            AvatarEnterSceneInfo avatarInfo =
                    AvatarEnterSceneInfo.newBuilder()
                            .setAvatarGuid(avatarEntity.getAvatar().getGuid())
                            .setAvatarEntityId(avatarEntity.getId())
                            .setWeaponGuid(weaponGuid)
                            .setWeaponEntityId(avatarEntity.getWeaponEntityId())
                            .setAvatarAbilityInfo(empty)
                            .setWeaponAbilityInfo(empty)
                            .build();

            proto.addAvatarEnterInfo(avatarInfo);
        }

        this.setData(proto.build());
    }
}
