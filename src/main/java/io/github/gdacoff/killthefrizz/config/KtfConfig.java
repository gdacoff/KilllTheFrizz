package io.github.gdacoff.killthefrizz.config;

import io.github.gdacoff.killthefrizz.util.KtfLogger;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

/**
 * Central configuration for KilllTheFrizz.
 *
 * <p>Three config files are registered:
 * <ul>
 *   <li>{@code killthefrizz-common.toml} (COMMON): general settings + gigabyteSqueezer</li>
 *   <li>{@code killthefrizz-client.toml} (CLIENT): gpuCulling + adaptiveDistance</li>
 *   <li>{@code killthefrizz-server.toml} (SERVER): sleepingEntities</li>
 * </ul>
 *
 * <p>Effective values are cached and only recomputed on config load/reload. When the
 * active {@link Preset} is not {@link Preset#CUSTOM}, effective values come from the
 * preset table; otherwise they come from the config file.</p>
 */
public final class KtfConfig {

    public static final ModConfigSpec COMMON_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;
    public static final ModConfigSpec SERVER_SPEC;

    private static final Common COMMON;
    private static final Client CLIENT;
    private static final Server SERVER;

    static {
        Pair<Common, ModConfigSpec> commonPair = new ModConfigSpec.Builder().configure(Common::new);
        COMMON = commonPair.getLeft();
        COMMON_SPEC = commonPair.getRight();

        Pair<Client, ModConfigSpec> clientPair = new ModConfigSpec.Builder().configure(Client::new);
        CLIENT = clientPair.getLeft();
        CLIENT_SPEC = clientPair.getRight();

        Pair<Server, ModConfigSpec> serverPair = new ModConfigSpec.Builder().configure(Server::new);
        SERVER = serverPair.getLeft();
        SERVER_SPEC = serverPair.getRight();
    }

    private KtfConfig() {
    }

    private static volatile EffectiveCommon effectiveCommon = EffectiveCommon.defaults();
    private static volatile EffectiveClient effectiveClient = EffectiveClient.defaults();
    private static volatile EffectiveServer effectiveServer = EffectiveServer.defaults();

    /**
     * Runtime auto-disable flag set by {@code SlowdownMonitor} when the server is
     * consistently slow. When true, all feature checks short-circuit to "off".
     */
    private static volatile boolean autoDisabled = false;

    public static boolean isAutoDisabled() {
        return autoDisabled;
    }

    public static void setAutoDisabled(boolean val) {
        autoDisabled = val;
    }

    public static EffectiveCommon getEffectiveCommon() {
        return effectiveCommon;
    }

    public static EffectiveClient getEffectiveClient() {
        return effectiveClient;
    }

    public static EffectiveServer getEffectiveServer() {
        return effectiveServer;
    }

    public static Common common() {
        return COMMON;
    }

    public static Client client() {
        return CLIENT;
    }

    public static Server server() {
        return SERVER;
    }

    /** Recomputes effective values from raw config + active preset, applying cross-validation. */
    public static void recomputeEffective() {
        Preset preset = EffectiveCommon.safeGet(COMMON.preset, Preset.SAFE);
        if (COMMON_SPEC.isLoaded()) {
            effectiveCommon = EffectiveCommon.from(COMMON, preset);
        }
        if (CLIENT_SPEC.isLoaded()) {
            effectiveClient = EffectiveClient.from(CLIENT, preset);
        }
        if (SERVER_SPEC.isLoaded()) {
            effectiveServer = EffectiveServer.from(SERVER, preset);
        }
        KtfLogger.resetRateLimit();
    }

    public static void onConfigLoad(Object event) {
        recomputeEffective();
    }

    public static void onConfigReload(Object event) {
        recomputeEffective();
    }

    public static String dumpEffective() {
        Preset preset = EffectiveCommon.safeGet(COMMON.preset, Preset.SAFE);
        return "KilllTheFrizz Effective Config (Preset: " + preset + "):\n"
                + "  Enabled: " + effectiveCommon.enabled() + "\n"
                + "  SleepingEntities: " + effectiveServer.sleepingEnabled() + "\n"
                + "  GPU Culling: " + effectiveClient.cullingEnabled() + "\n"
                + "  GigabyteSqueezer: " + effectiveCommon.squeezerEnabled() + "\n"
                + "  AdaptiveDistance: " + effectiveClient.adaptiveEnabled();
    }

