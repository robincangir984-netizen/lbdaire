package org.byauth.command;

import org.byauth.ByCircleGame;
import org.byauth.data.VictoryEffect;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class AdminCommandTabCompleter implements TabCompleter {

    private final ByCircleGame plugin;
    private final List<String> subCommands = Arrays.asList(
            "setlobby", "setcenter", "setteam_size", "setrounds", "setshulkertimers", "setdisplayname",
            "reload", "setmainspawn", "givepoints", "takepoints", "setpoints", "giveallpoints",
            "givecoin", "takecoin", "setcoin", "giveallcoin", "testeffect", "resetarena"
    );

    public AdminCommandTabCompleter(ByCircleGame plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return subCommands.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            List<String> arenaSubCommands = Arrays.asList("setlobby", "setcenter", "setteam_size", "setrounds", "setshulkertimers", "setdisplayname", "resetarena");
            if (arenaSubCommands.contains(subCommand)) {
                return plugin.getArenaController().getArenas().keySet().stream()
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }

            List<String> playerSubCommands = Arrays.asList("givepoints", "takepoints", "setpoints", "givecoin", "takecoin", "setcoin");
            if (playerSubCommands.contains(subCommand)) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (subCommand.equals("testeffect")) {
                return plugin.getCosmeticManager().getVictoryEffects().stream()
                        .map(VictoryEffect::getId)
                        .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("testeffect")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
            List<String> amountCommands = Arrays.asList("givepoints", "takepoints", "setpoints", "givecoin", "takecoin", "setcoin", "giveallpoints", "giveallcoin");
            if(amountCommands.contains(subCommand)) {
                return Arrays.asList("10", "100", "1000");
            }
            if (subCommand.equals("setteam_size")) {
                return Arrays.asList("1", "2", "3", "4");
            }
        }

        if (args.length >= 3 && args[0].equalsIgnoreCase("setrounds")) {
            return Arrays.asList("120", "90", "60");
        }

        if (args.length >= 3 && args[0].equalsIgnoreCase("setshulkertimers")) {
            return Arrays.asList("30", "20", "10");
        }

        return new ArrayList<>();
    }
}