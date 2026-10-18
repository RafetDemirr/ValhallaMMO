package com.fool.duels.data;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * Crash-safe storage for everything that must survive a restart:
 * <ul>
 *   <li><b>pending/</b> - the pre-duel state of every player currently in (or just out of) a duel.
 *       Written the moment a duel starts, deleted once the player is restored. If the server crashes,
 *       the player is restored when they next join.</li>
 *   <li><b>backups/&lt;uuid&gt;/</b> - a rotating history of pre-duel states (like BattleArena's
 *       inventory backups) that admins can restore with /duelyonet geriyukle.</li>
 *   <li><b>escrow.yml</b> - money taken for bets/tournament fees that hasn't been paid out yet.
 *       Refunded automatically on startup after a crash.</li>
 * </ul>
 * Item serialization happens on the main thread; file writes go through a single IO thread so they
 * keep their order and never block the server tick.
 */
public final class StateStore {
    public record Pending(PlayerState state, boolean restoreInventory) {}

    public record Backup(Path path, long timestamp) {
        public String date() {
            return new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date(timestamp));
        }
    }

    private final JavaPlugin plugin;
    private final Path pendingDir;
    private final Path backupDir;
    private final File escrowFile;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "FoolDuels-IO");
        t.setDaemon(true);
        return t;
    });

    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Map<UUID, Double> escrow = new HashMap<>();
    private int maxBackups = 10;

    public StateStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.pendingDir = plugin.getDataFolder().toPath().resolve("pending");
        this.backupDir = plugin.getDataFolder().toPath().resolve("backups");
        this.escrowFile = new File(plugin.getDataFolder(), "escrow.yml");
    }

    public void setMaxBackups(int max) {
        this.maxBackups = Math.max(1, max);
    }

    /** Loads pending restores and escrow left over from a crash. */
    public Map<UUID, Double> load() {
        try {
            Files.createDirectories(pendingDir);
            Files.createDirectories(backupDir);
            try (Stream<Path> files = Files.list(pendingDir)) {
                for (Path file : files.toList()) {
                    String name = file.getFileName().toString();
                    if (!name.endsWith(".dat")) continue;
                    try {
                        UUID id = UUID.fromString(name.substring(0, name.length() - 4));
                        byte[] data = Files.readAllBytes(file);
                        boolean restoreInv = data[0] == 1;
                        byte[] state = new byte[data.length - 1];
                        System.arraycopy(data, 1, state, 0, state.length);
                        pending.put(id, new Pending(PlayerState.fromBytes(state), restoreInv));
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.SEVERE, "Bekleyen durum dosyası okunamadı: " + file, e);
                    }
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Veri klasörleri oluşturulamadı", e);
        }
        if (!pending.isEmpty()) {
            plugin.getLogger().warning(pending.size() + " oyuncunun düello öncesi durumu bulundu, oyuna girdiklerinde geri yüklenecek.");
        }

        Map<UUID, Double> leftover = new HashMap<>();
        if (escrowFile.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(escrowFile);
            for (String key : y.getKeys(false)) {
                try {
                    double amount = y.getDouble(key);
                    if (amount > 0) leftover.put(UUID.fromString(key), amount);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return leftover;
    }

    // ------------------------------------------------------------------ pending restores

    /** Stores the pre-duel state on disk (and as a backup) before anything about the player is changed. */
    public void put(UUID id, PlayerState state, boolean restoreInventory) {
        pending.put(id, new Pending(state, restoreInventory));
        byte[] stateBytes;
        try {
            stateBytes = state.toBytes();
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Durum kaydedilemedi: " + id, e);
            return;
        }
        byte[] data = new byte[stateBytes.length + 1];
        data[0] = (byte) (restoreInventory ? 1 : 0);
        System.arraycopy(stateBytes, 0, data, 1, stateBytes.length);
        io.execute(() -> {
            write(pendingDir.resolve(id + ".dat"), data);
            writeBackup(id, state.timestamp, stateBytes);
        });
    }

    /** Writes a backup only (used before admin actions that overwrite an inventory). */
    public void backup(UUID id, PlayerState state) {
        byte[] stateBytes;
        try {
            stateBytes = state.toBytes();
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Yedek alınamadı: " + id, e);
            return;
        }
        io.execute(() -> writeBackup(id, state.timestamp, stateBytes));
    }

    public Pending get(UUID id) {
        return pending.get(id);
    }

    public boolean has(UUID id) {
        return pending.containsKey(id);
    }

    public Pending take(UUID id) {
        Pending p = pending.remove(id);
        if (p != null) io.execute(() -> delete(pendingDir.resolve(id + ".dat")));
        return p;
    }

    // ------------------------------------------------------------------ backups

    private void writeBackup(UUID id, long timestamp, byte[] stateBytes) {
        Path dir = backupDir.resolve(id.toString());
        write(dir.resolve(timestamp + ".dat"), stateBytes);
        List<Backup> all = listBackups(id);
        for (int i = maxBackups; i < all.size(); i++) delete(all.get(i).path());
    }

    /** Newest first. Only reads file names, the contents are loaded on demand. */
    public List<Backup> listBackups(UUID id) {
        Path dir = backupDir.resolve(id.toString());
        if (!Files.isDirectory(dir)) return List.of();
        List<Backup> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : files.toList()) {
                String n = p.getFileName().toString();
                if (!n.endsWith(".dat")) continue;
                try {
                    out.add(new Backup(p, Long.parseLong(n.substring(0, n.length() - 4))));
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Yedekler listelenemedi: " + id, e);
        }
        out.sort(Collections.reverseOrder((a, b) -> Long.compare(a.timestamp(), b.timestamp())));
        return out;
    }

    public PlayerState loadBackup(Backup backup) throws IOException {
        return PlayerState.fromBytes(Files.readAllBytes(backup.path()));
    }

    // ------------------------------------------------------------------ escrow

    public void addEscrow(UUID id, double amount) {
        if (amount <= 0) return;
        escrow.merge(id, amount, Double::sum);
        saveEscrow();
    }

    public void removeEscrow(UUID id, double amount) {
        if (amount <= 0) return;
        Double left = escrow.computeIfPresent(id, (k, v) -> v - amount);
        if (left != null && left <= 0.0001) escrow.remove(id);
        saveEscrow();
    }

    public void clearEscrowFile() {
        escrow.clear();
        saveEscrow();
    }

    private void saveEscrow() {
        Map<UUID, Double> copy = new HashMap<>(escrow);
        io.execute(() -> {
            YamlConfiguration y = new YamlConfiguration();
            copy.forEach((k, v) -> y.set(k.toString(), v));
            try {
                y.save(escrowFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "escrow.yml kaydedilemedi", e);
            }
        });
    }

    // ------------------------------------------------------------------ io

    public void shutdown() {
        io.shutdown();
        try {
            if (!io.awaitTermination(10, TimeUnit.SECONDS)) plugin.getLogger().warning("Dosya yazma işlemleri zamanında bitmedi.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void write(Path path, byte[] data) {
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.write(tmp, data);
            Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Dosya yazılamadı: " + path, e);
        }
    }

    private void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Dosya silinemedi: " + path, e);
        }
    }
}
