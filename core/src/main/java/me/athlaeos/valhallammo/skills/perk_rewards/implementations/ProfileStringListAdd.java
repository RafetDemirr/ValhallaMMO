package me.athlaeos.valhallammo.skills.perk_rewards.implementations;

import me.athlaeos.valhallammo.dom.BiAction;
import me.athlaeos.valhallammo.playerstats.AccumulativeStatManager;
import me.athlaeos.valhallammo.playerstats.profiles.implementations.ConfigurableProfile;
import me.athlaeos.valhallammo.skills.perk_rewards.PerkReward;
import me.athlaeos.valhallammo.skills.perk_rewards.PerkRewardArgumentType;
import me.athlaeos.valhallammo.playerstats.profiles.Profile;
import me.athlaeos.valhallammo.playerstats.profiles.ProfileRegistry;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;

public class ProfileStringListAdd extends PerkReward {
    private List<String> value;
    private final String stat;
    private final Class<? extends Profile> type;
    private final String configurableType;
    private BiAction<String, Player> addAction = null;
    private BiAction<String, Player> removeAction = null;
    private boolean alwaysPersistent = false;

    public ProfileStringListAdd(String name, String stat, Class<? extends Profile> type) {
        super(name);
        this.stat = stat;
        this.type = type;
        this.configurableType = null;
    }

    public ProfileStringListAdd(String name, String stat, Class<? extends Profile> type, boolean alwaysPersistent) {
        super(name);
        this.stat = stat;
        this.type = type;
        this.alwaysPersistent = alwaysPersistent;
        this.configurableType = null;
    }

    public ProfileStringListAdd(String name, String stat, Class<? extends Profile> type, BiAction<String, Player> addAction, BiAction<String, Player> removeAction) {
        super(name);
        this.stat = stat;
        this.type = type;
        this.addAction = addAction;
        this.removeAction = removeAction;
        this.configurableType = null;
    }

    public ProfileStringListAdd(String name, String stat, Class<? extends Profile> type, boolean alwaysPersistent, BiAction<String, Player> addAction, BiAction<String, Player> removeAction) {
        super(name);
        this.stat = stat;
        this.type = type;
        this.alwaysPersistent = alwaysPersistent;
        this.addAction = addAction;
        this.removeAction = removeAction;
        this.configurableType = null;
    }

    public ProfileStringListAdd(String name, String stat, String type) {
        super(name);
        this.stat = stat;
        this.type = null;
        this.configurableType = type;
    }

    public ProfileStringListAdd(String name, String stat, String type, boolean alwaysPersistent) {
        super(name);
        this.stat = stat;
        this.type = null;
        this.configurableType = type;
        this.alwaysPersistent = alwaysPersistent;
    }

    public ProfileStringListAdd(String name, String stat, String type, BiAction<String, Player> addAction, BiAction<String, Player> removeAction) {
        super(name);
        this.stat = stat;
        this.type = null;
        this.configurableType = type;
        this.addAction = addAction;
        this.removeAction = removeAction;
    }

    public ProfileStringListAdd(String name, String stat, String type, boolean alwaysPersistent, BiAction<String, Player> addAction, BiAction<String, Player> removeAction) {
        super(name);
        this.stat = stat;
        this.type = null;
        this.configurableType = type;
        this.alwaysPersistent = alwaysPersistent;
        this.addAction = addAction;
        this.removeAction = removeAction;
    }

    @Override
    public void apply(Player player) {
        Profile profile = this.type != null ? (isPersistent() || alwaysPersistent ? ProfileRegistry.getPersistentProfile(player, type) : ProfileRegistry.getSkillProfile(player, type)) :
                (isPersistent() || alwaysPersistent ? ProfileRegistry.getPersistentConfigurableProfile(player, configurableType) : ProfileRegistry.getSkillConfigurableProfile(player, configurableType));

        Collection<String> existing = profile.getStringSet(stat);
        existing.addAll(value);
        profile.setStringSet(stat, existing);
        if (addAction != null) value.forEach(s -> addAction.act(s, player));

        if (this.type != null) {
            if (isPersistent() || alwaysPersistent) {
                profile.setShouldForcePersist(true);
                ProfileRegistry.setPersistentProfile(player, profile, type);
            } else ProfileRegistry.setSkillProfile(player, profile, type);
        } else {
            if (isPersistent() || alwaysPersistent) {
                profile.setShouldForcePersist(true);
                ProfileRegistry.setPersistentConfigurableProfile(player, (ConfigurableProfile) profile, configurableType);
            } else ProfileRegistry.setSkillConfigurableProfile(player, (ConfigurableProfile) profile, configurableType);
        }

        AccumulativeStatManager.uncacheProfile(player, type);
    }

    @Override
    public void remove(Player player) {
        Profile profile = this.type != null ? (isPersistent() || alwaysPersistent ? ProfileRegistry.getPersistentProfile(player, type) : ProfileRegistry.getSkillProfile(player, type)) :
                (isPersistent() || alwaysPersistent ? ProfileRegistry.getPersistentConfigurableProfile(player, configurableType) : ProfileRegistry.getSkillConfigurableProfile(player, configurableType));

        Collection<String> existing = profile.getStringSet(stat);
        existing.removeAll(value);
        profile.setStringSet(stat, existing);
        if (removeAction != null) value.forEach(s -> removeAction.act(s, player));

        if (this.type != null) {
            if (isPersistent() || alwaysPersistent) {
                profile.setShouldForcePersist(true);
                ProfileRegistry.setPersistentProfile(player, profile, type);
            } else ProfileRegistry.setSkillProfile(player, profile, type);
        } else {
            if (isPersistent() || alwaysPersistent) {
                profile.setShouldForcePersist(true);
                ProfileRegistry.setPersistentConfigurableProfile(player, (ConfigurableProfile) profile, configurableType);
            } else ProfileRegistry.setSkillConfigurableProfile(player, (ConfigurableProfile) profile, configurableType);
        }

        AccumulativeStatManager.uncacheProfile(player, type);
    }

    @Override
    public void parseArgument(Object argument) {
        value = parseStringList(argument);
    }

    @Override
    public String rewardPlaceholder() {
        return "";
    }

    @Override
    public PerkRewardArgumentType getRequiredType() {
        return PerkRewardArgumentType.STRING_LIST;
    }
}
