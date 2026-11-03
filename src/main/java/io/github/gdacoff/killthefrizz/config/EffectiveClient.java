package io.github.gdacoff.killthefrizz.config;

import io.github.gdacoff.killthefrizz.util.KtfLogger;

import java.util.List;

/**
 * Effective (resolved) client config values. Built from the raw config + active preset.
 */
public record EffectiveClient(
        boolean cullingEnabled,
        int minDistance,
        int updateEveryNFrames,
        int maxResultLatencyFrames,
        boolean disableWithShaderPacks,
        boolean disableWithOtherRenderers,
        List<String> neverCullEntityTypes,
        boolean debugOverlay,
        boolean adaptiveEnabled,
        int minViewDistance,
        int maxViewDistance,
        int targetFps,
        int hysteresisPercent,
        int stepChunks,
        int changeCooldownSeconds,
        int blockEntityRenderDistance,
        List<String> entityRenderDistanceOverrides,
        int particleRenderDistance,
        int maxParticles) {

    public static EffectiveClient defaults() {
        return new EffectiveClient(false, 16, 2, 2, true, true,
                List.of("minecraft:player", "minecraft:ender_dragon", "minecraft:wither",
                        "minecraft:warden", "minecraft:elder_guardian"),
                false,
                false, 8, -1, 120, 15, 1, 30, 64, List.of(), 48, 3000);
    }

    public static EffectiveClient from(KtfConfig.Client c, Preset preset) {
        List<String> neverCull = toStringList(EffectiveCommon.safeGet(c.neverCullEntityTypes, List.of()));
        List<String> overrides = toStringList(EffectiveCommon.safeGet(c.entityRenderDistanceOverrides, List.of()));

        if (preset == Preset.CUSTOM) {
            int minDist = EffectiveCommon.clamp(EffectiveCommon.safeGet(c.minDistance, 16), 8, 64, 16, "minDistance");
            int updateN = EffectiveCommon.clamp(EffectiveCommon.safeGet(c.updateEveryNFrames, 2), 1, 10, 2, "updateEveryNFrames");
            int latency = EffectiveCommon.clamp(EffectiveCommon.safeGet(c.maxResultLatencyFrames, 2), 1, 4, 2, "maxResultLatencyFrames");

            int minView = EffectiveCommon.clamp(EffectiveCommon.safeGet(c.minViewDistance, 8), 4, 32, 8, "minViewDistance");
            int maxView = EffectiveCommon.clamp(EffectiveCommon.safeGet(c.maxViewDistance, -1), -1, 64, -1, "maxViewDistance");
            if (maxView != -1 && minView > maxView) {
                KtfLogger.warnRateLimited("cfg:viewRange",
                        "minViewDistance (%d) > maxViewDistance (%d); using defaults 8/-1", minView, maxView);
                minView = 8;
                maxView = -1;
            }
            int target = EffectiveCommon.clamp(EffectiveCommon.safeGet(c.targetFps, 120), 30, 1000, 120, "targetFps");
            int hyst = EffectiveCommon.clamp(EffectiveCommon.safeGet(c.hysteresisPercent, 15), 5, 50, 15, "hysteresisPercent");
            int step = EffectiveCommon.clamp(EffectiveCommon.safeGet(c.stepChunks, 1), 1, 4, 1, "stepChunks");
            int cooldown = EffectiveCommon.clamp(EffectiveCommon.safeGet(c.changeCooldownSeconds, 30), 10, 300, 30, "changeCooldownSeconds");
            int beDist = EffectiveCommon.clamp(EffectiveCommon.safeGet(c.blockEntityRenderDistance, 64), 16, 256, 64, "blockEntityRenderDistance");
            int particleDist = EffectiveCommon.clamp(EffectiveCommon.safeGet(c.particleRenderDistance, 48), 16, 128, 48, "particleRenderDistance");
            int maxParticles = EffectiveCommon.clamp(EffectiveCommon.safeGet(c.maxParticles, 3000), 100, 10000, 3000, "maxParticles");

            return new EffectiveClient(
                    EffectiveCommon.safeGet(c.cullingEnabled, false), minDist, updateN, latency,
                    EffectiveCommon.safeGet(c.disableWithShaderPacks, true), EffectiveCommon.safeGet(c.disableWithOtherRenderers, true),
                    neverCull, EffectiveCommon.safeGet(c.debugOverlay, false),
                    EffectiveCommon.safeGet(c.adaptiveEnabled, false), minView, maxView, target, hyst, step, cooldown,
                    beDist, overrides, particleDist, maxParticles);
        }

        // Preset table.
        return switch (preset) {
            case OFF -> new EffectiveClient(false, 16, 2, 2, true, true, neverCull, false,
                    false, 8, -1, 120, 15, 1, 30, 64, overrides, 48, 3000);
            case SAFE -> new EffectiveClient(false, 16, 2, 2, true, true, neverCull, false,
                    false, 8, -1, 120, 15, 1, 30, 64, overrides, 48, 3000);
            case BALANCED -> new EffectiveClient(false, 16, 2, 2, true, true, neverCull, false,
                    true, 8, -1, 120, 15, 1, 30, 64, overrides, 48, 3000);
            case AGGRESSIVE -> new EffectiveClient(true, 24, 3, 2, true, true, neverCull, false,
                    true, 6, -1, 120, 10, 2, 20, 48, overrides, 32, 2000);
            default -> throw new IllegalStateException("Unexpected preset: " + preset);
        };
    }

    private static List<String> toStringList(List<? extends String> in) {
        return in == null ? List.of() : List.copyOf(in);
    }
}
