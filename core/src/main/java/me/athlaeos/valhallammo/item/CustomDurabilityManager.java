package me.athlaeos.valhallammo.item;

import me.athlaeos.valhallammo.ValhallaMMO;
import me.athlaeos.valhallammo.dom.Scaling;
import me.athlaeos.valhallammo.item.item_attributes.AttributeWrapper;
import me.athlaeos.valhallammo.localization.TranslationManager;
import me.athlaeos.valhallammo.utility.ItemUtils;
import org.bukkit.EntityEffect;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Method;

public class CustomDurabilityManager {
    private static final NamespacedKey DURABILITY = ValhallaMMO.key("durability");
    private static final NamespacedKey MAX_DURABILITY = ValhallaMMO.key("max_durability");

    /**
     * Damages an item's custom durability by the given amount. This method does not let you know if the item should break
     * and doesn't account for non-custom tools. It's recommended to use {@link ItemUtils#damageItem(Player, ItemStack, int, EntityEffect, boolean)}
     * to damage items as this also calls the appropriate events.
     * @param item the item to damage
     * @param damage the amount to damage the item with
     */
    public static void damage(ItemBuilder item, int damage){
        Material baseType = item.getItem().getType();
        if (baseType.getMaxDurability() > 0 && item.getMeta().getPersistentDataContainer().has(DURABILITY, PersistentDataType.INTEGER)){
            int durability = item.getMeta().getPersistentDataContainer().getOrDefault(DURABILITY, PersistentDataType.INTEGER, 0);
            int maxDurability = item.getMeta().getPersistentDataContainer().getOrDefault(MAX_DURABILITY, PersistentDataType.INTEGER, 0);
            if (durability > maxDurability) durability = maxDurability;
            durability = Math.max(0, Math.min(maxDurability, durability - damage));
            item.intTag(DURABILITY, durability);
            updateLore(item);
        }
    }

    /**
     * Sets the item's custom current and max durability. If either durability or maxDurability are <0, remove the attributes.
     * The item's lore will be updated to reflect the durability change.
     * @param item the item to set the durability
     * @param durability the current durability to set to the item
     * @param maxDurability the max durability to set to the item
     */
    public static void setDurability(ItemBuilder item, int durability, int maxDurability){
        Material baseType = item.getItem().getType();
        if (baseType.getMaxDurability() > 0){
            if (durability < 0 || maxDurability < 0){
                item.getMeta().getPersistentDataContainer().remove(DURABILITY);
                item.getMeta().getPersistentDataContainer().remove(MAX_DURABILITY);
            } else {
                item.intTag(DURABILITY, Math.min(maxDurability, durability));
                item.intTag(MAX_DURABILITY, Math.max(maxDurability, durability));
            }
            updateLore(item);
        }
    }

    /**
     * Sets an item's durability to a fraction of its max durability. Works for both vanilla and custom durability.
     * No matter the fraction, the new durability will always be at least 1.
     * @param item the item to set its new current durability
     * @param fraction the fraction of max durability the item should be set to
     */
    public static void setDurability(ItemBuilder item, double fraction){
        Material baseType = item.getItem().getType();
        fraction = Math.max(0, Math.min(1, fraction));
        boolean hasCustom = item.getMeta().getPersistentDataContainer().has(DURABILITY, PersistentDataType.INTEGER);
        int maxDurability = hasCustom ? getDurability(item, true) : baseType.getMaxDurability();
        int newDurability = Math.max(1, (int) Math.round(fraction * maxDurability));
        if (baseType.getMaxDurability() > 0 && item.getMeta() instanceof Damageable d){
            if (hasCustom){
                setDurability(item, newDurability, maxDurability);
            } else {
                d.setDamage(maxDurability - newDurability);
            }
        }
    }

