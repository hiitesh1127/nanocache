package com.nanocache.core;

public record CacheEntry<V>(V value, long expiresAt) {
    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }
}
