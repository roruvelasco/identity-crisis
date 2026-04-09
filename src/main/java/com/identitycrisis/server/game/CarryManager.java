package com.identitycrisis.server.game;

/**
 * Carry/throw mechanics, server-authoritative.
 * - Carry: within CARRY_RANGE, neither in existing carry.
 * - Carrier speed reduced, carried position locked to carrier + offset.
 * - Carrier CANNOT be marked safe while carrying.
 * - Throw: release with velocity in facing direction, brief stun on carried.
 */
public class CarryManager {

    private GameState gameState;

    public CarryManager(GameState gameState) { this.gameState = gameState; }

    public boolean tryCarry(int carrierPlayerId) { throw new UnsupportedOperationException("stub"); }

    public void throwCarried(int carrierPlayerId) { }

    public void tick(double dt) { }

    private int findNearestCarryTarget(int carrierPlayerId) { throw new UnsupportedOperationException("stub"); }
}
