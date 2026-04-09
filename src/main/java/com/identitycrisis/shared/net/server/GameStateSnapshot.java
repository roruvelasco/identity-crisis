package com.identitycrisis.shared.net.server;

import com.identitycrisis.shared.model.*;
import java.util.List;

/**
 * PER-CLIENT snapshot. Each client may receive different data
 * (e.g., different safeZone lists during FAKE_SAFE_ZONES chaos).
 */
public class GameStateSnapshot {

    private int roundNumber;
    private double timerRemaining;
    private RoundPhase phase;
    private ChaosEventType activeChaos;
    private double chaosDurationRemaining;
    private int controlledPlayerId;
    private List<Player> players;
    private List<SafeZone> safeZones; // includes decoys for this client

    public int getRoundNumber() { throw new UnsupportedOperationException("stub"); }
    public void setRoundNumber(int n) { }
    public double getTimerRemaining() { throw new UnsupportedOperationException("stub"); }
    public void setTimerRemaining(double t) { }
    public RoundPhase getPhase() { throw new UnsupportedOperationException("stub"); }
    public void setPhase(RoundPhase phase) { }
    public ChaosEventType getActiveChaos() { throw new UnsupportedOperationException("stub"); }
    public void setActiveChaos(ChaosEventType chaos) { }
    public double getChaosDurationRemaining() { throw new UnsupportedOperationException("stub"); }
    public void setChaosDurationRemaining(double d) { }
    public int getControlledPlayerId() { throw new UnsupportedOperationException("stub"); }
    public void setControlledPlayerId(int id) { }
    public List<Player> getPlayers() { throw new UnsupportedOperationException("stub"); }
    public void setPlayers(List<Player> players) { }
    public List<SafeZone> getSafeZones() { throw new UnsupportedOperationException("stub"); }
    public void setSafeZones(List<SafeZone> zones) { }
}
