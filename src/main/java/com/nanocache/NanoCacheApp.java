package com.nanocache;

import com.nanocache.server.NanoCacheServer;

public class NanoCacheApp {

    public static void main(String[] args) {
        // Default Configuration
        int port = 8080;
        int capacity = 1024;

        // Argument Parsing
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number. Using default: " + port);
            }
        }

        if (args.length > 1) {
            try {
                capacity = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid capacity. Using default: " + capacity);
            }
        }

        printBanner();
        System.out.println("   [ Configuration ]");
        System.out.println("   > Port:     " + port);
        System.out.println("   > Capacity: " + capacity + " items");
        System.out.println("   > Engine:   Sharded StampedLock (Java 21)");
        System.out.println("---------------------------------------------");

        // Start the Server
        NanoCacheServer server = new NanoCacheServer(port, capacity);
        server.start();
    }

    private static void printBanner() {
        System.out.println("\n" +
                "  _   _                  _____           _          \n" +
                " | \\ | |                / ____|         | |         \n" +
                " |  \\| | __ _ _ __   __| |     __ _  ___| |__   ___ \n" +
                " | . ` |/ _` | '_ \\ / _` |    / _` |/ __| '_ \\ / _ \\\n" +
                " | |\\  | (_| | | | | (_| |___| (_| | (__| | | |  __/\n" +
                " |_| \\_|\\__,_|_| |_|\\__,_|\\_____\\__,_|\\___|_| |_|\\___|\n" +
                "                                                      \n" +
                " :: High-Performance Concurrent Cache :: (v1.0)       \n");
    }
}