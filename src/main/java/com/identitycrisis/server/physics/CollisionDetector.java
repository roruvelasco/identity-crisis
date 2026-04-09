package com.identitycrisis.server.physics;

import com.identitycrisis.server.game.GameState;

/**
 * Resolves: player vs walls (clamp), player vs obstacles (push out),
 * player vs player (soft push, no overlap).
 */
public class CollisionDetector {

    public CollisionDetector() { }

    public void resolve(GameState state) { }

    private void resolveWallCollision(/* player, arena */) { }

    private void resolvePlayerCollision(/* playerA, playerB */) { }
}
