package io.github.gdacoff.killthefrizz.collision;

import io.github.gdacoff.killthefrizz.config.KtfConfig;
import io.github.gdacoff.killthefrizz.util.KtfLogger;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Throttles {@link LivingEntity#pushEntities()} broadphase queries for dense
 * stationary mobs.
 *
 * <p>Vanilla runs {@code pushEntities()} every tick for every living entity,
 * issuing a broadphase AABB query against the world. In mob farms and dense
 * pens this dominates the entity tick. This throttler halves the query rate
 * (20 Hz -&gt; 10 Hz) for stationary, awake, non-interacting mobs while leaving
 * every gameplay-relevant case at full rate:</p>
 * <ul>
 *   <li>players nearby (&lt; 6 blocks) -&gt; full rate (player collisions)</li>
 *   <li>hurt or burning -&gt; full rate (knockback / fire spread)</li>
 *   <li>moving -&gt; full rate (crowd dynamics)</li>
 *   <li>passenger / vehicle / leashed -&gt; full rate (transport &amp; leash physics)</li>
 * </ul>
 *
 * <p>Sleeping entities are handled by the Phase 7.7.2 deferred push logic in
 * {@code LivingEntityMixin} and never reach this throttler.</p>
 *
 * <p>Fail-open: any exception returns {@code true} (full rate) so a bug here
 * can never freeze collisions.</p>
 */
public final class CollisionThrottler {

    /** Squared horizontal velocity threshold (0.20^2) to ignore passive collision jitter while maintaining full rate for walking/knockback. */
    private static final double STATIONARY_VELOCITY_SQR = 0.04;

    /** Radius (blocks) within which a player forces full-rate collision processing. */
    private static final double PLAYER_PROXIMITY = 6.0;

    /** Stagger period in ticks. 2 = 10 Hz for stationary mobs. */
    private static final int STAGGER_PERIOD = 2;

    private CollisionThrottler() {
    }

    /**
     * Returns {@code true} if {@code pushEntities()} should run this tick.
     *
     * @param entity the living entity being ticked; may be {@code null} (fail-open)
     * @return {@code true} to run vanilla push logic, {@code false} to skip this tick
     */
    public static boolean shouldPushEntities(LivingEntity entity) {
        try {
            if (entity == null) {
                return true;
            }
            if (!KtfConfig.getEffectiveCommon().enabled() || KtfConfig.isAutoDisabled()
                    || !KtfConfig.getEffectiveServer().sleepingEnabled()) {
                return true;
            }

            if (entity.level().hasNearbyAlivePlayer(
                    entity.getX(), entity.getY(), entity.getZ(), PLAYER_PROXIMITY)) {
                return true;
            }
            if (entity.hurtTime > 0 || entity.isOnFire()) {
                return true;
            }
            if (entity.getDeltaMovement().horizontalDistanceSqr() > STATIONARY_VELOCITY_SQR) {
                return true;
            }
            if (entity.isPassenger() || entity.isVehicle()
                    || (entity instanceof Mob mob && mob.isLeashed())) {
                return true;
            }

            long phase = (long) entity.tickCount + entity.getId();
            return Math.floorMod(phase, STAGGER_PERIOD) == 0;
        } catch (Throwable t) {
            KtfLogger.warnRateLimited("collision-throttler",
                    "CollisionThrottler failed, falling back to full-rate push: {}", t.toString());
            return true;
        }
    }
}
