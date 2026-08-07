package me.athlaeos.valhallammo.placeholder.placeholders;

import me.athlaeos.valhallammo.placeholder.Placeholder;
import me.athlaeos.valhallammo.playerstats.format.StatFormat;
import me.athlaeos.valhallammo.playerstats.profiles.Profile;
import me.athlaeos.valhallammo.playerstats.profiles.ProfileCache;
import me.athlaeos.valhallammo.skills.skills.SkillRegistry;
import org.bukkit.entity.Player;

public class ProfileNextLevelPlaceholder extends Placeholder {
    private StatFormat format;
    private final Class<? extends Profile> type;
    private final String configurableType;

    public ProfileNextLevelPlaceholder(String placeholder, Class<? extends Profile> type, StatFormat format) {
        super(placeholder);
        this.type = type;
        this.format = format;
        this.configurableType = null;
    }
    public ProfileNextLevelPlaceholder(String placeholder, String type, StatFormat format) {
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
        return s.replace(placeholder, format.format(Math.min(SkillRegistry.getSkill(profile.getSkillTypeName()).getMaxLevel(), profile.getLevel() + 1)));
    }
}
