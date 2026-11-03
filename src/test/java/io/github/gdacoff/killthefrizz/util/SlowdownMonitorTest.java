package io.github.gdacoff.killthefrizz.util;

import io.github.gdacoff.killthefrizz.config.KtfConfig;
import io.github.gdacoff.killthefrizz.stats.KtfStats;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlowdownMonitorTest {

    @BeforeEach
    void setUp() {
        SlowdownMonitor.resetForTest();
        KtfStats.reset();
    }

    @AfterEach
    void tearDown() {
        SlowdownMonitor.resetForTest();
        KtfStats.reset();
    }

    @Test
    void normalMsptDoesNotAutoDisable() {
        for (int i = 0; i < 200; i++) {
            SlowdownMonitor.evaluateForTest(15.0);
        }
        assertFalse(KtfConfig.isAutoDisabled(), "Normal MSPT should not trigger auto-disable");
    }

    @Test
    void prolongedHighMsptTriggersAutoDisable() {
        // 99 ticks: not disabled yet.
        for (int i = 0; i < 99; i++) {
            SlowdownMonitor.evaluateForTest(50.0);
        }
        assertFalse(KtfConfig.isAutoDisabled(), "99 slow ticks should not trigger disable (needs 100)");

        // 100th tick: triggers auto-disable.
        SlowdownMonitor.evaluateForTest(50.0);
        assertTrue(KtfConfig.isAutoDisabled(), "100 slow ticks should trigger auto-disable");
    }

    @Test
    void autoDisabledRecoversAfterProlongedFastTicks() {
        // Trigger disable.
        for (int i = 0; i < 100; i++) {
            SlowdownMonitor.evaluateForTest(55.0);
        }
        assertTrue(KtfConfig.isAutoDisabled());

        // Fast ticks: needs 200 ticks to recover.
        for (int i = 0; i < 199; i++) {
            SlowdownMonitor.evaluateForTest(20.0);
        }
        assertTrue(KtfConfig.isAutoDisabled(), "199 fast ticks should not re-enable yet");

        SlowdownMonitor.evaluateForTest(20.0);
        assertFalse(KtfConfig.isAutoDisabled(), "200 fast ticks should re-enable the mod");
    }

    @Test
    void onServerTickPreAndPostMeasureElapsedTime() {
        // A normal tick: Pre records start, Post measures elapsed and publishes stats.
        SlowdownMonitor.onServerTickPre(null);
        // Busy-wait a tiny amount so elapsed > 0 (Post ignores zero-elapsed ticks).
        long spinUntil = System.nanoTime() + 1_000_000L; // ~1ms
        while (System.nanoTime() < spinUntil) {
            // spin
        }
        SlowdownMonitor.onServerTickPost(null);

        // No exception, and the start timestamp was consumed (a second Post without
        // a matching Pre must be a no-op rather than measuring from a stale start).
        SlowdownMonitor.onServerTickPost(null);
    }

    @Test
    void onServerTickPostWithoutPreIsNoOp() {
        // resetForTest clears tickStartNanos to 0; a Post with no Pre must not throw
        // and must not evaluate a bogus elapsed time.
        SlowdownMonitor.resetForTest();
        SlowdownMonitor.onServerTickPost(null);
        assertFalse(KtfConfig.isAutoDisabled(),
                "A Post without a matching Pre must not trigger auto-disable");
    }

    @Test
    void resetForTestClearsTimingState() {
        // Prime the start timestamp, then reset; a subsequent Post must be a no-op.
        SlowdownMonitor.onServerTickPre(null);
        SlowdownMonitor.resetForTest();
        SlowdownMonitor.onServerTickPost(null);
        assertFalse(KtfConfig.isAutoDisabled(),
                "resetForTest should clear the pending tick start so Post is a no-op");
    }

    @Test
    void publishStatsRefreshesSleepingAndSavedTime() {
        // Record 100 skipped AI ticks this tick.
        for (int i = 0; i < 100; i++) {
            KtfStats.recordSkippedAiTick();
        }
        assertEquals(100L, KtfStats.CURRENT_TICK_SLEEPING.get());
        assertEquals(100L, KtfStats.SKIPPED_AI_TICKS.get());

        // Publish stats.
        SlowdownMonitor.publishStats();

        // Published counters updated; current-tick accumulator reset.
        assertEquals(100L, KtfStats.SLEEPING_ENTITIES.get());
        assertEquals(0L, KtfStats.CURRENT_TICK_SLEEPING.get());
        assertEquals(8L, KtfStats.SAVED_MS.get()); // 100 * 0.08 = 8 ms
    }
}
