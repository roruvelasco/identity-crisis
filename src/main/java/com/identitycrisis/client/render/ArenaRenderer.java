package com.identitycrisis.client.render;

import com.identitycrisis.client.game.LocalGameState;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * Renders the arena background: dark fill → subtle grid → gold border.
 * Called each frame before players/HUD are drawn.
 */
public class ArenaRenderer {

    private static final Color BG_COLOR     = Color.web("#0d0d14");
    private static final Color GRID_COLOR   = Color.rgb(201, 168, 76, 0.06);
    private static final Color BORDER_COLOR = Color.web("#c9a84c");
    private static final double BORDER_WIDTH = 3.0;
    private static final double GRID_SIZE    = 64.0;

    @SuppressWarnings("unused")
    private final SpriteManager spriteManager;

    public ArenaRenderer(SpriteManager spriteManager) {
        this.spriteManager = spriteManager;
    }

    /**
     * Renders arena background, grid, and border into the full canvas.
     *
     * @param gc     GraphicsContext of the game canvas
     * @param width  current canvas width (supports fullscreen)
     * @param height current canvas height
     */
    public void render(GraphicsContext gc, double width, double height) {
        // ── 1. Dark background ───────────────────────────────────────────────
        gc.setFill(BG_COLOR);
        gc.fillRect(0, 0, width, height);

        // ── 2. Subtle grid ───────────────────────────────────────────────────
        gc.setStroke(GRID_COLOR);
        gc.setLineWidth(1.0);
        for (double x = 0; x <= width; x += GRID_SIZE) {
            gc.strokeLine(x, 0, x, height);
        }
        for (double y = 0; y <= height; y += GRID_SIZE) {
            gc.strokeLine(0, y, width, y);
        }

        // ── 3. Gold border ───────────────────────────────────────────────────
        gc.setStroke(BORDER_COLOR);
        gc.setLineWidth(BORDER_WIDTH);
        double half = BORDER_WIDTH / 2.0;
        gc.strokeRect(half, half, width - BORDER_WIDTH, height - BORDER_WIDTH);
    }

    /**
     * Overload consumed by the master {@link Renderer}.
     * State-based render will be wired once LocalGameState is populated by
     * the server; until then the no-arg width/height overload is used by GameArena.
     */
    public void render(GraphicsContext gc, LocalGameState state) {
        // no-op stub — GameArena calls render(gc, w, h) directly.
    }
}
