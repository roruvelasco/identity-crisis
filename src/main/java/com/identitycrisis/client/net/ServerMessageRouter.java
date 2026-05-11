package com.identitycrisis.client.net;

import com.identitycrisis.shared.net.*;
import com.identitycrisis.client.game.LocalGameState;
import javafx.application.Platform;

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
        switch (type) {
            case S_LOBBY_STATE -> {
                MessageDecoder.LobbyStateData data = decoder.decodeLobbyState();
                localGameState.updateLobbyState(data);
                if (onLobbyStateChanged != null) {
                    Platform.runLater(onLobbyStateChanged);
                }
            }
            case S_GAME_STATE -> {
                MessageDecoder.GameStateData data = decoder.decodeGameState();
                localGameState.updateFromSnapshot(data);
                // Phase 3: Latch myPlayerId from the first snapshot we receive.
                // controlledPlayerId == clientId at game start (no control swap yet).
                if (localGameState.getMyPlayerId() == 0) {
                    localGameState.setMyPlayerId(data.controlledPlayerId());
                }
                // Phase 6: Fire game-started callback exactly once — first snapshot
                // arriving means the server game loop has kicked off.
                if (onGameStarted != null) {
                    Runnable cb = onGameStarted;
                    onGameStarted = null; // null before runLater to prevent double-fire
                    Platform.runLater(cb);
                }
            }
            case S_ROUND_STATE -> {
                // localGameState.updateRoundState(decoder.decodeRoundState());
            }
            case S_SAFE_ZONE -> {
                // localGameState.updateSafeZones(decoder.decodeSafeZone());
            }
            case S_PLAYER_ELIMINATED -> {
                // localGameState.markEliminated(decoder.decodeElimination());
                // if (onElimination != null) Platform.runLater(onElimination);
            }
            case S_CHAOS_EVENT -> {
                localGameState.setChaosEvent(decoder.decodeChaosEvent());
            }
            case S_CONTROL_SWAP -> {
                localGameState.setControlledPlayerId(decoder.decodeControlSwap());
            }
            case S_GAME_OVER -> {
                localGameState.setGameOver(decoder.decodeGameOver());
                if (onGameOver != null) Platform.runLater(onGameOver);
            }
            case S_CHAT_BROADCAST -> {
                // localGameState.addChatMessage(decoder.decodeChat());
                // if (onChatReceived != null) Platform.runLater(onChatReceived);
            }
            default -> {
                // Unknown message type — silently skip
            }
        }
    }

    public void setOnLobbyStateChanged(Runnable r) { this.onLobbyStateChanged = r; }
    public void setOnGameStarted(Runnable r) { this.onGameStarted = r; }
    public void setOnElimination(Runnable r) { this.onElimination = r; }
    public void setOnGameOver(Runnable r) { this.onGameOver = r; }
    public void setOnChatReceived(Runnable r) { this.onChatReceived = r; }
}
