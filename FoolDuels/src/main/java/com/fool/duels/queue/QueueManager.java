package com.fool.duels.queue;

import com.fool.duels.FoolDuels;
import com.fool.duels.kit.KitManager;
import com.fool.duels.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 1v1 ranked queue per kit ("kendi" = own inventory). Players are matched with the closest ELO;
 * the allowed ELO gap grows the longer they wait. Queued players wait in the lobby.
 */
public final class QueueManager {
    public static final String OWN = "kendi";

    private final FoolDuels plugin;
    /** queue key -> (player -> join time) */
    private final Map<String, LinkedHashMap<UUID, Long>> queues = new HashMap<>();
    private final Map<UUID, String> byPlayer = new HashMap<>();

    public QueueManager(FoolDuels plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 40L);
    }

    public String queueOf(UUID id) {
        return byPlayer.get(id);
    }

    public int size(String key) {
        LinkedHashMap<UUID, Long> q = queues.get(key == null ? null : key.toLowerCase());
        return q == null ? 0 : q.size();
    }

    public int totalSize() {
        return byPlayer.size();
    }

    public void join(Player p, String key) {
        if (!plugin.getConfig().getBoolean("queue.enabled", true)) {
            Msg.send(p, "queue.disabled");
            return;
        }
        key = key.toLowerCase();
        KitManager.Kit kit = null;
        if (!key.equals(OWN)) {
            kit = plugin.kits().get(key);
            if (kit == null) {
                Msg.send(p, "queue.unknown", "queue", key);
                return;
            }
        } else if (!plugin.getConfig().getBoolean("duel.allow-own-inventory", true)) {
            Msg.send(p, "queue.unknown", "queue", key);
            return;
        }
        if (plugin.matches().inMatch(p.getUniqueId()) || plugin.store().has(p.getUniqueId())) {
            Msg.send(p, "in-match");
            return;
        }
        if (plugin.parties().get(p.getUniqueId()) != null) {
            Msg.send(p, "queue.in-party");
            return;
        }
        if (plugin.combat().isInCombat(p)) {
            Msg.send(p, "in-combat", "player", p.getName());
            return;
        }
        leave(p.getUniqueId(), false);
        queues.computeIfAbsent(key, k -> new LinkedHashMap<>()).put(p.getUniqueId(), System.currentTimeMillis());
        byPlayer.put(p.getUniqueId(), key);
        plugin.lobby().join(p);
        Msg.send(p, "queue.joined", "queue", kit == null ? "Kendi Envanteri" : kit.name, "count", size(key));
    }

    /** @return true if the player was queued */
    public boolean leave(UUID id, boolean message) {
        String key = byPlayer.remove(id);
        if (key == null) return false;
        LinkedHashMap<UUID, Long> q = queues.get(key);
        if (q != null) q.remove(id);
        Player p = Bukkit.getPlayer(id);
        if (message && p != null) Msg.send(p, "queue.left");
        return true;
    }

    public List<String> keys() {
        List<String> out = new ArrayList<>();
        if (plugin.getConfig().getBoolean("duel.allow-own-inventory", true)) out.add(OWN);
        for (KitManager.Kit k : plugin.kits().all()) out.add(k.name.toLowerCase());
        return out;
    }

    private void tick() {
        if (byPlayer.isEmpty()) return;
        long now = System.currentTimeMillis();
        double base = plugin.getConfig().getDouble("queue.elo-range", 100);
        double growth = plugin.getConfig().getDouble("queue.elo-range-growth-per-second", 10);
        double maxRange = plugin.getConfig().getDouble("queue.max-elo-range", 1000);

        for (Map.Entry<String, LinkedHashMap<UUID, Long>> e : queues.entrySet()) {
            LinkedHashMap<UUID, Long> q = e.getValue();
            // drop players that went offline or got busy
            for (Iterator<UUID> it = q.keySet().iterator(); it.hasNext(); ) {
                UUID id = it.next();
                Player p = Bukkit.getPlayer(id);
                if (p == null || plugin.matches().inMatch(id) || plugin.store().has(id)) {
                    it.remove();
                    byPlayer.remove(id);
                }
            }
            if (q.size() < 2) continue;

            List<UUID> sorted = new ArrayList<>(q.keySet());
            sorted.sort(Comparator.comparingInt(plugin.stats()::elo));
            KitManager.Kit kit = e.getKey().equals(OWN) ? null : plugin.kits().get(e.getKey());
            if (kit == null && !e.getKey().equals(OWN)) continue;

            for (int i = 0; i + 1 < sorted.size(); i++) {
                UUID a = sorted.get(i), b = sorted.get(i + 1);
                double rangeA = Math.min(maxRange, base + growth * (now - q.get(a)) / 1000.0);
                double rangeB = Math.min(maxRange, base + growth * (now - q.get(b)) / 1000.0);
                int diff = Math.abs(plugin.stats().elo(a) - plugin.stats().elo(b));
                if (diff > Math.min(rangeA, rangeB)) continue;

                Player pa = Bukkit.getPlayer(a), pb = Bukkit.getPlayer(b);
                String[] failed = new String[1];
                String error = plugin.matches().start(List.of(pa), List.of(pb), 0, kit, null, failed);
                if ("no-free-arena".equals(error)) break; // try again next tick
                if (error != null) {
                    // one of them can't play right now (combat tag etc.); they stay queued
                    continue;
                }
                q.remove(a);
                q.remove(b);
                byPlayer.remove(a);
                byPlayer.remove(b);
                i++;
            }
        }
    }
}
