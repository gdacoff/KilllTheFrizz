package io.github.gdacoff.killthefrizz.culling.backend;

import io.github.gdacoff.killthefrizz.util.KtfLogger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

import java.util.HashMap;
import java.util.Map;

/**
 * OpenGL occlusion-query backend.
 *
 * <p>Uses {@code GL_ANY_SAMPLES_PASSED_CONSERVATIVE} (GL 3.3 / ARB_occlusion_query2) when
 * available, falling back to {@code GL_ANY_SAMPLES_PASSED} (GL 3.0). Results are asynchronous:
 * a query issued in frame N is typically readable 1-2 frames later. Until a result is
 * available, {@link #isOccluded(int)} returns {@code false} (visible) Ã¢ÂÂ the safe direction.</p>
 *
 * <p>Any GL error disables the backend permanently (conservative fallback) without crashing.</p>
 */
public final class OpenGlBackend implements GpuBackend {

    private static final int ANY_SAMPLES_PASSED = 0x8C2F;
    private static final int ANY_SAMPLES_PASSED_CONSERVATIVE = 0x8D6A;

    private final Map<Integer, Integer> glIds = new HashMap<>();
    private int nextQueryId = 1;
    private boolean initialized;
    private boolean disabled;
    private int queryTarget = ANY_SAMPLES_PASSED;

    @Override
    public boolean isSupported() {
        if (disabled) {
            return false;
        }
        try {
            String version = GL11.glGetString(GL11.GL_VERSION);
            if (version == null) {
                return false;
            }
            // GL 3.0+ is required for ANY_SAMPLES_PASSED.
            return parseMajor(version) >= 3;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void init() {
        if (initialized || disabled) {
            return;
        }
        try {
            if (!isSupported()) {
                disabled = true;
                return;
            }
            // Prefer the conservative variant when the driver advertises GL 3.3+.
            String version = GL11.glGetString(GL11.GL_VERSION);
            if (version != null && parseMajor(version) >= 3 && parseMinor(version) >= 3) {
                queryTarget = ANY_SAMPLES_PASSED_CONSERVATIVE;
            }
            initialized = true;
        } catch (Throwable t) {
            disable("init failed: " + t.getMessage());
        }
    }

    @Override
    public void beginFrame() {
        // Nothing to do; queries are issued by the caller between begin/end.
    }

    @Override
    public void endFrame() {
        // Nothing to do.
    }

    @Override
    public boolean isOccluded(int queryId) {
        if (disabled || !initialized) {
            return false;
        }
        Integer glId = glIds.get(queryId);
        if (glId == null) {
            return false;
        }
        try {
            int available = GL15.glGetQueryObjecti(glId, GL15.GL_QUERY_RESULT_AVAILABLE);
            if (available == GL11.GL_FALSE) {
                // Result not ready yet (latency) Ã¢ÂÂ treat as visible.
                return false;
            }
            int result = GL15.glGetQueryObjecti(glId, GL15.GL_QUERY_RESULT);
            return result == 0;
        } catch (Throwable t) {
            disable("isOccluded failed: " + t.getMessage());
            return false;
        }
    }

    @Override
    public int createQuery() {
        if (disabled || !initialized) {
            return -1;
        }
        try {
            int glId = GL15.glGenQueries();
            if (glId == 0) {
                return -1;
            }
            int id = nextQueryId++;
            glIds.put(id, glId);
            return id;
        } catch (Throwable t) {
            disable("createQuery failed: " + t.getMessage());
            return -1;
        }
    }

    @Override
    public void deleteQuery(int queryId) {
        Integer glId = glIds.remove(queryId);
        if (glId == null || disabled) {
            return;
        }
        try {
            GL15.glDeleteQueries(glId);
        } catch (Throwable t) {
            disable("deleteQuery failed: " + t.getMessage());
        }
    }

    @Override
    public void destroy() {
        for (Integer glId : glIds.values()) {
            try {
                GL15.glDeleteQueries(glId);
            } catch (Throwable ignored) {
                // best effort
            }
        }
        glIds.clear();
        initialized = false;
    }

    /** @return the GL query target currently in use (for callers issuing queries). */
    public int queryTarget() {
        return queryTarget;
    }

    private void disable(String reason) {
        if (!disabled) {
            disabled = true;
            KtfLogger.warnRateLimited("gpu:disabled", "GPU culling disabled: %s", reason);
        }
    }

    private static int parseMajor(String version) {
        try {
            int dot = version.indexOf('.');
            return Integer.parseInt(version.substring(0, dot).trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static int parseMinor(String version) {
        try {
            int dot = version.indexOf('.');
            int end = version.indexOf('.', dot + 1);
            String minor = end > 0 ? version.substring(dot + 1, end) : version.substring(dot + 1);
            return Integer.parseInt(minor.trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
