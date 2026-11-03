package io.github.gdacoff.killthefrizz.squeezer;

import io.github.gdacoff.killthefrizz.stats.KtfStats;

import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Canonical interning for {@link String}s and other immutable value objects.
 *
 * <p>Duplicate strings are extremely common in Minecraft: block/entity ids, resource
 * locations, translation keys, NBT keys, etc. Many of these are created repeatedly and
 * never interned, so the heap ends up holding thousands of identical copies.</p>
 *
 * <p>This deduplicator keeps a {@link ConcurrentHashMap} of canonical instances. To avoid
 * pinning strings forever, the map stores {@link WeakReference}s to the canonical value;
 * when the canonical value is garbage-collected the entry is removed on the next sweep.
 * The estimated saved bytes are accumulated in {@link KtfStats#SQUEEZED_MEMORY_BYTES}.</p>
 *
 * <p>All methods are thread-safe and never throw: on any internal error they fall back to
 * returning the input unchanged.</p>
 */
public final class StringDeduplicator {

    /** Approximate per-String overhead on a 64-bit JVM (header + hash + array header). */
    private static final int STRING_OVERHEAD_BYTES = 40;

    private final ConcurrentHashMap<String, WeakReference<String>> canonical = new ConcurrentHashMap<>();
    private final AtomicLong internedCount = new AtomicLong();
    private final AtomicLong dedupHits = new AtomicLong();

    /**
     * Returns a canonical instance equal to {@code value}. If an equal canonical instance
     * already exists, the input is discarded and the saved bytes are recorded.
     *
     * @param value the string to intern (may be {@code null})
     * @return the canonical instance, or {@code null} if {@code value} was {@code null}
     */
    public String intern(String value) {
        if (value == null) {
            return null;
        }
        try {
            WeakReference<String> existingRef = canonical.get(value);
            if (existingRef != null) {
                String existing = existingRef.get();
                if (existing != null) {
                    if (existing != value) {
                        // Duplicate eliminated: record the bytes we would have kept.
                        long saved = estimateBytes(value);
                        KtfStats.SQUEEZED_MEMORY_BYTES.addAndGet(saved);
                        dedupHits.incrementAndGet();
                    }
                    return existing;
                }
                // Stale entry (referent collected) - fall through and replace.
            }
            canonical.put(value, new WeakReference<>(value));
            internedCount.incrementAndGet();
            return value;
        } catch (Throwable t) {
            // Never let deduplication break gameplay.
            return value;
        }
    }

    /**
     * Removes entries whose canonical value has been garbage-collected. Called
     * periodically by {@link GigabyteSqueezer}.
     *
     * @return number of entries removed
     */
    public int sweep() {
        int removed = 0;
        try {
            for (var it = canonical.entrySet().iterator(); it.hasNext(); ) {
                var entry = it.next();
                WeakReference<String> ref = entry.getValue();
                if (ref == null || ref.get() == null) {
                    it.remove();
                    removed++;
                }
            }
        } catch (Throwable ignored) {
            // Best-effort sweep.
        }
        return removed;
    }

    /** Number of distinct canonical strings currently tracked. */
    public int size() {
        return canonical.size();
    }

    /** Number of duplicate strings eliminated since start. */
    public long dedupHits() {
        return dedupHits.get();
    }

    /** Number of distinct strings interned since start. */
    public long internedCount() {
        return internedCount.get();
    }

    /** Clears all state (used by tests and on config reload). */
    public void clear() {
        canonical.clear();
        internedCount.set(0);
        dedupHits.set(0);
    }

    /**
     * Estimates the heap bytes a duplicate String occupies: object header, the backing
     * byte[] header, and the payload (Latin-1 = 1 byte/char, else 2 bytes/char).
     */
    static long estimateBytes(String value) {
        if (value == null) {
            return 0;
        }
        int len = value.length();
        // Assume compact strings (Java 9+): 1 byte per char for Latin-1, else 2.
        boolean latin1 = value.chars().allMatch(c -> c <= 0xFF);
        long payload = latin1 ? len : (long) len * 2L;
        return STRING_OVERHEAD_BYTES + payload;
    }
}
