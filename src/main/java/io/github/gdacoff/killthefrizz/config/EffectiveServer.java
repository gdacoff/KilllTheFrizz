package io.github.gdacoff.killthefrizz.config;

import java.util.List;
import java.util.Set;

/**
 * Effective (resolved) server config values. Built from the raw config + active preset.
 *
 * <p>Blacklist/whitelist are exposed both as {@link List} (for display) and as
 * {@link Set} (for O(1) membership checks in the hot AI path).</p>
 */
public record EffectiveServer(
        boolean sleepingEnabled,
        int sleepRadius,
        int heartbeatTicks,
        int recentDamageWakeTicks,
        int blockChangeWakeRadius,
        List<String> entityBlacklist,
        Set<String> entityBlacklistSet,
        boolean entityWhitelistMode,
        List<String> entityWhitelist,
        Set<String> entityWhitelistSet,
        boolean skipBabies,
        boolean skipTamedAndLeashed,
        double maxIdleVelocity,
        double maxIdleVelocitySqr,
        boolean villagerThrottlingEnabled,
        int villagerStallInterval,
        int villagerBedInterval) {

    public static EffectiveServer defaults() {
        List<String> bl = List.of("minecraft:villager", "minecraft:wandering_trader", "minecraft:iron_golem",
                "minecraft:snow_golem", "minecraft:ender_dragon", "minecraft:wither",
                "minecraft:warden", "minecraft:elder_guardian", "minecraft:vex");
        List<String> wl = List.of();
        double maxIdleVelocity = 0.03;
        return new EffectiveServer(false, 48, 40, 100, 2,
                bl, Set.copyOf(bl),
                false, wl, Set.copyOf(wl),
                true, true,
                maxIdleVelocity, maxIdleVelocity * maxIdleVelocity,
                true, 4, 10);
    }

    public static EffectiveServer from(KtfConfig.Server s, Preset preset) {
        List<String> blacklist = toStringList(EffectiveCommon.safeGet(s.entityBlacklist, List.of()));
        List<String> whitelist = toStringList(EffectiveCommon.safeGet(s.entityWhitelist, List.of()));
        Set<String> blacklistSet = Set.copyOf(blacklist);
        Set<String> whitelistSet = Set.copyOf(whitelist);
        boolean whitelistMode = EffectiveCommon.safeGet(s.entityWhitelistMode, false);
        boolean skipBabies = EffectiveCommon.safeGet(s.skipBabies, true);
        boolean skipTamed = EffectiveCommon.safeGet(s.skipTamedAndLeashed, true);
        double maxIdleVelocity = EffectiveCommon.clamp(
                EffectiveCommon.safeGet(s.maxIdleVelocity, 0.03), 0.001, 0.5, 0.03, "maxIdleVelocity");
        double maxIdleVelocitySqr = maxIdleVelocity * maxIdleVelocity;

        if (preset == Preset.CUSTOM) {
            int radius = EffectiveCommon.clamp(EffectiveCommon.safeGet(s.sleepRadius, 48), 16, 128, 48, "sleepRadius");
            int heartbeat = EffectiveCommon.clamp(EffectiveCommon.safeGet(s.heartbeatTicks, 40), 5, 200, 40, "heartbeatTicks");
            int damageWake = EffectiveCommon.clamp(EffectiveCommon.safeGet(s.recentDamageWakeTicks, 100), 20, 600, 100, "recentDamageWakeTicks");
            int blockWake = EffectiveCommon.clamp(EffectiveCommon.safeGet(s.blockChangeWakeRadius, 2), 1, 8, 2, "blockChangeWakeRadius");
            boolean villagerEnabled = EffectiveCommon.safeGet(s.villagerThrottlingEnabled, true);
            int villagerStall = EffectiveCommon.clamp(EffectiveCommon.safeGet(s.villagerStallInterval, 4), 2, 20, 4, "villagerStallInterval");
            int villagerBed = EffectiveCommon.clamp(EffectiveCommon.safeGet(s.villagerBedInterval, 10), 2, 40, 10, "villagerBedInterval");
            return new EffectiveServer(EffectiveCommon.safeGet(s.sleepingEnabled, false), radius, heartbeat, damageWake, blockWake,
                    blacklist, blacklistSet, whitelistMode, whitelist, whitelistSet, skipBabies, skipTamed,
                    maxIdleVelocity, maxIdleVelocitySqr,
                    villagerEnabled, villagerStall, villagerBed);
        }

        // Preset table per spec: SAFE=on/64/20, BALANCED=on/48/40, AGGRESSIVE=on/32/80.
        return switch (preset) {
            case OFF -> new EffectiveServer(false, 48, 40, 100, 2, blacklist, blacklistSet, whitelistMode, whitelist, whitelistSet, skipBabies, skipTamed, maxIdleVelocity, maxIdleVelocitySqr, false, 4, 10);
            case SAFE -> new EffectiveServer(true, 64, 20, 100, 2, blacklist, blacklistSet, whitelistMode, whitelist, whitelistSet, skipBabies, skipTamed, maxIdleVelocity, maxIdleVelocitySqr, true, 4, 10);
            case BALANCED -> new EffectiveServer(true, 48, 40, 100, 2, blacklist, blacklistSet, whitelistMode, whitelist, whitelistSet, skipBabies, skipTamed, maxIdleVelocity, maxIdleVelocitySqr, true, 4, 10);
            case AGGRESSIVE -> new EffectiveServer(true, 32, 80, 100, 2, blacklist, blacklistSet, whitelistMode, whitelist, whitelistSet, skipBabies, skipTamed, maxIdleVelocity, maxIdleVelocitySqr, true, 4, 10);
            default -> throw new IllegalStateException("Unexpected preset: " + preset);
        };
    }

    private static List<String> toStringList(List<? extends String> in) {
        return in == null ? List.of() : List.copyOf(in);
    }
}
