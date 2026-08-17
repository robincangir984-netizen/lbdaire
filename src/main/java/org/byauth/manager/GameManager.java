package org.byauth.manager;

import org.byauth.ByCircleGame;
import org.byauth.data.PlayerStats;
import org.byauth.data.VictoryEffect;
import org.byauth.game.Arena;
import org.byauth.game.ArenaState;
import org.byauth.game.Team;
import org.byauth.game.VictoryEffects;
import org.byauth.utils.SettingsManager;
import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.stream.Collectors;

public class GameManager {

    private final ByCircleGame plugin;
    private final SettingsManager settings;
    private final PlayerDataManager playerDataManager;
    private final Arena arena;
    private final SpecialItemManager specialItemManager;
    private final Map<UUID, Team> playerTeams;
    private final Map<Team, List<UUID>> teams;
    private final Map<Location, Team> playerPlacedBlocks;
    private final Map<Location, BukkitTask> activeAoETraps;
    private final Map<Location, BukkitTask> activeGravityBombs;
    private final Map<UUID, Long> snowballCooldown;
    private final List<Team> teamsInGame = new ArrayList<>();
    private final Map<UUID, Integer> playerInCobwebTicks = new HashMap<>();
    private final Set<UUID> fallDamageImmunity = new HashSet<>();
    private final Set<UUID> invinciblePlayers = new HashSet<>();
    private BukkitTask cobwebTask;
    private BukkitTask roundTimerTask;
    private BukkitTask suddenDeathTask;
    private int currentRound = 0;
    private List<Integer> roundDurations;
    private final Map<Team, Integer> teamSpawnIndex = new HashMap<>();
    private final Set<UUID> waitingPlayers = new HashSet<>();

    public GameManager(ByCircleGame plugin, Arena arena) {
        this.plugin = plugin;
        this.settings = plugin.getSettingsManager();
        this.playerDataManager = plugin.getPlayerDataManager();
        this.arena = arena;
        this.specialItemManager = new SpecialItemManager(plugin, this);
        this.playerTeams = new HashMap<>();
        this.teams = new HashMap<>();
        this.playerPlacedBlocks = new HashMap<>();
        this.activeAoETraps = new HashMap<>();
        this.activeGravityBombs = new HashMap<>();
        this.snowballCooldown = new HashMap<>();
        for (Team team : Team.values()) {
            teams.put(team, new ArrayList<>());
        }
    }

    public void assignPlayerToTeam(Player player, Team newTeam) {
        unassignPlayerFromTeam(player);
        teams.get(newTeam).add(player.getUniqueId());
        playerTeams.put(player.getUniqueId(), newTeam);
    }

    public void unassignPlayerFromTeam(Player player) {
        Team oldTeam = playerTeams.get(player.getUniqueId());
        if (oldTeam != null) {
            teams.get(oldTeam).remove(player.getUniqueId());
            playerTeams.remove(player.getUniqueId());
        }
    }

    public void startGame(Location center) {
        arena.setState(ArenaState.STARTING);
        cleanupBeforeGame();

        this.roundDurations = arena.getRoundDurations();
        if (this.roundDurations == null || this.roundDurations.isEmpty()) {
            this.roundDurations = Arrays.asList(120, 90, 60);
        }

        List<Player> playersInArena = new ArrayList<>();
        for (UUID uuid : arena.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                playersInArena.add(p);
            }
        }

        assignPlayersToTeams(playersInArena);

        teamsInGame.clear();
        for(Map.Entry<Team, List<UUID>> entry : teams.entrySet()){
            if(!entry.getValue().isEmpty()){
                teamsInGame.add(entry.getKey());
            }
        }

