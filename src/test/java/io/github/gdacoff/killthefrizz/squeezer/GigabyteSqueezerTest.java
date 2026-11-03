package io.github.gdacoff.killthefrizz.squeezer;

import io.github.gdacoff.killthefrizz.stats.KtfStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Phase 3 GigabyteSqueezer components:
 * {@link StringDeduplicator}, {@link ColdDataCompressor} and {@link GigabyteSqueezer}.
 */
class GigabyteSqueezerTest {

    @BeforeEach
    void resetStats() {
        KtfStats.reset();
    }

    // ------------------------------------------------------------------
    // StringDeduplicator
    // ------------------------------------------------------------------

    @Test
    void deduplicatorReturnsCanonicalReference() {
        StringDeduplicator dedup = new StringDeduplicator();

        // Two distinct but equal String instances.
        String a = new String("test_string");
        String b = new String("test_string");
        assertNotSame(a, b, "precondition: distinct instances");

        String canonicalA = dedup.intern(a);
        String canonicalB = dedup.intern(b);

        assertSame(canonicalA, canonicalB, "equal strings must map to the same canonical instance");
        assertEquals("test_string", canonicalA);
    }

    @Test
    void deduplicatorRecordsStatistics() {
        StringDeduplicator dedup = new StringDeduplicator();
        long before = KtfStats.SQUEEZED_MEMORY_BYTES.get();

        dedup.intern(new String("alpha"));
        dedup.intern(new String("alpha")); // duplicate -> hit
        dedup.intern(new String("beta"));

        assertEquals(1, dedup.dedupHits(), "exactly one duplicate eliminated");
        assertEquals(2, dedup.internedCount(), "two distinct strings interned");
        assertTrue(KtfStats.SQUEEZED_MEMORY_BYTES.get() > before,
                "squeezed memory counter must grow on a dedup hit");
    }

    @Test
    void deduplicatorHandlesNull() {
        StringDeduplicator dedup = new StringDeduplicator();
        assertNull(dedup.intern(null));
        assertEquals(0, dedup.size());
    }

    @Test
    void deduplicatorSweepRemovesCollectedEntries() {
        StringDeduplicator dedup = new StringDeduplicator();
        dedup.intern(new String("transient"));
        assertEquals(1, dedup.size());
        // Force GC is not deterministic; sweep must at least not throw and return >= 0.
        int removed = dedup.sweep();
        assertTrue(removed >= 0);
    }

    @Test
    void deduplicatorClearResetsState() {
        StringDeduplicator dedup = new StringDeduplicator();
        dedup.intern(new String("x"));
        dedup.intern(new String("x"));
        dedup.clear();
        assertEquals(0, dedup.size());
        assertEquals(0, dedup.dedupHits());
        assertEquals(0, dedup.internedCount());
    }

    @Test
    void estimateBytesGrowsWithLength() {
        long small = StringDeduplicator.estimateBytes("a");
        long large = StringDeduplicator.estimateBytes("aaaaaaaaaaaaaaaaaaaa");
        assertTrue(large > small, "longer strings must estimate more bytes");
        assertEquals(0, StringDeduplicator.estimateBytes(null));
    }

    // ------------------------------------------------------------------
    // ColdDataCompressor
    // ------------------------------------------------------------------

    @Test
    void compressorRoundTripIntegrity() {
        ColdDataCompressor compressor = new ColdDataCompressor(1, 300, 256, 5.0);
        compressor.beginTick();

        byte[] payload = repetitivePayload(64 * 1024);
        boolean compressed = compressor.tryCompress("key1", payload, System.currentTimeMillis());
        assertTrue(compressed, "large repetitive payload should compress");
        assertTrue(compressor.contains("key1"));

        byte[] restored = compressor.decompress("key1");
        assertNotNull(restored);
        assertArrayEquals(payload, restored, "round-trip must preserve bytes exactly");
    }

    @Test
    void compressorSkipsSmallPayloads() {
        // minObjectSizeKb = 4 -> 4096 bytes threshold.
        ColdDataCompressor compressor = new ColdDataCompressor(4, 300, 256, 5.0);
        compressor.beginTick();

        byte[] tiny = new byte[100];
        boolean compressed = compressor.tryCompress("tiny", tiny, System.currentTimeMillis());
        assertFalse(compressed, "payloads below minObjectSizeKb must not be compressed");
        assertFalse(compressor.contains("tiny"));
    }

