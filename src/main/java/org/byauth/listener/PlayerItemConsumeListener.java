package org.byauth.listener;

import org.byauth.ByCircleGame;
import org.byauth.controller.ArenaController;
import org.byauth.game.Arena;
import org.byauth.game.ArenaState;
import org.byauth.game.Team;
import org.byauth.manager.GameManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.stream.Collectors;

public class PlayerItemConsumeListener implements Listener {

    private final ByCircleGame plugin;
    private final ArenaController arenaController;

    public PlayerItemConsumeListener(ByCircleGame plugin) {
        this.plugin = plugin;
        this.arenaController = plugin.getArenaController();
    }

    @EventHandler
    public void onItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        Arena arena = arenaController.getArenaByPlayer(player);

        if (arena == null || arena.getState() != ArenaState.ACTIVE) {
            return;
        }

        GameManager gameManager = arena.getGameManager();
        ItemStack item = event.getItem();

        if (item.getType() == Material.CHORUS_FRUIT && item.hasItemMeta() && item.getItemMeta().getDisplayName().contains("Takım Işınlayıcı")) {
            event.setCancelled(true);

            Team playerTeam = gameManager.getTeamOfPlayer(player);
            if(playerTeam == null) return;

            List<Player> aliveTeammates = gameManager.getPlayersInTeam(playerTeam).stream()
                    .map(Bukkit::getPlayer)
                    .filter(p -> p != null && !p.isDead() && !p.getUniqueId().equals(player.getUniqueId()))
                    .collect(Collectors.toList());

            if (!aliveTeammates.isEmpty()) {
                Player targetTeammate = aliveTeammates.get((int) (Math.random() * aliveTeammates.size()));
                player.teleport(targetTeammate.getLocation());
                player.getInventory().getItemInMainHand().setAmount(player.getInventory().getItemInMainHand().getAmount() - 1);
            }
        }
    }
}