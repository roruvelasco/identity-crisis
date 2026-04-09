package com.identitycrisis.server.physics;

import com.identitycrisis.server.game.GameState;
import com.identitycrisis.shared.model.GameConfig;
import com.identitycrisis.shared.model.Player;
import com.identitycrisis.shared.model.PlayerState;
import com.identitycrisis.shared.util.Vector2D;

/**
 * Movement: Euler integration. position += velocity * dt.
 * Applies throw velocity decay and stun timers.
 */
public class PhysicsEngine {

    public PhysicsEngine() { }

    public void step(GameState state, double dt) {
        for (Player p : state.getAlivePlayers()) {
            if (p.getState() == PlayerState.CARRIED) continue;
            if (p.getStunTimer() > 0) {
                p.setStunTimer(Math.max(0, p.getStunTimer() - dt));
            }
            Vector2D pos = p.getPosition().add(p.getVelocity().multiply(dt));
            p.setPosition(pos);
            if (p.getVelocity().magnitude() > GameConfig.VELOCITY_STOP_THRESHOLD) {
                p.setVelocity(p.getVelocity().multiply(GameConfig.VELOCITY_DAMPING));
            } else {
                p.setVelocity(Vector2D.zero());
            }
        }
    }

    public void applyInput(GameState state, int playerId,
                           boolean up, boolean down, boolean left, boolean right,
                           boolean reversedControls) {
        Player p = state.getPlayerById(playerId);
        if (p == null || p.getState() == PlayerState.ELIMINATED
                      || p.getState() == PlayerState.SPECTATING
                      || p.getState() == PlayerState.CARRIED
                      || p.getStunTimer() > 0) return;

        double speed = GameConfig.PLAYER_SPEED;
        if (p.getState() == PlayerState.CARRYING) speed *= 0.6;

        boolean u = reversedControls ? down  : up;
        boolean d = reversedControls ? up    : down;
        boolean l = reversedControls ? right : left;
        boolean r = reversedControls ? left  : right;

        double vx = 0, vy = 0;
        if (u) vy -= 1;
        if (d) vy += 1;
        if (l) vx -= 1;
        if (r) vx += 1;

        double mag = Math.sqrt(vx * vx + vy * vy);
        if (mag > 0) {
            vx = (vx / mag) * speed;
            vy = (vy / mag) * speed;
        }

        p.setVelocity(new Vector2D(vx, vy));

        if      (vy < 0) p.setFacingDirection(0);
        else if (vx > 0) p.setFacingDirection(1);
        else if (vy > 0) p.setFacingDirection(2);
        else if (vx < 0) p.setFacingDirection(3);
    }
}
