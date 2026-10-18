package com.fool.duels.tournament;

import com.fool.duels.FoolDuels;
import com.fool.duels.match.Match;
import com.fool.duels.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Spectator bets on tournament matches (pari-mutuel, like a horse race):
 * all stakes go into one pool, the pool minus tax is shared among the people who backed the winner,
 * in proportion to their stake. If nobody backed the loser, winners just get their stake back.
 * Betting is open during the pre-fight countdown. Money is held in escrow (crash-safe).
 */
public final class SpectatorBets {
    private static final class Book {
        final Match match;
        final List<Map<UUID, Double>> stakes = List.of(new HashMap<>(), new HashMap<>());
        boolean open = true;

        Book(Match match) {
            this.match = match;
        }

        double total(int side) {
            double t = 0;
            for (double v : stakes.get(side).values()) t += v;
            return t;
        }
    }

    private final FoolDuels plugin;
    private final Map<Integer, Book> books = new HashMap<>();

    public SpectatorBets(FoolDuels plugin) {
        this.plugin = plugin;
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("tournament.spectator-bets.enabled", true) && plugin.economy().enabled();
    }

    public void open(Match m) {
        if (!enabled()) return;
        books.put(m.id(), new Book(m));
        Msg.broadcast("tournament.bet-open", "a", m.teamNames(0), "b", m.teamNames(1),
                "seconds", Math.max(0, plugin.getConfig().getInt("tournament.countdown", 15)));
    }

    public void close(Match m) {
        Book b = books.get(m.id());
        if (b == null || !b.open) return;
        b.open = false;
        Msg.broadcast("tournament.bet-closed", "a", m.teamNames(0), "b", m.teamNames(1),
                "pa", plugin.economy().format(b.total(0)), "pb", plugin.economy().format(b.total(1)));
    }

    public void place(Player p, String targetName, double amount) {
        if (!enabled()) {
            Msg.send(p, "bet-disabled");
            return;
        }
        Book book = null;
        int side = -1;
        for (Book b : books.values()) {
            if (!b.open) continue;
            for (int t = 0; t < 2; t++) for (UUID id : b.match.team(t)) {
                if (b.match.name(id).equalsIgnoreCase(targetName)) {
                    book = b;
                    side = t;
                }
            }
        }
        if (book == null) {
            Msg.send(p, "tournament.bet-no-match", "player", targetName);
            return;
        }
        if (plugin.tournament().isRegistered(p.getUniqueId()) || book.match.teamOf(p.getUniqueId()) >= 0) {
            Msg.send(p, "tournament.bet-participant");
            return;
        }
        if (book.stakes.get(1 - side).containsKey(p.getUniqueId())) {
            Msg.send(p, "tournament.bet-other-side");
            return;
        }
        double min = plugin.getConfig().getDouble("tournament.spectator-bets.min", 10);
        double max = plugin.getConfig().getDouble("tournament.spectator-bets.max", 100000);
        double already = book.stakes.get(side).getOrDefault(p.getUniqueId(), 0.0);
        if (amount < min || already + amount > max || Double.isNaN(amount)) {
            Msg.send(p, "bet-invalid", "min", plugin.economy().format(min), "max", plugin.economy().format(max));
            return;
        }
        if (!plugin.economy().withdraw(p, amount)) {
            Msg.send(p, "not-enough-money", "player", p.getName(), "amount", plugin.economy().format(amount));
            return;
        }
        plugin.store().addEscrow(p.getUniqueId(), amount);
        book.stakes.get(side).merge(p.getUniqueId(), amount, Double::sum);
        Msg.send(p, "tournament.bet-placed", "amount", plugin.economy().format(amount), "player", book.match.teamNames(side));
    }

    /** winner: 0/1, or -1 for draw/cancel (everyone refunded). */
    public void settle(Match m, int winner) {
        Book b = books.remove(m.id());
        if (b == null) return;
        double winSide = winner < 0 ? 0 : b.total(winner);
        double loseSide = winner < 0 ? 0 : b.total(1 - winner);
        if (winner < 0 || winSide <= 0 || loseSide <= 0) {
            refund(b);
            return;
        }
        double tax = plugin.getConfig().getDouble("tournament.spectator-bets.tax-percent", 5) / 100.0;
        double pool = (winSide + loseSide) * (1 - tax);
        for (Map.Entry<UUID, Double> e : b.stakes.get(winner).entrySet()) {
            double payout = pool * e.getValue() / winSide;
            plugin.economy().deposit(Bukkit.getOfflinePlayer(e.getKey()), payout);
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) Msg.send(p, "tournament.bet-won", "amount", plugin.economy().format(payout));
        }
        for (Map.Entry<UUID, Double> e : b.stakes.get(1 - winner).entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) Msg.send(p, "tournament.bet-lost", "amount", plugin.economy().format(e.getValue()));
        }
        releaseEscrow(b);
    }

    private void refund(Book b) {
        for (Map<UUID, Double> side : b.stakes) for (Map.Entry<UUID, Double> e : side.entrySet()) {
            plugin.economy().deposit(Bukkit.getOfflinePlayer(e.getKey()), e.getValue());
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) Msg.send(p, "tournament.bet-refunded", "amount", plugin.economy().format(e.getValue()));
        }
        releaseEscrow(b);
    }

    private void releaseEscrow(Book b) {
        for (Map<UUID, Double> side : b.stakes) side.forEach((id, v) -> plugin.store().removeEscrow(id, v));
    }

    public void refundAll() {
        for (Book b : new ArrayList<>(books.values())) refund(b);
        books.clear();
    }

    public List<String> openTargets() {
        List<String> out = new ArrayList<>();
        for (Book b : books.values()) if (b.open) for (UUID id : b.match.all()) out.add(b.match.name(id));
        return out;
    }
}
