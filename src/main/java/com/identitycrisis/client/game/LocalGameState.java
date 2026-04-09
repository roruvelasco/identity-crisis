package com.identitycrisis.client.game;

import com.identitycrisis.shared.model.*;
import com.identitycrisis.shared.net.MessageDecoder;
import java.util.List;

/**
 * Client's local copy of game state. Thread-safe via volatile reference swap.
 * Written by network thread, read by render thread.
 */
public class LocalGameState {

    // Lobby
    private volatile int lobbyConnectedCount;
    private volatile int lobbyRequiredCount;
    private volatile String[] lobbyPlayerNames;
    private volatile boolean[] lobbyReadyFlags;

    // Game (from latest snapshot)
    private volatile int roundNumber;
    private volatile double timerRemaining;
    private volatile RoundPhase phase;
    private volatile ChaosEventType activeChaos;
    private volatile double chaosDurationRemaining;
    private volatile int controlledPlayerId;
    private volatile int myPlayerId;
    private volatile List<Player> players;
    private volatile List<SafeZone> safeZones;

    // UI
    private volatile boolean gameOver;
    private volatile int winnerPlayerId;
    private volatile String winnerName;
    private volatile List<String> chatMessages;
    private volatile String lastEliminatedName;

    // Update methods (network thread)
    public void updateFromSnapshot(MessageDecoder.GameStateData data) { }
    public void updateLobbyState(MessageDecoder.LobbyStateData data) { }
    public void updateRoundState(MessageDecoder.RoundStateData data) { }
    public void updateSafeZones(MessageDecoder.SafeZoneData data) { }
    public void markEliminated(MessageDecoder.EliminationData data) { }
    public void setChaosEvent(MessageDecoder.ChaosEventData data) { }
    public void setControlledPlayerId(int id) { }
    public void setGameOver(MessageDecoder.GameOverData data) { }
    public void addChatMessage(MessageDecoder.ChatData data) { }
    public void setMyPlayerId(int id) { }

    // Read methods (render thread)
    public int getRoundNumber() { throw new UnsupportedOperationException("stub"); }
    public double getTimerRemaining() { throw new UnsupportedOperationException("stub"); }
    public RoundPhase getPhase() { throw new UnsupportedOperationException("stub"); }
    public ChaosEventType getActiveChaos() { throw new UnsupportedOperationException("stub"); }
    public int getControlledPlayerId() { throw new UnsupportedOperationException("stub"); }
    public int getMyPlayerId() { throw new UnsupportedOperationException("stub"); }
    public List<Player> getPlayers() { throw new UnsupportedOperationException("stub"); }
    public List<SafeZone> getSafeZones() { throw new UnsupportedOperationException("stub"); }
    public boolean isGameOver() { throw new UnsupportedOperationException("stub"); }
    public int getWinnerPlayerId() { throw new UnsupportedOperationException("stub"); }
    public String getWinnerName() { throw new UnsupportedOperationException("stub"); }
    public List<String> getChatMessages() { throw new UnsupportedOperationException("stub"); }
    public String getLastEliminatedName() { throw new UnsupportedOperationException("stub"); }
    public int getLobbyConnectedCount() { throw new UnsupportedOperationException("stub"); }
    public int getLobbyRequiredCount() { throw new UnsupportedOperationException("stub"); }
    public String[] getLobbyPlayerNames() { throw new UnsupportedOperationException("stub"); }
    public boolean[] getLobbyReadyFlags() { throw new UnsupportedOperationException("stub"); }
}
