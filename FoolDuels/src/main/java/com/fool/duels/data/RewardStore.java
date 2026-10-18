package com.fool.duels.data;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Runs reward commands as console. If the player is offline the commands are saved
 * (rewards.yml) and run the next time they join, so nobody loses a tournament key.
 */
public final class RewardStore {
    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, List<String>> pending = new HashMap<>();

    public RewardStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "rewards.yml");
    }

    public void load() {
        pending.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String k : y.getKeys(false)) {
            try {
                pending.put(UUID.fromString(k), new ArrayList<>(y.getStringList(k)));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        pending.forEach((k, v) -> y.set(k.toString(), v));
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "rewards.yml kaydedilemedi", e);
        }
    }

    /** Placeholders: %player%, %uuid%, plus any extra key/value pairs (e.g. "%place%", "1"). */
    public void give(UUID id, List<String> commands, String... extra) {
        if (commands == null || commands.isEmpty()) return;
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        String name = op.getName() == null ? id.toString() : op.getName();
        List<String> resolved = new ArrayList<>();
        for (String c : commands) {
            String r = c.replace("%player%", name).replace("%uuid%", id.toString());
            for (int i = 0; i + 1 < extra.length; i += 2) r = r.replace(extra[i], extra[i + 1]);
            resolved.add(r.startsWith("/") ? r.substring(1) : r);
        }
        Player p = op.getPlayer();
        if (p != null && p.isOnline()) run(resolved);
        else {
            pending.computeIfAbsent(id, k -> new ArrayList<>()).addAll(resolved);
            save();
        }
    }

    public void onJoin(Player p) {
        List<String> cmds = pending.remove(p.getUniqueId());
        if (cmds == null) return;
        save();
        Bukkit.getScheduler().runTaskLater(plugin, () -> run(cmds), 40L);
    }

    private void run(List<String> commands) {
        for (String c : commands) {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Ödül komutu çalıştırılamadı: " + c, e);
            }
        }
    }
}
