package com.fool.duels.match;

import com.fool.duels.FoolDuels;
import com.fool.duels.arena.Arena;
import com.fool.duels.data.PlayerState;
import com.fool.duels.data.StateStore;
import com.fool.duels.kit.KitManager;
import com.fool.duels.util.Msg;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.potion.PotionEffect;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Owns every running match. Design notes (borrowed from Duels / BattleArena):
 * <ul>
 *   <li>One repeating task for all matches instead of one task per match.</li>
 *   <li>All lookups are UUID hash lookups so event listeners can bail out immediately.</li>
 *   <li>The pre-duel state is written to disk before the player is touched (crash-safe).</li>
 *   <li>Quitting a match kills the player so every elimination goes through the same death path,
 *       and the player is restored on respawn or on their next join.</li>
 * </ul>
 */
public final class MatchManager {
    private final FoolDuels plugin;
    private final Map<UUID, Match> byPlayer = new HashMap<>();
    private final List<Match> matches = new ArrayList<>();
    private final Set<UUID> quitting = new HashSet<>();
    private final Set<UUID> forceTeleport = new HashSet<>();
    private int nextId = 1;
    private int ticker = -1;

    public MatchManager(FoolDuels plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ queries

    public Match get(UUID player) {
        return byPlayer.get(player);
    }

    public boolean inMatch(UUID player) {
        return byPlayer.containsKey(player);
    }

    public boolean isEmpty() {
        return matches.isEmpty();
    }

    public List<Match> matches() {
        return matches;
    }

    public boolean isQuitting(UUID id) {
        return quitting.contains(id);
    }

    public boolean consumeForceTeleport(UUID id) {
        return forceTeleport.remove(id);
    }

    /** True for players that must not be combat-punished, use blocked commands, etc. */
    public boolean isProtected(Player p) {
        return byPlayer.containsKey(p.getUniqueId()) || plugin.store().has(p.getUniqueId());
    }

    // ------------------------------------------------------------------ start

    /**
     * Starts a match. Returns null on success, otherwise the message key explaining the failure
     * (and the second element of {@code failedPlayer} is filled with the player's name when relevant).
     */
    public String start(List<Player> teamA, List<Player> teamB, double bet, KitManager.Kit kit,
                        Match.Callback callback, String[] failedPlayer) {
        List<Player> everyone = new ArrayList<>(teamA);
        everyone.addAll(teamB);

        for (Player p : everyone) {
            failedPlayer[0] = p.getName();
            if (!p.isOnline() || p.isDead() || plugin.store().has(p.getUniqueId())) return "player-not-found";
            if (inMatch(p.getUniqueId())) return "target-in-match";
            if (plugin.combat().isInCombat(p)) return "in-combat";
        }
        Arena arena = plugin.arenas().findFree();
        if (arena == null) return "no-free-arena";

        // Take bets first; roll back if anyone can't pay.
        if (bet > 0) {
            List<Player> paid = new ArrayList<>();
            for (Player p : everyone) {
                if (!plugin.economy().withdraw(p, bet)) {
                    for (Player r : paid) {
                        plugin.economy().deposit(r, bet);
                        plugin.store().removeEscrow(r.getUniqueId(), bet);
                    }
                    failedPlayer[0] = p.getName();
                    return "not-enough-money";
                }
                plugin.store().addEscrow(p.getUniqueId(), bet);
                paid.add(p);
            }
        }

        arena.inUse(true);
        Match m = new Match(nextId++, arena, uuids(teamA), uuids(teamB), kit, bet, callback);
        m.countdown = Math.max(0, plugin.getConfig().getInt("duel.countdown", 5));
        matches.add(m);

        boolean clearEffects = plugin.getConfig().getBoolean("duel.clear-effects-on-start", true);
        for (int team = 0; team < 2; team++) {
            List<Player> members = team == 0 ? teamA : teamB;
            for (int i = 0; i < members.size(); i++) {
                Player p = members.get(i);
                m.names.put(p.getUniqueId(), p.getName());
                prepare(p, m, arena.spawn(team, i), clearEffects);
                byPlayer.put(p.getUniqueId(), m);
            }
        }

        String betText = bet > 0 ? plugin.economy().format(bet) : "-";
        String kitText = kitName(kit);
        for (int team = 0; team < 2; team++) {
            String opponents = m.teamNames(1 - team);
            for (UUID id : m.team(team)) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) Msg.send(p, "match-start", "opponents", opponents, "bet", betText, "kit", kitText);
            }
        }
        if (m.countdown == 0) beginFight(m);
        ensureTicker();
        return null;
    }

    private void prepare(Player p, Match m, Location spawn, boolean clearEffects) {
        p.closeInventory();
        if (p.isInsideVehicle()) p.leaveVehicle();

        // 1) Save everything first, to disk, before anything is changed.
        plugin.store().put(p.getUniqueId(), PlayerState.capture(p), !m.ownInventory());

        // 2) Reset the player for the fight.
        if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) p.setGameMode(GameMode.SURVIVAL);
        p.setFlying(false);
        p.setAllowFlight(false);
        if (!m.ownInventory()) {
            p.setItemOnCursor(null);
            clearCraftingGrid(p);
            p.getInventory().clear();
            plugin.kits().equip(p, m.kit);
        }
        if (clearEffects) for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        heal(p);
        teleport(p, spawn);
    }

    @SuppressWarnings("deprecation")
    private static void heal(Player p) {
        p.setHealth(p.getMaxHealth());
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setExhaustion(0f);
        p.setFireTicks(0);
        p.setFallDistance(0);
    }

    private static void clearCraftingGrid(Player p) {
        InventoryView view = p.getOpenInventory();
        if (view.getTopInventory().getType() == InventoryType.CRAFTING) view.getTopInventory().clear();
    }

    /** Teleport that can't be blocked by other plugins (same trick as Duels' Teleport#tryTeleport). */
    public void teleport(Player p, Location to) {
        if (to == null || to.getWorld() == null) return;
        forceTeleport.add(p.getUniqueId());
        p.teleport(to, PlayerTeleportEvent.TeleportCause.PLUGIN);
        forceTeleport.remove(p.getUniqueId());
    }

    // ------------------------------------------------------------------ ticking

    private void ensureTicker() {
        if (ticker != -1) return;
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L).getTaskId();
    }

    private void tick() {
        if (matches.isEmpty()) {
            Bukkit.getScheduler().cancelTask(ticker);
            ticker = -1;
            return;
        }
        long now = System.currentTimeMillis();
        long maxDuration = plugin.getConfig().getLong("duel.max-duration", 300) * 1000L;
        for (Match m : new ArrayList<>(matches)) {
            switch (m.state) {
                case COUNTDOWN -> {
                    m.countdown--;
                    if (m.countdown <= 0) beginFight(m);
                    else showTitle(m, Msg.get("match-countdown-title", "seconds", m.countdown), Msg.get("match-countdown-subtitle"));
                }
                case FIGHT -> {
                    if (maxDuration > 0 && now - m.fightStartMillis >= maxDuration) timeout(m);
                }
                case ENDING -> {
                    if (now >= m.endAtMillis) cleanup(m);
                }
            }
        }
    }

    private void beginFight(Match m) {
        m.state = Match.State.FIGHT;
        m.fightStartMillis = System.currentTimeMillis();
        showTitle(m, Msg.get("match-start-title"), net.kyori.adventure.text.Component.empty());
    }

    private void showTitle(Match m, net.kyori.adventure.text.Component title, net.kyori.adventure.text.Component sub) {
        Title t = Title.title(title, sub, Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ofMillis(200)));
        for (UUID id : m.alive) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.showTitle(t);
        }
    }

    private void timeout(Match m) {
        if (!m.isTournament()) {
            end(m, -1, "match-draw");
            return;
        }
        // Tournaments need a winner: more players alive, then more total health.
        int[] alive = new int[2];
        double[] health = new double[2];
        for (UUID id : m.alive) {
            int t = m.teamOf(id);
            Player p = Bukkit.getPlayer(id);
            alive[t]++;
            if (p != null) health[t] += p.getHealth();
        }
        int winner = alive[0] != alive[1] ? (alive[0] > alive[1] ? 0 : 1) : (health[0] >= health[1] ? 0 : 1);
        end(m, winner, null);
    }

    // ------------------------------------------------------------------ elimination

    /** Called from PlayerDeathEvent. Returns true if the player was in a match. */
    public boolean handleDeath(org.bukkit.event.entity.PlayerDeathEvent e) {
        Player p = e.getPlayer();
        Match m = byPlayer.remove(p.getUniqueId());
        if (m == null) return false;

        e.deathMessage(null);
        e.setKeepLevel(true);
        e.setDroppedExp(0);
        boolean quit = quitting.contains(p.getUniqueId());
        if (!m.ownInventory()) {
            // Kit items never drop; the real inventory comes back from the saved state.
            e.getDrops().clear();
            e.setKeepInventory(false);
            clearCraftingGrid(p);
        } else if (quit || plugin.getConfig().getBoolean("duel.own-inventory-keep-items-on-death", true)) {
            // Own inventory: keep everything that is left. Used food/potions/arrows stay used.
            e.getDrops().clear();
            e.setKeepInventory(true);
        }

        boolean wasAlive = m.alive.remove(p.getUniqueId());
        if (wasAlive) {
            sendToMatch(m, quit ? "match-quit" : "match-eliminated", "player", p.getName());
            checkEnd(m);
        }
        if (!quit) Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline() && p.isDead()) p.spigot().respawn();
        });
        return true;
    }

    /** Called from PlayerQuitEvent (also covers kicks). */
    public void handleQuit(Player p) {
        Match m = byPlayer.get(p.getUniqueId());
        if (m == null) return;
        if (m.state == Match.State.ENDING) {
            // Already decided; keep the pending state, they are restored when they come back.
            byPlayer.remove(p.getUniqueId());
            if (!m.ownInventory()) p.getInventory().clear();
            return;
        }
        quitting.add(p.getUniqueId());
        try {
            p.setHealth(0);
        } finally {
            quitting.remove(p.getUniqueId());
        }
        // If something prevented the death (a totem in own-inventory mode, another plugin...), eliminate anyway.
        if (byPlayer.remove(p.getUniqueId()) != null) {
            if (!m.ownInventory()) p.getInventory().clear();
            if (m.alive.remove(p.getUniqueId())) {
                sendToMatch(m, "match-quit", "player", p.getName());
                checkEnd(m);
            }
        }
    }

    /** Restores a player from the stored pre-duel state, if there is one. */
    public boolean restore(Player p) {
        StateStore.Pending pending = plugin.store().take(p.getUniqueId());
        if (pending == null) return false;
        teleport(p, pending.state().location);
        pending.state().apply(p, pending.restoreInventory());
        if (!pending.restoreInventory()) stripKitItems(p);
        Msg.send(p, "restored");
        return true;
    }

    private void stripKitItems(Player p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) if (plugin.kits().isKitItem(inv.getItem(i))) inv.setItem(i, null);
    }

    private void checkEnd(Match m) {
        if (m.state == Match.State.ENDING) return;
        boolean a = false, b = false;
        for (UUID id : m.alive) {
            if (m.teamOf(id) == 0) a = true;
            else b = true;
        }
        if (a && b) return;
        end(m, a ? 0 : b ? 1 : -1, a || b ? null : "match-draw");
    }

    // ------------------------------------------------------------------ end

    /**
     * Decides the match: pays out, updates stats, notifies the tournament.
     * Players still standing are restored after the configured delay by the ticker.
     */
    public void end(Match m, int winner, String drawMessage) {
        if (m.state == Match.State.ENDING) return;
        m.state = Match.State.ENDING;

        List<UUID> all = m.all();
        if (winner < 0) {
            refundBets(m);
            if (drawMessage != null) sendToAll(m, drawMessage);
        } else {
            List<UUID> winners = m.team(winner);
            List<UUID> losers = m.team(1 - winner);

            double share = 0;
            if (m.bet > 0) {
                double pot = m.bet * all.size();
                double tax = pot * plugin.getConfig().getDouble("bet.tax-percent", 0) / 100.0;
                share = (pot - tax) / winners.size();
                for (UUID w : winners) plugin.economy().deposit(Bukkit.getOfflinePlayer(w), share);
                for (UUID u : all) plugin.store().removeEscrow(u, m.bet);
            }

            boolean applyElo = plugin.getConfig().getBoolean("elo.enabled", true)
                    && (m.isTournament() ? plugin.getConfig().getBoolean("elo.count-tournament", true)
                    : (m.bet <= 0 || plugin.getConfig().getBoolean("elo.count-bet-duels", true)));
            int change = plugin.stats().record(names(m, winners), names(m, losers), applyElo);

            String prize = share > 0 ? plugin.economy().format(share) : "-";
            for (UUID w : winners) {
                Player p = Bukkit.getPlayer(w);
                if (p != null) Msg.send(p, "match-win", "prize", prize, "elo", plugin.stats().elo(w), "delta", change);
            }
            for (UUID l : losers) {
                Player p = Bukkit.getPlayer(l);
                if (p != null) Msg.send(p, "match-lose", "elo", plugin.stats().elo(l), "delta", change);
            }
        }

        boolean lootTime = m.ownInventory() && !plugin.getConfig().getBoolean("duel.own-inventory-keep-items-on-death", true);
        long delay = plugin.getConfig().getLong(lootTime ? "duel.own-inventory-loot-delay" : "duel.end-delay", 3);
        m.endAtMillis = System.currentTimeMillis() + delay * 1000L;

        if (m.callback != null) {
            try {
                m.callback.onEnd(m, winner);
            } catch (RuntimeException ex) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Turnuva maç sonucu işlenemedi", ex);
            }
        }
        ensureTicker();
    }

    private void refundBets(Match m) {
        if (m.bet <= 0) return;
        for (UUID u : m.all()) {
            plugin.economy().deposit(Bukkit.getOfflinePlayer(u), m.bet);
            plugin.store().removeEscrow(u, m.bet);
        }
    }

    /** Sends the remaining players home and frees the arena. */
    private void cleanup(Match m) {
        for (UUID id : m.all()) {
            if (byPlayer.get(id) != m) continue;
            byPlayer.remove(id);
            Player p = Bukkit.getPlayer(id);
            if (p != null && !p.isDead()) restore(p);
        }
        m.arena.inUse(false);
        matches.remove(m);
    }

    /** Ends everything immediately (plugin disable / tournament cancel). */
    public void cancel(Match m) {
        if (m.state != Match.State.ENDING) end(m, -1, "match-cancelled");
        cleanup(m);
    }

    public void cancelAll() {
        for (Match m : new ArrayList<>(matches)) cancel(m);
        // Players that died and haven't respawned yet (same as Duels' PlayerInfoManager#handleUnload).
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!plugin.store().has(p.getUniqueId())) continue;
            if (p.isDead()) p.spigot().respawn();
            restore(p);
        }
    }

    // ------------------------------------------------------------------ helpers

    private void sendToMatch(Match m, String key, Object... ph) {
        for (UUID id : m.all()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) Msg.send(p, key, ph);
        }
    }

    private void sendToAll(Match m, String key) {
        sendToMatch(m, key);
    }

    private static List<UUID> uuids(Collection<Player> players) {
        List<UUID> out = new ArrayList<>(players.size());
        for (Player p : players) out.add(p.getUniqueId());
        return out;
    }

    private static Map<UUID, String> names(Match m, List<UUID> ids) {
        Map<UUID, String> out = new LinkedHashMap<>();
        for (UUID id : ids) out.put(id, m.name(id));
        return out;
    }

    public String kitName(KitManager.Kit kit) {
        return kit == null ? "Kendi Envanteri" : kit.name;
    }
}
