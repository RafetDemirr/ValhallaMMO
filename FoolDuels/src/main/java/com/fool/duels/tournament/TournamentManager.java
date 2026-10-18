package com.fool.duels.tournament;

import com.fool.duels.FoolDuels;
import com.fool.duels.kit.KitManager;
import com.fool.duels.match.Match;
import com.fool.duels.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Single-elimination 1v1 tournament started by an admin.
 * Registration costs a fee that goes into the prize pool; rounds are paired randomly,
 * odd players get a bye, matches run in parallel on all free arenas.
 */
public final class TournamentManager implements Match.Callback {
    private enum State { REGISTRATION, RUNNING }

    private final FoolDuels plugin;

    private State state;
    private double fee;
    private String kitName; // null = own inventory
    private double pot;
    private final Set<UUID> registered = new LinkedHashSet<>();
    private final List<UUID> roundPlayers = new ArrayList<>();
    private final List<UUID> advancing = new ArrayList<>();
    private final Deque<UUID[]> queue = new ArrayDeque<>();
    private final Set<Integer> running = new java.util.HashSet<>();
    private int round;
    private UUID finalist;
    private int task = -1;

    public TournamentManager(FoolDuels plugin) {
        this.plugin = plugin;
    }

    public boolean active() {
        return state != null;
    }

    public boolean isRegistered(UUID id) {
        return registered.contains(id);
    }

    // ------------------------------------------------------------------ admin

    public void create(Player admin, double fee, String kit) {
        if (active()) {
            Msg.send(admin, "tournament.already-exists");
            return;
        }
        if (fee > 0 && !plugin.economy().enabled()) {
            Msg.send(admin, "bet-disabled");
            return;
        }
        this.state = State.REGISTRATION;
        this.fee = Math.max(0, fee);
        this.kitName = kit;
        this.pot = 0;
        this.round = 0;
        this.finalist = null;
        registered.clear();
        roundPlayers.clear();
        advancing.clear();
        queue.clear();
        running.clear();
        Msg.broadcast("tournament.opened", "fee", fee > 0 ? plugin.economy().format(fee) : "Ücretsiz", "kit", kitText());
    }

    public void addPrize(Player admin, double amount) {
        if (!active()) {
            Msg.send(admin, "tournament.none");
            return;
        }
        if (amount <= 0) return;
        pot += amount;
        Msg.send(admin, "tournament.prize-added", "amount", plugin.economy().format(amount));
    }

