package com.nanocache;

import com.nanocache.core.NanoCache;
import com.nanocache.core.ShardedNanoCacheImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NanoCacheAppTest {

    private NanoCache<String, String> cache;
    private final int CAPACITY = 100;

    @BeforeEach
    void setUp() {
        // Initialize a fresh cache before each test
        // 100 Capacity, 4 Segments
        cache = new ShardedNanoCacheImpl<>(CAPACITY, 4);
    }

    // --- FUNCTIONAL TESTS (Logic Verification) ---

    @Test
    @DisplayName("Basic Put and Get")
    void testBasicPutAndGet() {
        cache.put("key1", "value1", 5000);
        Optional<String> value = cache.get("key1");

        assertTrue(value.isPresent());
        assertEquals("value1", value.get());
    }

    @Test
    @DisplayName("TTL Expiration: Item should disappear after time passes")
    void testExpiration() throws InterruptedException {
        // TTL = 100ms
        cache.put("shortLived", "data", 100);

        // Check immediately
        assertTrue(cache.get("shortLived").isPresent());

        // Wait for expiration
        Thread.sleep(200);

        // Check again
        assertFalse(cache.get("shortLived").isPresent(), "Item should be expired");
    }

    @Test
    @DisplayName("LRU Eviction: Should remove oldest item when full")
    void testLRUEviction() {
        // Create small cache: Capacity 3
        NanoCache<String, String> smallCache = new ShardedNanoCacheImpl<>(3, 1);

        smallCache.put("A", "1", 5000); // 1. Put A
        smallCache.put("B", "2", 5000); // 2. Put B
        smallCache.put("C", "3", 5000); // 3. Put C

        // Cache is [A, B, C] (A is oldest)

        // Access A to make it "fresh" (MRU)
        // Cache becomes [B, C, A] (B is now oldest)
        smallCache.get("A");

        // Add D. B should be evicted.
        smallCache.put("D", "4", 5000);

        // Assertions
        assertFalse(smallCache.get("B").isPresent(), "B should have been evicted (LRU)");
        assertTrue(smallCache.get("A").isPresent(), "A should still exist (recently accessed)");
        assertTrue(smallCache.get("D").isPresent(), "D should exist (newly added)");
    }

    // --- CONCURRENCY STRESS TEST ---

    @Test
    @DisplayName("Concurrency: 100 Threads hitting the cache simultaneously")
    void testHighConcurrency() throws InterruptedException {
        int threadCount = 100;
        int opsPerThread = 1000;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // Submit tasks
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < opsPerThread; j++) {
                        String key = "key-" + threadId + "-" + j;
                        cache.put(key, "value", 10000);

                        // Read back immediately to verify visibility
                        if (cache.get(key).isPresent()) {
                            successCount.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for all threads to finish
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Verify
        // We performed (100 threads * 1000 ops) = 100,000 operations
        // The cache capacity is 100.
        // It should NOT crash, and size should be capped at 100 (or slightly less due to concurrency gaps)

        System.out.println("Successful Operations: " + successCount.get());

        // Check that it didn't throw exceptions and obeyed the limit.
        assertTrue(cache.size() <= CAPACITY + 16,
                "Cache size should roughly respect capacity (allowing for small segment drift)");
    }

    @Test
    @DisplayName("Update Existing Key: Should update value and refresh LRU position")
    void testUpdateExistingKey() {
        // Capacity 2
        NanoCache<String, String> smallCache = new ShardedNanoCacheImpl<>(2, 1);

        smallCache.put("A", "original", 5000);
        smallCache.put("B", "standby", 5000);

        // Cache is [A, B]

        // Update A. This should make A the "Newest" (MRU).
        smallCache.put("A", "updated", 5000);

        // Now add C. Since A was just updated, B is now the oldest. B should go.
        smallCache.put("C", "new", 5000);

        // Assertions
        assertEquals("updated", smallCache.get("A").get(), "Value should be updated");
        assertFalse(smallCache.get("B").isPresent(), "B should be evicted because A was updated (made MRU)");
        assertTrue(smallCache.get("C").isPresent(), "C should exist");
    }

    @Test
    @DisplayName("Explicit Removal: Should delete item and allow re-insertion")
    void testExplicitRemoval() {
        cache.put("deleteMe", "data", 5000);
        assertTrue(cache.get("deleteMe").isPresent());

        cache.remove("deleteMe");
        assertFalse(cache.get("deleteMe").isPresent(), "Item should be gone after remove");

        // Ensure we can add it back (not permanently blacklisted)
        cache.put("deleteMe", "newData", 5000);
        assertEquals("newData", cache.get("deleteMe").get());
    }

    @Test
    @DisplayName("Volume Test: Fill cache to capacity and verify only latest exist")
    void testVolumeDataIntegrity() {
        int exactCapacity = 500;
        // Segment to ensure strict LRU behavior without sharding noise
        NanoCache<Integer, String> strictCache = new ShardedNanoCacheImpl<>(exactCapacity, 1);

        // Fill exactly to capacity
        for (int i = 0; i < exactCapacity; i++) {
            strictCache.put(i, "val-" + i, 10000);
        }
        assertEquals(exactCapacity, strictCache.size());

        // Overfill by 100 items
        for (int i = exactCapacity; i < exactCapacity + 100; i++) {
            strictCache.put(i, "val-" + i, 10000);
        }

        // Verify Size (Should still be capped)
        assertEquals(exactCapacity, strictCache.size());

        // Verify Content
        // The first 100 items (0 to 99) should be gone.
        // The items from 100 to 599 should exist.
        for (int i = 0; i < 100; i++) {
            assertFalse(strictCache.get(i).isPresent(), "Old item " + i + " should be evicted");
        }
        for (int i = 100; i < exactCapacity + 100; i++) {
            assertTrue(strictCache.get(i).isPresent(), "Recent item " + i + " should exist");
        }
    }

    @Test
    @DisplayName("Expiry Race: Concurrent reads on expiring items")
    void testConcurrentExpiry() throws InterruptedException {
        String key = "flash";
        cache.put(key, "data", 100); // 100ms life

        int threads = 10;
        CountDownLatch latch = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger successfulReads = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    // Hammer the cache for 200ms (covering the expiration window)
                    for (int j = 0; j < 20; j++) {
                        if (cache.get(key).isPresent()) {
                            successfulReads.incrementAndGet();
                        }
                        Thread.sleep(10);
                    }
                } catch (InterruptedException e) {
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        pool.shutdown();

        // We expect some reads to succeed (before 100ms) and some to fail (after 100ms).
        // If successfulReads is 0, something is wrong (too slow).
        // If successfulReads is (threads * 20), expiration didn't work.
        System.out.println("Successful reads during expiration window: " + successfulReads.get());
        assertTrue(successfulReads.get() > 0, "Should be able to read before expiration");
        assertTrue(successfulReads.get() < (threads * 20), "Should eventually stop reading after expiration");
    }
}