package com.identitycrisis.client.render;

import com.identitycrisis.client.game.LocalGameState;
import javafx.scene.canvas.GraphicsContext;

/** Renders player sprites with facing, animation, and state visuals. */
public class PlayerRenderer {

    private SpriteManager spriteManager;
    private double animationTimer;

    public PlayerRenderer(SpriteManager spriteManager) { this.spriteManager = spriteManager; }

    public void render(GraphicsContext gc, LocalGameState state, double dt) { }

    private void drawPlayer(GraphicsContext gc /* , Player data */) { }
}
