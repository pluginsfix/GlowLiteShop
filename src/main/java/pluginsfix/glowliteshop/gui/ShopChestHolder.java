package pluginsfix.glowliteshop.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import pluginsfix.glowliteshop.domain.StoredItem;

import java.util.HashMap;
import java.util.Map;

public final class ShopChestHolder implements InventoryHolder {
    private Inventory inventory;
    private final Map<Integer, StoredItem> slotToItem = new HashMap<>();

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void putItem(int slot, StoredItem item) {
        slotToItem.put(slot, item);
    }

    public StoredItem getItem(int slot) {
        return slotToItem.get(slot);
    }
}
