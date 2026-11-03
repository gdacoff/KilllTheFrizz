package io.github.gdacoff.killthefrizz.sleeping;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the wake-up timer logic in {@link IdleChecker}.
 */
class IdleCheckerTest {

    @Test
    void unknownMobIsNotForceAwake() {
        UUID id = UUID.randomUUID();
        assertFalse(IdleChecker.isForceAwake(id, 100L),
                "An entity that was never woken should not be force-awake");
    }

    @Test
    void wakeUpMakesMobForceAwake() {
        UUID id = UUID.randomUUID();
        IdleChecker.wakeUp(id, 100L, 50);
        assertTrue(IdleChecker.isForceAwake(id, 100L),
                "A mob woken for 50 ticks should be force-awake immediately");
    }

    @Test
    void wakeUpTimerExpiresAfterTicks() {
        UUID id = UUID.randomUUID();
        // Wake at game time 100 for 50 ticks -> awake until 150.
        IdleChecker.wakeUp(id, 100L, 50);
        assertTrue(IdleChecker.isForceAwake(id, 100L), "Should be awake at t=100");

        // Still awake just before expiry.
        assertTrue(IdleChecker.isForceAwake(id, 149L), "Should still be awake at t=149");

        // Expired at exactly the deadline (>= until).
        assertFalse(IdleChecker.isForceAwake(id, 150L), "Should be expired at t=150");
    }

    @Test
    void expiredTimerIsRemovedAndStaysAwakeFalse() {
        UUID id = UUID.randomUUID();
        IdleChecker.wakeUp(id, 10L, 5);
        assertTrue(IdleChecker.isForceAwake(id, 10L));

        // Advance past expiry; first check removes the entry.
        assertFalse(IdleChecker.isForceAwake(id, 20L));
        // Subsequent checks remain false (entry removed).
        assertFalse(IdleChecker.isForceAwake(id, 20L));
    }

    @Test
    void wakeUpCanExtendExistingTimer() {
        UUID id = UUID.randomUUID();
        IdleChecker.wakeUp(id, 100L, 10); // until 110
        IdleChecker.wakeUp(id, 100L, 100); // until 200 (overwrites)

        assertTrue(IdleChecker.isForceAwake(id, 150L),
                "Extended timer should keep the mob awake past the original deadline");

        assertFalse(IdleChecker.isForceAwake(id, 200L),
                "Extended timer should expire at the new deadline");
    }

    @Test
    void wakeUpWithNullMobIsNoOp() {
        // Should not throw.
        IdleChecker.wakeUp(null, 50);
        assertFalse(IdleChecker.isForceAwake(null));
    }

    @Test
    void wakeUpWithZeroTicksExpiresImmediately() {
        UUID id = UUID.randomUUID();
        IdleChecker.wakeUp(id, 100L, 0);
        // until == 100, and currentGameTime >= until -> not awake.
        assertFalse(IdleChecker.isForceAwake(id, 100L),
                "A zero-tick wake-up should expire immediately");
    }

    // ------------------------------------------------------------------
    // Staggered-evaluation interval math (mirrors IdleChecker.shouldSleep).
    // These tests validate the pure arithmetic used to decide whether a
    // sleeping mob re-evaluates on a given tick, including overflow safety.
    // ------------------------------------------------------------------

    private static final int EVAL_INTERVAL = 20;

    private static boolean isEvalTick(int tickCount, int entityId) {
        return Math.floorMod((long) tickCount + entityId, (long) EVAL_INTERVAL) == 0;
    }

    @Test
    void evalTickIsTrueAtIntervalBoundaries() {
        // tickCount + id == 0, 20, 40 ... are eval ticks.
        assertTrue(isEvalTick(0, 0), "tick 0 / id 0 should be an eval tick");
        assertTrue(isEvalTick(20, 0), "tick 20 / id 0 should be an eval tick");
        assertTrue(isEvalTick(15, 5), "tick 15 / id 5 (sum 20) should be an eval tick");
    }

    @Test
    void evalTickIsFalseBetweenBoundaries() {
        assertFalse(isEvalTick(1, 0), "tick 1 / id 0 should not be an eval tick");
        assertFalse(isEvalTick(19, 0), "tick 19 / id 0 should not be an eval tick");
        assertFalse(isEvalTick(10, 5), "tick 10 / id 5 (sum 15) should not be an eval tick");
    }

    @Test
    void evalTickHandlesLargeTickCountWithoutOverflow() {
        // Integer.MAX_VALUE + id would overflow int; the long cast + floorMod must
        // still produce a correct, non-negative result.
        int large = Integer.MAX_VALUE;
        // floorMod(MAX_VALUE + 0, 20) = MAX_VALUE % 20 = 2147483647 % 20 = 7 -> not eval.
        assertFalse(isEvalTick(large, 0),
                "MAX_VALUE tick should not overflow into a false eval tick");
        // Choose an id so that (MAX_VALUE + id) is a multiple of 20.
        // MAX_VALUE % 20 == 7, so id == 13 makes the sum divisible by 20.
        assertTrue(isEvalTick(large, 13),
                "MAX_VALUE + 13 should be a valid eval tick without overflow");
    }

    @Test
    void evalTickHandlesNegativeTickCount() {
        // floorMod keeps the result non-negative even for negative sums.
        assertTrue(isEvalTick(-20, 0), "negative tick -20 should still be an eval tick");
        assertTrue(isEvalTick(-1, 1), "tick -1 / id 1 (sum 0) should be an eval tick");
        assertFalse(isEvalTick(-1, 0), "tick -1 / id 0 should not be an eval tick");
    }

