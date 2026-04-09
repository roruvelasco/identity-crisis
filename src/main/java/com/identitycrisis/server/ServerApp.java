package com.identitycrisis.server;

/**
 * Entry point.
 * Usage: java com.identitycrisis.server.ServerApp [port]
 */
public class ServerApp {

    public static void main(String[] args) {
        // 1. Parse optional port (default: GameConfig.SERVER_PORT)
        // 2. Create GameServer
        // 3. Start listening (blocking on main thread or spawn listener)
        // 4. When lobby fills + all ready → create and start ServerGameLoop
    }
}
