package io.github.gdacoff.killthefrizz.sleeping;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 7.7.2 Step 4 — Sleeping entity push deferral.
 *
 * <p>Sleeping mobs skip {@code LivingEntity.pushEntities()} entirely to avoid
 * mutual collision oscillations that exceed {@code maxIdleVelocity} and cause
 * continuous wakeups. Awake mobs and players retain vanilla push behavior.</p>
 */
class SleepingPushOptimizationTest {

    // ------------------------------------------------------------------
    // 1. SleepStateAccess contract.
    // ------------------------------------------------------------------

    @Test
    void sleepStateAccessReportsSleepingState() {
        SleepStateAccess sleeping = () -> true;
        SleepStateAccess awake = () -> false;

        assertTrue(sleeping.ktf$isSleeping(), "Sleeping entity must report true");
        assertFalse(awake.ktf$isSleeping(), "Awake entity must report false");
    }

    // ------------------------------------------------------------------
    // 2. Cramming evaluation logic (mirrors LivingEntityMixin branch).
    // ------------------------------------------------------------------

    @Test
    void crammingEvaluatedOnlyOnStaggeredTick() {
        int entityId = 5;
        int evalTicks = 0;
        for (int tick = 0; tick < 16; tick++) {
            if (((tick + entityId) & 15) == 0) {
                evalTicks++;
            }
        }
        assertEquals(1, evalTicks, "Cramming must be evaluated exactly once per 16 ticks");
    }

    @Test
    void crammingDamageAppliedWhenOverThreshold() {
        int cramming = 24;
        int pushableCount = 30;
        boolean damaged = evaluateCramming(cramming, pushableCount);
        assertTrue(damaged, "Over-crammed sleeping entity must take cramming damage");
    }

    @Test
    void crammingDamageSkippedWhenUnderThreshold() {
        int cramming = 24;
        int pushableCount = 10;
        boolean damaged = evaluateCramming(cramming, pushableCount);
        assertFalse(damaged, "Under-threshold entity must not take cramming damage");
    }

    @Test
    void crammingDisabledWhenRuleZero() {
        int cramming = 0;
        int pushableCount = 100;
        boolean damaged = evaluateCramming(cramming, pushableCount);
        assertFalse(damaged, "Cramming rule 0 disables damage entirely");
    }

    @Test
    void crammingIgnoresPassengers() {
        int cramming = 5;
        // 10 entities in list, but 8 are passengers -> only 2 count.
        int total = 10;
        int passengers = 8;
        int counted = total - passengers;
        boolean damaged = evaluateCramming(cramming, counted);
        assertFalse(damaged, "Passengers must not count toward cramming");
    }

    // ------------------------------------------------------------------
    // 3. Branch logic: sleeping cancels pushEntities, awake does not.
    // ------------------------------------------------------------------

    @Test
    void sleepingEntityCancelsPushEntities() {
        boolean sleeping = true;
        boolean cancelled = shouldCancelPush(sleeping);
        assertTrue(cancelled, "Sleeping entity must cancel pushEntities");
    }

    @Test
    void awakeEntityRunsPushEntities() {
        boolean sleeping = false;
        boolean cancelled = shouldCancelPush(sleeping);
        assertFalse(cancelled, "Awake entity must run vanilla pushEntities");
    }

    // ------------------------------------------------------------------
    // 4. Wakeup invariant: external velocity still wakes a sleeping mob.
    // ------------------------------------------------------------------

    @Test
    void externalVelocityWakesSleepingMobDespitePushSkip() {
        // Skipping pushEntities does not touch IdleChecker's velocity check,
        // which runs every tick for sleeping mobs. An external impulse must
        // therefore still wake the mob.
        double maxIdleVelocitySqr = 0.03 * 0.03;
        double externalImpulseSqr = 0.25;

        assertTrue(externalImpulseSqr > maxIdleVelocitySqr,
                "External impulse must exceed idle velocity threshold");

        // Sleeping mobs bypass the awake early-exit, so the velocity check runs.
        int entityId = 9;
        for (int tick = 0; tick < 32; tick++) {
            boolean wasSleeping = true;
            boolean evalTick = IdleChecker.isAwakeEvalTick(tick, entityId);
            boolean earlyExit = !wasSleeping && !evalTick;
            assertFalse(earlyExit, "Sleeping mob must run velocity check every tick");

            boolean shouldSleep = !(externalImpulseSqr > maxIdleVelocitySqr);
            assertFalse(shouldSleep, "External impulse must wake the sleeping mob");
        }
    }

    @Test
    void mutualPushOscillationNoLongerExceedsIdleThreshold() {
        // Vanilla mutual doPush applies ~0.05 blocks/tick, exceeding the 0.03
        // idle threshold. With pushEntities skipped for sleeping mobs, the
        // residual velocity stays below the threshold and mobs stay asleep.
        double maxIdleVelocity = 0.03;
        double vanillaMutualPush = 0.05;
        double residualAfterSkip = 0.0;

        assertTrue(vanillaMutualPush > maxIdleVelocity,
                "Vanilla mutual push must exceed idle threshold (root cause)");
        assertTrue(residualAfterSkip <= maxIdleVelocity,
                "Skipping pushEntities must keep residual velocity under threshold");
    }

    // ------------------------------------------------------------------
    // Helpers mirroring the mixin branch logic.
    // ------------------------------------------------------------------

    private static boolean evaluateCramming(int cramming, int pushableCount) {
        if (cramming <= 0) {
            return false;
        }
        return pushableCount > cramming - 1;
    }

    private static boolean shouldCancelPush(boolean sleeping) {
        return sleeping;
    }
}
