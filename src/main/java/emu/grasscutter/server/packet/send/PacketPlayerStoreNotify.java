package emu.grasscutter.server.packet.send;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ItemOuterClass.Item;
import emu.grasscutter.net.proto.PlayerStoreNotifyOuterClass.PlayerStoreNotify;
import emu.grasscutter.net.proto.StoreTypeOuterClass.StoreType;

public class PacketPlayerStoreNotify extends BasePacket {

    public PacketPlayerStoreNotify(Player player) {
        super(PacketOpcodes.PlayerStoreNotify);

        this.buildHeader(2);

        PlayerStoreNotify.Builder p =
                PlayerStoreNotify.newBuilder()
                        .setStoreType(StoreType.StoreType_STORE_PACK)
                        .setWeightLimit(GAME_OPTIONS.inventoryLimits.all);

        for (GameItem item : player.getInventory()) {
            Item itemProto = item.toProto();
            p.addItemList(itemProto);
        }

        this.setData(p.build());
    }
}
