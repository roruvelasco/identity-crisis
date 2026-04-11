package com.identitycrisis.client.scene;

import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.input.KeyCombination;
import java.util.HashMap;
import java.util.Map;
import com.identitycrisis.client.net.GameClient;
import com.identitycrisis.client.game.LocalGameState;
import com.identitycrisis.client.input.InputManager;
import com.identitycrisis.shared.model.GameConfig;

/** Manages transitions between scenes by swapping Stage's Scene. */
public class SceneManager {

    private Stage primaryStage;
    private GameClient gameClient;
    private LocalGameState localGameState;
    private InputManager inputManager;

    // Scene cache
    private Map<String, Scene> scenes = new HashMap<>();

    // Scene controllers
    private MenuScene menuScene;
    private HowToPlayScene howToPlayScene;
    private LobbyScene lobbyScene;
    private GameScene gameScene;
    private ResultScene resultScene;
    private AboutScene aboutScene;

    public SceneManager(Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.primaryStage.setFullScreenExitHint("");
        this.primaryStage.setFullScreenExitKeyCombination(KeyCombination.NO_MATCH);

        // Initialize scene controllers
        this.menuScene = new MenuScene(this);
        this.howToPlayScene = new HowToPlayScene(this);
        this.lobbyScene = new LobbyScene(this);
        this.gameScene = new GameScene(this);
        this.resultScene = new ResultScene(this);
        this.aboutScene = new AboutScene(this);
    }

    public void showMenu() {
        Scene scene = scenes.computeIfAbsent("menu", k -> menuScene.createScene());
        setupFullscreenHandler(scene);
        primaryStage.setScene(scene);
        primaryStage.setTitle(GameConfig.WINDOW_TITLE + " - Main Menu");
    }

    public void showLobby() {
        Scene scene = scenes.computeIfAbsent("lobby", k -> lobbyScene.createScene());
        setupFullscreenHandler(scene);
        primaryStage.setScene(scene);
        primaryStage.setTitle(GameConfig.WINDOW_TITLE + " - Lobby");
        lobbyScene.onEnter();
    }

    public void showGame() {
        Scene scene = scenes.computeIfAbsent("game", k -> gameScene.createScene());
        setupFullscreenHandler(scene);
        primaryStage.setScene(scene);
        primaryStage.setTitle(GameConfig.WINDOW_TITLE + " - Game");
        gameScene.onEnter();
    }

    public void showResult() {
        Scene scene = scenes.computeIfAbsent("result", k -> resultScene.createScene());
        setupFullscreenHandler(scene);
        primaryStage.setScene(scene);
        primaryStage.setTitle(GameConfig.WINDOW_TITLE + " - Results");
    }

    public void showHowToPlay() {
        Scene scene = scenes.computeIfAbsent("howtoplay", k -> howToPlayScene.createScene());
        setupFullscreenHandler(scene);
        primaryStage.setScene(scene);
        primaryStage.setTitle(GameConfig.WINDOW_TITLE + " - How to Play");
    }

    public void showAbout() {
        Scene scene = scenes.computeIfAbsent("about", k -> aboutScene.createScene());
        setupFullscreenHandler(scene);
        primaryStage.setScene(scene);
        primaryStage.setTitle(GameConfig.WINDOW_TITLE + " - About");
    }

    /**
     * Toggle fullscreen mode on the primary stage.
     * Can be triggered by F11 key or fullscreen button.
     */
    public void toggleFullscreen() {
        boolean isFullScreen = !primaryStage.isFullScreen();
        primaryStage.setFullScreen(isFullScreen);
    }

    /**
     * Check if the stage is currently in fullscreen mode.
     */
    public boolean isFullscreen() {
        return primaryStage.isFullScreen();
    }

    /**
     * Set up F11 key handler for fullscreen toggle on a scene.
     */
    private void setupFullscreenHandler(Scene scene) {
        scene.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case F11:
                    toggleFullscreen();
                    event.consume();
                    break;
                case ESCAPE:
                    // Only exit fullscreen, don't propagate
                    if (primaryStage.isFullScreen()) {
                        primaryStage.setFullScreen(false);
                        event.consume();
                    }
                    break;
                default:
                    break;
            }
        });
    }

    public Stage getStage() {
        return primaryStage;
    }

    public GameClient getGameClient() {
        return gameClient;
    }

    public void setGameClient(GameClient gameClient) {
        this.gameClient = gameClient;
    }

    public LocalGameState getLocalGameState() {
        return localGameState;
    }

    public void setLocalGameState(LocalGameState localGameState) {
        this.localGameState = localGameState;
    }

    public InputManager getInputManager() {
        return inputManager;
    }

    public void setInputManager(InputManager inputManager) {
        this.inputManager = inputManager;
    }

    public MenuScene getMenuScene() {
        return menuScene;
    }

    public HowToPlayScene getHowToPlayScene() {
        return howToPlayScene;
    }

    public LobbyScene getLobbyScene() {
        return lobbyScene;
    }

    public GameScene getGameScene() {
        return gameScene;
    }

    public ResultScene getResultScene() {
        return resultScene;
    }

    public AboutScene getAboutScene() {
        return aboutScene;
    }
}
