package com.fool.duels.command;

import com.fool.duels.FoolDuels;
import com.fool.duels.arena.Arena;
import com.fool.duels.data.PlayerState;
import com.fool.duels.data.StateStore;
import com.fool.duels.kit.KitManager;
import com.fool.duels.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** /duelyonet - arenas, kits, backups, elo, reload. Messages here are admin-only, so they are inline. */
public final class AdminCommand implements TabExecutor {
    private final FoolDuels plugin;

    public AdminCommand(FoolDuels plugin) {
        this.plugin = plugin;
    }

    private static void ok(CommandSender s, String msg) {
        s.sendMessage(Msg.parse("<dark_gray>[<gold>FoolDuels</gold>]</dark_gray> <green>" + msg));
    }

    private static void err(CommandSender s, String msg) {
        s.sendMessage(Msg.parse("<dark_gray>[<gold>FoolDuels</gold>]</dark_gray> <red>" + msg));
    }

    private static void help(CommandSender s) {
        for (String line : List.of(
                "<gold><bold>FoolDuels Yönetim",
                "<yellow>/duelyonet arena olustur <ad>",
                "<yellow>/duelyonet arena spawn <ad> <1|2> <gray>- bulunduğun yeri takıma spawn ekler (2v2 için her takıma 2 spawn)",
                "<yellow>/duelyonet arena spawntemizle <ad>",
                "<yellow>/duelyonet arena pos1|pos2 <ad> <gray>- koruma bölgesinin köşeleri",
                "<yellow>/duelyonet arena ac|kapat <ad> <gray>- etkinlik sırasında arenayı kilitle",
                "<yellow>/duelyonet arena sil|tp <ad>",
                "<yellow>/duelyonet arena liste",
                "<yellow>/duelyonet kit olustur <ad> <gray>- envanterini kit olarak kaydeder, elindeki eşya ikon olur",
                "<yellow>/duelyonet kit sil <ad> <gray>| <yellow>liste <gray>| <yellow>al <ad>",
                "<yellow>/duelyonet yedek <oyuncu> <gray>- düello öncesi yedekleri listeler",
                "<yellow>/duelyonet geriyukle <oyuncu> <no> <gray>- yedeği geri yükler (envanter, XP, can...)",
                "<yellow>/duelyonet lobi <gray>- bekleme/izleme lobisini bulunduğun yere ayarlar",
                "<yellow>/duelyonet elo <oyuncu> <değer>",
                "<yellow>/duelyonet reload")) s.sendMessage(Msg.parse(line));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "arena" -> arena(sender, args);
            case "kit" -> kit(sender, args);
            case "yedek" -> backups(sender, args);
            case "geriyukle" -> restore(sender, args);
            case "elo" -> {
                if (args.length < 3) {
                    help(sender);
                    return true;
                }
                OfflinePlayer t = Bukkit.getOfflinePlayer(args[1]);
                try {
                    plugin.stats().set(t.getUniqueId(), t.getName(), Integer.parseInt(args[2]));
                    ok(sender, args[1] + " ELO: " + args[2]);
                } catch (NumberFormatException e) {
                    err(sender, "Geçersiz sayı.");
                }
            }
            case "lobi" -> {
                if (!(sender instanceof Player p)) {
                    Msg.send(sender, "player-only");
                    return true;
                }
                plugin.lobby().setLobby(p.getLocation());
                ok(sender, "Lobi ayarlandı. Arena koruma bölgesinin (pos1-pos2) dışında olmasına dikkat et.");
            }
            case "reload" -> {
                plugin.reloadAll();
                ok(sender, "Yeniden yüklendi.");
            }
            default -> help(sender);
        }
        return true;
    }

    private void arena(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("liste")) {
            for (Arena a : plugin.arenas().all()) {
                sender.sendMessage(Msg.parse("<gray>- <white>" + a.name() + " <gray>| " + (a.enabled() ? "<green>açık" : "<red>kapalı")
                        + " <gray>| " + (a.inUse() ? "<yellow>maç var" : "<green>boş") + " <gray>| spawn: <white>" + a.spawns(0).size() + "/" + a.spawns(1).size()
                        + " <gray>| bölge: " + (a.hasBounds() ? "<green>var" : "<red>yok")));
            }
            return;
        }
        if (args.length < 3) {
            help(sender);
            return;
        }
        String action = args[1].toLowerCase(), name = args[2];
        if (action.equals("olustur")) {
            if (plugin.arenas().get(name) != null) {
                err(sender, "Bu isimde arena zaten var.");
                return;
            }
            plugin.arenas().create(name);
            plugin.arenas().save();
            ok(sender, "Arena oluşturuldu: " + name + ". Şimdi /duelyonet arena spawn " + name + " 1 ve 2 ile spawn ekle.");
            return;
        }
        Arena a = plugin.arenas().get(name);
        if (a == null) {
            err(sender, "Arena bulunamadı: " + name);
            return;
        }
        switch (action) {
            case "sil" -> {
                if (a.inUse()) {
                    err(sender, "Arenada maç var.");
                    return;
                }
                plugin.arenas().delete(name);
                ok(sender, "Arena silindi.");
            }
            case "ac", "kapat" -> {
                a.enabled(action.equals("ac"));
                ok(sender, "Arena " + (a.enabled() ? "açıldı." : "kapatıldı. Devam eden maç bitince yeni maç başlamaz."));
            }
            case "spawntemizle" -> {
                a.spawns(0).clear();
                a.spawns(1).clear();
                ok(sender, "Spawnlar temizlendi.");
            }
            case "spawn", "pos1", "pos2", "tp" -> {
                if (!(sender instanceof Player p)) {
                    Msg.send(sender, "player-only");
                    return;
                }
                switch (action) {
                    case "spawn" -> {
                        int team = args.length > 3 && args[3].equals("2") ? 1 : 0;
                        a.spawns(team).add(p.getLocation().clone());
                        ok(sender, (team + 1) + ". takıma spawn eklendi (" + a.spawns(team).size() + ").");
                    }
                    case "pos1" -> {
                        a.pos1(p.getLocation());
                        ok(sender, "pos1 ayarlandı.");
                    }
                    case "pos2" -> {
                        a.pos2(p.getLocation());
                        ok(sender, "pos2 ayarlandı." + (a.hasBounds() ? " Koruma bölgesi aktif." : ""));
                    }
                    case "tp" -> {
                        if (a.ready()) p.teleport(a.spawn(0, 0));
                        else err(sender, "Arenanın spawnı yok.");
                        return;
                    }
                    default -> {
                    }
                }
            }
            default -> {
                help(sender);
                return;
            }
        }
        plugin.arenas().save();
    }

    private void kit(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("liste")) {
            for (KitManager.Kit k : plugin.kits().all()) sender.sendMessage(Msg.parse("<gray>- <white>" + k.name + " <gray>(" + k.icon + ")"));
            return;
        }
        if (args.length < 3) {
            help(sender);
            return;
        }
        String action = args[1].toLowerCase(), name = args[2];
        switch (action) {
            case "olustur" -> {
                if (!(sender instanceof Player p)) {
                    Msg.send(sender, "player-only");
                    return;
                }
                plugin.kits().createFrom(name, p);
                ok(sender, "Kit kaydedildi: " + name);
            }
            case "sil" -> {
                if (plugin.kits().delete(name)) ok(sender, "Kit silindi.");
                else err(sender, "Kit bulunamadı.");
            }
            case "al" -> {
                if (!(sender instanceof Player p)) return;
                KitManager.Kit k = plugin.kits().get(name);
                if (k == null) {
                    err(sender, "Kit bulunamadı.");
                    return;
                }
                if (plugin.matches().inMatch(p.getUniqueId())) return;
                // Preview only: the kit replaces the inventory, so take a backup first.
                plugin.store().backup(p.getUniqueId(), PlayerState.capture(p));
                p.getInventory().setContents(k.contents.clone());
                ok(sender, "Kit verildi (önceki envanterin yedeklendi: /duelyonet yedek " + p.getName() + ").");
            }
            default -> help(sender);
        }
    }

    private void backups(CommandSender sender, String[] args) {
        if (args.length < 2) {
            help(sender);
            return;
        }
        OfflinePlayer t = Bukkit.getOfflinePlayer(args[1]);
        List<StateStore.Backup> list = plugin.store().listBackups(t.getUniqueId());
        if (list.isEmpty()) {
            err(sender, "Yedek yok.");
            return;
        }
        sender.sendMessage(Msg.parse("<gold>" + args[1] + " yedekleri (yeniden eskiye):"));
        for (int i = 0; i < list.size(); i++) {
            sender.sendMessage(Msg.parse("<yellow>" + (i + 1) + ". <white>" + list.get(i).date()
                    + " <click:suggest_command:'/duelyonet geriyukle " + args[1] + " " + (i + 1) + "'><green>[GERİ YÜKLE]</green></click>"));
        }
    }

    private void restore(CommandSender sender, String[] args) {
        if (args.length < 3) {
            help(sender);
            return;
        }
        Player t = Bukkit.getPlayerExact(args[1]);
        if (t == null) {
            err(sender, "Oyuncu çevrimiçi olmalı.");
            return;
        }
        if (plugin.matches().inMatch(t.getUniqueId())) {
            err(sender, "Oyuncu şu an maçta.");
            return;
        }
        List<StateStore.Backup> list = plugin.store().listBackups(t.getUniqueId());
        int index;
        try {
            index = Integer.parseInt(args[2]) - 1;
        } catch (NumberFormatException e) {
            err(sender, "Geçersiz numara.");
            return;
        }
        if (index < 0 || index >= list.size()) {
            err(sender, "Böyle bir yedek yok.");
            return;
        }
        try {
            // Back up the current state first so a wrong restore can be undone.
            plugin.store().backup(t.getUniqueId(), PlayerState.capture(t));
            plugin.store().loadBackup(list.get(index)).apply(t, true);
            ok(sender, t.getName() + " oyuncusunun " + list.get(index).date() + " yedeği geri yüklendi (konum hariç).");
        } catch (IOException | RuntimeException e) {
            err(sender, "Yedek okunamadı: " + e.getMessage());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        List<String> options;
        if (args.length == 1) options = List.of("arena", "kit", "yedek", "geriyukle", "elo", "lobi", "reload");
        else if (args.length == 2 && args[0].equalsIgnoreCase("arena"))
            options = List.of("olustur", "spawn", "spawntemizle", "pos1", "pos2", "ac", "kapat", "sil", "tp", "liste");
        else if (args.length == 2 && args[0].equalsIgnoreCase("kit")) options = List.of("olustur", "sil", "liste", "al");
        else if (args.length == 3 && args[0].equalsIgnoreCase("arena")) {
            options = new ArrayList<>();
            for (Arena a : plugin.arenas().all()) options.add(a.name());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("kit")) {
            options = new ArrayList<>();
            for (KitManager.Kit k : plugin.kits().all()) options.add(k.name);
        } else if (args.length == 4 && args[0].equalsIgnoreCase("arena") && args[1].equalsIgnoreCase("spawn")) options = List.of("1", "2");
        else if (args.length == 2) {
            options = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) options.add(p.getName());
        } else options = List.of();
        String last = args[args.length - 1].toLowerCase();
        for (String o : options) if (o.toLowerCase().startsWith(last)) out.add(o);
        return out;
    }
}
