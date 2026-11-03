package io.github.gdacoff.killthefrizz.profiling;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerBrainProfilerTest {

    @BeforeEach
    void setUp() {
        KtfProfiler.reset();
    }

    @AfterEach
    void tearDown() {
        KtfProfiler.reset();
    }

    @Test
    void inactiveProfilerDoesNotRecordVillagerEvents() {
        assertFalse(KtfProfiler.isRecording());
        KtfProfiler.recordVillagerCustomAi(100L);
        KtfProfiler.recordVillagerBrain(200L);
        KtfProfiler.recordVillagerSensors(300L);
        KtfProfiler.recordVillagerStartBehaviors(400L);
        KtfProfiler.recordVillagerRunningBehaviors(500L);
        KtfProfiler.recordVillagerSchedule(600L);

        KtfProfiler.Snapshot s = KtfProfiler.getSnapshot();
        assertEquals(0L, s.villagerCustomAiCalls());
        assertEquals(0L, s.villagerBrainCalls());
        assertEquals(0L, s.villagerSensorCalls());
        assertEquals(0L, s.villagerStartBehaviorsCalls());
        assertEquals(0L, s.villagerRunningBehaviorsCalls());
        assertEquals(0L, s.villagerScheduleCalls());
    }

    @Test
    void activeProfilerAccumulatesVillagerMetrics() {
        KtfProfiler.start(100);
        assertTrue(KtfProfiler.isRecording());

        KtfProfiler.recordVillagerCustomAi(1000L);
        KtfProfiler.recordVillagerCustomAi(2000L);

        KtfProfiler.recordVillagerBrain(500L);
        KtfProfiler.recordVillagerBrain(1500L);

        KtfProfiler.recordVillagerSensors(300L);
        KtfProfiler.recordVillagerStartBehaviors(400L);
        KtfProfiler.recordVillagerRunningBehaviors(700L);
        KtfProfiler.recordVillagerSchedule(200L);

        KtfProfiler.Snapshot s = KtfProfiler.getSnapshot();
        assertTrue(s.active());

        assertEquals(2L, s.villagerCustomAiCalls());
        assertEquals(3000L, s.villagerCustomAiTotalNanos());
        assertEquals(1500L, s.villagerCustomAiAvgNanos());

        assertEquals(2L, s.villagerBrainCalls());
        assertEquals(2000L, s.villagerBrainTotalNanos());
        assertEquals(1500L, s.villagerBrainMaxNanos());
        assertEquals(1000L, s.villagerBrainAvgNanos());

        assertEquals(1L, s.villagerSensorCalls());
        assertEquals(300L, s.villagerSensorTotalNanos());
        assertEquals(300L, s.villagerSensorAvgNanos());

        assertEquals(1L, s.villagerStartBehaviorsCalls());
        assertEquals(400L, s.villagerStartBehaviorsTotalNanos());

        assertEquals(1L, s.villagerRunningBehaviorsCalls());
        assertEquals(700L, s.villagerRunningBehaviorsTotalNanos());

        assertEquals(1L, s.villagerScheduleCalls());
        assertEquals(200L, s.villagerScheduleTotalNanos());
    }

    @Test
    void resetZeroesVillagerCounters() {
        KtfProfiler.start(50);
        KtfProfiler.recordVillagerCustomAi(100L);
        KtfProfiler.recordVillagerBrain(200L);
        KtfProfiler.recordVillagerSensors(300L);
        KtfProfiler.recordVillagerStartBehaviors(400L);
        KtfProfiler.recordVillagerRunningBehaviors(500L);
        KtfProfiler.recordVillagerSchedule(600L);

        KtfProfiler.reset();
        KtfProfiler.Snapshot s = KtfProfiler.getSnapshot();
        assertEquals(0L, s.villagerCustomAiCalls());
        assertEquals(0L, s.villagerCustomAiTotalNanos());
        assertEquals(0L, s.villagerBrainCalls());
        assertEquals(0L, s.villagerBrainTotalNanos());
        assertEquals(0L, s.villagerBrainMaxNanos());
        assertEquals(0L, s.villagerSensorCalls());
        assertEquals(0L, s.villagerSensorTotalNanos());
        assertEquals(0L, s.villagerStartBehaviorsCalls());
        assertEquals(0L, s.villagerStartBehaviorsTotalNanos());
        assertEquals(0L, s.villagerRunningBehaviorsCalls());
        assertEquals(0L, s.villagerRunningBehaviorsTotalNanos());
        assertEquals(0L, s.villagerScheduleCalls());
        assertEquals(0L, s.villagerScheduleTotalNanos());
    }

    @Test
    void formatReportIncludesVillagerBreakdownWhenMetricsExist() {
        KtfProfiler.start(10);
        KtfProfiler.recordVillagerCustomAi(1000L);
        KtfProfiler.recordVillagerBrain(2000L);
        KtfProfiler.recordVillagerSensors(300L);
        KtfProfiler.recordVillagerStartBehaviors(400L);
        KtfProfiler.recordVillagerRunningBehaviors(500L);
        KtfProfiler.recordVillagerSchedule(600L);

        String report = KtfProfiler.formatReport();
        assertTrue(report.contains("Villager Brain Profile:"), "report should contain villager section header");
        assertTrue(report.contains("customServerAiStep:"), "report should contain customServerAiStep line");
        assertTrue(report.contains("Brain.tick:"), "report should contain Brain.tick line");
        assertTrue(report.contains("Sensors:"), "report should contain Sensors line");
        assertTrue(report.contains("StartBehaviors:"), "report should contain StartBehaviors line");
        assertTrue(report.contains("RunningBehaviors:"), "report should contain RunningBehaviors line");
        assertTrue(report.contains("ScheduleUpdates:"), "report should contain ScheduleUpdates line");
        assertTrue(report.contains("calls=1"), "report should contain call counts");
        assertTrue(report.contains("max=2000 ns"), "Brain.tick line should include max");
    }

    @Test
    void formatReportOmitsVillagerSectionWhenNoMetrics() {
        KtfProfiler.start(10);
        KtfProfiler.recordIdleCheck(100L);

        String report = KtfProfiler.formatReport();
        assertFalse(report.contains("Villager Brain Profile:"), "report should omit villager section when no metrics");
    }

    @Test
    void stopCapturesVillagerMetricsInSnapshot() {
        KtfProfiler.start(10);
        KtfProfiler.recordVillagerBrain(1234L);
        KtfProfiler.stop();

        KtfProfiler.Snapshot s = KtfProfiler.getSnapshot();
        assertFalse(s.active());
        assertEquals(1L, s.villagerBrainCalls());
        assertEquals(1234L, s.villagerBrainTotalNanos());
        assertEquals(1234L, s.villagerBrainMaxNanos());
    }
}
