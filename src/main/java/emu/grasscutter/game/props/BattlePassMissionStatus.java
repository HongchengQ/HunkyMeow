package emu.grasscutter.game.props;

import emu.grasscutter.net.proto.MissionStatusOuterClass.MissionStatus;

public enum BattlePassMissionStatus {
    MISSION_STATUS_INVALID(0, MissionStatus.MissionSTATUS_MISSION_INVALID),
    MISSION_STATUS_UNFINISHED(1, MissionStatus.MissionSTATUS_MISSION_UNFINISHED),
    MISSION_STATUS_FINISHED(2, MissionStatus.MissionSTATUS_MISSION_FINISHED),
    MISSION_STATUS_POINT_TAKEN(3, MissionStatus.MissionSTATUS_MISSION_POINT_TAKEN);

    private final int value;
    private final MissionStatus missionStatus;

    BattlePassMissionStatus(int value, MissionStatus missionStatus) {
        this.value = value;
        this.missionStatus = missionStatus; // In case proto enum values change later
    }

    public int getValue() {
        return value;
    }

    public MissionStatus getMissionStatus() {
        return missionStatus;
    }
}
