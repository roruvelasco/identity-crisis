package com.identitycrisis.client.net;

import com.identitycrisis.shared.net.*;
import com.identitycrisis.client.game.LocalGameState;

/**
 * Routes incoming server messages to update LocalGameState.
 * Runs on reader thread — marshal UI updates via Platform.runLater().
 */
public class ServerMessageRouter {

    private LocalGameState localGameState;

    // Callback interfaces for UI events
    private Runnable onLobbyStateChanged;
    private Runnable onGameStarted;
    private Runnable onElimination;
    private Runnable onGameOver;
    private Runnable onChatReceived;

    public ServerMessageRouter(LocalGameState localGameState) {
        this.localGameState = localGameState;
    }

    public void route(MessageType type, MessageDecoder decoder) {
        // switch (type):
        //   S_LOBBY_STATE     → update lobby, fire onLobbyStateChanged
        //   S_GAME_STATE      → localGameState.updateFromSnapshot(...)
        //   S_ROUND_STATE     → localGameState.updateRoundState(...)
        //   S_SAFE_ZONE       → localGameState.updateSafeZones(...)
        //   S_PLAYER_ELIMINATED → mark eliminated, fire callback
        //   S_CHAOS_EVENT     → localGameState.setChaosEvent(...)
        //   S_CONTROL_SWAP    → localGameState.setControlledPlayerId(...)
        //   S_GAME_OVER       → set game over, fire callback
        //   S_CHAT_BROADCAST  → add chat message, fire callback
    }

    public void setOnLobbyStateChanged(Runnable r) { this.onLobbyStateChanged = r; }
    public void setOnGameStarted(Runnable r) { this.onGameStarted = r; }
    public void setOnElimination(Runnable r) { this.onElimination = r; }
    public void setOnGameOver(Runnable r) { this.onGameOver = r; }
    public void setOnChatReceived(Runnable r) { this.onChatReceived = r; }
}
