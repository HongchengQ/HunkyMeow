package emu.grasscutter.data.excels;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.*;
import emu.grasscutter.data.common.ItemParamData;
import java.util.List;

@ResourceType(
        name = "EnvAnimalGatherExcelConfigData.json",
        loadPriority = ResourceType.LoadPriority.LOW)
public class EnvAnimalGatherConfigData extends GameResource {
    private int animalId;
    private String entityType;
    @SerializedName(value = "gatherItemList", alternate = "gatherItemId")
    private List<ItemParamData> gatherItemList;
    private String excludeWeathers;
    private int aliveTime;
    private int escapeTime;
    private int escapeRadius;

    @Override
    public int getId() {
        return animalId;
    }

    public int getAnimalId() {
        return animalId;
    }

    public String getEntityType() {
        return entityType;
    }

    public ItemParamData getGatherItem() {
        if (gatherItemList == null || gatherItemList.isEmpty()) {
            return null;
        }

        return gatherItemList.getFirst();
    }
}
