package com.fool.duels.hook;

import com.fool.duels.FoolDuels;
import com.fool.duels.data.StatsManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PlaceholderAPI expansion. Only loaded when PlaceholderAPI is installed.
 *
 * <pre>
 * %foolduels_elo%  %foolduels_wins%  %foolduels_losses%  %foolduels_winrate%  %foolduels_rank%
 * %foolduels_top_name_1%  %foolduels_top_elo_1%  %foolduels_top_wins_1%  %foolduels_top_losses_1%
 * %foolduels_in_match%  %foolduels_queue%  %foolduels_queue_size%
 * %foolduels_tournament_state%  %foolduels_tournament_players%  %foolduels_tournament_pot%
 * </pre>
 */
public final class PapiExpansion extends PlaceholderExpansion {
    private final FoolDuels plugin;

    public PapiExpansion(FoolDuels plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "foolduels";
    }

    @Override
    public String getAuthor() {
        return "Fool";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        String p = params.toLowerCase();

        if (p.startsWith("top_")) {
            // top_<field>_<n>
            int last = p.lastIndexOf('_');
            String field = p.substring(4, last);
            int n;
            try {
                n = Integer.parseInt(p.substring(last + 1));
            } catch (NumberFormatException e) {
                return null;
            }
            List<Map.Entry<UUID, StatsManager.Stats>> top = plugin.stats().topCached();
            if (n < 1 || n > top.size()) return field.equals("name") ? "-" : "0";
            StatsManager.Stats s = top.get(n - 1).getValue();
            return switch (field) {
                case "name" -> s.name;
                case "elo" -> String.valueOf(s.elo);
                case "wins" -> String.valueOf(s.wins);
                case "losses" -> String.valueOf(s.losses);
                default -> null;
            };
        }
        switch (p) {
            case "tournament_state":
                return plugin.tournament().stateText();
            case "tournament_players":
                return String.valueOf(plugin.tournament().playerCount());
            case "tournament_pot":
                return plugin.economy().format(plugin.tournament().pot());
            case "queue_size":
                return String.valueOf(plugin.queues().totalSize());
            default:
        }
        if (player == null) return "";
        UUID id = player.getUniqueId();
        StatsManager.Stats s = plugin.stats().find(id);
        return switch (p) {
            case "elo" -> String.valueOf(plugin.stats().elo(id));
            case "wins" -> String.valueOf(s == null ? 0 : s.wins);
            case "losses" -> String.valueOf(s == null ? 0 : s.losses);
            case "winrate" -> {
                int total = s == null ? 0 : s.wins + s.losses;
                yield total == 0 ? "0" : String.valueOf(Math.round(s.wins * 100.0 / total));
            }
            case "rank" -> {
                int r = plugin.stats().rank(id);
                yield r <= 0 ? "-" : String.valueOf(r);
            }
            case "in_match" -> plugin.matches().inMatch(id) ? "true" : "false";
            case "queue" -> {
                String q = plugin.queues().queueOf(id);
                yield q == null ? "" : q;
            }
            default -> null;
        };
    }
}
