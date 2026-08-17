package org.byauth.manager;

import org.byauth.ByCircleGame;
import org.byauth.game.Team;
import org.byauth.utils.SettingsManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SpecialItemManager {

    private final GameManager gameManager;
    private final ByCircleGame plugin;
    private final SettingsManager settings;
    private final Map<Team, BukkitTask> activeEffects = new HashMap<>();

    public SpecialItemManager(ByCircleGame plugin, GameManager gameManager) {
        this.gameManager = gameManager;
        this.plugin = plugin;
        this.settings = plugin.getSettingsManager();
    }

    public void handleCandlePlace(Player placer, Team team, Material candleType) {
        if (activeEffects.containsKey(team)) {
            settings.sendMessage(placer, settings.getMessage("errors.team-already-has-buff"));
            return;
        }

        PotionEffect effect = null;
        String effectName = "";
        int duration = 0;

        if (candleType == Material.LIME_CANDLE) {
            effect = new PotionEffect(PotionEffectType.SPEED, 30 * 20, 1);
            effectName = "Hız II";
            duration = 30;
        } else if (candleType == Material.RED_CANDLE) {
            effect = new PotionEffect(PotionEffectType.STRENGTH, 15 * 20, 0);
            effectName = "Kuvvet I";
            duration = 15;
        } else if (candleType == Material.PINK_CANDLE) {
            effect = new PotionEffect(PotionEffectType.REGENERATION, 10 * 20, 1);
            effectName = "Yenilenme II";
            duration = 10;
        }

        if (effect != null) {
            placer.getInventory().getItemInMainHand().setAmount(placer.getInventory().getItemInMainHand().getAmount() - 1);
            applyEffectToTeam(team, effect, effectName, duration);
        }
    }

    private void applyEffectToTeam(Team team, PotionEffect effect, String effectName, int duration) {
        List<UUID> teamMembers = gameManager.getTeams().get(team);
        if (teamMembers == null) return;

        String message = settings.getMessage("game.team-buff-activated")
                .replace("%team_color%", team.getChatColor().toString())
                .replace("%team_name%", team.getDisplayName())
                .replace("%duration%", String.valueOf(duration))
                .replace("%buff_name%", effectName);

        plugin.getArenaController().broadcastArenaMessage(gameManager.getArena(), message);

        for (UUID memberUUID : teamMembers) {
            Player member = Bukkit.getPlayer(memberUUID);
            if (member != null) {
                member.addPotionEffect(effect);
            }
        }

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            activeEffects.remove(team);
            String endMessage = settings.getMessage("game.team-buff-expired")
                    .replace("%team_color%", team.getChatColor().toString())
                    .replace("%team_name%", team.getDisplayName())
                    .replace("%buff_name%", effectName);
            plugin.getArenaController().broadcastArenaMessage(gameManager.getArena(), endMessage);
        }, duration * 20L);

        activeEffects.put(team, task);
    }

    public void cancelAllEffects() {
        for (BukkitTask task : activeEffects.values()) {
            task.cancel();
        }
        activeEffects.clear();
    }
}