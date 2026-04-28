package com.identitycrisis.server.game;

import com.identitycrisis.server.net.ClientConnection;
import com.identitycrisis.server.net.GameServer;
import com.identitycrisis.shared.model.GameConfig;
import com.identitycrisis.shared.model.Player;
import com.identitycrisis.shared.model.RoundPhase;
import com.identitycrisis.shared.net.MessageEncoder;
import com.identitycrisis.shared.util.Vector2D;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pre-game lobby. Accepts players, tracks readiness, signals start. */
public class LobbyManager {

    private final GameServer server;
    private final Set<Integer> readyClientIds = new HashSet<>();
    private GameState gameState;
    private SafeZoneManager safeZoneManager;
    private boolean gameStarted = false;

    public LobbyManager(GameServer server) { this.server = server; }

    /** Setter injection — called from ServerApp.main() after GameState is created. */
    public void setGameState(GameState gs) { this.gameState = gs; }

    /** Setter injection — called from ServerApp.main() so lobby can spawn round-1 safe zone. */
    public void setSafeZoneManager(SafeZoneManager szm) { this.safeZoneManager = szm; }

    public synchronized void handleJoin(ClientConnection client, String displayName) {
        if (displayName == null || displayName.isBlank()) {
            displayName = "Player" + client.getClientId();
        }
        client.setDisplayName(displayName);
        broadcastLobbyState();
    }

    public synchronized void handleReady(ClientConnection client) {
        if (gameStarted) return;
        readyClientIds.add(client.getClientId());
        broadcastLobbyState();
        if (canStartGame()) {
            gameStarted = true;
            List<ClientConnection> clients = server.getClients();
            double cx = gameState.getArena().getWidth()  / 2.0;
            double cy = gameState.getArena().getHeight() / 2.0;
            int n = clients.size();
            for (int i = 0; i < n; i++) {
                ClientConnection c = clients.get(i);
                Player p = new Player(c.getClientId(), c.getDisplayName());
                double angle = 2 * Math.PI * i / n;
                p.setPosition(new Vector2D(
                    cx + GameConfig.SPAWN_RADIUS * Math.cos(angle),
                    cy + GameConfig.SPAWN_RADIUS * Math.sin(angle)));
                gameState.getPlayers().add(p);
                gameState.getControlMap().put(c.getClientId(), c.getClientId());
            }
            // Round 1 is a warm-up round → unlimited capacity, one zone per player.
            // The N-zones-for-N-players formula matches RoundManager.startNewRound's
            // warm-up branch so subsequent rounds use the identical algorithm.
            safeZoneManager.spawnRoundZones(n);
            gameState.setPhase(RoundPhase.COUNTDOWN);
            gameState.setRoundNumber(1);
            gameState.setRoundTimer(GameConfig.COUNTDOWN_SECONDS);
            server.startGame();
        }
    }

    public boolean canStartGame() {
        List<ClientConnection> clients = server.getClients();
        return clients.size() >= GameConfig.MIN_PLAYERS
            && readyClientIds.containsAll(
                   clients.stream().map(ClientConnection::getClientId).toList());
    }

    public void broadcastLobbyState() {
        List<ClientConnection> clients = server.getClients();
        int count = clients.size();
        String[] names  = new String[count];
        boolean[] ready = new boolean[count];
        for (int i = 0; i < count; i++) {
            ClientConnection c = clients.get(i);
            names[i] = c.getDisplayName() != null
                     ? c.getDisplayName() : "Player " + c.getClientId();
            ready[i] = readyClientIds.contains(c.getClientId());
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MessageEncoder enc = new MessageEncoder(new DataOutputStream(baos));
            enc.encodeLobbyState(count, GameConfig.MIN_PLAYERS, names, ready);
            enc.flush();
            server.broadcastToAll(baos.toByteArray());
        } catch (IOException e) {
            // log and continue
        }
    }
}
