package me.athlaeos.valhallammo.skills.skills.implementations;

import me.athlaeos.valhallammo.ValhallaMMO;
import me.athlaeos.valhallammo.configuration.ConfigManager;
import me.athlaeos.valhallammo.playerstats.profiles.Profile;
import me.athlaeos.valhallammo.playerstats.profiles.implementations.ConfigurableProfile;
import me.athlaeos.valhallammo.skills.skills.Skill;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;

public class ConfigurableSkill extends Skill implements Listener {
    private final int order;
    public ConfigurableSkill(String type, int order) {
        super(type);
        this.order = order;
    }

    @Override
    public void loadConfiguration() {
        ValhallaMMO.getInstance().save("skills/custom/" + type + ".yml");

        YamlConfiguration skillConfig = ConfigManager.getConfig("skills/custom/" + type + ".yml").get();

        loadCommonConfig(skillConfig, skillConfig);
    }

    @Override
    public boolean isLevelableSkill() {
        return true;
    }

    @Override
    public Class<? extends Profile> getProfileType() {
        return ConfigurableProfile.class;
    }

    @Override
    public int getSkillTreeMenuOrderPriority() {
        return order;
    }
}
