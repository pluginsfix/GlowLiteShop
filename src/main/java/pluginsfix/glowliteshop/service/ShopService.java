package pluginsfix.glowliteshop.service;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import pluginsfix.glowliteshop.config.ShopConfig;
import pluginsfix.glowliteshop.currency.CurrencyService;
import pluginsfix.glowliteshop.domain.ActiveShopItem;
import pluginsfix.glowliteshop.domain.PurchaseMode;
import pluginsfix.glowliteshop.domain.ShopProduct;
import pluginsfix.glowliteshop.text.Messages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ShopService {
    private final JavaPlugin plugin;
    private final ShopConfig config;
    private final CurrencyService currencyService;
    private final ShopChestService chestService;
    private final Messages messages;

    private final Map<Integer, ActiveShopItem> activeItems = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private long nextRotationTime;

    public ShopService(JavaPlugin plugin, ShopConfig config, CurrencyService currencyService, ShopChestService chestService, Messages messages) {
        this.plugin = plugin;
        this.config = config;
        this.currencyService = currencyService;
        this.chestService = chestService;
        this.messages = messages;
        rotateAssortment();
    }

    public synchronized void rotateAssortment() {
        resolvePendingAuctions();

        activeItems.clear();
        List<ShopProduct> pool = new ArrayList<>(config.products().values());
        if (pool.isEmpty()) {
            return;
        }

        Collections.shuffle(pool, random);
        List<Integer> slots = config.activeSlots();
        long durationMs = (long) config.getEffectiveRotationIntervalMinutes() * 60L * 1000L;
        this.nextRotationTime = System.currentTimeMillis() + durationMs;

        for (int i = 0; i < slots.size(); i++) {
            int slot = slots.get(i);
            ShopProduct product = pool.get(i % pool.size());
            ActiveShopItem item = new ActiveShopItem(slot, product, nextRotationTime);
            activeItems.put(slot, item);
        }
    }

    public synchronized void resolvePendingAuctions() {
        for (ActiveShopItem item : activeItems.values()) {
            if (!item.isSold() && item.highestBidder() != null) {
                UUID winnerUuid = item.highestBidder();
                ItemStack stack = item.product().itemStack().clone();
                chestService.storeItem(winnerUuid, stack, "AUCTION_WIN");

                Player online = Bukkit.getPlayer(winnerUuid);
                if (online != null && online.isOnline()) {
                    String itemName = stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()
                            ? PlainTextComponentSerializer.plainText().serialize(stack.getItemMeta().displayName())
                            : stack.getType().name();
                    String curName = currencyService.getCurrencyName(item.product().currencyKey());
                    String formatted = currencyService.formatAmount(item.currentBid());
                    messages.send(online, "auction-won", Map.of(
                            "item", itemName,
                            "amount", formatted,
                            "currency", curName
                    ));
                }
                item.markSold();
            }
        }
    }

    public void checkRotationTick() {
        if (System.currentTimeMillis() >= nextRotationTime) {
            rotateAssortment();
        }
    }

    public void setEarlyWipe(boolean enabled) {
        config.setEarlyWipe(enabled);
        long remainingMs = nextRotationTime - System.currentTimeMillis();
        long newMaxDurationMs = (long) config.getEffectiveRotationIntervalMinutes() * 60L * 1000L;
        if (remainingMs > newMaxDurationMs) {
            this.nextRotationTime = System.currentTimeMillis() + newMaxDurationMs;
        }
    }

    public boolean isEarlyWipe() {
        return config.earlyWipe();
    }

    public long getNextRotationTime() {
        return nextRotationTime;
    }

    public Map<Integer, ActiveShopItem> getActiveItems() {
        return Collections.unmodifiableMap(activeItems);
    }

    public ActiveShopItem getActiveItem(int slot) {
        return activeItems.get(slot);
    }

    public void buyDirect(Player player, int slot, Runnable onComplete) {
        ActiveShopItem item = activeItems.get(slot);
        if (item == null || item.isSold()) {
            messages.send(player, "item-already-sold");
            return;
        }

        ShopProduct product = item.product();
        if (product.purchaseMode() == PurchaseMode.AUCTION) {
            messages.send(player, "item-not-auction");
            return;
        }

        double price = product.directPrice();
        String currencyKey = product.currencyKey();
        String currencyName = currencyService.getCurrencyName(currencyKey);
        String formattedPrice = currencyService.formatAmount(price);

        currencyService.hasBalance(player.getUniqueId(), currencyKey, price).thenAccept(hasEnough -> {
            if (!hasEnough) {
                player.getServer().getScheduler().runTask(plugin, () ->
                        messages.send(player, "not-enough-funds", Map.of(
                                "price", formattedPrice,
                                "currency", currencyName
                        )));
                return;
            }

            currencyService.withdraw(player.getUniqueId(), currencyKey, price).thenAccept(withdrawn -> {
                if (!withdrawn) {
                    player.getServer().getScheduler().runTask(plugin, () ->
                            messages.send(player, "not-enough-funds", Map.of(
                                    "price", formattedPrice,
                                    "currency", currencyName
                            )));
                    return;
                }

                ItemStack stack = product.itemStack().clone();
                chestService.storeItem(player.getUniqueId(), stack, "DIRECT_BUY").thenAccept(storedId -> {
                    player.getServer().getScheduler().runTask(plugin, () -> {
                        item.markSold();
                        String itemName = stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()
                                ? PlainTextComponentSerializer.plainText().serialize(stack.getItemMeta().displayName())
                                : stack.getType().name();
                        messages.send(player, "buy-success", Map.of(
                                "item", itemName,
                                "price", formattedPrice,
                                "currency", currencyName
                        ));
                        if (onComplete != null) {
                            onComplete.run();
                        }
                    });
                });
            });
        });
    }

    public void placeBid(Player player, int slot, double bidAmount, Runnable onComplete) {
        ActiveShopItem item = activeItems.get(slot);
        if (item == null || item.isSold()) {
            messages.send(player, "item-already-sold");
            return;
        }

        ShopProduct product = item.product();
        if (product.purchaseMode() == PurchaseMode.DIRECT) {
            messages.send(player, "item-not-auction");
            return;
        }

        if (player.getUniqueId().equals(item.highestBidder())) {
            messages.send(player, "cannot-outbid-self");
            return;
        }

        double minRequiredBid = item.getNextMinimumBid();
        String currencyKey = product.currencyKey();
        String currencyName = currencyService.getCurrencyName(currencyKey);

        if (bidAmount < minRequiredBid) {
            String formattedMin = currencyService.formatAmount(minRequiredBid);
            messages.send(player, "bid-too-low", Map.of(
                    "min_bid", formattedMin,
                    "currency", currencyName
            ));
            return;
        }

        String formattedBid = currencyService.formatAmount(bidAmount);

        currencyService.hasBalance(player.getUniqueId(), currencyKey, bidAmount).thenAccept(hasEnough -> {
            if (!hasEnough) {
                player.getServer().getScheduler().runTask(plugin, () ->
                        messages.send(player, "not-enough-funds", Map.of(
                                "price", formattedBid,
                                "currency", currencyName
                        )));
                return;
            }

            currencyService.withdraw(player.getUniqueId(), currencyKey, bidAmount).thenAccept(withdrawn -> {
                if (!withdrawn) {
                    player.getServer().getScheduler().runTask(plugin, () ->
                            messages.send(player, "not-enough-funds", Map.of(
                                    "price", formattedBid,
                                    "currency", currencyName
                            )));
                    return;
                }

                UUID previousBidder = item.highestBidder();
                double previousBid = item.currentBid();

                item.setBid(player.getUniqueId(), player.getName(), bidAmount);

                if (previousBidder != null && previousBid > 0) {
                    currencyService.deposit(previousBidder, currencyKey, previousBid);
                    Player prevOnline = Bukkit.getPlayer(previousBidder);
                    if (prevOnline != null && prevOnline.isOnline()) {
                        String itemName = product.itemStack().hasItemMeta() && product.itemStack().getItemMeta().hasDisplayName()
                                ? PlainTextComponentSerializer.plainText().serialize(product.itemStack().getItemMeta().displayName())
                                : product.itemStack().getType().name();
                        String prevFormatted = currencyService.formatAmount(previousBid);
                        messages.send(prevOnline, "bid-outbid", Map.of(
                                "item", itemName,
                                "player", player.getName(),
                                "amount", prevFormatted,
                                "currency", currencyName
                        ));
                    }
                }

                player.getServer().getScheduler().runTask(plugin, () -> {
                    String itemName = product.itemStack().hasItemMeta() && product.itemStack().getItemMeta().hasDisplayName()
                            ? PlainTextComponentSerializer.plainText().serialize(product.itemStack().getItemMeta().displayName())
                            : product.itemStack().getType().name();
                    messages.send(player, "bid-success", Map.of(
                            "item", itemName,
                            "amount", formattedBid,
                            "currency", currencyName
                    ));
                    if (onComplete != null) {
                        onComplete.run();
                    }
                });
            });
        });
    }

    public String formatTimeRemaining() {
        long remainingMs = Math.max(0, nextRotationTime - System.currentTimeMillis());
        long totalSeconds = remainingMs / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        if (hours > 0) {
            return String.format("%02d ч. %02d мин.", hours, minutes);
        }
        return String.format("%02d мин. %02d сек.", minutes, seconds);
    }
}