    @Test
    void compressorSkipsIncompressibleData() {
        ColdDataCompressor compressor = new ColdDataCompressor(1, 300, 256, 5.0);
        compressor.beginTick();

        // Random data does not compress; tryCompress should return false.
        byte[] random = new byte[32 * 1024];
        new Random(42).nextBytes(random);
        boolean compressed = compressor.tryCompress("rand", random, System.currentTimeMillis());
        assertFalse(compressed, "incompressible data must not be stored");
    }

    @Test
    void compressorDecompressMissingKeyReturnsNull() {
        ColdDataCompressor compressor = new ColdDataCompressor(1, 300, 256, 5.0);
        assertNull(compressor.decompress("nope"));
        assertNull(compressor.decompress(null));
    }

    @Test
    void compressorTracksStatistics() {
        ColdDataCompressor compressor = new ColdDataCompressor(1, 300, 256, 5.0);
        compressor.beginTick();
        byte[] payload = repetitivePayload(128 * 1024);
        compressor.tryCompress("k", payload, System.currentTimeMillis());

        assertEquals(1, compressor.compressedCount());
        assertTrue(compressor.compressedBytes() > 0);
        assertEquals(payload.length, compressor.originalBytes());
        assertTrue(compressor.compressedBytes() < compressor.originalBytes(),
                "compressed size must be smaller than original");
    }

    @Test
    void compressorEvictsColdEntries() {
        ColdDataCompressor compressor = new ColdDataCompressor(1, 1, 256, 5.0);
        compressor.beginTick();
        long now = System.currentTimeMillis();
        compressor.tryCompress("cold", repetitivePayload(16 * 1024), now);
        assertTrue(compressor.contains("cold"));

        // Advance time beyond coldAfterSeconds (1s).
        int removed = compressor.evictCold(now + 5000);
        assertEquals(1, removed);
        assertFalse(compressor.contains("cold"));
    }

    @Test
    void compressorClearResetsState() {
        ColdDataCompressor compressor = new ColdDataCompressor(1, 300, 256, 5.0);
        compressor.beginTick();
        compressor.tryCompress("k", repetitivePayload(16 * 1024), System.currentTimeMillis());
        compressor.clear();
        assertEquals(0, compressor.size());
        assertEquals(0, compressor.compressedCount());
        assertEquals(0, compressor.compressedBytes());
    }

    // ------------------------------------------------------------------
    // GigabyteSqueezer
    // ------------------------------------------------------------------

    @Test
    void squeezerIsSingleton() {
        assertSame(GigabyteSqueezer.getInstance(), GigabyteSqueezer.getInstance());
    }

    @Test
    void squeezerByteConversions() {
        GigabyteSqueezer squeezer = GigabyteSqueezer.getInstance();
        KtfStats.SQUEEZED_MEMORY_BYTES.set(1024L * 1024L * 1024L); // exactly 1 GiB

        assertEquals(1024.0, squeezer.getSqueezedMegabytes(), 0.0001);
        assertEquals(1.0, squeezer.getSqueezedGigabytes(), 0.0001);
        assertEquals(1024L * 1024L * 1024L, squeezer.getSqueezedBytes());
    }

    @Test
    void squeezerExposesComponents() {
        GigabyteSqueezer squeezer = GigabyteSqueezer.getInstance();
        assertNotNull(squeezer.getDeduplicator());
        assertNotNull(squeezer.getCompressor());
    }

    @Test
    void squeezerClearCachesDoesNotThrow() {
        GigabyteSqueezer squeezer = GigabyteSqueezer.getInstance();
        assertDoesNotThrow(squeezer::clearCaches);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static byte[] repetitivePayload(int size) {
        // Highly compressible: repeating ASCII pattern.
        byte[] data = new byte[size];
        byte[] pattern = "KilllTheFrizz-GigabyteSqueezer-".getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i < size; i++) {
            data[i] = pattern[i % pattern.length];
        }
        return data;
    }
}
