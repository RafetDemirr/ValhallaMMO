package me.athlaeos.valhallammo.trading.data;

import me.athlaeos.valhallammo.trading.dom.MerchantData;
import me.athlaeos.valhallammo.utility.Callback;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public abstract class MerchantDataPersistence {
    // written from database threads and read from the main thread, so these must be thread safe
    protected final Map<UUID, MerchantData> allData = new ConcurrentHashMap<>();
    // merchants known to have no stored data. kept apart from allData because ConcurrentHashMap can't hold null values
    protected final Set<UUID> absentData = ConcurrentHashMap.newKeySet();

    public Map<UUID, MerchantData> getAllData() {
        return new HashMap<>(allData);
    }

    /**
     * @return the merchant's data if it's held in memory, or null. Prefer this over getAllData().get(id), which copies
     * the entire cache for a single lookup.
     */
    public MerchantData getCachedData(UUID id){
        return allData.get(id);
    }

    public abstract void setData(UUID id, MerchantData data);

    public abstract void getData(UUID id, Callback<MerchantData> onFinish);

    /**
     * @return true if the data for this id is already held in memory, meaning
     * {@link #getData(UUID, Callback)} will invoke its callback synchronously.
     */
    public boolean isCached(UUID id){
        return allData.containsKey(id) || absentData.contains(id);
    }

    public abstract void saveAllData();

    public abstract void saveData(UUID id, MerchantData data);

    public abstract void removeData(UUID id);
}
