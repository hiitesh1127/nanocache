package com.nanocache.core;

import java.util.Optional;

// The main entry point implementing our API
public class ShardedNanoCacheImpl<K, V> implements NanoCache<K, V> {

    private final CacheSegment<K, V>[] segments;
    private final int segmentMask;

    @SuppressWarnings("unchecked")
    public ShardedNanoCacheImpl(int totalCapacity, int concurrencyLevel) {
        // Calculate Segment Count (Power of 2)
        int numSegments = findNextPowerOfTwo(concurrencyLevel);
        this.segmentMask = numSegments - 1;
        this.segments = new CacheSegment[numSegments];

        // Calculate Capacity PER Segment
        // If total is 1024 and segments are 16, each segment gets 64.
        // We use Math.ceil to ensure we don't under-allocate if division isn't perfect.
        int segmentCapacity = (int) Math.ceil((double) totalCapacity / numSegments);

        // Initialize segments
        for (int i = 0; i < numSegments; i++) {
            this.segments[i] = new CacheSegment<>(segmentCapacity);
        }
    }

    // --- The Core Routing Logic ---

    private int getSegmentIndex(K key) {
        int hash = key.hashCode();

        // Optimization: Bitwise AND is faster than Modulo (%)
        // equivalent to: Math.abs(hash) % segments.length
        return (hash ^ (hash >>> 16)) & segmentMask;
    }

    private CacheSegment<K, V> segmentFor(K key) {
        return segments[getSegmentIndex(key)];
    }

    // --- API Implementation ---

    @Override
    public void put(K key, V value, long ttlMillis) {
        // Delegate to the specific segment responsible for this key
        segmentFor(key).put(key, value, ttlMillis);
    }

    @Override
    public Optional<V> get(K key) {
        return segmentFor(key).get(key);
    }

    @Override
    public void remove(K key) {
        segmentFor(key).remove(key);
    }

    // Helper to enforce Power of 2
    private int findNextPowerOfTwo(int n) {
        int power = 1;
        while (power < n) {
            power <<= 1;
        }
        return power;
    }
}