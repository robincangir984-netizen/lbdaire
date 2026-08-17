package org.byauth.controller;

import org.byauth.ByCircleGame;
import org.byauth.data.PlayerStats;
import org.byauth.data.ShopItem;
import org.byauth.game.Arena;
import org.byauth.game.ArenaState;
import org.byauth.game.Team;
import org.byauth.manager.GameManager;
import org.byauth.manager.ShopManager;
import org.byauth.utils.ItemBuilder;
import org.byauth.utils.SettingsManager;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.stream.Collectors;

public class ArenaController {

    private final ByCircleGame plugin;
    private final SettingsManager settings;
    private final Map<String, Arena> arenas = new HashMap<>();
    private final Map<UUID, Arena> playerArenas = new HashMap<>();
    private final Map<UUID, Arena> spectators = new HashMap<>();
    private final Map<UUID, UUID> supportSelections = new HashMap<>();

    public ArenaController(ByCircleGame plugin) {
        this.plugin = plugin;
        this.settings = plugin.getSettingsManager();
        loadArenas();
    }

    public void loadArenas() {
        if (settings.getArenaNames() == null) return;

        List<String> arenasToClear = new ArrayList<>(arenas.keySet());

        for (String arenaName : settings.getArenaNames()) {
            Location lobby = settings.getArenaLocation(arenaName, "lobby");
            Location center = settings.getArenaLocation(arenaName, "center");

            if (lobby != null && lobby.getWorld() != null) {
                Arena arena = arenas.computeIfAbsent(arenaName, n -> new Arena(n, plugin, lobby, center));
                arena.setLobbyLocation(lobby);
                arena.setCenterLocation(center);
                arena.setTeamSize(settings.getTeamSize(arenaName));
                arena.setRoundDurations(settings.getRoundDurations(arenaName));
                arena.setShulkerDespawnTimers(settings.getShulkerDespawnTimers(arenaName));
                arena.setDisplayName(settings.getDisplayName(arenaName));

                arenasToClear.remove(arenaName);
            } else {
                plugin.getLogger().warning("Arena '" + arenaName + "' için lobi konumu veya dünyası bulunamadı/yüklü değil. Arena devre dışı.");
            }
        }

        arenasToClear.forEach(arenas::remove);
    }

    public Arena getOrCreateArena(String name) {
        return arenas.computeIfAbsent(name, n -> {
            Arena arena = new Arena(n, plugin, null, null);
            arena.setTeamSize(settings.getTeamSize(name));
            arena.setRoundDurations(settings.getRoundDurations(name));
            arena.setShulkerDespawnTimers(settings.getShulkerDespawnTimers(name));
            arena.setDisplayName(settings.getDisplayName(name));
            return arena;
        });
    }

    public void addPlayer(Player player, String arenaName) {
        Arena currentArena = getArenaByPlayerIncludingSpectators(player);
        if (currentArena != null) {
            removePlayer(player);
        }

        Arena arena = arenas.get(arenaName);
        if (arena == null) {
            settings.sendMessage(player, settings.getMessage("errors.arena-not-found"));
            return;
        }

        if (arena.getState() != ArenaState.WAITING && arena.getState() != ArenaState.COUNTDOWN) {
            settings.sendMessage(player, settings.getMessage("errors.arena-in-game"));
            return;
        }

        int maxPlayers = Team.values().length * arena.getTeamSize();
        if(arena.getPlayers().size() >= maxPlayers) {
            settings.sendMessage(player, settings.getMessage("errors.arena-full"));
            return;
        }

        if (arena.getLobbyLocation() == null || arena.getCenterLocation() == null) {
            settings.sendMessage(player, settings.getMessage("errors.arena-not-ready"));
            return;
        }

        arena.getPlayers().add(player.getUniqueId());
        playerArenas.put(player.getUniqueId(), arena);

        Location spawnLocation = arena.getLobbyLocation().clone().add(0.5, 2, 0.5);
        player.teleport(spawnLocation);
        player.setGameMode(GameMode.SURVIVAL);

        giveLobbyItems(player);
        updatePlayerVisibility(player);

        String joinMessage = settings.getMessage("lobby.player-join")
                .replace("%player%", player.getName())
                .replace("%current%", String.valueOf(arena.getPlayers().size()))
                .replace("%max%", String.valueOf(maxPlayers));
        broadcastArenaMessage(arena, joinMessage);

        for (UUID uuid : arena.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.playSound(p.getLocation(), settings.PLAYER_JOIN_SOUND, settings.PLAYER_JOIN_VOLUME, settings.PLAYER_JOIN_PITCH);
            }
        }

