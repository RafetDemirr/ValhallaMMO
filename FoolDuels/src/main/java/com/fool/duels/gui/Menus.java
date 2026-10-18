package com.fool.duels.gui;

import com.fool.duels.FoolDuels;
import com.fool.duels.arena.Arena;
import com.fool.duels.data.StatsManager;
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
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * /duel menu (5 rows):
 * <pre>
 *  ■ ■ ■ ■ [⚔] ■ ■ ■ ■
 *  ■ ■ [Sen] ■ [VS] ■ [Rakip] ■ ■
 *  ■ [-1000][-100][-10][Bahis][+10][+100][+1000] ■
 *  ■ ■ [Kit] ■ [Arenalar] ■ [Sıra] ■ ■
 *  ■ [İptal] ■ ■ [Bilgi] ■ ■ [Gönder] ■
 * </pre>
 */
public final class Menus implements Listener {
    private static final int SIZE = 45;
    private static final int SLOT_TITLE = 4, SLOT_SELF = 11, SLOT_VS = 13, SLOT_TARGET = 15;
    private static final int SLOT_BET = 22, SLOT_KIT = 29, SLOT_ARENAS = 31, SLOT_QUEUE = 33;
    private static final int SLOT_CANCEL = 37, SLOT_INFO = 40, SLOT_SEND = 43;
    private static final int[] SLOTS_MINUS = {21, 20, 19};
    private static final int[] SLOTS_PLUS = {23, 24, 25};
    private static final int[] KIT_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

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
        final Map<Integer, String> slots = new java.util.HashMap<>();
        Inventory inventory;

