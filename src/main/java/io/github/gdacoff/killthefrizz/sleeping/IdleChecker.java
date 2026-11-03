package io.github.gdacoff.killthefrizz.sleeping;

import io.github.gdacoff.killthefrizz.config.EffectiveServer;
import io.github.gdacoff.killthefrizz.config.KtfConfig;
import io.github.gdacoff.killthefrizz.stats.KtfStats;
import io.github.gdacoff.killthefrizz.util.KtfLogger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Determines whether a mob is idle enough to skip AI evaluation, and tracks
 * temporary "force awake" timers triggered by external events (damage, block changes).
 *
 * <p>Checks are ordered cheapest-first so that the common case (mob near a player)
 * bails out in a handful of nanoseconds.</p>
 */
public final class IdleChecker {

    private static final Map<UUID, Long> WAKE_TIMERS = new ConcurrentHashMap<>();

    private IdleChecker() {
    }

    public static boolean shouldSleep(Mob mob) {
        return shouldSleep(mob, false);
    }

    public static boolean shouldSleep(Mob mob, boolean wasSleeping) {
        try {
            // 1. Global kill switches (cheapest possible).
            if (!KtfConfig.getEffectiveCommon().enabled()) {
                KtfStats.recordRejection(RejectionReason.DISABLED);
                return false;
            }
            if (KtfConfig.isAutoDisabled()) {
                KtfStats.recordRejection(RejectionReason.AUTO_DISABLED);
                return false;
            }
            EffectiveServer serverCfg = KtfConfig.getEffectiveServer();
            if (!serverCfg.sleepingEnabled()) {
                KtfStats.recordRejection(RejectionReason.SLEEPING_DISABLED);
                return false;
            }

            // 1b. Awake entity hot path early exit (Phase 7.7.1).
            // Awake mobs only evaluate sleep eligibility once every 16 ticks.
            // On intermediate ticks, skip all wake/sleep checks and return false immediately (remain awake).
            boolean evalTick = isAwakeEvalTick(mob.tickCount, mob.getId());
            if (!wasSleeping && !evalTick) {
                return false;
            }

            // 2. Cheap per-tick wake-up checks (must run every tick, no delay).
            if (isForceAwake(mob)) {
                KtfStats.recordRejection(RejectionReason.FORCE_AWAKE);
                return false;
            }
            if (mob.hurtTime > 0 || mob.isOnFire()) {
                KtfStats.recordRejection(RejectionReason.BURNING_OR_HURT_OR_EFFECTS);
                return false;
            }
            if (mob.getTarget() != null) {
                KtfStats.recordRejection(RejectionReason.HAS_TARGET);
                return false;
            }
            if (!mob.onGround() || mob.isFallFlying()) {
                KtfStats.recordRejection(RejectionReason.NOT_ON_GROUND);
                return false;
            }
            if (mob.isInWater() || mob.isInLava()) {
                KtfStats.recordRejection(RejectionReason.IN_FLUID);
                return false;
            }
            if (mob.getDeltaMovement().horizontalDistanceSqr() > serverCfg.maxIdleVelocitySqr()) {
                KtfStats.recordRejection(RejectionReason.VELOCITY);
                return false;
            }
            if (mob.isPassenger() || mob.isVehicle() || mob.isLeashed()) {
                KtfStats.recordRejection(RejectionReason.PASSENGER_OR_LEASHED);
                return false;
            }
            if (!mob.getActiveEffects().isEmpty()) {
                KtfStats.recordRejection(RejectionReason.BURNING_OR_HURT_OR_EFFECTS);
                return false;
            }
            if (!mob.getNavigation().isDone()) {
                KtfStats.recordRejection(RejectionReason.NAVIGATING);
                return false;
            }
            int heartbeat = serverCfg.heartbeatTicks();
            if (heartbeat > 0 && Math.floorMod((long) mob.tickCount + mob.getId(), (long) heartbeat) == 0) {
                KtfStats.recordRejection(RejectionReason.HEARTBEAT);
                return false;
            }

            // 3. Staggered re-eval interval check.
            int interval = 20;
            boolean isEvalTick = Math.floorMod((long) mob.tickCount + mob.getId(), (long) interval) == 0;
            if (wasSleeping && !isEvalTick) {
                KtfStats.CURRENT_TICK_SLEEPING.incrementAndGet();
                return true;
            }

            // 4. Expensive evaluation (runs on !wasSleeping or isEvalTick).
            int radius = serverCfg.sleepRadius();
            if (mob.level().hasNearbyAlivePlayer(mob.getX(), mob.getY(), mob.getZ(), radius)) {
                KtfStats.recordRejection(RejectionReason.PLAYER_NEARBY);
                return false;
            }
            if (serverCfg.skipTamedAndLeashed() && mob instanceof TamableAnimal t && t.isTame()) {
                KtfStats.recordRejection(RejectionReason.TAMED);
                return false;
            }
            if (mob instanceof Animal a && a.isInLove()) {
                KtfStats.recordRejection(RejectionReason.IN_LOVE);
                return false;
            }
            if (serverCfg.skipBabies() && mob.isBaby()) {
                KtfStats.recordRejection(RejectionReason.BABY);
                return false;
            }

            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
            if (id == null) {
                KtfStats.recordRejection(RejectionReason.BLACKLISTED);
                return false;
            }
            String idStr = id.toString();
            if (serverCfg.entityWhitelistMode()) {
                if (!serverCfg.entityWhitelistSet().contains(idStr)) {
                    KtfStats.recordRejection(RejectionReason.BLACKLISTED);
                    return false;
                }
            } else {
                if (!id.getNamespace().equals("minecraft") || serverCfg.entityBlacklistSet().contains(idStr)) {
                    KtfStats.recordRejection(RejectionReason.BLACKLISTED);
                    return false;
                }
            }

            KtfStats.CURRENT_TICK_SLEEPING.incrementAndGet();
            return true;
        } catch (Exception t) {
            KtfLogger.errorRateLimited("idle_checker_err", "Error in IdleChecker: " + t.getMessage());
            return false;
        }
    }

    /**
     * Returns true when the given mob should perform a full sleep-eligibility
     * evaluation on this tick. Awake mobs are evaluated once every 16 ticks,
     * staggered by entity id to spread load across ticks.
     */
    public static boolean isAwakeEvalTick(int tickCount, int entityId) {
        return ((tickCount + entityId) & 15) == 0;
    }

    public static void wakeUp(UUID uuid, long gameTime, int ticks) {
        if (uuid != null) {
            WAKE_TIMERS.put(uuid, gameTime + ticks);
        }
    }

    public static boolean isForceAwake(UUID uuid, long currentGameTime) {
        Long until = WAKE_TIMERS.get(uuid);
        if (until == null) {
            return false;
        }
        if (currentGameTime >= until) {
            WAKE_TIMERS.remove(uuid);
            return false;
        }
        return true;
    }

    public static void wakeUp(Mob mob, int ticks) {
        if (mob != null) {
            wakeUp(mob.getUUID(), mob.level().getGameTime(), ticks);
        }
    }

    public static boolean isForceAwake(Mob mob) {
        if (mob == null) {
            return false;
        }
        return isForceAwake(mob.getUUID(), mob.level().getGameTime());
    }
}
