package com.fool.duels.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * CombatLogX integration without a compile-time dependency.
 * <ul>
 *   <li>Players in a duel are never combat-tagged (PlayerPreTagEvent is cancelled).</li>
 *   <li>Players in a duel (or waiting for their post-duel restore) are never punished on quit/kick
 *       (PlayerPunishEvent is cancelled), so CombatLogX never kills them or drops their items.
 *       FoolDuels handles the quit itself and restores their inventory.</li>
 *   <li>Duels can't start while a participant is combat-tagged from a fight outside the arena.</li>
 * </ul>
 */
public final class CombatLogXHook {
    private static final String[] PROTECTED_EVENTS = {
            "com.github.sirblobman.combatlogx.api.event.PlayerPreTagEvent",
            "com.github.sirblobman.combatlogx.api.event.PlayerPunishEvent"
    };

    private Plugin combatLogX;

    @SuppressWarnings("unchecked")
    public void setup(Plugin owner, Predicate<Player> isProtected, Logger log) {
        combatLogX = Bukkit.getPluginManager().getPlugin("CombatLogX");
        if (combatLogX == null || !combatLogX.isEnabled()) {
            combatLogX = null;
            return;
        }
        Listener dummy = new Listener() {};
        ClassLoader loader = combatLogX.getClass().getClassLoader();
        for (String className : PROTECTED_EVENTS) {
            try {
                Class<? extends Event> type = (Class<? extends Event>) Class.forName(className, true, loader);
                Method getPlayer = type.getMethod("getPlayer");
                Bukkit.getPluginManager().registerEvent(type, dummy, EventPriority.HIGHEST, (l, event) -> {
                    if (!type.isInstance(event) || !(event instanceof Cancellable c)) return;
                    try {
                        Object p = getPlayer.invoke(event);
                        if (p instanceof Player player && isProtected.test(player)) c.setCancelled(true);
                    } catch (ReflectiveOperationException ignored) {
                    }
                }, owner, false);
                log.info("CombatLogX entegrasyonu: " + type.getSimpleName() + " dinleniyor.");
            } catch (ReflectiveOperationException | ClassCastException e) {
                log.warning("CombatLogX olayı bulunamadı (" + className + "): " + e.getMessage());
            }
        }
    }

    public boolean isInCombat(Player player) {
        if (combatLogX == null) return false;
        try {
            Object manager = combatLogX.getClass().getMethod("getCombatManager").invoke(combatLogX);
            Object result = manager.getClass().getMethod("isInCombat", Player.class).invoke(manager, player);
            return result instanceof Boolean b && b;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
