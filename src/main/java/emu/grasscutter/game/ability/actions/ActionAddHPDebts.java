package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.ChangeHpDebtsOuterClass;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass;
import emu.grasscutter.server.packet.send.PacketEntityFightPropChangeReasonNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import emu.grasscutter.Grasscutter;

@AbilityAction(value = AbilityModifier.AbilityModifierAction.Type.AddHPDebts)
public final class ActionAddHPDebts extends AbilityActionHandler {
    @Override
    public boolean execute(Ability ability, AbilityModifier.AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        if (!(target instanceof EntityAvatar avatar)) {
            Grasscutter.getLogger().warn("[ActionAddHPDebts] Cannot add HP debt to non-avatar entity");
            return false;
        }

        float maxHp = avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        float debt = action.ratio.get(ability) * maxHp;
        float currentDebt = avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);

        // 计算新的「生命之契」值，并确保其在合理范围内
        // 1. 新「生命之契」值 = 当前「生命之契」值 + 新增「生命之契」值
        // 2. 确保新「生命之契」值至少为0
        // 3. 确保新「生命之契」值最多不超过两倍的最大生命值
        float newDebt = Math.min(Math.max(currentDebt + debt, 0), 2 * maxHp);
        float changeDebt = newDebt - currentDebt;

        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, newDebt);
        avatar.getWorld().broadcastPacket(new PacketEntityFightPropUpdateNotify(avatar, FightProperty.FIGHT_PROP_CUR_HP_DEBTS));

        if (changeDebt != 0) {
            ChangeHpDebtsOuterClass.ChangeHpDebts reason;

            if (newDebt == 0) {
                reason = ChangeHpDebtsOuterClass.ChangeHpDebts.ChangeHpDebts_CHANGE_HP_DEBTS_PAY_FINISH;
            } else if (changeDebt > 0) {
                reason = ChangeHpDebtsOuterClass.ChangeHpDebts.ChangeHpDebts_CHANGE_HP_DEBTS_ADD_ABILITY;
            } else {
                reason = ChangeHpDebtsOuterClass.ChangeHpDebts.ChangeHpDebts_CHANGE_HP_DEBTS_PAY;
            }

            avatar.getWorld().broadcastPacket(new PacketEntityFightPropChangeReasonNotify(
                avatar,
                FightProperty.FIGHT_PROP_CUR_HP_DEBTS,
                changeDebt,
                PropChangeReasonOuterClass.PropChangeReason.PropChangeReason_PROP_CHANGE_ABILITY,
                reason
            ));
        }

        return true;
    }
}
