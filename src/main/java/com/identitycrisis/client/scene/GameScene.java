package com.identitycrisis.client.scene;

import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;

/** Core gameplay view. Canvas + InputManager + ClientGameLoop. */
public class GameScene {

    private Scene scene;
    private Canvas canvas;
    private SceneManager sceneManager;

    public GameScene(SceneManager sceneManager) { this.sceneManager = sceneManager; }

    public Scene getScene() { throw new UnsupportedOperationException("stub"); }

    /** Attach input, start ClientGameLoop. */
    public void onEnter() { }

    /** Stop loop, detach input. */
    public void onExit() { }
}
