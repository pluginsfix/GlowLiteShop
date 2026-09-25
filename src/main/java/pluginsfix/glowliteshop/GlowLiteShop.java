package pluginsfix.glowliteshop;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import pluginsfix.glowliteshop.command.ShopCommand;
import pluginsfix.glowliteshop.config.ShopConfig;
import pluginsfix.glowliteshop.currency.CurrencyService;
import pluginsfix.glowliteshop.gui.ShopChestGui;
import pluginsfix.glowliteshop.gui.ShopGui;
import pluginsfix.glowliteshop.hook.PlayerPointsHook;
import pluginsfix.glowliteshop.hook.VaultHook;
import pluginsfix.glowliteshop.listener.GuiListener;
import pluginsfix.glowliteshop.service.ShopChestService;
import pluginsfix.glowliteshop.service.ShopService;
import pluginsfix.glowliteshop.storage.ShopStorage;
import pluginsfix.glowliteshop.storage.SqliteShopStorage;
import pluginsfix.glowliteshop.text.Messages;

public final class GlowLiteShop extends JavaPlugin {
    private ShopStorage storage;
    private ShopConfig shopConfig;
    private Messages messages;
    private ShopService shopService;
    private BukkitTask rotationTask;

    @Override
    public void onEnable() {
        this.shopConfig = new ShopConfig(this);
        this.messages = new Messages(this);

        this.storage = new SqliteShopStorage(getDataFolder(), getLogger());
        this.storage.initialize();

        VaultHook vaultHook = new VaultHook(this);
        PlayerPointsHook playerPointsHook = new PlayerPointsHook();

        CurrencyService currencyService = new CurrencyService(shopConfig, vaultHook, playerPointsHook, storage);
        ShopChestService chestService = new ShopChestService(storage, messages);
        this.shopService = new ShopService(this, shopConfig, currencyService, chestService, messages);

        ShopGui shopGui = new ShopGui(shopConfig, shopService, chestService, currencyService);
        ShopChestGui chestGui = new ShopChestGui(shopConfig, chestService);

        getServer().getPluginManager().registerEvents(
                new GuiListener(shopConfig, shopService, chestService, shopGui, chestGui),
                this
        );

        PluginCommand shopCmd = getCommand("shop");
        if (shopCmd != null) {
            ShopCommand executor = new ShopCommand(shopConfig, shopService, shopGui, chestGui, messages);
            shopCmd.setExecutor(executor);
            shopCmd.setTabCompleter(executor);
        }

        this.rotationTask = getServer().getScheduler().runTaskTimer(this, () -> {
            if (shopService != null) {
                shopService.checkRotationTick();
            }
        }, 40L, 40L);
    }

    @Override
    public void onDisable() {
        if (rotationTask != null) {
            rotationTask.cancel();
        }
        if (shopService != null) {
            shopService.resolvePendingAuctions();
        }
        if (storage != null) {
            storage.close();
        }
    }
}
