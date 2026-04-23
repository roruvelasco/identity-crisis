package com.identitycrisis.client.scene;

import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.input.KeyCombination;
import java.util.HashMap;
import java.util.Map;
import com.identitycrisis.client.net.GameClient;
import com.identitycrisis.client.game.LocalGameState;
import com.identitycrisis.client.input.InputManager;
import com.identitycrisis.server.EmbeddedServer;
import com.identitycrisis.shared.model.GameConfig;

/** Manages transitions between scenes by swapping Stage's Scene. */
public class SceneManager {

    private Stage primaryStage;
    private GameClient gameClient;
    private LocalGameState localGameState;
    private InputManager inputManager;

    // Room-code / host-lifecycle state (populated by CreateOrJoinScene "Create Game"
    // or JoinRoomScene "Join"; consumed by LobbyScene to display the code).
    private EmbeddedServer embeddedServer; // non-null only when this client is the host
    private String roomCode;
    private boolean isHost;

    // Scene cache
    private Map<String, Scene> scenes = new HashMap<>();

    // Scene controllers
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
        this.primaryStage.setFullScreenExitHint("");
        this.primaryStage.setFullScreenExitKeyCombination(KeyCombination.NO_MATCH);

        // Release network resources on window close so the EmbeddedServer daemon
        // thread and client socket don't linger after the FX app exits.
        this.primaryStage.setOnCloseRequest(e -> shutdownNetwork());

        // Initialize scene controllers
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

    /**
     * Disconnects the client and (if host) stops the embedded server. Idempotent.
     * Called from the window-close hook and from "Back" navigations that leave
     * a room.
     */
    public void shutdownNetwork() {
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
        Scene scene = scenes.computeIfAbsent("initialloading", k -> initialLoadingScene.createScene());
        setupFullscreenHandler(scene);
        primaryStage.setScene(scene);
        primaryStage.setTitle(GameConfig.WINDOW_TITLE + " - Loading");
        initialLoadingScene.onEnter();
    }

    public void showMenu() {
        Scene scene = scenes.computeIfAbsent("menu", k -> menuScene.createScene());
        setupFullscreenHandler(scene);
        primaryStage.setScene(scene);
        primaryStage.setTitle(GameConfig.WINDOW_TITLE + " - Main Menu");
    }

    public void showLoading() {
        Scene scene = scenes.computeIfAbsent("loading", k -> loadingScene.createScene());
        setupFullscreenHandler(scene);
        primaryStage.setScene(scene);
        primaryStage.setTitle(GameConfig.WINDOW_TITLE + " - Loading");
        loadingScene.onEnter();
    }

    public void showCreateOrJoin() {
        Scene scene = scenes.computeIfAbsent("createorjoin", k -> createOrJoinScene.createScene());
        setupFullscreenHandler(scene);
        primaryStage.setScene(scene);
        primaryStage.setTitle(GameConfig.WINDOW_TITLE + " - Create or Join");
    }

    public void showJoinRoom() {
        Scene scene = scenes.computeIfAbsent("joinroom", k -> joinRoomScene.createScene());
        setupFullscreenHandler(scene);
        primaryStage.setScene(scene);
        primaryStage.setTitle(GameConfig.WINDOW_TITLE + " - Join Room");
    }

    public void showGameArena() {
        Scene scene = scenes.computeIfAbsent("gamearena", k -> gameArena.createScene());
        setupFullscreenHandler(scene);
        primaryStage.setScene(scene);
        primaryStage.setTitle(GameConfig.WINDOW_TITLE + " - Game Arena");
        gameArena.onEnter();
    }

    public void showLobby() {
        Scene scene = scenes.computeIfAbsent("lobby", k -> lobbyScene.createScene());
        setupFullscreenHandler(scene);
        primaryStage.setScene(scene);
        primaryStage.setTitle(GameConfig.WINDOW_TITLE + " - Lobby");
        lobbyScene.onEnter();
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

    // ── Room / host lifecycle ──────────────────────────────────────────────

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
