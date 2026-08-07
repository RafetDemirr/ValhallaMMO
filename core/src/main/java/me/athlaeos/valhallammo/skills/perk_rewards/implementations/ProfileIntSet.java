package me.athlaeos.valhallammo.skills.perk_rewards.implementations;

import me.athlaeos.valhallammo.playerstats.AccumulativeStatManager;
import me.athlaeos.valhallammo.playerstats.format.StatFormat;
import me.athlaeos.valhallammo.playerstats.profiles.implementations.ConfigurableProfile;
import me.athlaeos.valhallammo.skills.perk_rewards.PerkReward;
import me.athlaeos.valhallammo.skills.perk_rewards.PerkRewardArgumentType;
import me.athlaeos.valhallammo.playerstats.profiles.Profile;
import me.athlaeos.valhallammo.playerstats.profiles.ProfileRegistry;
import me.athlaeos.valhallammo.playerstats.profiles.properties.StatProperties;
import org.bukkit.entity.Player;

public class ProfileIntSet extends PerkReward {
    private int value;
    private final String stat;
    private final Class<? extends Profile> type;
    private final String configurableType;
    public ProfileIntSet(String name, String stat, Class<? extends Profile> type) {
        super(name);
        this.stat = stat;
        this.type = type;
        this.configurableType = null;
    }
    public ProfileIntSet(String name, String stat, String type) {
        super(name);
        this.stat = stat;
        this.type = null;
        this.configurableType = type;
    }

    @Override
    public void apply(Player player) {
        Profile profile = this.type != null ? (isPersistent() ? ProfileRegistry.getPersistentProfile(player, type) : ProfileRegistry.getSkillProfile(player, type)) :
                (isPersistent() ? ProfileRegistry.getPersistentConfigurableProfile(player, configurableType) : ProfileRegistry.getSkillConfigurableProfile(player, configurableType));

        profile.setInt(stat, value);

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
    public void remove(Player player) { }

    @Override
    public void parseArgument(Object argument) {
        value = parseInt(argument);
    }

    @Override
    public String rewardPlaceholder() {
        StatProperties properties = this.type != null ? ProfileRegistry.getRegisteredProfiles().get(type).getNumberStatProperties().get(stat) :
                ProfileRegistry.getRegisteredConfigurableProfiles().get(configurableType).getNumberStatProperties().get(stat);
        if (properties == null) return StatFormat.INT.format(value);
        return properties.getFormat().format(value);
    }

    @Override
    public PerkRewardArgumentType getRequiredType() {
        return PerkRewardArgumentType.INTEGER;
    }
}
