package io.github.gdacoff.killthefrizz.sleeping;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link SleepingWakeupEvents#computeExplosionBoundingBox(List, int)}.
 *
 * <p>These tests exercise the pure bounding-box math used to collapse an explosion's
 * affected-block list into a single {@code getEntitiesOfClass} query, avoiding an
 * O(blocks) query storm.</p>
 */
class SleepingWakeupEventsTest {

    @Test
    void nullListReturnsNull() {
        assertNull(SleepingWakeupEvents.computeExplosionBoundingBox(null, 4),
                "A null block list should yield a null bounding box");
    }

    @Test
    void emptyListReturnsNull() {
        assertNull(SleepingWakeupEvents.computeExplosionBoundingBox(Collections.emptyList(), 4),
                "An empty block list should yield a null bounding box");
    }

    @Test
    void singleBlockProducesInflatedUnitBox() {
        int radius = 3;
        BlockPos pos = new BlockPos(10, 64, -5);
        AABB box = SleepingWakeupEvents.computeExplosionBoundingBox(List.of(pos), radius);

        assertNotNull(box, "A single block should produce a non-null box");
        assertEquals(10 - radius, box.minX, 1e-9, "minX should be blockX - radius");
        assertEquals(64 - radius, box.minY, 1e-9, "minY should be blockY - radius");
        assertEquals(-5 - radius, box.minZ, 1e-9, "minZ should be blockZ - radius");
        assertEquals(10 + 1 + radius, box.maxX, 1e-9, "maxX should be blockX + 1 + radius");
        assertEquals(64 + 1 + radius, box.maxY, 1e-9, "maxY should be blockY + 1 + radius");
        assertEquals(-5 + 1 + radius, box.maxZ, 1e-9, "maxZ should be blockZ + 1 + radius");
    }

    @Test
    void multipleBlocksProduceEncompassingBox() {
        int radius = 2;
        List<BlockPos> blocks = List.of(
                new BlockPos(0, 0, 0),
                new BlockPos(5, 10, -3),
                new BlockPos(-4, 2, 7));

        AABB box = SleepingWakeupEvents.computeExplosionBoundingBox(blocks, radius);

        assertNotNull(box, "Multiple blocks should produce a non-null box");
        // min corner: (-4, 0, -3) inflated by radius.
        assertEquals(-4 - radius, box.minX, 1e-9);
        assertEquals(0 - radius, box.minY, 1e-9);
        assertEquals(-3 - radius, box.minZ, 1e-9);
        // max corner: (5, 10, 7) + 1 inflated by radius.
        assertEquals(5 + 1 + radius, box.maxX, 1e-9);
        assertEquals(10 + 1 + radius, box.maxY, 1e-9);
        assertEquals(7 + 1 + radius, box.maxZ, 1e-9);
    }

    @Test
    void negativeRadiusIsClampedToZero() {
        BlockPos pos = new BlockPos(1, 2, 3);
        AABB box = SleepingWakeupEvents.computeExplosionBoundingBox(List.of(pos), -10);

        assertNotNull(box);
        assertEquals(1.0, box.minX, 1e-9, "Negative radius must clamp to 0 (minX)");
        assertEquals(2.0, box.minY, 1e-9, "Negative radius must clamp to 0 (minY)");
        assertEquals(3.0, box.minZ, 1e-9, "Negative radius must clamp to 0 (minZ)");
        assertEquals(2.0, box.maxX, 1e-9, "Negative radius must clamp to 0 (maxX)");
        assertEquals(3.0, box.maxY, 1e-9, "Negative radius must clamp to 0 (maxY)");
        assertEquals(4.0, box.maxZ, 1e-9, "Negative radius must clamp to 0 (maxZ)");
    }

    @Test
    void largeRadiusIsClampedTo32() {
        BlockPos pos = new BlockPos(0, 0, 0);
        AABB box = SleepingWakeupEvents.computeExplosionBoundingBox(List.of(pos), 1000);

        assertNotNull(box);
        assertEquals(-32.0, box.minX, 1e-9, "Large radius must clamp to 32 (minX)");
        assertEquals(-32.0, box.minY, 1e-9, "Large radius must clamp to 32 (minY)");
        assertEquals(-32.0, box.minZ, 1e-9, "Large radius must clamp to 32 (minZ)");
        assertEquals(33.0, box.maxX, 1e-9, "Large radius must clamp to 32 (maxX)");
        assertEquals(33.0, box.maxY, 1e-9, "Large radius must clamp to 32 (maxY)");
        assertEquals(33.0, box.maxZ, 1e-9, "Large radius must clamp to 32 (maxZ)");
    }

    @Test
    void zeroRadiusProducesTightBox() {
        BlockPos pos = new BlockPos(1, 2, 3);
        AABB box = SleepingWakeupEvents.computeExplosionBoundingBox(List.of(pos), 0);

        assertNotNull(box);
        assertEquals(1.0, box.minX, 1e-9);
        assertEquals(2.0, box.minY, 1e-9);
        assertEquals(3.0, box.minZ, 1e-9);
        assertEquals(2.0, box.maxX, 1e-9);
        assertEquals(3.0, box.maxY, 1e-9);
        assertEquals(4.0, box.maxZ, 1e-9);
    }
}
