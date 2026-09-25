package pluginsfix.glowliteshop.service;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import pluginsfix.glowliteshop.domain.StoredItem;
import pluginsfix.glowliteshop.storage.ShopStorage;
import pluginsfix.glowliteshop.text.Messages;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ShopChestService {
    private final ShopStorage storage;
    private final Messages messages;

    public ShopChestService(ShopStorage storage, Messages messages) {
        this.storage = storage;
        this.messages = messages;
    }

    public CompletableFuture<List<StoredItem>> getPlayerItems(UUID playerUuid) {
        return storage.getStoredItems(playerUuid);
    }

    public CompletableFuture<Long> storeItem(UUID playerUuid, ItemStack item, String reason) {
        return storage.insertStoredItem(playerUuid, item, reason);
    }

    public void claimItem(Player player, StoredItem item, Runnable onSuccess) {
        if (player.getInventory().firstEmpty() == -1) {
            messages.send(player, "inventory-full");
            return;
        }

        storage.deleteStoredItem(item.id(), player.getUniqueId()).thenAccept(deleted -> {
            if (deleted) {
                player.getServer().getScheduler().runTask(player.getServer().getPluginManager().getPlugin("GlowLiteShop"), () -> {
                    player.getInventory().addItem(item.itemStack().clone());
                    String itemName = item.itemStack().hasItemMeta() && item.itemStack().getItemMeta().hasDisplayName()
                            ? PlainTextComponentSerializer.plainText().serialize(item.itemStack().getItemMeta().displayName())
                            : item.itemStack().getType().name();
                    messages.send(player, "item-claimed", Map.of("item", itemName));
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                });
            }
        });
    }

    public void claimAll(Player player, Runnable onSuccess) {
        storage.getStoredItems(player.getUniqueId()).thenAccept(items -> {
            if (items.isEmpty()) {
                messages.send(player, "chest-empty");
                return;
            }

            player.getServer().getScheduler().runTask(player.getServer().getPluginManager().getPlugin("GlowLiteShop"), () -> {
                int claimedCount = 0;
                for (StoredItem item : items) {
                    if (player.getInventory().firstEmpty() == -1) {
                        break;
                    }
                    player.getInventory().addItem(item.itemStack().clone());
                    storage.deleteStoredItem(item.id(), player.getUniqueId());
                    claimedCount++;
                }

                if (claimedCount > 0) {
                    messages.send(player, "all-items-claimed", Map.of("count", String.valueOf(claimedCount)));
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                } else {
                    messages.send(player, "inventory-full");
                }
            });
        });
    }
}
