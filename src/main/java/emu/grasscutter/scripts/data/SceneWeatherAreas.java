package emu.grasscutter.scripts.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.common.PointsInfo;
import lombok.Getter;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import static emu.grasscutter.Grasscutter.config;

public class SceneWeatherAreas {
    public int area_id;
    @Getter private List<PointsInfo> points;
    @Getter private boolean isUseHeightBorder;
    @Getter private float bottom;
    @Getter private float top;

    public int getId() {
        return this.area_id;
    }

    public static List<SceneWeatherAreas> SceneWeatherAreasall(int sceneId) {
        List<SceneWeatherAreas> weatherAreas = new ArrayList<>();
        File directory = new File(config.folderStructure.resources + "Scripts/Scene/" + sceneId);
        File[] files = directory.listFiles((dir, name) -> name.matches("scene" + sceneId + "_weather_areas.json"));

        if (files != null) {
            Gson gson = new Gson();
            Type weatherAreaListType = new TypeToken<List<SceneWeatherAreas>>() {}.getType(); // 解析为List

            for (File file : files) {
                try (FileReader reader = new FileReader(file)) {
                    List<SceneWeatherAreas> data = gson.fromJson(reader, weatherAreaListType);
                    weatherAreas.addAll(data); // 将解析的列表全部添加到weatherAreas
                } catch (IOException e) {
                    Grasscutter.getLogger().debug("尝试读取文件 {} 失败: {}", file.getAbsolutePath(), e.getMessage());
                }
            }
        }
        return weatherAreas;
    }
}

