package io.github.gdacoff.killthefrizz.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the effective config resolution logic (presets + clamping).
 *
 * <p>These tests build real {@link KtfConfig.Common}/{@link KtfConfig.Client}/
 * {@link KtfConfig.Server} instances via a {@link ModConfigSpec.Builder} and then
 * exercise the pure resolution helpers ({@code defaults()}, preset tables and the
 * {@code clamp} utilities). No running game or loaded config file is required.</p>
 */
class KtfConfigTest {

    // ------------------------------------------------------------------
    // EffectiveCommon
    // ------------------------------------------------------------------

    @Test
    void commonDefaultsAreSane() {
        EffectiveCommon c = EffectiveCommon.defaults();
        assertTrue(c.enabled(), "common.enabled should default to true");
        assertEquals(Preset.SAFE, c.preset(), "default preset should be SAFE");
        assertFalse(c.debugLogging(), "debugLogging should default to false");
        assertEquals(60, c.logSpamLimitSeconds());
        assertFalse(c.squeezerEnabled(), "squeezer should be disabled by default");
        assertEquals(KtfConfig.SqueezerMode.DEDUP_ONLY, c.squeezerMode());
        assertEquals(300, c.coldAfterSeconds());
        assertEquals(256, c.maxCompressedCacheMb());
        assertEquals(16, c.minObjectSizeKb());
        assertEquals(0.5, c.maxCpuMillisPerTick(), 1e-9);
        assertTrue(c.autoDisableOnSlowdown());
    }

    @Test
    void commonClampIntReturnsValueInRange() {
        assertEquals(50, EffectiveCommon.clamp(50, 10, 100, 42, "test"));
        assertEquals(10, EffectiveCommon.clamp(10, 10, 100, 42, "test"));
        assertEquals(100, EffectiveCommon.clamp(100, 10, 100, 42, "test"));
    }

    @Test
    void commonClampIntFallsBackWhenOutOfRange() {
        assertEquals(42, EffectiveCommon.clamp(9, 10, 100, 42, "test"));
        assertEquals(42, EffectiveCommon.clamp(101, 10, 100, 42, "test"));
    }

    @Test
    void commonClampDoubleReturnsValueInRange() {
        assertEquals(0.5, EffectiveCommon.clamp(0.5, 0.1, 5.0, 1.0, "test"), 1e-9);
        assertEquals(0.1, EffectiveCommon.clamp(0.1, 0.1, 5.0, 1.0, "test"), 1e-9);
        assertEquals(5.0, EffectiveCommon.clamp(5.0, 0.1, 5.0, 1.0, "test"), 1e-9);
    }

    @Test
    void commonClampDoubleFallsBackWhenOutOfRange() {
        assertEquals(1.0, EffectiveCommon.clamp(0.05, 0.1, 5.0, 1.0, "test"), 1e-9);
        assertEquals(1.0, EffectiveCommon.clamp(5.5, 0.1, 5.0, 1.0, "test"), 1e-9);
    }

    // ------------------------------------------------------------------
    // EffectiveServer presets
    // ------------------------------------------------------------------

    @Test
    void serverDefaultsAreSane() {
        EffectiveServer s = EffectiveServer.defaults();
        assertFalse(s.sleepingEnabled(), "sleeping should be disabled by default");
        assertEquals(48, s.sleepRadius());
        assertEquals(40, s.heartbeatTicks());
        assertEquals(100, s.recentDamageWakeTicks());
        assertEquals(2, s.blockChangeWakeRadius());
        assertFalse(s.entityWhitelistMode());
        assertTrue(s.skipBabies());
        assertTrue(s.skipTamedAndLeashed());
        assertTrue(s.entityBlacklist().contains("minecraft:villager"));
        assertTrue(s.entityBlacklist().contains("minecraft:ender_dragon"));
    }

    @Test
    void serverPresetSafe() {
        // SAFE: sleeping on, radius 64, heartbeat 20.
        EffectiveServer s = serverPreset(Preset.SAFE);
        assertTrue(s.sleepingEnabled());
        assertEquals(64, s.sleepRadius());
        assertEquals(20, s.heartbeatTicks());
    }

    @Test
    void serverPresetBalanced() {
        // BALANCED: sleeping on, radius 48, heartbeat 40.
        EffectiveServer s = serverPreset(Preset.BALANCED);
        assertTrue(s.sleepingEnabled());
        assertEquals(48, s.sleepRadius());
        assertEquals(40, s.heartbeatTicks());
    }

    @Test
    void serverPresetAggressive() {
        // AGGRESSIVE: sleeping on, radius 32, heartbeat 80.
        EffectiveServer s = serverPreset(Preset.AGGRESSIVE);
        assertTrue(s.sleepingEnabled());
        assertEquals(32, s.sleepRadius());
        assertEquals(80, s.heartbeatTicks());
    }

