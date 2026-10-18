package com.fool.duels.kit;

import com.fool.duels.util.Items;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

public final class KitManager {
    public static final class Kit {
        public final String name;
        public Material icon;
        public final ItemStack[] contents;

        Kit(String name, Material icon, ItemStack[] contents) {
            this.name = name;
            this.icon = icon;
            this.contents = contents;
        }
    }

    private final JavaPlugin plugin;
    private final File file;
    private final NamespacedKey kitItemKey;
    private final Map<String, Kit> kits = new LinkedHashMap<>();

    public KitManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "kits.yml");
        this.kitItemKey = new NamespacedKey(plugin, "kit_item");
    }

    public void load() {
        kits.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String name : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(name);
            if (s == null) continue;
            Material icon = Material.matchMaterial(s.getString("icon", "IRON_SWORD"));
            kits.put(name.toLowerCase(), new Kit(name, icon == null ? Material.IRON_SWORD : icon,
                    Items.decodeAll(s.getStringList("items"), 41)));
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Kit k : kits.values()) {
            y.set(k.name + ".icon", k.icon.name());
            y.set(k.name + ".items", Items.encodeAll(k.contents));
        }
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "kits.yml kaydedilemedi", e);
        }
    }

    public Kit get(String name) {
        return name == null ? null : kits.get(name.toLowerCase());
    }

    public Collection<Kit> all() {
        return Collections.unmodifiableCollection(kits.values());
    }

    public Kit createFrom(String name, Player p) {
        ItemStack[] src = p.getInventory().getContents();
        ItemStack[] copy = new ItemStack[src.length];
        for (int i = 0; i < src.length; i++) copy[i] = src[i] == null ? null : src[i].clone();
        ItemStack hand = p.getInventory().getItemInMainHand();
        Kit k = new Kit(name, hand.getType().isAir() ? Material.IRON_SWORD : hand.getType(), copy);
        kits.put(name.toLowerCase(), k);
        save();
        return k;
    }

    public boolean delete(String name) {
        boolean removed = kits.remove(name.toLowerCase()) != null;
        if (removed) save();
        return removed;
    }

    /** Equips a kit. Every item is tagged so it can be wiped if it ever ends up outside a duel. */
    public void equip(Player p, Kit kit) {
        ItemStack[] out = new ItemStack[kit.contents.length];
        for (int i = 0; i < out.length; i++) {
            ItemStack item = kit.contents[i];
            if (item == null) continue;
            item = item.clone();
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(kitItemKey, PersistentDataType.BYTE, (byte) 1);
                item.setItemMeta(meta);
            }
            out[i] = item;
        }
        p.getInventory().setContents(out);
        p.updateInventory();
    }

    public boolean isKitItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(kitItemKey, PersistentDataType.BYTE);
    }
}
