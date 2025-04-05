package emu.grasscutter.data.binout;

import com.google.gson.annotations.SerializedName;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ConfigGadgetCombatProperty {
    @SerializedName(value = "HP", alternate = "hp") float HP;
    boolean isLockHP;
    boolean isInvincible;
    boolean isGhostToAllied;
    float attack;
    float defence;
    float weight;
    boolean useCreatorProperty;
}
