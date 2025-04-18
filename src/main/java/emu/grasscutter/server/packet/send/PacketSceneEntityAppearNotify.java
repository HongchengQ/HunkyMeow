package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.SceneEntityAppearNotifyOuterClass.SceneEntityAppearNotify;
import emu.grasscutter.net.proto.VisionTypeOuterClass.VisionType;
import java.util.Collection;
import java.util.Objects;

public class PacketSceneEntityAppearNotify extends BasePacket {

    public PacketSceneEntityAppearNotify(GameEntity entity) {
        super(PacketOpcodes.SceneEntityAppearNotify, true);

        SceneEntityAppearNotify.Builder proto =
                SceneEntityAppearNotify.newBuilder()
                        .setAppearType(VisionType.VisionType_VISION_BORN)
                        .addEntityList(entity.toProto());

        this.setData(proto.build());
    }

    public PacketSceneEntityAppearNotify(GameEntity entity, VisionType vision, int param) {
        super(PacketOpcodes.SceneEntityAppearNotify, true);

        SceneEntityAppearNotify.Builder proto =
                SceneEntityAppearNotify.newBuilder()
                        .setAppearType(vision)
                        .setParam(param)
                        .addEntityList(entity.toProto());

        this.setData(proto.build());
    }

    public PacketSceneEntityAppearNotify(Player player) {
        this(Objects.requireNonNull(player.getTeamManager().getCurrentAvatarEntity()));
    }

    public PacketSceneEntityAppearNotify(
            Collection<? extends GameEntity> entities, VisionType visionType) {
        super(PacketOpcodes.SceneEntityAppearNotify, true);

        SceneEntityAppearNotify.Builder proto =
                SceneEntityAppearNotify.newBuilder().setAppearType(visionType);

        entities.forEach(e -> proto.addEntityList(e.toProto()));

        this.setData(proto.build());
    }
}
