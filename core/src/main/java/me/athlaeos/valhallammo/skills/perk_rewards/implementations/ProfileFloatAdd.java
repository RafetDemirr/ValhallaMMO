package me.athlaeos.valhallammo.skills.perk_rewards.implementations;

import me.athlaeos.valhallammo.playerstats.AccumulativeStatManager;
import me.athlaeos.valhallammo.playerstats.format.StatFormat;
import me.athlaeos.valhallammo.playerstats.profiles.Profile;
import me.athlaeos.valhallammo.playerstats.profiles.ProfileRegistry;
import me.athlaeos.valhallammo.playerstats.profiles.implementations.ConfigurableProfile;
import me.athlaeos.valhallammo.playerstats.profiles.properties.StatProperties;
import me.athlaeos.valhallammo.skills.perk_rewards.MultiplicativeReward;
import me.athlaeos.valhallammo.skills.perk_rewards.PerkReward;
import me.athlaeos.valhallammo.skills.perk_rewards.PerkRewardArgumentType;
import org.bukkit.entity.Player;

public class ProfileFloatAdd extends PerkReward implements MultiplicativeReward {
    private float value;
    private final String stat;
    private final Class<? extends Profile> type;
    private final String configurableType;
    public ProfileFloatAdd(String name, String stat, Class<? extends Profile> type) {
        super(name);
        this.stat = stat;
        this.type = type;
        this.configurableType = null;
    }
    public ProfileFloatAdd(String name, String stat, String type) {
        super(name);
        this.stat = stat;
        this.type = null;
        this.configurableType = type;
    }

    @Override
    public void apply(Player player) {
        apply(player, 1);
    }

    @Override
    public void remove(Player player) {
        remove(player, 1);
    }

    @Override
    public void apply(Player player, int multiplyBy) {
        Profile profile = this.type != null ? (isPersistent() ? ProfileRegistry.getPersistentProfile(player, type) : ProfileRegistry.getSkillProfile(player, type)) :
                (isPersistent() ? ProfileRegistry.getPersistentConfigurableProfile(player, configurableType) : ProfileRegistry.getSkillConfigurableProfile(player, configurableType));

        profile.setFloat(stat, profile.getFloat(stat) + (value * multiplyBy));

        if (this.type != null) {
            if (isPersistent()) {
                profile.setShouldForcePersist(true);
                ProfileRegistry.setPersistentProfile(player, profile, type);
            } else ProfileRegistry.setSkillProfile(player, profile, type);
        } else {
            if (isPersistent()) {
                profile.setShouldForcePersist(true);
                ProfileRegistry.setPersistentConfigurableProfile(player, (ConfigurableProfile) profile, configurableType);
            } else ProfileRegistry.setSkillConfigurableProfile(player, (ConfigurableProfile) profile, configurableType);
        }

        AccumulativeStatManager.uncacheProfile(player, type);
    }

    @Override
    public void remove(Player player, int multiplyBy) {
        Profile profile = this.type != null ? (isPersistent() ? ProfileRegistry.getPersistentProfile(player, type) : ProfileRegistry.getSkillProfile(player, type)) :
                (isPersistent() ? ProfileRegistry.getPersistentConfigurableProfile(player, configurableType) : ProfileRegistry.getSkillConfigurableProfile(player, configurableType));

        profile.setFloat(stat, profile.getFloat(stat) - (value * multiplyBy));

        if (this.type != null) {
            if (isPersistent()) {
                profile.setShouldForcePersist(true);
                ProfileRegistry.setPersistentProfile(player, profile, type);
            } else ProfileRegistry.setSkillProfile(player, profile, type);
        } else {
            if (isPersistent()) {
                profile.setShouldForcePersist(true);
                ProfileRegistry.setPersistentConfigurableProfile(player, (ConfigurableProfile) profile, configurableType);
            } else ProfileRegistry.setSkillConfigurableProfile(player, (ConfigurableProfile) profile, configurableType);
        }

        AccumulativeStatManager.uncacheProfile(player, type);
    }

    @Override
    public void parseArgument(Object argument) {
        value = parseFloat(argument);
    }

    @Override
    public String rewardPlaceholder() {
        StatProperties properties = this.type != null ? ProfileRegistry.getRegisteredProfiles().get(type).getNumberStatProperties().get(stat) :
                ProfileRegistry.getRegisteredConfigurableProfiles().get(configurableType).getNumberStatProperties().get(stat);
        if (properties == null) return StatFormat.FLOAT_P2.format(value);
        return properties.getFormat().format(value);
    }

    @Override
    public PerkRewardArgumentType getRequiredType() {
        return PerkRewardArgumentType.FLOAT;
    }
}
