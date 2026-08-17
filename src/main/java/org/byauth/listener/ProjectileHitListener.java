package org.byauth.listener;

import org.byauth.ByCircleGame;
import org.byauth.controller.ArenaController;
import org.byauth.game.Arena;
import org.byauth.game.ArenaState;
import org.byauth.game.Team;
import org.byauth.manager.GameManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;

public class ProjectileHitListener implements Listener {

    private final ByCircleGame plugin;
    private final ArenaController arenaController;

    public ProjectileHitListener(ByCircleGame plugin) {
        this.plugin = plugin;
        this.arenaController = plugin.getArenaController();
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player)) {
            return;
        }

        Player shooter = (Player) event.getEntity().getShooter();
        Arena arena = arenaController.getArenaByPlayer(shooter);

        if (arena == null || arena.getState() != ArenaState.ACTIVE) {
            return;
        }

        GameManager gameManager = arena.getGameManager();

        if (!(event.getEntity() instanceof Snowball)) {
            return;
        }

        if (gameManager.getSnowballCooldown().containsKey(shooter.getUniqueId())) {
            handleSpecialProjectileHit(event, shooter, gameManager, arena);
        } else {
            Entity hitEntity = event.getHitEntity();
            if (hitEntity instanceof Player) {
                Player victim = (Player) hitEntity;

                if (arena.equals(arenaController.getArenaByPlayer(victim))) {
                    Team shooterTeam = gameManager.getTeamOfPlayer(shooter);
                    Team victimTeam = gameManager.getTeamOfPlayer(victim);
                    if (shooterTeam != null && shooterTeam != victimTeam) {
                        victim.damage(plugin.getSettingsManager().SNOWBALL_DAMAGE, shooter);
                    }
                }
            }
        }
    }

    private void handleSpecialProjectileHit(ProjectileHitEvent event, Player shooter, GameManager gameManager, Arena arena) {
        long timeSinceThrow = System.currentTimeMillis() - gameManager.getSnowballCooldown().get(shooter.getUniqueId());
        if (timeSinceThrow > 4000) {
            gameManager.getSnowballCooldown().remove(shooter.getUniqueId());
            return;
        }

        Projectile projectile = event.getEntity();
        if (projectile.getType().toString().contains("SNOWBALL")) {
            Block hitBlock = event.getHitBlock();
            if (hitBlock != null) {
                Team shooterTeam = gameManager.getTeamOfPlayer(shooter);
                Team blockTeam = arena.getArenaManager().getTeamFromLocation(hitBlock.getLocation());

                if (shooterTeam != null && blockTeam != null && shooterTeam != blockTeam) {
                    hitBlock.setType(Material.WHITE_CONCRETE);
                }
            }
        }
        else if (projectile.getType().toString().contains("ENDER_PEARL")) {
        }

        gameManager.getSnowballCooldown().remove(shooter.getUniqueId());
    }
}