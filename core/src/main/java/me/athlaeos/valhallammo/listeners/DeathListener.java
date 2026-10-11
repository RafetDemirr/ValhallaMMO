package me.athlaeos.valhallammo.listeners;

import me.athlaeos.valhallammo.ValhallaMMO;
import me.athlaeos.valhallammo.dom.CustomDamageType;
import me.athlaeos.valhallammo.entities.MonsterScalingManager;
import me.athlaeos.valhallammo.localization.TranslationManager;
import me.athlaeos.valhallammo.playerstats.AccumulativeStatManager;
import me.athlaeos.valhallammo.playerstats.EntityCache;
import me.athlaeos.valhallammo.potioneffects.PotionEffectRegistry;
import me.athlaeos.valhallammo.skills.ChunkEXPNerf;
import me.athlaeos.valhallammo.utility.Bleeder;
import me.athlaeos.valhallammo.utility.StringUtils;
import me.athlaeos.valhallammo.utility.Utils;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.*;

public class DeathListener implements Listener {
    private final String deathStatus;
    private final String deathStatusNoDamageType;
    private final String killedStatus;
    private final String killedStatusNoDamageType;

    public DeathListener(){
        deathStatus = TranslationManager.getTranslation("death_status");
        killedStatus = TranslationManager.getTranslation("death_status_killer");
        deathStatusNoDamageType = TranslationManager.getTranslation("death_status_no_damage_type");
        killedStatusNoDamageType = TranslationManager.getTranslation("death_status_killer_no_damage_type");
    }

    /**
     * Lets players keep a fraction of their experience on death, defined by the DEATH_EXP_RETENTION stat. The experience
     * they'd drop is reduced by the same fraction, so the experience kept and dropped never exceeds what they had.<br>
     * Does nothing if the player already keeps their levels (keepInventory, or another plugin deciding so)
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDeathExpRetention(PlayerDeathEvent e){
        if (e.getKeepLevel() || ValhallaMMO.isWorldBlacklisted(e.getEntity().getWorld().getName())) return;
        double retention = Math.max(0, Math.min(1, AccumulativeStatManager.getCachedStats("DEATH_EXP_RETENTION", e.getEntity(), 10000, true)));
        if (retention <= 0) return;
        int kept = (int) Math.floor(totalExperience(e.getEntity().getLevel(), e.getEntity().getExp()) * retention);
        if (kept <= 0) return;
        // the player respawns at level 0 and is then granted the experience they kept
        e.setNewLevel(0);
        e.setNewTotalExp(0);
        e.setNewExp(kept);
        e.setDroppedExp((int) Math.floor(e.getDroppedExp() * (1 - retention)));
    }

    /**
     * @return the total amount of experience points needed to get to the given level and progress to the next level
     */
    private static int totalExperience(int level, float progress){
        double total;
        if (level <= 16) total = (level * level) + (6D * level);
        else if (level <= 31) total = (2.5 * level * level) - (40.5 * level) + 360;
        else total = (4.5 * level * level) - (162.5 * level) + 2220;
        int toNextLevel = level <= 15 ? (2 * level) + 7 : level <= 30 ? (5 * level) - 38 : (9 * level) - 158;
        return (int) Math.round(total + (Math.max(0, Math.min(1, progress)) * toNextLevel));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent e){
        PotionEffectRegistry.removePotionEffects(e.getEntity(), EntityPotionEffectEvent.Cause.DEATH, eff -> eff.getWrapper().isRemovable());

        String cause = EntityDamagedListener.getLastDamageCause(e.getEntity());
        if (cause == null) return;

        Entity lastDamager = EntityDamagedListener.getLastDamager(e.getEntity());

        double lastDamageTaken = EntityDamagedListener.getLastDamageTaken(e.getEntity().getUniqueId());
        CustomDamageType type = CustomDamageType.getCustomType(cause);
        ValhallaMMO.getInstance().getServer().getScheduler().runTaskLater(ValhallaMMO.getInstance(), () -> {
            if (type != null && !StringUtils.isEmpty(type.getTranslation())){
                if (lastDamager == null) Utils.sendMessage(e.getEntity(), deathStatus
                        .replace("%damage%", String.format("%.1f", lastDamageTaken))
                        .replace("%type%", type.getTranslation()));
                else Utils.sendMessage(e.getEntity(), killedStatus
                        .replace("%damage%", String.format("%.1f", lastDamageTaken))
                        .replace("%type%", type.getTranslation())
                        .replace("%killer%", lastDamager.getName()));
            } else {
                if (lastDamager == null) Utils.sendMessage(e.getEntity(), deathStatusNoDamageType
                        .replace("%damage%", String.format("%.1f", lastDamageTaken)));
                else Utils.sendMessage(e.getEntity(), killedStatusNoDamageType
                        .replace("%damage%", String.format("%.1f", lastDamageTaken))
                        .replace("%killer%", lastDamager.getName()));
            }
        }, 20L);

        List<String> deathMessages = lastDamager != null ?
                TranslationManager.getListTranslation("death_message_" + cause.toLowerCase(java.util.Locale.US) + "_enemy") :
                TranslationManager.getListTranslation("death_message_" + cause.toLowerCase(java.util.Locale.US));
        if (deathMessages == null || deathMessages.isEmpty()) return;
        String pickedEntry = deathMessages.get(Utils.getRandom().nextInt(deathMessages.size()));
        if (!StringUtils.isEmpty(pickedEntry)) e.setDeathMessage(Utils.chat(pickedEntry
                .replace("%player%", e.getEntity().getName())
                .replace("%killer%", lastDamager != null ? lastDamager.getName() : ""))
        );
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityExpDrops(EntityDeathEvent e){
        double multiplier = (1 + MonsterScalingManager.getExpOrbMultiplier(e.getEntity()));
        if (e.getEntity().getKiller() != null) multiplier *= ChunkEXPNerf.getChunkEXPOrbsNerf(e.getEntity().getLocation().getChunk(), e.getEntity().getKiller(), "exp_orbs");
        e.setDroppedExp(Utils.randomAverage(e.getDroppedExp() * multiplier));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent e){
        EntityCache.removeProperties(e.getEntity());
        Bleeder.removeBleed(e.getEntity());
        AccumulativeStatManager.uncache(e.getEntity());
    }
}
