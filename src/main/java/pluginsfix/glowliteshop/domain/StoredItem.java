package pluginsfix.glowliteshop.domain;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public record StoredItem(
        long id,
        UUID playerUuid,
        ItemStack itemStack,
        long acquiredAt,
        String sourceNote
) {
}