        arena.getArenaManager().resetArena(teamsInGame);
        startInterRoundPhase(true);
    }

    private void startInterRoundPhase(boolean isFirstRound) {
        arena.setState(ArenaState.STARTING);

        if (!isFirstRound) {
            List<Player> alivePlayersList = arena.getPlayers().stream()
                    .map(Bukkit::getPlayer)
                    .filter(p -> p != null && !arena.getSpectators().contains(p.getUniqueId()))
                    .collect(Collectors.toList());

            for (Player p : alivePlayersList) {
                playerDataManager.processRoundSurvivedStats(p, settings.POINTS_ON_ROUND_SURVIVE, settings.COINS_ON_ROUND_SURVIVE);
                if (settings.POINTS_ENABLED && settings.POINTS_ON_ROUND_SURVIVE > 0) {
                    String message = settings.getMessage("points.on-round-survive").replace("%points%", String.valueOf(settings.POINTS_ON_ROUND_SURVIVE));
                    p.sendMessage(settings.PREFIX + message);
                }
                if (settings.COIN_SYSTEM_ENABLED && settings.COINS_ON_ROUND_SURVIVE > 0) {
                    String message = settings.getMessage("coin.on-round-survive")
                            .replace("%coins%", String.valueOf(settings.COINS_ON_ROUND_SURVIVE))
                            .replace("%coin_name%", settings.COIN_SYSTEM_NAME);
                    p.sendMessage(settings.PREFIX + message);
                }
            }
        }

        List<Team> activeTeams = teamsInGame.stream().filter(this::isTeamAlive).collect(Collectors.toList());
        arena.getArenaManager().generateWaitingPods(activeTeams);

        if (isFirstRound) {
            for (UUID uuid : arena.getPlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    p.getInventory().clear();
                }
            }
        }

        List<Player> alivePlayers = arena.getPlayers().stream()
                .map(Bukkit::getPlayer)
                .filter(p -> p != null && !arena.getSpectators().contains(p.getUniqueId()))
                .collect(Collectors.toList());

        for (Player p : alivePlayers) {
            Team team = getTeamOfPlayer(p);
            if (team != null && activeTeams.contains(team)) {
                List<UUID> teamMembers = getPlayersInTeam(team);
                int playerIndexInTeam = teamMembers.indexOf(p.getUniqueId());
                teleportPlayerToPodWithOffset(p, team, playerIndexInTeam, teamMembers.size());
            }
        }

        new BukkitRunnable() {
            int countdown = settings.INTER_ROUND_COUNTDOWN_SECONDS;
            @Override
            public void run() {
                if(countdown > 0) {
                    String title = settings.getMessage("titles.inter-round-selecting-title");
                    String subtitle = settings.getMessage("titles.inter-round-selecting-subtitle").replace("%time%", String.valueOf(countdown));
                    Set<UUID> allInvolved = new HashSet<>(arena.getPlayers());
                    allInvolved.addAll(arena.getSpectators());
                    for(UUID uuid : allInvolved) {
                        Player p = Bukkit.getPlayer(uuid);
                        if(p != null && arena.equals(plugin.getArenaController().getArenaByPlayerIncludingSpectators(p))) {
                            p.sendTitle(title, subtitle, 0, 25, 5);
                            p.playSound(p.getLocation(), settings.COUNTDOWN_TICK_SOUND, settings.COUNTDOWN_TICK_VOLUME, settings.COUNTDOWN_TICK_PITCH);
                        }
                    }
                    countdown--;
                } else {
                    this.cancel();
                    startNextRound();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void startNextRound() {
        currentRound++;
        arena.getArenaManager().placeCenterLootContainers();
        arena.getArenaManager().fillLootContainers(currentRound);
        arena.setState(ArenaState.ACTIVE);
        handleStaggeredSpawning();

        List<Integer> despawnTimers = arena.getShulkerDespawnTimers();
        int despawnTime = 30;

        if (despawnTimers != null && !despawnTimers.isEmpty()) {
            if (currentRound - 1 < despawnTimers.size()) {
                despawnTime = despawnTimers.get(currentRound - 1);
            } else {
                despawnTime = despawnTimers.get(despawnTimers.size() - 1);
            }
        }

        if (despawnTime > 0) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (arena.getState() == ArenaState.ACTIVE) {
                        arena.getArenaManager().clearLootContainers();
                        plugin.getArenaController().broadcastArenaMessage(arena, settings.getMessage("game.loot-despawned"));
                    }
                }
            }.runTaskLater(plugin, despawnTime * 20L);
        }

        startRoundTimer();
        if (currentRound == 1) {
            startCobwebTask();
        }
    }

    private void handleStaggeredSpawning() {
        int playersPerSpawnWave = 2;
        int spawnDelayTicks = 15 * 20;
        waitingPlayers.clear();

        for (Team team : teamsInGame) {
            List<Player> aliveTeamMembers = getPlayersInTeam(team).stream()
                    .map(Bukkit::getPlayer)
                    .filter(p -> p != null && !arena.getSpectators().contains(p.getUniqueId()))
                    .collect(Collectors.toList());

            if (aliveTeamMembers.isEmpty()) continue;

            aliveTeamMembers.forEach(p -> waitingPlayers.add(p.getUniqueId()));

            String title = settings.getMessage("titles.spawner-selected-title");
            String subtitle;

            if (aliveTeamMembers.size() == 1) {
                Player spawner = aliveTeamMembers.get(0);
                subtitle = settings.getMessage("titles.spawner-selected-subtitle-solo").replace("%spawner%", spawner.getName());
                spawnPlayerAfterDelay(spawner, 0);
            } else if (aliveTeamMembers.size() == 2) {
                Player spawner = (currentRound % 2 == 1) ? aliveTeamMembers.get(0) : aliveTeamMembers.get(1);
                Player waiter = (currentRound % 2 == 1) ? aliveTeamMembers.get(1) : aliveTeamMembers.get(0);

                subtitle = settings.getMessage("titles.spawner-selected-subtitle")
                        .replace("%spawner%", spawner.getName())
                        .replace("%waiter%", waiter.getName());

                spawnPlayerAfterDelay(spawner, 0);
                spawnPlayerAfterDelay(waiter, spawnDelayTicks);
            } else {
                int currentIndex = teamSpawnIndex.getOrDefault(team, 0);
                Player spawner1 = aliveTeamMembers.get(currentIndex % aliveTeamMembers.size());
                Player spawner2 = aliveTeamMembers.get((currentIndex + 1) % aliveTeamMembers.size());

                String spawners = spawner1.getName() + ", " + spawner2.getName();
                subtitle = settings.getMessage("titles.spawner-selected-subtitle-multi").replace("%spawners%", spawners);

                spawnPlayerAfterDelay(spawner1, 0);
                spawnPlayerAfterDelay(spawner2, spawnDelayTicks);

                teamSpawnIndex.put(team, (currentIndex + playersPerSpawnWave) % aliveTeamMembers.size());
            }

            for (Player member : aliveTeamMembers) {
                if (arena.equals(plugin.getArenaController().getArenaByPlayerIncludingSpectators(member))) {
                    member.sendTitle(title, subtitle, 10, 60, 10);
                    member.playSound(member.getLocation(), settings.GAME_START_SOUND, settings.GAME_START_VOLUME, settings.GAME_START_PITCH);
                }
            }
        }
    }

    private void spawnPlayerAfterDelay(Player player, long delayTicks) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline() && !arena.getSpectators().contains(player.getUniqueId())) {

                    waitingPlayers.remove(player.getUniqueId());

                    Location podLocation = player.getLocation();
                    Location centerLocation = arena.getArenaManager().getCenter();
                    Vector direction = centerLocation.toVector().subtract(podLocation.toVector()).normalize();

                    Location spawnLocation = podLocation.clone().add(direction.multiply(2.1));
                    spawnLocation.subtract(0, 2, 0);
                    spawnLocation.setDirection(direction);

                    player.teleport(spawnLocation);

                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, settings.SLOW_FALL_DURATION_TICKS, 0));
                    fallDamageImmunity.add(player.getUniqueId());
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            fallDamageImmunity.remove(player.getUniqueId());
                        }
                    }.runTaskLater(plugin, settings.FALL_DAMAGE_IMMUNITY_SECONDS * 20L);
                }
            }
        }.runTaskLater(plugin, delayTicks);
    }

    private void startRoundTimer() {
        if (currentRound > roundDurations.size()) {
            startSuddenDeath();
            return;
        }
        int duration = roundDurations.get(currentRound - 1);
        roundTimerTask = new BukkitRunnable() {
            int timeLeft = duration;
            @Override
            public void run() {
                if (timeLeft <= 0 || arena.getState() != ArenaState.ACTIVE) {
                    this.cancel();
                    endRound();
                    return;
                }

                Set<UUID> allInvolved = new HashSet<>(arena.getPlayers());
                allInvolved.addAll(arena.getSpectators());
                for(UUID uuid : allInvolved) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        p.setExp((float) timeLeft / duration);
                        p.setLevel(timeLeft);
                    }
                }
                timeLeft--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void endRound() {
        if (arena.getState() == ArenaState.ENDING || arena.getState() == ArenaState.RESETTING) {
            return;
        }

        if (roundTimerTask != null) {
            roundTimerTask.cancel();
            roundTimerTask = null;
        }

        if (checkWinCondition()) {
            return;
        }

        if (currentRound >= roundDurations.size()) {
            startSuddenDeath();
        } else {
            startInterRoundPhase(false);
        }
    }

    private void startSuddenDeath() {
        arena.setState(ArenaState.STARTING);

        List<Team> survivingTeams = teamsInGame.stream()
                .filter(this::isTeamAlive)
                .collect(Collectors.toList());

        arena.getArenaManager().generateWaitingPods(survivingTeams);

        List<Player> alivePlayers = arena.getPlayers().stream()
                .map(Bukkit::getPlayer)
                .filter(p -> p != null && !arena.getSpectators().contains(p.getUniqueId()))
                .collect(Collectors.toList());

        for (Player p : alivePlayers) {
            Team team = getTeamOfPlayer(p);
            if (team != null) {
                List<UUID> teamMembers = getPlayersInTeam(team);
                int playerIndexInTeam = teamMembers.indexOf(p.getUniqueId());
                teleportPlayerToPodWithOffset(p, team, playerIndexInTeam, teamMembers.size());
            }
        }

        new BukkitRunnable() {
            int countdown = settings.INTER_ROUND_COUNTDOWN_SECONDS;
            @Override
            public void run() {
                if (countdown > 0) {
                    String title = settings.getMessage("titles.sudden-death-title");
                    String subtitle = settings.getMessage("game.inter-round-countdown").replace("%time%", String.valueOf(countdown));
                    Set<UUID> allInvolved = new HashSet<>(arena.getPlayers());
                    allInvolved.addAll(arena.getSpectators());
                    for(UUID uuid : allInvolved) {
                        Player p = Bukkit.getPlayer(uuid);
                        if(p != null && arena.equals(plugin.getArenaController().getArenaByPlayerIncludingSpectators(p))) {
                            p.sendTitle(title, subtitle, 0, 25, 5);
                            p.playSound(p.getLocation(), settings.COUNTDOWN_TICK_SOUND, settings.COUNTDOWN_TICK_VOLUME, settings.COUNTDOWN_TICK_PITCH);
                        }
                    }
                    countdown--;
                } else {
                    this.cancel();
                    beginSuddenDeathFall();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void beginSuddenDeathFall() {
        arena.getArenaManager().setAllSlicesToWhite();
        arena.getArenaManager().clearWaitingPods();
        arena.setState(ArenaState.ACTIVE);

        Set<UUID> allInvolved = new HashSet<>(arena.getPlayers());
        allInvolved.addAll(arena.getSpectators());
        for (UUID uuid : allInvolved) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && arena.equals(plugin.getArenaController().getArenaByPlayerIncludingSpectators(p))) {
                p.sendTitle(settings.getMessage("titles.sudden-death-title"), settings.getMessage("titles.sudden-death-subtitle"), 10, 60, 10);
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);
                if (!arena.getSpectators().contains(p.getUniqueId())) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, settings.SLOW_FALL_DURATION_TICKS, 0));
                }
            }
        }

        World world = arena.getArenaManager().getCenter().getWorld();
        WorldBorder border = world.getWorldBorder();
        border.setCenter(arena.getArenaManager().getCenter());
        border.setSize(settings.ARENA_RADIUS * 2.0);
        border.setDamageAmount(2.0);
        border.setDamageBuffer(0);
        border.setSize(1, settings.SUDDEN_DEATH_DURATION_SECONDS);
    }

    private void startCobwebTask() {
        cobwebTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (arena.getState() != ArenaState.ACTIVE) {
                    this.cancel();
                    return;
                }

                for (UUID uuid : new ArrayList<>(playerInCobwebTicks.keySet())) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player == null || player.getLocation().getBlock().getType() != Material.COBWEB) {
                        playerInCobwebTicks.remove(uuid);
                    }
                }

                for (UUID uuid : arena.getPlayers()) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player == null || arena.getSpectators().contains(uuid)) {
                        playerInCobwebTicks.remove(uuid);
                        continue;
                    }

                    if (player.getLocation().getBlock().getType() == Material.COBWEB) {
                        int ticks = playerInCobwebTicks.getOrDefault(uuid, 0) + 20;
                        playerInCobwebTicks.put(uuid, ticks);

                        if (ticks >= settings.COBWEB_ELIMINATION_SECONDS * 20) {
                            settings.sendMessage(player, settings.getMessage("game.cobweb-elimination"));
                            player.setHealth(0.0);
                            playerInCobwebTicks.remove(uuid);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public void stopGame(boolean broadcast) {
        arena.setState(ArenaState.RESETTING);
        if (broadcast) {
            plugin.getServer().getScheduler().runTaskLater(plugin, this::resetArena, 200L);
        } else {
            resetArena();
        }
    }

    private void resetArena() {
        if (arena.getArenaManager().getCenter() != null) {
            arena.getArenaManager().getCenter().getWorld().getWorldBorder().reset();
        }

        Set<UUID> allInvolved = new HashSet<>(arena.getPlayers());
        allInvolved.addAll(arena.getSpectators());

        for (UUID uuid : allInvolved) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                plugin.getArenaController().removePlayer(p);
                if (plugin.getRankManager() != null) {
                    plugin.getRankManager().checkAndPromote(p);
                }
            }
        }

        arena.getArenaManager().resetArena(new ArrayList<>());

        arena.getPlayers().clear();
        arena.getSpectators().clear();

        cleanupBeforeGame();
        playerTeams.clear();
        teams.values().forEach(List::clear);

        new BukkitRunnable() {
            @Override
            public void run() {
                if(arena.getState() == ArenaState.RESETTING) {
                    arena.setState(ArenaState.WAITING);
                }
            }
        }.runTaskLater(plugin, settings.RESETTING_DURATION_SECONDS * 20L);
    }

    private void cleanupBeforeGame() {
        if (roundTimerTask != null) {
            roundTimerTask.cancel();
            roundTimerTask = null;
        }
        if (cobwebTask != null) {
            cobwebTask.cancel();
            cobwebTask = null;
        }
        if (suddenDeathTask != null) {
            suddenDeathTask.cancel();
            suddenDeathTask = null;
        }
        currentRound = 0;
        specialItemManager.cancelAllEffects();
        playerPlacedBlocks.clear();
        activeAoETraps.values().forEach(BukkitTask::cancel);
        activeAoETraps.clear();
        activeGravityBombs.values().forEach(BukkitTask::cancel);
        activeGravityBombs.clear();
        snowballCooldown.clear();
        teamsInGame.clear();
        playerInCobwebTicks.clear();
        fallDamageImmunity.clear();
        invinciblePlayers.clear();
        teamSpawnIndex.clear();
        waitingPlayers.clear();
    }

    private void assignPlayersToTeams(List<Player> playersInArena) {
        teams.values().forEach(List::clear);
        Map<Team, List<Player>> preselectedTeams = new HashMap<>();
        List<Player> unassignedPlayers = new ArrayList<>();

        for (Player player : playersInArena) {
            Team preselected = playerTeams.get(player.getUniqueId());
            if (preselected != null) {
                if (teams.getOrDefault(preselected, new ArrayList<>()).size() < arena.getTeamSize()) {
                    preselectedTeams.computeIfAbsent(preselected, k -> new ArrayList<>()).add(player);
                } else {
                    unassignedPlayers.add(player);
                }
            } else {
                unassignedPlayers.add(player);
            }
        }

        for (Map.Entry<Team, List<Player>> entry : preselectedTeams.entrySet()) {
            for (Player player : entry.getValue()) {
                teams.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(player.getUniqueId());
            }
        }

        if (!unassignedPlayers.isEmpty()) {
            List<Team> availableTeams = Arrays.asList(Team.values());
            Collections.shuffle(availableTeams);

            for (Player player : unassignedPlayers) {
                Team bestTeam = availableTeams.stream()
                        .filter(team -> teams.getOrDefault(team, new ArrayList<>()).size() < arena.getTeamSize())
                        .min(Comparator.comparingInt(team -> teams.getOrDefault(team, new ArrayList<>()).size()))
                        .orElse(null);

                if (bestTeam != null) {
                    playerTeams.put(player.getUniqueId(), bestTeam);
                    teams.get(bestTeam).add(player.getUniqueId());
                } else {
                    Team fallbackTeam = availableTeams.stream()
                            .min(Comparator.comparingInt(team -> teams.getOrDefault(team, new ArrayList<>()).size()))
                            .orElse(Team.GRAY);

                    playerTeams.put(player.getUniqueId(), fallbackTeam);
                    teams.get(fallbackTeam).add(player.getUniqueId());
                }
            }
        }
    }

    private void teleportPlayerToPodWithOffset(Player player, Team team, int index, int total) {
        Location podCenter = arena.getArenaManager().getPodLocation(team);
        if (total > 1) {
            double angle = (2 * Math.PI / total) * index;
            double radius = 0.8;
            podCenter.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
        }
        player.teleport(podCenter);
    }

    public void removePlayerFromGame(Player player) {
        Team team = getTeamOfPlayer(player);
        if (team != null) {
            if (arena.getState() == ArenaState.ACTIVE) {
                if (!isTeamAlive(team)) {
                    arena.getArenaManager().convertTeamSliceToWhite(team);
                }
                checkWinCondition();
            }
        }
    }

    public boolean checkWinCondition() {
        if (arena.getState() != ArenaState.ACTIVE && arena.getState() != ArenaState.STARTING) {
            return false;
        }

        List<UUID> alivePlayers = arena.getPlayers().stream()
                .filter(uuid -> !arena.getSpectators().contains(uuid))
                .collect(Collectors.toList());

        long aliveTeamsCount = alivePlayers.stream()
                .map(this::getTeamOfPlayerByUUID)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        if (alivePlayers.size() <= 1 || aliveTeamsCount <= 1) {
            if (roundTimerTask != null) {
                roundTimerTask.cancel();
                roundTimerTask = null;
            }
            if (suddenDeathTask != null) {
                suddenDeathTask.cancel();
                suddenDeathTask = null;
            }

            Team winnerTeam = null;
            if (!alivePlayers.isEmpty()) {
                Player winner = Bukkit.getPlayer(alivePlayers.get(0));
                if (winner != null) {
                    winnerTeam = getTeamOfPlayer(winner);
                }
            }

            startCelebration(winnerTeam);
            return true;
        }

        return false;
    }

    private void startCelebration(Team winnerTeam) {
        arena.setState(ArenaState.ENDING);

        String winMessage;
        String winnerNames = "Yok";
        List<Player> winners = new ArrayList<>();

        if (winnerTeam != null) {
            winMessage = settings.getMessage("game.win-message")
                    .replace("%team_color%", winnerTeam.getChatColor().toString())
                    .replace("%team_name%", winnerTeam.getDisplayName());

            winners = getPlayersInTeam(winnerTeam).stream()
                    .map(Bukkit::getPlayer)
                    .filter(p -> p != null && !arena.getSpectators().contains(p.getUniqueId()))
                    .collect(Collectors.toList());


            winnerNames = winners.stream()
                    .map(Player::getName)
                    .collect(Collectors.joining(", "));

            for (Player p : winners) {
                playerDataManager.processWinStats(p, settings.POINTS_ON_WIN, settings.COINS_ON_WIN);
                if (settings.POINTS_ENABLED && settings.POINTS_ON_WIN > 0) {
                    String message = settings.getMessage("points.on-win").replace("%points%", String.valueOf(settings.POINTS_ON_WIN));
                    p.sendMessage(settings.PREFIX + message);
                }
                if (settings.COIN_SYSTEM_ENABLED && settings.COINS_ON_WIN > 0) {
                    String message = settings.getMessage("coin.on-win")
                            .replace("%coins%", String.valueOf(settings.COINS_ON_WIN))
                            .replace("%coin_name%", settings.COIN_SYSTEM_NAME);
                    p.sendMessage(settings.PREFIX + message);
                }
                invinciblePlayers.add(p.getUniqueId());
            }
        } else {
            winMessage = settings.getMessage("game.draw-message");
        }

        Set<UUID> allInvolvedPlayersForStats = new HashSet<>();
        for (List<UUID> playerList : teams.values()) {
            allInvolvedPlayersForStats.addAll(playerList);
        }
        allInvolvedPlayersForStats.removeAll(invinciblePlayers);

        for (UUID loserUUID : allInvolvedPlayersForStats) {
            Player p = Bukkit.getPlayer(loserUUID);
            if (p != null) {
                playerDataManager.incrementLosses(p);
            }
        }

        plugin.getArenaController().broadcastArenaMessage(arena, winMessage);

        Set<UUID> allInvolvedPlayers = new HashSet<>(arena.getPlayers());
        allInvolvedPlayers.addAll(arena.getSpectators());

        for (UUID uuid : allInvolvedPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && arena.equals(plugin.getArenaController().getArenaByPlayerIncludingSpectators(p))) {
                playWinMusic(p);
            }
        }

        for (Player winner : winners) {
            Location winnerLoc = winner.getLocation();
            if (winnerLoc.getY() < arena.getArenaManager().getArenaY()) {
                winnerLoc.setY(arena.getArenaManager().getArenaY());
            }
            winner.teleport(winnerLoc.add(0, 0.5, 0));

            PlayerStats stats = playerDataManager.getStats(winner.getUniqueId());
            if (stats != null && stats.getSelectedCosmetic() != null) {
                String cosmeticId = stats.getSelectedCosmetic();
                switch (cosmeticId) {
                    case "yildirim_firtinasi":
                        VictoryEffects.playLightningStorm(winner, plugin);
                        break;
                    case "melek_inisi":
                        VictoryEffects.playAngelicDescent(winner, plugin);
                        break;
                    case "ejderha_kukremesi":
                        VictoryEffects.playDragonRoar(winner, plugin);
                        break;
                    case "cehennem_lordu":
                        VictoryEffects.playNetherLord(winner, plugin);
                        break;
                    case "kozmik_hukumdarr":
                        VictoryEffects.playCosmicSovereign(winner, plugin);
                        break;
                    case "gardiyanin_gazabi":
                        VictoryEffects.playWardensWrath(winner, plugin);
                        break;
                    default:
                        spawnFireworks(winner.getLocation());
                        break;
                }
            } else {
                spawnFireworks(winner.getLocation());
            }
        }

        String finalWinnerNames = winnerNames;
        new BukkitRunnable() {
            int duration = settings.CELEBRATION_DURATION_SECONDS;

            @Override
            public void run() {
                if (duration <= 0) {
                    stopGame(true);
                    this.cancel();
                    return;
                }

                for (UUID uuid : allInvolvedPlayers) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && arena.equals(plugin.getArenaController().getArenaByPlayerIncludingSpectators(p))) {
                        p.sendTitle(settings.getMessage("titles.winner-title"), finalWinnerNames, 0, 40, 20);
                    }
                }
                duration--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void spawnFireworks(Location location) {
        Location loc = location.clone();
        Firework fw = (Firework) loc.getWorld().spawnEntity(loc, EntityType.FIREWORK_ROCKET);
        FireworkMeta fwm = fw.getFireworkMeta();

        fwm.setPower(1);
        fwm.addEffect(FireworkEffect.builder()
                .withColor(Color.LIME, Color.GREEN, Color.AQUA)
                .flicker(true)
                .trail(true)
                .with(FireworkEffect.Type.BALL_LARGE)
                .build());

        fw.setFireworkMeta(fwm);
    }

    private void playWinMusic(Player player) {
        new BukkitRunnable() {
            int note = 0;
            float[] pitches = {0.594604f, 0.707107f, 0.840896f, 1.059463f, 1.0f, 1.189207f, 1.414214f, 1.587401f};
            @Override
            public void run() {
                if (note >= pitches.length * 2) {
                    this.cancel();
                    return;
                }

                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 1.0f, pitches[note % pitches.length]);
                note++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    public Arena getArena() {
        return this.arena;
    }

    public boolean hasFallDamageImmunity(Player player) {
        return fallDamageImmunity.contains(player.getUniqueId());
    }

    public Set<UUID> getInvinciblePlayers() {
        return invinciblePlayers;
    }

    public boolean isPlayerWaiting(Player player) {
        return waitingPlayers.contains(player.getUniqueId());
    }

    public boolean isSuddenDeath() {
        if (roundDurations == null || roundDurations.isEmpty()) {
            return currentRound >= 3;
        }
        return currentRound >= roundDurations.size();
    }

    public boolean isTeamAlive(Team team) {
        List<UUID> teamPlayers = teams.get(team);
        if (teamPlayers == null || teamPlayers.isEmpty()) {
            return false;
        }
        for (UUID uuid : teamPlayers) {
            if (!arena.getSpectators().contains(uuid)) {
                return true;
            }
        }
        return false;
    }

    public Team getTeamOfPlayerByUUID(UUID uuid) {
        return playerTeams.get(uuid);
    }

    public Team getTeamOfPlayer(Player player) {
        return playerTeams.get(player.getUniqueId());
    }

    public List<UUID> getPlayersInTeam(Team team) {
        return teams.getOrDefault(team, new ArrayList<>());
    }

    public SpecialItemManager getSpecialItemManager() {
        return specialItemManager;
    }

    public Map<Team, List<UUID>> getTeams() {
        return teams;
    }

    public Map<UUID, Team> getPlayerTeams() {
        return playerTeams;
    }

    public Map<Location, Team> getPlayerPlacedBlocks() {
        return playerPlacedBlocks;
    }

    public Map<Location, BukkitTask> getActiveAoETraps() {
        return activeAoETraps;
    }

    public Map<Location, BukkitTask> getActiveGravityBombs() {
        return activeGravityBombs;
    }

    public Map<UUID, Long> getSnowballCooldown() {
        return snowballCooldown;
    }
}