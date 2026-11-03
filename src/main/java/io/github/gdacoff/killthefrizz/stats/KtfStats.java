package io.github.gdacoff.killthefrizz.stats;

import io.github.gdacoff.killthefrizz.config.KtfConfig;
import io.github.gdacoff.killthefrizz.sleeping.RejectionReason;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe statistics counters for the whole mod.
 *
 * <p>All counters are {@link AtomicLong} so they can be updated from server, client
 * and worker threads without locking. Values are read for display via
 * {@code /killthefrizz stats}.</p>
 */
public final class KtfStats {

    /** Number of entities currently sleeping (AI skipped), published once per server tick. */
    public static final AtomicLong SLEEPING_ENTITIES = new AtomicLong();

    /** Number of entities that slept during the current tick (reset each tick). */
    public static final AtomicLong CURRENT_TICK_SLEEPING = new AtomicLong();

    /** Total number of AI ticks that were skipped. */
    public static final AtomicLong SKIPPED_AI_TICKS = new AtomicLong();

    /** Estimated time saved, in milliseconds. */
    public static final AtomicLong SAVED_MS = new AtomicLong();

    /** Bytes of memory saved by the gigabyte squeezer. */
    public static final AtomicLong SQUEEZED_MEMORY_BYTES = new AtomicLong();

    /** Number of entities culled by GPU culling. */
    public static final AtomicLong CULLED_ENTITIES = new AtomicLong();

    /** Number of block entities culled by adaptive distance. */
    public static final AtomicLong CULLED_BLOCK_ENTITIES = new AtomicLong();

    /** Number of particles culled by adaptive distance. */
    public static final AtomicLong CULLED_PARTICLES = new AtomicLong();

    /** Per-reason counters for idle-check rejections (diagnostics). */
    public static final Map<RejectionReason, AtomicLong> REJECTION_REASONS = new EnumMap<>(RejectionReason.class);

    static {
        for (RejectionReason reason : RejectionReason.values()) {
            REJECTION_REASONS.put(reason, new AtomicLong(0));
        }
    }

    private KtfStats() {
    }

    /** Records that a mob was rejected from sleeping for the given reason. */
    public static void recordRejection(RejectionReason reason) {
        if (reason == null) {
            return;
        }
        AtomicLong counter = REJECTION_REASONS.get(reason);
        if (counter != null) {
            counter.incrementAndGet();
        }
    }

    /** Records that one mob skipped its AI tick this tick. */
    public static void recordSkippedAiTick() {
        SKIPPED_AI_TICKS.incrementAndGet();
        CURRENT_TICK_SLEEPING.incrementAndGet();
    }

    /** Resets every counter to zero. */
    public static void reset() {
        SLEEPING_ENTITIES.set(0);
        CURRENT_TICK_SLEEPING.set(0);
        SKIPPED_AI_TICKS.set(0);
        SAVED_MS.set(0);
        SQUEEZED_MEMORY_BYTES.set(0);
        CULLED_ENTITIES.set(0);
        CULLED_BLOCK_ENTITIES.set(0);
        CULLED_PARTICLES.set(0);
        for (AtomicLong counter : REJECTION_REASONS.values()) {
            counter.set(0);
        }
    }

    public static String formatStats() {
        return format();
    }

    /**
     * Formats the current statistics as a multi-line, human-readable string
     * suitable for chat output.
     */
    public static String format() {
        StringBuilder sb = new StringBuilder();
        sb.append("KilllTheFrizz statistics:\n");
        sb.append("  Sleeping entities (current tick): ").append(CURRENT_TICK_SLEEPING.get()).append('\n');
        sb.append("  Sleeping entities (last published): ").append(SLEEPING_ENTITIES.get()).append('\n');
        sb.append("  Skipped AI ticks: ").append(SKIPPED_AI_TICKS.get()).append('\n');
        sb.append("  Estimated time saved (estimate, ~0.08ms/tick): ").append(SAVED_MS.get()).append(" ms\n");
        sb.append("  Squeezed memory: ").append(formatBytes(SQUEEZED_MEMORY_BYTES.get())).append('\n');
        sb.append("  Culled entities: ").append(CULLED_ENTITIES.get()).append('\n');
        sb.append("  Auto-disabled: ").append(KtfConfig.isAutoDisabled()).append('\n');
        sb.append("Top idle rejection reasons:\n");
        var sorted = REJECTION_REASONS.entrySet().stream()
                .filter(e -> e.getValue().get() > 0)
                .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
                .toList();
        if (sorted.isEmpty()) {
            sb.append("  (none recorded yet)\n");
        } else {
            for (var entry : sorted) {
                sb.append(String.format("  - %s: %d\n", entry.getKey().getDisplayName(), entry.getValue().get()));
            }
        }
        return sb.toString();
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.2f KiB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format("%.2f MiB", mb);
        }
        return String.format("%.2f GiB", mb / 1024.0);
    }
}
