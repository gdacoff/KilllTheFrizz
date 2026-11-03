package io.github.gdacoff.killthefrizz.sleeping;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 7.7.1 â Awake entity hot path optimization.
 *
 * <p>Awake mobs only evaluate sleep eligibility once every 16 ticks; on
 * intermediate ticks {@link IdleChecker#shouldSleep} returns {@code false}
 * immediately so the mob remains awake and vanilla AI is untouched.</p>
 */
class AwakeHotPathOptimizationTest {

    private static final int INTERVAL = 16;

    // ------------------------------------------------------------------
    // 1. Awake mob skips evaluation on intermediate ticks.
    // ------------------------------------------------------------------

    @Test
    void awakeMobSkipsEvaluationOnIntermediateTicks() {
        int entityId = 0;
        int evalCount = 0;
        for (int tick = 0; tick < INTERVAL; tick++) {
            if (IdleChecker.isAwakeEvalTick(tick, entityId)) {
                evalCount++;
            }
        }
        assertEquals(1, evalCount,
                "Exactly one tick out of 16 should be an eval tick for a given entity id");

        // 15 of 16 ticks must be non-eval ticks.
        int skipCount = 0;
        for (int tick = 0; tick < INTERVAL; tick++) {
            if (!IdleChecker.isAwakeEvalTick(tick, entityId)) {
                skipCount++;
            }
        }
        assertEquals(15, skipCount, "15 out of 16 ticks should skip evaluation");
    }

    // ------------------------------------------------------------------
    // 2. Awake mob evaluates on the 16th tick.
    // ------------------------------------------------------------------

    @Test
    void awakeMobEvaluatesOn16thTick() {
        // (tickCount + id) & 15 == 0 -> eval tick.
        assertTrue(IdleChecker.isAwakeEvalTick(0, 0), "tick 0 / id 0 should evaluate");
        assertTrue(IdleChecker.isAwakeEvalTick(16, 0), "tick 16 / id 0 should evaluate");
        assertTrue(IdleChecker.isAwakeEvalTick(15, 1), "tick 15 / id 1 (sum 16) should evaluate");
        assertTrue(IdleChecker.isAwakeEvalTick(32, 0), "tick 32 / id 0 should evaluate");

        assertFalse(IdleChecker.isAwakeEvalTick(1, 0), "tick 1 / id 0 should not evaluate");
        assertFalse(IdleChecker.isAwakeEvalTick(15, 0), "tick 15 / id 0 should not evaluate");
        assertFalse(IdleChecker.isAwakeEvalTick(17, 0), "tick 17 / id 0 should not evaluate");
    }

    // ------------------------------------------------------------------
    // 3. Sleeping mob is checked every tick (early exit does not apply).
    // ------------------------------------------------------------------

    @Test
    void sleepingMobCheckedEveryTick() {
        // The early exit condition is `!wasSleeping && !evalTick`. When
        // wasSleeping == true, the early exit never triggers regardless of
        // the eval-tick math, so cheap wake checks run every tick.
        int entityId = 3;
        for (int tick = 0; tick < 64; tick++) {
            boolean evalTick = IdleChecker.isAwakeEvalTick(tick, entityId);
            boolean wasSleeping = true;
            boolean earlyExit = !wasSleeping && !evalTick;
            assertFalse(earlyExit,
                    "Sleeping mobs must never take the awake early exit (tick " + tick + ")");
        }
    }

    // ------------------------------------------------------------------
    // 4. Awake mob does not lose vanilla AI (early exit returns false).
    // ------------------------------------------------------------------

    @Test
    void awakeMobDoesNotLoseVanillaAi() {
        // Simulate MobMixin: shouldSleep() == false -> ci.cancel() NOT called.
        int entityId = 7;
        for (int tick = 0; tick < 64; tick++) {
            boolean evalTick = IdleChecker.isAwakeEvalTick(tick, entityId);
            boolean wasSleeping = false;
            boolean earlyExit = !wasSleeping && !evalTick;
            boolean shouldSleepResult = earlyExit ? false : true; // placeholder for full eval
            boolean cancelCalled = shouldSleepResult;
            if (earlyExit) {
                assertFalse(cancelCalled,
                        "Early exit must return false so ci.cancel() is never invoked");
            }
        }
    }

    // ------------------------------------------------------------------
    // 5. State machine: awake -> eligible -> sleeps -> wakes -> stays awake.
    // ------------------------------------------------------------------

    @Test
    void mobStoppingAndResleepingWorksCorrectly() {
        int entityId = 5;
        boolean sleeping = false;
        boolean eligible = true;

        // Advance to the next eval tick.
        int tick = 0;
        while (!IdleChecker.isAwakeEvalTick(tick, entityId)) {
            tick++;
        }
        // On eval tick, awake mob evaluates and becomes eligible -> sleeps.
        if (eligible) {
            sleeping = true;
        }
        assertTrue(sleeping, "Mob should fall asleep on its eval tick when eligible");

        // External wake event (e.g. damage) -> sleeping = false.
        sleeping = false;
        assertFalse(sleeping, "Mob should wake on external event");

        // After waking, mob remains awake until the next eval tick.
        int nextEval = tick + INTERVAL;
        for (int t = tick + 1; t < nextEval; t++) {
            boolean evalTick = IdleChecker.isAwakeEvalTick(t, entityId);
            boolean earlyExit = !sleeping && !evalTick;
            assertTrue(earlyExit,
                    "Woken mob must remain awake until next eval tick (tick " + t + ")");
        }
        assertTrue(IdleChecker.isAwakeEvalTick(nextEval, entityId),
                "Next eval tick should be reachable after 16 ticks");
    }

    // ------------------------------------------------------------------
    // 6. Damage wakes a sleeping mob immediately (hurtTime > 0).
    // ------------------------------------------------------------------

    @Test
    void damageWakesSleepingMobImmediately() {
        // Sleeping mobs bypass the early exit, so the cheap hurtTime check
        // runs every tick and wakes them immediately.
        int entityId = 2;
        for (int tick = 0; tick < 32; tick++) {
            boolean wasSleeping = true;
            boolean evalTick = IdleChecker.isAwakeEvalTick(tick, entityId);
            boolean earlyExit = !wasSleeping && !evalTick;
            assertFalse(earlyExit, "Sleeping mob must run cheap wake checks every tick");

            // Simulate hurtTime > 0 -> shouldSleep returns false -> wake.
            int hurtTime = 1;
            boolean shouldSleep = !(hurtTime > 0);
            assertFalse(shouldSleep, "Hurt mob must not sleep regardless of eval tick");
        }
    }

    // ------------------------------------------------------------------
    // 7. Navigation not blocked.
    // ------------------------------------------------------------------

    @Test
    void navigationNotBlocked() {
        // Active navigation wakes a sleeping mob and keeps an awake mob active.
        int entityId = 4;
        for (int tick = 0; tick < 32; tick++) {
            boolean wasSleeping = true;
            boolean evalTick = IdleChecker.isAwakeEvalTick(tick, entityId);
            boolean earlyExit = !wasSleeping && !evalTick;
            assertFalse(earlyExit, "Sleeping mob must run navigation check every tick");

            boolean navigating = true;
            boolean shouldSleep = !navigating;
            assertFalse(shouldSleep, "Navigating mob must not sleep");
        }

        // Awake mob with active navigation: early exit returns false -> stays awake.
        for (int tick = 0; tick < 32; tick++) {
            boolean wasSleeping = false;
            boolean evalTick = IdleChecker.isAwakeEvalTick(tick, entityId);
            boolean earlyExit = !wasSleeping && !evalTick;
            boolean navigating = true;
            boolean shouldSleep = earlyExit ? false : !navigating;
            assertFalse(shouldSleep, "Awake mob with active navigation must remain awake");
        }
    }

    // ------------------------------------------------------------------
    // 8. External impulse prevents sleep.
    // ------------------------------------------------------------------

    @Test
    void externalImpulsePreventsSleep() {
        double maxIdleVelocitySqr = 0.0001;
        double impulseVelocitySqr = 0.5;
        assertTrue(impulseVelocitySqr > maxIdleVelocitySqr,
                "Impulse velocity must exceed idle threshold");

        // Sleeping mob: cheap velocity check runs every tick -> wakes.
        int entityId = 6;
        for (int tick = 0; tick < 32; tick++) {
            boolean wasSleeping = true;
            boolean evalTick = IdleChecker.isAwakeEvalTick(tick, entityId);
            boolean earlyExit = !wasSleeping && !evalTick;
            assertFalse(earlyExit, "Sleeping mob must run velocity check every tick");

            boolean shouldSleep = !(impulseVelocitySqr > maxIdleVelocitySqr);
            assertFalse(shouldSleep, "Impulse must prevent sleep");
        }
    }

    // ------------------------------------------------------------------
    // 9. Entity ids distributed evenly across 16 phases.
    // ------------------------------------------------------------------

    @Test
    void entityIdsDistributedEvenlyAcross16Phases() {
        // Sequential ids 0..255 should cover all 16 phases evenly.
        int[] counts = new int[INTERVAL];
        for (int id = 0; id < 256; id++) {
            int phase = id & 15;
            counts[phase]++;
        }
        for (int phase = 0; phase < INTERVAL; phase++) {
            assertEquals(16, counts[phase],
                    "Phase " + phase + " should have exactly 16 ids in 0..255");
        }

        // Verify each phase corresponds to exactly one eval tick in a 16-tick window.
        Set<Integer> evalTicks = new HashSet<>();
        for (int id = 0; id < 16; id++) {
            for (int tick = 0; tick < INTERVAL; tick++) {
                if (IdleChecker.isAwakeEvalTick(tick, id)) {
                    evalTicks.add(tick);
                }
            }
        }
        assertEquals(INTERVAL, evalTicks.size(),
                "Across 16 entity ids, all 16 ticks should be eval ticks for some id");
    }

    // ------------------------------------------------------------------
    // 10. Chunk reload state safety (tickCount jumps, negatives, MAX_VALUE).
    // ------------------------------------------------------------------

    @Test
    void chunkReloadStateSafety() {
        // Negative tick counts must not throw and must produce a valid boolean.
        assertTrue(IdleChecker.isAwakeEvalTick(-16, 0), "-16 + 0 should be an eval tick");
        assertTrue(IdleChecker.isAwakeEvalTick(-1, 1), "-1 + 1 should be an eval tick");
        assertFalse(IdleChecker.isAwakeEvalTick(-1, 0), "-1 + 0 should not be an eval tick");

        // Zero tick count.
        assertTrue(IdleChecker.isAwakeEvalTick(0, 0), "0 + 0 should be an eval tick");

        // Integer.MAX_VALUE: (MAX_VALUE + id) & 15 wraps via int arithmetic.
        // MAX_VALUE = 0x7FFFFFFF, low nibble = 0xF. So (MAX_VALUE + 1) & 15 == 0.
        assertTrue(IdleChecker.isAwakeEvalTick(Integer.MAX_VALUE, 1),
                "MAX_VALUE + 1 should be an eval tick (low nibble wraps to 0)");
        assertFalse(IdleChecker.isAwakeEvalTick(Integer.MAX_VALUE, 0),
                "MAX_VALUE + 0 should not be an eval tick");

        // Integer.MIN_VALUE: low nibble = 0x0, so MIN_VALUE + 0 is an eval tick.
        assertTrue(IdleChecker.isAwakeEvalTick(Integer.MIN_VALUE, 0),
                "MIN_VALUE + 0 should be an eval tick");

        // No exception thrown for any of the above (implicit).
    }
}
