package io.github.gdacoff.killthefrizz.profiling;

import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Diagnostic profiler for the idle-check / AI-skip hot path.
 *
 * <p>Disabled by default. When inactive, all record methods are no-ops and no
 * {@link System#nanoTime()} calls are made by callers (they guard on
 * {@link #isRecording()}). All accumulators are mutated only on the server thread.</p>
 */
public final class KtfProfiler {

    private static volatile boolean recording = false;

    private static int remainingTicks = 0;
    private static int totalTicksRecorded = 0;

    private static long idleCheckCalls = 0L;
    private static long idleCheckTotalNanos = 0L;
    private static long idleCheckMaxNanos = 0L;

    private static long serverAiStepCalls = 0L;
    private static long serverAiStepTotalNanos = 0L;
    private static long serverAiStepMaxNanos = 0L;

    private static long skippedAiCalls = 0L;

    private static long pushEntitiesCalls = 0L;
    private static long pushEntitiesTotalNanos = 0L;
    private static long pushEntitiesMaxNanos = 0L;

    private static long villagerCustomAiCalls = 0L;
    private static long villagerCustomAiTotalNanos = 0L;

    private static long villagerBrainCalls = 0L;
    private static long villagerBrainTotalNanos = 0L;
    private static long villagerBrainMaxNanos = 0L;

    private static long villagerSensorCalls = 0L;
    private static long villagerSensorTotalNanos = 0L;

    private static long villagerStartBehaviorsCalls = 0L;
    private static long villagerStartBehaviorsTotalNanos = 0L;

    private static long villagerRunningBehaviorsCalls = 0L;
    private static long villagerRunningBehaviorsTotalNanos = 0L;

    private static long villagerScheduleCalls = 0L;
    private static long villagerScheduleTotalNanos = 0L;

    private static long skippedVillagerBrainCalls = 0L;

    private static volatile Snapshot snapshot = Snapshot.EMPTY;

    private KtfProfiler() {
    }

    public static boolean isRecording() {
        return recording;
    }

    public static synchronized void start(int ticks) {
        resetCounters();
        remainingTicks = Math.max(1, ticks);
        totalTicksRecorded = 0;
        snapshot = Snapshot.EMPTY;
        recording = true;
    }

    public static synchronized void stop() {
        recording = false;
        snapshot = new Snapshot(
                false,
                totalTicksRecorded,
                remainingTicks,
                idleCheckCalls, idleCheckTotalNanos, idleCheckMaxNanos,
                serverAiStepCalls, serverAiStepTotalNanos, serverAiStepMaxNanos,
                skippedAiCalls,
                pushEntitiesCalls, pushEntitiesTotalNanos, pushEntitiesMaxNanos,
                villagerCustomAiCalls, villagerCustomAiTotalNanos,
                villagerBrainCalls, villagerBrainTotalNanos, villagerBrainMaxNanos,
                villagerSensorCalls, villagerSensorTotalNanos,
                villagerStartBehaviorsCalls, villagerStartBehaviorsTotalNanos,
                villagerRunningBehaviorsCalls, villagerRunningBehaviorsTotalNanos,
                villagerScheduleCalls, villagerScheduleTotalNanos);
    }

    public static synchronized void reset() {
        recording = false;
        resetCounters();
        remainingTicks = 0;
        totalTicksRecorded = 0;
        snapshot = Snapshot.EMPTY;
    }

    private static void resetCounters() {
        idleCheckCalls = 0L;
        idleCheckTotalNanos = 0L;
        idleCheckMaxNanos = 0L;
        serverAiStepCalls = 0L;
        serverAiStepTotalNanos = 0L;
        serverAiStepMaxNanos = 0L;
        skippedAiCalls = 0L;
        pushEntitiesCalls = 0L;
        pushEntitiesTotalNanos = 0L;
        pushEntitiesMaxNanos = 0L;
        villagerCustomAiCalls = 0L;
        villagerCustomAiTotalNanos = 0L;
        villagerBrainCalls = 0L;
        villagerBrainTotalNanos = 0L;
        villagerBrainMaxNanos = 0L;
        villagerSensorCalls = 0L;
        villagerSensorTotalNanos = 0L;
        villagerStartBehaviorsCalls = 0L;
        villagerStartBehaviorsTotalNanos = 0L;
        villagerRunningBehaviorsCalls = 0L;
        villagerRunningBehaviorsTotalNanos = 0L;
        villagerScheduleCalls = 0L;
        villagerScheduleTotalNanos = 0L;
        skippedVillagerBrainCalls = 0L;
    }

    public static void recordIdleCheck(long nanos) {
        if (!recording) {
            return;
        }
        idleCheckCalls++;
        idleCheckTotalNanos += nanos;
        if (nanos > idleCheckMaxNanos) {
            idleCheckMaxNanos = nanos;
        }
    }

    public static void recordServerAiStep(long nanos) {
        if (!recording) {
            return;
        }
        serverAiStepCalls++;
        serverAiStepTotalNanos += nanos;
        if (nanos > serverAiStepMaxNanos) {
            serverAiStepMaxNanos = nanos;
        }
    }

    public static void recordSkippedAi() {
        if (!recording) {
            return;
        }
        skippedAiCalls++;
    }

    public static void recordPushEntities(long nanos) {
        if (!recording) {
            return;
        }
        pushEntitiesCalls++;
        pushEntitiesTotalNanos += nanos;
        if (nanos > pushEntitiesMaxNanos) {
            pushEntitiesMaxNanos = nanos;
        }
    }

    public static void recordVillagerCustomAi(long nanos) {
        if (!recording) {
            return;
        }
        villagerCustomAiCalls++;
        villagerCustomAiTotalNanos += nanos;
    }

    public static void recordVillagerBrain(long nanos) {
        if (!recording) {
            return;
        }
        villagerBrainCalls++;
        villagerBrainTotalNanos += nanos;
        if (nanos > villagerBrainMaxNanos) {
            villagerBrainMaxNanos = nanos;
        }
    }

    public static void recordVillagerSensors(long nanos) {
        if (!recording) {
            return;
        }
        villagerSensorCalls++;
        villagerSensorTotalNanos += nanos;
    }

    public static void recordVillagerStartBehaviors(long nanos) {
        if (!recording) {
            return;
        }
        villagerStartBehaviorsCalls++;
        villagerStartBehaviorsTotalNanos += nanos;
    }

    public static void recordVillagerRunningBehaviors(long nanos) {
        if (!recording) {
            return;
        }
        villagerRunningBehaviorsCalls++;
        villagerRunningBehaviorsTotalNanos += nanos;
    }

    public static void recordSkippedVillagerBrain() {
        if (!recording) {
            return;
        }
        skippedVillagerBrainCalls++;
    }

    public static void recordVillagerSchedule(long nanos) {
        if (!recording) {
            return;
        }
        villagerScheduleCalls++;
        villagerScheduleTotalNanos += nanos;
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        if (!recording) {
            return;
        }
        totalTicksRecorded++;
        if (remainingTicks > 0) {
            remainingTicks--;
            if (remainingTicks == 0) {
                stop();
            }
        }
    }

    public static Snapshot getSnapshot() {
        if (recording) {
            return new Snapshot(
                    true,
                    totalTicksRecorded,
                    remainingTicks,
                    idleCheckCalls, idleCheckTotalNanos, idleCheckMaxNanos,
                    serverAiStepCalls, serverAiStepTotalNanos, serverAiStepMaxNanos,
                    skippedAiCalls,
                    pushEntitiesCalls, pushEntitiesTotalNanos, pushEntitiesMaxNanos,
                    villagerCustomAiCalls, villagerCustomAiTotalNanos,
                    villagerBrainCalls, villagerBrainTotalNanos, villagerBrainMaxNanos,
                    villagerSensorCalls, villagerSensorTotalNanos,
                    villagerStartBehaviorsCalls, villagerStartBehaviorsTotalNanos,
                    villagerRunningBehaviorsCalls, villagerRunningBehaviorsTotalNanos,
                    villagerScheduleCalls, villagerScheduleTotalNanos);
        }
        return snapshot;
    }

    public static String formatReport() {
        Snapshot s = getSnapshot();
        StringBuilder sb = new StringBuilder();
        sb.append("KilllTheFrizz profiler: ").append(s.active() ? "ACTIVE" : "STOPPED").append('\n');
        sb.append("  Recorded ticks: ").append(s.recordedTicks()).append('\n');
        sb.append("  Remaining ticks: ").append(s.remainingTicks()).append('\n');
        appendSection(sb, "IdleCheck", s.idleCheckCalls(), s.idleCheckTotalNanos(), s.idleCheckMaxNanos());
        appendSection(sb, "serverAiStep", s.serverAiStepCalls(), s.serverAiStepTotalNanos(), s.serverAiStepMaxNanos());
        appendSection(sb, "pushEntities", s.pushEntitiesCalls(), s.pushEntitiesTotalNanos(), s.pushEntitiesMaxNanos());
        long totalAi = s.serverAiStepCalls() + s.skippedAiCalls();
        double pct = totalAi > 0 ? (100.0 * s.skippedAiCalls() / totalAi) : 0.0;
        sb.append("  Skipped AI calls: ").append(s.skippedAiCalls())
                .append(String.format(" (%.2f%% of %d)", pct, totalAi)).append('\n');
        if (s.villagerBrainCalls() > 0 || s.villagerCustomAiCalls() > 0) {
            appendVillagerSection(sb, s);
        }
        return sb.toString();
    }

    private static void appendVillagerSection(StringBuilder sb, Snapshot s) {
        sb.append("  Villager Brain Profile:\n");
        appendVillagerLine(sb, "customServerAiStep", s.villagerCustomAiCalls(), s.villagerCustomAiTotalNanos(), -1L);
        appendVillagerLine(sb, "Brain.tick", s.villagerBrainCalls(), s.villagerBrainTotalNanos(), s.villagerBrainMaxNanos());
        appendVillagerLine(sb, "Sensors", s.villagerSensorCalls(), s.villagerSensorTotalNanos(), -1L);
        appendVillagerLine(sb, "StartBehaviors", s.villagerStartBehaviorsCalls(), s.villagerStartBehaviorsTotalNanos(), -1L);
        appendVillagerLine(sb, "RunningBehaviors", s.villagerRunningBehaviorsCalls(), s.villagerRunningBehaviorsTotalNanos(), -1L);
        appendVillagerLine(sb, "ScheduleUpdates", s.villagerScheduleCalls(), s.villagerScheduleTotalNanos(), -1L);
    }

    private static void appendVillagerLine(StringBuilder sb, String name, long calls, long totalNanos, long maxNanos) {
        long avg = calls > 0 ? totalNanos / calls : 0L;
        sb.append("    ").append(name).append(":");
        int pad = 20 - name.length();
        for (int i = 0; i < pad; i++) {
            sb.append(' ');
        }
        sb.append(" calls=").append(calls)
                .append(", total=").append(totalNanos).append(" ns")
                .append(", avg=").append(avg).append(" ns");
        if (maxNanos >= 0L) {
            sb.append(", max=").append(maxNanos).append(" ns");
        }
        sb.append('\n');
    }

    private static void appendSection(StringBuilder sb, String name, long calls, long totalNanos, long maxNanos) {
        sb.append("  ").append(name).append(":\n");
        sb.append("    calls: ").append(calls).append('\n');
        sb.append("    total: ").append(formatNanos(totalNanos)).append('\n');
        long avg = calls > 0 ? totalNanos / calls : 0L;
        sb.append("    avg:   ").append(avg).append(" ns").append('\n');
        sb.append("    max:   ").append(maxNanos).append(" ns").append('\n');
    }

    private static String formatNanos(long nanos) {
        if (nanos < 1_000L) {
            return nanos + " ns";
        }
        if (nanos < 1_000_000L) {
            return String.format("%.2f us", nanos / 1_000.0);
        }
        return String.format("%.2f ms", nanos / 1_000_000.0);
    }

    public record Snapshot(
            boolean active,
            int recordedTicks,
            int remainingTicks,
            long idleCheckCalls,
            long idleCheckTotalNanos,
            long idleCheckMaxNanos,
            long serverAiStepCalls,
            long serverAiStepTotalNanos,
            long serverAiStepMaxNanos,
            long skippedAiCalls,
            long pushEntitiesCalls,
            long pushEntitiesTotalNanos,
            long pushEntitiesMaxNanos,
            long villagerCustomAiCalls,
            long villagerCustomAiTotalNanos,
            long villagerBrainCalls,
            long villagerBrainTotalNanos,
            long villagerBrainMaxNanos,
            long villagerSensorCalls,
            long villagerSensorTotalNanos,
            long villagerStartBehaviorsCalls,
            long villagerStartBehaviorsTotalNanos,
            long villagerRunningBehaviorsCalls,
            long villagerRunningBehaviorsTotalNanos,
            long villagerScheduleCalls,
            long villagerScheduleTotalNanos) {

        public static final Snapshot EMPTY = new Snapshot(false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        public long idleCheckAvgNanos() {
            return idleCheckCalls > 0 ? idleCheckTotalNanos / idleCheckCalls : 0L;
        }

        public long serverAiStepAvgNanos() {
            return serverAiStepCalls > 0 ? serverAiStepTotalNanos / serverAiStepCalls : 0L;
        }

        public long pushEntitiesAvgNanos() {
            return pushEntitiesCalls > 0 ? pushEntitiesTotalNanos / pushEntitiesCalls : 0L;
        }

        public long villagerCustomAiAvgNanos() {
            return villagerCustomAiCalls > 0 ? villagerCustomAiTotalNanos / villagerCustomAiCalls : 0L;
        }

        public long villagerBrainAvgNanos() {
            return villagerBrainCalls > 0 ? villagerBrainTotalNanos / villagerBrainCalls : 0L;
        }

        public long villagerSensorAvgNanos() {
            return villagerSensorCalls > 0 ? villagerSensorTotalNanos / villagerSensorCalls : 0L;
        }

        public long villagerStartBehaviorsAvgNanos() {
            return villagerStartBehaviorsCalls > 0 ? villagerStartBehaviorsTotalNanos / villagerStartBehaviorsCalls : 0L;
        }

        public long villagerRunningBehaviorsAvgNanos() {
            return villagerRunningBehaviorsCalls > 0 ? villagerRunningBehaviorsTotalNanos / villagerRunningBehaviorsCalls : 0L;
        }

        public long villagerScheduleAvgNanos() {
            return villagerScheduleCalls > 0 ? villagerScheduleTotalNanos / villagerScheduleCalls : 0L;
        }
    }
}
