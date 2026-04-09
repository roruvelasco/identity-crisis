package com.identitycrisis.server.game;

import com.identitycrisis.shared.model.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Single authoritative game state. Modified only by ServerGameLoop. */
public class GameState {

    private List<Player> players;
    private Arena arena;
    private SafeZone trueSafeZone;
    private int roundNumber;
    private RoundPhase phase;
    private double roundTimer;
    private ChaosEventType activeChaosEvent;
    private double chaosEventTimer;
    private Map<Integer, Integer> controlMap; // clientId → controlledPlayerId
    private List<CarryState> activeCarries;
    private final List<Integer> pendingEliminationIds = new ArrayList<>();
    private int pendingGameOverWinnerId = -1;

    /**
     * Initializes all collections and sets safe defaults so no field is ever
     * {@code null} when the game managers first access it.
     */
    public GameState() {
        this.players          = new ArrayList<>();
        this.arena            = Arena.loadDefault();
        this.phase            = RoundPhase.LOBBY;
        this.roundNumber      = 0;
        this.roundTimer       = 0.0;
        this.activeChaosEvent = ChaosEventType.NONE;
        this.chaosEventTimer  = 0.0;
        this.controlMap       = new HashMap<>();
        this.activeCarries    = new ArrayList<>();
    }

    public List<Player> getPlayers() { return players; }
    public List<Player> getAlivePlayers() {
        return players.stream()
            .filter(p -> p.getState() == PlayerState.ALIVE
                      || p.getState() == PlayerState.CARRYING
                      || p.getState() == PlayerState.CARRIED)
            .toList();
    }
    public Player getPlayerById(int id) {
        return players.stream()
            .filter(p -> p.getPlayerId() == id)
            .findFirst()
            .orElse(null);
    }
    public Arena getArena() { return arena; }
    public SafeZone getTrueSafeZone() { return trueSafeZone; }
    public void setTrueSafeZone(SafeZone zone) { this.trueSafeZone = zone; }
    public int getRoundNumber() { return roundNumber; }
    public void setRoundNumber(int n) { this.roundNumber = n; }
    public RoundPhase getPhase() { return phase; }
    public void setPhase(RoundPhase phase) { this.phase = phase; }
    public double getRoundTimer() { return roundTimer; }
    public void setRoundTimer(double t) { this.roundTimer = t; }
    public ChaosEventType getActiveChaosEvent() { return activeChaosEvent; }
    public void setActiveChaosEvent(ChaosEventType type) { this.activeChaosEvent = type; }
    public double getChaosEventTimer() { return chaosEventTimer; }
    public void setChaosEventTimer(double t) { this.chaosEventTimer = t; }
    public Map<Integer, Integer> getControlMap() { return controlMap; }
    public List<CarryState> getActiveCarries() { return activeCarries; }
    public int getAliveCount() { return getAlivePlayers().size(); }

    public List<Integer> getPendingEliminationIds() { return pendingEliminationIds; }
    public void clearPendingEliminationIds() { pendingEliminationIds.clear(); }
    public int getPendingGameOverWinnerId() { return pendingGameOverWinnerId; }
    public void setPendingGameOverWinnerId(int id) { this.pendingGameOverWinnerId = id; }
}
