package com.fool.duels.listener;

import com.fool.duels.FoolDuels;
import com.fool.duels.data.StateStore;
import com.fool.duels.match.Match;
import com.fool.duels.match.MatchManager;
import com.fool.duels.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Everything that happens to a player while they are in a duel, plus the rules that keep
 * other players out of arenas. Every handler starts with cheap O(1) checks and returns early.
 */
public final class MatchListener implements Listener {
    private final FoolDuels plugin;
    private final MatchManager matches;
    private Set<String> whitelist = Set.of();
    private Set<String> arenaBlockedCommands = Set.of();

    public MatchListener(FoolDuels plugin) {
        this.plugin = plugin;
        this.matches = plugin.matches();
        reload();
    }

    public void reload() {
        whitelist = labels("command-whitelist");
        arenaBlockedCommands = labels("protection.blocked-commands-in-arena");
    }

    private Set<String> labels(String path) {
        Set<String> s = new HashSet<>();
        for (String c : plugin.getConfig().getStringList(path)) s.add(c.toLowerCase(Locale.ROOT).replace("/", ""));
        return s;
    }

    private boolean bypass(Player p) {
        return p.hasPermission("foolduels.bypass");
    }

    // ------------------------------------------------------------------ death / respawn / quit / join

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        matches.handleDeath(e);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        StateStore.Pending pending = plugin.store().get(p.getUniqueId());
        if (pending != null && !matches.inMatch(p.getUniqueId())) {
            Location loc = pending.state().location;
            if (loc != null && loc.getWorld() != null) e.setRespawnLocation(loc);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline() && !p.isDead()) matches.restore(p);
            });
            return;
        }
        // A bed/anchor/spawn point inside an arena must not drop someone into a duel.
        if (!bypass(p) && plugin.arenas().at(e.getRespawnLocation()) != null) {
            e.setRespawnLocation(p.getWorld().getSpawnLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        plugin.requests().clear(e.getPlayer().getUniqueId());
        plugin.queues().leave(e.getPlayer().getUniqueId(), false);
        matches.handleQuit(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.rewards().onJoin(p);
        if (!plugin.store().has(p.getUniqueId())) return;
        // Dead players are restored by the respawn handler.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            if (p.isDead()) p.spigot().respawn();
            else matches.restore(p);
        }, 5L);
    }

    // ------------------------------------------------------------------ damage

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Match m = matches.get(p.getUniqueId());
        if (m != null && m.state() != Match.State.FIGHT) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent e) {
        if (matches.isEmpty() || !(e.getEntity() instanceof Player victim)) return;
        Player attacker = attacker(e.getDamager());
        if (attacker == null || attacker.equals(victim)) return;
        Match mv = matches.get(victim.getUniqueId());
        Match ma = matches.get(attacker.getUniqueId());
        if (mv == null && ma == null) return;
        // Outsiders can't hit duelists, duelists can't hit outsiders or teammates.
        if (mv != ma || mv.teamOf(victim.getUniqueId()) == mv.teamOf(attacker.getUniqueId())) e.setCancelled(true);
    }

    /**
     * WorldGuard (pvp deny), Essentials, faction/claim plugins etc. may cancel PvP in the arena region.
     * Two opponents in the same running duel can always hit each other (same approach as Duels).
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPvpForceAllow(EntityDamageByEntityEvent e) {
        if (!e.isCancelled() || matches.isEmpty() || !(e.getEntity() instanceof Player victim)) return;
        if (!plugin.getConfig().getBoolean("protection.force-allow-pvp", true)) return;
        Player attacker = attacker(e.getDamager());
        if (attacker == null || attacker.equals(victim)) return;
        Match m = matches.get(victim.getUniqueId());
        if (m == null || m.state() != Match.State.FIGHT || matches.get(attacker.getUniqueId()) != m) return;
        if (!m.isAlive(victim.getUniqueId()) || !m.isAlive(attacker.getUniqueId())) return;
        if (m.teamOf(victim.getUniqueId()) == m.teamOf(attacker.getUniqueId())) return;
        e.setCancelled(false);
    }

    private static Player attacker(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    // ------------------------------------------------------------------ movement / teleport

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (matches.isEmpty()) return;
        Location from = e.getFrom(), to = e.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()
                && from.getX() == to.getX() && from.getZ() == to.getZ()) return;
        Match m = matches.get(e.getPlayer().getUniqueId());
        if (m == null) return;
        if (m.state() == Match.State.COUNTDOWN) {
            // Freeze in place during the countdown, but let them look around.
            Location frozen = from.clone();
            frozen.setYaw(to.getYaw());
            frozen.setPitch(to.getPitch());
            e.setTo(frozen);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        if (matches.isForcing(p.getUniqueId())) return; // our own teleports (arena, lobby, restore)
        Match m = matches.get(p.getUniqueId());
        if (m != null) {
            PlayerTeleportEvent.TeleportCause c = e.getCause();
            // Chorus fruit is CHORUS_FRUIT before 1.21.2 and CONSUMABLE_EFFECT after.
            if (c == PlayerTeleportEvent.TeleportCause.ENDER_PEARL || c.name().equals("CHORUS_FRUIT") || c.name().equals("CONSUMABLE_EFFECT")) {
                if (m.arena().hasBounds() && !m.arena().contains(e.getTo())) e.setCancelled(true);
                return;
            }
            // /tpa, /home, /spawn etc. can't pull a player out of a duel.
            e.setCancelled(true);
            return;
        }
        // Nobody gets into an arena with /back, /home, /warp, /tpa to a duelist, etc.
        if (plugin.getConfig().getBoolean("protection.block-teleport-into-arena", true) && !bypass(p)
                && plugin.arenas().at(e.getTo()) != null && plugin.arenas().at(e.getFrom()) == null) {
            e.setCancelled(true);
            Msg.send(p, "arena-teleport-blocked");
        }
    }

    /** Un-cancels our own teleports if another plugin blocked them (Duels' Teleport trick). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleportMonitor(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        if (matches.isForcing(p.getUniqueId())) {
            if (e.isCancelled()) e.setCancelled(false);
            return;
        }
        // Left the lobby on their own (/home, /spawn...): forget the lobby return point and the queue.
        if (!e.isCancelled() && plugin.lobby().inLobby(p.getUniqueId()) && !plugin.lobby().isNearLobby(e.getTo())
                && !matches.inMatch(p.getUniqueId()) && !plugin.store().has(p.getUniqueId())) {
            plugin.lobby().forget(p.getUniqueId());
            plugin.queues().leave(p.getUniqueId(), true);
        }
    }

    // ------------------------------------------------------------------ commands / inventories / items

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (bypass(p)) return;
        boolean inDuel = matches.isProtected(p);
        boolean inArena = !inDuel && !arenaBlockedCommands.isEmpty() && plugin.arenas().at(p.getLocation()) != null;
        if (!inDuel && !inArena) return;
        String label = label(e.getMessage());
        if (inDuel) {
            if (whitelist.contains(label)) return;
            e.setCancelled(true);
            Msg.send(p, "command-blocked");
        } else if (arenaBlockedCommands.contains(label)) {
            // e.g. /sethome or /setwarp inside an arena
            e.setCancelled(true);
            Msg.send(p, "arena-command-blocked");
        }
    }

    private static String label(String message) {
        int space = message.indexOf(' ');
        String label = (space == -1 ? message.substring(1) : message.substring(1, space)).toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        return colon == -1 ? label : label.substring(colon + 1); // "essentials:ah" -> "ah"
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p) || !matches.inMatch(p.getUniqueId())) return;
        if (!plugin.getConfig().getBoolean("protection.block-containers", true)) return;
        InventoryType type = e.getInventory().getType();
        if (type == InventoryType.PLAYER || type == InventoryType.CRAFTING) return;
        e.setCancelled(true);
        Msg.send(p, "container-blocked");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        Match m = matches.get(e.getPlayer().getUniqueId());
        if (m == null || m.ownInventory() || !plugin.getConfig().getBoolean("protection.block-item-drop-kit", true)) return;
        e.setCancelled(true);
        Msg.send(e.getPlayer(), "drop-blocked");
    }
}
