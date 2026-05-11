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

    /** If true the game arena has already been navigated to. Used to avoid
     *  double-triggering showGameArena() when multiple snapshots arrive. */
    private volatile boolean gameArenaShown = false;

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
                // Always keep myPlayerId up-to-date (server echoes it every snapshot).
                if (data.selfPlayerId() > 0) {
                    localGameState.setMyPlayerId(data.selfPlayerId());
                }
                // Navigate to the arena the FIRST time a game-state snapshot arrives.
                // We use a dedicated flag (not the callback reference) so concurrent
                // snapshots cannot fire the navigation twice.
                if (!gameArenaShown && onGameStarted != null) {
                    gameArenaShown = true;
                    Platform.runLater(onGameStarted);
                }
            }
            case S_ROUND_STATE -> {
                localGameState.updateRoundState(decoder.decodeRoundState());
            }
            case S_SAFE_ZONE -> {
                localGameState.updateSafeZones(decoder.decodeSafeZoneUpdate());
            }
            case S_PLAYER_ELIMINATED -> {
                localGameState.markEliminated(decoder.decodePlayerEliminated());
                if (onElimination != null) Platform.runLater(onElimination);
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
                localGameState.addChatMessage(decoder.decodeChatBroadcast());
                if (onChatReceived != null) Platform.runLater(onChatReceived);
            }
            default -> {
                // Unknown message type — silently skip
            }
        }
    }

    public void setOnLobbyStateChanged(Runnable r) { this.onLobbyStateChanged = r; }
    public void setOnGameStarted(Runnable r) {
        this.onGameStarted = r;
        this.gameArenaShown = false; // reset flag when a new callback is registered
    }
    public void setOnElimination(Runnable r) { this.onElimination = r; }
    public void setOnGameOver(Runnable r) { this.onGameOver = r; }
    public void setOnChatReceived(Runnable r) { this.onChatReceived = r; }
}
