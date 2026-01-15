package com.nanocache.server;

import com.nanocache.core.ShardedNanoCacheImpl;
import com.nanocache.core.NanoCache;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class NanoCacheServer {

    private final NanoCache<String, String> cache;
    private final int port;

    public NanoCacheServer(int port, int capacity) {
        this.port = port;
        // Initialize with 16 segments and LRU policy
        this.cache = new ShardedNanoCacheImpl<>(capacity, 16);
    }

    public void start() {
        System.out.println("NanoCache Server starting on port " + port);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                // Accept new connection (blocking, but that's okay)
                Socket clientSocket = serverSocket.accept();

                // Virtual Threads!
                // Instead of 'new Thread()', we use 'Thread.ofVirtual()'
                Thread.ofVirtual().start(() -> handleClient(clientSocket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleClient(Socket socket) {
        try (
                socket;
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
        ) {
            String line;
            while ((line = in.readLine()) != null) {
                String response = processCommand(line);
                out.println(response);
            }
        } catch (IOException e) {
            // Client disconnected
        }
    }

    private String processCommand(String commandLine) {
        String[] parts = commandLine.split(" ");
        String command = parts[0].toUpperCase();

        try {
            return switch (command) {
                case "PUT" -> handlePut(parts);
                case "GET" -> handleGet(parts);
                default -> "ERROR: Unknown command";
            };
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private String handlePut(String[] parts) {
        // Syntax: PUT key value ttl
        if (parts.length < 4) return "ERROR: Usage: PUT <key> <value> <ttl_ms>";

        String key = parts[1];
        String value = parts[2];
        long ttl = Long.parseLong(parts[3]);

        cache.put(key, value, ttl);
        return "OK";
    }

    private String handleGet(String[] parts) {
        // Syntax: GET key
        if (parts.length < 2) return "ERROR: Usage: GET <key>";

        String key = parts[1];
        return cache.get(key).orElse("(null)");
    }
}