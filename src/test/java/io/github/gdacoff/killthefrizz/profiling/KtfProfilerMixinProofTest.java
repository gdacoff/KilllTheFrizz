package io.github.gdacoff.killthefrizz.profiling;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Formal experimental and structural proofs for the Mixin control-flow invariants:
 * 1. Normal completion is registered as executed AI.
 * 2. Cancellation via ci.cancel() is NOT registered as executed AI.
 * 3. Each call is counted at most once.
 * 4. Exceptions do not leave incorrect timer state (reset at HEAD).
 * 5. All exit paths are correctly handled.
 * 6. ktf$aiStartNanos is reset after measurement completion.
 */
class KtfProfilerMixinProofTest {

    @BeforeEach
    void setUp() {
        KtfProfiler.reset();
    }

    @AfterEach
    void tearDown() {
        KtfProfiler.reset();
    }

    /**
     * Simulates the exact bytecode logic of MobMixin.ktf$onServerAiStep and ktf$onServerAiStepReturn.
     */
    static class SimulatedMobEntity {
        boolean wasSleeping = false;
        long aiStartNanos = 0L;

        // Returns true if serverAiStep executed normally, false if cancelled
        boolean executeServerAiStep(boolean shouldSleep, boolean throwExceptionDuringAi) {
            // Bytecode 0-7: Reset start nanos to 0L unconditionally at HEAD
            this.aiStartNanos = 0L;

            boolean profiling = KtfProfiler.isRecording();
            long t0 = profiling ? System.nanoTime() : 0L;

            // Simulate IdleChecker.shouldSleep
            boolean sleep = shouldSleep;
            this.wasSleeping = sleep;

            if (profiling) {
                KtfProfiler.recordIdleCheck(System.nanoTime() - t0);
            }

            if (sleep) {
                if (profiling) {
                    KtfProfiler.recordSkippedAi();
                }
                // Simulates mob.setNoActionTime and ci.cancel()
                return false;
            }

            if (profiling) {
                this.aiStartNanos = System.nanoTime();
            }

            // Body of serverAiStep
            if (throwExceptionDuringAi) {
                throw new RuntimeException("Simulated AI Failure");
            }

            // Bytecode of ktf$onServerAiStepReturn
            simulateReturnInjection();
            return true;
        }

        void simulateReturnInjection() {
            if (this.aiStartNanos != 0L) {
                long duration = System.nanoTime() - this.aiStartNanos;
                this.aiStartNanos = 0L;
                KtfProfiler.recordServerAiStep(duration);
            }
        }
    }

    @Test
    void proof1_normalCompletionRegistersAsExecutedAi() {
        KtfProfiler.start(100);
        SimulatedMobEntity mob = new SimulatedMobEntity();

        boolean executed = mob.executeServerAiStep(false, false);

        assertTrue(executed, "Non-sleeping mob must execute AI");
        assertEquals(0L, mob.aiStartNanos, "aiStartNanos must be reset to 0L after return");

        KtfProfiler.Snapshot s = KtfProfiler.getSnapshot();
        assertEquals(1L, s.serverAiStepCalls(), "Normal execution must register exactly 1 executed AI call");
        assertEquals(0L, s.skippedAiCalls(), "Normal execution must register 0 skipped AI calls");
        assertEquals(1L, s.idleCheckCalls(), "Normal execution must register 1 idle check");
        assertTrue(s.serverAiStepTotalNanos() > 0, "Execution time must be > 0");
    }

    @Test
    void proof2_cancellationDoesNotRegisterAsExecutedAi() {
        KtfProfiler.start(100);
        SimulatedMobEntity mob = new SimulatedMobEntity();

        boolean executed = mob.executeServerAiStep(true, false);

        assertFalse(executed, "Sleeping mob AI must be cancelled");
        assertEquals(0L, mob.aiStartNanos, "aiStartNanos must remain 0L when cancelled");

        KtfProfiler.Snapshot s = KtfProfiler.getSnapshot();
        assertEquals(0L, s.serverAiStepCalls(), "Cancelled AI must NEVER be registered as executed AI");
        assertEquals(0L, s.serverAiStepTotalNanos(), "Cancelled AI must accumulate 0 executed nanos");
        assertEquals(1L, s.skippedAiCalls(), "Cancelled AI must register 1 skipped AI call");
        assertEquals(1L, s.idleCheckCalls(), "Cancelled AI must register 1 idle check");
    }

    @Test
    void proof3_eachCallCountedAtMostOnceEvenWithMultipleReturns() {
        KtfProfiler.start(100);
        SimulatedMobEntity mob = new SimulatedMobEntity();

        mob.executeServerAiStep(false, false);

        // Simulate subsequent return opcode firing in the same method invocation
        mob.simulateReturnInjection();
        mob.simulateReturnInjection();

        KtfProfiler.Snapshot s = KtfProfiler.getSnapshot();
        assertEquals(1L, s.serverAiStepCalls(), "Subsequent return opcodes must find aiStartNanos == 0L and be no-ops");
    }

    @Test
    void proof4_exceptionDoesNotLeaveCorruptedTimerState() {
        KtfProfiler.start(100);
        SimulatedMobEntity mob = new SimulatedMobEntity();

        // 1. First invocation throws exception
        try {
            mob.executeServerAiStep(false, true);
        } catch (RuntimeException ignored) {
            // Exception unwinds stack without passing through simulateReturnInjection
        }

        // aiStartNanos was set before exception, but never returned
        assertTrue(mob.aiStartNanos > 0, "Timer was started before exception");

        // Simulate passage of time (e.g. 1 second)
        try {
            Thread.sleep(10);
        } catch (InterruptedException ignored) {}

        // 2. Next tick invocation
        mob.executeServerAiStep(false, false);

        assertEquals(0L, mob.aiStartNanos, "Timer must be 0L after clean return");

        KtfProfiler.Snapshot s = KtfProfiler.getSnapshot();
        // The first failed invocation was not completed
        // The second clean invocation executed
        assertEquals(1L, s.serverAiStepCalls(), "Only the successful call must be recorded");
        // Verify duration is fresh (< 5 milliseconds, not including the 10ms exception gap)
        assertTrue(s.serverAiStepTotalNanos() < 5_000_000L, "Dangling exception duration was wiped at HEAD");
    }

    @Test
    void proof5_allExitPathsHandled() {
        KtfProfiler.start(100);
        SimulatedMobEntity mob = new SimulatedMobEntity();

        // Path 1: Sleep
        mob.executeServerAiStep(true, false);
        // Path 2: Normal awake
        mob.executeServerAiStep(false, false);
        // Path 3: Exception
        try {
            mob.executeServerAiStep(false, true);
        } catch (Exception ignored) {}
        // Path 4: Sleep again
        mob.executeServerAiStep(true, false);

        KtfProfiler.Snapshot s = KtfProfiler.getSnapshot();
        assertEquals(2L, s.skippedAiCalls(), "2 sleep paths");
        assertEquals(1L, s.serverAiStepCalls(), "1 clean awake path");
        assertEquals(4L, s.idleCheckCalls(), "4 total idle checks");
        assertEquals(0L, mob.aiStartNanos, "Final timer state must be 0L");
    }
}
