package pluginsfix.glowliteshop.text;

import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public final class Messages {
    private final JavaPlugin plugin;
    private final Logger logger;
    private final Map<String, List<String>> messageMap = new HashMap<>();

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        messageMap.clear();

        for (String key : config.getKeys(false)) {
            if (config.isList(key)) {
                messageMap.put(key, config.getStringList(key));
            } else if (config.isString(key)) {
                messageMap.put(key, Collections.singletonList(config.getString(key)));
            }
        }
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Collections.emptyMap());
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        List<String> lines = messageMap.get(key);
        if (lines == null || lines.isEmpty()) {
            logger.warning("Missing message key in messages.yml: " + key);
            return;
        }

        for (String rawLine : lines) {
            String line = replacePlaceholders(rawLine, placeholders);
            dispatchLine(sender, line);
        }
    }

    private void dispatchLine(CommandSender sender, String line) {
        if (line.startsWith("[message] ")) {
            String content = line.substring(10);
            Component component = TextUtil.parseComponent(content);
            sender.sendMessage(component);
            return;
        }

        if (line.startsWith("[actionbar] ")) {
            if (sender instanceof Player player) {
                String content = line.substring(12);
                Component component = TextUtil.parseComponent(content);
                player.sendActionBar(component);
            }
            return;
        }

        if (line.startsWith("[sound] ")) {
            if (sender instanceof Player player) {
                String soundName = line.substring(8).trim();
                playSound(player, soundName);
            }
            return;
        }

        Component component = TextUtil.parseComponent(line);
        sender.sendMessage(component);
    }

    private void playSound(Player player, String soundName) {
        try {
            Sound sound = Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid sound name in messages.yml: " + soundName);
        }
    }

    private String replacePlaceholders(String text, Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) {
            return text;
        }
        String result = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue())
                           .replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return result;
    }

    public List<String> getRawList(String key) {
        return messageMap.getOrDefault(key, Collections.emptyList());
    }
}