    public void start(Player admin) {
        if (state != State.REGISTRATION) {
            Msg.send(admin, "tournament.not-registration");
            return;
        }
        int min = Math.max(2, plugin.getConfig().getInt("tournament.min-players", 2));
        if (registered.size() < min) {
            Msg.send(admin, "tournament.not-enough-players", "min", min);
            return;
        }
        state = State.RUNNING;
        roundPlayers.clear();
        roundPlayers.addAll(registered);
        Collections.shuffle(roundPlayers);
        Msg.broadcast("tournament.started", "count", registered.size(), "pot", plugin.economy().format(pot));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tryStartQueued, 100L, 100L).getTaskId();
        nextRound();
    }

    public void cancel(Player admin) {
        if (!active()) {
            if (admin != null) Msg.send(admin, "tournament.none");
            return;
        }
        for (Match m : new ArrayList<>(plugin.matches().matches())) if (running.contains(m.id())) plugin.matches().cancel(m);
        // Fees come back out of escrow.
        if (fee > 0) for (UUID id : registered) {
            plugin.economy().deposit(Bukkit.getOfflinePlayer(id), fee);
            plugin.store().removeEscrow(id, fee);
        }
        reset();
        Msg.broadcast("tournament.cancelled");
    }

    // ------------------------------------------------------------------ players

    public void join(Player p) {
        if (state == null) {
            Msg.send(p, "tournament.none");
            return;
        }
        if (state != State.REGISTRATION) {
            Msg.send(p, "tournament.not-registration");
            return;
        }
        if (registered.contains(p.getUniqueId())) {
            Msg.send(p, "tournament.already-joined");
            return;
        }
        if (fee > 0) {
            if (!plugin.economy().withdraw(p, fee)) {
                Msg.send(p, "not-enough-money", "player", p.getName(), "amount", plugin.economy().format(fee));
                return;
            }
            plugin.store().addEscrow(p.getUniqueId(), fee);
            pot += fee;
        }
        registered.add(p.getUniqueId());
        Msg.send(p, "tournament.joined", "count", registered.size());
        Msg.broadcast("tournament.joined-broadcast", "player", p.getName(), "count", registered.size(), "pot", plugin.economy().format(pot));
    }

    public void leave(Player p) {
        if (!registered.contains(p.getUniqueId())) {
            Msg.send(p, "tournament.not-joined");
            return;
        }
        if (state == State.REGISTRATION) {
            registered.remove(p.getUniqueId());
            if (fee > 0) {
                plugin.economy().deposit(p, fee);
                plugin.store().removeEscrow(p.getUniqueId(), fee);
                pot -= fee;
            }
            Msg.send(p, "tournament.left");
            return;
        }
        // Running: forfeit. If they are in a match, leaving it is handled like a quit.
        Msg.send(p, "tournament.forfeit");
        Match m = plugin.matches().get(p.getUniqueId());
        if (m != null && running.contains(m.id())) {
            p.setHealth(0);
            return;
        }
        roundPlayers.remove(p.getUniqueId());
        advancing.remove(p.getUniqueId());
        for (UUID[] pair : queue) {
            if (p.getUniqueId().equals(pair[0])) pair[0] = null;
            if (p.getUniqueId().equals(pair[1])) pair[1] = null;
        }
        tryStartQueued();
    }

    public void info(org.bukkit.command.CommandSender to) {
        if (!active()) {
            Msg.send(to, "tournament.none");
            return;
        }
        Msg.send(to, "tournament.info", "state", state == State.REGISTRATION ? "Kayıt" : round + ". tur",
                "count", registered.size(), "fee", fee > 0 ? plugin.economy().format(fee) : "Ücretsiz",
                "pot", plugin.economy().format(pot), "kit", kitText());
    }

    // ------------------------------------------------------------------ bracket

    private void nextRound() {
        if (roundPlayers.size() <= 1) {
            finish(roundPlayers.isEmpty() ? null : roundPlayers.get(0));
            return;
        }
        round++;
        Msg.broadcast("tournament.round", "round", round, "count", roundPlayers.size());
        advancing.clear();
        queue.clear();
        List<UUID> players = new ArrayList<>(roundPlayers);
        if (players.size() % 2 == 1) {
            UUID bye = players.remove(players.size() - 1);
            advancing.add(bye);
            Msg.broadcast("tournament.bye", "player", name(bye));
        }
        for (int i = 0; i + 1 < players.size(); i += 2) queue.add(new UUID[]{players.get(i), players.get(i + 1)});
        tryStartQueued();
    }

    private void tryStartQueued() {
        if (state != State.RUNNING) return;
        KitManager.Kit kit = plugin.kits().get(kitName);
        int size = queue.size();
        for (int i = 0; i < size; i++) {
            UUID[] pair = queue.poll();
            if (pair == null) break;
            Player a = pair[0] == null ? null : Bukkit.getPlayer(pair[0]);
            Player b = pair[1] == null ? null : Bukkit.getPlayer(pair[1]);
            if (a == null || b == null) {
                // Walkover: whoever is online goes through, an empty pair just disappears.
                if (a == null && pair[0] != null) Msg.broadcast("tournament.forfeit-offline", "player", name(pair[0]));
                if (b == null && pair[1] != null) Msg.broadcast("tournament.forfeit-offline", "player", name(pair[1]));
                if (a != null) advancing.add(a.getUniqueId());
                if (b != null) advancing.add(b.getUniqueId());
                continue;
            }
            if (plugin.matches().inMatch(a.getUniqueId()) || plugin.matches().inMatch(b.getUniqueId())
                    || plugin.store().has(a.getUniqueId()) || plugin.store().has(b.getUniqueId())) {
                queue.add(pair); // busy (another duel / still respawning), try again later
                continue;
            }
            String[] failed = new String[1];
            String error = plugin.matches().start(List.of(a), List.of(b), 0, kit, this, failed);
            if (error != null) {
                queue.addFirst(pair);
                break; // most likely no free arena; retried by the timer
            }
            Match m = plugin.matches().get(a.getUniqueId());
            if (m != null) running.add(m.id());
            Msg.broadcast("tournament.match", "a", a.getName(), "b", b.getName());
        }
        if (queue.isEmpty() && running.isEmpty()) {
            roundPlayers.clear();
            roundPlayers.addAll(advancing);
            nextRound();
        }
    }

    @Override
    public void onEnd(Match m, int winnerTeam) {
        if (!running.remove(m.id())) return;
        UUID a = m.team(0).get(0), b = m.team(1).get(0);
        if (winnerTeam < 0) {
            // Both died at the same time: replay.
            queue.add(new UUID[]{a, b});
        } else {
            UUID winner = winnerTeam == 0 ? a : b, loser = winnerTeam == 0 ? b : a;
            advancing.add(winner);
            if (roundPlayers.size() == 2) finalist = loser;
            Msg.broadcast("tournament.match-result", "winner", m.name(winner), "loser", m.name(loser));
        }
        // Next pairings start after the loser has been sent home.
        Bukkit.getScheduler().runTaskLater(plugin, this::tryStartQueued, 20L * (plugin.getConfig().getLong("duel.end-delay", 3) + 2));
    }

    private void finish(UUID winner) {
        double tax = pot * plugin.getConfig().getDouble("tournament.tax-percent", 0) / 100.0;
        double pool = pot - tax;
        List<Double> prizes = plugin.getConfig().getDoubleList("tournament.prizes");
        double p1 = prizes.isEmpty() ? pool : pool * prizes.get(0) / 100.0;
        double p2 = prizes.size() < 2 || finalist == null ? 0 : pool * prizes.get(1) / 100.0;
        if (winner != null) plugin.economy().deposit(Bukkit.getOfflinePlayer(winner), p1);
        if (finalist != null) plugin.economy().deposit(Bukkit.getOfflinePlayer(finalist), p2);
        if (fee > 0) for (UUID id : registered) plugin.store().removeEscrow(id, fee);
        Msg.broadcast("tournament.winner", "first", winner == null ? "-" : name(winner), "prize1", plugin.economy().format(p1),
                "second", finalist == null ? "-" : name(finalist), "prize2", plugin.economy().format(p2));
        reset();
    }

    private void reset() {
        if (task != -1) Bukkit.getScheduler().cancelTask(task);
        task = -1;
        state = null;
        registered.clear();
        roundPlayers.clear();
        advancing.clear();
        queue.clear();
        running.clear();
        finalist = null;
        pot = 0;
    }

    private String kitText() {
        return kitName == null ? "Kendi Envanteri" : kitName;
    }

    private static String name(UUID id) {
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? "?" : n;
    }
}
