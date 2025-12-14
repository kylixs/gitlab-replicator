package com.gitlab.mirror.server.service.monitor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LocalCacheManager Test
 *
 * @author GitLab Mirror Team
 */
class LocalCacheManagerTest {

    private LocalCacheManager cacheManager;

    @BeforeEach
    void setUp() {
        cacheManager = new LocalCacheManager();
    }

    @Test
    void testPutAndGet_success() {
        // Put value
        cacheManager.put("key1", "value1", 10);

        // Get value
        String value = cacheManager.get("key1");

        assertThat(value).isEqualTo("value1");
    }

    @Test
    void testGet_keyNotFound() {
        String value = cacheManager.get("nonexistent");

        assertThat(value).isNull();
    }

    @Test
    void testGet_expired() throws InterruptedException {
        // Put with very short TTL (1 second = 1/60 minutes)
        cacheManager.put("key2", "value2", 0); // TTL 0 minutes - should expire immediately

        // Wait a bit
        Thread.sleep(100);

        // Get should return null
        String value = cacheManager.get("key2");

        assertThat(value).isNull();
    }

    @Test
    void testRemove_success() {
        cacheManager.put("key3", "value3", 10);

        String value = cacheManager.get("key3");
        assertThat(value).isEqualTo("value3");

        cacheManager.remove("key3");

        value = cacheManager.get("key3");
        assertThat(value).isNull();
    }

    @Test
    void testClear_removesAllEntries() {
        cacheManager.put("key1", "value1", 10);
        cacheManager.put("key2", "value2", 10);
        cacheManager.put("key3", "value3", 10);

        assertThat(cacheManager.size()).isEqualTo(3);

        cacheManager.clear();

        assertThat(cacheManager.size()).isEqualTo(0);
    }

    @Test
    void testSize_countsEntries() {
        assertThat(cacheManager.size()).isEqualTo(0);

        cacheManager.put("key1", "value1", 10);
        assertThat(cacheManager.size()).isEqualTo(1);

        cacheManager.put("key2", "value2", 10);
        assertThat(cacheManager.size()).isEqualTo(2);

        cacheManager.remove("key1");
        assertThat(cacheManager.size()).isEqualTo(1);
    }

    @Test
    void testGetStats_tracksHitsAndMisses() {
        cacheManager.put("key1", "value1", 10);

        // Hit
        cacheManager.get("key1");
        cacheManager.get("key1");

        // Miss
        cacheManager.get("nonexistent");

        LocalCacheManager.CacheStats stats = cacheManager.getStats();

        assertThat(stats.getHits()).isEqualTo(2);
        assertThat(stats.getMisses()).isEqualTo(1);
        assertThat(stats.getHitRate()).isCloseTo(66.67, org.assertj.core.data.Offset.offset(0.1));
    }

    @Test
    void testGetStats_emptyCache() {
        LocalCacheManager.CacheStats stats = cacheManager.getStats();

        assertThat(stats.getSize()).isEqualTo(0);
        assertThat(stats.getHits()).isEqualTo(0);
        assertThat(stats.getMisses()).isEqualTo(0);
        assertThat(stats.getHitRate()).isEqualTo(0.0);
    }

    @Test
    void testConcurrentAccess_threadSafe() throws InterruptedException {
        int threadCount = 10;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String key = "key-" + threadId + "-" + j;
                        cacheManager.put(key, "value-" + j, 10);
                        cacheManager.get(key);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Verify no data corruption
        assertThat(cacheManager.size()).isEqualTo(threadCount * operationsPerThread);
    }

    @Test
    void testCleanupExpiredEntries_removesExpired() throws InterruptedException {
        // Put entries with very short TTL
        cacheManager.put("short1", "value1", 0);
        cacheManager.put("short2", "value2", 0);
        cacheManager.put("long1", "value3", 100);

        // Wait for expiration
        Thread.sleep(100);

        // Call cleanup
        cacheManager.cleanupExpiredEntries();

        // Verify only non-expired entries remain
        assertThat(cacheManager.size()).isEqualTo(1);
        String value = cacheManager.get("long1");
        assertThat(value).isEqualTo("value3");
    }

    @Test
    void testGenericTypeSupport() {
        // Test with different types
        cacheManager.put("string", "test", 10);
        cacheManager.put("integer", 123, 10);
        cacheManager.put("long", 456L, 10);

        String stringValue = cacheManager.get("string");
        Integer intValue = cacheManager.get("integer");
        Long longValue = cacheManager.get("long");

        assertThat(stringValue).isEqualTo("test");
        assertThat(intValue).isEqualTo(123);
        assertThat(longValue).isEqualTo(456L);
    }
}
