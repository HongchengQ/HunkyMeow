package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.WeaponAwakenReqOuterClass.WeaponAwakenReq;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.WeaponAwakenReq)
public class HandlerWeaponAwakenReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        WeaponAwakenReq req = WeaponAwakenReq.parseFrom(payload);

        if (req.getTargetWeaponGuid() == 0) {
            Grasscutter.getLogger().error("WeaponAwakenReq.getTargetWeaponGuid 发生异常 TargetWeaponGuid没有获取到");
            return;
        }

        // Weapon refinement
        for (Long ItemGuid : req.getItemGuidList()) {
            session.getServer().getInventorySystem()
                .refineWeapon(session.getPlayer(), req.getTargetWeaponGuid(), ItemGuid);
        }
    }
}
