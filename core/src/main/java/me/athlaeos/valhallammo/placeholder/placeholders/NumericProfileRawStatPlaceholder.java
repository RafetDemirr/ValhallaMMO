package me.athlaeos.valhallammo.placeholder.placeholders;

import me.athlaeos.valhallammo.placeholder.Placeholder;
import me.athlaeos.valhallammo.playerstats.format.StatFormat;
import me.athlaeos.valhallammo.playerstats.profiles.Profile;
import me.athlaeos.valhallammo.playerstats.profiles.ProfileCache;
import org.bukkit.entity.Player;

public class NumericProfileRawStatPlaceholder extends Placeholder {
    private final Class<? extends Profile> type;
    private final String stat;
    private final String configurableType;

    public NumericProfileRawStatPlaceholder(String placeholder, Class<? extends Profile> type, String stat) {
        super(placeholder);
        this.type = type;
        this.stat = stat;
        this.configurableType = null;
    }
    public NumericProfileRawStatPlaceholder(String placeholder, String type, String stat) {
        super(placeholder);
        this.type = null;
        this.stat = stat;
        this.configurableType = type;
    }

    @Override
    public String parse(String s, Player p) {
        Profile profile = this.type != null ? ProfileCache.getOrCache(p, type) : ProfileCache.getOrCacheConfigurable(p, configurableType);
        if (profile.intStatNames().contains(stat)) return s.replace(placeholder, StatFormat.INT.format(profile.getInt(stat)));
        if (profile.floatStatNames().contains(stat)) return s.replace(placeholder, StatFormat.FLOAT_P2.format(profile.getFloat(stat)));
        if (profile.doubleStatNames().contains(stat)) return s.replace(placeholder, StatFormat.FLOAT_P2.format(profile.getDouble(stat)));
        if (profile.stringSetStatNames().contains(stat)) return s.replace(placeholder, String.join(",", profile.getStringSet(stat)));
        if (profile.booleanStatNames().contains(stat)) return s.replace(placeholder, String.valueOf(profile.getBoolean(stat)));
        throw new IllegalArgumentException("Numeric stat placeholder uses stat " + stat + ", but it's not a number, string set, or boolean");
    }
}
