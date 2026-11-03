package io.github.gdacoff.killthefrizz.profiling;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KtfProfilerTest {

    @BeforeEach
    void setUp() {
        KtfProfiler.reset();
    }

    @AfterEach
    void tearDown() {
        KtfProfiler.reset();
    }

    @Test
    void inactiveByDefault() {
        assertFalse(KtfProfiler.isRecording());
        KtfProfiler.Snapshot s = KtfProfiler.getSnapshot();
        assertNotNull(s);
        assertFalse(s.active());
        assertEquals(0, s.recordedTicks());
    }

    @Test
    void noAccumulationWhenInactive() {
        KtfProfiler.recordIdleCheck(1000L);
        KtfProfiler.recordServerAiStep(2000L);
        KtfProfiler.recordPushEntities(3000L);
        KtfProfiler.recordSkippedAi();

        KtfProfiler.Snapshot s = KtfProfiler.getSnapshot();
        assertEquals(0L, s.idleCheckCalls());
        assertEquals(0L, s.serverAiStepCalls());
        assertEquals(0L, s.pushEntitiesCalls());
        assertEquals(0L, s.skippedAiCalls());
    }

    @Test
    void accumulatesWhenActive() {
        KtfProfiler.start(100);
        assertTrue(KtfProfiler.isRecording());

        KtfProfiler.recordIdleCheck(100L);
        KtfProfiler.recordIdleCheck(300L);
        KtfProfiler.recordServerAiStep(500L);
        KtfProfiler.recordPushEntities(200L);

        KtfProfiler.Snapshot s = KtfProfiler.getSnapshot();
        assertTrue(s.active());
        assertEquals(2L, s.idleCheckCalls());
        assertEquals(400L, s.idleCheckTotalNanos());
        assertEquals(300L, s.idleCheckMaxNanos());
        assertEquals(200L, s.idleCheckAvgNanos());

        assertEquals(1L, s.serverAiStepCalls());
        assertEquals(500L, s.serverAiStepTotalNanos());
        assertEquals(500L, s.serverAiStepMaxNanos());

        assertEquals(1L, s.pushEntitiesCalls());
        assertEquals(200L, s.pushEntitiesTotalNanos());
    }

    @Test
    void separatesSkippedAndExecutedAi() {
        KtfProfiler.start(100);
        KtfProfiler.recordSkippedAi();
        KtfProfiler.recordSkippedAi();
        KtfProfiler.recordServerAiStep(1000L);

        KtfProfiler.Snapshot s = KtfProfiler.getSnapshot();
        assertEquals(2L, s.skippedAiCalls());
        assertEquals(1L, s.serverAiStepCalls());
    }

    @Test
    void autoStopsAfterSpecifiedTicks() {
        KtfProfiler.start(3);
        for (int i = 0; i < 3; i++) {
            KtfProfiler.onServerTickPost(null);
        }
        assertFalse(KtfProfiler.isRecording());
        KtfProfiler.Snapshot s = KtfProfiler.getSnapshot();
        assertEquals(3, s.recordedTicks());
        assertEquals(0, s.remainingTicks());
    }

    @Test
    void resetClearsEverything() {
        KtfProfiler.start(50);
        KtfProfiler.recordIdleCheck(100L);
        KtfProfiler.recordSkippedAi();
        KtfProfiler.onServerTickPost(null);

        KtfProfiler.reset();
        assertFalse(KtfProfiler.isRecording());
        KtfProfiler.Snapshot s = KtfProfiler.getSnapshot();
        assertEquals(0L, s.idleCheckCalls());
        assertEquals(0L, s.skippedAiCalls());
        assertEquals(0, s.recordedTicks());
        assertEquals(0, s.remainingTicks());
    }

    @Test
    void formatReportContainsKeyFields() {
        KtfProfiler.start(10);
        KtfProfiler.recordIdleCheck(1000L);
        KtfProfiler.recordServerAiStep(2000L);
        KtfProfiler.recordSkippedAi();

        String report = KtfProfiler.formatReport();
        assertTrue(report.contains("ACTIVE"), "report should show ACTIVE status");
        assertTrue(report.contains("IdleCheck"), "report should contain IdleCheck section");
        assertTrue(report.contains("serverAiStep"), "report should contain serverAiStep section");
        assertTrue(report.contains("pushEntities"), "report should contain pushEntities section");
        assertTrue(report.contains("Skipped AI calls"), "report should contain skipped AI count");

        KtfProfiler.stop();
        String stopped = KtfProfiler.formatReport();
        assertTrue(stopped.contains("STOPPED"), "report should show STOPPED after stop");
    }

    @Test
    void invalidTickCountClampedToOne() {
        KtfProfiler.start(0);
        assertTrue(KtfProfiler.isRecording());
        KtfProfiler.onServerTickPost(null);
        assertFalse(KtfProfiler.isRecording(), "start(0) should clamp to 1 tick and auto-stop");

        KtfProfiler.start(-5);
        assertTrue(KtfProfiler.isRecording());
        KtfProfiler.onServerTickPost(null);
        assertFalse(KtfProfiler.isRecording(), "start(-5) should clamp to 1 tick and auto-stop");
    }

    @Test
    void repeatedStartResetsAccumulators() {
        KtfProfiler.start(100);
        KtfProfiler.recordIdleCheck(500L);
        KtfProfiler.start(100);
        KtfProfiler.Snapshot s = KtfProfiler.getSnapshot();
        assertEquals(0L, s.idleCheckCalls());
        assertEquals(0L, s.idleCheckTotalNanos());
    }

    @Test
    void repeatedStopIsIdempotent() {
        KtfProfiler.start(100);
        KtfProfiler.recordIdleCheck(100L);
        KtfProfiler.stop();
        KtfProfiler.Snapshot first = KtfProfiler.getSnapshot();
        KtfProfiler.stop();
        KtfProfiler.Snapshot second = KtfProfiler.getSnapshot();
        assertEquals(first.idleCheckCalls(), second.idleCheckCalls());
        assertFalse(second.active());
    }

    @Test
    void maxTimeTracksLargestSample() {
        KtfProfiler.start(100);
        KtfProfiler.recordServerAiStep(100L);
        KtfProfiler.recordServerAiStep(5000L);
        KtfProfiler.recordServerAiStep(200L);
        KtfProfiler.Snapshot s = KtfProfiler.getSnapshot();
        assertEquals(5000L, s.serverAiStepMaxNanos());
        assertEquals(5300L, s.serverAiStepTotalNanos());
    }

    @Test
    void tickPostIgnoredWhenInactive() {
        KtfProfiler.onServerTickPost(null);
        KtfProfiler.Snapshot s = KtfProfiler.getSnapshot();
        assertEquals(0, s.recordedTicks());
    }
}