    @Test
    void serverPresetOff() {
        EffectiveServer s = serverPreset(Preset.OFF);
        assertFalse(s.sleepingEnabled(), "OFF preset must disable sleeping");
    }

    @Test
    void serverMaxIdleVelocityDefaultsAndSqr() {
        EffectiveServer s = EffectiveServer.defaults();
        assertEquals(0.03, s.maxIdleVelocity(), 1e-9);
        assertEquals(0.03 * 0.03, s.maxIdleVelocitySqr(), 1e-12);
    }

    @Test
    void serverMaxIdleVelocityFromConfig() {
        EffectiveServer s = serverPreset(Preset.SAFE);
        assertEquals(0.03, s.maxIdleVelocity(), 1e-9);
        assertEquals(0.03 * 0.03, s.maxIdleVelocitySqr(), 1e-12);
    }

    // ------------------------------------------------------------------
    // EffectiveClient presets
    // ------------------------------------------------------------------

    @Test
    void clientDefaultsAreSane() {
        EffectiveClient c = EffectiveClient.defaults();
        assertFalse(c.cullingEnabled(), "culling should be disabled by default");
        assertEquals(16, c.minDistance());
        assertEquals(2, c.updateEveryNFrames());
        assertEquals(2, c.maxResultLatencyFrames());
        assertTrue(c.disableWithShaderPacks());
        assertTrue(c.disableWithOtherRenderers());
        assertFalse(c.debugOverlay());
        assertFalse(c.adaptiveEnabled());
        assertEquals(8, c.minViewDistance());
        assertEquals(-1, c.maxViewDistance());
        assertEquals(120, c.targetFps());
        assertEquals(15, c.hysteresisPercent());
        assertEquals(1, c.stepChunks());
        assertEquals(30, c.changeCooldownSeconds());
        assertEquals(64, c.blockEntityRenderDistance());
        assertEquals(48, c.particleRenderDistance());
        assertEquals(3000, c.maxParticles());
        assertTrue(c.neverCullEntityTypes().contains("minecraft:player"));
    }

    @Test
    void clientPresetOffDisablesEverything() {
        EffectiveClient c = clientPreset(Preset.OFF);
        assertFalse(c.cullingEnabled());
        assertFalse(c.adaptiveEnabled());
    }

    @Test
    void clientPresetSafeDisablesCulling() {
        EffectiveClient c = clientPreset(Preset.SAFE);
        assertFalse(c.cullingEnabled(), "SAFE must not enable culling");
        assertFalse(c.adaptiveEnabled());
    }

    @Test
    void clientPresetBalancedEnablesAdaptiveOnly() {
        EffectiveClient c = clientPreset(Preset.BALANCED);
        assertFalse(c.cullingEnabled(), "BALANCED keeps culling off");
        assertTrue(c.adaptiveEnabled(), "BALANCED enables adaptive distance");
    }

    @Test
    void clientPresetAggressiveEnablesCullingAndAdaptive() {
        EffectiveClient c = clientPreset(Preset.AGGRESSIVE);
        assertTrue(c.cullingEnabled());
        assertTrue(c.adaptiveEnabled());
        assertEquals(24, c.minDistance());
        assertEquals(3, c.updateEveryNFrames());
        assertEquals(6, c.minViewDistance());
        assertEquals(10, c.hysteresisPercent());
        assertEquals(2, c.stepChunks());
        assertEquals(20, c.changeCooldownSeconds());
        assertEquals(48, c.blockEntityRenderDistance());
        assertEquals(32, c.particleRenderDistance());
        assertEquals(2000, c.maxParticles());
    }

    @Test
    void recomputeEffectiveDoesNotThrowWhenSpecsUnloaded() {
        // Simulates the exact condition during ModConfigEvent.Loading before specs are loaded
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(KtfConfig::recomputeEffective);
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> KtfConfig.onConfigLoad(null));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> KtfConfig.onConfigReload(null));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(KtfConfig::dumpEffective);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static EffectiveServer serverPreset(Preset preset) {
        Pair<KtfConfig.Server, ModConfigSpec> pair =
                new ModConfigSpec.Builder().configure(KtfConfig.Server::new);
        return EffectiveServer.from(pair.getLeft(), preset);
    }

    private static EffectiveClient clientPreset(Preset preset) {
        Pair<KtfConfig.Client, ModConfigSpec> pair =
                new ModConfigSpec.Builder().configure(KtfConfig.Client::new);
        return EffectiveClient.from(pair.getLeft(), preset);
    }
}
