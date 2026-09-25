package pluginsfix.glowliteshop.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import pluginsfix.glowliteshop.domain.PurchaseMode;
import pluginsfix.glowliteshop.domain.ShopProduct;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ShopConfig {
    public record CurrencyDef(String name, String symbol, String type) {}
    public record NavButton(int slot, Material material, String name, List<String> lore, String command) {}

    private final JavaPlugin plugin;
    private final Map<String, CurrencyDef> currencies = new HashMap<>();
    private final List<Integer> activeSlots = new ArrayList<>();
    private final Map<String, ShopProduct> products = new HashMap<>();

    private int rotationIntervalMinutes;
    private boolean earlyWipe;
    private int earlyWipeIntervalMinutes;

    private String guiTitle;
    private int guiSize;
    private Material guiFillerMaterial;
    private String guiFillerName;

    private NavButton kitsButton;
    private NavButton boostersButton;
    private NavButton chestButton;
    private NavButton rotationIndicator;

    private String chestGuiTitle;
    private int chestGuiSize;
    private Material chestFillerMaterial;
    private String chestFillerName;
    private NavButton claimAllButton;
    private NavButton backButton;

    public ShopConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.exists()) {
            plugin.saveResource("config.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        currencies.clear();
        ConfigurationSection curSec = config.getConfigurationSection("currencies");
        if (curSec != null) {
            for (String key : curSec.getKeys(false)) {
                ConfigurationSection sec = curSec.getConfigurationSection(key);
                if (sec != null) {
                    currencies.put(key.toLowerCase(), new CurrencyDef(
                            sec.getString("name", key),
                            sec.getString("symbol", ""),
                            sec.getString("type", "vault")
                    ));
                }
            }
        }

        this.rotationIntervalMinutes = config.getInt("rotation.interval-minutes", 120);
        this.earlyWipe = config.getBoolean("rotation.early-wipe", false);
        this.earlyWipeIntervalMinutes = config.getInt("rotation.early-wipe-interval-minutes", 30);

        activeSlots.clear();
        List<Integer> slots = config.getIntegerList("rotation.active-slots");
        if (slots.isEmpty()) {
            activeSlots.addAll(List.of(11, 12, 13, 14, 15, 20, 21, 22, 23, 24));
        } else {
            activeSlots.addAll(slots);
        }

        this.guiTitle = config.getString("gui.title", "&#FFFF00◆ &fМагазин &8| &#FFFF00HolyLite");
        this.guiSize = config.getInt("gui.size", 54);
        this.guiFillerMaterial = parseMaterial(config.getString("gui.filler.material", "BLACK_STAINED_GLASS_PANE"));
        this.guiFillerName = config.getString("gui.filler.name", " ");

        this.kitsButton = parseNavButton(config.getConfigurationSection("gui.navigation.kits"), 47, Material.NETHERITE_SWORD, "kits");
        this.boostersButton = parseNavButton(config.getConfigurationSection("gui.navigation.boosters"), 49, Material.BEACON, "boosters");
        this.chestButton = parseNavButton(config.getConfigurationSection("gui.navigation.chest"), 51, Material.ENDER_CHEST, "");
        this.rotationIndicator = parseNavButton(config.getConfigurationSection("gui.navigation.rotation-indicator"), 4, Material.CLOCK, "");

        this.chestGuiTitle = config.getString("chest-gui.title", "&#FFFF00◆ &fХранилище покупок");
        this.chestGuiSize = config.getInt("chest-gui.size", 54);
        this.chestFillerMaterial = parseMaterial(config.getString("chest-gui.filler.material", "BLACK_STAINED_GLASS_PANE"));
        this.chestFillerName = config.getString("chest-gui.filler.name", " ");
        this.claimAllButton = parseNavButton(config.getConfigurationSection("chest-gui.claim-all"), 49, Material.HOPPER, "");
        this.backButton = parseNavButton(config.getConfigurationSection("chest-gui.back"), 45, Material.ARROW, "");

        products.clear();
        ConfigurationSection prodSec = config.getConfigurationSection("products");
        if (prodSec != null) {
            for (String key : prodSec.getKeys(false)) {
                ConfigurationSection sec = prodSec.getConfigurationSection(key);
                if (sec != null) {
                    ItemStack itemStack = ItemParser.parseItem(sec);
                    PurchaseMode mode = PurchaseMode.fromString(sec.getString("purchase-mode", "DIRECT"));
                    String currency = sec.getString("currency", "coins").toLowerCase();
                    double directPrice = sec.getDouble("direct-price", 0.0);
                    double auctionMinBid = sec.getDouble("auction-min-bid", 0.0);
                    double auctionBidStep = sec.getDouble("auction-bid-step", 0.0);

                    products.put(key, new ShopProduct(
                            key,
                            itemStack,
                            mode,
                            currency,
                            directPrice,
                            auctionMinBid,
                            auctionBidStep
                    ));
                }
            }
        }
    }

    private NavButton parseNavButton(ConfigurationSection section, int defaultSlot, Material defaultMat, String defaultCmd) {
        if (section == null) {
            return new NavButton(defaultSlot, defaultMat, "", Collections.emptyList(), defaultCmd);
        }
        int slot = section.getInt("slot", defaultSlot);
        Material mat = parseMaterial(section.getString("material", defaultMat.name()));
        String name = section.getString("name", "");
        List<String> lore = section.getStringList("lore");
        String cmd = section.getString("command", defaultCmd);
        return new NavButton(slot, mat, name, lore, cmd);
    }

    private Material parseMaterial(String name) {
        if (name == null) {
            return Material.AIR;
        }
        Material mat = Material.matchMaterial(name);
        return mat != null ? mat : Material.STONE;
    }

    public CurrencyDef getCurrency(String key) {
        return currencies.get(key != null ? key.toLowerCase() : "coins");
    }

    public int getEffectiveRotationIntervalMinutes() {
        return earlyWipe ? earlyWipeIntervalMinutes : rotationIntervalMinutes;
    }

    public int rotationIntervalMinutes() {
        return rotationIntervalMinutes;
    }

    public boolean earlyWipe() {
        return earlyWipe;
    }

    public void setEarlyWipe(boolean earlyWipe) {
        this.earlyWipe = earlyWipe;
    }

    public int earlyWipeIntervalMinutes() {
        return earlyWipeIntervalMinutes;
    }

    public List<Integer> activeSlots() {
        return activeSlots;
    }

    public String guiTitle() {
        return guiTitle;
    }

    public int guiSize() {
        return guiSize;
    }

    public Material guiFillerMaterial() {
        return guiFillerMaterial;
    }

    public String guiFillerName() {
        return guiFillerName;
    }

    public NavButton kitsButton() {
        return kitsButton;
    }

    public NavButton boostersButton() {
        return boostersButton;
    }

    public NavButton chestButton() {
        return chestButton;
    }

    public NavButton rotationIndicator() {
        return rotationIndicator;
    }

    public String chestGuiTitle() {
        return chestGuiTitle;
    }

    public int chestGuiSize() {
        return chestGuiSize;
    }

    public Material chestFillerMaterial() {
        return chestFillerMaterial;
    }

    public String chestFillerName() {
        return chestFillerName;
    }

    public NavButton claimAllButton() {
        return claimAllButton;
    }

    public NavButton backButton() {
        return backButton;
    }

    public Map<String, ShopProduct> products() {
        return products;
    }
}
