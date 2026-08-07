package me.athlaeos.valhallammo.playerstats.profiles.implementations;

import me.athlaeos.valhallammo.playerstats.format.StatFormat;
import me.athlaeos.valhallammo.playerstats.profiles.Profile;
import me.athlaeos.valhallammo.playerstats.profiles.ProfileRegistry;
import me.athlaeos.valhallammo.playerstats.profiles.properties.PropertyBuilder;
import me.athlaeos.valhallammo.skills.skills.Skill;
import me.athlaeos.valhallammo.skills.skills.implementations.ConfigurableSkill;

import java.util.UUID;

@SuppressWarnings("unused")
public class ConfigurableProfile extends Profile {
    private final String type;
    {
        doubleStat("expMultiplier", new PropertyBuilder().format(StatFormat.PERCENTILE_BASE_1_P2).perkReward().create());
    }
    public double getExperienceMultiplier(){ return getDouble("expMultiplier");}
    public void setExperienceMultiplier(double value){ setDouble("expMultiplier", value);}

    public ConfigurableProfile(UUID owner, String type) {
        super(owner);
        this.type = type;
    }

    @Override
    public String getTableName() {
        return "profiles_" + type;
    }

    @Override
    public Profile getBlankProfile(UUID owner) {
        return ProfileRegistry.copyDefaultStats(new ConfigurableProfile(owner, type));
    }

    @Override
    public Class<? extends Skill> getSkillType() {
        return ConfigurableSkill.class;
    }

    @Override
    public String getSkillTypeName() {
        return type;
    }
}
