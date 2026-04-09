package com.identitycrisis.client.scene;

import javafx.stage.Stage;
import com.identitycrisis.client.net.GameClient;
import com.identitycrisis.client.game.LocalGameState;
import com.identitycrisis.client.input.InputManager;

/** Manages transitions between scenes by swapping Stage's Scene. */
public class SceneManager {

    private Stage primaryStage;
    private GameClient gameClient;
    private LocalGameState localGameState;
    private InputManager inputManager;

    public SceneManager(Stage primaryStage) { this.primaryStage = primaryStage; }

    public void showMenu() { }

    public void showLobby() { }

    public void showGame() { }

    public void showResult() { }

    public void showHowToPlay() { }

    public Stage getStage() { throw new UnsupportedOperationException("stub"); }

    public GameClient getGameClient() { throw new UnsupportedOperationException("stub"); }

    public LocalGameState getLocalGameState() { throw new UnsupportedOperationException("stub"); }

    public InputManager getInputManager() { throw new UnsupportedOperationException("stub"); }
}
