package com.identitycrisis.server.game;

import com.identitycrisis.server.net.ClientConnection;
import com.identitycrisis.server.net.GameServer;
import com.identitycrisis.shared.model.GameConfig;
import com.identitycrisis.shared.model.Player;
import com.identitycrisis.shared.model.RoundPhase;
import com.identitycrisis.shared.net.MessageEncoder;
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

    public LobbyManager(GameServer server) { this.server = server; }

    /** Setter injection — called from ServerApp.main() after GameState is created. */
    public void setGameState(GameState gs) { this.gameState = gs; }

    public void handleJoin(ClientConnection client, String displayName) {
        client.setDisplayName(displayName);
        broadcastLobbyState();
    }

    public void handleReady(ClientConnection client) {
        readyClientIds.add(client.getClientId());
        broadcastLobbyState();
        if (canStartGame()) {
            for (ClientConnection c : server.getClients()) {
                Player p = new Player(c.getClientId(), c.getDisplayName());
                gameState.getPlayers().add(p);
                gameState.getControlMap().put(c.getClientId(), c.getClientId());
            }
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
