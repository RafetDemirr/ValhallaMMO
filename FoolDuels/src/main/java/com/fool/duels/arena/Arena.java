package com.fool.duels.arena;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

public final class Arena {
    private final String name;
    private boolean enabled = true;
    private final List<Location> spawnsA = new ArrayList<>();
    private final List<Location> spawnsB = new ArrayList<>();
    private Location pos1;
    private Location pos2;
    private transient boolean inUse;

    // cached bounds, recomputed when pos1/pos2 change
    private World world;
    private int minX, minY, minZ, maxX, maxY, maxZ;
    private boolean hasBounds;

    public Arena(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public boolean enabled() {
        return enabled;
    }

    public void enabled(boolean v) {
        enabled = v;
    }

    public boolean inUse() {
        return inUse;
    }

    public void inUse(boolean v) {
        inUse = v;
    }

    public List<Location> spawns(int team) {
        return team == 0 ? spawnsA : spawnsB;
    }

    public boolean ready() {
        return !spawnsA.isEmpty() && !spawnsB.isEmpty();
    }

    public boolean available() {
        return enabled && !inUse && ready();
    }

    /** Spawn for the i-th member of a team. If there are fewer spawns than players they are reused. */
    public Location spawn(int team, int index) {
        List<Location> list = spawns(team);
        return list.get(index % list.size()).clone();
    }

    public Location pos1() {
        return pos1;
    }

    public Location pos2() {
        return pos2;
    }

    public void pos1(Location l) {
        pos1 = l == null ? null : l.clone();
        recompute();
    }

    public void pos2(Location l) {
        pos2 = l == null ? null : l.clone();
        recompute();
    }

    public boolean hasBounds() {
        return hasBounds;
    }

    private void recompute() {
        hasBounds = pos1 != null && pos2 != null && pos1.getWorld() != null && pos1.getWorld().equals(pos2.getWorld());
        if (!hasBounds) return;
        world = pos1.getWorld();
        minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());
    }

    /** Cheap integer bounds check, safe to call from movement events. */
    public boolean contains(World w, int x, int y, int z) {
        return hasBounds && w == world && x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean contains(Location l) {
        return l != null && contains(l.getWorld(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }
}
