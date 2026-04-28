package com.identitycrisis.client.render;

import com.identitycrisis.client.game.LocalGameState;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * Renders the game arena.
 *
 * When TMX map loads, all visual layers (floor, shadow,
 * water, walls, objects) are rendered via {@link MapManager}
 * that always fits the full map within the current view.
 *
 * uses dark grid placeholder if the map fails to load
 */
public class ArenaRenderer {

    // ── Placeholder colours (fallback only) ─────────────────────────────────
    private static final Color BG_COLOR = Color.web("#0d0d14");
    private static final Color GRID_COLOR = Color.rgb(201, 168, 76, 0.06);
    private static final Color BORDER_COLOR = Color.web("#c9a84c");
    private static final double BORDER_WIDTH = 3.0;
    private static final double GRID_SIZE = 64.0;

    @SuppressWarnings("unused")
    private final SpriteManager spriteManager;

    /** Handles TMX parsing and tile rendering. May be null if load fails. */
    private final MapManager mapManager;

    // ── Constructor ──────────────────────────────────────────────────────────

    public ArenaRenderer(SpriteManager spriteManager) {
        this.spriteManager = spriteManager;
        MapManager mm = new MapManager();
        mm.load("/sprites/map/ArenaMap.tmx");
        // Keep the reference even if load logged errors — isSolid/render guard
        // internally
        this.mapManager = mm;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Renders the arena into the full canvas.
     * Scales the TMX map to fit {@code width × height}; centres if aspect ratios
     * differ.
     *
     * @param gc     GraphicsContext of the game canvas
     * @param width  current canvas width (supports fullscreen)
     * @param height current canvas height
     */
    public void render(GraphicsContext gc, double width, double height) {
        // Always paint the background first (covers letterbox bars)
        gc.setFill(BG_COLOR);
        gc.fillRect(0, 0, width, height);

        if (mapManager != null && mapManager.getWorldWidth() > 0) {
            mapManager.render(gc, width, height);
        } else {
            renderPlaceholder(gc, width, height);
        }
    }

    /**
     * Returns the {@link MapManager} so callers can query collision and
     * safe zones, or convert between world and screen coordinates.
     */
    public MapManager getMapManager() {
        return mapManager;
    }

    /**
     * Overload consumed by the master {@link Renderer}.
     * Delegates to the state-independent overload; state-based integration
     * will be wired when the full network pipeline is complete.
     */
    public void render(GraphicsContext gc, LocalGameState state) {
        // no-op stub — active render path uses render(gc, w, h) directly.
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Original placeholder: dark bg + subtle gold grid + border. */
    private void renderPlaceholder(GraphicsContext gc, double width, double height) {
        gc.setStroke(GRID_COLOR);
        gc.setLineWidth(1.0);
        for (double x = 0; x <= width; x += GRID_SIZE)
            gc.strokeLine(x, 0, x, height);
        for (double y = 0; y <= height; y += GRID_SIZE)
            gc.strokeLine(0, y, width, y);

        gc.setStroke(BORDER_COLOR);
        gc.setLineWidth(BORDER_WIDTH);
        double half = BORDER_WIDTH / 2.0;
        gc.strokeRect(half, half, width - BORDER_WIDTH, height - BORDER_WIDTH);
    }
}
