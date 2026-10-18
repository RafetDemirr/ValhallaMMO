package com.fool.duels.match;

import com.fool.duels.arena.Arena;
import com.fool.duels.kit.KitManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Match {
    public enum State { COUNTDOWN, FIGHT, ENDING }

    /** Called once when a match is decided. winnerTeam is 0, 1 or -1 for a draw/cancel. */
    public interface Callback {
        void onEnd(Match match, int winnerTeam);
    }

    final int id;
    final Arena arena;
    final List<List<UUID>> teams;
    final Set<UUID> alive = new HashSet<>();
    final Map<UUID, String> names = new HashMap<>();
    final KitManager.Kit kit;
    final double bet;
    final Callback callback;

    State state = State.COUNTDOWN;
    int countdown;
    long fightStartMillis;
    long endAtMillis;

    Match(int id, Arena arena, List<UUID> a, List<UUID> b, KitManager.Kit kit, double bet, Callback callback) {
        this.id = id;
        this.arena = arena;
        this.teams = List.of(List.copyOf(a), List.copyOf(b));
        this.kit = kit;
        this.bet = bet;
        this.callback = callback;
        alive.addAll(a);
        alive.addAll(b);
    }

    public int id() {
        return id;
    }

    public Arena arena() {
        return arena;
    }

    public State state() {
        return state;
    }

    public KitManager.Kit kit() {
        return kit;
    }

    public boolean ownInventory() {
        return kit == null;
    }

    public double bet() {
        return bet;
    }

    public boolean isTournament() {
        return callback != null;
    }

    public List<UUID> team(int i) {
        return teams.get(i);
    }

    public int teamOf(UUID id) {
        if (teams.get(0).contains(id)) return 0;
        if (teams.get(1).contains(id)) return 1;
        return -1;
    }

    public List<UUID> all() {
        List<UUID> out = new ArrayList<>(teams.get(0));
        out.addAll(teams.get(1));
        return out;
    }

    public boolean isAlive(UUID id) {
        return alive.contains(id);
    }

    public Set<UUID> alive() {
        return Collections.unmodifiableSet(alive);
    }

    public String name(UUID id) {
        return names.getOrDefault(id, "?");
    }

    public String teamNames(int team) {
        StringBuilder sb = new StringBuilder();
        for (UUID u : teams.get(team)) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(name(u));
        }
        return sb.toString();
    }

    public String mode() {
        return teams.get(0).size() + "v" + teams.get(1).size();
    }
}
