package com.nanocache.policy;

import java.util.HashMap;
import java.util.Map;

public final class LRUPolicy<K> implements EvictionPolicy<K> {

    // Internal Node for our Doubly Linked List
    private static class Node<K> {
        K key;
        Node<K> prev;
        Node<K> next;

        Node(K key) { this.key = key; }
    }

    private final Map<K, Node<K>> nodeMap = new HashMap<>();
    private Node<K> head; // Most Recently Used (MRU)
    private Node<K> tail; // Least Recently Used (LRU)

    @Override
    public void onAccess(K key) {
        if (nodeMap.containsKey(key)) {
            removeNode(nodeMap.get(key));
            addToHead(nodeMap.get(key));
        }
    }

    @Override
    public void onPut(K key) {
        if (nodeMap.containsKey(key)) {
            onAccess(key);
            return;
        }
        Node<K> newNode = new Node<>(key);
        nodeMap.put(key, newNode);
        addToHead(newNode);
    }

    @Override
    public void onRemove(K key) {
        if (nodeMap.containsKey(key)) {
            removeNode(nodeMap.get(key));
            nodeMap.remove(key);
        }
    }

    @Override
    public K evict() {
        if (tail == null) return null;

        K victimKey = tail.key;

        // Remove from internal tracking
        removeNode(tail);
        nodeMap.remove(victimKey);

        return victimKey;
    }

    // --- Helper Methods (Classic Data Structure Operations) ---

    private void addToHead(Node<K> node) {
        if (head == null) {
            head = tail = node;
        } else {
            node.next = head;
            head.prev = node;
            head = node;
        }
    }

    private void removeNode(Node<K> node) {
        if (node.prev != null) node.prev.next = node.next;
        else head = node.next; // Node was head

        if (node.next != null) node.next.prev = node.prev;
        else tail = node.prev; // Node was tail

        node.prev = null;
        node.next = null;
    }
}