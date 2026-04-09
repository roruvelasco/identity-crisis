package com.identitycrisis.server.physics;

import com.identitycrisis.server.game.GameState;

/**
 * Movement: Euler integration. position += velocity * dt.
 * Applies throw velocity decay and stun timers.
 */
public class PhysicsEngine {

    public PhysicsEngine() { }

    public void step(GameState state, double dt) { }

    public void applyInput(GameState state, int playerId,
                           boolean up, boolean down, boolean left, boolean right,
                           boolean reversedControls) { }
}
