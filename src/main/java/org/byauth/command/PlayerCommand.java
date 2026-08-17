package org.byauth.command;

import org.byauth.ByCircleGame;
import org.byauth.controller.ArenaController;
import org.byauth.utils.SettingsManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PlayerCommand implements CommandExecutor {

    private final ByCircleGame plugin;
    private final ArenaController arenaController;
    private final SettingsManager settings;

    public PlayerCommand(ByCircleGame plugin) {
        this.plugin = plugin;
        this.arenaController = plugin.getArenaController();
        this.settings = plugin.getSettingsManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(settings.format(settings.getMessage("errors.players-only")));
            return true;
        }

        Player player = (Player) sender;

        if (!plugin.isReady()) {
            player.sendMessage(settings.PREFIX + settings.format(settings.getMessage("errors.world-not-found")
                    .replace("%world%", "ayarlanmış arena dünyaları")));
            return true;
        }

        if (args.length == 0) {
            arenaController.openLobbyGui(player);
            settings.sendMessage(player, settings.getMessage("commands.player.gui-opened"));
            return true;
        }

        if (args[0].equalsIgnoreCase("ayril") || args[0].equalsIgnoreCase("leave")) {
            if (arenaController.getArenaByPlayerIncludingSpectators(player) == null) {
                player.sendMessage(settings.PREFIX + settings.getMessage("errors.not-in-any-arena"));
                return true;
            }
            arenaController.removePlayer(player);
        } else {
            sendHelpMessage(player);
        }

        return true;
    }

    private void sendHelpMessage(CommandSender sender) {
        for (String line : settings.getMessageList("commands.player-help")) {
            sender.sendMessage(settings.format(line));
        }
    }
}