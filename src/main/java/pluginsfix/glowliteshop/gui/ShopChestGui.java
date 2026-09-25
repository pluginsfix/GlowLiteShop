package pluginsfix.glowliteshop.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pluginsfix.glowliteshop.config.ItemParser;
import pluginsfix.glowliteshop.config.ShopConfig;
import pluginsfix.glowliteshop.domain.StoredItem;
import pluginsfix.glowliteshop.service.ShopChestService;
import pluginsfix.glowliteshop.text.TextUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class ShopChestGui {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy HH:mm");

    private final ShopConfig config;
    private final ShopChestService chestService;

    public ShopChestGui(ShopConfig config, ShopChestService chestService) {
        this.config = config;
        this.chestService = chestService;
    }

    public void open(Player player) {
        chestService.getPlayerItems(player.getUniqueId()).thenAccept(items -> {
            player.getServer().getScheduler().runTask(player.getServer().getPluginManager().getPlugin("GlowLiteShop"), () -> {
                ShopChestHolder holder = new ShopChestHolder();
                Component title = TextUtil.parseComponent(config.chestGuiTitle()).decoration(TextDecoration.ITALIC, false);
                Inventory inv = Bukkit.createInventory(holder, config.chestGuiSize(), title);
                holder.setInventory(inv);

                ItemStack filler = ItemParser.createButton(config.chestFillerMaterial(), config.chestFillerName(), List.of());
                int bottomStart = config.chestGuiSize() - 9;
                for (int i = bottomStart; i < config.chestGuiSize(); i++) {
                    inv.setItem(i, filler);
                }

                ShopConfig.NavButton backBtn = config.backButton();
                if (backBtn.slot() >= 0 && backBtn.slot() < inv.getSize()) {
                    inv.setItem(backBtn.slot(), ItemParser.createButton(backBtn.material(), backBtn.name(), backBtn.lore()));
                }

                ShopConfig.NavButton claimAllBtn = config.claimAllButton();
                if (claimAllBtn.slot() >= 0 && claimAllBtn.slot() < inv.getSize()) {
                    inv.setItem(claimAllBtn.slot(), ItemParser.createButton(claimAllBtn.material(), claimAllBtn.name(), claimAllBtn.lore()));
                }

                int maxItemSlots = bottomStart;
                int count = Math.min(items.size(), maxItemSlots);
                for (int i = 0; i < count; i++) {
                    StoredItem stored = items.get(i);
                    ItemStack display = buildChestItem(stored);
                    inv.setItem(i, display);
                    holder.putItem(i, stored);
                }

                player.openInventory(inv);
            });
        });
    }

    private ItemStack buildChestItem(StoredItem stored) {
        ItemStack item = stored.itemStack().clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Component.empty().decoration(TextDecoration.ITALIC, false));
        String dateStr = DATE_FORMAT.format(new Date(stored.acquiredAt()));
        lore.add(TextUtil.parseComponent("&#FFFF00◆ &fПолучено: &#FFFF00" + dateStr).decoration(TextDecoration.ITALIC, false));
        lore.add(TextUtil.parseComponent("&#FFFF00▶ &f[Клик] &8— &fЗабрать предмет").decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }
}
