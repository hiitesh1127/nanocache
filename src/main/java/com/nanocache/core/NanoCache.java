package com.nanocache.core;

import java.util.Optional;

public interface NanoCache<K, V> {

    /**
     * Stores a value in the cache with a Time-To-Live (TTL).
     *
     * @param key       The key to identify the value.
     * @param value     The value to store.
     * @param ttlMillis The lifespan of the entry in milliseconds.
     */
    void put(K key, V value, long ttlMillis);

    /**
     * Retrieves a value associated with the key.
     *
     * @param key The key to look up.
     * @return An Optional containing the value if found and not expired,
     * otherwise Optional.empty().
     */
    Optional<V> get(K key);

    /**
     * Explicitly removes a value associated with the key.
     *
     * @param key The key to remove.
     */
    void remove(K key);

    /**
     * Returns the approximate number of items in the cache.
     * (Approximate because count is aggregated across shards)
     */
    default int size() {
        return 0; // Optional implementation
    }

    /**
     * Clears all data from the cache.
     */
    default void clear() {
        // Optional implementation
    }
}