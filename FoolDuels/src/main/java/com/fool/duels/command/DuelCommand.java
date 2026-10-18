package com.fool.duels.command;

import com.fool.duels.FoolDuels;
import com.fool.duels.data.StatsManager;
import com.fool.duels.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DuelCommand implements TabExecutor {
    private static final List<String> SUBS = List.of("kabul", "red", "iptal", "toggle", "elo", "top");
    private final FoolDuels plugin;

    public DuelCommand(FoolDuels plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            Msg.sendList(sender, "help");
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "elo" -> {
                OfflinePlayer target = args.length > 1 ? Bukkit.getOfflinePlayer(args[1]) : (sender instanceof Player p ? p : null);
                if (target == null) {
                    Msg.send(sender, "player-only");
                    return true;
                }
                StatsManager.Stats s = plugin.stats().find(target.getUniqueId());
                Msg.send(sender, "elo-info", "player", target.getName() == null ? args[1] : target.getName(),
                        "elo", s == null ? plugin.stats().elo(target.getUniqueId()) : s.elo,
                        "wins", s == null ? 0 : s.wins, "losses", s == null ? 0 : s.losses);
                return true;
            }
            case "top" -> {
                sender.sendMessage(Msg.get("elo-top-header"));
                int rank = 1;
                for (Map.Entry<UUID, StatsManager.Stats> e : plugin.stats().top(10)) {
                    sender.sendMessage(Msg.get("elo-top-line", "rank", rank++, "player", e.getValue().name, "elo", e.getValue().elo));
                }
                return true;
            }
            default -> {
            }
        }

        if (!(sender instanceof Player p)) {
            Msg.send(sender, "player-only");
            return true;
        }
        switch (sub) {
            case "kabul" -> plugin.requests().accept(p, args.length > 1 ? args[1] : null);
            case "red" -> plugin.requests().deny(p, args.length > 1 ? args[1] : null);
            case "iptal" -> plugin.requests().cancelOutgoing(p);
            case "toggle" -> Msg.send(p, plugin.requests().toggle(p.getUniqueId()) ? "toggle-on" : "toggle-off");
            default -> {
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    Msg.send(p, "player-not-found", "player", args[0]);
                    return true;
                }
                if (plugin.matches().inMatch(p.getUniqueId())) {
                    Msg.send(p, "in-match");
                    return true;
                }
                if (!plugin.requests().validate(p, target, 0, p)) return true;
                plugin.menus().openDuel(p, target);
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String a = args[0].toLowerCase();
            for (String s : SUBS) if (s.startsWith(a)) out.add(s);
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase().startsWith(a) && !p.equals(sender)) out.add(p.getName());
        } else if (args.length == 2 && sender instanceof Player p) {
            String sub = args[0].toLowerCase();
            if (sub.equals("kabul") || sub.equals("red")) out.addAll(plugin.requests().incomingNames(p.getUniqueId()));
            else if (sub.equals("elo")) for (Player o : Bukkit.getOnlinePlayers()) out.add(o.getName());
        }
        return out;
    }
}
