package org.byauth.game;

import org.byauth.ByCircleGame;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Warden;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class VictoryEffects {

    private static final Random random = new Random();

    public static void performStartupCleanup(ByCircleGame plugin) {
        File cleanupFile = new File(plugin.getDataFolder(), "cleanup.yml");
        if (!cleanupFile.exists()) {
            return;
        }
        FileConfiguration cleanupConfig = YamlConfiguration.loadConfiguration(cleanupFile);
        ConfigurationSection mainSection = cleanupConfig.getConfigurationSection("effects");
        if (mainSection == null) {
            return;
        }

        plugin.getLogger().info("Kalan efekt kalıntıları temizleniyor...");
        for (String uuidStr : mainSection.getKeys(false)) {
            ConfigurationSection playerSection = mainSection.getConfigurationSection(uuidStr);
            if (playerSection == null) continue;

            if (playerSection.isList("blocks")) {
                List<Map<?, ?>> blockList = playerSection.getMapList("blocks");
                for (Map<?, ?> blockMap : blockList) {
                    try {
                        World world = Bukkit.getWorld((String) blockMap.get("world"));
                        if (world == null) continue;
                        int x = (int) blockMap.get("x");
                        int y = (int) blockMap.get("y");
                        int z = (int) blockMap.get("z");
                        BlockData data = Bukkit.createBlockData((String) blockMap.get("data"));
                        world.getBlockAt(x, y, z).setBlockData(data, false);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Bir blok temizlenirken hata oluştu: " + e.getMessage());
                    }
                }
            }

            if (playerSection.isList("entities")) {
                List<String> entityUuids = playerSection.getStringList("entities");
                for (String entityUuidStr : entityUuids) {
                    try {
                        UUID entityUuid = UUID.fromString(entityUuidStr);
                        Entity entity = Bukkit.getEntity(entityUuid);
                        if (entity != null) {
                            entity.remove();
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Bir varlık temizlenirken hata oluştu: " + e.getMessage());
                    }
                }
            }
        }
        cleanupFile.delete();
        plugin.getLogger().info("Efekt kalıntıları başarıyla temizlendi.");
    }

    private static void saveCleanupData(ByCircleGame plugin, Player player, Map<Block, BlockData> blocks, List<ArmorStand> stands) {
        File cleanupFile = new File(plugin.getDataFolder(), "cleanup.yml");
        FileConfiguration cleanupConfig = YamlConfiguration.loadConfiguration(cleanupFile);

        String path = "effects." + player.getUniqueId().toString();
        List<Map<String, Object>> blockDataList = new ArrayList<>();
        for (Map.Entry<Block, BlockData> entry : blocks.entrySet()) {
            Map<String, Object> blockMap = new HashMap<>();
            blockMap.put("world", entry.getKey().getWorld().getName());
            blockMap.put("x", entry.getKey().getX());
            blockMap.put("y", entry.getKey().getY());
            blockMap.put("z", entry.getKey().getZ());
            blockMap.put("data", entry.getValue().getAsString());
            blockDataList.add(blockMap);
        }
        cleanupConfig.set(path + ".blocks", blockDataList);

        List<String> entityUuids = new ArrayList<>();
        for (ArmorStand stand : stands) {
            entityUuids.add(stand.getUniqueId().toString());
        }
        cleanupConfig.set(path + ".entities", entityUuids);

        try {
            cleanupConfig.save(cleanupFile);
        } catch (IOException e) {
            plugin.getLogger().severe("cleanup.yml dosyası kaydedilemedi!");
        }
    }

    private static void clearCleanupData(ByCircleGame plugin, Player player) {
        File cleanupFile = new File(plugin.getDataFolder(), "cleanup.yml");
        FileConfiguration cleanupConfig = YamlConfiguration.loadConfiguration(cleanupFile);
        cleanupConfig.set("effects." + player.getUniqueId().toString(), null);
        try {
            cleanupConfig.save(cleanupFile);
        } catch (IOException e) {
            plugin.getLogger().severe("cleanup.yml dosyası temizlenemedi!");
        }
    }

    public static void playLightningStorm(Player player, ByCircleGame plugin) {
        new BukkitRunnable() {
            private int ticks = 0;
            private final int DURATION = 200;
            private final Location center = player.getLocation();
            private final List<ArmorStand> tridents = new ArrayList<>();

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= DURATION) {
                    player.setGravity(true);
                    player.removePotionEffect(PotionEffectType.SLOW_FALLING);
                    for (ArmorStand as : tridents) {
                        as.remove();
                    }
                    clearCleanupData(plugin, player);
                    this.cancel();
                    return;
                }

                if (ticks == 0) {
                    player.setGravity(false);
                    player.getWorld().playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.2f, 0.8f);
                    for (int i = 0; i < 4; i++) {
                        double angle = (Math.PI / 2) * i;
                        Location spawnLoc = center.clone().add(4 * Math.cos(angle), -3, 4 * Math.sin(angle));
                        tridents.add(center.getWorld().spawn(spawnLoc, ArmorStand.class, as -> {
                            as.setVisible(false);
                            as.setGravity(false);
                            as.setMarker(true);
                            as.getEquipment().setItemInMainHand(new ItemStack(Material.TRIDENT));
                            as.setRightArmPose(new EulerAngle(Math.toRadians(-90), 0, 0));
                        }));
                    }
                    saveCleanupData(plugin, player, new HashMap<>(), tridents);
                }

                if (ticks < 60) {
                    player.teleport(player.getLocation().add(0, 0.04, 0));
                    for (ArmorStand as : tridents) {
                        as.teleport(as.getLocation().add(0, 0.05, 0));
                    }
                    player.getWorld().spawnParticle(Particle.WHITE_ASH, player.getLocation(), 5, 1, 0.5, 1, 0);
                    player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, player.getLocation(), 3, 0.5, 0.5, 0.5, 0.1);
                }

                if (ticks >= 60 && ticks < 140) {
                    if (ticks % 10 == 0) {
                        double angle = random.nextDouble() * 2 * Math.PI;
                        double radius = 6 + random.nextDouble() * 4;
                        Location strikeLoc = center.clone().add(radius * Math.cos(angle), 0, radius * Math.sin(angle));
                        strikeLoc.setY(strikeLoc.getWorld().getHighestBlockYAt(strikeLoc) + 1);
                        player.getWorld().strikeLightningEffect(strikeLoc);
                        player.getWorld().playSound(strikeLoc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.0f, 1.0f);
                    }
                }

                if (ticks == 140) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 80, 0));
                    player.setGravity(true);
                    for (ArmorStand as : tridents) {
                        as.setGravity(true);
                    }
                }

                double orbitAngle = (double) ticks * Math.PI / 20;
                for (ArmorStand as : tridents) {
                    if (as.hasGravity()) continue;
                    Vector dir = as.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(0.05);
                    as.teleport(as.getLocation().subtract(dir));
                    player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, as.getLocation().add(0, 2, 0), 1, 0, 0, 0, 0);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public static void playAngelicDescent(Player player, ByCircleGame plugin) {
        new BukkitRunnable() {
            private int ticks = 0;
            private final int DURATION = 240;
            private final Location center = player.getLocation();
            private final Map<Block, BlockData> originalBlocks = new HashMap<>();

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= DURATION) {
                    for (Map.Entry<Block, BlockData> entry : originalBlocks.entrySet()) {
                        entry.getKey().setBlockData(entry.getValue());
                    }
                    player.setGravity(true);
                    player.removePotionEffect(PotionEffectType.GLOWING);
                    clearCleanupData(plugin, player);
                    this.cancel();
                    return;
                }

                if (ticks == 0) {
                    player.setGravity(false);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, DURATION + 20, 0));
                    player.getWorld().playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.5f);
                    for (int x = -2; x <= 2; x++) {
                        for (int z = -2; z <= 2; z++) {
                            if (x * x + z * z > 5) continue;
                            Block b = center.clone().add(x, -1, z).getBlock();
                            if(b.getType().isSolid()){
                                originalBlocks.put(b, b.getBlockData());
                                b.setType(Math.abs(x) <= 1 && Math.abs(z) <= 1 ? Material.SEA_LANTERN : Material.QUARTZ_BLOCK);
                            }
                        }
                    }
                    saveCleanupData(plugin, player, originalBlocks, new ArrayList<>());
                }

                if (ticks < 80) {
                    player.teleport(player.getLocation().add(0, 0.05, 0));
                    player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation(), 3, 0.5, 0.5, 0.5, 0);
                }

                if (ticks > 180){
                    player.teleport(player.getLocation().subtract(0, 0.1, 0));
                    player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation(), 3, 0.5, 0.5, 0.5, 0);
                }

                drawWings(player.getLocation(), ticks);

                if (ticks % 15 == 0) {
                    player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_HIT, 1.0f, 1.8f);
                }

                ticks++;
            }

            private void drawWings(Location loc, int tick) {
                double angleY = Math.toRadians(-loc.getYaw() - 90);
                for (int i = 0; i < 2; i++) {
                    double flap = Math.sin((double) (tick + i * 10) / 10.0) * 0.2 + 0.2;
                    for (double r = 0; r < 2.5; r += 0.2) {
                        double angle = Math.PI / 2.5 * r;
                        Vector v = new Vector(0, Math.cos(angle) * 1.5, Math.sin(angle) * 1.5);
                        v.setY(v.getY() - flap);
                        if (i == 1) v.setZ(-v.getZ());

                        double cosY = Math.cos(angleY);
                        double sinY = Math.sin(angleY);
                        double tempX = v.getX() * cosY - v.getZ() * sinY;
                        double tempZ = v.getX() * sinY + v.getZ() * cosY;
                        v.setX(tempX);
                        v.setZ(tempZ);

                        loc.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(v).add(0, 1.2, 0), 1, 0, 0, 0, 0);
                    }
                }
            }

        }.runTaskTimer(plugin, 0L, 1L);
    }

    public static void playDragonRoar(Player player, ByCircleGame plugin) {
        new BukkitRunnable() {
            private int ticks = 0;
            private final int DURATION = 160;
            private final Location center = player.getLocation();
            private final List<ArmorStand> dragonHeads = new ArrayList<>();

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= DURATION) {
                    if (!dragonHeads.isEmpty()) dragonHeads.get(0).remove();
                    clearCleanupData(plugin, player);
                    this.cancel();
                    return;
                }

                if (ticks == 0) {
                    player.getWorld().playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 1.2f);
                    ArmorStand dragonHead = center.getWorld().spawn(player.getEyeLocation().add(0, 0.8, 0), ArmorStand.class, as -> {
                        as.setGravity(false);
                        as.setVisible(false);
                        as.setMarker(true);
                        as.getEquipment().setHelmet(new ItemStack(Material.DRAGON_HEAD));
                    });
                    dragonHeads.add(dragonHead);
                    saveCleanupData(plugin, player, new HashMap<>(), dragonHeads);
                }

                if (!dragonHeads.isEmpty()) {
                    Location headLoc = player.getEyeLocation().add(0, 0.8, 0);
                    headLoc.setYaw(player.getLocation().getYaw());
                    dragonHeads.get(0).teleport(headLoc);
                }

                if (ticks == 40) {
                    player.getWorld().playSound(center, Sound.ENTITY_WARDEN_ROAR, 1.5f, 0.8f);
                    player.getWorld().spawnParticle(Particle.SONIC_BOOM, player.getEyeLocation().add(player.getLocation().getDirection().multiply(1.5)), 1);
                    for (int i = 0; i < 50; i++) {
                        Location loc = player.getEyeLocation().add(player.getLocation().getDirection());
                        Vector dir = player.getLocation().getDirection().clone().add(new Vector(random.nextDouble()-0.5, random.nextDouble()-0.5, random.nextDouble()-0.5).multiply(0.5)).normalize();
                        player.getWorld().spawnParticle(Particle.DRAGON_BREATH, loc, 0, dir.getX(), dir.getY(), dir.getZ(), 0.5);
                    }
                }

                double radius = (double) ticks / 10.0;
                if (radius < 5) {
                    for (double angle = 0; angle < 2 * Math.PI; angle += Math.PI / 12) {
                        double x = radius * Math.cos(angle);
                        double z = radius * Math.sin(angle);
                        player.getWorld().spawnParticle(Particle.LARGE_SMOKE, center.clone().add(x, 0.2, z), 1, 0, 0, 0, 0);
                        player.getWorld().spawnParticle(Particle.DRAGON_BREATH, center.clone().add(x, 0.2, z), 1, 0, 0, 0, 0.05);
                    }
                }

                if (ticks > 40 && ticks % 8 == 0) {
                    player.getWorld().playSound(center, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.5f);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public static void playNetherLord(Player player, ByCircleGame plugin) {
        new BukkitRunnable() {
            private final int DURATION = 240;
            private int ticks = 0;
            private final Location center = player.getLocation();
            private final Map<Block, BlockData> originalBlocks = new HashMap<>();
            private final List<ArmorStand> skulls = new ArrayList<>();

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= DURATION) {
                    for (Map.Entry<Block, BlockData> entry : originalBlocks.entrySet()) {
                        entry.getKey().setBlockData(entry.getValue());
                    }
                    for (ArmorStand as : skulls) as.remove();
                    clearCleanupData(plugin, player);
                    this.cancel();
                    return;
                }

                if (ticks == 0) {
                    player.getWorld().playSound(center, Sound.ENTITY_WITHER_SPAWN, 1.5f, 1.0f);
                    for (int x = -3; x <= 3; x++) {
                        for (int z = -3; z <= 3; z++) {
                            if (x * x + z * z > 10) continue;
                            Block b = center.clone().add(x, -1, z).getBlock();
                            if (b.getType().isSolid()) {
                                originalBlocks.put(b, b.getBlockData());
                                b.setType(random.nextBoolean() ? Material.NETHERRACK : Material.MAGMA_BLOCK);
                            }
                        }
                    }
                    for (int i = 0; i < 3; i++) {
                        skulls.add(center.getWorld().spawn(center.clone().add(0, 5, 0), ArmorStand.class, as -> {
                            as.setGravity(false);
                            as.setVisible(false);
                            as.setMarker(true);
                            as.getEquipment().setHelmet(new ItemStack(Material.WITHER_SKELETON_SKULL));
                        }));
                    }
                    saveCleanupData(plugin, player, originalBlocks, skulls);
                }

                double orbitAngle = (double) ticks * Math.PI / 40;
                double orbitRadius = 2.5;
                for (int i = 0; i < skulls.size(); i++) {
                    ArmorStand skull = skulls.get(i);
                    double currentAngle = orbitAngle + (2 * Math.PI / skulls.size() * i);
                    double x = orbitRadius * Math.cos(currentAngle);
                    double z = orbitRadius * Math.sin(currentAngle);
                    double y = 1.5 + Math.sin((double) (ticks + i * 20) / 20.0);
                    Location orbitLoc = player.getLocation().clone().add(x, y, z);
                    skull.teleport(orbitLoc);
                    skull.getWorld().spawnParticle(Particle.FLAME, orbitLoc.clone().add(0, 0.4, 0), 1, 0, 0, 0, 0);
                    skull.getWorld().spawnParticle(Particle.SOUL, orbitLoc.clone().add(0, 0.4, 0), 1, 0, 0, 0, 0);
                }

                if (ticks % 30 == 0) {
                    player.getWorld().playSound(center, Sound.ENTITY_BLAZE_SHOOT, 1f, 1.2f);
                }

                if (ticks % 4 == 0) {
                    player.getWorld().spawnParticle(Particle.LAVA, player.getLocation(), 1);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public static void playCosmicSovereign(Player player, ByCircleGame plugin) {
        new BukkitRunnable() {
            private final int DURATION = 300;
            private int ticks = 0;
            private final Location center = player.getLocation();
            private final Map<Block, BlockData> originalBlocks = new HashMap<>();
            private final List<ArmorStand> planets = new ArrayList<>();

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= DURATION) {
                    player.setGravity(true);
                    for (ArmorStand planet : planets) planet.remove();
                    for (Map.Entry<Block, BlockData> entry : originalBlocks.entrySet()) {
                        entry.getKey().setBlockData(entry.getValue());
                    }
                    for(Entity entity : player.getNearbyEntities(30, 30, 30)) {
                        if(entity instanceof Player) ((Player) entity).removePotionEffect(PotionEffectType.DARKNESS);
                    }
                    clearCleanupData(plugin, player);
                    this.cancel();
                    return;
                }

                if (ticks == 0) {
                    player.setGravity(false);
                    player.getWorld().playSound(center, Sound.BLOCK_END_PORTAL_SPAWN, 2f, 0.5f);
                    for (Entity entity : player.getNearbyEntities(30, 30, 30)) {
                        if(entity instanceof Player) ((Player) entity).addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, DURATION, 0));
                    }
                    for (int x = -3; x <= 3; x++) {
                        for (int z = -3; z <= 3; z++) {
                            Block b = center.clone().add(x, -1, z).getBlock();
                            if (b.getType().isSolid()) {
                                originalBlocks.put(b, b.getBlockData());
                                b.setType(Material.END_STONE);
                            }
                        }
                    }
                    saveCleanupData(plugin, player, originalBlocks, new ArrayList<>());
                }

                if (ticks < 100) {
                    player.teleport(player.getLocation().add(0, 0.04, 0));
                    player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation(), 10, 1, 1, 1, 0.1);
                }

                if (ticks == 40) {
                    planets.add(createPlanet(Material.LAPIS_BLOCK));
                    planets.add(createPlanet(Material.REDSTONE_BLOCK));
                    planets.add(createPlanet(Material.GLOWSTONE));
                    saveCleanupData(plugin, player, originalBlocks, planets);
                }

                if(ticks > 40) {
                    double angle = (double) ticks * Math.PI / 120;
                    for (int i = 0; i < planets.size(); i++) {
                        ArmorStand planet = planets.get(i);
                        double radius = 3.0 + i * 2.0;
                        double speed = 1.0 - i * 0.3;
                        double x = radius * Math.cos(angle * speed);
                        double z = radius * Math.sin(angle * speed);
                        double y = Math.sin((double) (ticks + i * 40) / 40.0) * 0.5;
                        planet.teleport(player.getLocation().clone().add(x, y, z));
                        planet.getWorld().spawnParticle(Particle.END_ROD, planet.getLocation(), 1, 0, 0, 0, 0);
                    }
                }

                player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation(), 5, 3, 1, 3, 0);

                ticks++;
            }

            private ArmorStand createPlanet(Material material) {
                return center.getWorld().spawn(center.clone().add(0,5,0), ArmorStand.class, as -> {
                    as.setVisible(false);
                    as.setGravity(false);
                    as.setMarker(true);
                    as.getEquipment().setHelmet(new ItemStack(material));
                });
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public static void playWardensWrath(Player player, ByCircleGame plugin) {
        new BukkitRunnable() {
            private final int DURATION = 200;
            private int ticks = 0;
            private final Location center = player.getLocation();
            private final Map<Block, BlockData> originalBlocks = new HashMap<>();
            private final List<Warden> wardens = new ArrayList<>();

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= DURATION) {
                    for (Map.Entry<Block, BlockData> entry : originalBlocks.entrySet()) {
                        entry.getKey().setBlockData(entry.getValue());
                    }
                    for(Entity entity : player.getNearbyEntities(30, 30, 30)) {
                        if(entity instanceof Player) ((Player) entity).removePotionEffect(PotionEffectType.DARKNESS);
                    }
                    for (Warden w : wardens) {
                        w.remove();
                    }
                    this.cancel();
                    return;
                }

                World world = player.getWorld();

                if (ticks == 0) {
                    world.playSound(center, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 2f, 0.7f);
                    for (Entity entity : player.getNearbyEntities(30, 30, 30)) {
                        if(entity instanceof Player) ((Player) entity).addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, DURATION, 0));
                    }
                    for (int i = 0; i < 40; i++) {
                        int x = random.nextInt(11) - 5;
                        int z = random.nextInt(11) - 5;
                        Block b = center.clone().add(x, -1, z).getBlock();
                        if (b.getType().isSolid() && !originalBlocks.containsKey(b)) {
                            originalBlocks.put(b, b.getBlockData());
                        }
                    }
                    for (Block b : originalBlocks.keySet()){
                        b.setType(Material.SCULK);
                    }

                    Vector dir = player.getLocation().getDirection().normalize();
                    float playerYaw = player.getLocation().getYaw();
                    Vector leftDir = dir.clone().rotateAroundY(Math.toRadians(90));
                    Vector rightDir = dir.clone().rotateAroundY(Math.toRadians(-90));

                    Location frontLoc = center.clone().add(dir.multiply(5));
                    Location leftLoc = center.clone().add(leftDir.multiply(5));
                    Location rightLoc = center.clone().add(rightDir.multiply(5));

                    Warden frontWarden = (Warden) world.spawnEntity(frontLoc, org.bukkit.entity.EntityType.WARDEN);
                    setupWarden(frontWarden);
                    frontWarden.setRotation(playerYaw + 180, 0);
                    wardens.add(frontWarden);

                    Warden leftWarden = (Warden) world.spawnEntity(leftLoc, org.bukkit.entity.EntityType.WARDEN);
                    setupWarden(leftWarden);
                    leftWarden.setRotation(playerYaw + 90, 0);
                    wardens.add(leftWarden);

                    Warden rightWarden = (Warden) world.spawnEntity(rightLoc, org.bukkit.entity.EntityType.WARDEN);
                    setupWarden(rightWarden);
                    rightWarden.setRotation(playerYaw - 90, 0);
                    wardens.add(rightWarden);
                }

                if (ticks < 100) {
                    if (ticks % 20 == 0) {
                        float volume = 0.5f + (ticks / 100f);
                        world.playSound(center, Sound.ENTITY_WARDEN_HEARTBEAT, volume, 1f);
                    }
                    Location particleLoc = center.clone().add(random.nextDouble() * 8 - 4, 0, random.nextDouble() * 8 - 4);
                    if(particleLoc.getBlock().getRelative(org.bukkit.block.BlockFace.DOWN).getType() == Material.SCULK) {
                        world.spawnParticle(Particle.SCULK_SOUL, particleLoc.add(0,0.2,0), 1, 0,0,0,0);
                    }
                }

                if (ticks % 30 == 0 && ticks < 100 && ticks > 0) {
                    for (Warden w : wardens) {
                        world.playSound(w.getLocation(), Sound.ENTITY_WARDEN_ROAR, 1.5f, 1f);
                        world.spawnParticle(Particle.SCULK_CHARGE_POP, w.getLocation().add(0, 1, 0), 20, 1, 1, 1, 0.1);
                    }
                }

                if(ticks == 100) {
                    for (Warden w : wardens) {
                        world.playSound(w.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 1f);
                        Location start = w.getEyeLocation();
                        Location end = player.getEyeLocation();
                        Vector direction = end.toVector().subtract(start.toVector()).normalize();
                        double distance = start.distance(end);
                        for (double d = 0; d < distance; d += 0.5) {
                            Location pLoc = start.clone().add(direction.clone().multiply(d));
                            world.spawnParticle(Particle.SONIC_BOOM, pLoc, 1);
                        }
                    }
                }

                ticks++;
            }

            private void setupWarden(Warden w) {
                w.setAI(false);
                w.setCollidable(false);
                w.setInvulnerable(true);
                w.setSilent(true);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}