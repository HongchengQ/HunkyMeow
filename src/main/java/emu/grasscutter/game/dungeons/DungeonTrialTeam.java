package emu.grasscutter.game.dungeons;

import emu.grasscutter.net.proto.GrantReasonOuterClass.GrantReason;
import java.util.List;
import lombok.*;

@Data
@AllArgsConstructor
public class DungeonTrialTeam {
    List<Integer> trialAvatarIds;
    GrantReason grantReason;
}
