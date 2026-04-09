package com.identitycrisis.server.game;

/**
 * Carry/throw mechanics, server-authoritative.
 * - Carry: within CARRY_RANGE, neither in existing carry.
 * - Carrier speed reduced, carried position locked to carrier + offset.
 * - Carrier CANNOT be marked safe while carrying.
 * - Throw: release with velocity in facing direction, brief stun on carried.
 */
public class CarryManager {

    private final GameState gameState;

    public CarryManager(GameState gameState) { this.gameState = gameState; }

    public boolean tryCarry(int carrierPlayerId) { throw new UnsupportedOperationException("stub"); }

    public void throwCarried(int carrierPlayerId) { }

    public void tick(double dt) { }

    /**
     * Releases any carry relationship that involves {@code playerId} — either as
     * carrier or as the carried player. Called when a client disconnects mid-carry
     * to prevent the other player from being permanently stuck.
     */
    public void releaseCarry(int playerId) { }

    private int findNearestCarryTarget(int carrierPlayerId) { throw new UnsupportedOperationException("stub"); }
}
