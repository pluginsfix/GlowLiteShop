package pluginsfix.glowliteshop.hook;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Optional;

public final class VaultHook implements Listener {
    private final Plugin plugin;
    private Economy economy;

    public VaultHook(Plugin plugin) {
        this.plugin = plugin;
        tryHook();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void tryHook() {
        if (this.economy != null) {
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return;
        }

        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            this.economy = rsp.getProvider();
        }
    }

    @EventHandler
    public void onServiceRegister(ServiceRegisterEvent event) {
        if (event.getProvider().getService().equals(Economy.class)) {
            tryHook();
        }
    }

    public Optional<Economy> getEconomy() {
        if (this.economy == null) {
            tryHook();
        }
        return Optional.ofNullable(this.economy);
    }

    public boolean isAvailable() {
        return getEconomy().isPresent();
    }
}
