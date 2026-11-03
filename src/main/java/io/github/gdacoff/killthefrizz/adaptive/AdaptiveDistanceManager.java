package io.github.gdacoff.killthefrizz.adaptive;

import io.github.gdacoff.killthefrizz.config.EffectiveClient;
import io.github.gdacoff.killthefrizz.config.KtfConfig;
import io.github.gdacoff.killthefrizz.stats.KtfStats;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Central manager for adaptive render distance and distance-based culling.
 *
 * <p>Thread-safe singleton. All public methods are safe to call from the render
 * thread and the client tick thread. When adaptive distance is disabled, no
 * distance adjustments are made and no culling occurs.</p>
 */
public final class AdaptiveDistanceManager {

    private static final AdaptiveDistanceManager INSTANCE = new AdaptiveDistanceManager();

    /** Number of recent frame samples used for the rolling average. */
    private static final int SAMPLE_WINDOW = 60;

    private final long[] frameTimesNanos = new long[SAMPLE_WINDOW];
    private int frameIndex = 0;
    private int frameCount = 0;

    private volatile int currentDistance = -1;
    private volatile long lastChangeTimeMillis = 0L;
    private volatile String lastChangeReason = "none";

    private volatile Map<String, Integer> overrideCache = Map.of();
    private volatile List<String> overrideCacheSource = List.of();

    private AdaptiveDistanceManager() {
    }

    public static AdaptiveDistanceManager getInstance() {
        return INSTANCE;
    }

    private static volatile EffectiveClient testOverride = null;

    private static EffectiveClient cfg() {
        EffectiveClient override = testOverride;
        return override != null ? override : KtfConfig.getEffectiveClient();
    }

    /** Test-only hook to inject an effective client config without loading NeoForge config. */
    static void setEffectiveClientForTest(EffectiveClient client) {
        testOverride = client;
    }

    /** Test-only hook to reset rolling samples and cooldown state between tests. */
    synchronized void resetForTest() {
        java.util.Arrays.fill(frameTimesNanos, 0L);
        frameIndex = 0;
        frameCount = 0;
        currentDistance = -1;
        lastChangeTimeMillis = 0L;
        lastChangeReason = "none";
        overrideCache = Map.of();
        overrideCacheSource = List.of();
        trackedParticleCount = 0;
    }

    // ------------------------------------------------------------------
    // FPS sampling
    // ------------------------------------------------------------------

    /** Records a single frame duration in nanoseconds. */
    public synchronized void recordFrame(long frameTimeNanos) {
        if (frameTimeNanos <= 0L) {
            return;
        }
        frameTimesNanos[frameIndex] = frameTimeNanos;
        frameIndex = (frameIndex + 1) % SAMPLE_WINDOW;
        if (frameCount < SAMPLE_WINDOW) {
            frameCount++;
        }
    }

    /** Returns the rolling average FPS over the recent sample window, or 0 if no samples. */
    public synchronized double getAverageFps() {
        if (frameCount == 0) {
            return 0.0;
        }
        long total = 0L;
        for (int i = 0; i < frameCount; i++) {
            total += frameTimesNanos[i];
        }
        double avgNanos = (double) total / (double) frameCount;
        if (avgNanos <= 0.0) {
            return 0.0;
        }
        return 1_000_000_000.0 / avgNanos;
    }

    // ------------------------------------------------------------------
    // Adaptive view distance
    // ------------------------------------------------------------------

