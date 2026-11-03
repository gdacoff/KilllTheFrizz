package io.github.gdacoff.killthefrizz.squeezer;

import io.github.gdacoff.killthefrizz.stats.KtfStats;
import io.github.gdacoff.killthefrizz.util.KtfLogger;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Compresses cold byte payloads with LZ4 to reclaim heap.
 *
 * <p>A payload is eligible for compression when it is at least {@code minObjectSizeBytes}
 * large and has not been accessed for {@code coldAfterSeconds}. Compressed payloads are
 * stored in a bounded cache; when the cache exceeds {@code maxCompressedCacheMb} the
 * least-recently-used entries are evicted (dropped entirely, since the caller is expected
 * to be able to regenerate them).</p>
 *
 * <p>Compression work is bounded by a per-tick CPU budget ({@code maxCpuMillisPerTick}).
 * On any error or budget overrun the compressor degrades gracefully: it stops compressing
 * for the current tick and never throws to the caller.</p>
 *
 * <p>All methods are thread-safe.</p>
 */
public final class ColdDataCompressor {

    private static final LZ4Factory FACTORY = LZ4Factory.fastestInstance();
    private static final LZ4Compressor COMPRESSOR = FACTORY.fastCompressor();
    private static final LZ4FastDecompressor DECOMPRESSOR = FACTORY.fastDecompressor();

    /** A compressed payload plus the metadata needed to restore it. */
    public static final class CompressedEntry {
        final byte[] data;
        final int originalLength;
        volatile long lastAccessMillis;

        CompressedEntry(byte[] data, int originalLength, long lastAccessMillis) {
            this.data = data;
            this.originalLength = originalLength;
            this.lastAccessMillis = lastAccessMillis;
        }

        public int compressedLength() {
            return data.length;
        }

        public int originalLength() {
            return originalLength;
        }
    }

    private final ConcurrentHashMap<String, CompressedEntry> cache = new ConcurrentHashMap<>();
    private final AtomicLong compressedBytes = new AtomicLong();
    private final AtomicLong originalBytes = new AtomicLong();
    private final AtomicLong compressedCount = new AtomicLong();
    private final AtomicLong evictedCount = new AtomicLong();

    private final int minObjectSizeBytes;
    private final long coldAfterMillis;
    private final long maxCacheBytes;
    private final double maxCpuMillisPerTick;

    /** Millis of CPU spent in the current tick; reset by {@link #beginTick()}. */
    private long tickCpuNanos;

    public ColdDataCompressor(int minObjectSizeKb, int coldAfterSeconds,
                              int maxCompressedCacheMb, double maxCpuMillisPerTick) {
        this.minObjectSizeBytes = Math.max(0, minObjectSizeKb) * 1024;
        this.coldAfterMillis = Math.max(0, coldAfterSeconds) * 1000L;
        this.maxCacheBytes = Math.max(0L, (long) maxCompressedCacheMb) * 1024L * 1024L;
        this.maxCpuMillisPerTick = Math.max(0.0, maxCpuMillisPerTick);
    }

    /** Resets the per-tick CPU budget. Call once per game tick. */
    public void beginTick() {
        tickCpuNanos = 0L;
    }

    /**
     * Attempts to compress {@code payload} under {@code key} if it is cold and large enough.
     *
     * @return {@code true} if the payload was compressed and cached, {@code false} otherwise
     */
    public boolean tryCompress(String key, byte[] payload, long nowMillis) {
        if (key == null || payload == null) {
            return false;
        }
        if (payload.length < minObjectSizeBytes) {
            return false;
        }
        if (cache.containsKey(key)) {
            // Already compressed; just refresh access time.
            touch(key, nowMillis);
            return false;
        }
        if (!hasCpuBudget()) {
            return false;
        }

        long start = System.nanoTime();
        try {
            int maxLen = COMPRESSOR.maxCompressedLength(payload.length);
            byte[] compressed = new byte[maxLen];
            int written = COMPRESSOR.compress(payload, 0, payload.length, compressed, 0, maxLen);
            if (written >= payload.length) {
                // Not worth storing: compression did not help.
                return false;
            }
            byte[] exact = new byte[written];
            System.arraycopy(compressed, 0, exact, 0, written);

            CompressedEntry entry = new CompressedEntry(exact, payload.length, nowMillis);
            cache.put(key, entry);
            compressedBytes.addAndGet(written);
            originalBytes.addAndGet(payload.length);
            compressedCount.incrementAndGet();
            KtfStats.SQUEEZED_MEMORY_BYTES.addAndGet(payload.length - written);

            enforceBudget();
            return true;
        } catch (Throwable t) {
            KtfLogger.warnRateLimited("squeezer:compress",
                    "Cold compression failed for key %s: %s", key, t.toString());
            return false;
        } finally {
            tickCpuNanos += System.nanoTime() - start;
        }
    }

