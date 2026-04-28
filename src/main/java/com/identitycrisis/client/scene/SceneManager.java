package com.identitycrisis.client.scene;

import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import java.util.HashMap;
import java.util.Map;
import com.identitycrisis.client.net.GameClient;
import com.identitycrisis.client.game.LocalGameState;
import com.identitycrisis.client.input.InputManager;
import com.identitycrisis.server.EmbeddedServer;
import com.identitycrisis.shared.model.GameConfig;
import javafx.scene.Parent;
import com.identitycrisis.client.audio.AudioManager;


/** Manages transitions between scenes by swapping Stage's Scene. */
public class SceneManager {

    private Stage primaryStage;
    private GameClient gameClient;
    private LocalGameState localGameState;
    private InputManager inputManager;
    private AudioManager audioManager;


    private final Scene permanentScene;

    private final Map<String, Parent> roots = new HashMap<>();

    private EmbeddedServer embeddedServer;
    private String roomCode;
    private boolean isHost;

    private InitialLoadingScene initialLoadingScene;
    private MenuScene menuScene;
    private HowToPlayScene howToPlayScene;
    private LoadingScene loadingScene;
    private CreateOrJoinScene createOrJoinScene;
    private LobbyScene lobbyScene;
    private JoinRoomScene joinRoomScene;
    private GameArena gameArena;
    private ResultScene resultScene;
    private AboutScene aboutScene;

