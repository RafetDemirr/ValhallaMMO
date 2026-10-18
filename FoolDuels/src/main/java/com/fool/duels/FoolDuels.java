package com.fool.duels;

import com.fool.duels.arena.ArenaManager;
import com.fool.duels.command.AdminCommand;
import com.fool.duels.command.DuelCommand;
import com.fool.duels.command.PartyCommand;
import com.fool.duels.command.TournamentCommand;
import com.fool.duels.data.RewardStore;
import com.fool.duels.data.StateStore;
import com.fool.duels.data.StatsManager;
import com.fool.duels.duel.RequestManager;
import com.fool.duels.gui.Menus;
import com.fool.duels.hook.CombatLogXHook;
import com.fool.duels.hook.EconomyHook;
import com.fool.duels.kit.KitManager;
import com.fool.duels.listener.MatchListener;
import com.fool.duels.lobby.LobbyManager;
import com.fool.duels.queue.QueueManager;
import com.fool.duels.listener.ProtectionListener;
import com.fool.duels.match.MatchManager;
import com.fool.duels.party.PartyManager;
import com.fool.duels.tournament.TournamentManager;
import com.fool.duels.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;

public final class FoolDuels extends JavaPlugin {
    private EconomyHook economy;
    private CombatLogXHook combat;
    private StateStore store;
    private StatsManager stats;
    private ArenaManager arenas;
    private KitManager kits;
    private PartyManager parties;
    private MatchManager matches;
    private RequestManager requests;
    private TournamentManager tournament;
    private Menus menus;
    private LobbyManager lobby;
    private QueueManager queues;
    private RewardStore rewards;
    private MatchListener matchListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        upgradeConfig();
        Msg.load(getConfig());

        economy = new EconomyHook();
        economy.setup(getLogger());

        store = new StateStore(this);
        store.setMaxBackups(getConfig().getInt("backups.max-per-player", 10));
        refundLeftoverEscrow(store.load());

        stats = new StatsManager(this);
        stats.configure(getConfig().getInt("elo.default", 1000), getConfig().getInt("elo.k-factor", 32));
        stats.load();

        arenas = new ArenaManager(this);
        arenas.load();
        kits = new KitManager(this);
        kits.load();
        parties = new PartyManager();
        lobby = new LobbyManager(this);
        lobby.load();
        rewards = new RewardStore(this);
        rewards.load();
        matches = new MatchManager(this);
        requests = new RequestManager(this);
        queues = new QueueManager(this);
        tournament = new TournamentManager(this);
        menus = new Menus(this);

        matchListener = new MatchListener(this);
        Bukkit.getPluginManager().registerEvents(matchListener, this);
        Bukkit.getPluginManager().registerEvents(new ProtectionListener(this), this);
        Bukkit.getPluginManager().registerEvents(menus, this);

        // CombatLogX loads its API classes on enable; hook one tick later so it is ready.
        Bukkit.getScheduler().runTask(this, () -> {
            combat = new CombatLogXHook();
            combat.setup(this, matches::isProtected, getLogger());
        });
        combat = new CombatLogXHook();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new com.fool.duels.hook.PapiExpansion(this).register();
            getLogger().info("PlaceholderAPI bağlandı: %foolduels_...%");
        }

        register("duel", new DuelCommand(this));
        register("parti", new PartyCommand(this));
        register("turnuva", new TournamentCommand(this));
        register("duelyonet", new AdminCommand(this));

        getLogger().info("FoolDuels aktif: " + arenas.all().size() + " arena, " + kits.all().size() + " kit.");
    }

    @Override
    public void onDisable() {
        if (tournament != null && tournament.active()) tournament.cancel(null);
        if (matches != null) matches.cancelAll();
        if (stats != null) stats.saveNow();
        if (arenas != null) arenas.save();
        if (store != null) store.shutdown();
    }

    /** Money held for bets/fees when the server crashed goes back to its owners. */
    private void refundLeftoverEscrow(Map<UUID, Double> leftover) {
        if (leftover.isEmpty()) return;
        if (!economy.enabled()) {
            getLogger().severe("escrow.yml içinde iade bekleyen para var ama ekonomi yok! Dosyayı silmeyin: " + leftover);
            return;
        }
        leftover.forEach((id, amount) -> {
            economy.deposit(Bukkit.getOfflinePlayer(id), amount);
            getLogger().warning("Çökme sonrası iade: " + id + " -> " + amount);
        });
        store.clearEscrowFile();
    }

    /**
     * Adds settings and messages from newer versions to an existing config.yml, keeping the
     * owner's values and comments. Presets and tax permissions are left alone once they exist,
     * so deleted entries don't come back.
     */
    private void upgradeConfig() {
        var cfg = getConfig();
        var defaults = cfg.getDefaults();
        if (defaults == null) return;
        boolean changed = false;
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key) || cfg.contains(key, true)) continue;
            if (ownedSection(cfg, key, "tournament.presets") || ownedSection(cfg, key, "bet.tax-permissions")) continue;
            cfg.set(key, defaults.get(key));
            cfg.setComments(key, defaults.getComments(key));
            changed = true;
        }
        if (changed) saveConfig();
    }

    private static boolean ownedSection(org.bukkit.configuration.Configuration cfg, String key, String section) {
        return key.startsWith(section + ".") && cfg.contains(section, true);
    }

    public void reloadAll() {
        reloadConfig();
        upgradeConfig();
        Msg.load(getConfig());
        store.setMaxBackups(getConfig().getInt("backups.max-per-player", 10));
        stats.configure(getConfig().getInt("elo.default", 1000), getConfig().getInt("elo.k-factor", 32));
        matchListener.reload();
        if (matches.isEmpty()) {
            arenas.load();
            kits.load();
        }
    }

    private void register(String name, TabExecutor executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) return;
        cmd.setExecutor(executor);
        cmd.setTabCompleter(executor);
    }

    public EconomyHook economy() { return economy; }
    public CombatLogXHook combat() { return combat; }
    public StateStore store() { return store; }
    public StatsManager stats() { return stats; }
    public ArenaManager arenas() { return arenas; }
    public KitManager kits() { return kits; }
    public PartyManager parties() { return parties; }
    public MatchManager matches() { return matches; }
    public RequestManager requests() { return requests; }
    public TournamentManager tournament() { return tournament; }
    public Menus menus() { return menus; }
    public LobbyManager lobby() { return lobby; }
    public QueueManager queues() { return queues; }
    public RewardStore rewards() { return rewards; }
}
