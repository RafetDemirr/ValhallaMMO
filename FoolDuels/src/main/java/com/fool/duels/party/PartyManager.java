package com.fool.duels.party;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PartyManager {
    public static final class Party {
        private UUID leader;
        private final Set<UUID> members = new LinkedHashSet<>();
        private final Map<UUID, Long> invites = new HashMap<>();

        Party(UUID leader) {
            this.leader = leader;
            members.add(leader);
        }

        public UUID leader() {
            return leader;
        }

        public Set<UUID> members() {
            return members;
        }

        public int size() {
            return members.size();
        }

        public List<UUID> memberList() {
            return new ArrayList<>(members);
        }
    }

    private final Map<UUID, Party> byMember = new HashMap<>();

    public Party get(UUID player) {
        return byMember.get(player);
    }

    public Party getOrCreate(UUID leader) {
        return byMember.computeIfAbsent(leader, Party::new);
    }

    public void invite(Party party, UUID target, long expiresAt) {
        party.invites.put(target, expiresAt);
    }

    /** @return the party joined, or null if there was no valid invite */
    public Party accept(UUID player, UUID inviterAnyMember) {
        Party party = byMember.get(inviterAnyMember);
        if (party == null) return null;
        Long expires = party.invites.remove(player);
        if (expires == null || expires < System.currentTimeMillis()) return null;
        party.members.add(player);
        byMember.put(player, party);
        return party;
    }

    /** @return the party the player left (possibly now disbanded), or null */
    public Party leave(UUID player) {
        Party party = byMember.remove(player);
        if (party == null) return null;
        party.members.remove(player);
        if (party.members.isEmpty()) return party;
        if (party.leader.equals(player)) party.leader = party.members.iterator().next();
        if (party.members.size() == 1) disband(party);
        return party;
    }

    public void disband(Party party) {
        for (UUID m : party.members) byMember.remove(m);
        party.members.clear();
        party.invites.clear();
    }
}
