package com.identitycrisis.client.render;

import com.identitycrisis.client.game.LocalGameState;
import javafx.scene.canvas.GraphicsContext;

/** Renders static map: background tiles, walls, obstacles. */
public class ArenaRenderer {

    private SpriteManager spriteManager;

    public ArenaRenderer(SpriteManager spriteManager) { this.spriteManager = spriteManager; }

    public void render(GraphicsContext gc, LocalGameState state) { }
}
