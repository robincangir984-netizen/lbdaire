package org.byauth.command;

import org.byauth.ByCircleGame;
import org.byauth.controller.ArenaController;
import org.byauth.data.VictoryEffect;
import org.byauth.game.Arena;
import org.byauth.game.Team;
import org.byauth.game.VictoryEffects;
import org.byauth.manager.PlayerDataManager;
import org.byauth.utils.SettingsManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.*;
import java.util.stream.Collectors;

public class AdminCommand implements CommandExecutor {

    private final ByCircleGame plugin;
    private final ArenaController arenaController;
    private final SettingsManager settings;
    private final PlayerDataManager playerDataManager;

    public AdminCommand(ByCircleGame plugin) {
        this.plugin = plugin;
        this.arenaController = plugin.getArenaController();
        this.settings = plugin.getSettingsManager();
        this.playerDataManager = plugin.getPlayerDataManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadPlugin();
            sender.sendMessage(settings.PREFIX + ChatColor.GREEN + "Eklenti başarıyla yeniden yüklendi!");
            return true;
        }

        if (args[0].equalsIgnoreCase("resetarena")) {
            if (args.length < 2) {
                sender.sendMessage(settings.PREFIX + ChatColor.RED + "Kullanım: /daireadmin resetarena <arena_ismi>");
                return true;
            }
            String arenaNameToReset = args[1];
            Arena arenaToReset = arenaController.getArenas().get(arenaNameToReset);
            if (arenaToReset == null) {
                sender.sendMessage(settings.PREFIX + ChatColor.RED + "'" + arenaNameToReset + "' adında bir arena bulunamadı.");
                return true;
            }

            sender.sendMessage(settings.PREFIX + ChatColor.YELLOW + "'" + arenaToReset.getDisplayName() + "' arenası sıfırlanıyor...");

            Set<UUID> allInvolved = new HashSet<>();
            allInvolved.addAll(arenaToReset.getPlayers());
            allInvolved.addAll(arenaToReset.getSpectators());

            for (UUID playerUUID : allInvolved) {
                Player playerInArena = Bukkit.getPlayer(playerUUID);
                if (playerInArena != null) {
                    arenaController.removePlayer(playerInArena);
                    settings.sendMessage(playerInArena, settings.getMessage("errors.arena-force-reset"));
                }
            }

            arenaToReset.getPlayers().clear();
            arenaToReset.getSpectators().clear();
            arenaController.getPlayerArenasMap().values().removeIf(a -> a.equals(arenaToReset));
            arenaController.getSpectatorsMap().values().removeIf(a -> a.equals(arenaToReset));

            arenaToReset.getGameManager().stopGame(false);

            sender.sendMessage(settings.PREFIX + ChatColor.GREEN + "'" + arenaToReset.getDisplayName() + "' arenası başarıyla sıfırlandı.");
            return true;
        }