    public static boolean toggleFeature(String feature) {
        if ("sleepingEntities".equalsIgnoreCase(feature)) {
            boolean next = !EffectiveCommon.safeGet(SERVER.sleepingEnabled, false);
            SERVER.sleepingEnabled.set(next);
            return next;
        } else if ("gpuCulling".equalsIgnoreCase(feature)) {
            boolean next = !EffectiveCommon.safeGet(CLIENT.cullingEnabled, false);
            CLIENT.cullingEnabled.set(next);
            return next;
        } else if ("gigabyteSqueezer".equalsIgnoreCase(feature)) {
            boolean next = !EffectiveCommon.safeGet(COMMON.squeezerEnabled, false);
            COMMON.squeezerEnabled.set(next);
            return next;
        } else if ("adaptiveDistance".equalsIgnoreCase(feature)) {
            boolean next = !EffectiveCommon.safeGet(CLIENT.adaptiveEnabled, false);
            CLIENT.adaptiveEnabled.set(next);
            return next;
        }
        boolean next = !EffectiveCommon.safeGet(COMMON.enabled, true);
        COMMON.enabled.set(next);
        return next;
    }

    public static final class Common {
        public final ModConfigSpec.BooleanValue enabled;
        public final ModConfigSpec.EnumValue<Preset> preset;
        public final ModConfigSpec.BooleanValue debugLogging;
        public final ModConfigSpec.IntValue logSpamLimitSeconds;

        public final ModConfigSpec.BooleanValue squeezerEnabled;
        public final ModConfigSpec.EnumValue<SqueezerMode> squeezerMode;
        public final ModConfigSpec.IntValue coldAfterSeconds;
        public final ModConfigSpec.IntValue maxCompressedCacheMb;
        public final ModConfigSpec.IntValue minObjectSizeKb;
        public final ModConfigSpec.DoubleValue maxCpuMillisPerTick;
        public final ModConfigSpec.BooleanValue autoDisableOnSlowdown;

        Common(ModConfigSpec.Builder b) {
            b.comment("General settings for KilllTheFrizz.").push("general");
            enabled = b.comment("Master switch for the whole mod. RISK: none.")
                    .define("enabled", true);
            preset = b.comment("Active preset. If not CUSTOM, feature values come from the preset table. RISK: none.")
                    .defineEnum("preset", Preset.SAFE);
            debugLogging = b.comment("Verbose logging. RISK: none.")
                    .define("debugLogging", false);
            logSpamLimitSeconds = b.comment("Minimum seconds between identical log messages. Range 5-600. RISK: none.")
                    .defineInRange("logSpamLimitSeconds", 60, 5, 600);
            b.pop();

            b.comment("GigabyteSqueezer: memory saving. RISK: low-medium.").push("gigabyteSqueezer");
            squeezerEnabled = b.comment("Enable memory saving. RISK: low.")
                    .define("enabled", false);
            squeezerMode = b.comment("DEDUP_ONLY or COMPRESS_COLD. RISK: medium for COMPRESS_COLD.")
                    .defineEnum("mode", SqueezerMode.DEDUP_ONLY);
            coldAfterSeconds = b.comment("Seconds without access before data is cold. Range 30-3600. RISK: low.")
                    .defineInRange("coldAfterSeconds", 300, 30, 3600);
            maxCompressedCacheMb = b.comment("Max compressed cache size in MB. Range 16-4096. RISK: low.")
                    .defineInRange("maxCompressedCacheMb", 256, 16, 4096);
            minObjectSizeKb = b.comment("Objects smaller than this (KB) are not compressed. Range 4-1024. RISK: low.")
                    .defineInRange("minObjectSizeKb", 16, 4, 1024);
            maxCpuMillisPerTick = b.comment("CPU budget per tick for compression (ms). Range 0.1-5.0. RISK: low.")
                    .defineInRange("maxCpuMillisPerTick", 0.5, 0.1, 5.0);
            autoDisableOnSlowdown = b.comment("Auto-disable on tick/frame slowdown. RISK: none.")
                    .define("autoDisableOnSlowdown", true);
            b.pop();
        }
    }

    public static final class Client {
        public final ModConfigSpec.BooleanValue cullingEnabled;
        public final ModConfigSpec.IntValue minDistance;
        public final ModConfigSpec.IntValue updateEveryNFrames;
        public final ModConfigSpec.IntValue maxResultLatencyFrames;
        public final ModConfigSpec.BooleanValue disableWithShaderPacks;
        public final ModConfigSpec.BooleanValue disableWithOtherRenderers;
        public final ModConfigSpec.ConfigValue<List<? extends String>> neverCullEntityTypes;
        public final ModConfigSpec.BooleanValue debugOverlay;

