package org.quorix.qHomingBow;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class QHomingBow extends JavaPlugin implements Listener {

    private final Map<Arrow, UUID> homingArrows = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Homing Bow активирован!");

        new HomingTask(homingArrows, this).runTaskTimer(this, 1L, 1L);
    }

    @EventHandler
    public void onPlayerShoot(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();

        if (!(projectile instanceof Arrow arrow)) return;
        if (!(arrow.getShooter() instanceof Player player)) return;

        if (!player.hasPermission("homingbow.use")) return;

        Location playerLoc = player.getLocation();
        Entity target = findNearestMob(playerLoc, 30);

        if (target != null) {
            homingArrows.put(arrow, target.getUniqueId());
            arrow.setMetadata("HomingArrow", new FixedMetadataValue(this, true));
            player.playSound(playerLoc, Sound.ENTITY_BLAZE_SHOOT, 1f, 1.5f);
        }
    }

    @EventHandler
    public void onArrowHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Arrow arrow)) return;
        if (!arrow.hasMetadata("HomingArrow")) return;

        Entity target = event.getEntity();

        ProjectileSource shooter = arrow.getShooter();
        if (!(shooter instanceof Player player)) return;

        Location hitLocation = target.getLocation();

        player.playSound(player.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1f, 1f);

        hitLocation.getWorld().spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, hitLocation, 50, 1, 1, 1, 0.5);
    }

    private Entity findNearestMob(Location loc, double radius) {
        List<Entity> nearbyEntities = new ArrayList<>(loc.getWorld().getNearbyEntities(loc, radius, radius, radius));
        nearbyEntities.removeIf(entity -> !(entity instanceof Mob));

        return nearbyEntities.stream()
                .min(Comparator.comparingDouble(entity -> entity.getLocation().distance(loc)))
                .orElse(null);
    }

    private static class HomingTask extends BukkitRunnable {
        private final Map<Arrow, UUID> homingArrows;
        private final JavaPlugin plugin;

        public HomingTask(Map<Arrow, UUID> homingArrows, JavaPlugin plugin) {
            this.homingArrows = homingArrows;
            this.plugin = plugin;
        }

        @Override
        public void run() {
            Iterator<Map.Entry<Arrow, UUID>> iterator = homingArrows.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<Arrow, UUID> entry = iterator.next();
                Arrow arrow = entry.getKey();
                UUID targetId = entry.getValue();

                if (arrow.isDead() || arrow.isOnGround() || !arrow.isValid()) {
                    iterator.remove();
                    continue;
                }

                Entity target = Bukkit.getEntity(targetId);
                if (!(target instanceof Mob) || !target.isValid()) {
                    iterator.remove();
                    continue;
                }

                World world = arrow.getWorld();
                Location arrowLoc = arrow.getLocation();
                Location targetLoc = target.getLocation().add(0, 1, 0);

                double distance = arrowLoc.distance(targetLoc);

                if (distance <= 7.0) {
                    Vector directionToTarget = targetLoc.toVector().subtract(arrowLoc.toVector()).normalize();
                    Vector currentVelocity = arrow.getVelocity();

                    double turnSpeed = 0.3;
                    Vector newVelocity = currentVelocity.normalize()
                            .multiply(1 - turnSpeed)
                            .add(directionToTarget.multiply(turnSpeed))
                            .normalize()
                            .multiply(currentVelocity.length());

                    arrow.setVelocity(newVelocity);

                    Vector direction = targetLoc.toVector().subtract(arrowLoc.toVector());
                    float yaw = (float) Math.atan2(direction.getZ(), direction.getX()) * (180f / (float) Math.PI) - 90f;
                    float pitch = (float) Math.asin(direction.getY() / distance) * (180f / (float) Math.PI);
                    arrow.setRotation(yaw, pitch);

                    Vector backward = directionToTarget.clone().multiply(-0.2);
                    Location particleLocation = arrow.getLocation().clone().add(0, 0.2, 0);

                    for (int i = 0; i < 3; i++) {
                        world.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, particleLocation, 1, 0, 0, 0, 0);
                        particleLocation.add(backward);
                    }
                } else {
                    Location particleLocation = arrow.getLocation().clone().add(0, 0.2, 0);
                    world.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, particleLocation, 1, 0, 0, 0, 0);
                }
            }
        }
    }
}