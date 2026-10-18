package com.fool.duels.lobby;

import com.fool.duels.FoolDuels;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The shared waiting / spectating lobby. Players in the queue, tournament players between their
 * matches and spectators stand here. Where each player came from is saved (and persisted), so
 * /duel ayril sends them back.
 */
public final class LobbyManager {
    private final FoolDuels plugin;
    private final File file;
    private Location lobby;
    private final Map<UUID, Location> returns = new HashMap<>();

    public LobbyManager(FoolDuels plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "lobby.yml");
    }

    public void load() {
        returns.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        lobby = y.getLocation("lobby");
        ConfigurationSection s = y.getConfigurationSection("returns");
        if (s != null) for (String k : s.getKeys(false)) {
            Location l = s.getLocation(k);
            try {
                if (l != null) returns.put(UUID.fromString(k), l);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("lobby", lobby);
        returns.forEach((k, v) -> y.set("returns." + k, v));
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "lobby.yml kaydedilemedi", e);
        }
    }

    public boolean hasLobby() {
        return lobby != null && lobby.getWorld() != null;
    }

    public Location lobby() {
        return lobby == null ? null : lobby.clone();
    }

    public void setLobby(Location l) {
        lobby = l.clone();
        save();
    }

    public boolean inLobby(UUID id) {
        return returns.containsKey(id);
    }

    /** Sends the player to the lobby, remembering where they came from (only the first time). */
    public boolean join(Player p) {
        if (!hasLobby()) return false;
        if (!returns.containsKey(p.getUniqueId())) {
            returns.put(p.getUniqueId(), p.getLocation().clone());
            save();
        }
        plugin.matches().teleport(p, lobby());
        return true;
    }

    /** Sends the player back to where they were before the lobby. */
    public boolean leave(Player p) {
        Location back = returns.remove(p.getUniqueId());
        if (back == null) return false;
        save();
        plugin.matches().teleport(p, back);
        return true;
    }

    /** The player went somewhere else on their own (/home, /spawn...): forget the return point. */
    public void forget(UUID id) {
        if (returns.remove(id) != null) save();
    }

    /** True if the location is the lobby or right next to it. */
    public boolean isNearLobby(Location l) {
        return hasLobby() && l != null && l.getWorld() == lobby.getWorld() && l.distanceSquared(lobby) <= 64 * 64;
    }
}
