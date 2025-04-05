package emu.grasscutter.server.packet.send;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.common.WeatherResult;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ClimateType;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.SceneAreaWeatherNotifyOuterClass.SceneAreaWeatherNotify;

public class PacketSceneAreaWeatherNotify extends BasePacket {

    public PacketSceneAreaWeatherNotify(Player player) {
        super(PacketOpcodes.SceneAreaWeatherNotify);
        WeatherResult result = player.getOptimalWeatherData();

        int areaID; ClimateType climateType;
        if (result != null) {
            areaID = result.getAreaID();
            climateType = result.getDefaultClimate();
        } else {
            areaID = player.getWeatherId();
            climateType = ClimateType.getTypeByValue(player.getClimate().getValue());
        }

        SceneAreaWeatherNotify proto =
                SceneAreaWeatherNotify.newBuilder()
                        .setWeatherAreaId(areaID)
                        .setClimateType(climateType.getValue())
                        .build();
        Grasscutter.getLogger().debug("Weather 发包了 {}", proto);

        this.setData(proto);
    }
}