        checkAndStartCountdown(arena);
    }

    public void removePlayer(Player player) {
        Arena arena = playerArenas.get(player.getUniqueId());
        boolean wasSpectator = false;

        if (arena == null) {
            arena = spectators.get(player.getUniqueId());
            if (arena != null) {
                wasSpectator = true;
            }
        }

        if (arena == null) {
            if (player.getWorld().equals(plugin.getSettingsManager().getMainSpawnLocation() != null ? plugin.getSettingsManager().getMainSpawnLocation().getWorld() : Bukkit.getServer().getWorlds().get(0))) {
                player.getInventory().clear();
                player.setExp(0);
                player.setLevel(0);
                teleportToMainSpawn(player);
                return;
            }
            return;
        }

        player.setGameMode(GameMode.SURVIVAL);

        if (wasSpectator) {
            arena.getSpectators().remove(player.getUniqueId());
            this.spectators.remove(player.getUniqueId());
            this.supportSelections.remove(player.getUniqueId());
            settings.sendMessage(player, settings.getMessage("spectator.leave-spectating").replace("%arena%", arena.getDisplayName()));
        } else {
            Team team = arena.getGameManager().getTeamOfPlayer(player);

            arena.getPlayers().remove(player.getUniqueId());
            playerArenas.remove(player.getUniqueId());
            arena.getGameManager().unassignPlayerFromTeam(player);

            if (arena.getState() == ArenaState.ACTIVE || arena.getState() == ArenaState.STARTING) {
                PlayerStats stats = plugin.getPlayerDataManager().getStats(player.getUniqueId());
                if (stats != null) {
                    stats.incrementLosses();
                }

                if (team != null && !arena.getGameManager().isTeamAlive(team)) {
                    arena.getArenaManager().convertTeamSliceToWhite(team);
                }

                arena.getGameManager().checkWinCondition();

            } else {
                int maxPlayers = Team.values().length * arena.getTeamSize();
                String leaveMessage = settings.getMessage("lobby.player-leave")
                        .replace("%player%", player.getName())
                        .replace("%current%", String.valueOf(arena.getPlayers().size()))
                        .replace("%max%", String.valueOf(maxPlayers));
                broadcastArenaMessage(arena, leaveMessage);
            }
        }

        player.getInventory().clear();
        player.setExp(0);
        player.setLevel(0);
        player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
        player.setFoodLevel(20);
        player.setSaturation(5.0f);
        player.setFireTicks(0);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        player.sendTitle("", "", 0, 1, 0);

        teleportToMainSpawn(player);
        updatePlayerVisibility(player);
    }

    public void teleportToMainSpawn(Player player) {
        Location mainSpawn = settings.getMainSpawnLocation();
        if (mainSpawn != null) {
            player.teleport(mainSpawn);
        } else {
            World defaultWorld = Bukkit.getServer().getWorlds().get(0);
            if (defaultWorld != null) {
                player.teleport(defaultWorld.getSpawnLocation());
            }
        }
    }

    public Location getPrimaryLobbyLocation() {
        for (Arena arena : arenas.values()) {
            if (arena.getLobbyLocation() != null) {
                return arena.getLobbyLocation();
            }
        }
        return null;
    }

    public void checkAndStartCountdown(Arena arena){
        if (arena.getState() == ArenaState.WAITING && arena.getPlayers().size() >= settings.MIN_PLAYERS_TO_START) {
            startLobbyCountdown(arena);
        }
    }

    private void startLobbyCountdown(Arena arena) {
        arena.setState(ArenaState.COUNTDOWN);
        new BukkitRunnable() {
            int countdown = settings.LOBBY_COUNTDOWN_SECONDS;

            @Override
            public void run() {
                if (arena.getState() != ArenaState.COUNTDOWN) {
                    this.cancel();
                    return;
                }

                if (arena.getPlayers().size() < settings.MIN_PLAYERS_TO_START) {
                    arena.setState(ArenaState.WAITING);
                    broadcastArenaMessage(arena, settings.getMessage("lobby.countdown-cancelled-players"));
                    this.cancel();
                    return;
                }

                if (countdown > 0) {
                    for (UUID uuid : arena.getPlayers()) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null && arena.equals(getArenaByPlayerIncludingSpectators(p))) {
                            p.sendTitle(ChatColor.GREEN + String.valueOf(countdown), "", 5, 10, 5);
                            if (countdown <= 5 || countdown % 5 == 0) {
                                p.sendMessage(settings.PREFIX + settings.getMessage("lobby.countdown-starting").replace("%time%", String.valueOf(countdown)));
                                p.playSound(p.getLocation(), settings.COUNTDOWN_TICK_SOUND, settings.COUNTDOWN_TICK_VOLUME, settings.COUNTDOWN_TICK_PITCH);
                            }
                        }
                    }
                    countdown--;
                } else {
                    this.cancel();
                    for (UUID uuid : arena.getPlayers()) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) {
                            p.playSound(p.getLocation(), settings.LOBBY_START_SOUND, settings.LOBBY_START_VOLUME, settings.LOBBY_START_PITCH);
                        }
                    }
                    arena.getGameManager().startGame(arena.getCenterLocation());
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public void openLobbyGui(Player player) {
        int invSize = 54;
        Inventory gui = Bukkit.createInventory(null, invSize, settings.getMessage("gui.lobby-title"));

        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).setName(" ").build();
        for (int i = 0; i < invSize; i++) {
            gui.setItem(i, filler);
        }

        List<Integer> arenaSlots = Arrays.asList(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43);
        int currentSlot = 0;

        NamespacedKey key = new NamespacedKey(plugin, "arena_name");

        List<Arena> sortedArenas = arenas.values().stream()
                .sorted(Comparator.comparing(Arena::getName))
                .collect(Collectors.toList());

        for (Arena arena : sortedArenas) {
            if(currentSlot >= arenaSlots.size()) break;

            if (arena.getLobbyLocation() == null || arena.getCenterLocation() == null || arena.getLobbyLocation().getWorld() == null) continue;

            Material material;
            List<String> lore = new ArrayList<>();
            ArenaState state = arena.getState();

            if (state == ArenaState.WAITING || state == ArenaState.COUNTDOWN) {
                material = Material.LIME_WOOL;
                lore.add(settings.getMessage("gui.arena-item.status-waiting"));
                lore.add("");
                lore.add(settings.getMessage("gui.arena-item.click-to-join"));
            } else if (state == ArenaState.RESETTING) {
                material = Material.RED_WOOL;
                lore.add(settings.getMessage("gui.arena-item.status-resetting"));
                lore.add("");
                lore.add(settings.getMessage("gui.arena-item.please-wait"));
            } else {
                material = Material.YELLOW_WOOL;
                lore.add(settings.getMessage("gui.arena-item.status-inprogress"));
                lore.add("");
                lore.add(settings.getMessage("gui.arena-item.click-to-spectate"));
            }

            int maxPlayers = Team.values().length * arena.getTeamSize();
            lore.add("");
            lore.add(settings.getMessage("gui.arena-item.players").replace("%current%", String.valueOf(arena.getPlayers().size())).replace("%max%", String.valueOf(maxPlayers)));
            lore.add(settings.getMessage("gui.arena-item.team-size").replace("%size%", String.valueOf(arena.getTeamSize())));

            String worldName = arena.getLobbyLocation().getWorld().getName();
            lore.add(settings.format("&7Dünya: &e" + worldName));

            ItemStack item = new ItemBuilder(material)
                    .setName(ChatColor.GOLD + arena.getDisplayName())
                    .setLore(lore)
                    .build();

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, arena.getName());
                item.setItemMeta(meta);
            }

            gui.setItem(arenaSlots.get(currentSlot), item);
            currentSlot++;
        }
        player.openInventory(gui);
    }

    public void spectateArena(Player player, String arenaName) {
        Arena currentArena = getArenaByPlayerIncludingSpectators(player);
        if (currentArena != null) {
            removePlayer(player);
        }

        Arena arena = arenas.get(arenaName);
        if (arena == null) {
            settings.sendMessage(player, settings.getMessage("errors.arena-not-found"));
            return;
        }

        if (arena.getCenterLocation() == null) {
            settings.sendMessage(player, settings.getMessage("errors.arena-not-ready"));
            return;
        }

        player.getInventory().clear();
        addSpectator(player, arena);
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(arena.getArenaManager().getCenter().clone().add(0.5, 2, 0.5));
        updatePlayerVisibility(player);
        settings.sendMessage(player, settings.getMessage("spectator.start-spectating").replace("%arena%", arena.getDisplayName()));
        player.sendMessage(settings.getMessage("spectator.help"));
    }

    public void addSpectator(Player player, Arena arena) {
        if (arena == null || player == null) return;
        arena.getSpectators().add(player.getUniqueId());
        this.spectators.put(player.getUniqueId(), arena);
    }

    private boolean isSpectating(Player player) {
        return this.spectators.containsKey(player.getUniqueId());
    }

    public void openTeamSelectionGUI(Player player) {
        Arena arena = getArenaByPlayer(player);
        if (arena == null) return;

        Inventory gui = Bukkit.createInventory(null, 9, settings.getMessage("gui.team-select-title"));
        for (Team team : Team.values()) {
            int playersInTeam = arena.getGameManager().getPlayersInTeam(team).size();
            ItemStack item = new ItemBuilder(team.getConcreteMaterial())
                    .setName(team.getChatColor() + team.getDisplayName())
                    .setLore(
                            settings.getMessage("gui.team-item.click-to-join"),
                            settings.getMessage("gui.team-item.players")
                                    .replace("%current%", String.valueOf(playersInTeam))
                                    .replace("%max%", String.valueOf(arena.getTeamSize()))
                    )
                    .build();
            gui.addItem(item);
        }

        ItemStack barrier = new ItemBuilder(Material.BARRIER)
                .setName(settings.getMessage("gui.unselect-item-name"))
                .setLore(settings.getMessage("gui.unselect-item-lore"))
                .build();
        gui.setItem(8, barrier);

        player.openInventory(gui);
    }

    public void selectTeam(Player player, Team team) {
        Arena arena = getArenaByPlayer(player);
        if (arena == null) return;

        if (arena.getGameManager().getPlayersInTeam(team).size() >= arena.getTeamSize()) {
            settings.sendMessage(player, settings.getMessage("errors.team-full"));
            return;
        }

        arena.getGameManager().assignPlayerToTeam(player, team);
        player.closeInventory();
        settings.sendMessage(player, settings.getMessage("lobby.team-select").replace("%team_color%", team.getChatColor().toString()).replace("%team_name%", team.getDisplayName()));
        player.teleport(getTeamPlatformLocation(arena, team));

        checkAndStartCountdown(arena);
    }

    public void unselectTeam(Player player) {
        Arena arena = getArenaByPlayer(player);
        if (arena == null) return;

        arena.getGameManager().unassignPlayerFromTeam(player);
        player.closeInventory();
        settings.sendMessage(player, settings.getMessage("lobby.team-unselect"));
        player.teleport(arena.getLobbyLocation().clone().add(0.5, 2, 0.5));

        checkAndStartCountdown(arena);
    }

    public void openTeammateSupportGui(Player spectator) {
        Arena arena = spectators.get(spectator.getUniqueId());
        if (arena == null) return;

        GameManager gameManager = arena.getGameManager();
        Team spectatorTeam = gameManager.getTeamOfPlayer(spectator);

        List<Player> aliveTeammates = gameManager.getPlayersInTeam(spectatorTeam).stream()
                .map(Bukkit::getPlayer)
                .filter(p -> p != null && !p.isDead() && !p.getUniqueId().equals(spectator.getUniqueId()) && !arena.getSpectators().contains(p.getUniqueId()))
                .collect(Collectors.toList());

        if (aliveTeammates.isEmpty()) {
            spectator.sendMessage(settings.PREFIX + settings.getMessage("support.no-living-teammates"));
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 27, settings.getMessage("support.gui-teammate-title"));

        ItemStack filler = new ItemBuilder(Material.LIGHT_BLUE_STAINED_GLASS_PANE).setName(" ").build();
        for (int i = 0; i < 27; i++) {
            gui.setItem(i, filler);
        }

        List<Integer> slots = Arrays.asList(10, 11, 12, 13, 14, 15, 16);
        int currentSlot = 0;

        for (Player teammate : aliveTeammates) {
            if (currentSlot >= slots.size()) break;

            PlayerStats stats = plugin.getPlayerDataManager().getStats(teammate.getUniqueId());
            String kdr = "N/A";
            if (stats != null) {
                kdr = String.format("%.2f", stats.getDeaths() == 0 ? (double) stats.getKills() : (double) stats.getKills() / stats.getDeaths());
            }

            ItemStack head = new ItemBuilder(Material.PLAYER_HEAD)
                    .setSkullOwner(teammate)
                    .setName(ChatColor.GREEN + teammate.getName())
                    .setLore(
                            "&7Sağlık: &c" + String.format("%.1f", teammate.getHealth()),
                            "&7Puan: &e" + (stats != null ? stats.getPoints() : 0),
                            "&7KDR: &e" + kdr,
                            "",
                            "&bBu oyuncuya destek olmak için tıkla."
                    )
                    .build();

            gui.setItem(slots.get(currentSlot), head);
            currentSlot++;
        }
        spectator.openInventory(gui);
    }

    public void openSupportShopGui(Player spectator, Player targetTeammate) {
        supportSelections.put(spectator.getUniqueId(), targetTeammate.getUniqueId());

        String title = settings.getMessage("support.gui-shop-title").replace("%player%", targetTeammate.getName());
        Inventory gui = Bukkit.createInventory(null, 54, title);

        ShopManager shopManager = plugin.getShopManager();
        NamespacedKey key = new NamespacedKey(plugin, "shop_item_id");
        PlayerStats spectatorStats = plugin.getPlayerDataManager().getStats(spectator.getUniqueId());
        int playerPoints = spectatorStats != null ? spectatorStats.getPoints() : 0;

        ItemStack filler = new ItemBuilder(Material.BLUE_STAINED_GLASS_PANE).setName(" ").build();
        for (int i = 0; i < 54; i++) {
            gui.setItem(i, filler);
        }

        ItemStack backButton = new ItemBuilder(Material.ARROW).setName("&cGeri Dön").setLore("&7Takım arkadaşı seçimine geri döner.").build();
        NamespacedKey backKey = new NamespacedKey(plugin, "gui_action");
        ItemMeta backMeta = backButton.getItemMeta();
        if (backMeta != null) {
            backMeta.getPersistentDataContainer().set(backKey, PersistentDataType.STRING, "back_to_teammates");
            backButton.setItemMeta(backMeta);
        }
        gui.setItem(49, backButton);

        List<Integer> itemSlots = Arrays.asList(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34);
        int currentSlot = 0;

        for (ShopItem shopItem : shopManager.getShopItems()) {
            if (currentSlot >= itemSlots.size()) break;

            ItemStack displayItem = shopItem.getDisplayItem().clone();
            ItemMeta meta = displayItem.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();

                List<String> updatedLore = new ArrayList<>();
                if (lore != null) {
                    updatedLore.addAll(lore);
                }

                if (playerPoints < shopItem.getPrice()) {
                    meta.setDisplayName(ChatColor.RED + ChatColor.stripColor(meta.getDisplayName()) + " (Yetersiz Puan)");
                    updatedLore.add(settings.format("&c(Puanın Yetersiz)"));
                }

                meta.setLore(updatedLore.stream().map(settings::format).collect(Collectors.toList()));

                meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, shopItem.getId());
                displayItem.setItemMeta(meta);
            }
            gui.setItem(itemSlots.get(currentSlot), displayItem);
            currentSlot++;
        }
        spectator.openInventory(gui);
    }

    public Player getSupportTarget(Player spectator) {
        UUID targetUUID = supportSelections.get(spectator.getUniqueId());
        if (targetUUID == null) return null;
        return Bukkit.getPlayer(targetUUID);
    }

    public Location getTeamPlatformLocation(Arena arena, Team team) {
        Location lobbyCenter = arena.getLobbyLocation();
        if (lobbyCenter == null) return null;
        int teamIndex = team.ordinal();
        int totalTeams = Team.values().length;
        double angle = (2 * Math.PI / totalTeams) * teamIndex;
        double radius = settings.LOBBY_TEAM_ISLAND_DISTANCE;
        double x = Math.cos(angle) * radius;
        double z = Math.sin(angle) * radius;
        return lobbyCenter.clone().add(x + 0.5, 7, z + 0.5);
    }

    private void giveLobbyItems(Player player) {
        player.getInventory().clear();

        ItemStack teamSelector = new ItemBuilder(settings.TEAM_SELECTOR_MATERIAL)
                .setName(settings.TEAM_SELECTOR_NAME)
                .setLore(settings.TEAM_SELECTOR_LORE)
                .build();
        player.getInventory().setItem(settings.TEAM_SELECTOR_SLOT, teamSelector);

        ItemStack leaveItem = new ItemBuilder(settings.LEAVE_LOBBY_MATERIAL)
                .setName(settings.LEAVE_LOBBY_NAME)
                .setLore(settings.LEAVE_LOBBY_LORE)
                .build();
        player.getInventory().setItem(settings.LEAVE_LOBBY_SLOT, leaveItem);

        player.updateInventory();
    }

    public void buildLobby(Arena arena) {
        Location center = arena.getLobbyLocation();
        if (center == null) return;
        World world = center.getWorld();
        int clearRadius = 30;

        for (int x = -clearRadius; x <= clearRadius; x++) {
            for (int z = -clearRadius; z <= clearRadius; z++) {
                for (int y = -15; y <= 30; y++) {
                    world.getBlockAt(center.clone().add(x, y, z)).setType(Material.AIR);
                }
            }
        }

        int mainIslandRadius = settings.LOBBY_MAIN_ISLAND_RADIUS;
        for (int x = -mainIslandRadius; x <= mainIslandRadius; x++) {
            for (int z = -mainIslandRadius; z <= mainIslandRadius; z++) {
                for (int y = -5; y <= 0; y++) {
                    Location loc = center.clone().add(x, y, z);
                    if (loc.distance(center) <= mainIslandRadius) {
                        if (y == 0) loc.getBlock().setType(Material.GRASS_BLOCK);
                        else if (y > -4) loc.getBlock().setType(Material.DIRT);
                        else loc.getBlock().setType(Material.STONE);
                    }
                }
            }
        }

        int teamIndex = 0;
        int totalTeams = Team.values().length;
        double teamIslandDistance = settings.LOBBY_TEAM_ISLAND_DISTANCE;
        int teamIslandRadius = settings.LOBBY_TEAM_ISLAND_RADIUS;
        double angleIncrement = 2 * Math.PI / totalTeams;

        for (Team team : Team.values()) {
            double angle = angleIncrement * teamIndex;
            double cx = Math.cos(angle) * teamIslandDistance;
            double cz = Math.sin(angle) * teamIslandDistance;
            Location teamCenter = center.clone().add(cx, 5, cz);

            for (int x = -teamIslandRadius; x <= teamIslandRadius; x++) {
                for (int z = -teamIslandRadius; z <= teamIslandRadius; z++) {
                    Location floorLoc = teamCenter.clone().add(x, 0, z);
                    if (floorLoc.distance(teamCenter) <= teamIslandRadius) {
                        floorLoc.getBlock().setType(team.getConcreteMaterial());
                    } else {
                        floorLoc.getBlock().setType(Material.BARRIER);
                    }

                    for (int y = -2; y < 0; y++) {
                        Location loc = teamCenter.clone().add(x, y, z);
                        if (loc.distance(teamCenter.clone().add(0, y, 0)) <= teamIslandRadius) {
                            loc.getBlock().setType(team.getConcreteMaterial());
                        }
                    }
                }
            }

            int barrierRadius = teamIslandRadius + 1;
            for (double a = 0; a < 2 * Math.PI; a += 0.2) {
                for (int y = 0; y <= 6; y++) {
                    double bx = Math.cos(a) * barrierRadius;
                    double bz = Math.sin(a) * barrierRadius;
                    teamCenter.clone().add(bx, y, bz).getBlock().setType(Material.BARRIER);
                }
            }
            teamIndex++;
        }

        for (int x = -clearRadius; x <= clearRadius; x++) {
            for (int z = -clearRadius; z <= clearRadius; z++) {
                world.getBlockAt(center.clone().add(x, -15, z)).setType(Material.BARRIER);
                world.getBlockAt(center.clone().add(x, 30, z)).setType(Material.BARRIER);
            }
        }

        for(int x = -clearRadius; x <= clearRadius; x++){
            for(int y = -15; y <= 30; y++){
                world.getBlockAt(center.clone().add(x,y,clearRadius)).setType(Material.BARRIER);
                world.getBlockAt(center.clone().add(x,y,-clearRadius)).setType(Material.BARRIER);
            }
        }

        for(int z = -clearRadius; z <= clearRadius; z++){
            for(int y = -15; y <= 30; y++){
                world.getBlockAt(center.clone().add(clearRadius,y,z)).setType(Material.BARRIER);
                world.getBlockAt(center.clone().add(-clearRadius,y,z)).setType(Material.BARRIER);
            }
        }
    }

    public void broadcastArenaMessage(Arena arena, String message) {
        Set<UUID> allInvolved = new HashSet<>(arena.getPlayers());
        allInvolved.addAll(arena.getSpectators());
        for (UUID uuid : allInvolved) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendMessage(settings.PREFIX + message);
            }
        }
    }

    public Arena getArenaByPlayer(Player player) {
        return playerArenas.get(player.getUniqueId());
    }

    public Arena getArenaByPlayerIncludingSpectators(Player player) {
        Arena arena = playerArenas.get(player.getUniqueId());
        if (arena == null) {
            arena = spectators.get(player.getUniqueId());
        }
        return arena;
    }

    public void updatePlayerVisibility(Player subject) {
        Arena subjectArena = getArenaByPlayerIncludingSpectators(subject);

        for (Player observer : Bukkit.getOnlinePlayers()) {
            if (subject.equals(observer)) continue;

            Arena observerArena = getArenaByPlayerIncludingSpectators(observer);

            boolean shouldSee;
            if (subjectArena == null) {
                shouldSee = (observerArena == null);
            } else {
                shouldSee = subjectArena.equals(observerArena);
            }

            if (shouldSee) {
                subject.showPlayer(plugin, observer);
                observer.showPlayer(plugin, subject);
            } else {
                subject.hidePlayer(plugin, observer);
                observer.hidePlayer(plugin, subject);
            }
        }
    }

    public Map<String, Arena> getArenas() {
        return arenas;
    }

    public SettingsManager getSettingsManager() {
        return settings;
    }//f

    public Map<UUID, Arena> getSpectatorsMap() {
        return spectators;
    }

    public Map<UUID, Arena> getPlayerArenasMap() {
        return playerArenas;
    }

    public void shutdown() {
        for (Arena arena : arenas.values()) {
            if (arena.getArenaManager().getCenter() != null) {
                arena.getArenaManager().destroyArena();
            }
        }
    }
}