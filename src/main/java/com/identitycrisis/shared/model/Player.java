package com.identitycrisis.shared.model;

import com.identitycrisis.shared.util.Vector2D;

/**
 * Shared player data transferred over the network.
 * Server holds authoritative copy; client holds rendered copy.
 */
public class Player {

    private int playerId;
    private String displayName;
    private Vector2D position;
    private Vector2D velocity;
    private PlayerState state;
    private int facingDirection;        // 0=up, 1=right, 2=down, 3=left
    private boolean inSafeZone;
    private int carriedByPlayerId;      // -1 if not carried
    private int carryingPlayerId;       // -1 if not carrying

    public int getPlayerId() { throw new UnsupportedOperationException("stub"); }
    public void setPlayerId(int id) { }
    public String getDisplayName() { throw new UnsupportedOperationException("stub"); }
    public void setDisplayName(String name) { }
    public Vector2D getPosition() { throw new UnsupportedOperationException("stub"); }
    public void setPosition(Vector2D pos) { }
    public Vector2D getVelocity() { throw new UnsupportedOperationException("stub"); }
    public void setVelocity(Vector2D vel) { }
    public PlayerState getState() { throw new UnsupportedOperationException("stub"); }
    public void setState(PlayerState state) { }
    public int getFacingDirection() { throw new UnsupportedOperationException("stub"); }
    public void setFacingDirection(int dir) { }
    public boolean isInSafeZone() { throw new UnsupportedOperationException("stub"); }
    public void setInSafeZone(boolean val) { }
    public int getCarriedByPlayerId() { throw new UnsupportedOperationException("stub"); }
    public void setCarriedByPlayerId(int id) { }
    public int getCarryingPlayerId() { throw new UnsupportedOperationException("stub"); }
    public void setCarryingPlayerId(int id) { }
}