    /**
     * Computes the next view distance based on the rolling average FPS.
     *
     * @param currentDistance the current view distance in chunks
     * @param currentTimeMillis the current wall-clock time in milliseconds
     * @return the (possibly unchanged) view distance to apply
     */
    public int updateViewDistance(int currentDistance, long currentTimeMillis) {
        EffectiveClient c = cfg();
        if (!c.adaptiveEnabled()) {
            return currentDistance;
        }

        this.currentDistance = currentDistance;

        long cooldownMillis = (long) c.changeCooldownSeconds() * 1000L;
        if (lastChangeTimeMillis != 0L && (currentTimeMillis - lastChangeTimeMillis) < cooldownMillis) {
            return currentDistance;
        }

        double avgFps = getAverageFps();
        if (avgFps <= 0.0) {
            return currentDistance;
        }

        double target = c.targetFps();
        double hysteresis = c.hysteresisPercent() / 100.0;
        double lowThreshold = target * (1.0 - hysteresis);
        double highThreshold = target * (1.0 + hysteresis);

        int min = c.minViewDistance();
        int max = resolveMax(c);
        int step = c.stepChunks();

        int newDistance = currentDistance;
        String reason = null;

        if (avgFps < lowThreshold) {
            newDistance = Math.max(min, currentDistance - step);
            reason = String.format("FPS below target (<%.1f)", lowThreshold);
        } else if (avgFps > highThreshold) {
            newDistance = Math.min(max, currentDistance + step);
            reason = String.format("FPS above target (>%.1f)", highThreshold);
        }

        if (newDistance != currentDistance) {
            this.currentDistance = newDistance;
            this.lastChangeTimeMillis = currentTimeMillis;
            this.lastChangeReason = reason;
            return newDistance;
        }
        return currentDistance;
    }

    private static int resolveMax(EffectiveClient c) {
        int max = c.maxViewDistance();
        if (max == -1) {
            return 32;
        }
        return max;
    }

    /**
     * Called once per second from the client tick event. Reads the current render
     * distance, evaluates the rolling FPS average and applies any adjustment.
     */
    public void tick(net.minecraft.client.Minecraft mc) {
        EffectiveClient c = cfg();
        if (!c.adaptiveEnabled() || mc == null || mc.options == null) {
            return;
        }
        int current = mc.options.renderDistance().get();
        int next = updateViewDistance(current, System.currentTimeMillis());
        if (next != current) {
            mc.options.renderDistance().set(next);
        }
    }

    public int getCurrentDistance() {
        return currentDistance;
    }

    public String getLastChangeReason() {
        return lastChangeReason;
    }

    // ------------------------------------------------------------------
    // Block entity culling
    // ------------------------------------------------------------------

    public boolean shouldCullBlockEntity(@Nullable BlockEntity be, @Nullable Camera camera) {
        EffectiveClient c = cfg();
        if (!c.adaptiveEnabled() || be == null || camera == null) {
            return false;
        }
        BlockPos pos = be.getBlockPos();
        if (pos == null) {
            return false;
        }

        // Never cull the block entity the camera is currently looking at.
        if (isTargeted(pos, camera)) {
            return false;
        }

        Vec3 camPos = camera.getPosition();
        double dx = (pos.getX() + 0.5) - camPos.x;
        double dy = (pos.getY() + 0.5) - camPos.y;
        double dz = (pos.getZ() + 0.5) - camPos.z;
        double distSq = dx * dx + dy * dy + dz * dz;
        double limit = c.blockEntityRenderDistance();
        if (distSq > limit * limit) {
            KtfStats.CULLED_BLOCK_ENTITIES.incrementAndGet();
            return true;
        }
        return false;
    }

    private static boolean isTargeted(BlockPos pos, Camera camera) {
        // The camera does not expose its hit result directly in a stable API across
        // versions; the mixin passes the dispatcher's cameraHitResult. Here we do a
        // best-effort check using the camera's forward vector and a short ray.
        Vec3 camPos = camera.getPosition();
        Vec3 look = new Vec3(camera.getLookVector());
        Vec3 toBlock = new Vec3(pos.getX() + 0.5 - camPos.x,
                pos.getY() + 0.5 - camPos.y,
                pos.getZ() + 0.5 - camPos.z);
        double len = toBlock.length();
        if (len < 1.0e-4) {
            return true;
        }
        Vec3 dir = toBlock.scale(1.0 / len);
        double dot = dir.dot(look);
        // Within ~5 degrees and within reach.
        return dot > 0.996 && len < 32.0;
    }

