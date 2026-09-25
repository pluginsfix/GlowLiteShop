package pluginsfix.glowliteshop.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import pluginsfix.glowliteshop.config.ShopConfig;
import pluginsfix.glowliteshop.gui.ShopChestGui;
import pluginsfix.glowliteshop.gui.ShopGui;
import pluginsfix.glowliteshop.service.ShopService;
import pluginsfix.glowliteshop.text.Messages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ShopCommand implements CommandExecutor, TabCompleter {
    private final ShopConfig config;
    private final ShopService shopService;
    private final ShopGui shopGui;
    private final ShopChestGui chestGui;
    private final Messages messages;

    public ShopCommand(ShopConfig config, ShopService shopService, ShopGui shopGui, ShopChestGui chestGui, Messages messages) {
        this.config = config;
        this.shopService = shopService;
        this.shopGui = shopGui;
        this.chestGui = chestGui;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("glowliteshop.use")) {
            messages.send(sender, "no-permission");
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "player-only");
                return true;
            }
            messages.send(player, "shop-opened");
            shopGui.open(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("chest") || sub.equals("storage")) {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "player-only");
                return true;
            }
            messages.send(player, "chest-opened");
            chestGui.open(player);
            return true;
        }

        if (sub.equals("bid")) {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "player-only");
                return true;
            }
            if (args.length < 3) {
                messages.send(sender, "usage");
                return true;
            }
            try {
                int slot = Integer.parseInt(args[1]);
                double amount = Double.parseDouble(args[2]);
                if (amount <= 0) {
                    messages.send(player, "invalid-amount");
                    return true;
                }
                shopService.placeBid(player, slot, amount, () -> {});
            } catch (NumberFormatException e) {
                messages.send(player, "invalid-amount");
            }
            return true;
        }

        if (sub.equals("earlywipe") || sub.equals("wipe")) {
            if (!sender.hasPermission("glowliteshop.admin.earlywipe")) {
                messages.send(sender, "no-permission");
                return true;
            }
            boolean newState;
            if (args.length > 1) {
                newState = args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("true");
            } else {
                newState = !shopService.isEarlyWipe();
            }
            shopService.setEarlyWipe(newState);
            if (newState) {
                messages.send(sender, "early-wipe-enabled");
            } else {
                messages.send(sender, "early-wipe-disabled");
            }
            return true;
        }

        if (sub.equals("forcerotate") || sub.equals("rotate")) {
            if (!sender.hasPermission("glowliteshop.admin.rotate")) {
                messages.send(sender, "no-permission");
                return true;
            }
            shopService.rotateAssortment();
            messages.send(sender, "force-rotated");
            return true;
        }

        if (sub.equals("reload")) {
            if (!sender.hasPermission("glowliteshop.admin.reload")) {
                messages.send(sender, "no-permission");
                return true;
            }
            config.reload();
            messages.reload();
            messages.send(sender, "reloaded");
            return true;
        }

        messages.send(sender, "usage");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            if (sender.hasPermission("glowliteshop.use")) {
                completions.add("chest");
            }
            if (sender.hasPermission("glowliteshop.admin.earlywipe")) {
                completions.add("earlywipe");
            }
            if (sender.hasPermission("glowliteshop.admin.rotate")) {
                completions.add("forcerotate");
            }
            if (sender.hasPermission("glowliteshop.admin.reload")) {
                completions.add("reload");
            }

            String current = args[0].toLowerCase();
            return completions.stream()
                    .filter(s -> s.startsWith(current))
                    .toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("earlywipe")) {
            if (sender.hasPermission("glowliteshop.admin.earlywipe")) {
                List<String> opts = List.of("on", "off");
                String current = args[1].toLowerCase();
                return opts.stream().filter(s -> s.startsWith(current)).toList();
            }
        }

        return Collections.emptyList();
    }
}
