package com.identitycrisis.server.physics;

import com.identitycrisis.server.game.GameState;
import com.identitycrisis.server.physics.TmxWallsParser.WallCollisionData;
import com.identitycrisis.shared.model.Arena;
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
 *   <li><b>Player vs player</b> — intentionally non-blocking.</li>
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
    private static final double HIT_HALF_W = 3.0;
    private static final double HIT_HALF_H = 5.0;
    private static final double HIT_OFS_X = 0.0;
    private static final double HIT_OFS_Y = 4.0;

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

        double halfW = HIT_HALF_W;
        double halfH = HIT_HALF_H;

        // ── Step 1: Arena boundary clamp ─────────────────────────────────────
        double worldW = wallData != null ? wallData.worldCols() * wallData.tileSize() : arena.getWidth();
        double worldH = wallData != null ? wallData.worldRows() * wallData.tileSize() : arena.getHeight();
        double x = Math.max(halfW - HIT_OFS_X, Math.min(p.getPosition().x(), worldW - halfW - HIT_OFS_X));
        double y = Math.max(halfH - HIT_OFS_Y, Math.min(p.getPosition().y(), worldH - halfH - HIT_OFS_Y));
        p.setPosition(new Vector2D(x, y));

        if (wallData == null) return;

        // ── Step 2: Tile-grid push-out ────────────────────────────────────────
        // Re-read after boundary clamp (position may have changed).
        x = p.getPosition().x();
        y = p.getPosition().y();

        int ts      = wallData.tileSize();
        int colMin  = Math.max(0, (int) Math.floor((x + HIT_OFS_X - halfW) / ts));
        int colMax  = Math.min(wallData.worldCols() - 1, (int) Math.floor((x + HIT_OFS_X + halfW) / ts));
        int rowMin  = Math.max(0, (int) Math.floor((y + HIT_OFS_Y - halfH) / ts));
        int rowMax  = Math.min(wallData.worldRows() - 1, (int) Math.floor((y + HIT_OFS_Y + halfH) / ts));

        for (int row = rowMin; row <= rowMax; row++) {
            for (int col = colMin; col <= colMax; col++) {
                if (!wallData.solidGrid()[row][col]) continue; // empty walkable cell
                List<double[]> shapes = List.of(new double[] { 0, 0, ts, ts });

                for (double[] rect : shapes) {
                    // rect = {localX, localY, w, h} in tile-local pixels
                    double rectL = col * ts + rect[0];
                    double rectT = row * ts + rect[1];
                    double rectR = rectL + rect[2];
                    double rectB = rectT + rect[3];

                    x = p.getPosition().x();
                    y = p.getPosition().y();
                    double hitL = x + HIT_OFS_X - halfW;
                    double hitT = y + HIT_OFS_Y - halfH;
                    double hitR = x + HIT_OFS_X + halfW;
                    double hitB = y + HIT_OFS_Y + halfH;

                    if (hitL < rectR && hitR > rectL && hitT < rectB && hitB > rectT) {
                        double pushLeft = rectR - hitL;
                        double pushRight = hitR - rectL;
                        double pushUp = rectB - hitT;
                        double pushDown = hitB - rectT;
                        double min = Math.min(Math.min(pushLeft, pushRight), Math.min(pushUp, pushDown));

                        if (min == pushLeft) {
                            p.setPosition(new Vector2D(x + pushLeft, y));
                        } else if (min == pushRight) {
                            p.setPosition(new Vector2D(x - pushRight, y));
                        } else if (min == pushUp) {
                            p.setPosition(new Vector2D(x, y + pushUp));
                        } else {
                            p.setPosition(new Vector2D(x, y - pushDown));
                        }
                    }
                }
            }
        }
    }

}
