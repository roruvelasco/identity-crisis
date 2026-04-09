package com.identitycrisis.client.render;

import com.identitycrisis.client.game.LocalGameState;
import javafx.scene.canvas.GraphicsContext;

/**
 * Renders safe zone(s). During fake chaos, renders ALL identically.
 * Client has no idea which is real. Pulsing/glow effect.
 */
public class SafeZoneRenderer {

    private SpriteManager spriteManager;

    public SafeZoneRenderer(SpriteManager spriteManager) { this.spriteManager = spriteManager; }

    public void render(GraphicsContext gc, LocalGameState state) { }
}
