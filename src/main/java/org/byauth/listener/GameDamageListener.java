package org.byauth.listener;

import org.byauth.ByCircleGame;
import org.byauth.game.Arena;
import org.byauth.game.ArenaState;
import org.byauth.game.Team;
import org.byauth.manager.GameManager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

public class GameDamageListener implements Listener {

    private final ByCircleGame plugin;

    public GameDamageListener(ByCircleGame plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();
        Arena arena = plugin.getArenaController().getArenaByPlayer(player);

        if (arena == null) {
            return;
        }

        GameManager gameManager = arena.getGameManager();
        ArenaState state = arena.getState();

        if (state == ArenaState.STARTING || state == ArenaState.WAITING || state == ArenaState.COUNTDOWN) {
            event.setCancelled(true);
            return;
        }

        if ((state == ArenaState.ENDING || state == ArenaState.RESETTING) && gameManager.getInvinciblePlayers().contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            if (gameManager.hasFallDamageImmunity(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player victim = (Player) event.getEntity();
        Arena arena = plugin.getArenaController().getArenaByPlayer(victim);

        if (arena == null) {
            return;
        }

        Player attacker = null;

        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile) event.getDamager();
            if (projectile.getShooter() instanceof Player) {
                attacker = (Player) projectile.getShooter();
            }
        }

        if (attacker == null || attacker.equals(victim)) {
            return;
        }

        Arena attackerArena = plugin.getArenaController().getArenaByPlayer(attacker);
        if (attackerArena == null || attackerArena != arena || arena.getState() != ArenaState.ACTIVE) {
            event.setCancelled(true);
            return;
        }

        if (!attacker.getUniqueId().equals(victim.getUniqueId())) {
            GameManager gameManager = arena.getGameManager();
            Team victimTeam = gameManager.getTeamOfPlayer(victim);
            Team attackerTeam = gameManager.getTeamOfPlayer(attacker);

            if (victimTeam != null && victimTeam.equals(attackerTeam)) {
                event.setCancelled(true);
            }
        }
    }
}