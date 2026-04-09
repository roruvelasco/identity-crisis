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

    public List<Player> getPlayers() { throw new UnsupportedOperationException("stub"); }
    public List<Player> getAlivePlayers() { throw new UnsupportedOperationException("stub"); }
    public Player getPlayerById(int id) { throw new UnsupportedOperationException("stub"); }
    public Arena getArena() { throw new UnsupportedOperationException("stub"); }
    public SafeZone getTrueSafeZone() { throw new UnsupportedOperationException("stub"); }
    public void setTrueSafeZone(SafeZone zone) { }
    public int getRoundNumber() { throw new UnsupportedOperationException("stub"); }
    public void setRoundNumber(int n) { }
    public RoundPhase getPhase() { throw new UnsupportedOperationException("stub"); }
    public void setPhase(RoundPhase phase) { }
    public double getRoundTimer() { throw new UnsupportedOperationException("stub"); }
    public void setRoundTimer(double t) { }
    public ChaosEventType getActiveChaosEvent() { throw new UnsupportedOperationException("stub"); }
    public void setActiveChaosEvent(ChaosEventType type) { }
    public double getChaosEventTimer() { throw new UnsupportedOperationException("stub"); }
    public void setChaosEventTimer(double t) { }
    public Map<Integer, Integer> getControlMap() { throw new UnsupportedOperationException("stub"); }
    public List<CarryState> getActiveCarries() { throw new UnsupportedOperationException("stub"); }
    public int getAliveCount() { throw new UnsupportedOperationException("stub"); }
}
