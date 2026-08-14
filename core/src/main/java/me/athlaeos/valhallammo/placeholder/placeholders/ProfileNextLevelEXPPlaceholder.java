package me.athlaeos.valhallammo.placeholder.placeholders;

import me.athlaeos.valhallammo.localization.TranslationManager;
import me.athlaeos.valhallammo.placeholder.Placeholder;
import me.athlaeos.valhallammo.playerstats.format.StatFormat;
import me.athlaeos.valhallammo.playerstats.profiles.Profile;
import me.athlaeos.valhallammo.playerstats.profiles.ProfileCache;
import me.athlaeos.valhallammo.skills.skills.Skill;
import me.athlaeos.valhallammo.skills.skills.SkillRegistry;
import org.bukkit.entity.Player;

public class ProfileNextLevelEXPPlaceholder extends Placeholder {
    private StatFormat format;
    private final Class<? extends Profile> type;
    private final String configurableType;

    public ProfileNextLevelEXPPlaceholder(String placeholder, Class<? extends Profile> type, StatFormat format) {
        super(placeholder);
        this.type = type;
        this.format = format;
        this.configurableType = null;
    }
    public ProfileNextLevelEXPPlaceholder(String placeholder, String type, StatFormat format) {
        super(placeholder);
        this.type = null;
        this.format = format;
        this.configurableType = type;
    }

    public StatFormat getFormat() {
        return format;
    }

    public void setFormat(StatFormat format) {
        this.format = format;
    }

    @Override
    public String parse(String s, Player p) {
        Profile profile = this.type != null ? ProfileCache.getOrCache(p, type) : ProfileCache.getOrCacheConfigurable(p, configurableType);
        Skill skill = SkillRegistry.getSkill(profile.getSkillTypeName());
        if (profile.getLevel() >= skill.getMaxLevel()) return s.replace(placeholder, TranslationManager.getTranslation("max_level"));
        return s.replace(placeholder, format.format(skill.expForLevel(profile.getLevel() + 1)));
    }
}
