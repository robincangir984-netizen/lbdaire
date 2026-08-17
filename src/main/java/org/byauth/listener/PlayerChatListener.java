package org.byauth.listener;

import org.byauth.ByCircleGame;
import org.byauth.controller.ArenaController;
import org.byauth.game.Arena;
import org.byauth.game.Team;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerChatListener implements Listener {

    private final ByCircleGame plugin;
    private final ArenaController arenaController;

    public PlayerChatListener(ByCircleGame plugin) {
        this.plugin = plugin;
        this.arenaController = plugin.getArenaController();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Arena arena = arenaController.getArenaByPlayerIncludingSpectators(player);

        if (arena == null) {
            return;
        }

        event.setCancelled(true);

        boolean isSpectator = arena.getSpectators().contains(player.getUniqueId());
        Set<Player> recipients = new HashSet<>();
        String format;
        String finalMessage;

        if (isSpectator) {
            for (UUID uuid : arena.getSpectators()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    recipients.add(p);
                }
            }
            format = "&7[İzleyici] %s&7: &f%s";
            finalMessage = plugin.getSettingsManager().format(String.format(format, player.getName(), event.getMessage()));
        } else {
            for (UUID uuid : arena.getPlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    recipients.add(p);
                }
            }
            for (UUID uuid : arena.getSpectators()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    recipients.add(p);
                }
            }

            Team team = arena.getGameManager().getTeamOfPlayer(player);
            if (team != null) {
                format = "&" + team.getChatColor().getChar() + "[T] &r%s&7: &f%s";
            } else {
                format = "%s&7: &f%s";
            }
            finalMessage = plugin.getSettingsManager().format(String.format(format, player.getDisplayName(), event.getMessage()));
        }

        for (Player recipient : recipients) {
            recipient.sendMessage(finalMessage);
        }
    }
}