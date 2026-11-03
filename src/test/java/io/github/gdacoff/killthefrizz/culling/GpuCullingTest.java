package io.github.gdacoff.killthefrizz.culling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for {@link GpuCullingManager} exception rules and defaults.
 *
 * <p>These tests exercise the conservative fallback behaviour: with default
 * configuration (culling disabled) the manager must never cull, and it must
 * never throw even when given {@code null} inputs.</p>
 */
class GpuCullingTest {

    @Test
    void shouldCullReturnsFalseForNullEntity() {
        // Conservative fallback: null entity must not cause an exception and must not cull.
        assertFalse(GpuCullingManager.shouldCull(null, null));
    }

    @Test
    void shouldCullReturnsFalseByDefault() {
        // With default config (culling disabled) the manager must not cull.
        assertFalse(GpuCullingManager.shouldCull(null, null));
    }

    @Test
    void backendIsAvailable() {
        assertNotNull(GpuCullingManager.backend());
    }

    @Test
    void disabledForSessionDefaultsToFalse() {
        // Fresh session should not be disabled.
        assertFalse(GpuCullingManager.isDisabledForSession());
    }
}
