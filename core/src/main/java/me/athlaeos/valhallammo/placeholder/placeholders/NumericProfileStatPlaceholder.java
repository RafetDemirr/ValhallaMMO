package me.athlaeos.valhallammo.placeholder.placeholders;

import me.athlaeos.valhallammo.placeholder.Placeholder;
import me.athlaeos.valhallammo.playerstats.format.StatFormat;
import me.athlaeos.valhallammo.playerstats.profiles.Profile;
import me.athlaeos.valhallammo.playerstats.profiles.ProfileCache;
import org.bukkit.entity.Player;

public class NumericProfileStatPlaceholder extends Placeholder {
    private StatFormat format;
    private final Class<? extends Profile> type;
    private final String stat;
    private final String configurableType;

    public NumericProfileStatPlaceholder(String placeholder, Class<? extends Profile> type, String stat, StatFormat format) {
        super(placeholder);
        this.type = type;
        this.stat = stat;
        this.format = format;
        this.configurableType = null;
    }
    public NumericProfileStatPlaceholder(String placeholder, String type, String stat, StatFormat format) {
        super(placeholder);
        this.type = null;
        this.format = format;
        this.stat = stat;
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
        if (profile.intStatNames().contains(stat)) return s.replace(placeholder, format.format(profile.getInt(stat)));
        if (profile.floatStatNames().contains(stat)) return s.replace(placeholder, format.format(profile.getFloat(stat)));
        if (profile.doubleStatNames().contains(stat)) return s.replace(placeholder, format.format(profile.getDouble(stat)));
        if (profile.stringSetStatNames().contains(stat)) return s.replace(placeholder, String.join(",", profile.getStringSet(stat)));
        if (profile.booleanStatNames().contains(stat)) return s.replace(placeholder, String.valueOf(profile.getBoolean(stat)));
        throw new IllegalArgumentException("Numeric stat placeholder uses stat " + stat + ", but it's not a number, string set, or boolean");
    }
}
