package org.byauth.listener;

import org.byauth.ByCircleGame;
import org.byauth.data.PlayerStats;
import org.byauth.game.EffectPlayer;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;

public class ArrowEffectListener implements Listener {

    private final ByCircleGame plugin;

    public ArrowEffectListener(ByCircleGame plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Arrow)) {
            return;
        }
        if (!(event.getEntity().getShooter() instanceof Player)) {
            return;
        }

        Arrow arrow = (Arrow) event.getEntity();
        Player shooter = (Player) arrow.getShooter();

        if (plugin.getArenaController().getArenaByPlayer(shooter) == null) {
            return;
        }

        PlayerStats stats = plugin.getPlayerDataManager().getStats(shooter.getUniqueId());
        if (stats != null && stats.getSelectedArrowEffect() != null) {
            EffectPlayer.playArrowTrail(arrow, stats.getSelectedArrowEffect(), plugin);
        }
    }
}