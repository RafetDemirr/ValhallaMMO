package com.fool.duels.duel;

import com.fool.duels.FoolDuels;
import com.fool.duels.kit.KitManager;
import com.fool.duels.party.PartyManager;
import com.fool.duels.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Duel requests: validation, sending, accepting, expiring. */
public final class RequestManager {
    public record Request(UUID sender, UUID target, double bet, String kit, long expiresAt) {}

    private final FoolDuels plugin;
    /** target -> (sender -> request) */
    private final Map<UUID, Map<UUID, Request>> incoming = new HashMap<>();
    private final Set<UUID> toggledOff = new HashSet<>();
    /** "sender:target" -> time until another request is allowed */
    private final Map<String, Long> cooldowns = new HashMap<>();

    public RequestManager(FoolDuels plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::expire, 100L, 100L);
    }

    public boolean toggle(UUID id) {
        if (!toggledOff.remove(id)) {
            toggledOff.add(id);
            return false;
        }
        return true;
    }

    /** The team a player duels with: their party (if they lead it) or just themselves. Null if they are a non-leader party member. */
    public List<Player> teamOf(Player p) {
        PartyManager.Party party = plugin.parties().get(p.getUniqueId());
        if (party == null || party.size() <= 1) return List.of(p);
        if (!party.leader().equals(p.getUniqueId())) return null;
        List<Player> out = new ArrayList<>();
        for (UUID id : party.members()) {
            Player m = Bukkit.getPlayer(id);
            if (m != null) out.add(m);
        }
        return out;
    }

    /**
     * Checks everything that can be checked before the request is sent (and again on accept).
     * Sends the error to {@code to} and returns false if something is wrong.
     */
    public boolean validate(Player sender, Player target, double bet, Player to) {
        if (sender.equals(target)) {
            Msg.send(to, "not-self");
            return false;
        }
        List<Player> a = teamOf(sender);
        if (a == null) {
            Msg.send(to, "not-party-leader");
            return false;
        }
        List<Player> b = teamOf(target);
        if (b == null) {
            PartyManager.Party party = plugin.parties().get(target.getUniqueId());
            Player leader = party == null ? null : Bukkit.getPlayer(party.leader());
            Msg.send(to, "target-not-leader", "player", target.getName(), "leader", leader == null ? "?" : leader.getName());
            return false;
        }
        for (Player p : a) if (b.contains(p)) {
            Msg.send(to, "not-self");
            return false;
        }
        if (plugin.getConfig().getBoolean("duel.require-equal-teams", true) && a.size() != b.size()) {
            Msg.send(to, "teams-unequal", "a", a.size(), "b", b.size());
            return false;
        }
        List<Player> all = new ArrayList<>(a);
        all.addAll(b);
        for (Player p : all) {
            if (plugin.matches().inMatch(p.getUniqueId()) || plugin.store().has(p.getUniqueId())) {
                Msg.send(to, p.equals(to) ? "in-match" : "target-in-match", "player", p.getName());
                return false;
            }
            if (plugin.combat().isInCombat(p)) {
                Msg.send(to, "in-combat", "player", p.getName());
                return false;
            }
            if (bet > 0 && !plugin.economy().has(p, bet)) {
                Msg.send(to, "not-enough-money", "player", p.getName(), "amount", plugin.economy().format(bet));
                return false;
            }
        }
        return true;
    }

    /** Seconds left before sender may send target another request, 0 if allowed. */
    public long cooldownLeft(Player sender, Player target) {
        if (sender.hasPermission("foolduels.bypass")) return 0;
        Long until = cooldowns.get(sender.getUniqueId() + ":" + target.getUniqueId());
        if (until == null) return 0;
        long left = (until - System.currentTimeMillis() + 999) / 1000;
        return Math.max(0, left);
    }

    private void cooldown(UUID sender, UUID target, String path, long def) {
        long seconds = plugin.getConfig().getLong(path, def);
        if (seconds <= 0) return;
        String key = sender + ":" + target;
        long until = System.currentTimeMillis() + seconds * 1000L;
        cooldowns.merge(key, until, Math::max);
    }

    public void send(Player sender, Player target, double bet, String kitName) {
        long left = cooldownLeft(sender, target);
        if (left > 0) {
            Msg.send(sender, "request-cooldown", "player", target.getName(), "seconds", left);
            return;
        }
        if (toggledOff.contains(target.getUniqueId())) {
            Msg.send(sender, "target-toggled", "player", target.getName());
            return;
        }
        if (!validate(sender, target, bet, sender)) return;
        long timeout = plugin.getConfig().getLong("duel.request-timeout", 60) * 1000L;
        incoming.computeIfAbsent(target.getUniqueId(), k -> new LinkedHashMap<>())
                .put(sender.getUniqueId(), new Request(sender.getUniqueId(), target.getUniqueId(), bet, kitName, System.currentTimeMillis() + timeout));

        List<Player> a = teamOf(sender), b = teamOf(target);
        String mode = a.size() + "v" + b.size();
        String betText = bet > 0 ? plugin.economy().format(bet) : "-";
        String kitText = plugin.matches().kitName(plugin.kits().get(kitName));
        cooldown(sender.getUniqueId(), target.getUniqueId(), "duel.request-cooldown", 15);
        Msg.send(sender, "request-sent", "player", target.getName());
        Msg.send(target, "request-received", "player", sender.getName(), "mode", mode, "bet", betText, "kit", kitText);
    }

    /** @param senderName null = the most recent request */
    public void accept(Player target, String senderName) {
        Request r = find(target, senderName);
        if (r == null) {
            Msg.send(target, "request-none");
            return;
        }
        incoming.get(target.getUniqueId()).remove(r.sender());
        // An accepted request is not spam: allow a rematch right away.
        cooldowns.remove(r.sender() + ":" + target.getUniqueId());
        Player sender = Bukkit.getPlayer(r.sender());
        if (sender == null) {
            Msg.send(target, "request-none");
            return;
        }
        KitManager.Kit kit = plugin.kits().get(r.kit());
        if (r.kit() != null && kit == null) {
            Msg.send(target, "request-none");
            return;
        }
        if (!validate(sender, target, r.bet(), target)) return;

        String[] failed = new String[1];
        String error = plugin.matches().start(teamOf(sender), teamOf(target), r.bet(), kit, null, failed);
        if (error != null) {
            for (Player p : List.of(sender, target)) Msg.send(p, error, "player", failed[0], "amount", plugin.economy().format(r.bet()));
        }
    }

    public void deny(Player target, String senderName) {
        Request r = find(target, senderName);
        if (r == null) {
            Msg.send(target, "request-none");
            return;
        }
        incoming.get(target.getUniqueId()).remove(r.sender());
        cooldown(r.sender(), target.getUniqueId(), "duel.deny-cooldown", 60);
        Msg.send(target, "request-denied-self");
        Player sender = Bukkit.getPlayer(r.sender());
        if (sender != null) Msg.send(sender, "request-denied", "player", target.getName());
    }

    public void cancelOutgoing(Player sender) {
        for (Map<UUID, Request> m : incoming.values()) m.remove(sender.getUniqueId());
        Msg.send(sender, "request-cancelled");
    }

    public void clear(UUID player) {
        incoming.remove(player);
        for (Map<UUID, Request> m : incoming.values()) m.remove(player);
    }

    public List<String> incomingNames(UUID target) {
        List<String> out = new ArrayList<>();
        Map<UUID, Request> m = incoming.get(target);
        if (m != null) for (UUID s : m.keySet()) {
            Player p = Bukkit.getPlayer(s);
            if (p != null) out.add(p.getName());
        }
        return out;
    }

    private Request find(Player target, String senderName) {
        Map<UUID, Request> m = incoming.get(target.getUniqueId());
        if (m == null || m.isEmpty()) return null;
        if (senderName == null) {
            Request last = null;
            for (Request r : m.values()) last = r;
            return last;
        }
        Player s = Bukkit.getPlayerExact(senderName);
        return s == null ? null : m.get(s.getUniqueId());
    }

    private void expire() {
        long now = System.currentTimeMillis();
        cooldowns.values().removeIf(until -> until < now);
        for (Iterator<Map.Entry<UUID, Map<UUID, Request>>> it = incoming.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Map<UUID, Request>> e = it.next();
            e.getValue().values().removeIf(r -> {
                if (r.expiresAt() > now) return false;
                Player s = Bukkit.getPlayer(r.sender()), t = Bukkit.getPlayer(r.target());
                if (s != null && t != null) Msg.send(s, "request-expired", "player", t.getName());
                return true;
            });
            if (e.getValue().isEmpty()) it.remove();
        }
    }
}
