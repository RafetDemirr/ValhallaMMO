package com.fool.duels.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * ELO and win/loss stats. Kept in memory, saved asynchronously when something changed
 * (at most once per minute) and on shutdown.
 */
public final class StatsManager {
    public static final class Stats {
        public String name;
        public int elo;
        public int wins;
        public int losses;

        Stats(String name, int elo) {
            this.name = name;
            this.elo = elo;
        }
    }

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, Stats> stats = new HashMap<>();
    private int defaultElo = 1000;
    private int kFactor = 32;
    private boolean dirty;

    public StatsManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
    }

    public void configure(int defaultElo, int kFactor) {
        this.defaultElo = defaultElo;
        this.kFactor = kFactor;
    }

    public void load() {
        stats.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String key : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(key);
            if (s == null) continue;
            try {
                Stats st = new Stats(s.getString("name", "?"), s.getInt("elo", defaultElo));
                st.wins = s.getInt("wins");
                st.losses = s.getInt("losses");
                stats.put(UUID.fromString(key), st);
            } catch (IllegalArgumentException ignored) {
            }
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                if (dirty) saveAsync();
            }
        }.runTaskTimer(plugin, 1200L, 1200L);
    }

    public Stats get(UUID id, String name) {
        Stats s = stats.computeIfAbsent(id, k -> new Stats(name, defaultElo));
        if (name != null) s.name = name;
        return s;
    }

    public Stats find(UUID id) {
        return stats.get(id);
    }

    public int elo(UUID id) {
        Stats s = stats.get(id);
        return s == null ? defaultElo : s.elo;
    }

    /**
     * Standard ELO update using team averages. Every winner gains and every loser loses the same amount.
     *
     * @return the rating change (always >= 1 when elo is applied)
     */
    public int record(Map<UUID, String> winners, Map<UUID, String> losers, boolean applyElo) {
        double wAvg = average(winners.keySet());
        double lAvg = average(losers.keySet());
        int change = 0;
        if (applyElo) {
            double expected = 1.0 / (1.0 + Math.pow(10, (lAvg - wAvg) / 400.0));
            change = Math.max(1, (int) Math.round(kFactor * (1 - expected)));
        }
        for (Map.Entry<UUID, String> e : winners.entrySet()) {
            Stats s = get(e.getKey(), e.getValue());
            s.wins++;
            s.elo += change;
        }
        for (Map.Entry<UUID, String> e : losers.entrySet()) {
            Stats s = get(e.getKey(), e.getValue());
            s.losses++;
            s.elo = Math.max(0, s.elo - change);
        }
        dirty = true;
        return change;
    }

    public void set(UUID id, String name, int elo) {
        get(id, name).elo = Math.max(0, elo);
        dirty = true;
    }

    private double average(Collection<UUID> ids) {
        if (ids.isEmpty()) return defaultElo;
        double sum = 0;
        for (UUID id : ids) sum += elo(id);
        return sum / ids.size();
    }

    public List<Map.Entry<UUID, Stats>> top(int limit) {
        List<Map.Entry<UUID, Stats>> list = new ArrayList<>(stats.entrySet());
        list.sort(Comparator.comparingInt((Map.Entry<UUID, Stats> e) -> e.getValue().elo).reversed());
        return list.subList(0, Math.min(limit, list.size()));
    }

    private YamlConfiguration snapshot() {
        YamlConfiguration y = new YamlConfiguration();
        stats.forEach((id, s) -> {
            String k = id.toString();
            y.set(k + ".name", s.name);
            y.set(k + ".elo", s.elo);
            y.set(k + ".wins", s.wins);
            y.set(k + ".losses", s.losses);
        });
        return y;
    }

    private void saveAsync() {
        dirty = false;
        String data = snapshot().saveToString();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> write(data));
    }

    public void saveNow() {
        write(snapshot().saveToString());
        dirty = false;
    }

    private synchronized void write(String data) {
        try {
            java.nio.file.Files.writeString(file.toPath(), data);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "stats.yml kaydedilemedi", e);
        }
    }
}
