package com.fool.duels.command;

import com.fool.duels.FoolDuels;
import com.fool.duels.party.PartyManager;
import com.fool.duels.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PartyCommand implements TabExecutor {
    private static final List<String> SUBS = List.of("davet", "kabul", "ayril", "at", "dagit", "liste");
    private final FoolDuels plugin;

    public PartyCommand(FoolDuels plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            Msg.send(sender, "player-only");
            return true;
        }
        if (args.length == 0) {
            Msg.sendList(p, "party.help");
            return true;
        }
        PartyManager parties = plugin.parties();
        PartyManager.Party party = parties.get(p.getUniqueId());
        int max = plugin.getConfig().getInt("party.max-size", 4);

        switch (args[0].toLowerCase()) {
            case "davet" -> {
                if (args.length < 2) {
                    Msg.sendList(p, "party.help");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null || target.equals(p)) {
                    Msg.send(p, "player-not-found", "player", args[1]);
                    return true;
                }
                if (party != null && !party.leader().equals(p.getUniqueId())) {
                    Msg.send(p, "party.not-leader");
                    return true;
                }
                if (parties.get(target.getUniqueId()) != null) {
                    Msg.send(p, "party.already-in-party", "player", target.getName());
                    return true;
                }
                if (blockedByMatch(p)) return true;
                party = parties.getOrCreate(p.getUniqueId());
                if (party.size() >= max) {
                    Msg.send(p, "party.full", "max", max);
                    return true;
                }
                parties.invite(party, target.getUniqueId(), System.currentTimeMillis() + plugin.getConfig().getLong("party.invite-timeout", 60) * 1000L);
                Msg.send(p, "party.invited", "player", target.getName());
                Msg.send(target, "party.invite-received", "player", p.getName());
            }
            case "kabul" -> {
                if (args.length < 2) {
                    Msg.sendList(p, "party.help");
                    return true;
                }
                if (party != null) {
                    Msg.send(p, "party.already-in-party", "player", p.getName());
                    return true;
                }
                Player inviter = Bukkit.getPlayerExact(args[1]);
                PartyManager.Party target = inviter == null ? null : parties.get(inviter.getUniqueId());
                if (target != null && target.size() >= max) {
                    Msg.send(p, "party.full", "max", max);
                    return true;
                }
                if (blockedByMatch(p)) return true;
                PartyManager.Party joined = inviter == null ? null : parties.accept(p.getUniqueId(), inviter.getUniqueId());
                if (joined == null) {
                    Msg.send(p, "party.no-invite", "player", args[1]);
                    return true;
                }
                broadcast(joined, "party.joined", p.getName());
            }
            case "ayril" -> {
                if (party == null) {
                    Msg.send(p, "party.not-in-party");
                    return true;
                }
                if (blockedByMatch(p)) return true;
                List<UUID> before = party.memberList();
                parties.leave(p.getUniqueId());
                for (UUID id : before) {
                    Player m = Bukkit.getPlayer(id);
                    if (m != null) Msg.send(m, "party.left", "player", p.getName());
                }
            }
            case "at" -> {
                if (party == null || !party.leader().equals(p.getUniqueId())) {
                    Msg.send(p, party == null ? "party.not-in-party" : "party.not-leader");
                    return true;
                }
                if (args.length < 2) return true;
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null || !party.members().contains(target.getUniqueId()) || target.equals(p)) {
                    Msg.send(p, "player-not-found", "player", args[1]);
                    return true;
                }
                if (blockedByMatch(p)) return true;
                List<UUID> before = party.memberList();
                parties.leave(target.getUniqueId());
                for (UUID id : before) {
                    Player m = Bukkit.getPlayer(id);
                    if (m != null) Msg.send(m, "party.kicked", "player", target.getName());
                }
            }
            case "dagit" -> {
                if (party == null || !party.leader().equals(p.getUniqueId())) {
                    Msg.send(p, party == null ? "party.not-in-party" : "party.not-leader");
                    return true;
                }
                if (blockedByMatch(p)) return true;
                List<UUID> before = party.memberList();
                parties.disband(party);
                for (UUID id : before) {
                    Player m = Bukkit.getPlayer(id);
                    if (m != null) Msg.send(m, "party.disbanded");
                }
            }
            case "liste" -> {
                if (party == null) {
                    Msg.send(p, "party.not-in-party");
                    return true;
                }
                StringBuilder sb = new StringBuilder();
                for (UUID id : party.members()) {
                    if (!sb.isEmpty()) sb.append(", ");
                    String n = Bukkit.getOfflinePlayer(id).getName();
                    sb.append(id.equals(party.leader()) ? "★" : "").append(n == null ? "?" : n);
                }
                Msg.send(p, "party.list", "size", party.size(), "max", max, "members", sb.toString());
            }
            default -> Msg.sendList(p, "party.help");
        }
        return true;
    }

    private boolean blockedByMatch(Player p) {
        PartyManager.Party party = plugin.parties().get(p.getUniqueId());
        List<UUID> check = party == null ? List.of(p.getUniqueId()) : party.memberList();
        for (UUID id : check) if (plugin.matches().inMatch(id)) {
            Msg.send(p, "in-match");
            return true;
        }
        return false;
    }

    private void broadcast(PartyManager.Party party, String key, String name) {
        for (UUID id : party.members()) {
            Player m = Bukkit.getPlayer(id);
            if (m != null) Msg.send(m, key, "player", name);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUBS) if (s.startsWith(args[0].toLowerCase())) out.add(s);
        } else if (args.length == 2) {
            for (Player p : Bukkit.getOnlinePlayers()) if (!p.equals(sender) && p.getName().toLowerCase().startsWith(args[1].toLowerCase())) out.add(p.getName());
        }
        return out;
    }
}
