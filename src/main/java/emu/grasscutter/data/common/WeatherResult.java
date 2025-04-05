package emu.grasscutter.data.common;

import emu.grasscutter.game.props.ClimateType;

public class WeatherResult {
    private final int areaID;
    private final ClimateType defaultClimate;

    public WeatherResult(int areaID, ClimateType defaultClimate) {
        this.areaID = areaID;
        this.defaultClimate = defaultClimate;
    }

    public int getAreaID() {
        return areaID;
    }

    public ClimateType getDefaultClimate() {
        return defaultClimate;
    }
}