    /**
     * Sets an item's durability to an amount. Works for both vanilla and custom durability.
     * @param item the item to set its new current durability
     * @param amount the durability the item should be set to
     */
    public static void setDurability(ItemBuilder item, int amount){
        Material baseType = item.getItem().getType();
        boolean hasCustom = item.getMeta().getPersistentDataContainer().has(DURABILITY, PersistentDataType.INTEGER);
        int maxDurability = hasCustom ? getDurability(item, true) : baseType.getMaxDurability();
        if (baseType.getMaxDurability() > 0 && item.getMeta() instanceof Damageable d){
            if (hasCustom){
                setDurability(item, amount, maxDurability);
            } else {
                d.setDamage(Math.max(0, maxDurability - amount));
            }
        }
    }

    private static final Method HAS_MAX_DAMAGE = findDamageableMethod("hasMaxDamage");
    private static final Method GET_MAX_DAMAGE = findDamageableMethod("getMaxDamage");

    /**
     * Custom durability is stored separately from the item's vanilla damage value, which only mirrors it. Anything that
     * repairs an item through vanilla means (repair commands, virtual anvils, grindstones, mending handled by another
     * plugin, etc.) only lowers the vanilla damage, so the next time the item got damaged it would snap back to its
     * old custom durability.<br>
     * If the item's vanilla damage is lower than its custom durability says it should be, it was repaired externally and
     * its custom durability is raised to match. This never lowers an item's custom durability.
     * @param item the item to sync
     */
    public static void syncExternalRepairs(ItemBuilder item){
        int vanillaMax = item.getItem().getType().getMaxDurability();
        if (vanillaMax <= 0 || !(item.getMeta() instanceof Damageable d)) return;
        if (!item.getMeta().getPersistentDataContainer().has(DURABILITY, PersistentDataType.INTEGER)) return;
        if (hasDifferentMaxDamage(d, vanillaMax)) return; // vanilla damage isn't on the scale updateLore() writes it on
        int maxCustom = getDurability(item, true);
        int custom = getDurability(item, false);
        if (maxCustom <= 0) return;
        // same formula as updateLore(), which is what the vanilla damage would be if nothing else had touched it
        double fraction = Math.max(0, Math.min(1, (double) custom / (double) maxCustom));
        int expectedDamage = vanillaMax - (int) Math.ceil(fraction * vanillaMax);
        if (d.getDamage() >= expectedDamage) return;
        int repaired = d.getDamage() <= 0 ? maxCustom : (int) Math.round(((vanillaMax - d.getDamage()) / (double) vanillaMax) * maxCustom);
        if (repaired <= custom) return;
        setDurability(item, repaired, maxCustom);
    }

    private static Method findDamageableMethod(String name){
        try {
            return Damageable.class.getMethod(name);
        } catch (NoSuchMethodException ignored){
            return null;
        }
    }

    private static boolean hasDifferentMaxDamage(Damageable d, int vanillaMax){
        if (HAS_MAX_DAMAGE == null || GET_MAX_DAMAGE == null) return false; // versions before item components
        try {
            return (boolean) HAS_MAX_DAMAGE.invoke(d) && (int) GET_MAX_DAMAGE.invoke(d) != vanillaMax;
        } catch (ReflectiveOperationException | RuntimeException ignored){
            return true; // can't tell, so don't touch the item
        }
    }

    public static double getDurabilityFraction(ItemBuilder item){
        Material baseType = item.getItem().getType();
        boolean hasCustom = item.getMeta().getPersistentDataContainer().has(DURABILITY, PersistentDataType.INTEGER);
        int maxDurability = hasCustom ? getDurability(item, true) : baseType.getMaxDurability();
        if (baseType.getMaxDurability() > 0 && item.getMeta() instanceof Damageable d){
            if (hasCustom){
                return (double) getDurability(item, false) / (double) maxDurability;
            } else {
                return (double) (maxDurability - d.getDamage()) / maxDurability;
            }
        }
        return 0;
    }

