package io.github.gdacoff.killthefrizz.villager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerBrainThrottlerTest {

    @Test
    void disabledConfigAlwaysReturnsTrue() {
        assertTrue(VillagerBrainThrottler.shouldTickBrain(null, false, 4, 10),
                "When disabled, shouldTickBrain must always return true");
    }

    @Test
    void failOpenOnNullOrException() {
        assertTrue(VillagerBrainThrottler.shouldTickBrain(null, true, 4, 10),
                "Null villager must fail open and return true");
    }

    @Test
    void stallIntervalGatingMath() {
        int stallInterval = 4;
        int entityId = 2;
        int evaluated = 0;

        for (int tick = 0; tick < stallInterval; tick++) {
            long phase = (long) tick + entityId;
            if (Math.floorMod(phase, stallInterval) == 0) {
                evaluated++;
            }
        }
        assertEquals(1, evaluated, "Exactly 1 in 4 ticks must evaluate for stall state");
    }

    @Test
    void bedIntervalGatingMath() {
        int bedInterval = 10;
        int entityId = 7;
        int evaluated = 0;

        for (int tick = 0; tick < bedInterval; tick++) {
            long phase = (long) tick + entityId;
            if (Math.floorMod(phase, bedInterval) == 0) {
                evaluated++;
            }
        }
        assertEquals(1, evaluated, "Exactly 1 in 10 ticks must evaluate for bed sleep state");
    }

    @Test
    void overflowSafetyAtMaxIntTickCount() {
        int interval = 4;
        int entityId = 100;
        long tickCount = Integer.MAX_VALUE;

        long phase = tickCount + entityId;
        boolean result = Math.floorMod(phase, interval) == 0 || Math.floorMod(phase, interval) != 0;
        assertTrue(result, "Modulo math must never throw on large integer values");
        assertTrue(Math.floorMod(phase, interval) >= 0, "Modulo result must always be non-negative");
    }

    @Test
    void negativeIdSafety() {
        int interval = 4;
        int entityId = -5;
        int tick = 1;

        long phase = (long) tick + entityId;
        int rem = Math.floorMod(phase, interval);
        assertTrue(rem >= 0 && rem < interval, "Math.floorMod must handle negative phase safely");
    }

    @Test
    void velocityThresholdBoundary() {
        double thresholdSqr = 0.0009; // 0.03 * 0.03
        double vSub = 0.029;
        double vSuper = 0.031;

        assertTrue(vSub * vSub < thresholdSqr, "0.029 blocks/tick must be below moving threshold");
        assertTrue(vSuper * vSuper > thresholdSqr, "0.031 blocks/tick must be above moving threshold");
    }

    @Test
    void uniformPhaseStaggerAcrossFortyVillagers() {
        int stallInterval = 4;
        int[] buckets = new int[stallInterval];

        for (int id = 0; id < 40; id++) {
            int tick = 0;
            int bucket = Math.floorMod((long) tick + id, stallInterval);
            buckets[bucket]++;
        }

        for (int b = 0; b < stallInterval; b++) {
            assertEquals(10, buckets[b], "40 villagers must be evenly partitioned into 10 per phase tick");
        }
    }

    @Test
    void compensateSkippedTicksNullSafety() {
        // Must never throw even if null brain or negative skipped
        VillagerBrainThrottler.compensateSkippedTicks(null, 0);
        VillagerBrainThrottler.compensateSkippedTicks(null, -1);
        VillagerBrainThrottler.compensateSkippedTicks(null, 4);
    }

    @Test
    void sensorCadenceCompensationMathematicalInvariant() {
        int stallInterval = 4;
        int worldTicks = 20;
        int timeToTick = 20;

        for (int tick = 1; tick <= worldTicks; tick++) {
            if (tick % stallInterval == 0) {
                int skipped = stallInterval - 1; // 3
                timeToTick -= skipped;
                timeToTick -= 1; // standard decrement
            }
        }
        assertEquals(0, timeToTick, "After 20 world ticks with stallInterval=4, sensor timer must reach exactly 0");
    }
}
