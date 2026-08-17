package org.byauth.utils;

import org.byauth.ByCircleGame;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SettingsManager {

    private final ByCircleGame plugin;
    private FileConfiguration config;
    private FileConfiguration messages;
    private FileConfiguration worlds;
    private File messagesFile;
    private File worldsFile;

    public String PREFIX;
    public boolean POINTS_ENABLED;
    public int POINTS_ON_KILL;
    public int POINTS_ON_WIN;
    public int POINTS_ON_ROUND_SURVIVE;
    public boolean COIN_SYSTEM_ENABLED;
    public String COIN_SYSTEM_NAME;
    public int COINS_ON_KILL;
    public int COINS_ON_WIN;
    public int COINS_ON_ROUND_SURVIVE;
    public int MIN_PLAYERS_TO_START;
    public int MIN_TEAMS_TO_START;
    public int LOBBY_COUNTDOWN_SECONDS;
    public int INTER_ROUND_COUNTDOWN_SECONDS;
    public int SLOW_FALL_DURATION_TICKS;
    public int FALL_DAMAGE_IMMUNITY_SECONDS;
    public int COBWEB_ELIMINATION_SECONDS;
    public int RESETTING_DURATION_SECONDS;
    public int SUDDEN_DEATH_DURATION_SECONDS;
    public double SNOWBALL_DAMAGE;
    public int INSTANT_TNT_FUSE_TICKS;
    public int CELEBRATION_DURATION_SECONDS;
    public Material TEAM_SELECTOR_MATERIAL;
    public int TEAM_SELECTOR_SLOT;
    public String TEAM_SELECTOR_NAME;
    public List<String> TEAM_SELECTOR_LORE;
    public Material LEAVE_LOBBY_MATERIAL;
    public int LEAVE_LOBBY_SLOT;
    public String LEAVE_LOBBY_NAME;
    public List<String> LEAVE_LOBBY_LORE;
    public int LOBBY_MAIN_ISLAND_RADIUS;
    public double LOBBY_TEAM_ISLAND_DISTANCE;
    public int LOBBY_TEAM_ISLAND_RADIUS;
    public int MAIN_SPAWN_PROTECTION_RADIUS;
    public int ARENA_RADIUS;
    public int ARENA_CENTER_RADIUS;
    public int ARENA_Y_LEVEL;
    public int LAVA_Y_LEVEL;
    public double POD_RADIUS_OFFSET;
    public int POD_Y_OFFSET;
    public Sound PLAYER_JOIN_SOUND;
    public float PLAYER_JOIN_VOLUME;
    public float PLAYER_JOIN_PITCH;
    public Sound PLAYER_LEAVE_SOUND;
    public float PLAYER_LEAVE_VOLUME;
    public float PLAYER_LEAVE_PITCH;
    public Sound COUNTDOWN_TICK_SOUND;
    public float COUNTDOWN_TICK_VOLUME;
    public float COUNTDOWN_TICK_PITCH;
    public Sound LOBBY_START_SOUND;
    public float LOBBY_START_VOLUME;
    public float LOBBY_START_PITCH;
    public Sound GAME_START_SOUND;
    public float GAME_START_VOLUME;
    public float GAME_START_PITCH;
    public Sound TEAM_FULL_SOUND;
    public float TEAM_FULL_VOLUME;
    public float TEAM_FULL_PITCH;

    public SettingsManager(ByCircleGame plugin) {
        this.plugin = plugin;
        loadFiles();
    }

    public void reload() {
        plugin.reloadConfig();
        config = plugin.getConfig();

        messages = YamlConfiguration.loadConfiguration(messagesFile);
        worlds = YamlConfiguration.loadConfiguration(worldsFile);

        loadValues();
    }

    private void loadFiles() {
        plugin.saveDefaultConfig();
        config = plugin.getConfig();

        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);

        worldsFile = new File(plugin.getDataFolder(), "worlds.yml");
        if (!worldsFile.exists()) {
            try {
                worldsFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        worlds = YamlConfiguration.loadConfiguration(worldsFile);

        loadValues();
    }

    private void loadValues() {
        PREFIX = format(config.getString("plugin-prefix", "&6[CircleGame] &r"));

        POINTS_ENABLED = config.getBoolean("points-system.enabled", true);
        POINTS_ON_KILL = config.getInt("points-system.on-kill", 10);
        POINTS_ON_WIN = config.getInt("points-system.on-win", 50);
        POINTS_ON_ROUND_SURVIVE = config.getInt("points-system.on-round-survive", 5);

        COIN_SYSTEM_ENABLED = config.getBoolean("coin-system.enabled", true);
        COIN_SYSTEM_NAME = format(config.getString("coin-system.name", "BCoin"));
        COINS_ON_KILL = config.getInt("coin-system.on-kill", 5);
        COINS_ON_WIN = config.getInt("coin-system.on-win", 25);
        COINS_ON_ROUND_SURVIVE = config.getInt("coin-system.on-round-survive", 2);

        MIN_PLAYERS_TO_START = config.getInt("game-rules.min-players-to-start", 2);
        MIN_TEAMS_TO_START = config.getInt("game-rules.min-teams-to-start", 2);
        LOBBY_COUNTDOWN_SECONDS = config.getInt("game-rules.lobby-countdown-seconds", 15);
        INTER_ROUND_COUNTDOWN_SECONDS = config.getInt("game-rules.inter-round-countdown-seconds", 10);
        SLOW_FALL_DURATION_TICKS = config.getInt("game-rules.slow-fall-duration-ticks", 60);
        FALL_DAMAGE_IMMUNITY_SECONDS = config.getInt("game-rules.fall-damage-immunity-seconds", 10);
        COBWEB_ELIMINATION_SECONDS = config.getInt("game-rules.cobweb-elimination-seconds", 15);
        RESETTING_DURATION_SECONDS = config.getInt("game-rules.resetting-duration-seconds", 10);
        SUDDEN_DEATH_DURATION_SECONDS = config.getInt("game-rules.sudden-death-duration-seconds", 60);
        SNOWBALL_DAMAGE = config.getDouble("game-rules.snowball-damage", 1.0);
        INSTANT_TNT_FUSE_TICKS = config.getInt("game-rules.instant-tnt-fuse-seconds", 3) * 20;
        CELEBRATION_DURATION_SECONDS = config.getInt("game-rules.celebration-duration-seconds", 5);

        try {
            TEAM_SELECTOR_MATERIAL = Material.valueOf(config.getString("lobby.team-selector-item.material", "BARREL").toUpperCase());
        } catch (IllegalArgumentException e) {
            TEAM_SELECTOR_MATERIAL = Material.BARREL;
        }
        TEAM_SELECTOR_SLOT = config.getInt("lobby.team-selector-item.slot", 4);
        TEAM_SELECTOR_NAME = config.getString("lobby.team-selector-item.name", "&aTakım Seç (Sağ Tıkla)");
        TEAM_SELECTOR_LORE = config.getStringList("lobby.team-selector-item.lore");

        try {
            LEAVE_LOBBY_MATERIAL = Material.valueOf(config.getString("lobby.leave-lobby-item.material", "OAK_DOOR").toUpperCase());
        } catch (IllegalArgumentException e) {
            LEAVE_LOBBY_MATERIAL = Material.OAK_DOOR;
        }
        LEAVE_LOBBY_SLOT = config.getInt("lobby.leave-lobby-item.slot", 8);
        LEAVE_LOBBY_NAME = config.getString("lobby.leave-lobby-item.name", "&cLobiden Ayrıl (Sağ Tıkla)");
        LEAVE_LOBBY_LORE = config.getStringList("lobby.leave-lobby-item.lore");

        LOBBY_MAIN_ISLAND_RADIUS = config.getInt("lobby.main-island-radius", 10);
        LOBBY_TEAM_ISLAND_DISTANCE = config.getDouble("lobby.team-island-distance", 15.0);
        LOBBY_TEAM_ISLAND_RADIUS = config.getInt("lobby.team-island-radius", 4);
        MAIN_SPAWN_PROTECTION_RADIUS = config.getInt("lobby.main-spawn-protection-radius", 30);

        ARENA_RADIUS = config.getInt("arena.radius", 24);
        ARENA_CENTER_RADIUS = config.getInt("arena.center-radius", 3);
        ARENA_Y_LEVEL = config.getInt("arena.y-level", 150);
        LAVA_Y_LEVEL = config.getInt("arena.lava-y-level", 130);
        POD_RADIUS_OFFSET = config.getDouble("arena.pod-radius-offset", 2.0);
        POD_Y_OFFSET = config.getInt("arena.pod-y-offset", 15);

        loadSound("sounds.player-join", Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        loadSound("sounds.player-leave", Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.8f);
        loadSound("sounds.lobby-countdown-tick", Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
        loadSound("sounds.lobby-countdown-start", Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        loadSound("sounds.game-start", Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.5f);
        loadSound("sounds.team-select-full", Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
    }

    private void loadSound(String path, Sound defaultSound, float defaultVol, float defaultPitch) {
        String[] soundData = config.getString(path, "").split(",");
        try {
            Sound sound = soundData.length > 0 && !soundData[0].isEmpty() ? Sound.valueOf(soundData[0].toUpperCase()) : defaultSound;
            float volume = soundData.length > 1 ? Float.parseFloat(soundData[1]) : defaultVol;
            float pitch = soundData.length > 2 ? Float.parseFloat(soundData[2]) : defaultPitch;

            if (path.contains("player-join")) { PLAYER_JOIN_SOUND = sound; PLAYER_JOIN_VOLUME = volume; PLAYER_JOIN_PITCH = pitch; }
            if (path.contains("player-leave")) { PLAYER_LEAVE_SOUND = sound; PLAYER_LEAVE_VOLUME = volume; PLAYER_LEAVE_PITCH = pitch; }
            if (path.contains("lobby-countdown-tick")) { COUNTDOWN_TICK_SOUND = sound; COUNTDOWN_TICK_VOLUME = volume; COUNTDOWN_TICK_PITCH = pitch; }
            if (path.contains("lobby-countdown-start")) { LOBBY_START_SOUND = sound; LOBBY_START_VOLUME = volume; LOBBY_START_PITCH = pitch; }
            if (path.contains("game-start")) { GAME_START_SOUND = sound; GAME_START_VOLUME = volume; GAME_START_PITCH = pitch; }
            if (path.contains("team-select-full")) { TEAM_FULL_SOUND = sound; TEAM_FULL_VOLUME = volume; TEAM_FULL_PITCH = pitch; }

        } catch (Exception e) {
            plugin.getLogger().warning("Invalid sound format in config.yml for path: " + path);
        }
    }

    public String getMessage(String path) {
        String message = messages.getString(path);
        if (message == null) {
            return format("&cMessage not found: " + path);
        }
        return format(message);
    }

    public List<String> getMessageList(String path) {
        return messages.getStringList(path).stream().map(this::format).collect(Collectors.toList());
    }

    public void sendMessage(Player player, String message) {
        player.sendMessage(PREFIX + message);
    }

    public String format(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public void saveMainSpawnLocation(Location location) {
        String path = "main-spawn";
        worlds.set(path + ".world", location.getWorld().getName());
        worlds.set(path + ".x", location.getX());
        worlds.set(path + ".y", location.getY());
        worlds.set(path + ".z", location.getZ());
        worlds.set(path + ".yaw", location.getYaw());
        worlds.set(path + ".pitch", location.getPitch());
        saveWorldsFile();
    }

    public Location getMainSpawnLocation() {
        String path = "main-spawn";
        ConfigurationSection section = worlds.getConfigurationSection(path);
        if (section == null) return null;
        String worldName = section.getString("world");
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"), (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
    }

    public void saveArenaLocation(String arenaName, String type, Location location) {
        String path = "arenas." + arenaName;
        if (type.equalsIgnoreCase("lobby") || type.equalsIgnoreCase("center")) {
            worlds.set(path + ".world", location.getWorld().getName());
        }

        worlds.set(path + "." + type + ".x", location.getX());
        worlds.set(path + "." + type + ".y", location.getY());
        worlds.set(path + "." + type + ".z", location.getZ());
        worlds.set(path + "." + type + ".yaw", location.getYaw());
        worlds.set(path + "." + type + ".pitch", location.getPitch());
        saveWorldsFile();
    }

    public Location getArenaLocation(String arenaName, String type) {
        String arenaPath = "arenas." + arenaName;
        ConfigurationSection arenaSection = worlds.getConfigurationSection(arenaPath);
        if (arenaSection == null) return null;

        String worldName = arenaSection.getString("world");
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        ConfigurationSection typeSection = worlds.getConfigurationSection(arenaPath + "." + type);
        if (typeSection == null) return null;

        return new Location(world, typeSection.getDouble("x"), typeSection.getDouble("y"), typeSection.getDouble("z"), (float) typeSection.getDouble("yaw"), (float) typeSection.getDouble("pitch"));
    }

    public void saveTeamSize(String arenaName, int size) {
        worlds.set("arenas." + arenaName + ".team_size", size);
        saveWorldsFile();
    }

    public int getTeamSize(String arenaName) {
        return worlds.getInt("arenas." + arenaName + ".team_size", 1);
    }

    public void saveRoundDurations(String arenaName, List<Integer> durations) {
        worlds.set("arenas." + arenaName + ".round_durations", durations);
        saveWorldsFile();
    }

    public List<Integer> getRoundDurations(String arenaName) {
        return worlds.getIntegerList("arenas." + arenaName + ".round_durations");
    }

    public void saveShulkerDespawnTimers(String arenaName, List<Integer> durations) {
        worlds.set("arenas." + arenaName + ".shulker_despawn_timers", durations);
        saveWorldsFile();
    }

    public List<Integer> getShulkerDespawnTimers(String arenaName) {
        return worlds.getIntegerList("arenas." + arenaName + ".shulker_despawn_timers");
    }

    public void saveDisplayName(String arenaName, String displayName) {
        worlds.set("arenas." + arenaName + ".display-name", displayName);
        saveWorldsFile();
    }

    public String getDisplayName(String arenaName) {
        return worlds.getString("arenas." + arenaName + ".display-name", arenaName);
    }

    public Set<String> getArenaNames() {
        ConfigurationSection section = worlds.getConfigurationSection("arenas");
        if (section == null) return null;
        return section.getKeys(false);
    }

    public String getArenaWorldName(String arenaName) {
        return worlds.getString("arenas." + arenaName + ".world");
    }

    private void saveWorldsFile() {
        try {
            worlds.save(worldsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save worlds.yml file!");
        }
    }
}