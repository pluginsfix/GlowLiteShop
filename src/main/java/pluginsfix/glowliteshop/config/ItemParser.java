package pluginsfix.glowliteshop.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pluginsfix.glowliteshop.text.TextUtil;

import java.util.ArrayList;
import java.util.List;

public final class ItemParser {
    private ItemParser() {
    }

    public static ItemStack parseItem(ConfigurationSection section) {
        if (section == null) {
            return new ItemStack(Material.AIR);
        }

        String matName = section.getString("material", "STONE");
        Material material = Material.matchMaterial(matName);
        if (material == null) {
            material = Material.STONE;
        }

        int amount = section.getInt("amount", 1);
        ItemStack item = new ItemStack(material, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        if (section.isString("name")) {
            String rawName = section.getString("name");
            meta.displayName(TextUtil.parseComponent(rawName).decoration(TextDecoration.ITALIC, false));
        }

        if (section.isList("lore")) {
            List<String> rawLore = section.getStringList("lore");
            List<Component> lore = new ArrayList<>(rawLore.size());
            for (String line : rawLore) {
                lore.add(TextUtil.parseComponent(line).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
        }

        if (section.isInt("custom-model-data")) {
            int cmd = section.getInt("custom-model-data");
            if (cmd > 0) {
                meta.setCustomModelData(cmd);
            }
        }

        if (section.isConfigurationSection("enchantments")) {
            ConfigurationSection enchSec = section.getConfigurationSection("enchantments");
            if (enchSec != null) {
                for (String enchKey : enchSec.getKeys(false)) {
                    int level = enchSec.getInt(enchKey, 1);
                    Enchantment ench = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(enchKey.toLowerCase()));
                    if (ench != null) {
                        meta.addEnchant(ench, level, true);
                    }
                }
            }
        }

        if (section.getBoolean("enchanted", false)) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack createButton(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null && !name.isEmpty()) {
                meta.displayName(TextUtil.parseComponent(name).decoration(TextDecoration.ITALIC, false));
            }
            if (lore != null && !lore.isEmpty()) {
                List<Component> loreComponents = new ArrayList<>(lore.size());
                for (String line : lore) {
                    loreComponents.add(TextUtil.parseComponent(line).decoration(TextDecoration.ITALIC, false));
                }
                meta.lore(loreComponents);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }
}
