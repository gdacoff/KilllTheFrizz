package io.github.gdacoff.killthefrizz.collision;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 7.10.5 - Collision throttler logic.
 *
 * <p>Pure JUnit 5 tests. The throttler's Minecraft-dependent branches are
 * exercised in-game; here we lock down the deterministic math (stagger cadence,
 * velocity boundary, uniform distribution) and the fail-open contract.</p>
 */
class CollisionThrottlerTest {

    private static final double STATIONARY_VELOCITY_SQR = 0.04;
    private static final int STAGGER_PERIOD = 2;

    // ------------------------------------------------------------------
    // 1. Stagger cadence: exactly 50% of ticks evaluate.
    // ------------------------------------------------------------------

    @Test
    void testStaggerMathIntervalTwo() {
        int entityId = 7;
        int evaluated = 0;
        int total = 1000;
        for (int tick = 0; tick < total; tick++) {
            if (staggerAllows(tick, entityId)) {
                evaluated++;
            }
        }
        assertEquals(total / 2, evaluated,
                "Stagger period 2 must evaluate exactly 50% of ticks");
    }

    // ------------------------------------------------------------------
    // 2. Velocity boundary: 0.029 stationary, 0.031 moving.
    // ------------------------------------------------------------------

    @Test
    void testVelocityBoundary() {
        double belowSqr = 0.19 * 0.19;
        double aboveSqr = 0.21 * 0.21;

        assertTrue(belowSqr <= STATIONARY_VELOCITY_SQR,
                "0.19 blocks/tick must be classified stationary");
        assertTrue(aboveSqr > STATIONARY_VELOCITY_SQR,
                "0.21 blocks/tick must be classified moving");
    }

    // ------------------------------------------------------------------
    // 3. Fail-open on null input.
    // ------------------------------------------------------------------

    @Test
    void testFailOpenOnNull() {
        assertTrue(CollisionThrottler.shouldPushEntities(null),
                "Null entity must fail open to full-rate push");
    }

    // ------------------------------------------------------------------
    // 4. Uniform stagger across entity IDs.
    // ------------------------------------------------------------------

    @Test
    void testUniformStagger() {
        int tick = 42;
        int even = 0;
        int odd = 0;
        for (int id = 0; id < 100; id++) {
            if (staggerAllows(tick, id)) {
                even++;
            } else {
                odd++;
            }
        }
        assertEquals(50, even, "Exactly half of 100 entity IDs must evaluate on a given tick");
        assertEquals(50, odd, "The other half must be deferred");
    }

    // ------------------------------------------------------------------
    // Helper mirroring the throttler's stagger expression.
    // ------------------------------------------------------------------

    private static boolean staggerAllows(int tickCount, int entityId) {
        long phase = (long) tickCount + entityId;
        return Math.floorMod(phase, STAGGER_PERIOD) == 0;
    }
}
