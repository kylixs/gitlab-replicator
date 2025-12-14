package com.gitlab.mirror.server.service.monitor;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Local Cache Manager
 * <p>
 * Manages in-memory cache with TTL support using ConcurrentHashMap.
 * Provides thread-safe cache operations with automatic expiration.
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Component
public class LocalCacheManager {

    private final Map<String, CacheEntry<?>> cache = new ConcurrentHashMap<>();
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);

    /**
     * Put value into cache with TTL
     *
     * @param key Cache key
     * @param value Value to cache
     * @param ttlMinutes TTL in minutes
     * @param <T> Value type
     */
    public <T> void put(String key, T value, long ttlMinutes) {
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(ttlMinutes);
        cache.put(key, new CacheEntry<>(value, expiresAt));
        log.debug("Cached key: {}, expires at: {}", key, expiresAt);
    }

    /**
     * Get value from cache
     *
     * @param key Cache key
     * @param <T> Value type
     * @return Cached value or null if not found/expired
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        CacheEntry<?> entry = cache.get(key);

        if (entry == null) {
            misses.incrementAndGet();
            log.debug("Cache miss: {}", key);
            return null;
        }

        // Check expiration
        if (entry.isExpired()) {
            cache.remove(key);
            misses.incrementAndGet();
            log.debug("Cache expired: {}", key);
            return null;
        }

        hits.incrementAndGet();
        log.debug("Cache hit: {}", key);
        return (T) entry.getValue();
    }

    /**
     * Remove value from cache
     *
     * @param key Cache key
     */
    public void remove(String key) {
        cache.remove(key);
        log.debug("Removed cache key: {}", key);
    }

    /**
     * Clear all cache
     */
    public void clear() {
        int size = cache.size();
        cache.clear();
        hits.set(0);
        misses.set(0);
        log.info("Cleared cache, removed {} entries", size);
    }

    /**
     * Get cache size
     *
     * @return Number of entries in cache
     */
    public int size() {
        return cache.size();
    }

    /**
     * Get cache statistics
     *
     * @return Cache statistics
     */
    public CacheStats getStats() {
        long totalHits = hits.get();
        long totalMisses = misses.get();
        long total = totalHits + totalMisses;
        double hitRate = total > 0 ? (totalHits * 100.0 / total) : 0.0;

        return new CacheStats(cache.size(), totalHits, totalMisses, hitRate);
    }

    /**
     * Clean up expired entries (runs every 5 minutes)
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void cleanupExpiredEntries() {
        int removed = 0;
        for (Map.Entry<String, CacheEntry<?>> entry : cache.entrySet()) {
            if (entry.getValue().isExpired()) {
                cache.remove(entry.getKey());
                removed++;
            }
        }

        if (removed > 0) {
            log.info("Cleaned up {} expired cache entries", removed);
        }
    }

    /**
     * Cache Entry
     */
    @Data
    @AllArgsConstructor
    private static class CacheEntry<T> {
        private final T value;
        private final LocalDateTime expiresAt;

        public boolean isExpired() {
            return LocalDateTime.now().isAfter(expiresAt);
        }
    }

    /**
     * Cache Statistics
     */
    @Data
    @AllArgsConstructor
    public static class CacheStats {
        private int size;
        private long hits;
        private long misses;
        private double hitRate;
    }
}
