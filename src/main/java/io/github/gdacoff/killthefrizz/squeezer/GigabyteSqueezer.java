package io.github.gdacoff.killthefrizz.squeezer;

import io.github.gdacoff.killthefrizz.config.EffectiveCommon;
import io.github.gdacoff.killthefrizz.config.KtfConfig;
import io.github.gdacoff.killthefrizz.stats.KtfStats;
import io.github.gdacoff.killthefrizz.util.KtfLogger;

/**
 * Entry point for the Phase 3 GigabyteSqueezer memory-saving subsystem.
 *
 * <p>Owns the singleton {@link StringDeduplicator} and {@link ColdDataCompressor} and
 * exposes aggregate statistics. The squeezer is only active when the master switch and
 * the squeezer switch are both enabled in the effective common config.</p>
 *
 * <p>All methods are thread-safe and never throw.</p>
 */
public final class GigabyteSqueezer {

    private static final GigabyteSqueezer INSTANCE = new GigabyteSqueezer();

    private static final double BYTES_PER_KB = 1024.0;
    private static final double BYTES_PER_MB = 1024.0 * 1024.0;
    private static final double BYTES_PER_GB = 1024.0 * 1024.0 * 1024.0;

    private final StringDeduplicator deduplicator = new StringDeduplicator();
    private volatile ColdDataCompressor compressor;
    private volatile boolean initialized;

    private GigabyteSqueezer() {
    }

    public static GigabyteSqueezer getInstance() {
        return INSTANCE;
    }

    /**
     * Initializes the squeezer from the current effective config. Safe to call multiple
     * times; re-creates the compressor when the relevant config values change.
     */
    public synchronized void init() {
        try {
            EffectiveCommon cfg = KtfConfig.getEffectiveCommon();
            if (compressor == null
                    || compressor.minObjectSizeKb() != cfg.minObjectSizeKb()
                    || compressor.coldAfterSeconds() != cfg.coldAfterSeconds()
                    || compressor.maxCompressedCacheMb() != cfg.maxCompressedCacheMb()
                    || Double.compare(compressor.maxCpuMillisPerTick(), cfg.maxCpuMillisPerTick()) != 0) {
                compressor = new ColdDataCompressor(
                        cfg.minObjectSizeKb(),
                        cfg.coldAfterSeconds(),
                        cfg.maxCompressedCacheMb(),
                        cfg.maxCpuMillisPerTick());
            }
            initialized = true;
            KtfLogger.debug("squeezer:init", "GigabyteSqueezer initialized (enabled=%s, mode=%s)",
                    isEnabled(), cfg.squeezerMode());
        } catch (Throwable t) {
            KtfLogger.warnRateLimited("squeezer:init", "GigabyteSqueezer init failed: %s", t.toString());
        }
    }

    /** @return {@code true} when both the master switch and the squeezer switch are on. */
    public boolean isEnabled() {
        try {
            EffectiveCommon cfg = KtfConfig.getEffectiveCommon();
            return cfg.enabled() && cfg.squeezerEnabled();
        } catch (Throwable t) {
            return false;
        }
    }

    public StringDeduplicator getDeduplicator() {
        return deduplicator;
    }

    public ColdDataCompressor getCompressor() {
        ColdDataCompressor c = compressor;
        if (c == null) {
            synchronized (this) {
                c = compressor;
                if (c == null) {
                    EffectiveCommon cfg = KtfConfig.getEffectiveCommon();
                    c = new ColdDataCompressor(
                            cfg.minObjectSizeKb(),
                            cfg.coldAfterSeconds(),
                            cfg.maxCompressedCacheMb(),
                            cfg.maxCpuMillisPerTick());
                    compressor = c;
                }
            }
        }
        return c;
    }

    public boolean isInitialized() {
        return initialized;
    }

    /** @return total bytes saved by deduplication and compression. */
    public long getSqueezedBytes() {
        return KtfStats.SQUEEZED_MEMORY_BYTES.get();
    }

    public double getSqueezedMegabytes() {
        return getSqueezedBytes() / BYTES_PER_MB;
    }

    public double getSqueezedGigabytes() {
        return getSqueezedBytes() / BYTES_PER_GB;
    }

    /** Clears deduplicator and compressor caches (used on config reload / world unload). */
    public synchronized void clearCaches() {
        try {
            deduplicator.clear();
            ColdDataCompressor c = compressor;
            if (c != null) {
                c.clear();
            }
        } catch (Throwable t) {
            KtfLogger.warnRateLimited("squeezer:clear", "GigabyteSqueezer clearCaches failed: %s", t.toString());
        }
    }
}