    /**
     * Returns the item's current or max durability. Works for both vanilla and custom tools.
     * @param item the item to get its durability from
     * @param max whether you want to have the max durability (true) or current durability (false)
     * @return the (max)durability of the item
     */
    public static int getDurability(ItemBuilder item, boolean max){
        Material baseType = item.getItem().getType();
        if (baseType.getMaxDurability() > 0 && item.getMeta() instanceof Damageable d){
            boolean hasCustom = item.getMeta().getPersistentDataContainer().has(DURABILITY, PersistentDataType.INTEGER);
            if (hasCustom) {
                return Math.max(0, item.getMeta().getPersistentDataContainer().getOrDefault(max ? MAX_DURABILITY : DURABILITY, PersistentDataType.INTEGER, 0));
            } else {
                return max ? baseType.getMaxDurability() : baseType.getMaxDurability() - d.getDamage();
            }
        }
        return 0;
    }

    /**
     * @param meta the item meta to check if it has custom durability
     * @return true if it has custom durability, false otherwise
     */
    public static boolean hasCustomDurability(ItemMeta meta){
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(DURABILITY, PersistentDataType.INTEGER) ||
                meta.getPersistentDataContainer().has(MAX_DURABILITY, PersistentDataType.INTEGER);
    }

    /**
     * Updates the item's lore to reflect custom durability changes, or removes it if the item has no custom durability.
     * Lore is also removed if the item has the HIDE_DURABILITY {@link CustomFlag}
     * @param item the item to update
     */
    public static void updateLore(ItemBuilder item){
        Material baseType = item.getItem().getType();
        String translation = TranslationManager.getTranslation("translation_durability");
        if (baseType.getMaxDurability() > 0 && item.getMeta() instanceof Damageable d){
            int maxVanillaDurability = baseType.getMaxDurability();
            int maxCustomDurability = getDurability(item, true);
            int customDurability = getDurability(item, false);
            double fraction = Math.max(0, Math.min(1, (double) customDurability / (double) maxCustomDurability));
            int newVanillaDurability = maxVanillaDurability - (int) Math.ceil(fraction * maxVanillaDurability);
            d.setDamage(newVanillaDurability);

            if (CustomFlag.hasFlag(item.getMeta(), CustomFlag.HIDE_DURABILITY) || !hasCustomDurability(item.getMeta())){
                ItemUtils.removeIfLoreContains(item, translation);
            } else {
                ItemUtils.replaceOrAddLore(item,
                        translation,
                        String.format("%s %d/%d", translation, customDurability, maxCustomDurability));
            }
        } else {
            ItemUtils.removeIfLoreContains(item, translation);
        }
    }

    /**
     * Scales an item's durability values given a quality value. This method is coded differently from {@link SmithingItemPropertyManager#applyAttributeScaling(ItemBuilder, Scaling, int, String, double)}
     * as that method requires items to have a default custom durability attribute, which vanilla items are not configured to have.
     * @param item the item to scale its durability based on quality
     * @param quality the quality to scale the item with
     * @param minimumFraction the minimum fraction of the item's max durability the item should be left with after calculations
     */
    public static void applyDurabilityScaling(ItemBuilder item, Scaling scaling, int quality, double minimumFraction){
        Material baseMaterial = item.getItem().getType();
        if (baseMaterial.getMaxDurability() > 0 && item.getMeta() instanceof Damageable d){
            AttributeWrapper durabilityWrapper = ItemAttributesRegistry.getAttribute(item.getMeta(), "CUSTOM_MAX_DURABILITY", true);
            int defaultDurability = durabilityWrapper == null ? getDurability(item, true) : (int) durabilityWrapper.getValue();
            int minimum = (int) Math.round(Math.max(1, minimumFraction * defaultDurability));
            int newMaxDurability = Math.max(minimum, (int) Math.round(scaling.evaluate(scaling.getExpression().replace("%rating%", String.valueOf(quality)), defaultDurability)));
            double fraction;
            if (hasCustomDurability(item.getMeta())){
                fraction = (double) getDurability(item, false) / (double) getDurability(item, true);
            } else {
                fraction = (double) (baseMaterial.getMaxDurability() - d.getDamage()) / (double) baseMaterial.getMaxDurability();
            }
            fraction = Math.max(0, Math.min(1, fraction));
            int newDurability = (int) Math.ceil(fraction * newMaxDurability);
            setDurability(item, newDurability, newMaxDurability);
        }
    }
}
