package com.identitycrisis.shared.model;

import com.identitycrisis.shared.util.Vector2D;
import java.util.Objects;


public class Player {

    private int playerId;
    private String displayName;
    private Vector2D position;
    private Vector2D velocity;
    private PlayerState state;
    private int facingDirection;
    private boolean inSafeZone;
    private int carriedByPlayerId;
    private int carryingPlayerId;
    private double stunTimer;

    public Player(int playerId, String displayName) {
        this.playerId = playerId;
        this.displayName = displayName;
        this.position = Vector2D.zero();
        this.velocity = Vector2D.zero();
        this.state             = PlayerState.ALIVE;
        this.facingDirection   = 2;
        this.inSafeZone        = false;
        this.carriedByPlayerId = -1;
        this.carryingPlayerId  = -1;
        this.stunTimer         = 0.0;
    }

    public int getPlayerId() { return playerId; }
    public void setPlayerId(int id) { this.playerId = id; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String name) { this.displayName = name; }
    public Vector2D getPosition() { return position; }
    public void setPosition(Vector2D pos) { this.position = pos; }
    public Vector2D getVelocity() { return velocity; }
    public void setVelocity(Vector2D vel) { this.velocity = vel; }
    public PlayerState getState() { return state; }
    public void setState(PlayerState state) { this.state = state; }
    public int getFacingDirection() { return facingDirection; }
    public void setFacingDirection(int dir) { this.facingDirection = dir; }
    public boolean isInSafeZone() { return inSafeZone; }
    public void setInSafeZone(boolean val) { this.inSafeZone = val; }
    public int getCarriedByPlayerId() { return carriedByPlayerId; }
    public void setCarriedByPlayerId(int id) { this.carriedByPlayerId = id; }
    public int getCarryingPlayerId() { return carryingPlayerId; }
    public void setCarryingPlayerId(int id) { this.carryingPlayerId = id; }
    public double getStunTimer() { return stunTimer; }
    public void setStunTimer(double t) { this.stunTimer = t; }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Player other)) return false;
        return playerId == other.playerId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerId);
    }
}
