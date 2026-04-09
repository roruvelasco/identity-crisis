package com.identitycrisis.server.game;

import com.identitycrisis.server.net.ClientConnection;
import com.identitycrisis.server.net.GameServer;

/** Pre-game lobby. Accepts players, tracks readiness, signals start. */
public class LobbyManager {

    private GameServer server;

    public LobbyManager(GameServer server) { this.server = server; }

    public void handleJoin(ClientConnection client, String displayName) { }

    public void handleReady(ClientConnection client) { }

    public boolean canStartGame() { throw new UnsupportedOperationException("stub"); }

    public void broadcastLobbyState() { }
}
