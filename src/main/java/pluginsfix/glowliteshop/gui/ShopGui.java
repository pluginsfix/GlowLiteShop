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
import pluginsfix.glowliteshop.currency.CurrencyService;
import pluginsfix.glowliteshop.domain.ActiveShopItem;
import pluginsfix.glowliteshop.domain.PurchaseMode;
import pluginsfix.glowliteshop.domain.ShopProduct;
import pluginsfix.glowliteshop.service.ShopChestService;
import pluginsfix.glowliteshop.service.ShopService;
import pluginsfix.glowliteshop.text.TextUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ShopGui {
    private final ShopConfig config;
    private final ShopService shopService;
    private final ShopChestService chestService;
    private final CurrencyService currencyService;

    public ShopGui(ShopConfig config, ShopService shopService, ShopChestService chestService, CurrencyService currencyService) {
        this.config = config;
        this.shopService = shopService;
        this.chestService = chestService;
        this.currencyService = currencyService;
    }

    public void open(Player player) {
        chestService.getPlayerItems(player.getUniqueId()).thenAccept(storedList -> {
            player.getServer().getScheduler().runTask(player.getServer().getPluginManager().getPlugin("GlowLiteShop"), () -> {
                int storedCount = storedList.size();
                ShopHolder holder = new ShopHolder();
                Component titleComponent = TextUtil.parseComponent(config.guiTitle()).decoration(TextDecoration.ITALIC, false);
                Inventory inv = Bukkit.createInventory(holder, config.guiSize(), titleComponent);
                holder.setInventory(inv);

                ItemStack filler = ItemParser.createButton(config.guiFillerMaterial(), config.guiFillerName(), List.of());
                for (int i = 0; i < config.guiSize(); i++) {
                    inv.setItem(i, filler);
                }

                setupNavigation(inv, storedCount);
                setupActiveItems(inv);

                player.openInventory(inv);
            });
        });
    }

    private void setupNavigation(Inventory inv, int storedCount) {
        ShopConfig.NavButton kits = config.kitsButton();
        if (kits.slot() >= 0 && kits.slot() < inv.getSize()) {
            inv.setItem(kits.slot(), ItemParser.createButton(kits.material(), kits.name(), kits.lore()));
        }

        ShopConfig.NavButton boosters = config.boostersButton();
        if (boosters.slot() >= 0 && boosters.slot() < inv.getSize()) {
            inv.setItem(boosters.slot(), ItemParser.createButton(boosters.material(), boosters.name(), boosters.lore()));
        }

        ShopConfig.NavButton chest = config.chestButton();
        if (chest.slot() >= 0 && chest.slot() < inv.getSize()) {
            List<String> chestLore = new ArrayList<>();
            for (String line : chest.lore()) {
                chestLore.add(line.replace("{count}", String.valueOf(storedCount)));
            }
            inv.setItem(chest.slot(), ItemParser.createButton(chest.material(), chest.name(), chestLore));
        }

        ShopConfig.NavButton indicator = config.rotationIndicator();
        if (indicator.slot() >= 0 && indicator.slot() < inv.getSize()) {
            String time = shopService.formatTimeRemaining();
            String earlyStatus = shopService.isEarlyWipe() ? "§aВключён" : "§cВыключен";
            List<String> indicatorLore = new ArrayList<>();
            for (String line : indicator.lore()) {
                indicatorLore.add(line.replace("{time}", time).replace("{early_wipe_status}", earlyStatus));
            }
            inv.setItem(indicator.slot(), ItemParser.createButton(indicator.material(), indicator.name(), indicatorLore));
        }
    }

    private void setupActiveItems(Inventory inv) {
        Map<Integer, ActiveShopItem> activeMap = shopService.getActiveItems();
        for (Map.Entry<Integer, ActiveShopItem> entry : activeMap.entrySet()) {
            int slot = entry.getKey();
            ActiveShopItem activeItem = entry.getValue();
            if (slot >= 0 && slot < inv.getSize()) {
                inv.setItem(slot, buildDisplayItem(activeItem));
            }
        }
    }

    private ItemStack buildDisplayItem(ActiveShopItem activeItem) {
        ShopProduct product = activeItem.product();
        ItemStack display = product.itemStack().clone();
        ItemMeta meta = display.getItemMeta();
        if (meta == null) {
            return display;
        }

        List<Component> currentLore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        currentLore.add(Component.empty().decoration(TextDecoration.ITALIC, false));

        String curName = currencyService.getCurrencyName(product.currencyKey());
        String curSymbol = currencyService.getCurrencySymbol(product.currencyKey());

        if (activeItem.isSold()) {
            currentLore.add(TextUtil.parseComponent("&#FB8808▶ &fСтатус: &#FB8808ПРОДАНО / СНЯТО").decoration(TextDecoration.ITALIC, false));
        } else {
            PurchaseMode mode = product.purchaseMode();

            if (mode == PurchaseMode.DIRECT || mode == PurchaseMode.BOTH) {
                String priceStr = currencyService.formatAmount(product.directPrice());
                currentLore.add(TextUtil.parseComponent("&#FFFF00◆ &fПрямая покупка: &#FFFF00" + priceStr + " " + curSymbol + " " + curName).decoration(TextDecoration.ITALIC, false));
            }

            if (mode == PurchaseMode.AUCTION || mode == PurchaseMode.BOTH) {
                String curBidStr = currencyService.formatAmount(activeItem.currentBid());
                String nextBidStr = currencyService.formatAmount(activeItem.getNextMinimumBid());
                String stepStr = currencyService.formatAmount(product.auctionBidStep());
                String bidder = activeItem.highestBidderName() != null ? activeItem.highestBidderName() : "отсутствует";

                currentLore.add(TextUtil.parseComponent("&#FFFF00◆ &fТекущая ставка: &#FFFF00" + curBidStr + " " + curSymbol).decoration(TextDecoration.ITALIC, false));
                currentLore.add(TextUtil.parseComponent("&#FFFF00◆ &fЛидер ставки: &#FFFF00" + bidder).decoration(TextDecoration.ITALIC, false));
                currentLore.add(TextUtil.parseComponent("&#FFFF00◆ &fСледующая ставка: &#FFFF00" + nextBidStr + " " + curSymbol + " &8(+ " + stepStr + ")").decoration(TextDecoration.ITALIC, false));
            }

            currentLore.add(Component.empty().decoration(TextDecoration.ITALIC, false));

            if (mode == PurchaseMode.DIRECT) {
                currentLore.add(TextUtil.parseComponent("&#FFFF00▶ &f[ЛКМ] &8— &fКупить предмет").decoration(TextDecoration.ITALIC, false));
            } else if (mode == PurchaseMode.AUCTION) {
                currentLore.add(TextUtil.parseComponent("&#FB8808▶ &f[ПКМ] &8— &fСделать минимальную ставку").decoration(TextDecoration.ITALIC, false));
            } else {
                currentLore.add(TextUtil.parseComponent("&#FFFF00▶ &f[ЛКМ] &8— &fКупить сразу").decoration(TextDecoration.ITALIC, false));
                currentLore.add(TextUtil.parseComponent("&#FB8808▶ &f[ПКМ] &8— &fСделать ставку").decoration(TextDecoration.ITALIC, false));
            }
        }

        meta.lore(currentLore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        display.setItemMeta(meta);
        return display;
    }
}
