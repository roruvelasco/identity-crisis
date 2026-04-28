package com.identitycrisis.client.game;

import com.identitycrisis.shared.model.*;
import com.identitycrisis.shared.net.MessageDecoder;
import java.util.List;

/** Client-side volatile game-state cache written by net thread, read by render thread. */
public class LocalGameState {

    private volatile int lobbyConnectedCount;
    private volatile int lobbyRequiredCount;
    private volatile String[] lobbyPlayerNames;
    private volatile boolean[] lobbyReadyFlags;

    private volatile int roundNumber;
    private volatile double timerRemaining;
    private volatile RoundPhase phase;
    private volatile ChaosEventType activeChaos;
    private volatile double chaosDurationRemaining;
    private volatile int controlledPlayerId;
    private volatile int myPlayerId;
    private volatile List<Player> players = new java.util.ArrayList<>();
    private volatile List<SafeZone> safeZones = new java.util.ArrayList<>();

    private volatile boolean gameOver;
    private volatile int winnerPlayerId;
    private volatile String winnerName;
    private volatile List<String> chatMessages;
    private volatile String lastEliminatedName;

    /** Sentinel for "no snapshot received yet"; callers fall back to local state. */
    public static final int NO_ROUND = 0;

    public void updateFromSnapshot(MessageDecoder.GameStateData data) {
        this.roundNumber = data.roundNumber();
        this.timerRemaining = data.timerRemaining();
        this.phase = RoundPhase.values()[data.phaseOrdinal()];
        this.activeChaos = ChaosEventType.values()[data.chaosOrdinal()];
        this.chaosDurationRemaining = data.chaosDuration();
        this.controlledPlayerId = data.controlledPlayerId();

        List<SafeZone> updatedZones = new java.util.ArrayList<>();
        if (data.zones() != null) {
            for (MessageDecoder.SafeZoneNetData z : data.zones()) {
                updatedZones.add(new SafeZone(z.id(), z.x(), z.y(), z.w(), z.h()));
            }
        }
        this.safeZones = updatedZones;
    }
    public void updateLobbyState(MessageDecoder.LobbyStateData data) {
        this.lobbyConnectedCount = data.connectedCount();
        this.lobbyRequiredCount   = data.requiredCount();
        this.lobbyPlayerNames     = data.names();
        this.lobbyReadyFlags     = data.ready();
    }
    public void updateRoundState(MessageDecoder.RoundStateData data) {
        this.roundNumber    = data.roundNumber();
        this.phase          = RoundPhase.values()[data.phaseOrdinal()];
        this.timerRemaining = data.timerRemaining();
    }
    public void updateSafeZones(MessageDecoder.SafeZoneData data) {
        if (data == null || data.ids() == null) return;
        List<SafeZone> updated = new java.util.ArrayList<>(data.ids().length);
        for (int i = 0; i < data.ids().length; i++) {
            updated.add(new SafeZone(
                data.ids()[i], data.xs()[i], data.ys()[i],
                data.ws()[i],  data.hs()[i]));
        }
        this.safeZones = updated;
    }
    public void markEliminated(MessageDecoder.EliminationData data) {
        if (data != null) this.lastEliminatedName = data.playerName();
    }
    public void setChaosEvent(MessageDecoder.ChaosEventData data) {
        this.activeChaos = ChaosEventType.values()[data.chaosOrdinal()];
        this.chaosDurationRemaining = data.duration();
    }
    public void setControlledPlayerId(int id) { this.controlledPlayerId = id; }
    public void setGameOver(MessageDecoder.GameOverData data) {
        this.gameOver       = true;
        this.winnerPlayerId = data.winnerPlayerId();
        this.winnerName     = data.winnerName();
    }
    public void addChatMessage(MessageDecoder.ChatData data) {
        if (data == null) return;
        if (this.chatMessages == null) this.chatMessages = new java.util.ArrayList<>();
        this.chatMessages.add(data.senderName() + ": " + data.text());
    }
    public void setMyPlayerId(int id) { this.myPlayerId = id; }

    public int getRoundNumber() { return roundNumber; }
    public double getTimerRemaining() { return timerRemaining; }
    public RoundPhase getPhase() { return phase; }
    public ChaosEventType getActiveChaos() { return activeChaos; }
    public double getChaosDurationRemaining() { return chaosDurationRemaining; }
    public int getControlledPlayerId() { return controlledPlayerId; }
    public int getMyPlayerId() { return myPlayerId; }
    public List<Player> getPlayers() { return players; }
    public List<SafeZone> getSafeZones() { return safeZones; }
    public boolean isGameOver() { return gameOver; }
    public int getWinnerPlayerId() { return winnerPlayerId; }
    public String getWinnerName() { return winnerName; }
    public List<String> getChatMessages() { return chatMessages; }
    public String getLastEliminatedName() { return lastEliminatedName; }
    public int getLobbyConnectedCount() { return lobbyConnectedCount; }
    public int getLobbyRequiredCount() { return lobbyRequiredCount; }
    public String[] getLobbyPlayerNames() { return lobbyPlayerNames; }
    public boolean[] getLobbyReadyFlags() { return lobbyReadyFlags; }

    public boolean hasReceivedSnapshot() { return roundNumber != NO_ROUND; }
}
