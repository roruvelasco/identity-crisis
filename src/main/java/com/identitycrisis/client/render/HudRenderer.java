package com.identitycrisis.client.render;

import com.identitycrisis.client.game.LocalGameState;
import javafx.scene.canvas.GraphicsContext;

/**
 * HUD: round number, countdown timer, players remaining,
 * chaos event toast, elimination toast, YOU WIN / YOU WERE ELIMINATED overlay.
 */
public class HudRenderer {

    public HudRenderer() { }

    public void render(GraphicsContext gc, LocalGameState state) { }

    private void drawToast(GraphicsContext gc, String message, double x, double y) { }
}
