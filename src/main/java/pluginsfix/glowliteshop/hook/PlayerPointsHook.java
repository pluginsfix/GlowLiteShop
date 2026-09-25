package pluginsfix.glowliteshop.hook;

import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.UUID;

public final class PlayerPointsHook {
    private PlayerPointsAPI api;

    public PlayerPointsHook() {
        tryHook();
    }

    private void tryHook() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("PlayerPoints");
        if (plugin instanceof PlayerPoints pp) {
            this.api = pp.getAPI();
        }
    }

    public Optional<PlayerPointsAPI> getApi() {
        if (this.api == null) {
            tryHook();
        }
        return Optional.ofNullable(this.api);
    }

    public boolean isAvailable() {
        return getApi().isPresent();
    }

    public int look(UUID uuid) {
        return getApi().map(a -> a.look(uuid)).orElse(0);
    }

    public boolean take(UUID uuid, int amount) {
        return getApi().map(a -> a.take(uuid, amount)).orElse(false);
    }

    public boolean give(UUID uuid, int amount) {
        return getApi().map(a -> a.give(uuid, amount)).orElse(false);
    }
}
