package com.fool.duels.arena;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public final class ArenaManager {
    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();

    public ArenaManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arenas.yml");
    }

    public void load() {
        arenas.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String name : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(name);
            if (s == null) continue;
            Arena a = new Arena(name);
            a.enabled(s.getBoolean("enabled", true));
            for (Object o : s.getList("spawns-1", List.of())) if (o instanceof Location l) a.spawns(0).add(l);
            for (Object o : s.getList("spawns-2", List.of())) if (o instanceof Location l) a.spawns(1).add(l);
            a.pos1(s.getLocation("pos1"));
            a.pos2(s.getLocation("pos2"));
            arenas.put(name.toLowerCase(), a);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Arena a : arenas.values()) {
            ConfigurationSection s = y.createSection(a.name());
            s.set("enabled", a.enabled());
            s.set("spawns-1", new ArrayList<>(a.spawns(0)));
            s.set("spawns-2", new ArrayList<>(a.spawns(1)));
            s.set("pos1", a.pos1());
            s.set("pos2", a.pos2());
        }
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "arenas.yml kaydedilemedi", e);
        }
    }

    public Arena get(String name) {
        return name == null ? null : arenas.get(name.toLowerCase());
    }

    public Arena create(String name) {
        Arena a = new Arena(name);
        arenas.put(name.toLowerCase(), a);
        return a;
    }

    public boolean delete(String name) {
        return arenas.remove(name.toLowerCase()) != null;
    }

    public Collection<Arena> all() {
        return Collections.unmodifiableCollection(arenas.values());
    }

    /** A random free, enabled arena, so matches spread out instead of always using the first one. */
    public Arena findFree() {
        List<Arena> free = new ArrayList<>();
        for (Arena a : arenas.values()) if (a.available()) free.add(a);
        if (free.isEmpty()) return null;
        return free.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(free.size()));
    }

    /** The arena whose bounds contain the given block, or null. */
    public Arena at(World w, int x, int y, int z) {
        for (Arena a : arenas.values()) if (a.contains(w, x, y, z)) return a;
        return null;
    }

    public Arena at(Location l) {
        return l == null ? null : at(l.getWorld(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    public boolean anyBounds() {
        for (Arena a : arenas.values()) if (a.hasBounds()) return true;
        return false;
    }
}
