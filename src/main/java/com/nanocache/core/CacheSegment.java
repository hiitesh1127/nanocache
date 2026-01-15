package com.nanocache.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.StampedLock;

public class CacheSegment<K, V> {

    // We use a standard HashMap because we are managing the locks ourselves
    private final Map<K, CacheEntry<V>> map = new HashMap<>();

    // Advanced Concurrency: StampedLock for optimistic locking
    private final StampedLock lock = new StampedLock();

    public void put(K key, V value, long ttlMillis) {
        long stamp = lock.writeLock(); // Exclusive Lock (Blocks everyone)
        try {
            long expiresAt = System.currentTimeMillis() + ttlMillis;
            map.put(key, new CacheEntry<>(value, expiresAt));
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    public Optional<V> get(K key) {
        // --- OPTIMISTIC READ START ---
        // Try to get a stamp without actually locking (very fast)
        long stamp = lock.tryOptimisticRead();

        // Read the value
        CacheEntry<V> entry = map.get(key);

        // Check: Did a write happen while I was reading?
        if (!lock.validate(stamp)) {
            // Yes, a write happened. Optimistic read failed.
            // Fallback to a pessimisic Read Lock (slower, blocks writers)
            stamp = lock.readLock();
            try {
                entry = map.get(key);
            } finally {
                lock.unlockRead(stamp);
            }
        }
        // --- OPTIMISTIC READ END ---

        if (entry == null || entry.isExpired()) {
            // Lazy expiration: if we find it expired during get, we can return empty.
            // (Ideally, we trigger a background cleanup here or return null)
            return Optional.empty();
        }

        return Optional.of(entry.value());
    }

    public void remove(K key) {
        long stamp = lock.writeLock();
        try {
            map.remove(key);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    public int size() {
        long stamp = lock.tryOptimisticRead();
        int size = map.size();
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                size = map.size();
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return size;
    }
}