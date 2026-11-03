package io.github.gdacoff.killthefrizz.config;

import io.github.gdacoff.killthefrizz.util.KtfLogger;

/**
 * Effective (resolved) common config values. Built from the raw config + active preset.
 */
public record EffectiveCommon(
        boolean enabled,
        Preset preset,
        boolean debugLogging,
        int logSpamLimitSeconds,
        boolean squeezerEnabled,
        KtfConfig.SqueezerMode squeezerMode,
        int coldAfterSeconds,
        int maxCompressedCacheMb,
        int minObjectSizeKb,
        double maxCpuMillisPerTick,
        boolean autoDisableOnSlowdown) {

    public static EffectiveCommon defaults() {
        return new EffectiveCommon(true, Preset.SAFE, false, 60,
                false, KtfConfig.SqueezerMode.DEDUP_ONLY, 300, 256, 16, 0.5, true);
    }

    public static EffectiveCommon from(KtfConfig.Common c, Preset preset) {
        // General settings always come from the file (preset does not override them).
        boolean enabled = safeGet(c.enabled, true);
        boolean debug = safeGet(c.debugLogging, false);
        int spam = clamp(safeGet(c.logSpamLimitSeconds, 60), 5, 600, 60, "logSpamLimitSeconds");

        boolean squeezerEnabled;
        KtfConfig.SqueezerMode mode;
        int cold;
        int cacheMb;
        int minKb;
        double cpu;
        boolean autoDisable;

        if (preset == Preset.CUSTOM) {
            squeezerEnabled = safeGet(c.squeezerEnabled, false);
            mode = safeGet(c.squeezerMode, KtfConfig.SqueezerMode.DEDUP_ONLY);
            cold = clamp(safeGet(c.coldAfterSeconds, 300), 30, 3600, 300, "coldAfterSeconds");
            cacheMb = clamp(safeGet(c.maxCompressedCacheMb, 256), 16, 4096, 256, "maxCompressedCacheMb");
            minKb = clamp(safeGet(c.minObjectSizeKb, 16), 4, 1024, 16, "minObjectSizeKb");
            cpu = clamp(safeGet(c.maxCpuMillisPerTick, 0.5), 0.1, 5.0, 0.5, "maxCpuMillisPerTick");
            autoDisable = safeGet(c.autoDisableOnSlowdown, true);
        } else {
            // Preset table for gigabyteSqueezer.
            switch (preset) {
                case OFF -> {
                    squeezerEnabled = false;
                    mode = KtfConfig.SqueezerMode.DEDUP_ONLY;
                    cold = 300;
                    cacheMb = 256;
                    minKb = 16;
                    cpu = 0.5;
                    autoDisable = true;
                }
                case SAFE -> {
                    squeezerEnabled = true;
                    mode = KtfConfig.SqueezerMode.DEDUP_ONLY;
                    cold = 300;
                    cacheMb = 256;
                    minKb = 16;
                    cpu = 0.5;
                    autoDisable = true;
                }
                case BALANCED -> {
                    squeezerEnabled = true;
                    mode = KtfConfig.SqueezerMode.DEDUP_ONLY;
                    cold = 300;
                    cacheMb = 256;
                    minKb = 16;
                    cpu = 0.5;
                    autoDisable = true;
                }
                case AGGRESSIVE -> {
                    squeezerEnabled = true;
                    mode = KtfConfig.SqueezerMode.COMPRESS_COLD;
                    cold = 120;
                    cacheMb = 512;
                    minKb = 8;
                    cpu = 1.0;
                    autoDisable = true;
                }
                default -> throw new IllegalStateException("Unexpected preset: " + preset);
            }
        }

        return new EffectiveCommon(enabled, preset, debug, spam,
                squeezerEnabled, mode, cold, cacheMb, minKb, cpu, autoDisable);
    }

    public static <T> T safeGet(net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<T> val, T fallback) {
        if (val == null) return fallback;
        try {
            return val.get();
        } catch (Throwable t) {
            try {
                T d = val.getDefault();
                return d != null ? d : fallback;
            } catch (Throwable ignored) {
                return fallback;
            }
        }
    }

    static int clamp(int value, int min, int max, int fallback, String name) {
        if (value < min || value > max) {
            KtfLogger.warnRateLimited("cfg:" + name,
                    "Config value %s=%d out of range [%d,%d]; using default %d", name, value, min, max, fallback);
            return fallback;
        }
        return value;
    }

    static double clamp(double value, double min, double max, double fallback, String name) {
        if (value < min || value > max) {
            KtfLogger.warnRateLimited("cfg:" + name,
                    "Config value %s=%.3f out of range [%.3f,%.3f]; using default %.3f", name, value, min, max, fallback);
            return fallback;
        }
        return value;
    }
}