    public SceneManager(Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.audioManager = new AudioManager();
        this.primaryStage.setFullScreenExitHint("");

        this.primaryStage.setFullScreenExitKeyCombination(KeyCombination.NO_MATCH);
        this.primaryStage.setOnCloseRequest(e -> shutdownNetwork());

        StackPane placeholder = new StackPane();
        placeholder.setStyle("-fx-background-color: #0d0d14;");
        permanentScene = new Scene(placeholder,
                GameConfig.WINDOW_WIDTH, GameConfig.WINDOW_HEIGHT);

        try {
            var css = getClass().getResource("/styles/global.css");
            if (css != null) permanentScene.getStylesheets().add(css.toExternalForm());
        } catch (Exception ignored) {}

        permanentScene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            switch (event.getCode()) {
                case F11  -> { toggleFullscreen(); event.consume(); }
                case ESCAPE -> {
                    if (primaryStage.isFullScreen()) {
                        primaryStage.setFullScreen(false);
                        event.consume();
                    }
                }
                default -> {}
            }
        });

        primaryStage.setScene(permanentScene);

        this.initialLoadingScene = new InitialLoadingScene(this);
        this.menuScene = new MenuScene(this);
        this.howToPlayScene = new HowToPlayScene(this);
        this.loadingScene = new LoadingScene(this);
        this.createOrJoinScene = new CreateOrJoinScene(this);
        this.lobbyScene = new LobbyScene(this);
        this.joinRoomScene = new JoinRoomScene(this);
        this.gameArena = new GameArena(this);
        this.resultScene = new ResultScene(this);
        this.aboutScene = new AboutScene(this);
    }

    /** Disconnects client and stops embedded server (if host). */
    public void shutdownNetwork() {
        if (audioManager != null) {
            audioManager.stopBGM();
        }
        if (gameClient != null) {

            gameClient.disconnect();
            gameClient = null;
        }
        if (embeddedServer != null) {
            embeddedServer.stop();
            embeddedServer = null;
        }
        roomCode = null;
        isHost   = false;
    }

    public void showInitialLoading() {
        swapRoot("initialloading", initialLoadingScene::createScene);
        primaryStage.setTitle(GameConfig.WINDOW_TITLE + " - Loading");
        initialLoadingScene.onEnter();
    }

    public void showMenu() {
        swapRoot("menu", menuScene::createScene);
        primaryStage.setTitle(GameConfig.WINDOW_TITLE + " - Main Menu");
    }

    public void showLoading() {
        swapRoot("loading", loadingScene::createScene);
        primaryStage.setTitle(GameConfig.WINDOW_TITLE + " - Loading");
        loadingScene.onEnter();
    }

    public void showCreateOrJoin() {
        swapRoot("createorjoin", createOrJoinScene::createScene);
        primaryStage.setTitle(GameConfig.WINDOW_TITLE + " - Create or Join");
    }

    public void showJoinRoom() {
        swapRoot("joinroom", joinRoomScene::createScene);
        primaryStage.setTitle(GameConfig.WINDOW_TITLE + " - Join Room");
    }

    public void showGameArena() {
        swapRoot("gamearena", gameArena::createScene);
        primaryStage.setTitle(GameConfig.WINDOW_TITLE + " - Game Arena");
        gameArena.onEnter();
    }

    public void showLobby() {
        swapRoot("lobby", lobbyScene::createScene);
        primaryStage.setTitle(GameConfig.WINDOW_TITLE + " - Lobby");
        lobbyScene.onEnter();
    }

    public void showResult() {
        swapRoot("result", resultScene::createScene);
        primaryStage.setTitle(GameConfig.WINDOW_TITLE + " - Results");
    }

    public void showHowToPlay() {
        swapRoot("howtoplay", howToPlayScene::createScene);
        primaryStage.setTitle(GameConfig.WINDOW_TITLE + " - How to Play");
    }

    public void showAbout() {
        swapRoot("about", aboutScene::createScene);
        primaryStage.setTitle(GameConfig.WINDOW_TITLE + " - About");
    }

    /** Returns permanent scene attached to Stage. */
    public Scene getPermanentScene() {
        return permanentScene;
    }

    /** Swaps displayed content by replacing permanent scene's root. */
    private void swapRoot(String key, java.util.function.Supplier<Scene> creator) {
        Parent root = roots.computeIfAbsent(key, k -> {
            Scene tmp = creator.get();
            Parent r = tmp.getRoot();
            tmp.setRoot(new javafx.scene.layout.StackPane());
            return r;
        });

        if ("gamearena".equals(key) || "initialloading".equals(key) || "loading".equals(key)) {
            audioManager.stopBGM();
        } else {
            audioManager.playBGM("/audio/bgmusic.wav");
        }


        permanentScene.setRoot(root);
    }


    /** Toggle fullscreen on the primary stage. Can be triggered by F11 or the HUD button. */
    public void toggleFullscreen() {
        primaryStage.setFullScreen(!primaryStage.isFullScreen());
    }

    /** Returns {@code true} if the stage is currently in fullscreen mode. */
    public boolean isFullscreen() {
        return primaryStage.isFullScreen();
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

    public AudioManager getAudioManager() {
        return audioManager;
    }



    public EmbeddedServer getEmbeddedServer() { return embeddedServer; }
    public void setEmbeddedServer(EmbeddedServer s) { this.embeddedServer = s; }

    public String getRoomCode() { return roomCode; }
    public void setRoomCode(String code) { this.roomCode = code; }

    public boolean isHost() { return isHost; }
    public void setHost(boolean host) { this.isHost = host; }

    public MenuScene getMenuScene() {
        return menuScene;
    }

    public HowToPlayScene getHowToPlayScene() {
        return howToPlayScene;
    }

    public LoadingScene getLoadingScene() {
        return loadingScene;
    }

    public CreateOrJoinScene getCreateOrJoinScene() {
        return createOrJoinScene;
    }

    public LobbyScene getLobbyScene() {
        return lobbyScene;
    }

    public JoinRoomScene getJoinRoomScene() {
        return joinRoomScene;
    }

    public GameArena getGameArena() {
        return gameArena;
    }

    public ResultScene getResultScene() {
        return resultScene;
    }

    public AboutScene getAboutScene() {
        return aboutScene;
    }

    public InitialLoadingScene getInitialLoadingScene() {
        return initialLoadingScene;
    }
}
