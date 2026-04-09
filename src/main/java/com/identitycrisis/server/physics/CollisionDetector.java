package com.identitycrisis.server.physics;

import com.identitycrisis.server.game.GameState;
import com.identitycrisis.shared.model.Arena;
import com.identitycrisis.shared.model.GameConfig;
import com.identitycrisis.shared.model.Player;
import com.identitycrisis.shared.util.Vector2D;
import java.util.List;

/**
 * Resolves: player vs walls (clamp), player vs obstacles (push out),
 * player vs player (soft push, no overlap).
 */
public class CollisionDetector {

    public CollisionDetector() { }

    public void resolve(GameState state) {
        List<Player> alive = state.getAlivePlayers();
        Arena arena = state.getArena();
        for (Player p : alive) {
            resolveWallCollision(p, arena);
        }
        for (int i = 0; i < alive.size(); i++) {
            for (int j = i + 1; j < alive.size(); j++) {
                resolvePlayerCollision(alive.get(i), alive.get(j));
            }
        }
    }

    private void resolveWallCollision(Player p, Arena arena) {
        double r = GameConfig.PLAYER_RADIUS;
        double x = Math.max(r, Math.min(p.getPosition().x(), arena.getWidth() - r));
        double y = Math.max(r, Math.min(p.getPosition().y(), arena.getHeight() - r));
        p.setPosition(new Vector2D(x, y));
    }

    private void resolvePlayerCollision(Player a, Player b) {
        double minDist = GameConfig.PLAYER_RADIUS * 2;
        double dist = a.getPosition().distanceTo(b.getPosition());
        if (dist < minDist && dist > 0.001) {
            Vector2D dir = b.getPosition().subtract(a.getPosition()).normalize();
            double overlap = (minDist - dist) / 2.0;
            a.setPosition(a.getPosition().subtract(dir.multiply(overlap)));
            b.setPosition(b.getPosition().add(dir.multiply(overlap)));
        }
    }
}
