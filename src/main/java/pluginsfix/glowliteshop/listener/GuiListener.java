package pluginsfix.glowliteshop.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import pluginsfix.glowliteshop.config.ShopConfig;
import pluginsfix.glowliteshop.domain.ActiveShopItem;
import pluginsfix.glowliteshop.domain.PurchaseMode;
import pluginsfix.glowliteshop.domain.StoredItem;
import pluginsfix.glowliteshop.gui.ShopChestGui;
import pluginsfix.glowliteshop.gui.ShopChestHolder;
import pluginsfix.glowliteshop.gui.ShopGui;
import pluginsfix.glowliteshop.gui.ShopHolder;
import pluginsfix.glowliteshop.service.ShopChestService;
import pluginsfix.glowliteshop.service.ShopService;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GuiListener implements Listener {
    private final ShopConfig config;
    private final ShopService shopService;
    private final ShopChestService chestService;
    private final ShopGui shopGui;
    private final ShopChestGui chestGui;
    private final Map<UUID, Long> debounce = new ConcurrentHashMap<>();

    public GuiListener(ShopConfig config, ShopService shopService, ShopChestService chestService, ShopGui shopGui, ShopChestGui chestGui) {
        this.config = config;
        this.shopService = shopService;
        this.chestService = chestService;
        this.shopGui = shopGui;
        this.chestGui = chestGui;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof ShopHolder || top.getHolder() instanceof ShopChestHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        boolean isShop = top.getHolder() instanceof ShopHolder;
        boolean isChest = top.getHolder() instanceof ShopChestHolder;

        if (!isShop && !isChest) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) {
            return;
        }

        long now = System.currentTimeMillis();
        long lastClick = debounce.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastClick < 250L) {
            return;
        }
        debounce.put(player.getUniqueId(), now);

        int rawSlot = event.getSlot();

        if (isShop) {
            handleShopClick(player, rawSlot, event.isLeftClick(), event.isRightClick());
        } else {
            handleChestClick(player, (ShopChestHolder) top.getHolder(), rawSlot);
        }
    }

    private void handleShopClick(Player player, int slot, boolean isLeftClick, boolean isRightClick) {
        ShopConfig.NavButton kits = config.kitsButton();
        if (slot == kits.slot()) {
            player.closeInventory();
            if (kits.command() != null && !kits.command().isEmpty()) {
                player.performCommand(kits.command());
            }
            return;
        }

        ShopConfig.NavButton boosters = config.boostersButton();
        if (slot == boosters.slot()) {
            player.closeInventory();
            if (boosters.command() != null && !boosters.command().isEmpty()) {
                player.performCommand(boosters.command());
            }
            return;
        }

        ShopConfig.NavButton chest = config.chestButton();
        if (slot == chest.slot()) {
            chestGui.open(player);
            return;
        }

        ShopConfig.NavButton indicator = config.rotationIndicator();
        if (slot == indicator.slot()) {
            shopGui.open(player);
            return;
        }

        ActiveShopItem activeItem = shopService.getActiveItem(slot);
        if (activeItem == null || activeItem.isSold()) {
            return;
        }

        PurchaseMode mode = activeItem.product().purchaseMode();

        if (isLeftClick && (mode == PurchaseMode.DIRECT || mode == PurchaseMode.BOTH)) {
            shopService.buyDirect(player, slot, () -> shopGui.open(player));
        } else if (isRightClick && (mode == PurchaseMode.AUCTION || mode == PurchaseMode.BOTH)) {
            double nextBid = activeItem.getNextMinimumBid();
            shopService.placeBid(player, slot, nextBid, () -> shopGui.open(player));
        }
    }

    private void handleChestClick(Player player, ShopChestHolder holder, int slot) {
        ShopConfig.NavButton backBtn = config.backButton();
        if (slot == backBtn.slot()) {
            shopGui.open(player);
            return;
        }

        ShopConfig.NavButton claimAllBtn = config.claimAllButton();
        if (slot == claimAllBtn.slot()) {
            chestService.claimAll(player, () -> chestGui.open(player));
            return;
        }

        StoredItem stored = holder.getItem(slot);
        if (stored != null) {
            chestService.claimItem(player, stored, () -> chestGui.open(player));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        debounce.remove(event.getPlayer().getUniqueId());
    }
}
