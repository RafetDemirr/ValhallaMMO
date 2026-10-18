package com.fool.duels.data;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything about a player that a duel can change, captured right before the duel.
 * Modeled after BattleArena's PlayerStorage (inventory, game mode, health, xp, flight, effects, location)
 * and stored on disk with Paper's lossless ItemStack byte serialization, like BattleArena's InventoryBackup.
 */
public final class PlayerState {
    private static final int FORMAT = 1;

    public final long timestamp;
    public final ItemStack[] inventory;
    public final Location location;
    public final GameMode gameMode;
    public final double health;
    public final int food;
    public final float saturation;
    public final float exhaustion;
    public final int totalExp;
    public final float exp;
    public final int level;
    public final boolean allowFlight;
    public final boolean flying;
    public final float walkSpeed;
    public final float flySpeed;
    public final List<PotionEffect> effects;

    private PlayerState(long timestamp, ItemStack[] inventory, Location location, GameMode gameMode, double health, int food,
                        float saturation, float exhaustion, int totalExp, float exp, int level, boolean allowFlight,
                        boolean flying, float walkSpeed, float flySpeed, List<PotionEffect> effects) {
        this.timestamp = timestamp;
        this.inventory = inventory;
        this.location = location;
        this.gameMode = gameMode;
        this.health = health;
        this.food = food;
        this.saturation = saturation;
        this.exhaustion = exhaustion;
        this.totalExp = totalExp;
        this.exp = exp;
        this.level = level;
        this.allowFlight = allowFlight;
        this.flying = flying;
        this.walkSpeed = walkSpeed;
        this.flySpeed = flySpeed;
        this.effects = effects;
    }

    public static PlayerState capture(Player p) {
        ItemStack[] contents = p.getInventory().getContents();
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) copy[i] = contents[i] == null ? null : contents[i].clone();
        return new PlayerState(System.currentTimeMillis(), copy, p.getLocation().clone(), p.getGameMode(), p.getHealth(),
                p.getFoodLevel(), p.getSaturation(), p.getExhaustion(), p.getTotalExperience(), p.getExp(), p.getLevel(),
                p.getAllowFlight(), p.isFlying(), p.getWalkSpeed(), p.getFlySpeed(), new ArrayList<>(p.getActivePotionEffects()));
    }

    /**
     * Restores the player. Location is handled by the caller (teleport or respawn location).
     *
     * @param restoreInventory false for own-inventory duels: the player keeps what they have now,
     *                         so food, potions and arrows used in the duel are really gone.
     */
    @SuppressWarnings("deprecation")
    public void apply(Player p, boolean restoreInventory) {
        p.closeInventory();
        if (restoreInventory) {
            p.setItemOnCursor(null);
            p.getInventory().clear();
            p.getInventory().setContents(inventory);
        }
        p.setGameMode(gameMode);
        p.setTotalExperience(totalExp);
        p.setLevel(level);
        p.setExp(exp);
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        p.addPotionEffects(effects);
        p.setWalkSpeed(walkSpeed);
        p.setFlySpeed(flySpeed);
        p.setAllowFlight(allowFlight);
        if (allowFlight) p.setFlying(flying);
        p.setFireTicks(0);
        p.setFallDistance(0);
        // Health last: equipment/effects can change max health (same order as BattleArena).
        p.setHealth(Math.max(0.5, Math.min(health, p.getMaxHealth())));
        p.setFoodLevel(food);
        p.setSaturation(saturation);
        p.setExhaustion(exhaustion);
        p.updateInventory();
    }

    // ------------------------------------------------------------------ serialization

    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(FORMAT);
            out.writeLong(timestamp);
            out.writeUTF(location.getWorld() == null ? "" : location.getWorld().getName());
            out.writeDouble(location.getX());
            out.writeDouble(location.getY());
            out.writeDouble(location.getZ());
            out.writeFloat(location.getYaw());
            out.writeFloat(location.getPitch());
            out.writeUTF(gameMode.name());
            out.writeDouble(health);
            out.writeInt(food);
            out.writeFloat(saturation);
            out.writeFloat(exhaustion);
            out.writeInt(totalExp);
            out.writeFloat(exp);
            out.writeInt(level);
            out.writeBoolean(allowFlight);
            out.writeBoolean(flying);
            out.writeFloat(walkSpeed);
            out.writeFloat(flySpeed);
            out.writeInt(effects.size());
            for (PotionEffect e : effects) {
                out.writeUTF(e.getType().getKey().toString());
                out.writeInt(e.getDuration());
                out.writeInt(e.getAmplifier());
                out.writeBoolean(e.isAmbient());
                out.writeBoolean(e.hasParticles());
                out.writeBoolean(e.hasIcon());
            }
            writeItems(out, inventory);
        }
        return bytes.toByteArray();
    }

    public static PlayerState fromBytes(byte[] data) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            int format = in.readInt();
            if (format != FORMAT) throw new IOException("Unknown state format " + format);
            long timestamp = in.readLong();
            String worldName = in.readUTF();
            World world = worldName.isEmpty() ? null : Bukkit.getWorld(worldName);
            if (world == null) world = Bukkit.getWorlds().get(0);
            Location loc = new Location(world, in.readDouble(), in.readDouble(), in.readDouble(), in.readFloat(), in.readFloat());
            GameMode gm;
            try {
                gm = GameMode.valueOf(in.readUTF());
            } catch (IllegalArgumentException e) {
                gm = GameMode.SURVIVAL;
            }
            double health = in.readDouble();
            int food = in.readInt();
            float saturation = in.readFloat();
            float exhaustion = in.readFloat();
            int totalExp = in.readInt();
            float exp = in.readFloat();
            int level = in.readInt();
            boolean allowFlight = in.readBoolean();
            boolean flying = in.readBoolean();
            float walkSpeed = in.readFloat();
            float flySpeed = in.readFloat();
            int effectCount = in.readInt();
            List<PotionEffect> effects = new ArrayList<>(effectCount);
            for (int i = 0; i < effectCount; i++) {
                String key = in.readUTF();
                int duration = in.readInt(), amplifier = in.readInt();
                boolean ambient = in.readBoolean(), particles = in.readBoolean(), icon = in.readBoolean();
                NamespacedKey nk = NamespacedKey.fromString(key);
                PotionEffectType type = nk == null ? null : Registry.EFFECT.get(nk);
                if (type != null) effects.add(new PotionEffect(type, duration, amplifier, ambient, particles, icon));
            }
            ItemStack[] items = readItems(in);
            return new PlayerState(timestamp, items, loc, gm, health, food, saturation, exhaustion, totalExp, exp, level,
                    allowFlight, flying, walkSpeed, flySpeed, effects);
        }
    }

    static void writeItems(DataOutputStream out, ItemStack[] items) throws IOException {
        out.writeInt(items.length);
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
                out.writeBoolean(false);
                continue;
            }
            byte[] b = item.serializeAsBytes();
            out.writeBoolean(true);
            out.writeInt(b.length);
            out.write(b);
        }
    }

    static ItemStack[] readItems(DataInputStream in) throws IOException {
        int length = in.readInt();
        ItemStack[] items = new ItemStack[length];
        for (int i = 0; i < length; i++) {
            if (!in.readBoolean()) continue;
            byte[] b = new byte[in.readInt()];
            in.readFully(b);
            items[i] = ItemStack.deserializeBytes(b);
        }
        return items;
    }
}
