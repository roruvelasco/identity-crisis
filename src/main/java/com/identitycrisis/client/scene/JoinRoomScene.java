package com.identitycrisis.client.scene;

import javafx.scene.Scene;
import javafx.scene.layout.*;
import javafx.scene.control.*;
import javafx.geometry.*;
import javafx.animation.*;
import javafx.util.Duration;
import java.io.IOException;
import java.util.List;
import com.identitycrisis.client.game.LocalGameState;
import com.identitycrisis.client.net.GameClient;
import com.identitycrisis.client.net.ServerMessageRouter;
import com.identitycrisis.shared.model.GameConfig;
import com.identitycrisis.shared.util.Logger;
import com.identitycrisis.shared.util.RoomCodec;

/**
 * Join Room scene - allows players to enter a room code to join an existing
 * game.
 * Part of the Play -> CreateOrJoin -> Lobby -> Loading -> GameArena flow.
 * Features LobbyScene-style corner glow effects and Press Start 2P typography.
 */
public class JoinRoomScene {

    private static final Logger LOG = new Logger("JoinRoomScene");

    private Scene scene;
    private SceneManager sceneManager;

    // Color constants matching the game's dark aesthetic
    private static final String GOLD = "#c9a84c";
    private static final String GOLD_LIGHT = "#e8c86a";
    private static final String GOLD_DARK = "#8a6a1a";
    private static final String STONE_DARK = "#0d0d10";
    private static final String STONE_PANEL = "#1c1c26";
    private static final String STONE_BORDER = "#2a2a36";
    private static final String TEXT_PARCHMENT = "#e8dfc4";
    private static final String TEXT_MUTED = "#7a7060";
    private static final String DANGER_RED = "#e87d7d";

    private TextField roomCodeInput;
    private TextField nameInput;
    private Label statusLabel; // inline error feedback

    public JoinRoomScene(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
    }

    public Scene createScene() {
        StackPane root = new StackPane();
        root.setStyle("-fx-background-color: " + STONE_DARK + ";");
        root.setAlignment(Pos.CENTER);

        // Dark stone background pattern
        addStonePattern(root);

        // Corner torch glows (like LobbyScene)
        addCornerGlows(root);

        // Back button (top-left) - AboutScene style
        addBackButton(root);

        // Main content - centered
        VBox mainContent = createMainContent();
        root.getChildren().add(mainContent);
        StackPane.setAlignment(mainContent, Pos.CENTER);

        // Scanlines overlay (like LobbyScene)
        addScanlines(root);

        // Fullscreen button (top-right)
        addFullscreenButton(root);

        scene = new Scene(root, GameConfig.WINDOW_WIDTH, GameConfig.WINDOW_HEIGHT);
        scene.getStylesheets().add(getClass().getResource("/styles/global.css").toExternalForm());

        return scene;
    }

    /**
     * Dark stone pattern background.
     */
    private void addStonePattern(StackPane root) {
        Pane pattern = new Pane();
        pattern.prefWidthProperty().bind(root.widthProperty());
        pattern.prefHeightProperty().bind(root.heightProperty());
        pattern.setStyle("-fx-background-color: " + STONE_DARK + ";");
        pattern.setMouseTransparent(true);
        root.getChildren().add(pattern);
    }

