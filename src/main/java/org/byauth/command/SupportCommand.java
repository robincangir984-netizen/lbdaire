package org.byauth.command;

import org.byauth.ByCircleGame;
import org.byauth.controller.ArenaController;
import org.byauth.utils.SettingsManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class SupportCommand implements CommandExecutor {

    private final ByCircleGame plugin;
    private final ArenaController arenaController;
    private final SettingsManager settings;

    public SupportCommand(ByCircleGame plugin) {
        this.plugin = plugin;
        this.arenaController = plugin.getArenaController();
        this.settings = plugin.getSettingsManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Bu komutu sadece oyuncular kullanabilir.");
            return true;
        }

        Player player = (Player) sender;

        if (!arenaController.getSpectatorsMap().containsKey(player.getUniqueId())) {
            player.sendMessage(settings.PREFIX + settings.getMessage("support.not-a-spectator"));
            return true;
        }

        arenaController.openTeammateSupportGui(player);
        return true;
    }
}