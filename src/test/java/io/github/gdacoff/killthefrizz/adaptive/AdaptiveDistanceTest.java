package io.github.gdacoff.killthefrizz.adaptive;

import io.github.gdacoff.killthefrizz.config.EffectiveClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure JUnit 5 tests for {@link AdaptiveDistanceManager}.
 *
 * <p>These tests do not start a Minecraft window. They exercise the numeric
 * logic (rolling FPS, hysteresis, clamping, cooldown), the override parser and
 * the particle-limit check by injecting an {@link EffectiveClient} through the
 * package-private test hook.</p>
 */
class AdaptiveDistanceTest {

    private AdaptiveDistanceManager mgr;

    @BeforeEach
    void setUp() {
        mgr = AdaptiveDistanceManager.getInstance();
        // Reset rolling samples and cooldown state between tests.
        mgr.resetForTest();
    }

    @AfterEach
    void tearDown() {
        AdaptiveDistanceManager.setEffectiveClientForTest(null);
        mgr.resetForTest();
    }

    // ------------------------------------------------------------------
    // Rolling FPS
    // ------------------------------------------------------------------

    @Test
    void averageFpsIsZeroWithoutSamples() {
        assertEquals(0.0, mgr.getAverageFps(), 1e-9);
    }

    @Test
    void averageFpsComputesFromFrameTimes() {
        // 10 ms per frame => 100 FPS.
        for (int i = 0; i < 10; i++) {
            mgr.recordFrame(10_000_000L);
        }
        assertEquals(100.0, mgr.getAverageFps(), 1e-6);
    }

    @Test
    void averageFpsIgnoresNonPositiveSamples() {
        mgr.recordFrame(0L);
        mgr.recordFrame(-5L);
        assertEquals(0.0, mgr.getAverageFps(), 1e-9);
    }

    @Test
    void averageFpsRollsOverWindow() {
        // Fill the window with 10 ms frames (100 FPS).
        for (int i = 0; i < 60; i++) {
            mgr.recordFrame(10_000_000L);
        }
        assertEquals(100.0, mgr.getAverageFps(), 1e-6);
        // Now push 20 ms frames (50 FPS) to fully replace the window.
        for (int i = 0; i < 60; i++) {
            mgr.recordFrame(20_000_000L);
        }
        assertEquals(50.0, mgr.getAverageFps(), 1e-6);
    }

    // ------------------------------------------------------------------
    // Hysteresis + clamping
    // ------------------------------------------------------------------

    @Test
    void lowFpsDecreasesDistance() {
        AdaptiveDistanceManager.setEffectiveClientForTest(client(true, 8, 32, 120, 15, 1, 30));
        // 50 FPS is well below 120*(1-0.15)=102.
        for (int i = 0; i < 60; i++) {
            mgr.recordFrame(20_000_000L);
        }
        int next = mgr.updateViewDistance(16, 1_000_000L);
        assertEquals(15, next, "distance should step down by 1");
    }

    @Test
    void highFpsIncreasesDistance() {
        AdaptiveDistanceManager.setEffectiveClientForTest(client(true, 8, 32, 120, 15, 1, 30));
        // 200 FPS is above 120*(1+0.15)=138.
        for (int i = 0; i < 60; i++) {
            mgr.recordFrame(5_000_000L);
        }
        int next = mgr.updateViewDistance(16, 1_000_000L);
        assertEquals(17, next, "distance should step up by 1");
    }

    @Test
    void fpsWithinHysteresisBandDoesNotChange() {
        AdaptiveDistanceManager.setEffectiveClientForTest(client(true, 8, 32, 120, 15, 1, 30));
        // 120 FPS is exactly the target, inside the dead zone.
        for (int i = 0; i < 60; i++) {
            mgr.recordFrame(8_333_333L);
        }
        int next = mgr.updateViewDistance(16, 1_000_000L);
        assertEquals(16, next, "distance should not change inside hysteresis band");
    }

    @Test
    void distanceClampsToMin() {
        AdaptiveDistanceManager.setEffectiveClientForTest(client(true, 8, 32, 120, 15, 1, 30));
        for (int i = 0; i < 60; i++) {
            mgr.recordFrame(20_000_000L);
        }
        int next = mgr.updateViewDistance(8, 1_000_000L);
        assertEquals(8, next, "distance must not go below minViewDistance");
    }

