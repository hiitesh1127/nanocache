package com.nanocache.policy;

public sealed interface EvictionPolicy<K> permits LRUPolicy {

    // Called when a key is accessed (GET) -> move to MRU (Most Recently Used)
    void onAccess(K key);

    // Called when a key is added (PUT)
    void onPut(K key);

    // Called when a key is explicitly removed
    void onRemove(K key);

    // Returns the key to be evicted (LRU)
    K evict();
}