        public final ModConfigSpec.BooleanValue adaptiveEnabled;
        public final ModConfigSpec.IntValue minViewDistance;
        public final ModConfigSpec.IntValue maxViewDistance;
        public final ModConfigSpec.IntValue targetFps;
        public final ModConfigSpec.IntValue hysteresisPercent;
        public final ModConfigSpec.IntValue stepChunks;
        public final ModConfigSpec.IntValue changeCooldownSeconds;
        public final ModConfigSpec.IntValue blockEntityRenderDistance;
        public final ModConfigSpec.ConfigValue<List<? extends String>> entityRenderDistanceOverrides;
        public final ModConfigSpec.IntValue particleRenderDistance;
        public final ModConfigSpec.IntValue maxParticles;

        Client(ModConfigSpec.Builder b) {
            b.comment("GPU-assisted culling (experimental). RISK: medium.").push("gpuCulling");
            cullingEnabled = b.comment("Enable GPU culling. RISK: medium.")
                    .define("enabled", false);
            minDistance = b.comment("Do not cull closer than this (blocks). Range 8-64. RISK: none.")
                    .defineInRange("minDistance", 16, 8, 64);
            updateEveryNFrames = b.comment("Recompute visibility every N frames. Range 1-10. RISK: none.")
                    .defineInRange("updateEveryNFrames", 2, 1, 10);
            maxResultLatencyFrames = b.comment("Max result latency in frames. Range 1-4. RISK: none.")
                    .defineInRange("maxResultLatencyFrames", 2, 1, 4);
            disableWithShaderPacks = b.comment("Disable when shader packs are active. RISK: none.")
                    .define("disableWithShaderPacks", true);
            disableWithOtherRenderers = b.comment("Disable when Sodium/Embeddium etc. present. RISK: none.")
                    .define("disableWithOtherRenderers", true);
            neverCullEntityTypes = b.comment("Entity types never culled (id or #tag). RISK: none.")
                    .defineListAllowEmpty("neverCullEntityTypes",
                            List.of("minecraft:player", "minecraft:ender_dragon", "minecraft:wither",
                                    "minecraft:warden", "minecraft:elder_guardian"),
                            o -> o instanceof String s && !s.isBlank());
            debugOverlay = b.comment("Draw debug boxes for culled objects. RISK: none.")
                    .define("debugOverlay", false);
            b.pop();

            b.comment("Adaptive render distance. RISK: low.").push("adaptiveDistance");
            adaptiveEnabled = b.comment("Enable adaptive render distance. RISK: low.")
                    .define("enabled", false);
            minViewDistance = b.comment("Lower bound in chunks. Range 4-32. RISK: none.")
                    .defineInRange("minViewDistance", 8, 4, 32);
            maxViewDistance = b.comment("Upper bound in chunks, -1 = player's choice. Range -1-64. RISK: none.")
                    .defineInRange("maxViewDistance", -1, -1, 64);
            targetFps = b.comment("Target FPS. Range 30-1000. RISK: none.")
                    .defineInRange("targetFps", 120, 30, 1000);
            hysteresisPercent = b.comment("Dead zone around target (%). Range 5-50. RISK: none.")
                    .defineInRange("hysteresisPercent", 15, 5, 50);
            stepChunks = b.comment("Chunk step per change. Range 1-4. RISK: none.")
                    .defineInRange("stepChunks", 1, 1, 4);
            changeCooldownSeconds = b.comment("Min seconds between changes. Range 10-300. RISK: none.")
                    .defineInRange("changeCooldownSeconds", 30, 10, 300);
            blockEntityRenderDistance = b.comment("Block entity render distance (blocks). Range 16-256. RISK: none.")
                    .defineInRange("blockEntityRenderDistance", 64, 16, 256);
            entityRenderDistanceOverrides = b.comment("Per-type render distance overrides id=blocks. RISK: none.")
                    .defineListAllowEmpty("entityRenderDistanceOverrides", List.of(),
                            o -> o instanceof String s && s.contains("="));
            particleRenderDistance = b.comment("Particle render distance (blocks). Range 16-128. RISK: none.")
                    .defineInRange("particleRenderDistance", 48, 16, 128);
            maxParticles = b.comment("Max particles. Range 100-10000. RISK: none.")
                    .defineInRange("maxParticles", 3000, 100, 10000);
            b.pop();
        }
    }