    @Test
    void distanceClampsToMax() {
        AdaptiveDistanceManager.setEffectiveClientForTest(client(true, 8, 32, 120, 15, 1, 30));
        for (int i = 0; i < 60; i++) {
            mgr.recordFrame(5_000_000L);
        }
        int next = mgr.updateViewDistance(32, 1_000_000L);
        assertEquals(32, next, "distance must not exceed maxViewDistance");
    }

    @Test
    void disabledAdaptiveReturnsUnchanged() {
        AdaptiveDistanceManager.setEffectiveClientForTest(client(false, 8, 32, 120, 15, 1, 30));
        for (int i = 0; i < 60; i++) {
            mgr.recordFrame(20_000_000L);
        }
        assertEquals(16, mgr.updateViewDistance(16, 1_000_000L));
    }

    // ------------------------------------------------------------------
    // Cooldown
    // ------------------------------------------------------------------

    @Test
    void cooldownBlocksRapidChanges() {
        AdaptiveDistanceManager.setEffectiveClientForTest(client(true, 8, 32, 120, 15, 1, 30));
        for (int i = 0; i < 60; i++) {
            mgr.recordFrame(20_000_000L);
        }
        long t0 = 1_000_000L;
        int first = mgr.updateViewDistance(16, t0);
        assertEquals(15, first, "first change should apply");

        // 5 seconds later: still within 30s cooldown, no change.
        int second = mgr.updateViewDistance(15, t0 + 5_000L);
        assertEquals(15, second, "change within cooldown must be suppressed");

        // 31 seconds later: cooldown elapsed, change applies.
        int third = mgr.updateViewDistance(15, t0 + 31_000L);
        assertEquals(14, third, "change after cooldown should apply");
    }

    // ------------------------------------------------------------------
    // Particle limit
    // ------------------------------------------------------------------

    @Test
    void particleLimitReachedAtThreshold() {
        AdaptiveDistanceManager.setEffectiveClientForTest(client(true, 8, 32, 120, 15, 1, 30));
        // maxParticles is 3000 in the helper below.
        assertFalse(mgr.isParticleLimitReached(2999));
        assertTrue(mgr.isParticleLimitReached(3000));
        assertTrue(mgr.isParticleLimitReached(3001));
    }

    @Test
    void particleLimitDisabledWhenAdaptiveOff() {
        AdaptiveDistanceManager.setEffectiveClientForTest(client(false, 8, 32, 120, 15, 1, 30));
        assertFalse(mgr.isParticleLimitReached(999_999));
    }

    // ------------------------------------------------------------------
    // Override parser
    // ------------------------------------------------------------------

    @Test
    void parseOverridesValidEntries() {
        Map<String, Integer> m = AdaptiveDistanceManager.parseOverrides(
                List.of("minecraft:zombie=32", "minecraft:cow=16"));
        assertEquals(2, m.size());
        assertEquals(32, m.get("minecraft:zombie"));
        assertEquals(16, m.get("minecraft:cow"));
    }

    @Test
    void parseOverridesSkipsInvalidEntries() {
        Map<String, Integer> m = AdaptiveDistanceManager.parseOverrides(
                List.of("noequals", "=16", "minecraft:pig=", "minecraft:pig=abc",
                        "minecraft:pig=-5", "minecraft:pig=0", "minecraft:sheep=24"));
        assertEquals(1, m.size(), "only the valid entry should remain");
        assertEquals(24, m.get("minecraft:sheep"));
    }

    @Test
    void parseOverridesHandlesNullAndEmpty() {
        assertTrue(AdaptiveDistanceManager.parseOverrides(null).isEmpty());
        assertTrue(AdaptiveDistanceManager.parseOverrides(List.of()).isEmpty());
    }

    @Test
    void parseOverridesTrimsWhitespace() {
        Map<String, Integer> m = AdaptiveDistanceManager.parseOverrides(
                List.of("  minecraft:zombie = 32  "));
        assertEquals(32, m.get("minecraft:zombie"));
    }

    // ------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------

    private static EffectiveClient client(boolean adaptive, int minView, int maxView,
                                          int targetFps, int hysteresis, int step, int cooldown) {
        return new EffectiveClient(
                false, 16, 2, 2, true, true,
                List.of("minecraft:player"), false,
                adaptive, minView, maxView, targetFps, hysteresis, step, cooldown,
                64, List.of(), 48, 3000);
    }
}
