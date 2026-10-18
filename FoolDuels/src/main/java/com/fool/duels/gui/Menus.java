package com.fool.duels.gui;

import com.fool.duels.FoolDuels;
import com.fool.duels.kit.KitManager;
import com.fool.duels.util.Items;
import com.fool.duels.util.Msg;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * /duel menu: bet amount, kit (or own inventory), send.
 *
 * <pre>
 *  [ ][ ][ ][ ][Hedef][ ][ ][ ][ ]
 *  [ ][-1000][-100][-10][Bahis][+10][+100][+1000][ ]
 *  [İptal][ ][ ][ ][Kit][ ][ ][ ][Gönder]
 * </pre>
 */
public final class Menus implements Listener {
    private static final int SLOT_TARGET = 4, SLOT_BET = 13, SLOT_KIT = 22, SLOT_CANCEL = 18, SLOT_SEND = 26;
    private static final int[] SLOTS_MINUS = {12, 11, 10};
    private static final int[] SLOTS_PLUS = {14, 15, 16};

    /** Per-sender settings of the menu being edited. */
    private static final class DuelSetup implements InventoryHolder {
        final UUID target;
        double bet;
        String kit; // null = own inventory
        Inventory inventory;

        DuelSetup(UUID target, String kit) {
            this.target = target;
            this.kit = kit;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class KitSelect implements InventoryHolder {
        final DuelSetup setup;
        final List<String> slots = new ArrayList<>();
        Inventory inventory;

        KitSelect(DuelSetup setup) {
            this.setup = setup;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final FoolDuels plugin;
    private final Map<UUID, Consumer<String>> chatInput = new java.util.concurrent.ConcurrentHashMap<>();

    public Menus(FoolDuels plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ duel menu

    public void openDuel(Player sender, Player target) {
        String defaultKit = plugin.getConfig().getBoolean("duel.allow-own-inventory", true) ? null
                : plugin.kits().all().stream().findFirst().map(k -> k.name).orElse(null);
        DuelSetup setup = new DuelSetup(target.getUniqueId(), defaultKit);
        setup.inventory = Bukkit.createInventory(setup, 27, Msg.parse("<dark_gray>Düello » <gold>" + target.getName()));
        render(sender, setup);
        sender.openInventory(setup.inventory);
    }

    private void render(Player viewer, DuelSetup s) {
        Inventory inv = s.inventory;
        inv.clear();
        ItemStack filler = Items.make(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        Player target = Bukkit.getPlayer(s.target);
        String targetName = target == null ? "?" : target.getName();
        List<Player> a = plugin.requests().teamOf(viewer);
        List<Player> b = target == null ? null : plugin.requests().teamOf(target);
        String mode = (a == null ? 1 : a.size()) + "v" + (b == null ? 1 : b.size());
        inv.setItem(SLOT_TARGET, Items.head(target == null ? viewer : target, "<gold><bold>" + targetName,
                "<gray>Mod: <yellow>" + mode,
                "<gray>ELO: <yellow>" + (target == null ? "-" : plugin.stats().elo(target.getUniqueId()))));

        boolean betOn = betEnabled();
        if (betOn) {
            List<Integer> steps = plugin.getConfig().getIntegerList("bet.steps");
            for (int i = 0; i < Math.min(3, steps.size()); i++) {
                String amount = plugin.economy().format(steps.get(i));
                inv.setItem(SLOTS_MINUS[i], Items.make(Material.RED_STAINED_GLASS_PANE, "<red>-" + amount));
                inv.setItem(SLOTS_PLUS[i], Items.make(Material.LIME_STAINED_GLASS_PANE, "<green>+" + amount));
            }
            inv.setItem(SLOT_BET, Items.make(Material.GOLD_INGOT, "<gold><bold>Bahis: <yellow>" + plugin.economy().format(s.bet),
                    "<gray>Kazanan takım tüm bahsi alır.",
                    "<gray>Vergi: <yellow>%" + plugin.getConfig().getDouble("bet.tax-percent", 0),
                    "",
                    "<yellow>Tıkla: <white>miktarı sohbete yaz"));
        } else {
            inv.setItem(SLOT_BET, Items.make(Material.BARRIER, "<red>Bahis kapalı"));
        }

        KitManager.Kit kit = plugin.kits().get(s.kit);
        if (s.kit == null) {
            inv.setItem(SLOT_KIT, Items.make(Material.CHEST, "<aqua><bold>Kit: <white>Kendi Envanterin",
                    "<gray>Herkes kendi eşyalarıyla dövüşür.",
                    "<gray>Maçta kullanılan eşyalar geri gelmez.",
                    "", "<yellow>Tıkla: <white>kit seç"));
        } else {
            inv.setItem(SLOT_KIT, Items.make(kit == null ? Material.IRON_SWORD : kit.icon, "<aqua><bold>Kit: <white>" + s.kit,
                    "<gray>Herkese aynı kit verilir.",
                    "<gray>Maçtan sonra envanterin geri gelir.",
                    "", "<yellow>Tıkla: <white>kit seç"));
        }
        inv.setItem(SLOT_CANCEL, Items.make(Material.BARRIER, "<red><bold>İptal"));
        inv.setItem(SLOT_SEND, Items.make(Material.LIME_CONCRETE, "<green><bold>İsteği Gönder",
                "<gray>Rakip: <white>" + targetName,
                "<gray>Mod: <white>" + mode,
                "<gray>Bahis: <white>" + (betOn && s.bet > 0 ? plugin.economy().format(s.bet) : "-"),
                "<gray>Kit: <white>" + (s.kit == null ? "Kendi Envanterin" : s.kit)));
    }

    private boolean betEnabled() {
        return plugin.getConfig().getBoolean("bet.enabled", true) && plugin.economy().enabled();
    }

    private double clampBet(double v) {
        double min = plugin.getConfig().getDouble("bet.min", 0), max = plugin.getConfig().getDouble("bet.max", 1_000_000);
        return Math.max(min, Math.min(max, v));
    }

    private void openKits(Player p, DuelSetup setup) {
        KitSelect ks = new KitSelect(setup);
        ks.inventory = Bukkit.createInventory(ks, 54, Msg.parse("<dark_gray>Kit Seç"));
        int slot = 0;
        if (plugin.getConfig().getBoolean("duel.allow-own-inventory", true)) {
            ks.inventory.setItem(slot++, Items.make(Material.CHEST, "<aqua><bold>Kendi Envanterin",
                    "<gray>Herkes kendi eşyalarıyla dövüşür."));
            ks.slots.add(null);
        }
        for (KitManager.Kit k : plugin.kits().all()) {
            if (slot >= 45) break;
            ks.inventory.setItem(slot++, Items.make(k.icon, "<aqua><bold>" + k.name, "<yellow>Tıkla: <white>seç"));
            ks.slots.add(k.name);
        }
        ks.inventory.setItem(49, Items.make(Material.ARROW, "<gray>Geri"));
        p.openInventory(ks.inventory);
    }

    // ------------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        InventoryHolder holder = e.getInventory().getHolder(false);
        if (!(holder instanceof DuelSetup) && !(holder instanceof KitSelect)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getInventory()) return;
        int slot = e.getRawSlot();

        if (holder instanceof KitSelect ks) {
            if (slot == 49) {
                reopen(p, ks.setup);
            } else if (slot >= 0 && slot < ks.slots.size()) {
                ks.setup.kit = ks.slots.get(slot);
                reopen(p, ks.setup);
            }
            return;
        }

        DuelSetup s = (DuelSetup) holder;
        Player target = Bukkit.getPlayer(s.target);
        if (target == null) {
            p.closeInventory();
            Msg.send(p, "player-not-found", "player", "?");
            return;
        }
        List<Integer> steps = plugin.getConfig().getIntegerList("bet.steps");
        for (int i = 0; i < 3; i++) {
            if (!betEnabled() || i >= steps.size()) break;
            if (slot == SLOTS_MINUS[i]) s.bet = clampBet(s.bet - steps.get(i));
            if (slot == SLOTS_PLUS[i]) s.bet = clampBet(s.bet + steps.get(i));
        }
        switch (slot) {
            case SLOT_BET -> {
                if (!betEnabled()) return;
                p.closeInventory();
                Msg.send(p, "bet-input");
                chatInput.put(p.getUniqueId(), text -> {
                    if (!text.equalsIgnoreCase("iptal")) {
                        try {
                            double v = Double.parseDouble(text.replace(",", ".").trim());
                            double min = plugin.getConfig().getDouble("bet.min", 0), max = plugin.getConfig().getDouble("bet.max", 1_000_000);
                            if (v < min || v > max || Double.isNaN(v)) throw new NumberFormatException();
                            s.bet = Math.floor(v * 100) / 100;
                        } catch (NumberFormatException ex) {
                            Msg.send(p, "bet-invalid", "min", plugin.getConfig().getDouble("bet.min", 0), "max", plugin.getConfig().getDouble("bet.max", 1_000_000));
                        }
                    }
                    reopen(p, s);
                });
                return;
            }
            case SLOT_KIT -> {
                openKits(p, s);
                return;
            }
            case SLOT_CANCEL -> {
                p.closeInventory();
                return;
            }
            case SLOT_SEND -> {
                p.closeInventory();
                plugin.requests().send(p, target, betEnabled() ? s.bet : 0, s.kit);
                return;
            }
            default -> {
            }
        }
        render(p, s);
    }

    private void reopen(Player p, DuelSetup s) {
        if (Bukkit.getPlayer(s.target) == null) return;
        render(p, s);
        p.openInventory(s.inventory);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        InventoryHolder holder = e.getInventory().getHolder(false);
        if (holder instanceof DuelSetup || holder instanceof KitSelect) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Consumer<String> handler = chatInput.remove(e.getPlayer().getUniqueId());
        if (handler == null) return;
        e.setCancelled(true);
        String text = Msg.plain(e.message());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (e.getPlayer().isOnline()) handler.accept(text);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        chatInput.remove(e.getPlayer().getUniqueId());
    }
}
