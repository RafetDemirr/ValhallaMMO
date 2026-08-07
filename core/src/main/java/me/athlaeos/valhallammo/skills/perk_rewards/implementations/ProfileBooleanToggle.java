package me.athlaeos.valhallammo.skills.perk_rewards.implementations;

import me.athlaeos.valhallammo.localization.TranslationManager;
import me.athlaeos.valhallammo.playerstats.AccumulativeStatManager;
import me.athlaeos.valhallammo.playerstats.profiles.Profile;
import me.athlaeos.valhallammo.playerstats.profiles.ProfileRegistry;
import me.athlaeos.valhallammo.playerstats.profiles.implementations.ConfigurableProfile;
import me.athlaeos.valhallammo.skills.perk_rewards.PerkReward;
import me.athlaeos.valhallammo.skills.perk_rewards.PerkRewardArgumentType;
import org.bukkit.entity.Player;

public class ProfileBooleanToggle extends PerkReward {
    private final String stat;
    private final Class<? extends Profile> type;
    private final String configurableType;
    public ProfileBooleanToggle(String name, String stat, Class<? extends Profile> type) {
        super(name);
        this.stat = stat;
        this.type = type;
        this.configurableType = null;
    }
    public ProfileBooleanToggle(String name, String stat, String type) {
        super(name);
        this.stat = stat;
        this.type = null;
        this.configurableType = type;
    }

    @Override
    public void apply(Player player) {
        Profile profile = this.type != null ? (isPersistent() ? ProfileRegistry.getPersistentProfile(player, type) : ProfileRegistry.getSkillProfile(player, type)) :
                (isPersistent() ? ProfileRegistry.getPersistentConfigurableProfile(player, configurableType) : ProfileRegistry.getSkillConfigurableProfile(player, configurableType));

        profile.setBoolean(stat, !profile.getBoolean(stat));

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
    public void remove(Player player) {
        Profile profile = this.type != null ? (isPersistent() ? ProfileRegistry.getPersistentProfile(player, type) : ProfileRegistry.getSkillProfile(player, type)) :
                (isPersistent() ? ProfileRegistry.getPersistentConfigurableProfile(player, configurableType) : ProfileRegistry.getSkillConfigurableProfile(player, configurableType));

        profile.setBoolean(stat, !profile.getBoolean(stat));

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
    public void parseArgument(Object argument) { }

    @Override
    public String rewardPlaceholder() {
        return TranslationManager.getTranslation("translation_toggles");
    }

    @Override
    public PerkRewardArgumentType getRequiredType() {
        return PerkRewardArgumentType.NONE;
    }
}
