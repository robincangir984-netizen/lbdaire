package org.byauth.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.byauth.ByCircleGame;
import org.byauth.data.PlayerStats;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;

public class PlaceholderHook extends PlaceholderExpansion {

    private final ByCircleGame plugin;

    public PlaceholderHook(ByCircleGame plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "bycirclegame";
    }

    @Override
    public @NotNull String getAuthor() {
        return "ByAuth";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        PlayerStats stats = plugin.getPlayerDataManager().getStats(player.getUniqueId());
        if (stats == null) {
            return "0";
        }

        switch (params.toLowerCase()) {
            case "points":
                return String.valueOf(stats.getPoints());
            case "bcoin":
                return String.valueOf(stats.getBCoin());
            case "kills":
                return String.valueOf(stats.getKills());
            case "deaths":
                return String.valueOf(stats.getDeaths());
            case "wins":
                return String.valueOf(stats.getWins());
            case "losses":
                return String.valueOf(stats.getLosses());
            case "kdr":
                if (stats.getDeaths() == 0) {
                    return String.valueOf(stats.getKills());
                }
                DecimalFormat df = new DecimalFormat("#.##");
                return df.format((double) stats.getKills() / stats.getDeaths());
            default:
                return null;
        }
    }
}