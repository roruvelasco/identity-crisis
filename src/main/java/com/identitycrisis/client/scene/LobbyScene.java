package com.identitycrisis.client.scene;

import javafx.scene.Scene;

/**
 * Waiting room. Shows connected players, ready status, ready button.
 * Transitions to GameScene when server signals game start.
 */
public class LobbyScene {

    private Scene scene;
    private SceneManager sceneManager;

    public LobbyScene(SceneManager sceneManager) { this.sceneManager = sceneManager; }

    public Scene getScene() { throw new UnsupportedOperationException("stub"); }

    private void onReadyClicked() { }

    public void refreshLobbyDisplay() { }
}
