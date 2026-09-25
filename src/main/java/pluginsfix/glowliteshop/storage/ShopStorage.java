package pluginsfix.glowliteshop.storage;

import pluginsfix.glowliteshop.domain.StoredItem;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface ShopStorage {
    void initialize();

    void close();

    CompletableFuture<Double> getInternalSapphires(UUID playerUuid);

    CompletableFuture<Void> setInternalSapphires(UUID playerUuid, double amount);

    CompletableFuture<Void> addInternalSapphires(UUID playerUuid, double amount);

    CompletableFuture<Boolean> removeInternalSapphires(UUID playerUuid, double amount);

    CompletableFuture<Long> insertStoredItem(UUID playerUuid, org.bukkit.inventory.ItemStack item, String sourceNote);

    CompletableFuture<List<StoredItem>> getStoredItems(UUID playerUuid);

    CompletableFuture<Boolean> deleteStoredItem(long id, UUID playerUuid);

    CompletableFuture<Integer> clearAllStoredItems(UUID playerUuid);
}
