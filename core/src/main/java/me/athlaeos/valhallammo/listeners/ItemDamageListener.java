package me.athlaeos.valhallammo.listeners;

import me.athlaeos.valhallammo.item.CustomDurabilityManager;
import me.athlaeos.valhallammo.item.CustomFlag;
import me.athlaeos.valhallammo.item.ItemBuilder;
import me.athlaeos.valhallammo.item.ItemSkillRequirements;
import me.athlaeos.valhallammo.playerstats.AccumulativeStatManager;
import me.athlaeos.valhallammo.utility.ItemUtils;
import me.athlaeos.valhallammo.utility.Utils;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

public class ItemDamageListener implements Listener {
    @EventHandler(priority= EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDurabilityChange(PlayerItemDamageEvent e){
        if (e.getDamage() == 0) return;
        double durabilityBonus = AccumulativeStatManager.getCachedStats("DURABILITY_BONUS", e.getPlayer(), 10000, true);
        // the durabilityBonus represents a % chance to not spend durability, effectively raising it. If this amount is negative though it's treated as a damage multiplier

        ItemStack itemStack = e.getItem();
        ItemBuilder builder = ItemUtils.isEmpty(itemStack) ? null : new ItemBuilder(itemStack);
        if (builder == null) return;
        durabilityBonus += ItemSkillRequirements.getPenalty(e.getPlayer(), builder, "durability");

        // examples:
        // +0.2 leads to 1/1.2 = ~0.83 and so damage is multiplied by that to produce roughly +20% durability
        // -11 leads to 11 - 1) = 10, 10x durability taken
        double multiplier = durabilityBonus < 0 ? -durabilityBonus - 1 : 1 / (durabilityBonus + 1);
        e.setDamage(Utils.randomAverage(e.getDamage() * Math.max(0, multiplier)));

        if (CustomDurabilityManager.hasCustomDurability(builder.getMeta())){
            CustomDurabilityManager.syncExternalRepairs(builder); // otherwise repairs made outside the plugin are undone here
            CustomDurabilityManager.damage(builder, e.getDamage());
            if (CustomDurabilityManager.getDurability(builder, false) > 0) {
                e.setCancelled(true);
                ItemMeta meta = ItemUtils.getItemMeta(builder.get());
                if (meta != null) ItemUtils.setMetaNoClone(itemStack, meta);
            } else if (builder.getMeta() instanceof Damageable d && itemStack.getType().getMaxDurability() > 0) {
                // with native durability the item's damage has already been set to its max damage
                if (!CustomDurabilityManager.usesNativeDurability()) d.setDamage(itemStack.getType().getMaxDurability());
                ItemMeta meta = ItemUtils.getItemMeta(builder.get());
                if (meta != null) ItemUtils.setMetaNoClone(itemStack, meta);
            }
        }
    }

    @EventHandler(priority=EventPriority.NORMAL, ignoreCancelled = true)
    public void onItemMend(PlayerItemMendEvent e){
        if (ItemUtils.isEmpty(e.getItem())) return;
        ItemBuilder item = new ItemBuilder(e.getItem());
        if (CustomFlag.hasFlag(item.getMeta(), CustomFlag.UNMENDABLE)){
            e.setCancelled(true);
            return;
        }
        if (CustomDurabilityManager.hasCustomDurability(item.getMeta())){
            if (CustomDurabilityManager.usesNativeDurability()){
                // the repair amount is already in the item's own durability, so vanilla is left to repair the item (and
                // spend the experience for it). only the durability displayed on the item needs to be kept up to date
                CustomDurabilityManager.updateLore(item, CustomDurabilityManager.getDurability(item, false) + e.getRepairAmount());
                ItemMeta meta = ItemUtils.getItemMeta(item.get());
                if (meta != null) ItemUtils.setMetaNoClone(e.getItem(), meta);
                return;
            }
            CustomDurabilityManager.syncExternalRepairs(item);
            // the repair amount is in vanilla durability points (and capped at the item's vanilla damage, which is only a
            // scaled down representation of its custom durability), so it's scaled up to custom durability points to make
            // mending restore the same fraction of an item's durability as it would on a vanilla item
            int vanillaMax = e.getItem().getType().getMaxDurability();
            int customMax = CustomDurabilityManager.getDurability(item, true);
            int repair = e.getRepairAmount();
            if (vanillaMax > 0 && customMax > 0 && repair > 0) repair = Math.max(1, (int) Math.round(repair * (customMax / (double) vanillaMax)));
            CustomDurabilityManager.damage(item, -repair);
            e.setCancelled(true);
            ItemMeta meta = ItemUtils.getItemMeta(item.get());
            if (meta == null) return;
            ItemUtils.setMetaNoClone(e.getItem(), meta);
        }
    }
}