package io.github.gdacoff.killthefrizz.culling;

import io.github.gdacoff.killthefrizz.culling.backend.GpuBackend;
import io.github.gdacoff.killthefrizz.culling.backend.OpenGlBackend;
import io.github.gdacoff.killthefrizz.config.KtfConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central entry point for GPU-assisted entity culling decisions.
 *
 * <p>This manager is intentionally conservative: any unexpected error disables
 * culling for the remainder of the session so that rendering never breaks.</p>
 */
public final class GpuCullingManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("killthefrizz-culling");

    private static final GpuBackend BACKEND = new OpenGlBackend();

    private static volatile boolean disabledForSession = false;

    private GpuCullingManager() {
    }

    /**
     * Decide whether the given entity should be culled (skipped) this frame.
     *
     * @param entity the entity being rendered
     * @param camera the active camera, may be {@code null}
     * @return {@code true} if the entity should be culled, {@code false} otherwise
     */
    public static boolean shouldCull(Entity entity, Camera camera) {
        if (disabledForSession) {
            return false;
        }
        try {
            if (!KtfConfig.getEffectiveCommon().enabled()
                    || !KtfConfig.getEffectiveClient().cullingEnabled()) {
                return false;
            }

            if (entity instanceof Player) {
                return false;
            }

            if (entity == Minecraft.getInstance().crosshairPickEntity) {
                return false;
            }

            if (entity.hasGlowingTag() || entity.isCurrentlyGlowing()) {
                return false;
            }

            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            if (id != null && KtfConfig.getEffectiveClient().neverCullEntityTypes().contains(id.toString())) {
                return false;
            }

            if (camera != null) {
                double minDist = KtfConfig.getEffectiveClient().minDistance();
                if (entity.distanceToSqr(camera.getPosition()) < minDist * minDist) {
                    return false;
                }
            }

            // Conservative fallback: do not cull unless a backend explicitly decides to.
            return false;
        } catch (Throwable t) {
            disabledForSession = true;
            LOGGER.error("GPU culling failed; disabling culling for this session", t);
            return false;
        }
    }

    /**
     * @return whether culling has been disabled for the current session
     */
    public static boolean isDisabledForSession() {
        return disabledForSession;
    }

    /**
     * @return the active GPU backend
     */
    public static GpuBackend backend() {
        return BACKEND;
    }
}
