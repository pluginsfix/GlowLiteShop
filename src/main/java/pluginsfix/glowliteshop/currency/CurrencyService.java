package pluginsfix.glowliteshop.currency;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import pluginsfix.glowliteshop.config.ShopConfig;
import pluginsfix.glowliteshop.hook.PlayerPointsHook;
import pluginsfix.glowliteshop.hook.VaultHook;
import pluginsfix.glowliteshop.storage.ShopStorage;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CurrencyService {
    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("#,##0.##", new DecimalFormatSymbols(Locale.US));

    private final ShopConfig config;
    private final VaultHook vaultHook;
    private final PlayerPointsHook playerPointsHook;
    private final ShopStorage storage;

    public CurrencyService(ShopConfig config, VaultHook vaultHook, PlayerPointsHook playerPointsHook, ShopStorage storage) {
        this.config = config;
        this.vaultHook = vaultHook;
        this.playerPointsHook = playerPointsHook;
        this.storage = storage;
    }

    public CompletableFuture<Double> getBalance(UUID playerUuid, String currencyKey) {
        ShopConfig.CurrencyDef def = config.getCurrency(currencyKey);
        String type = def != null ? def.type().toLowerCase() : "vault";

        if ("vault".equals(type)) {
            Optional<Economy> ecoOpt = vaultHook.getEconomy();
            if (ecoOpt.isPresent()) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(playerUuid);
                return CompletableFuture.completedFuture(ecoOpt.get().getBalance(op));
            }
            return CompletableFuture.completedFuture(0.0);
        }

        if ("playerpoints".equals(type) && playerPointsHook.isAvailable()) {
            return CompletableFuture.completedFuture((double) playerPointsHook.look(playerUuid));
        }

        return storage.getInternalSapphires(playerUuid);
    }

    public CompletableFuture<Boolean> hasBalance(UUID playerUuid, String currencyKey, double amount) {
        return getBalance(playerUuid, currencyKey).thenApply(bal -> bal >= amount);
    }

    public CompletableFuture<Boolean> withdraw(UUID playerUuid, String currencyKey, double amount) {
        ShopConfig.CurrencyDef def = config.getCurrency(currencyKey);
        String type = def != null ? def.type().toLowerCase() : "vault";

        if ("vault".equals(type)) {
            Optional<Economy> ecoOpt = vaultHook.getEconomy();
            if (ecoOpt.isPresent()) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(playerUuid);
                Economy economy = ecoOpt.get();
                if (economy.has(op, amount)) {
                    return CompletableFuture.completedFuture(economy.withdrawPlayer(op, amount).transactionSuccess());
                }
            }
            return CompletableFuture.completedFuture(false);
        }

        if ("playerpoints".equals(type) && playerPointsHook.isAvailable()) {
            int intAmount = (int) Math.ceil(amount);
            boolean success = playerPointsHook.take(playerUuid, intAmount);
            return CompletableFuture.completedFuture(success);
        }

        return storage.removeInternalSapphires(playerUuid, amount);
    }

    public CompletableFuture<Boolean> deposit(UUID playerUuid, String currencyKey, double amount) {
        ShopConfig.CurrencyDef def = config.getCurrency(currencyKey);
        String type = def != null ? def.type().toLowerCase() : "vault";

        if ("vault".equals(type)) {
            Optional<Economy> ecoOpt = vaultHook.getEconomy();
            if (ecoOpt.isPresent()) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(playerUuid);
                return CompletableFuture.completedFuture(ecoOpt.get().depositPlayer(op, amount).transactionSuccess());
            }
            return CompletableFuture.completedFuture(false);
        }

        if ("playerpoints".equals(type) && playerPointsHook.isAvailable()) {
            int intAmount = (int) Math.floor(amount);
            boolean success = playerPointsHook.give(playerUuid, intAmount);
            return CompletableFuture.completedFuture(success);
        }

        return storage.addInternalSapphires(playerUuid, amount).thenApply(v -> true);
    }

    public String formatAmount(double amount) {
        return NUMBER_FORMAT.format(amount).replace(",", " ");
    }

    public String getCurrencyName(String currencyKey) {
        ShopConfig.CurrencyDef def = config.getCurrency(currencyKey);
        return def != null ? def.name() : currencyKey;
    }

    public String getCurrencySymbol(String currencyKey) {
        ShopConfig.CurrencyDef def = config.getCurrency(currencyKey);
        return def != null ? def.symbol() : "";
    }
}
