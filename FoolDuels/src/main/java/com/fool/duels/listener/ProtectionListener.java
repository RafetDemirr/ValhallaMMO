package com.fool.duels.listener;

import com.fool.duels.FoolDuels;
import com.fool.duels.arena.Arena;
import com.fool.duels.match.Match;
import com.fool.duels.util.Msg;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.PlayerInventory;

/**
 * Arena protection (blocks, explosions, outsiders walking in, duelists walking out)
 * and kit item protection (kit items found outside a duel are deleted, like Duels' KitItemListener).
 */
public final class ProtectionListener implements Listener {
    private final FoolDuels plugin;

    public ProtectionListener(FoolDuels plugin) {
        this.plugin = plugin;
    }

    private boolean bypass(Player p) {
        return p.hasPermission("foolduels.bypass");
    }

    // ------------------------------------------------------------------ blocks

    private void checkBlock(Player p, Block b, Cancellable e) {
        if (!plugin.getConfig().getBoolean("protection.block-changes", true) || bypass(p)) return;
        if (plugin.arenas().at(b.getWorld(), b.getX(), b.getY(), b.getZ()) == null) return;
        e.setCancelled(true);
        Msg.send(p, "arena-protected");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        checkBlock(e.getPlayer(), e.getBlock(), e);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (kitItemOutsideDuel(e.getPlayer(), e.getItemInHand())) {
            e.setCancelled(true);
            e.getPlayer().getInventory().remove(e.getItemInHand());
            return;
        }
        checkBlock(e.getPlayer(), e.getBlock(), e);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        checkBlock(e.getPlayer(), e.getBlock(), e);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        checkBlock(e.getPlayer(), e.getBlock(), e);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (plugin.getConfig().getBoolean("protection.block-changes", true))
            e.blockList().removeIf(b -> plugin.arenas().at(b.getWorld(), b.getX(), b.getY(), b.getZ()) != null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (plugin.getConfig().getBoolean("protection.block-changes", true))
            e.blockList().removeIf(b -> plugin.arenas().at(b.getWorld(), b.getX(), b.getY(), b.getZ()) != null);
    }

    // ------------------------------------------------------------------ movement

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location from = e.getFrom(), to = e.getTo();
        // Only when the block changes - this keeps the handler practically free.
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) return;
        if (plugin.matches().isEmpty()) return;
        Player p = e.getPlayer();
        Match m = plugin.matches().get(p.getUniqueId());
        if (m != null) {
            Arena a = m.arena();
            if (plugin.getConfig().getBoolean("protection.keep-players-inside", true) && a.hasBounds() && !a.contains(to)) {
                int team = Math.max(0, m.teamOf(p.getUniqueId()));
                e.setTo(a.spawn(team, 0));
            }
            return;
        }
        if (!plugin.getConfig().getBoolean("protection.block-outsiders", true) || bypass(p)) return;
        Arena a = plugin.arenas().at(to.getWorld(), to.getBlockX(), to.getBlockY(), to.getBlockZ());
        if (a != null && a.inUse() && !a.contains(from)) {
            e.setTo(from);
            Msg.send(p, "arena-protected");
        }
    }

    // ------------------------------------------------------------------ kit items outside duels

    private boolean kitItemOutsideDuel(Player p, org.bukkit.inventory.ItemStack item) {
        return !plugin.matches().inMatch(p.getUniqueId()) && !bypass(p) && plugin.kits().isKitItem(item);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onKitClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !(e.getClickedInventory() instanceof PlayerInventory)) return;
        if (!kitItemOutsideDuel(p, e.getCurrentItem())) return;
        e.setCancelled(true);
        e.setCurrentItem(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onKitInteract(PlayerInteractEvent e) {
        if (e.getItem() == null || !kitItemOutsideDuel(e.getPlayer(), e.getItem())) return;
        e.setCancelled(true);
        e.getPlayer().getInventory().remove(e.getItem());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onKitPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Item item = e.getItem();
        if (!kitItemOutsideDuel(p, item.getItemStack())) return;
        e.setCancelled(true);
        item.remove();
    }
}
