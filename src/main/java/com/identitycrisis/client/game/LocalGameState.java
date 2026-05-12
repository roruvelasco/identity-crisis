package com.identitycrisis.client.game;

import com.identitycrisis.shared.model.*;
import com.identitycrisis.shared.net.ChatMessageType;
import com.identitycrisis.shared.net.MessageDecoder;
import com.identitycrisis.shared.util.Vector2D;
import java.util.List;

/**
 * Client's local copy of game state. Thread-safe via volatile reference swap.
 * Written by network thread, read by render thread.
 */
public class LocalGameState {

    // Lobby
    private volatile int lobbyConnectedCount;
    private volatile int lobbyRequiredCount;
    private volatile int lobbyMyIndex = -1;
    private volatile String[] lobbyPlayerNames;
    private volatile boolean[] lobbyReadyFlags;

    // Game (from latest snapshot)
    private volatile int roundNumber;
    private volatile double timerRemaining;
    private volatile RoundPhase phase;
    private volatile ChaosEventType activeChaos = ChaosEventType.NONE;
    private volatile double chaosDurationRemaining;
    private volatile int controlledPlayerId;
    private volatile int myPlayerId;
    private volatile List<Player> players = new java.util.ArrayList<>();
    private volatile List<SafeZone> safeZones = new java.util.ArrayList<>();

    // UI
    private volatile boolean gameOver;
    private volatile int winnerPlayerId;
    private volatile String winnerName;
    private volatile List<ChatMessage> chatMessages;
    private volatile String lastEliminatedName;

    /**
     * Sentinel returned by {@link #getRoundNumber()} when the client has not
     * yet received a snapshot.  Callers (e.g. {@link com.identitycrisis.client.scene.GameArena})
     * fall back to local state in this case.
     */
    public static final int NO_ROUND = 0;

    // Update methods (network thread)
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

        List<Player> updated = new java.util.ArrayList<>();
        if (data.players() != null) {
            for (MessageDecoder.PlayerNetData pd : data.players()) {
                Player p = new Player(pd.id(), pd.name());
                p.setPosition(new Vector2D(pd.x(), pd.y()));
                p.setVelocity(new Vector2D(pd.vx(), pd.vy()));
                p.setState(PlayerState.values()[pd.stateOrdinal()]);
                p.setFacingDirection(pd.facing());
                p.setInSafeZone(pd.inSafeZone());
                p.setCarriedByPlayerId(pd.carriedBy());
                p.setCarryingPlayerId(pd.carrying());
                updated.add(p);
            }
        }
        this.players = updated;
    }
    public void updateLobbyState(MessageDecoder.LobbyStateData data) {
        this.lobbyConnectedCount = data.connectedCount();
        this.lobbyRequiredCount   = data.requiredCount();
        this.lobbyMyIndex         = data.selfIndex();
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
    public synchronized void addChatMessage(MessageDecoder.ChatData data) {
        if (data == null) return;
        java.util.ArrayList<ChatMessage> updated = new java.util.ArrayList<>();
        if (this.chatMessages != null) updated.addAll(this.chatMessages);
        updated.add(new ChatMessage(data.senderName(), data.text(), data.messageType()));
        this.chatMessages = updated;
    }
    public void setMyPlayerId(int id) { this.myPlayerId = id; }

    // Read methods (render thread)
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
    public List<ChatMessage> getChatMessages() { return chatMessages; }
    public String getLastEliminatedName() { return lastEliminatedName; }
    public int getLobbyConnectedCount() { return lobbyConnectedCount; }
    public int getLobbyRequiredCount() { return lobbyRequiredCount; }
    public int getLobbyMyIndex() { return lobbyMyIndex; }
    public String[] getLobbyPlayerNames() { return lobbyPlayerNames; }
    public boolean[] getLobbyReadyFlags() { return lobbyReadyFlags; }

    /** True once the client has received at least one game-state snapshot. */
    public boolean hasReceivedSnapshot() { return roundNumber != NO_ROUND; }

    /**
     * Resets transient per-game state so a fresh game session starts cleanly
     * without stale game-over / elimination data from the previous round.
     * Call this from GameArena.onEnter().
     */
    public void resetForNewGame() {
        this.gameOver           = false;
        this.winnerPlayerId     = 0;
        this.winnerName         = null;
        this.lastEliminatedName = null;
        this.chatMessages       = null;
        this.roundNumber        = NO_ROUND; // re-arm hasReceivedSnapshot()
        this.activeChaos        = ChaosEventType.NONE;
        this.players            = new java.util.ArrayList<>();
        this.safeZones          = new java.util.ArrayList<>();
    }

    public record ChatMessage(String senderName, String text, ChatMessageType messageType) { }
}
