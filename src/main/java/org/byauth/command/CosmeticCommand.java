package org.byauth.command;

import org.byauth.ByCircleGame;
import org.byauth.controller.ArenaController;
import org.byauth.manager.CosmeticManager;
import org.byauth.utils.SettingsManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class CosmeticCommand implements CommandExecutor {

    private final CosmeticManager cosmeticManager;
    private final ArenaController arenaController;
    private final SettingsManager settingsManager;

    public CosmeticCommand(ByCircleGame plugin) {
        this.cosmeticManager = plugin.getCosmeticManager();
        this.arenaController = plugin.getArenaController();
        this.settingsManager = plugin.getSettingsManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Bu komutu sadece oyuncular kullanabilir.");
            return true;
        }

        Player player = (Player) sender;

        if (arenaController.getArenaByPlayerIncludingSpectators(player) != null) {
            player.sendMessage(settingsManager.PREFIX + settingsManager.format("&cBu komutu bir arena içindeyken kullanamazsın."));
            return true;
        }

        cosmeticManager.openCosmeticGui(player);
        return true;
    }
}