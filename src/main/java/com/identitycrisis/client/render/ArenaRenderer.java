package com.identitycrisis.client.render;

import com.identitycrisis.client.game.LocalGameState;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/** Renders game arena with TMX map or fallback grid. */
public class ArenaRenderer {

    private static final Color BG_COLOR     = Color.web("#0d0d14");
    private static final Color GRID_COLOR   = Color.rgb(201, 168, 76, 0.06);
    private static final Color BORDER_COLOR = Color.web("#c9a84c");
    private static final double BORDER_WIDTH = 3.0;
    private static final double GRID_SIZE    = 64.0;

    @SuppressWarnings("unused")
    private final SpriteManager spriteManager;

    /** Handles TMX parsing and tile rendering. May be null if load fails. */
    private final MapManager mapManager;


    public ArenaRenderer(SpriteManager spriteManager) {
        this.spriteManager = spriteManager;
        MapManager mm = new MapManager();
        mm.load("/sprites/map/ArenaMap.tmx");
        this.mapManager = mm;
    }


    /** Renders arena to canvas, scaling map to fit viewport. */
    public void render(GraphicsContext gc, double width, double height) {
        gc.setFill(BG_COLOR);
        gc.fillRect(0, 0, width, height);

        if (mapManager != null && mapManager.getWorldWidth() > 0) {
            mapManager.render(gc, width, height);
        } else {
            renderPlaceholder(gc, width, height);
        }
    }

    /** Returns MapManager for collision queries and coordinate conversion. */
    public MapManager getMapManager() { return mapManager; }

    /** No-op stub for state-based render overload. */
    public void render(GraphicsContext gc, LocalGameState state) {
    }


    /** Renders dark background with gold grid and border. */
    private void renderPlaceholder(GraphicsContext gc, double width, double height) {
        gc.setStroke(GRID_COLOR);
        gc.setLineWidth(1.0);
        for (double x = 0; x <= width; x += GRID_SIZE)  gc.strokeLine(x, 0, x, height);
        for (double y = 0; y <= height; y += GRID_SIZE) gc.strokeLine(0, y, width, y);

        gc.setStroke(BORDER_COLOR);
        gc.setLineWidth(BORDER_WIDTH);
        double half = BORDER_WIDTH / 2.0;
        gc.strokeRect(half, half, width - BORDER_WIDTH, height - BORDER_WIDTH);
    }
}
