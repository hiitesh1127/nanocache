package com.nanocache.core;

import com.nanocache.policy.EvictionPolicy;
import com.nanocache.policy.LRUPolicy;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.StampedLock;

public class CacheSegment<K, V> {

    // We use a standard HashMap because we are managing the locks ourselves
    private final Map<K, CacheEntry<V>> map = new HashMap<>();
    private final EvictionPolicy<K> policy = new LRUPolicy<>();
    private final int capacity;

    // Advanced Concurrency: StampedLock for optimistic locking
    private final StampedLock lock = new StampedLock();

    public CacheSegment(int capacity) {
        this.capacity = capacity;
    }

    public void put(K key, V value, long ttlMillis) {
        long stamp = lock.writeLock(); // Exclusive Lock (Blocks everyone)
        try {
            if(map.size() >= capacity && !map.containsKey(key)) {
                K victim = policy.evict();
                if (victim != null) {
                    map.remove(victim); // Remove from storage
                    // System.out.println("Evicted: " + victim);
                }
            }
            long expiresAt = System.currentTimeMillis() + ttlMillis;
            map.put(key, new CacheEntry<>(value, expiresAt));
            policy.onPut(key);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    public Optional<V> get(K key) {
        // Acquire Write Lock
        // We need a WRITE lock because 'policy.onAccess(key)' modifies the
        // Doubly Linked List (changing 'prev' and 'next' pointers).
        long stamp = lock.writeLock();

        try {
            // Read the value
            CacheEntry<V> entry = map.get(key);

            if (entry == null || entry.isExpired()) {
                if (entry != null) {
                    // Lazy Cleanup: If we found it but it's expired, remove it now.
                    map.remove(key);
                    policy.onRemove(key);
                }
                return Optional.empty();
            }

            // This moves the accessed key to the Head of the eviction list.
            policy.onAccess(key);

            return Optional.of(entry.value());
        } finally {
            lock.unlockRead(stamp);
        }
    }

    public void remove(K key) {
        long stamp = lock.writeLock();
        try {
            map.remove(key);
            policy.onRemove(key);
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