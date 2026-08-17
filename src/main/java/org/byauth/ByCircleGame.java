package org.byauth;

import net.luckperms.api.LuckPerms;
import org.byauth.command.*;
import org.byauth.controller.ArenaController;
import org.byauth.game.Arena;
import org.byauth.game.ArenaState;
import org.byauth.game.Team;
import org.byauth.game.VictoryEffects;
import org.byauth.hook.PlaceholderHook;
import org.byauth.listener.*;
import org.byauth.manager.*;
import org.byauth.utils.KeygenValidator;
import org.byauth.utils.SettingsManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class ByCircleGame extends JavaPlugin {

    private ArenaController arenaController;
    private SettingsManager settingsManager;
    private LootManager lootManager;
    private PlayerDataManager playerDataManager;
    private ShopManager shopManager;
    private DatabaseManager databaseManager;
    private RankManager rankManager;
    private CosmeticManager cosmeticManager;
    private LuckPerms luckPerms;
    private boolean isReady = false;
    private boolean isLicensed = false;
    private LobbyProtectionListener lobbyProtectionListener;

    public boolean isLicensed() {
        return isLicensed;
    }

    public void setLicensed(boolean licensed) {
        this.isLicensed = licensed;
    }

    @Override
    public void onEnable() {
        // config.yml dosyasının varlığından emin ol
        saveDefaultConfig();

        // Keygen Otomatik Makine Kayıtlı Lisans Kontrolü
        // Başarılı olursa initializePlugin() metodunu KeygenValidator içinden tetikleyecek
        new KeygenValidator(this).validate();
    }

    public void initializePlugin() {
        this.isLicensed = true;
        getLogger().info("[LBDaire] Lisans başarıyla doğrulandı. Eklenti bileşenleri başlatılıyor...");

        VictoryEffects.performStartupCleanup(this);

        this.settingsManager = new SettingsManager(this);
        this.databaseManager = new DatabaseManager(this);
        databaseManager.connect();

        this.playerDataManager = new PlayerDataManager(this, databaseManager);
        
        // ÖNEMLİ: LootManager, ShopManager'dan ÖNCE başlatılmalıdır.
        this.lootManager = new LootManager(this);
        this.shopManager = new ShopManager(this);
        this.cosmeticManager = new CosmeticManager(this);

        this.arenaController = new ArenaController(this);

        // LuckPerms bağımlılık kontrolü ve entegrasyonu
        setupLuckPerms();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderHook(this).register();
            getLogger().info("PlaceholderAPI entegrasyonu başarıyla sağlandı.");
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            playerDataManager.loadPlayerData(player);
        }

        this.isReady = true;

        scheduleSequentialArenaReset();
        startArenaStuckCheckTask();
        startEmptyArenaCheckTask();

        // Komut adları
        getCommand("daire").setExecutor(new PlayerCommand(this));
        getCommand("daire").setTabCompleter(new PlayerCommandTabCompleter(this));

        getCommand("daireadmin").setExecutor(new AdminCommand(this));
        getCommand("daireadmin").setTabCompleter(new AdminCommandTabCompleter(this));

        getCommand("destekle").setExecutor(new SupportCommand(this));
        getCommand("kozmetik").setExecutor(new CosmeticCommand(this));

        registerListeners();
    }

    private void setupLuckPerms() {
        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            try {
                RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
                if (provider != null) {
                    this.luckPerms = provider.getProvider();
                    this.rankManager = new RankManager(this, luckPerms);
                    getLogger().info("LuckPerms entegrasyonu başarılı, Rank Sistemi aktif.");
                } else {
                    getLogger().warning("LuckPerms servisi bulunamadı! Rank Sistemi devre dışı bırakılıyor.");
                }
            } catch (Exception e) {
                getLogger().severe("LuckPerms bağlanırken hata oluştu: " + e.getMessage());
            }
        } else {
            getLogger().warning("LuckPerms eklentisi sunucuda aktif değil! Rank Sistemi devre dışı bırakılıyor.");
        }
    }

    private void registerListeners() {
        this.lobbyProtectionListener = new LobbyProtectionListener(this);

        getServer().getPluginManager().registerEvents(new LobbyListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerMoveListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockPlaceListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockBreakListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerItemConsumeListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockFormListener(this), this);
        getServer().getPluginManager().registerEvents(new ProjectileHitListener(this), this);
        getServer().getPluginManager().registerEvents(new SpecialItemUseListener(this), this);
        getServer().getPluginManager().registerEvents(new GameDamageListener(this), this);
        getServer().getPluginManager().registerEvents(this.lobbyProtectionListener, this);
        getServer().getPluginManager().registerEvents(new LobbyGuiListener(this), this);
        getServer().getPluginManager().registerEvents(new WorldProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerGameModeChangeListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new SupportGuiListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerChatListener(this), this);
        getServer().getPluginManager().registerEvents(new CosmeticGuiListener(this), this);
        getServer().getPluginManager().registerEvents(new ArrowEffectListener(this), this);
    }

    private void scheduleSequentialArenaReset() {
        getLogger().info("Sunucu başlangıcı için arena temizleme görevi zamanlanıyor...");
        Queue<Arena> arenaQueue = new LinkedList<>(arenaController.getArenas().values());

        if (arenaQueue.isEmpty()) {
            getLogger().info("Sıfırlanacak kayıtlı arena bulunamadı.");
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (arenaQueue.isEmpty()) {
                    getLogger().info("Tüm arenaların başlangıç temizliği tamamlandı.");
                    this.cancel();
                    return;
                }

                Arena arena = arenaQueue.poll();
                if (arena.getCenterLocation() != null) {
                    World arenaWorld = arena.getCenterLocation().getWorld();
                    if (arenaWorld == null) {
                        getLogger().log(Level.WARNING, arena.getName() + " arenası için dünya bulunamadı veya yüklü değil. Sıfırlama atlanıyor.");
                        return;
                    }

                    arenaWorld.getWorldBorder().reset();
                    getLogger().info(arena.getName() + " arenası (" + arenaWorld.getName() + " dünyasında) yeniden oluşturuluyor...");
                    arena.setState(ArenaState.RESETTING);

                    arena.getArenaManager().destroyArena();

                    arena.getArenaManager().generateArena(arena.getCenterLocation(), new ArrayList<>(Arrays.asList(Team.values())));

                    arena.setState(ArenaState.WAITING);
                    getLogger().info(arena.getName() + " arenası başarıyla yenilendi.");
                }
            }
        }.runTaskTimer(this, 20L, 20L);
    }

    private void startArenaStuckCheckTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (arenaController == null || arenaController.getArenas().isEmpty()) {
                    return;
                }
                long currentTime = System.currentTimeMillis();
                long resetTimeout = 30000;
                for (Arena arena : arenaController.getArenas().values()) {
                    if (arena.getState() == ArenaState.RESETTING) {
                        if ((currentTime - arena.getLastStateChangeTime()) > resetTimeout) {
                            getLogger().warning("Arena '" + arena.getName() + "' 'Yenileniyor' modunda takılı kaldı. Zorla sıfırlanıyor...");
                            try {
                                if (arena.getCenterLocation() != null && arena.getCenterLocation().getWorld() != null) {
                                    arena.getCenterLocation().getWorld().getWorldBorder().reset();
                                }
                                Set<UUID> allInvolved = new HashSet<>(arena.getPlayers());
                                allInvolved.addAll(arena.getSpectators());
                                for (UUID uuid : allInvolved) {
                                    Player p = Bukkit.getPlayer(uuid);
                                    if (p != null) {
                                        arenaController.removePlayer(p);
                                    }
                                }
                                arena.getPlayers().clear();
                                arena.getSpectators().clear();
                                arena.getArenaManager().resetArena(new ArrayList<>());
                                arena.setState(ArenaState.WAITING);
                                getLogger().info("Arena '" + arena.getName() + "' başarıyla sıfırlandı ve 'Bekleniyor' durumuna getirildi.");
                            } catch (Exception e) {
                                getLogger().severe("Arena '" + arena.getName() + "' zorla sıfırlanırken bir hata oluştu: " + e.getMessage());
                                arena.setState(ArenaState.WAITING);
                            }
                        }
                    }
                }
            }
        }.runTaskTimerAsynchronously(this, 20L * 60, 20L * 30);
    }

    private void startEmptyArenaCheckTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (arenaController == null || arenaController.getArenas().isEmpty()) {
                    return;
                }
                for (Arena arena : arenaController.getArenas().values()) {
                    ArenaState state = arena.getState();
                    boolean isActiveState = state == ArenaState.ACTIVE || state == ArenaState.STARTING || state == ArenaState.ENDING;

                    if (isActiveState && arena.getPlayers().isEmpty() && arena.getSpectators().isEmpty()) {
                        getLogger().info("'" + arena.getName() + "' arenası boş olduğu için otomatik olarak sıfırlanıyor...");
                        arena.getGameManager().stopGame(false);
                    }
                }
            }
        }.runTaskTimer(this, 20L * 30, 20L * 20);
    }

    public void reloadPlugin() {
        settingsManager.reload();
        lootManager.reload();
        arenaController.loadArenas();
        if (rankManager != null) {
            rankManager.reload();
        }
        cosmeticManager.loadCosmetics();
        getLogger().info("Eklenti başarıyla yeniden yüklendi.");
    }

    @Override
    public void onDisable() {
        if (playerDataManager != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                playerDataManager.savePlayerData(player, false);
            }
        }

        if (databaseManager != null) {
            databaseManager.closeConnection();
        }

        if (arenaController != null) {
            arenaController.shutdown();
        }

        getLogger().info("LbDaire eklentisi devre dışı bırakıldı.");
    }

    public ArenaController getArenaController() {
        return arenaController;
    }

    public SettingsManager getSettingsManager() {
        return settingsManager;
    }

    public LootManager getLootManager() {
        return lootManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public RankManager getRankManager() {
        return rankManager;
    }

    public ShopManager getShopManager() {
        return shopManager;
    }

    public CosmeticManager getCosmeticManager() {
        return cosmeticManager;
    }

    public World getGameWorld(String arenaName) {
        String worldName = settingsManager.getArenaWorldName(arenaName);
        if (worldName == null) return null;
        return Bukkit.getWorld(worldName);
    }

    public boolean isReady() {
        return isReady;
    }
}