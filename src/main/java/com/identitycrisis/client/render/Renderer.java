package com.identitycrisis.client.render;

import com.identitycrisis.client.game.LocalGameState;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;

/**
 * Master renderer. Loads sprites in order so everything shows properly:
 * Arena -> SafeZone -> Players -> HUD -> Chat
 */
public class Renderer {

    private final Canvas canvas;
    private final GraphicsContext gc;
    private final ArenaRenderer arenaRenderer;
    private final PlayerRenderer playerRenderer;
    private final SafeZoneRenderer safeZoneRenderer;
    private final HudRenderer hudRenderer;
    private final ChatRenderer chatRenderer;
    private final SpriteManager spriteManager;

    public Renderer(Canvas canvas, SpriteManager spriteManager) {
        this.canvas = canvas;
        this.gc = canvas.getGraphicsContext2D();
        this.spriteManager = spriteManager;
        this.arenaRenderer = new ArenaRenderer(spriteManager);
        this.playerRenderer = new PlayerRenderer(spriteManager);
        this.safeZoneRenderer = new SafeZoneRenderer(spriteManager);
        this.hudRenderer = new HudRenderer();
        this.chatRenderer = new ChatRenderer();
    }

    public void render(LocalGameState state, double dt) {
        // gc.clearRect(...)
        // arenaRenderer.render(gc, state)
        // safeZoneRenderer.render(gc, state)
        // playerRenderer.render(gc, state, dt)
        // hudRenderer.render(gc, state)
        // chatRenderer.render(gc, state)
    }
}