    // ------------------------------------------------------------------
    // Particle culling
    // ------------------------------------------------------------------

    public boolean shouldCullParticle(double x, double y, double z) {
        EffectiveClient c = cfg();
        if (!c.adaptiveEnabled()) {
            return false;
        }
        Camera camera = currentCamera();
        if (camera == null) {
            return false;
        }
        Vec3 camPos = camera.getPosition();
        double dx = x - camPos.x;
        double dy = y - camPos.y;
        double dz = z - camPos.z;
        double distSq = dx * dx + dy * dy + dz * dz;
        double limit = c.particleRenderDistance();
        if (distSq > limit * limit) {
            KtfStats.CULLED_PARTICLES.incrementAndGet();
            return true;
        }
        return false;
    }

    public boolean isParticleLimitReached(int currentCount) {
        EffectiveClient c = cfg();
        if (!c.adaptiveEnabled()) {
            return false;
        }
        return currentCount >= c.maxParticles();
    }

    private volatile int trackedParticleCount = 0;

    /** Updates the tracked particle count (called from the particle engine mixin). */
    public void setTrackedParticleCount(int count) {
        this.trackedParticleCount = count;
    }

    /** Returns true when the tracked particle count has reached the configured limit. */
    public boolean isParticleLimitReached() {
        return isParticleLimitReached(trackedParticleCount);
    }

    // ------------------------------------------------------------------
    // Entity distance overrides
    // ------------------------------------------------------------------

    public boolean shouldCullEntity(@Nullable Entity entity, @Nullable Camera camera) {
        EffectiveClient c = cfg();
        if (!c.adaptiveEnabled() || entity == null || camera == null) {
            return false;
        }
        Map<String, Integer> overrides = overrides();
        if (overrides.isEmpty()) {
            return false;
        }
        EntityType<?> type = entity.getType();
        String id = EntityType.getKey(type).toString();
        Integer limit = overrides.get(id);
        if (limit == null) {
            return false;
        }
        Vec3 camPos = camera.getPosition();
        double dx = entity.getX() - camPos.x;
        double dy = entity.getY() - camPos.y;
        double dz = entity.getZ() - camPos.z;
        double distSq = dx * dx + dy * dy + dz * dz;
        return distSq > (double) limit * (double) limit;
    }

    private Map<String, Integer> overrides() {
        EffectiveClient c = cfg();
        List<String> source = c.entityRenderDistanceOverrides();
        if (source == overrideCacheSource) {
            return overrideCache;
        }
        Map<String, Integer> parsed = parseOverrides(source);
        overrideCache = parsed;
        overrideCacheSource = source;
        return parsed;
    }

    /** Parses a list of "id=distance" strings into a map. Invalid entries are skipped. */
    public static Map<String, Integer> parseOverrides(List<String> entries) {
        Map<String, Integer> result = new HashMap<>();
        if (entries == null) {
            return result;
        }
        for (String entry : entries) {
            if (entry == null) {
                continue;
            }
            int eq = entry.indexOf('=');
            if (eq <= 0 || eq == entry.length() - 1) {
                continue;
            }
            String id = entry.substring(0, eq).trim();
            String val = entry.substring(eq + 1).trim();
            if (id.isEmpty() || val.isEmpty()) {
                continue;
            }
            try {
                int dist = Integer.parseInt(val);
                if (dist > 0) {
                    result.put(id, dist);
                }
            } catch (NumberFormatException ignored) {
                // skip invalid
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Camera access (set by mixins / events)
    // ------------------------------------------------------------------

    private volatile Camera activeCamera;

    public void setActiveCamera(@Nullable Camera camera) {
        this.activeCamera = camera;
    }

    @Nullable
    private Camera currentCamera() {
        return activeCamera;
    }
}