    /**
     * Decompresses the payload stored under {@code key}.
     *
     * @return the original bytes, or {@code null} if no entry exists or decompression fails
     */
    public byte[] decompress(String key) {
        if (key == null) {
            return null;
        }
        CompressedEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        try {
            byte[] out = new byte[entry.originalLength];
            DECOMPRESSOR.decompress(entry.data, 0, out, 0, entry.originalLength);
            entry.lastAccessMillis = System.currentTimeMillis();
            return out;
        } catch (Throwable t) {
            KtfLogger.warnRateLimited("squeezer:decompress",
                    "Cold decompression failed for key %s: %s", key, t.toString());
            cache.remove(key);
            return null;
        }
    }

    /** Marks an entry as recently accessed without decompressing it. */
    public void touch(String key, long nowMillis) {
        CompressedEntry entry = cache.get(key);
        if (entry != null) {
            entry.lastAccessMillis = nowMillis;
        }
    }

    /**
     * Evicts entries that have been cold for longer than {@code coldAfterMillis}.
     *
     * @return number of entries evicted
     */
    public int evictCold(long nowMillis) {
        int removed = 0;
        Iterator<Map.Entry<String, CompressedEntry>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, CompressedEntry> e = it.next();
            if (nowMillis - e.getValue().lastAccessMillis >= coldAfterMillis) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            evictedCount.addAndGet(removed);
            recomputeTotals();
        }
        return removed;
    }

    /** Enforces the max cache size by evicting least-recently-used entries. */
    public void enforceBudget() {
        if (maxCacheBytes <= 0) {
            return;
        }
        long total = compressedBytes.get();
        if (total <= maxCacheBytes) {
            return;
        }
        // Evict LRU until under budget.
        while (total > maxCacheBytes && !cache.isEmpty()) {
            String lruKey = null;
            long lruTime = Long.MAX_VALUE;
            for (Map.Entry<String, CompressedEntry> e : cache.entrySet()) {
                long t = e.getValue().lastAccessMillis;
                if (t < lruTime) {
                    lruTime = t;
                    lruKey = e.getKey();
                }
            }
            if (lruKey == null) {
                break;
            }
            CompressedEntry removed = cache.remove(lruKey);
            if (removed != null) {
                compressedBytes.addAndGet(-removed.data.length);
                originalBytes.addAndGet(-removed.originalLength);
                evictedCount.incrementAndGet();
                total = compressedBytes.get();
            } else {
                break;
            }
        }
    }

    private void recomputeTotals() {
        long c = 0;
        long o = 0;
        for (CompressedEntry e : cache.values()) {
            c += e.data.length;
            o += e.originalLength;
        }
        compressedBytes.set(c);
        originalBytes.set(o);
    }

    private boolean hasCpuBudget() {
        if (maxCpuMillisPerTick <= 0) {
            return false;
        }
        return tickCpuNanos < (long) (maxCpuMillisPerTick * 1_000_000.0);
    }

    public int minObjectSizeKb() {
        return minObjectSizeBytes / 1024;
    }

    public int coldAfterSeconds() {
        return (int) (coldAfterMillis / 1000L);
    }

    public int maxCompressedCacheMb() {
        return (int) (maxCacheBytes / (1024L * 1024L));
    }

    public double maxCpuMillisPerTick() {
        return maxCpuMillisPerTick;
    }

    public int size() {
        return cache.size();
    }

    public boolean contains(String key) {
        return cache.containsKey(key);
    }

    public long compressedBytes() {
        return compressedBytes.get();
    }

    public long originalBytes() {
        return originalBytes.get();
    }

    public long compressedCount() {
        return compressedCount.get();
    }

    public long evictedCount() {
        return evictedCount.get();
    }

    public void clear() {
        cache.clear();
        compressedBytes.set(0);
        originalBytes.set(0);
        compressedCount.set(0);
        evictedCount.set(0);
        tickCpuNanos = 0;
    }
}
