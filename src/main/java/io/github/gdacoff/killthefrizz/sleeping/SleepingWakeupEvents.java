package io.github.gdacoff.killthefrizz.sleeping;

import io.github.gdacoff.killthefrizz.config.KtfConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.util.List;

/**
 * Event handlers that wake nearby sleeping mobs when relevant world events occur.
 *
 * <p>Only <em>active player-driven</em> modifications (block break, block place,
 * explosion) trigger wakeups. We deliberately do NOT listen to
 * {@code BlockEvent.NeighborNotifyEvent} because it fires thousands of times per
 * tick during chunk loading/generation, causing a {@code getEntitiesOfClass} storm.</p>
 */
public final class SleepingWakeupEvents {

    private SleepingWakeupEvents() {
    }

    public static void onLivingDamage(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof Mob mob) {
            IdleChecker.wakeUp(mob, KtfConfig.getEffectiveServer().recentDamageWakeTicks());
        }
    }

    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level) {
            wakeNearby(level, event.getPos());
        }
    }

    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof Level level) {
            wakeNearby(level, event.getPos());
        }
    }

    /**
     * Wakes mobs affected by an explosion. We first wake any mobs directly in the
     * affected-entity list (cheap, no query), then perform exactly ONE bounding-box
     * query covering all affected blocks instead of one query per block.
     */
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (level == null) {
            return;
        }

        // Wake mobs directly affected by the explosion (no query needed).
        for (Entity entity : event.getAffectedEntities()) {
            if (entity instanceof Mob mob) {
                IdleChecker.wakeUp(mob, 40);
            }
        }

        // Wake mobs near any affected block with a single bounding-box query.
        List<BlockPos> blocks = event.getAffectedBlocks();
        if (blocks != null && !blocks.isEmpty()) {
            int radius = KtfConfig.getEffectiveServer().blockChangeWakeRadius();
            AABB box = computeExplosionBoundingBox(blocks, radius);
            if (box != null) {
                for (Mob mob : level.getEntitiesOfClass(Mob.class, box)) {
                    IdleChecker.wakeUp(mob, 40);
                }
            }
        }
    }

    /**
     * Computes a single bounding box covering all affected blocks, inflated by
     * {@code radius}. Returns {@code null} if the block list is null or empty.
     */
    public static AABB computeExplosionBoundingBox(List<BlockPos> blocks, int radius) {
        if (blocks == null || blocks.isEmpty()) {
            return null;
        }
        int safeRadius = Math.max(0, Math.min(32, radius));
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : blocks) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new AABB(minX - safeRadius, minY - safeRadius, minZ - safeRadius,
                        maxX + 1 + safeRadius, maxY + 1 + safeRadius, maxZ + 1 + safeRadius);
    }

    private static void wakeNearby(Level level, BlockPos pos) {
        int radius = KtfConfig.getEffectiveServer().blockChangeWakeRadius();
        AABB box = new AABB(pos).inflate(radius);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box)) {
            IdleChecker.wakeUp(mob, 40);
        }
    }
}
