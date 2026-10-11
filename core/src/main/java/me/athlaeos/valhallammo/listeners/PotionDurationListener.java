package me.athlaeos.valhallammo.listeners;

import me.athlaeos.valhallammo.ValhallaMMO;
import me.athlaeos.valhallammo.event.EntityCustomPotionEffectEvent;
import me.athlaeos.valhallammo.playerstats.AccumulativeStatManager;
import me.athlaeos.valhallammo.potioneffects.CustomPotionEffect;
import me.athlaeos.valhallammo.potioneffects.EffectClass;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Set;

/**
 * Scales the duration of beneficial (POTION_DURATION_BONUS) and harmful (POTION_DEBUFF_BONUS) effects an entity
 * receives. Only effects coming from actual potions are affected, so beacons, commands, food, other plugins, etc. are not.
 */
public class PotionDurationListener implements Listener {
    private static final Set<EntityPotionEffectEvent.Cause> POTION_CAUSES = Set.of(
            EntityPotionEffectEvent.Cause.POTION_DRINK,
            EntityPotionEffectEvent.Cause.POTION_SPLASH,
            EntityPotionEffectEvent.Cause.AREA_EFFECT_CLOUD,
            EntityPotionEffectEvent.Cause.ARROW
    );

    /**
     * @return the number the duration of an effect of the given class should be multiplied with for this entity, never below 0
     */
    private static double durationMultiplier(LivingEntity entity, EffectClass effectClass){
        if (effectClass == EffectClass.BUFF) return Math.max(0, 1 + AccumulativeStatManager.getCachedStats("POTION_DURATION_BONUS", entity, 10000, true));
        if (effectClass == EffectClass.DEBUFF) return Math.max(0, 1 + AccumulativeStatManager.getCachedStats("POTION_DEBUFF_BONUS", entity, 10000, true));
        return 1;
    }

    private static boolean affects(EntityPotionEffectEvent.Cause cause, EntityPotionEffectEvent.Action action){
        return POTION_CAUSES.contains(cause) && (action == EntityPotionEffectEvent.Action.ADDED || action == EntityPotionEffectEvent.Action.CHANGED);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVanillaPotionEffect(EntityPotionEffectEvent e){
        if (!(e.getEntity() instanceof LivingEntity entity) || ValhallaMMO.isWorldBlacklisted(entity.getWorld().getName())) return;
        if (!affects(e.getCause(), e.getAction())) return;
        PotionEffect applied = e.getNewEffect();
        if (applied == null || applied.getType().isInstant() || applied.getDuration() <= 0) return; // instant or infinite
        double multiplier = durationMultiplier(entity, EffectClass.getClass(applied.getType()));
        if (multiplier == 1) return;
        PotionEffectType type = applied.getType();
        int newDuration = (int) Math.round(applied.getDuration() * multiplier);

        // The event doesn't allow the effect to be changed and cancelling it to apply another would change the effect's
        // cause, which other mechanics depend on. So the effect is left to be applied as normal and replaced right after.
        ValhallaMMO.getInstance().getServer().getScheduler().runTask(ValhallaMMO.getInstance(), () -> {
            if (!entity.isValid()) return;
            PotionEffect current = entity.getPotionEffect(type);
            // only replace the effect if it's still the one that was applied here
            if (current == null || current.getAmplifier() != applied.getAmplifier() || Math.abs(current.getDuration() - applied.getDuration()) > 5) return;
            entity.removePotionEffect(type);
            if (newDuration > 0) entity.addPotionEffect(new PotionEffect(type, newDuration, current.getAmplifier(), current.isAmbient(), current.hasParticles(), current.hasIcon()));
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCustomPotionEffect(EntityCustomPotionEffectEvent e){
        if (!(e.getEntity() instanceof LivingEntity entity) || ValhallaMMO.isWorldBlacklisted(entity.getWorld().getName())) return;
        if (!affects(e.getCause(), e.getAction())) return;
        CustomPotionEffect applied = e.getNewEffect();
        if (applied == null || applied.getWrapper().isInstant() || applied.getEffectiveUntil() <= 0) return; // instant, infinite or being removed
        long remaining = applied.getRemainingDuration();
        if (remaining <= 0) return;
        double multiplier = durationMultiplier(entity, applied.getWrapper().getClassification(applied.getAmplifier()));
        if (multiplier == 1) return;
        applied.setEffectiveUntil(System.currentTimeMillis() + Math.max(1, Math.round(remaining * multiplier)));
    }
}
