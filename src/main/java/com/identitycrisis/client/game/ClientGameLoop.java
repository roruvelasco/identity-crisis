package com.identitycrisis.client.game;

import com.identitycrisis.client.input.*;
import com.identitycrisis.client.net.GameClient;
import com.identitycrisis.client.render.Renderer;
import javafx.animation.AnimationTimer;

/**
 * Client-side loop via AnimationTimer (~60fps).
 * Each frame: poll input → apply chaos mods → send to server → render.
 * No client-side prediction. Pure server-authoritative.
 */
public class ClientGameLoop extends AnimationTimer {

    private final InputManager inputManager;
    private final GameClient gameClient;
    private final LocalGameState localGameState;
    private final Renderer renderer;
    private long lastFrameTime;

    public ClientGameLoop(InputManager inputManager, GameClient gameClient,
                          LocalGameState localGameState, Renderer renderer) {
        this.inputManager = inputManager;
        this.gameClient = gameClient;
        this.localGameState = localGameState;
        this.renderer = renderer;
    }

    @Override
    public void handle(long now) {
        // double dt = (now - lastFrameTime) / 1_000_000_000.0
        // InputSnapshot input = inputManager.snapshot()
        // InputSnapshot modified = applyChaosModifications(input)
        // gameClient.sendInput(...)
        // renderer.render(localGameState, dt)
        // lastFrameTime = now
    }

    /** If REVERSED_CONTROLS active, invert movement keys. */
    private InputSnapshot applyChaosModifications(InputSnapshot raw) {
        throw new UnsupportedOperationException("stub");
    }
}
