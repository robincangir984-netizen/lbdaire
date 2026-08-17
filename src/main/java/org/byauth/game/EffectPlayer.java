package org.byauth.game;

import org.byauth.ByCircleGame;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Arrow;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;

public class EffectPlayer {

    private static final Random random = new Random();

    public static void playKillEffect(Location location, String effectId) {
        if (effectId == null) return;

        switch (effectId) {
            case "kan_girdabi":
                location.getWorld().spawnParticle(Particle.DUST, location.add(0, 1, 0), 75, 0.5, 0.5, 0.5, new Particle.DustOptions(Color.RED, 1.2F));
                break;
            case "totem_patlamasi":
                location.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, location.add(0, 1, 0), 75, 0.6, 0.6, 0.6, 0.1);
                break;
            case "ruh_emici":
                location.getWorld().spawnParticle(Particle.SOUL, location.add(0, 0.5, 0), 40, 0.7, 0.7, 0.7, 0.05);
                break;
        }
    }

    public static void playArrowTrail(Arrow arrow, String effectId, ByCircleGame plugin) {
        if (effectId == null) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                if (arrow == null || arrow.isDead() || arrow.isOnGround()) {
                    this.cancel();
                    return;
                }

                switch (effectId) {
                    case "alev_izi":
                        arrow.getWorld().spawnParticle(Particle.FLAME, arrow.getLocation(), 1, 0, 0, 0, 0);
                        break;
                    case "kalp_izi":
                        arrow.getWorld().spawnParticle(Particle.HEART, arrow.getLocation(), 1, 0, 0, 0, 0);
                        break;
                    case "muzik_notalari":
                        arrow.getWorld().spawnParticle(Particle.NOTE, arrow.getLocation(), 1, random.nextFloat(), 0, 0, 1);
                        break;
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}