    // ------------------------------------------------------------------
    // Heartbeat math (mirrors IdleChecker.shouldSleep cheap wake-up path).
    // A sleeping mob must wake on heartbeat ticks even when heartbeat is not
    // a multiple of the 20-tick eval interval.
    // ------------------------------------------------------------------

    private static boolean isHeartbeatTick(int tickCount, int entityId, int heartbeat) {
        return heartbeat > 0
                && Math.floorMod((long) tickCount + entityId, (long) heartbeat) == 0;
    }

    @Test
    void heartbeatTriggersWakeupForNonMultipleOf20() {
        // heartbeat = 10: sum divisible by 10 must wake.
        assertTrue(isHeartbeatTick(10, 0, 10), "tick 10 / id 0 with heartbeat 10 should wake");
        assertTrue(isHeartbeatTick(5, 5, 10), "tick 5 / id 5 (sum 10) with heartbeat 10 should wake");
        assertFalse(isHeartbeatTick(7, 0, 10), "tick 7 / id 0 with heartbeat 10 should not wake");

        // heartbeat = 25: not a multiple of 20.
        assertTrue(isHeartbeatTick(25, 0, 25), "tick 25 / id 0 with heartbeat 25 should wake");
        assertTrue(isHeartbeatTick(20, 5, 25), "tick 20 / id 5 (sum 25) with heartbeat 25 should wake");
        assertFalse(isHeartbeatTick(24, 0, 25), "tick 24 / id 0 with heartbeat 25 should not wake");

        // heartbeat = 30: not a multiple of 20.
        assertTrue(isHeartbeatTick(30, 0, 30), "tick 30 / id 0 with heartbeat 30 should wake");
        assertTrue(isHeartbeatTick(15, 15, 30), "tick 15 / id 15 (sum 30) with heartbeat 30 should wake");
        assertFalse(isHeartbeatTick(29, 0, 30), "tick 29 / id 0 with heartbeat 30 should not wake");
    }

    @Test
    void heartbeatDisabledWhenZeroOrNegative() {
        assertFalse(isHeartbeatTick(0, 0, 0), "heartbeat 0 must never trigger");
        assertFalse(isHeartbeatTick(0, 0, -5), "negative heartbeat must never trigger");
    }

    @Test
    void heartbeatHandlesLargeTickCountWithoutOverflow() {
        int large = Integer.MAX_VALUE;
        // MAX_VALUE % 10 == 7, so id == 3 makes the sum divisible by 10.
        assertTrue(isHeartbeatTick(large, 3, 10),
                "MAX_VALUE + 3 with heartbeat 10 should wake without overflow");
        assertFalse(isHeartbeatTick(large, 0, 10),
                "MAX_VALUE + 0 with heartbeat 10 should not wake");
    }

    // ------------------------------------------------------------------
    // Navigation check: a mob with an active path must never sleep.
    // We verify the RejectionReason enum exposes NAVIGATING so the cheap
    // wake-up path can record it (the actual Mob.getNavigation() call is
    // exercised in-game; here we assert the contract exists).
    // ------------------------------------------------------------------

    @Test
    void navigatingRejectionReasonExists() {
        // Ensures the enum constant used by the navigation wake-up check exists.
        RejectionReason reason = RejectionReason.valueOf("NAVIGATING");
        org.junit.jupiter.api.Assertions.assertNotNull(reason,
                "RejectionReason.NAVIGATING must exist for the navigation wake-up check");
    }

    @Test
    void shouldSleepOverloadExists() {
        // The two-arg overload must be present and callable with a null-safe path.
        // A null mob is not expected in production, but the overload signature must
        // exist for the sleeping-state fast path used by the mixin.
        java.lang.reflect.Method m;
        try {
            m = IdleChecker.class.getMethod("shouldSleep",
                    net.minecraft.world.entity.Mob.class, boolean.class);
        } catch (NoSuchMethodException e) {
            throw new AssertionError("IdleChecker.shouldSleep(Mob, boolean) overload is missing", e);
        }
        assertTrue(java.lang.reflect.Modifier.isStatic(m.getModifiers()),
                "shouldSleep(Mob, boolean) must be static");
    }

    @Test
    void rejectionReasonCountersRecordCorrectly() {
        io.github.gdacoff.killthefrizz.stats.KtfStats.reset();
        org.junit.jupiter.api.Assertions.assertEquals(0L,
                io.github.gdacoff.killthefrizz.stats.KtfStats.REJECTION_REASONS.get(RejectionReason.VELOCITY).get());

        io.github.gdacoff.killthefrizz.stats.KtfStats.recordRejection(RejectionReason.VELOCITY);
        io.github.gdacoff.killthefrizz.stats.KtfStats.recordRejection(RejectionReason.VELOCITY);
        io.github.gdacoff.killthefrizz.stats.KtfStats.recordRejection(RejectionReason.PLAYER_NEARBY);

        org.junit.jupiter.api.Assertions.assertEquals(2L,
                io.github.gdacoff.killthefrizz.stats.KtfStats.REJECTION_REASONS.get(RejectionReason.VELOCITY).get());
        org.junit.jupiter.api.Assertions.assertEquals(1L,
                io.github.gdacoff.killthefrizz.stats.KtfStats.REJECTION_REASONS.get(RejectionReason.PLAYER_NEARBY).get());

        io.github.gdacoff.killthefrizz.stats.KtfStats.reset();
        org.junit.jupiter.api.Assertions.assertEquals(0L,
                io.github.gdacoff.killthefrizz.stats.KtfStats.REJECTION_REASONS.get(RejectionReason.VELOCITY).get());
    }
}