        KitSelect(DuelSetup setup) {
            this.setup = setup;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final String OWN = "\u0000own";

    private final FoolDuels plugin;
    private final Map<UUID, Consumer<String>> chatInput = new ConcurrentHashMap<>();
    /** Players whose next chat message (in any chat event) must be swallowed, until this time. */
    private final Map<UUID, Long> swallow = new ConcurrentHashMap<>();

    public Menus(FoolDuels plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ duel menu

    public void openDuel(Player sender, Player target) {
        String defaultKit = plugin.getConfig().getBoolean("duel.allow-own-inventory", true) ? null
                : plugin.kits().all().stream().findFirst().map(k -> k.name).orElse(null);
        DuelSetup setup = new DuelSetup(target.getUniqueId(), defaultKit);
        setup.inventory = Bukkit.createInventory(setup, SIZE, Msg.parse("<dark_gray>⚔ Düello » <gold><bold>" + target.getName()));
        render(sender, setup);
        sender.openInventory(setup.inventory);
    }

    private void frame(Inventory inv) {
        ItemStack fill = Items.make(Material.GRAY_STAINED_GLASS_PANE, " ");
        ItemStack edge = Items.make(Material.BLACK_STAINED_GLASS_PANE, " ");
        ItemStack accent = Items.make(Material.ORANGE_STAINED_GLASS_PANE, " ");
        int rows = inv.getSize() / 9;
        for (int i = 0; i < inv.getSize(); i++) {
            int r = i / 9, c = i % 9;
            boolean border = r == 0 || r == rows - 1 || c == 0 || c == 8;
            boolean corner = (r == 0 || r == rows - 1) && (c == 0 || c == 8);
            inv.setItem(i, corner ? accent : border ? edge : fill);
        }
    }

    private List<String> statLines(UUID id) {
        StatsManager.Stats s = plugin.stats().find(id);
        int wins = s == null ? 0 : s.wins, losses = s == null ? 0 : s.losses;
        int total = wins + losses;
        int rank = plugin.stats().rank(id);
        return List.of(
                "<gray>ELO: <gold>" + plugin.stats().elo(id) + (rank > 0 ? " <dark_gray>(#" + rank + ")" : ""),
                "<gray>Galibiyet: <green>" + wins + " <dark_gray>· <gray>Mağlubiyet: <red>" + losses,
                "<gray>Kazanma oranı: <yellow>%" + (total == 0 ? 0 : Math.round(wins * 100.0 / total)));
    }

    private void render(Player viewer, DuelSetup s) {
        Inventory inv = s.inventory;
        frame(inv);

        Player target = Bukkit.getPlayer(s.target);
        String targetName = target == null ? "?" : target.getName();
        List<Player> a = plugin.requests().teamOf(viewer);
        List<Player> b = target == null ? null : plugin.requests().teamOf(target);
        int sizeA = a == null ? 1 : a.size(), sizeB = b == null ? 1 : b.size();
        String mode = sizeA + "v" + sizeB;
        boolean betOn = betEnabled();
        KitManager.Kit kit = plugin.kits().get(s.kit);
        String kitName = s.kit == null ? "Kendi Envanterin" : s.kit;

        inv.setItem(SLOT_TITLE, Items.make(Material.NETHERITE_SWORD, "<gold><bold>⚔ DÜELLO ⚔",
                "<gray>Mod: <white>" + mode,
                "<gray>Kit: <white>" + kitName,
                "<gray>Bahis: <white>" + (betOn && s.bet > 0 ? plugin.economy().format(s.bet) : "-")));

        List<String> self = new ArrayList<>(List.of("<dark_gray>Sen", ""));
        self.addAll(statLines(viewer.getUniqueId()));
        if (a != null && a.size() > 1) {
            self.add("");
            self.add("<gray>Takımın:");
            for (Player p : a) self.add("<dark_gray>- <white>" + p.getName());
        }
        inv.setItem(SLOT_SELF, Items.head(viewer, "<aqua><bold>" + viewer.getName(), self.toArray(String[]::new)));

        inv.setItem(SLOT_VS, Items.make(Material.IRON_SWORD, "<red><bold>VS", "<gray>" + mode));

        List<String> opp = new ArrayList<>(List.of("<dark_gray>Rakip", ""));
        if (target != null) opp.addAll(statLines(target.getUniqueId()));
        if (b != null && b.size() > 1) {
            opp.add("");
            opp.add("<gray>Rakip takım:");
            for (Player p : b) opp.add("<dark_gray>- <white>" + p.getName());
        }
        inv.setItem(SLOT_TARGET, Items.head(target == null ? viewer : target, "<red><bold>" + targetName, opp.toArray(String[]::new)));

        // ---- bet row
        if (betOn) {
            List<Integer> steps = plugin.getConfig().getIntegerList("bet.steps");
            for (int i = 0; i < Math.min(3, steps.size()); i++) {
                String amount = plugin.economy().format(steps.get(i));
                inv.setItem(SLOTS_MINUS[i], Items.make(Material.RED_STAINED_GLASS_PANE, "<red><bold>-" + amount, "<gray>Bahsi azalt"));
                inv.setItem(SLOTS_PLUS[i], Items.make(Material.LIME_STAINED_GLASS_PANE, "<green><bold>+" + amount, "<gray>Bahsi artır"));
            }
            double tax = plugin.matches().taxPercent(viewer);
            double gross = s.bet * (sizeA + sizeB) / sizeA;
            double net = gross * (1 - tax / 100.0);
            inv.setItem(SLOT_BET, Items.make(s.bet > 0 ? Material.GOLD_BLOCK : Material.GOLD_NUGGET,
                    "<gold><bold>Bahis: <yellow>" + plugin.economy().format(s.bet),
                    "<gray>Kişi başı yatırılan miktar.",
                    "",
                    "<gray>Toplam havuz: <yellow>" + plugin.economy().format(s.bet * (sizeA + sizeB)),
                    "<gray>Vergi oranın: <yellow>%" + trim(tax),
                    "<gray>Kazanırsan alacağın: <green>" + plugin.economy().format(net),
                    "",
                    "<yellow>▶ Tıkla: <white>miktarı sohbete yaz"));
        } else {
            inv.setItem(SLOT_BET, Items.make(Material.BARRIER, "<red><bold>Bahis kapalı", "<gray>Ekonomi eklentisi bulunamadı."));
        }

        // ---- kit
        List<String> kitLore = new ArrayList<>();
        if (s.kit == null) {
            kitLore.add("<gray>Herkes kendi eşyalarıyla dövüşür.");
            kitLore.add("<gray>Maçta kullanılan eşyalar geri gelmez,");
            kitLore.add("<gray>XP ve can maçtan sonra geri yüklenir.");
        } else {
            kitLore.add("<gray>Herkese aynı kit verilir,");
            kitLore.add("<gray>maçtan sonra envanterin geri gelir.");
            kitLore.addAll(kitPreview(kit));
        }
        kitLore.add("");
        kitLore.add("<yellow>▶ Tıkla: <white>kit seç");
        inv.setItem(SLOT_KIT, Items.make(s.kit == null ? Material.CHEST : kit == null ? Material.IRON_SWORD : kit.icon,
                "<aqua><bold>Kit: <white>" + kitName, kitLore.toArray(String[]::new)));

        // ---- arenas
        int free = 0, total = 0;
        List<String> arenaLore = new ArrayList<>();
        for (Arena ar : plugin.arenas().all()) {
            if (!ar.ready()) continue;
            total++;
            String state;
            if (!ar.enabled()) state = "<red>kapalı";
            else if (ar.inUse()) state = "<yellow>maç var";
            else {
                state = "<green>boş";
                free++;
            }
            if (arenaLore.size() < 8) arenaLore.add("<dark_gray>- <white>" + ar.name() + " <dark_gray>» " + state);
        }
        arenaLore.add(0, "");
        arenaLore.add(0, "<gray>Boş arena: " + (free > 0 ? "<green>" : "<red>") + free + "<gray>/" + total);
        arenaLore.add("");
        arenaLore.add("<gray>Aktif maç: <white>" + plugin.matches().matches().size());
        inv.setItem(SLOT_ARENAS, Items.make(free > 0 ? Material.FILLED_MAP : Material.MAP, "<green><bold>Arenalar", arenaLore.toArray(String[]::new)));

        // ---- queue
        List<String> queueLore = new ArrayList<>();
        if (plugin.getConfig().getBoolean("queue.enabled", true)) {
            queueLore.add("<gray>Sıradaki oyuncular:");
            for (String k : plugin.queues().keys()) {
                int n = plugin.queues().size(k);
                queueLore.add("<dark_gray>- <white>" + (k.equals("kendi") ? "Kendi Envanteri" : k) + " <dark_gray>» " + (n > 0 ? "<green>" : "<gray>") + n);
            }
            queueLore.add("");
            queueLore.add("<gray>Sıra ELO'na yakın biriyle eşleştirir.");
            queueLore.add("<yellow>▶ Tıkla: <white>" + kitName + " sırasına gir");
        } else {
            queueLore.add("<red>Sıra sistemi kapalı.");
        }
        inv.setItem(SLOT_QUEUE, Items.make(Material.CLOCK, "<light_purple><bold>Sıra (Dereceli)", queueLore.toArray(String[]::new)));

        // ---- bottom row
        inv.setItem(SLOT_CANCEL, Items.make(Material.RED_CONCRETE, "<red><bold>✖ İptal"));
        inv.setItem(SLOT_INFO, Items.make(Material.BOOK, "<yellow><bold>Bilgi",
                "<gray>İstek <white>" + plugin.getConfig().getLong("duel.request-timeout", 60) + " sn <gray>geçerlidir.",
                "<gray>Maç süresi en fazla <white>" + plugin.getConfig().getLong("duel.max-duration", 300) / 60 + " dk<gray>.",
                "<gray>Maçta komutlar engellenir.",
                "<gray>Oyundan çıkan maçı kaybeder,",
                "<gray>ama eşyaları kaybolmaz.",
                "<gray>Berabere/iptal: bahisler iade."));
        long cooldown = target == null ? 0 : plugin.requests().cooldownLeft(viewer, target);
        if (cooldown > 0) {
            inv.setItem(SLOT_SEND, Items.make(Material.GRAY_CONCRETE, "<gray><bold>Bekle: " + cooldown + " sn",
                    "<gray>Bu oyuncuya kısa süre önce istek attın."));
        } else {
            inv.setItem(SLOT_SEND, Items.make(Material.LIME_CONCRETE, "<green><bold>✔ İsteği Gönder",
                    "<gray>Rakip: <white>" + targetName,
                    "<gray>Mod: <white>" + mode,
                    "<gray>Bahis: <white>" + (betOn && s.bet > 0 ? plugin.economy().format(s.bet) : "-"),
                    "<gray>Kit: <white>" + kitName));
        }
    }

    private List<String> kitPreview(KitManager.Kit kit) {
        List<String> out = new ArrayList<>();
        if (kit == null) return out;
        out.add("");
        out.add("<gray>İçerik:");
        int shown = 0, hidden = 0;
        for (ItemStack item : kit.contents) {
            if (item == null || item.getType().isAir()) continue;
            if (shown < 7) {
                out.add("<dark_gray>- <white>" + item.getAmount() + "x " + pretty(item.getType()));
                shown++;
            } else hidden++;
        }
        if (hidden > 0) out.add("<dark_gray>  ...ve " + hidden + " eşya daha");
        return out;
    }

    private static String pretty(Material m) {
        String s = m.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String trim(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
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
        ks.inventory = Bukkit.createInventory(ks, 54, Msg.parse("<dark_gray>⚔ Kit Seç"));
        frame(ks.inventory);
        int i = 0;
        if (plugin.getConfig().getBoolean("duel.allow-own-inventory", true)) {
            ks.inventory.setItem(KIT_SLOTS[i], Items.make(Material.CHEST, "<aqua><bold>Kendi Envanterin",
                    "<gray>Herkes kendi eşyalarıyla dövüşür.",
                    "<gray>Kullanılan eşyalar geri gelmez.",
                    "", setup.kit == null ? "<green>✔ Seçili" : "<yellow>▶ Tıkla: <white>seç"));
            ks.slots.put(KIT_SLOTS[i++], OWN);
        }
        for (KitManager.Kit k : plugin.kits().all()) {
            if (i >= KIT_SLOTS.length) break;
            List<String> lore = new ArrayList<>(kitPreview(k));
            lore.add("");
            lore.add(k.name.equalsIgnoreCase(String.valueOf(setup.kit)) ? "<green>✔ Seçili" : "<yellow>▶ Tıkla: <white>seç");
            ks.inventory.setItem(KIT_SLOTS[i], Items.make(k.icon, "<aqua><bold>" + k.name, lore.toArray(String[]::new)));
            ks.slots.put(KIT_SLOTS[i++], k.name);
        }
        ks.inventory.setItem(49, Items.make(Material.ARROW, "<gray>« Geri"));
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
            } else if (ks.slots.containsKey(slot)) {
                String k = ks.slots.get(slot);
                ks.setup.kit = OWN.equals(k) ? null : k;
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
            case SLOT_QUEUE -> {
                if (!plugin.getConfig().getBoolean("queue.enabled", true)) return;
                p.closeInventory();
                plugin.queues().join(p, s.kit == null ? "kendi" : s.kit);
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

    // ------------------------------------------------------------------ chat input
    // The typed bet amount must never reach chat. Paper fires the legacy AsyncPlayerChatEvent (used by many
    // chat plugins) and the modern AsyncChatEvent; we grab the message in whichever runs first, then cancel
    // and empty the recipients of both, at LOWEST and again at HIGHEST in case a chat plugin un-cancels it.

    private boolean consume(UUID id, String text) {
        Consumer<String> handler = chatInput.remove(id);
        if (handler != null) {
            swallow.put(id, System.currentTimeMillis() + 3000);
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.isOnline()) handler.accept(text);
            });
            return true;
        }
        Long until = swallow.get(id);
        if (until == null) return false;
        if (until < System.currentTimeMillis()) {
            swallow.remove(id);
            return false;
        }
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    @SuppressWarnings("deprecation")
    public void onLegacyChat(AsyncPlayerChatEvent e) {
        if (!consume(e.getPlayer().getUniqueId(), e.getMessage())) return;
        e.setCancelled(true);
        e.getRecipients().clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    @SuppressWarnings("deprecation")
    public void onLegacyChatEnforce(AsyncPlayerChatEvent e) {
        if (swallow.containsKey(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            e.getRecipients().clear();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        if (!consume(e.getPlayer().getUniqueId(), Msg.plain(e.message()))) return;
        e.setCancelled(true);
        e.viewers().clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChatEnforce(AsyncChatEvent e) {
        Long until = swallow.get(e.getPlayer().getUniqueId());
        if (until == null) return;
        e.setCancelled(true);
        e.viewers().clear();
        if (until < System.currentTimeMillis()) swallow.remove(e.getPlayer().getUniqueId());
    }

    /** The modern event is always the last one fired for a message; forget the swallow mark after it. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChatDone(AsyncChatEvent e) {
        swallow.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        chatInput.remove(e.getPlayer().getUniqueId());
        swallow.remove(e.getPlayer().getUniqueId());
    }
}