    public static final class Server {
        public final ModConfigSpec.BooleanValue sleepingEnabled;
        public final ModConfigSpec.IntValue sleepRadius;
        public final ModConfigSpec.IntValue heartbeatTicks;
        public final ModConfigSpec.IntValue recentDamageWakeTicks;
        public final ModConfigSpec.IntValue blockChangeWakeRadius;
        public final ModConfigSpec.ConfigValue<List<? extends String>> entityBlacklist;
        public final ModConfigSpec.BooleanValue entityWhitelistMode;
        public final ModConfigSpec.ConfigValue<List<? extends String>> entityWhitelist;
        public final ModConfigSpec.BooleanValue skipBabies;
        public final ModConfigSpec.BooleanValue skipTamedAndLeashed;
        public final ModConfigSpec.DoubleValue maxIdleVelocity;

        public final ModConfigSpec.BooleanValue villagerThrottlingEnabled;
        public final ModConfigSpec.IntValue villagerStallInterval;
        public final ModConfigSpec.IntValue villagerBedInterval;

        Server(ModConfigSpec.Builder b) {
            b.comment("Sleeping entities: skip AI for idle mobs. RISK: low-medium.").push("sleepingEntities");
            sleepingEnabled = b.comment("Enable AI skipping for idle mobs. RISK: low.")
                    .define("enabled", false);
            sleepRadius = b.comment("Radius (blocks) to nearest player beyond which a mob may sleep. Range 16-128. RISK: low.")
                    .defineInRange("sleepRadius", 48, 16, 128);
            heartbeatTicks = b.comment("Sleeping mob gets a real AI tick every N ticks. Range 5-200. RISK: low.")
                    .defineInRange("heartbeatTicks", 40, 5, 200);
            recentDamageWakeTicks = b.comment("Ticks after damage a mob stays awake. Range 20-600. RISK: none.")
                    .defineInRange("recentDamageWakeTicks", 100, 20, 600);
            blockChangeWakeRadius = b.comment("Wake radius on block change. Range 1-8. RISK: none.")
                    .defineInRange("blockChangeWakeRadius", 2, 1, 8);
            entityBlacklist = b.comment("Entity types that never sleep (id or #tag). RISK: none.")
                    .defineListAllowEmpty("entityBlacklist",
                            List.of("minecraft:villager", "minecraft:wandering_trader", "minecraft:iron_golem",
                                    "minecraft:snow_golem", "minecraft:ender_dragon", "minecraft:wither",
                                    "minecraft:warden", "minecraft:elder_guardian", "minecraft:vex"),
                            o -> o instanceof String s && !s.isBlank());
            entityWhitelistMode = b.comment("If true, only entityWhitelist types sleep. RISK: none.")
                    .define("entityWhitelistMode", false);
            entityWhitelist = b.comment("Types allowed to sleep (whitelist mode only). RISK: none.")
                    .defineListAllowEmpty("entityWhitelist", List.of(),
                            o -> o instanceof String s && !s.isBlank());
            skipBabies = b.comment("Do not sleep baby mobs. RISK: none.")
                    .define("skipBabies", true);
            skipTamedAndLeashed = b.comment("Do not sleep tamed or leashed mobs. RISK: none.")
                    .define("skipTamedAndLeashed", true);
            maxIdleVelocity = b.comment("Maximum horizontal velocity (blocks/tick) to consider a mob idle. Higher values tolerate crowd bumping. Range: 0.001 - 0.5. Default: 0.03.")
                    .defineInRange("maxIdleVelocity", 0.03, 0.001, 0.5);

            b.comment("Villager brain adaptive throttling. RISK: low.").push("villagers");
            villagerThrottlingEnabled = b.comment("Enable adaptive brain throttling for villagers.")
                    .define("enabled", true);
            villagerStallInterval = b.comment("Interval (ticks) for stationary trading booth villagers. Range 2-20.")
                    .defineInRange("stallInterval", 4, 2, 20);
            villagerBedInterval = b.comment("Interval (ticks) for villagers sleeping in beds. Range 2-40.")
                    .defineInRange("bedInterval", 10, 2, 40);
            b.pop();
            b.pop();
        }
    }

    public enum SqueezerMode {
        DEDUP_ONLY,
        COMPRESS_COLD
    }
}
