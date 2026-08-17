package org.byauth.manager;

import org.byauth.ByCircleGame;
import org.byauth.game.Arena;
import org.byauth.game.Team;
import org.byauth.utils.SettingsManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class ArenaManager {

    private final ByCircleGame plugin;
    private final SettingsManager settings;
    private final Arena arena;
    private Location center;
    private final List<Location> lootContainerLocations = new ArrayList<>();
    private final List<Location> waitingPodBlocks = new ArrayList<>();
    private final List<Location> borderBlockLocations = new ArrayList<>();
    private List<Team> currentTeams = new ArrayList<>();

    private final int radius;
    private final int centerRadius;
    private final int arenaY;
    private final int lavaY;

    public ArenaManager(ByCircleGame plugin, Arena arena) {
        this.plugin = plugin;
        this.settings = plugin.getSettingsManager();
        this.arena = arena;
        this.radius = settings.ARENA_RADIUS;
        this.centerRadius = settings.ARENA_CENTER_RADIUS;
        this.arenaY = settings.ARENA_Y_LEVEL;
        this.lavaY = settings.LAVA_Y_LEVEL;
    }

    public void generateArena(Location startLocation, List<Team> activeTeams) {
        this.center = startLocation.clone();
        this.center.setY(arenaY);
        borderBlockLocations.clear();
        paintArenaFloor(activeTeams);
        generateBarrierWalls();
    }

    private void generateBarrierWalls() {
        if (center == null) return;
        World world = center.getWorld();
        int wallRadius = radius + 1;

        for (int x = -wallRadius; x <= wallRadius; x++) {
            for (int z = -wallRadius; z <= wallRadius; z++) {
                double distanceSq = x * x + z * z;

                if (distanceSq >= radius * radius && distanceSq <= wallRadius * wallRadius) {
                    for (int y = lavaY; y < arenaY + 50; y++) {
                        world.getBlockAt(center.getBlockX() + x, y, center.getBlockZ() + z).setType(Material.BARRIER);
                    }
                }
            }
        }

        int ceilingY = arenaY + 50;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double distanceSq = x * x + z * z;
                if (distanceSq <= radius * radius) {
                    world.getBlockAt(center.getBlockX() + x, ceilingY, center.getBlockZ() + z).setType(Material.BARRIER);
                }
            }
        }
    }

    public void resetArena(List<Team> activeTeams) {
        if (center == null) {
            if (arena.getCenterLocation() != null) {
                this.center = arena.getCenterLocation();
                this.center.setY(arenaY);
            } else {
                return;
            }
        }

        clearEntitiesInArena();

        World world = center.getWorld();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = arenaY; y < arenaY + 50; y++) {
                    Block block = world.getBlockAt(center.getBlockX() + x, y, center.getBlockZ() + z);
                    if (block.getType() != Material.BARRIER) {
                        block.setType(Material.AIR);
                    }
                }
            }
        }

        paintArenaFloor(activeTeams);
        generateBarrierWalls();
    }

    private void paintArenaFloor(List<Team> activeTeams) {
        if (center == null) return;
        World world = center.getWorld();
        int teamCount = Team.values().length;
        List<Team> allTeams = List.of(Team.values());
        double sliceAngle = 2 * Math.PI / teamCount;

        borderBlockLocations.clear();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double distanceSq = x * x + z * z;
                if (distanceSq > (radius + 0.5) * (radius + 0.5)) {
                    continue;
                }

                boolean isCenterSquare = Math.abs(x) <= centerRadius && Math.abs(z) <= centerRadius;

                if (isCenterSquare) {
                    world.getBlockAt(center.getBlockX() + x, arenaY - 2, center.getBlockZ() + z).setType(Material.BEDROCK);
                    world.getBlockAt(center.getBlockX() + x, arenaY - 1, center.getBlockZ() + z).setType(Material.AIR);
                } else {
                    Block floorBlock = world.getBlockAt(center.getBlockX() + x, arenaY - 1, center.getBlockZ() + z);
                    double angle = Math.atan2(z, x);
                    if (angle < 0) angle += 2 * Math.PI;
                    int teamIndex = (int) (angle / sliceAngle);
                    if (teamIndex >= 0 && teamIndex < teamCount) {
                        Team sliceTeam = allTeams.get(teamIndex);
                        if (activeTeams.contains(sliceTeam)) {
                            floorBlock.setType(sliceTeam.getConcreteMaterial());
                        } else {
                            floorBlock.setType(Material.WHITE_CONCRETE);
                        }
                    }
                }

                Block blockAtLava = world.getBlockAt(center.getBlockX() + x, lavaY, center.getBlockZ() + z);
                if (blockAtLava.getType() != Material.LAVA) blockAtLava.setType(Material.LAVA, false);

                Block blockAtBarrier = world.getBlockAt(center.getBlockX() + x, lavaY - 1, center.getBlockZ() + z);
                if (blockAtBarrier.getType() != Material.BARRIER) blockAtBarrier.setType(Material.BARRIER);
            }
        }

        for (int i = 0; i < teamCount; i++) {
            double borderAngle = i * sliceAngle;
            int x1 = 0;
            int z1 = 0;
            int x2 = (int)Math.round(radius * Math.cos(borderAngle));
            int z2 = (int)Math.round(radius * Math.sin(borderAngle));

            int dx = Math.abs(x2 - x1);
            int sx = x1 < x2 ? 1 : -1;
            int dz = -Math.abs(z2 - z1);
            int sz = z1 < z2 ? 1 : -1;
            int err = dx + dz;

            while (true) {
                if (Math.abs(x1) > centerRadius || Math.abs(z1) > centerRadius) {
                    Block borderBlock = world.getBlockAt(center.getBlockX() + x1, arenaY - 1, center.getBlockZ() + z1);
                    if(borderBlock.getType() != Material.BEDROCK) {
                        borderBlock.setType(Material.COBWEB);
                        borderBlockLocations.add(borderBlock.getLocation());
                    }
                }

                if (x1 == x2 && z1 == z2) break;
                int e2 = 2 * err;
                if (e2 >= dz) {
                    err += dz;
                    x1 += sx;
                }
                if (e2 <= dx) {
                    err += dx;
                    z1 += sz;
                }
            }
        }
    }

    public void destroyArena() {
        if (center == null) {
            if (arena.getCenterLocation() != null) {
                this.center = arena.getCenterLocation();
                this.center.setY(arenaY);
            } else {
                return;
            }
        }
        World world = center.getWorld();
        clearWaitingPods();

        for (Location loc : lootContainerLocations) {
            loc.getBlock().setType(Material.AIR);
        }
        lootContainerLocations.clear();
        borderBlockLocations.clear();

        int clearRadius = radius + 5;
        for (int x = -clearRadius; x <= clearRadius; x++) {
            for (int z = -clearRadius; z <= clearRadius; z++) {
                for (int y = lavaY - 1; y < arenaY + 55; y++) {
                    Location loc = new Location(world, center.getBlockX() + x, y, center.getBlockZ() + z);
                    loc.getBlock().setType(Material.AIR);
                }
            }
        }
    }

    private void clearEntitiesInArena() {
        if (center == null) return;
        World world = center.getWorld();
        double clearRadiusSq = Math.pow(radius + 10.0, 2);

        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof Player)) {
                if (entity.getLocation().distanceSquared(center) < clearRadiusSq) {
                    entity.remove();
                }
            }
        }
    }

    public void placeCenterLootContainers() {
        if (center == null) return;
        lootContainerLocations.clear();

        Location shulkerCenter = center.clone();
        shulkerCenter.setY(arenaY - 1);

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Location loc = shulkerCenter.clone().add(x, 0, z);
                loc.getBlock().setType(Material.SHULKER_BOX);
                lootContainerLocations.add(loc);
            }
        }
    }

    public void clearLootContainers() {
        for (Location loc : lootContainerLocations) {
            loc.getBlock().setType(Material.AIR);
        }
        lootContainerLocations.clear();
    }

    public void fillLootContainers(int round) {
        if (lootContainerLocations.isEmpty()) return;

        List<ItemStack> lootTable = plugin.getLootManager().getLootForRound(round);
        if (lootTable.isEmpty()) return;

        Random random = new Random();
        for (Location loc : lootContainerLocations) {
            if (loc.getBlock().getState() instanceof ShulkerBox) {
                ShulkerBox shulkerBox = (ShulkerBox) loc.getBlock().getState();
                Inventory inv = shulkerBox.getInventory();
                inv.clear();

                int itemsToPlace = 5 + random.nextInt(4);
                Collections.shuffle(lootTable);
                for (int i = 0; i < itemsToPlace && i < lootTable.size(); i++) {
                    int slot = random.nextInt(inv.getSize());
                    if (inv.getItem(slot) == null) {
                        inv.setItem(slot, lootTable.get(i).clone());
                    }
                }
            }
        }
    }

    public void generateWaitingPods(List<Team> activeTeams) {
        if (center == null) return;
        clearWaitingPods();

        for (Team team : activeTeams) {
            Location podCenter = getPodLocation(team);

            for (int x = -2; x <= 2; x++) {
                for (int y = -1; y <= 3; y++) {
                    for (int z = -2; z <= 2; z++) {
                        podCenter.clone().add(x, y, z).getBlock().setType(Material.AIR);
                    }
                }
            }

            for (int x = -2; x <= 2; x++) {
                for (int y = -1; y <= 3; y++) {
                    for (int z = -2; z <= 2; z++) {
                        boolean isShell = Math.abs(x) == 2 || y == 3 || Math.abs(z) == 2 || y == -1;
                        if (isShell) {
                            Location blockLoc = podCenter.clone().add(x, y, z);
                            blockLoc.getBlock().setType(Material.BARRIER);
                            waitingPodBlocks.add(blockLoc);
                        }
                    }
                }
            }
        }
    }

    public void clearWaitingPods() {
        for (Location loc : waitingPodBlocks) {
            loc.getBlock().setType(Material.AIR);
        }
        waitingPodBlocks.clear();
    }

    public void convertTeamSliceToWhite(Team team) {
        if (center == null) return;
        World world = center.getWorld();

        int teamCount = Team.values().length;
        List<Team> allTeams = List.of(Team.values());
        double sliceAngle = 2 * Math.PI / teamCount;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double distanceSq = x * x + z * z;
                if (distanceSq >= (radius + 0.5) * (radius + 0.5) || (Math.abs(x) <= centerRadius && Math.abs(z) <= centerRadius)) {
                    continue;
                }

                double angle = Math.atan2(z, x);
                if (angle < 0) angle += 2 * Math.PI;

                int teamIndex = (int) (angle / sliceAngle);
                if (teamIndex >= 0 && teamIndex < teamCount && allTeams.get(teamIndex) == team) {
                    Block blockToChange = world.getBlockAt(center.getBlockX() + x, arenaY - 1, center.getBlockZ() + z);
                    if (blockToChange.getType() != Material.COBWEB && blockToChange.getType() != Material.BEDROCK) {
                        blockToChange.setType(Material.WHITE_CONCRETE);
                    }
                }
            }
        }
    }

    public void setAllSlicesToWhite() {
        if (center == null) return;
        World world = center.getWorld();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double distanceSq = x * x + z * z;
                if (distanceSq > (radius + 0.5) * (radius + 0.5)) {
                    continue;
                }

                if (Math.abs(x) <= centerRadius && Math.abs(z) <= centerRadius) {
                    continue;
                }
                Location loc = new Location(world, center.getBlockX() + x, arenaY - 1, center.getBlockZ() + z);
                if (loc.getBlock().getType() != Material.BEDROCK && loc.getBlock().getType() != Material.COBWEB) {
                    loc.getBlock().setType(Material.WHITE_CONCRETE);
                }
            }
        }
        for (Location loc : borderBlockLocations) {
            loc.getBlock().setType(Material.WHITE_CONCRETE);
        }
    }

    public Location getPodLocation(Team team) {
        if (center == null) return null;

        int teamCount = Team.values().length;
        List<Team> allTeams = List.of(Team.values());
        double sliceAngle = 2 * Math.PI / teamCount;

        int teamIndex = allTeams.indexOf(team);
        if (teamIndex == -1) return center;

        double midAngle = (teamIndex * sliceAngle) + (sliceAngle / 2);
        double podRadius = radius - settings.POD_RADIUS_OFFSET;

        double x = center.getX() + podRadius * Math.cos(midAngle);
        double z = center.getZ() + podRadius * Math.sin(midAngle);

        return new Location(center.getWorld(), x, arenaY + settings.POD_Y_OFFSET, z);
    }

    public Team getTeamFromLocation(Location location) {
        if (center == null) return null;

        List<Team> allTeams = List.of(Team.values());
        int teamCount = allTeams.size();
        if (teamCount == 0) return null;

        int relativeX = location.getBlockX() - center.getBlockX();
        int relativeZ = location.getBlockZ() - center.getBlockZ();

        if (Math.abs(relativeX) <= centerRadius && Math.abs(relativeZ) <= centerRadius) {
            return null;
        }

        double angle = Math.atan2(relativeZ, relativeX);
        if (angle < 0) angle += 2 * Math.PI;

        double sliceAngle = 2 * Math.PI / teamCount;
        int teamIndex = (int) (angle / sliceAngle);

        if (teamIndex >= 0 && teamIndex < teamCount) {
            return allTeams.get(teamIndex);
        }

        return null;
    }

    public Location getCenter() {
        return center;
    }

    public int getLavaY() {
        return lavaY;
    }

    public int getArenaY() {
        return arenaY;
    }
}