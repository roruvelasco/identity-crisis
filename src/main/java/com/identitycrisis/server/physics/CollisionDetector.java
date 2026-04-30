package com.identitycrisis.server.physics;

import com.identitycrisis.server.game.GameState;
import com.identitycrisis.server.physics.TmxWallsParser.WallCollisionData;
import com.identitycrisis.shared.model.Arena;
import com.identitycrisis.shared.model.GameConfig;
import com.identitycrisis.shared.model.Player;
import com.identitycrisis.shared.model.PlayerState;
import com.identitycrisis.shared.util.Vector2D;

import java.util.List;

/**
 * Resolves player collisions each tick:
 * <ol>
 *   <li><b>Player vs arena boundary</b> — clamps position to [r, W-r] × [r, H-r].</li>
 *   <li><b>Player vs wall tiles</b> — circle-vs-AABB push-out using per-tile
 *       objectgroup rectangles from the TMX.  Falls back to full-tile rectangles
 *       for tiles with no authored shape.</li>
 *   <li><b>Player vs player</b> — soft push-apart to prevent overlap.</li>
 * </ol>
 *
 * <h2>Construction</h2>
 * <pre>{@code
 * // With pixel-accurate tile collision (preferred):
 * WallCollisionData wallData = TmxWallsParser.load("/sprites/map/ArenaMap.tmx");
 * CollisionDetector cd = new CollisionDetector(wallData);
 *
 * // Legacy — arena-bounds-only (no tile collision):
 * CollisionDetector cd = new CollisionDetector();
 * }</pre>
 *
 * <h2>Thread safety</h2>
 * Called exclusively from the server game-loop thread.  {@code WallCollisionData}
 * is read-only after construction, so no locking is needed.
 */
public class CollisionDetector {

    /** Wall collision data parsed from the TMX at startup. May be {@code null}. */
    private final WallCollisionData wallData;

    // ── Constructors ──────────────────────────────────────────────────────────

    /**
     * Creates a detector with full tile-aware wall collision.
     *
     * @param wallData pre-parsed TMX wall data; must not be {@code null}
     */
    public CollisionDetector(WallCollisionData wallData) {
        this.wallData = (wallData != null && !wallData.isEmpty()) ? wallData : null;
    }

    /**
     * Legacy constructor: arena-boundary clamping only (no tile collision).
     * Prefer {@link #CollisionDetector(WallCollisionData)}.
     */
    public CollisionDetector() {
        this.wallData = null;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Resolves all collisions for the current tick.
     * Must be called after {@code PhysicsEngine.step()} has advanced positions.
     */
    public void resolve(GameState state) {
        List<Player> alive = state.getAlivePlayers();
        Arena        arena = state.getArena();

        for (Player p : alive) {
            resolveWallCollision(p, arena);
        }
        for (int i = 0; i < alive.size(); i++) {
            for (int j = i + 1; j < alive.size(); j++) {
                resolvePlayerCollision(alive.get(i), alive.get(j));
            }
        }
    }

    // ── Collision resolution ───────────────────────────────────────────────────

    /**
     * Resolves a single player against arena boundaries and wall tiles.
     *
     * <p>Steps:
     * <ol>
     *   <li>Clamp to arena bounds (always applied).</li>
     *   <li>If wall data is available: push the player out of any overlapping
     *       wall tile shape using the minimum-overlap axis.</li>
     * </ol>
     */
    private void resolveWallCollision(Player p, Arena arena) {
        if (p.getState() == PlayerState.CARRIED) return; // position locked by CarryManager

        double r = GameConfig.PLAYER_RADIUS;

        // ── Step 1: Arena boundary clamp ─────────────────────────────────────
        double x = Math.max(r, Math.min(p.getPosition().x(), arena.getWidth()  - r));
        double y = Math.max(r, Math.min(p.getPosition().y(), arena.getHeight() - r));
        p.setPosition(new Vector2D(x, y));

        if (wallData == null) return;

        // ── Step 2: Tile-grid push-out ────────────────────────────────────────
        // Re-read after boundary clamp (position may have changed).
        x = p.getPosition().x();
        y = p.getPosition().y();

        // Find all tiles whose bounding box overlaps the player's AABB
        int ts      = wallData.tileSize();
        int colMin  = Math.max(0, (int) Math.floor((x - r) / ts));
        int colMax  = Math.min(wallData.worldCols() - 1, (int) Math.floor((x + r) / ts));
        int rowMin  = Math.max(0, (int) Math.floor((y - r) / ts));
        int rowMax  = Math.min(wallData.worldRows() - 1, (int) Math.floor((y + r) / ts));

        for (int row = rowMin; row <= rowMax; row++) {
            for (int col = colMin; col <= colMax; col++) {
                int gid = wallData.wallsGidGrid()[row][col];
                if (gid == 0) continue; // empty cell

                // Get collision shapes for this tile (objectgroup rects or full-tile fallback)
                List<double[]> shapes = wallData.shapesFor(gid);

                for (double[] rect : shapes) {
                    // rect = {localX, localY, w, h} in tile-local pixels
                    double rectL = col * ts + rect[0];
                    double rectT = row * ts + rect[1];
                    double rectR = rectL + rect[2];
                    double rectB = rectT + rect[3];

                    // Re-read current position (may have shifted by earlier shapes)
                    x = p.getPosition().x();
                    y = p.getPosition().y();

                    // Nearest point on rect to circle centre
                    double nearX = Math.max(rectL, Math.min(x, rectR));
                    double nearY = Math.max(rectT, Math.min(y, rectB));

                    double dx   = x - nearX;
                    double dy   = y - nearY;
                    double dist = Math.sqrt(dx * dx + dy * dy);

                    if (dist < r && dist > 0.001) {
                        // Push out along the separating axis
                        double pen = r - dist;
                        double nx  = dx / dist;
                        double ny  = dy / dist;
                        p.setPosition(new Vector2D(x + nx * pen, y + ny * pen));
                    } else if (dist < 0.001) {
                        // Circle centre is exactly on the rect edge — push upward
                        p.setPosition(new Vector2D(x, y - r));
                    }
                }
            }
        }
    }

    /** Separates two overlapping players with a soft push. */
    private void resolvePlayerCollision(Player a, Player b) {
        if (a.getState() == PlayerState.CARRIED || b.getState() == PlayerState.CARRIED) return;

        double minDist = GameConfig.PLAYER_RADIUS * 2;
        double dist    = a.getPosition().distanceTo(b.getPosition());

        if (dist < minDist && dist > 0.001) {
            Vector2D dir     = b.getPosition().subtract(a.getPosition()).normalize();
            double   overlap = (minDist - dist) / 2.0;
            a.setPosition(a.getPosition().subtract(dir.multiply(overlap)));
            b.setPosition(b.getPosition().add(dir.multiply(overlap)));
        }
    }
}