        if (args[0].equalsIgnoreCase("setmainspawn")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(settings.getMessage("errors.players-only"));
                return true;
            }
            Player player = (Player) sender;
            settings.saveMainSpawnLocation(player.getLocation());
            settings.sendMessage(player, settings.getMessage("commands.admin.set-main-spawn"));
            return true;
        }

        if (args[0].equalsIgnoreCase("givepoints") || args[0].equalsIgnoreCase("takepoints") || args[0].equalsIgnoreCase("setpoints")) {
            handlePointsCommand(sender, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("givecoin") || args[0].equalsIgnoreCase("takecoin") || args[0].equalsIgnoreCase("setcoin")) {
            handleCoinCommand(sender, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("giveallpoints") || args[0].equalsIgnoreCase("giveallcoin")) {
            handleGiveAllCommand(sender, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("testeffect")) {
            handleTestEffectCommand(sender, args);
            return true;
        }

        if (args.length < 2) {
            sendHelpMessage(sender);
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(settings.getMessage("errors.players-only"));
            return true;
        }

        Player player = (Player) sender;
        String subCommand = args[0];
        String arenaName = args[1];
        Arena arena = arenaController.getOrCreateArena(arenaName);

        if (subCommand.equalsIgnoreCase("setlobby") || subCommand.equalsIgnoreCase("setcenter")) {

            World requiredWorld = plugin.getGameWorld(arenaName);
            World playerWorld = player.getWorld();

            if (requiredWorld != null && !playerWorld.equals(requiredWorld)) {
                settings.sendMessage(player, settings.format("&cBu komutu sadece ayrılmış oyun dünyasında ('" + requiredWorld.getName() + "') kullanabilirsiniz."));
                return true;
            }

            if (requiredWorld == null || requiredWorld.equals(playerWorld)) {
                for (Arena existingArena : arenaController.getArenas().values()) {
                    if (existingArena.getCenterLocation() != null) {
                        World existingWorld = existingArena.getCenterLocation().getWorld();
                        if (existingWorld.equals(playerWorld) && !existingArena.getName().equalsIgnoreCase(arenaName)) {
                            settings.sendMessage(player, settings.format("&cBu dünya ('" + playerWorld.getName() + "') zaten &e" + existingArena.getName() + "&c arenası için kullanılıyor!"));
                            return true;
                        }
                    }
                }

                if (requiredWorld == null) {
                    plugin.getLogger().info(arenaName + " arenasına " + playerWorld.getName() + " dünyası atandı.");
                }
            }
        }

        if (subCommand.equalsIgnoreCase("setlobby")) {
            arena.setLobbyLocation(player.getLocation());
            settings.saveArenaLocation(arenaName, "lobby", player.getLocation());
            arenaController.buildLobby(arena);
            settings.sendMessage(player, settings.getMessage("commands.admin.set-lobby").replace("%arena%", arena.getDisplayName()));
        } else if (subCommand.equalsIgnoreCase("setcenter")) {
            if (arena.getCenterLocation() != null && arena.getArenaManager().getCenter() != null) {
                arena.getArenaManager().destroyArena();
            }

            arena.setCenterLocation(player.getLocation());
            settings.saveArenaLocation(arenaName, "center", player.getLocation());

            arena.getArenaManager().generateArena(player.getLocation(), new ArrayList<>(Arrays.asList(Team.values())));
            settings.sendMessage(player, settings.getMessage("commands.admin.set-center-generated").replace("%arena%", arena.getDisplayName()));
        } else if (subCommand.equalsIgnoreCase("setdisplayname")) {
            if (args.length < 3) {
                sendHelpMessage(player);
                return true;
            }
            String displayName = Arrays.stream(args).skip(2).collect(Collectors.joining(" "));
            arena.setDisplayName(displayName);
            settings.saveDisplayName(arenaName, displayName);
            settings.sendMessage(player, settings.getMessage("commands.admin.set-display-name")
                    .replace("%arena%", arenaName)
                    .replace("%displayname%", displayName));
        } else if (subCommand.equalsIgnoreCase("setteam_size")) {
            if (args.length < 3) {
                sendHelpMessage(player);
                return true;
            }
            try {
                int size = Integer.parseInt(args[2]);
                if (size < 1) {
                    settings.sendMessage(player, settings.getMessage("errors.team-size-too-small"));
                    return true;
                }
                arena.setTeamSize(size);
                settings.saveTeamSize(arenaName, size);
                settings.sendMessage(player, settings.getMessage("commands.admin.set-team-size").replace("%arena%", arena.getDisplayName()).replace("%size%", String.valueOf(size)));
            } catch (NumberFormatException e) {
                settings.sendMessage(player, settings.getMessage("errors.invalid-number"));
            }
        } else if (subCommand.equalsIgnoreCase("setrounds")) {
            if (args.length < 3) {
                sendHelpMessage(player);
                return true;
            }
            List<Integer> durations = new ArrayList<>();
            try {
                for (int i = 2; i < args.length; i++) {
                    durations.add(Integer.parseInt(args[i]));
                }
                arena.setRoundDurations(durations);
                settings.saveRoundDurations(arenaName, durations);
                settings.sendMessage(player, settings.getMessage("commands.admin.set-rounds").replace("%arena%", arena.getDisplayName()).replace("%rounds%", durations.toString()));
            } catch (NumberFormatException e) {
                settings.sendMessage(player, settings.getMessage("errors.invalid-number"));
            }
        } else if (subCommand.equalsIgnoreCase("setshulkertimers")) {
            if (args.length < 3) {
                sendHelpMessage(player);
                return true;
            }
            List<Integer> durations = new ArrayList<>();
            try {
                for (int i = 2; i < args.length; i++) {
                    durations.add(Integer.parseInt(args[i]));
                }
                arena.setShulkerDespawnTimers(durations);
                settings.saveShulkerDespawnTimers(arenaName, durations);
                settings.sendMessage(player, settings.getMessage("commands.admin.set-shulker-timers").replace("%arena%", arena.getDisplayName()).replace("%times%", durations.toString()));
            } catch (NumberFormatException e) {
                settings.sendMessage(player, settings.getMessage("errors.invalid-number"));
            }
        } else {
            sendHelpMessage(player);
        }

        return true;
    }

    private void handlePointsCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendHelpMessage(sender);
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(settings.PREFIX + settings.getMessage("errors.player-not-found").replace("%player%", args[1]));
            return;
        }
        try {
            int amount = Integer.parseInt(args[2]);
            String action = args[0].toLowerCase();
            switch (action) {
                case "givepoints":
                    playerDataManager.addPoints(target, amount);
                    sender.sendMessage(settings.PREFIX + ChatColor.GREEN + target.getName() + " adlı oyuncuya " + amount + " puan verildi.");
                    target.sendMessage(settings.PREFIX + ChatColor.GREEN + "Hesabınıza " + amount + " puan eklendi!");
                    break;
                case "takepoints":
                    playerDataManager.addPoints(target, -amount);
                    sender.sendMessage(settings.PREFIX + ChatColor.RED + target.getName() + " adlı oyuncudan " + amount + " puan silindi.");
                    target.sendMessage(settings.PREFIX + ChatColor.RED + "Hesabınızdan " + amount + " puan düşürüldü.");
                    break;
                case "setpoints":
                    playerDataManager.setPoints(target, amount);
                    sender.sendMessage(settings.PREFIX + ChatColor.GREEN + target.getName() + " adlı oyuncunun puanı " + amount + " olarak ayarlandı.");
                    target.sendMessage(settings.PREFIX + ChatColor.GREEN + "Puanınız " + amount + " olarak güncellendi.");
                    break;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(settings.PREFIX + settings.getMessage("errors.invalid-number"));
        }
    }

    private void handleCoinCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendHelpMessage(sender);
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(settings.PREFIX + settings.getMessage("errors.player-not-found").replace("%player%", args[1]));
            return;
        }
        try {
            int amount = Integer.parseInt(args[2]);
            String action = args[0].toLowerCase();
            String coinName = settings.COIN_SYSTEM_NAME;
            switch (action) {
                case "givecoin":
                    playerDataManager.addBCoin(target, amount);
                    sender.sendMessage(settings.PREFIX + ChatColor.GREEN + target.getName() + " adlı oyuncuya " + amount + " " + coinName + " verildi.");
                    target.sendMessage(settings.PREFIX + ChatColor.GREEN + "Hesabınıza " + amount + " " + coinName + " eklendi!");
                    break;
                case "takecoin":
                    playerDataManager.addBCoin(target, -amount);
                    sender.sendMessage(settings.PREFIX + ChatColor.RED + target.getName() + " adlı oyuncudan " + amount + " " + coinName + " silindi.");
                    target.sendMessage(settings.PREFIX + ChatColor.RED + "Hesabınızdan " + amount + " " + coinName + " düşürüldü.");
                    break;
                case "setcoin":
                    playerDataManager.setBCoin(target, amount);
                    sender.sendMessage(settings.PREFIX + ChatColor.GREEN + target.getName() + " adlı oyuncunun " + coinName + " miktarı " + amount + " olarak ayarlandı.");
                    target.sendMessage(settings.PREFIX + ChatColor.GREEN + coinName + " miktarınız " + amount + " olarak güncellendi.");
                    break;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(settings.PREFIX + settings.getMessage("errors.invalid-number"));
        }
    }

    private void handleGiveAllCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendHelpMessage(sender);
            return;
        }
        try {
            int amount = Integer.parseInt(args[1]);
            String action = args[0].toLowerCase();
            String coinName = settings.COIN_SYSTEM_NAME;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (action.equals("giveallpoints")) {
                    playerDataManager.addPoints(p, amount);
                    p.sendMessage(settings.PREFIX + ChatColor.GREEN + "Hesabınıza " + amount + " puan eklendi!");
                } else if (action.equals("giveallcoin")) {
                    playerDataManager.addBCoin(p, amount);
                    p.sendMessage(settings.PREFIX + ChatColor.GREEN + "Hesabınıza " + amount + " " + coinName + " eklendi!");
                }
            }
            if (action.equals("giveallpoints")) {
                sender.sendMessage(settings.PREFIX + ChatColor.GREEN + "Çevrim içi olan tüm oyunculara " + amount + " puan dağıtıldı.");
            } else if (action.equals("giveallcoin")) {
                sender.sendMessage(settings.PREFIX + ChatColor.GREEN + "Çevrim içi olan tüm oyunculara " + amount + " " + coinName + " dağıtıldı.");
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(settings.PREFIX + settings.getMessage("errors.invalid-number"));
        }
    }

    private void handleTestEffectCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendHelpMessage(sender);
            return;
        }
        String effectId = args[1];
        VictoryEffect effect = plugin.getCosmeticManager().getEffectById(effectId);
        if (effect == null) {
            sender.sendMessage(settings.PREFIX + settings.getMessage("errors.effect-not-found").replace("%effect%", effectId));
            return;
        }

        Player target = null;
        if (args.length > 2) {
            target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage(settings.PREFIX + settings.getMessage("errors.player-not-found").replace("%player%", args[2]));
                return;
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
        }

        if (target == null) {
            sender.sendMessage(settings.PREFIX + settings.getMessage("errors.players-only"));
            return;
        }

        switch (effect.getId()) {
            case "yildirim_firtinasi":
                VictoryEffects.playLightningStorm(target, plugin);
                break;
            case "melek_inisi":
                VictoryEffects.playAngelicDescent(target, plugin);
                break;
            case "ejderha_kukremesi":
                VictoryEffects.playDragonRoar(target, plugin);
                break;
            case "cehennem_lordu":
                VictoryEffects.playNetherLord(target, plugin);
                break;
            case "kozmik_hukumdarr":
                VictoryEffects.playCosmicSovereign(target, plugin);
                break;
            case "gardiyanin_gazabi":
                VictoryEffects.playWardensWrath(target, plugin);
                break;
        }
        sender.sendMessage(settings.PREFIX + ChatColor.GREEN + "'" + effect.getDisplayName() + "' efekti " + target.getName() + " üzerinde başarıyla oynatıldı.");
    }

    private void sendHelpMessage(CommandSender sender) {
        for (String line : settings.getMessageList("commands.admin-help")) {
            sender.sendMessage(line);
        }
    }
}