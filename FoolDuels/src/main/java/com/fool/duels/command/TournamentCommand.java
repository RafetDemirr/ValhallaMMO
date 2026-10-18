package com.fool.duels.command;

import com.fool.duels.FoolDuels;
import com.fool.duels.kit.KitManager;
import com.fool.duels.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class TournamentCommand implements TabExecutor {
    private static final List<String> SUBS = List.of("katil", "ayril", "bilgi");
    private static final List<String> ADMIN_SUBS = List.of("olustur", "baslat", "iptal", "oduleekle");
    private final FoolDuels plugin;

    public TournamentCommand(FoolDuels plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean admin = sender.hasPermission("foolduels.admin");
        if (args.length == 0) {
            Msg.sendList(sender, "tournament.help");
            if (admin) Msg.sendList(sender, "tournament.help-admin");
            return true;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("bilgi")) {
            plugin.tournament().info(sender);
            return true;
        }
        if (!(sender instanceof Player p)) {
            if (sub.equals("iptal") && admin) plugin.tournament().cancel(null);
            else Msg.send(sender, "player-only");
            return true;
        }
        switch (sub) {
            case "katil" -> plugin.tournament().join(p);
            case "ayril" -> plugin.tournament().leave(p);
            case "olustur", "baslat", "iptal", "oduleekle" -> {
                if (!admin) {
                    Msg.send(p, "no-permission");
                    return true;
                }
                switch (sub) {
                    case "olustur" -> {
                        if (args.length < 3) {
                            Msg.sendList(p, "tournament.help-admin");
                            return true;
                        }
                        double fee;
                        try {
                            fee = Double.parseDouble(args[1].replace(",", "."));
                        } catch (NumberFormatException e) {
                            Msg.sendList(p, "tournament.help-admin");
                            return true;
                        }
                        String kit = null;
                        if (!args[2].equalsIgnoreCase("kendi")) {
                            KitManager.Kit k = plugin.kits().get(args[2]);
                            if (k == null) {
                                p.sendMessage(Msg.parse("<red>Kit bulunamadı: <white>" + args[2]));
                                return true;
                            }
                            kit = k.name;
                        }
                        plugin.tournament().create(p, fee, kit);
                    }
                    case "baslat" -> plugin.tournament().start(p);
                    case "iptal" -> plugin.tournament().cancel(p);
                    case "oduleekle" -> {
                        try {
                            plugin.tournament().addPrize(p, Double.parseDouble(args[1].replace(",", ".")));
                        } catch (RuntimeException e) {
                            Msg.sendList(p, "tournament.help-admin");
                        }
                    }
                    default -> {
                    }
                }
            }
            default -> {
                Msg.sendList(p, "tournament.help");
                if (admin) Msg.sendList(p, "tournament.help-admin");
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        boolean admin = sender.hasPermission("foolduels.admin");
        if (args.length == 1) {
            for (String s : SUBS) if (s.startsWith(args[0].toLowerCase())) out.add(s);
            if (admin) for (String s : ADMIN_SUBS) if (s.startsWith(args[0].toLowerCase())) out.add(s);
        } else if (args.length == 3 && admin && args[0].equalsIgnoreCase("olustur")) {
            out.add("kendi");
            for (KitManager.Kit k : plugin.kits().all()) out.add(k.name);
        }
        return out;
    }
}
