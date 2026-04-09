package com.identitycrisis.client.scene;

import javafx.scene.Scene;

/**
 * Main menu: Play, How to Play, Quit.
 * Text fields: player name, server IP, port.
 * Play → validate → connect → showLobby()
 */
public class MenuScene {

    private Scene scene;
    private SceneManager sceneManager;

    public MenuScene(SceneManager sceneManager) { this.sceneManager = sceneManager; }

    public Scene getScene() { throw new UnsupportedOperationException("stub"); }

    private void onPlayClicked() { }

    private void onHowToPlayClicked() { }

    private void onQuitClicked() { }
}