    /**
     * Adds animated corner torch glows.
     * Copied from LobbyScene implementation.
     */
    private void addCornerGlows(StackPane root) {
        String glowStyle = "-fx-background-color: radial-gradient(center 50% 50%, radius 50%, rgba(232,116,60,0.12), transparent 70%);";
        double[] delays = { 0, 0.5, 1.0, 1.5 };
        Pos[] positions = { Pos.TOP_LEFT, Pos.TOP_RIGHT, Pos.BOTTOM_LEFT, Pos.BOTTOM_RIGHT };
        double[][] offsets = { { -60, -60 }, { 60, -60 }, { -60, 60 }, { 60, 60 } };

        for (int i = 0; i < 4; i++) {
            Pane glow = new Pane();
            glow.setPrefSize(300, 300);
            glow.setStyle(glowStyle);
            glow.setMouseTransparent(true);
            StackPane.setAlignment(glow, positions[i]);
            glow.setTranslateX(offsets[i][0]);
            glow.setTranslateY(offsets[i][1]);
            root.getChildren().add(glow);

            // Pulse animation
            Timeline pulse = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(glow.opacityProperty(), 0.6),
                            new KeyValue(glow.scaleXProperty(), 0.95),
                            new KeyValue(glow.scaleYProperty(), 0.95)),
                    new KeyFrame(Duration.seconds(2.0),
                            new KeyValue(glow.opacityProperty(), 1.0),
                            new KeyValue(glow.scaleXProperty(), 1.05),
                            new KeyValue(glow.scaleYProperty(), 1.05)));
            pulse.setDelay(Duration.seconds(delays[i]));
            pulse.setAutoReverse(true);
            pulse.setCycleCount(Animation.INDEFINITE);
            pulse.play();
        }
    }

    /**
     * Back button - styled exactly like AboutScene.
     */
    private void addBackButton(StackPane root) {
        Button backBtn = new Button("◀  Back");
        backBtn.setStyle(
                "-fx-font-family: 'Cinzel', serif;" +
                        "-fx-font-size: 11px;" +
                        "-fx-font-weight: 700;" +
                        "-fx-text-fill: " + TEXT_MUTED + ";" +
                        "-fx-letter-spacing: 2px;" +
                        "-fx-background-color: transparent;" +
                        "-fx-border-color: " + STONE_BORDER + ";" +
                        "-fx-border-width: 1px;" +
                        "-fx-padding: 7px 14px;" +
                        "-fx-cursor: hand;");
        backBtn.setOnMouseEntered(e -> backBtn.setStyle(
                "-fx-font-family: 'Cinzel', serif;" +
                        "-fx-font-size: 11px;" +
                        "-fx-font-weight: 700;" +
                        "-fx-text-fill: " + TEXT_PARCHMENT + ";" +
                        "-fx-letter-spacing: 2px;" +
                        "-fx-background-color: transparent;" +
                        "-fx-border-color: " + GOLD_DARK + ";" +
                        "-fx-border-width: 1px;" +
                        "-fx-padding: 7px 14px;" +
                        "-fx-cursor: hand;"));
        backBtn.setOnMouseExited(e -> backBtn.setStyle(
                "-fx-font-family: 'Cinzel', serif;" +
                        "-fx-font-size: 11px;" +
                        "-fx-font-weight: 700;" +
                        "-fx-text-fill: " + TEXT_MUTED + ";" +
                        "-fx-letter-spacing: 2px;" +
                        "-fx-background-color: transparent;" +
                        "-fx-border-color: " + STONE_BORDER + ";" +
                        "-fx-border-width: 1px;" +
                        "-fx-padding: 7px 14px;" +
                        "-fx-cursor: hand;"));
        backBtn.setOnAction(e -> sceneManager.showCreateOrJoin());

        StackPane.setAlignment(backBtn, Pos.TOP_LEFT);
        StackPane.setMargin(backBtn, new Insets(20, 0, 0, 20));
        root.getChildren().add(backBtn);
    }

    private VBox createMainContent() {
        VBox content = new VBox(30);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(40, 40, 40, 40));
        content.setMaxWidth(500);

        // Title: "JOIN GAME" in Press Start 2P, gold
        Label title = new Label("JOIN GAME");
        title.setStyle(
                "-fx-font-family: 'Press Start 2P', monospace;" +
                        "-fx-font-size: 24px;" +
                        "-fx-text-fill: " + GOLD + ";" +
                        "-fx-letter-spacing: 4px;");
        VBox.setMargin(title, new Insets(0, 0, 20, 0));

        // Input card container
        VBox inputCard = new VBox(20);
        inputCard.setAlignment(Pos.CENTER);
        inputCard.setPadding(new Insets(40, 50, 40, 50));
        inputCard.setMaxWidth(450);
        inputCard.setStyle(
                "-fx-background-color: " + STONE_PANEL + ";" +
                        "-fx-border-color: " + GOLD_DARK + ";" +
                        "-fx-border-width: 1px;");

        // Name input label
        Label nameLabel = new Label("CHOOSE YOUR NAME");
        nameLabel.setStyle(
                "-fx-font-family: 'Press Start 2P', monospace;" +
                        "-fx-font-size: 10px;" +
                        "-fx-text-fill: " + TEXT_MUTED + ";" +
                        "-fx-letter-spacing: 2px;");

        // Name input field
        nameInput = new TextField();
        nameInput.setPromptText("NAME");
        nameInput.setAlignment(Pos.CENTER);
        nameInput.setPrefWidth(280);
        nameInput.setMaxWidth(280);
        nameInput.setText("Player");
        nameInput.setStyle(
                "-fx-font-family: 'Press Start 2P', monospace;" +
                        "-fx-font-size: 12px;" +
                        "-fx-text-fill: " + GOLD + ";" +
                        "-fx-prompt-text-fill: " + TEXT_MUTED + ";" +
                        "-fx-background-color: " + STONE_DARK + ";" +
                        "-fx-border-color: " + GOLD + ";" +
                        "-fx-border-width: 2px;" +
                        "-fx-padding: 15px 20px;");

        VBox.setMargin(nameInput, new Insets(0, 0, 10, 0));

        // Input field label in Press Start 2P
        Label inputLabel = new Label("ENTER ROOM CODE");
        inputLabel.setStyle(
                "-fx-font-family: 'Press Start 2P', monospace;" +
                        "-fx-font-size: 10px;" +
                        "-fx-text-fill: " + TEXT_MUTED + ";" +
                        "-fx-letter-spacing: 2px;");

        // Room code input field - Press Start 2P for both text and placeholder
        roomCodeInput = new TextField();
        roomCodeInput.setPromptText("ENTER CODE");
        roomCodeInput.setAlignment(Pos.CENTER);
        roomCodeInput.setPrefWidth(280);
        roomCodeInput.setMaxWidth(280);
        roomCodeInput.setStyle(
                "-fx-font-family: 'Press Start 2P', monospace;" +
                        "-fx-font-size: 12px;" +
                        "-fx-text-fill: " + GOLD + ";" +
                        "-fx-prompt-text-fill: " + TEXT_MUTED + ";" +
                        "-fx-background-color: " + STONE_DARK + ";" +
                        "-fx-border-color: " + GOLD + ";" +
                        "-fx-border-width: 2px;" +
                        "-fx-padding: 15px 20px;");

        // JOIN button in Press Start 2P
        Button joinBtn = createStyledButton("JOIN");
        joinBtn.setOnAction(e -> onJoinClicked());
        VBox.setMargin(joinBtn, new Insets(10, 0, 0, 0));

        // Enter key in the text field also triggers Join.
        roomCodeInput.setOnAction(e -> onJoinClicked());

        // Inline error label for bad codes / connect failures.
        statusLabel = new Label("");
        statusLabel.setStyle(
                "-fx-font-family: 'Press Start 2P', monospace;" +
                        "-fx-font-size: 8px;" +
                        "-fx-text-fill: " + DANGER_RED + ";" +
                        "-fx-letter-spacing: 1px;");
        statusLabel.setWrapText(true);
        statusLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        statusLabel.setMaxWidth(400);
        statusLabel.setVisible(false);
        statusLabel.setManaged(false);

        inputCard.getChildren().addAll(nameLabel, nameInput, inputLabel, roomCodeInput, joinBtn, statusLabel);

        content.getChildren().addAll(title, inputCard);
        return content;
    }

    /**
     * Join-flow: decode the entered code via {@link RoomCodec}, open a
     * {@link GameClient} to the host's IP/port, send the join request, and
     * transition to the lobby. All failures (bad code, connect refused) are
     * surfaced in {@link #statusLabel}; the user stays on this scene.
     */
    private void onJoinClicked() {
        String raw = roomCodeInput.getText();
        if (raw == null || raw.isBlank()) {
            showError("Please enter a room code.");
            return;
        }

        // 1. Decode room code -> host IP/port candidates.
        List<RoomCodec.HostPort> candidates;
        try {
            candidates = RoomCodec.decodeCandidates(raw);
        } catch (IllegalArgumentException e) {
            LOG.warn("Invalid room code entered: \"" + raw + "\" (" + e.getMessage() + ")");
            showError("Invalid room code.");
            return;
        }
        if (candidates.isEmpty()) {
            showError("Invalid room code.");
            return;
        }

        // 2. Build the client networking stack and connect.
        String displayName = nameInput.getText();
        if (displayName == null || displayName.isBlank()) displayName = "Player";
        displayName = displayName.trim();
        LocalGameState localState = new LocalGameState();
        ServerMessageRouter router = new ServerMessageRouter(localState);
        router.setOnLobbyStateChanged(() -> {
            String[] names = localState.getLobbyPlayerNames();
            boolean[] ready = localState.getLobbyReadyFlags();
            int mi = localState.getLobbyMyIndex();
            sceneManager.getLobbyScene().setLobbyPlayers(names, ready, mi);
        });
        router.setOnChatReceived(() -> sceneManager.getLobbyScene().refreshChatMessages());
        router.setOnGameStarted(() -> sceneManager.showGameArena());
        GameClient gameClient = null;
        RoomCodec.HostPort connectedHost = null;
        IOException lastError = null;
        for (RoomCodec.HostPort candidate : candidates) {
            GameClient candidateClient = new GameClient(router);
            candidateClient.setOnDisconnected(() -> sceneManager.handleServerDisconnected());
            try {
                candidateClient.connect(candidate.ip(), candidate.port(), 900);
                gameClient = candidateClient;
                connectedHost = candidate;
                break;
            } catch (IOException e) {
                lastError = e;
                LOG.warn("Could not connect to host candidate " + candidate + ": " + e.getMessage());
            }
        }
        if (gameClient == null) {
            if (lastError != null) {
                LOG.error("Could not connect to any host candidate for room " + raw, lastError);
            }
            showError("Could not connect. Use the same Wi-Fi and disable AP/client isolation or allow Java through firewall.");
            return;
        }
        gameClient.startListening();
        gameClient.sendJoinRequest(displayName);

        // 3. Publish session state. Joiners are NOT hosts and have no embedded server.
        sceneManager.setEmbeddedServer(null);
        sceneManager.setRoomCode(raw.trim().toUpperCase());
        sceneManager.setHost(false);
        sceneManager.setGameClient(gameClient);
        sceneManager.setLocalGameState(localState);
        sceneManager.setMyDisplayName(displayName);
        LOG.info("Joined room " + raw + " at " + connectedHost);
        clearError();
        sceneManager.showLobby();
    }

    private void showError(String msg) {
        if (statusLabel == null)
            return;
        statusLabel.setText(msg);
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
    }

    private void clearError() {
        if (statusLabel == null)
            return;
        statusLabel.setText("");
        statusLabel.setVisible(false);
        statusLabel.setManaged(false);
    }

    private Button createStyledButton(String text) {
        Button btn = new Button(text);
        btn.setPrefSize(180, 44);
        btn.setMinSize(180, 44);
        btn.setMaxSize(180, 44);
        btn.setStyle(
                "-fx-font-family: 'Press Start 2P', monospace;" +
                        "-fx-font-size: 12px;" +
                        "-fx-text-fill: " + GOLD + ";" +
                        "-fx-background-color: #4a3a1a;" +
                        "-fx-border-color: #1a0e00;" +
                        "-fx-border-width: 2px;" +
                        "-fx-cursor: hand;");

        btn.setOnMouseEntered(e -> btn.setStyle(
                "-fx-font-family: 'Press Start 2P', monospace;" +
                        "-fx-font-size: 12px;" +
                        "-fx-text-fill: " + GOLD_LIGHT + ";" +
                        "-fx-background-color: #5c4920;" +
                        "-fx-border-color: #1a0e00;" +
                        "-fx-border-width: 2px;" +
                        "-fx-cursor: hand;"));

        btn.setOnMouseExited(e -> btn.setStyle(
                "-fx-font-family: 'Press Start 2P', monospace;" +
                        "-fx-font-size: 12px;" +
                        "-fx-text-fill: " + GOLD + ";" +
                        "-fx-background-color: #4a3a1a;" +
                        "-fx-border-color: #1a0e00;" +
                        "-fx-border-width: 2px;" +
                        "-fx-cursor: hand;"));

        btn.setOnMousePressed(e -> {
            btn.setScaleX(0.97);
            btn.setScaleY(0.97);
            btn.setTranslateY(2);
        });

        btn.setOnMouseReleased(e -> {
            btn.setScaleX(1.0);
            btn.setScaleY(1.0);
            btn.setTranslateY(0);
        });

        return btn;
    }

    /**
     * Scanlines overlay - copied from LobbyScene.
     */
    private void addScanlines(StackPane root) {
        Pane scanlines = new Pane();
        scanlines.prefWidthProperty().bind(root.widthProperty());
        scanlines.prefHeightProperty().bind(root.heightProperty());
        // Use semi-transparent overlay instead of repeating-linear-gradient (not
        // supported in JavaFX)
        scanlines.setStyle("-fx-background-color: rgba(0,0,0,0.015);");
        scanlines.setMouseTransparent(true);
        root.getChildren().add(scanlines);
    }

    private void addFullscreenButton(StackPane root) {
        Button fullscreenBtn = new Button("⛶");
        fullscreenBtn.setPrefSize(32, 32);
        fullscreenBtn.setMinSize(32, 32);
        fullscreenBtn.setMaxSize(32, 32);
        fullscreenBtn.setStyle(
                "-fx-font-family: 'Press Start 2P', monospace;" +
                        "-fx-font-size: 12px;" +
                        "-fx-text-fill: " + GOLD + ";" +
                        "-fx-background-color: " + STONE_PANEL + ";" +
                        "-fx-border-color: " + GOLD_DARK + ";" +
                        "-fx-border-width: 1px;" +
                        "-fx-cursor: hand;");

        fullscreenBtn.setOnMouseEntered(e -> {
            fullscreenBtn.setStyle(
                    "-fx-font-family: 'Press Start 2P', monospace;" +
                            "-fx-font-size: 12px;" +
                            "-fx-text-fill: " + GOLD + ";" +
                            "-fx-background-color: rgba(201, 168, 76, 0.1);" +
                            "-fx-border-color: " + GOLD + ";" +
                            "-fx-border-width: 1px;" +
                            "-fx-cursor: hand;");
        });

        fullscreenBtn.setOnMouseExited(e -> {
            fullscreenBtn.setStyle(
                    "-fx-font-family: 'Press Start 2P', monospace;" +
                            "-fx-font-size: 12px;" +
                            "-fx-text-fill: " + GOLD + ";" +
                            "-fx-background-color: " + STONE_PANEL + ";" +
                            "-fx-border-color: " + GOLD_DARK + ";" +
                            "-fx-border-width: 1px;" +
                            "-fx-cursor: hand;");
        });

        fullscreenBtn.setOnAction(e -> sceneManager.toggleFullscreen());

        StackPane.setAlignment(fullscreenBtn, Pos.TOP_RIGHT);
        StackPane.setMargin(fullscreenBtn, new Insets(20, 20, 0, 0));
        root.getChildren().add(fullscreenBtn);
    }

    // Legacy method for compatibility
    public Scene getScene() {
        if (scene == null) {
            scene = createScene();
        }
        return scene;
    }
}
