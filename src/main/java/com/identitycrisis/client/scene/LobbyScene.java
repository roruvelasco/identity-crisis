package com.identitycrisis.client.scene;

import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.control.*;
import javafx.geometry.*;
import javafx.animation.*;
import javafx.util.Duration;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.Font;
import com.identitycrisis.client.render.SpriteManager;
import com.identitycrisis.shared.model.GameConfig;

/**
 * Lobby/waiting screen — displays while players gather before game start.
 * Shows connected players with their assigned sprites and rotating tips.
 */
public class LobbyScene {

    private Scene scene;
    private final SceneManager sceneManager;
    private final SpriteManager spriteManager;

    // Color constants
    private static final String GOLD = "#c9a84c";
    private static final String GOLD_LIGHT = "#e8c86a";
    private static final String GOLD_DARK = "#8a6a1a";
    private static final String STONE_DARK = "#0d0d10";
    private static final String STONE_PANEL = "#1c1c26";
    private static final String TEXT_PARCHMENT = "#e8dfc4";
    private static final String TEXT_MUTED = "#7a7060";
    private static final String STONE_BORDER = "#2a2a36";

    private FlowPane playerListContainer;
    private Label lobbyFullLabel;
    private Label tipLabel;
    private int tipIndex = 0;
    private Timeline tipRotation;
    private Label roomCodeLabel;
    private String roomCode;
    private Button startBtn;
    
    // Default font loaded from resources if needed
    private Font defaultFont;

    private final String[] tips = {
        "Carry a teammate to safety — but you can't enter the safe zone while holding them.",
        "When controls are reversed, stay calm. Panic kills faster than the chaos.",
        "Safe zones shrink each round. Position yourself early.",
        "The decoy safe zones look identical. Watch other players — or trust your instincts.",
        "In Round 3+, only n-1 players survive. One must be eliminated.",
        "You can throw carried players into the safe zone before entering yourself.",
        "Control swaps are unpredictable. Learn multiple playstyles."
    };

    public LobbyScene(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
        this.spriteManager = new SpriteManager();
        this.spriteManager.loadAll();
        try {
            defaultFont = Font.loadFont(getClass().getResourceAsStream("/fonts/PressStart2P-Regular.ttf"), 10);
        } catch (Exception e) {
            defaultFont = Font.font("Monospace", 10);
        }
    }

    public Scene createScene() {
        StackPane root = new StackPane();
        root.setStyle("-fx-background-color: " + STONE_DARK + ";");
        root.setPrefSize(GameConfig.WINDOW_WIDTH, GameConfig.WINDOW_HEIGHT);

        // Stone tile pattern
        addStonePattern(root);

        // Corner torch glows
        addCornerGlows(root);

        // Back button (top-left)
        addBackButton(root);

        // Main content
        VBox content = createContent();
        root.getChildren().add(content);

        // Scanlines
        addScanlines(root);

        // Fullscreen button
        addFullscreenButton(root);

        scene = new Scene(root, GameConfig.WINDOW_WIDTH, GameConfig.WINDOW_HEIGHT);
        scene.getStylesheets().add(getClass().getResource("/styles/global.css").toExternalForm());

        return scene;
    }

    private void addStonePattern(StackPane root) {
        Pane pattern = new Pane();
        pattern.setPrefSize(GameConfig.WINDOW_WIDTH, GameConfig.WINDOW_HEIGHT);
        // Use solid background color - JavaFX doesn't support multiple gradients in background-image
        pattern.setStyle("-fx-background-color: " + STONE_DARK + ";");
        pattern.setMouseTransparent(true);
        root.getChildren().add(pattern);
    }

    private void addCornerGlows(StackPane root) {
        String glowStyle = "-fx-background-color: radial-gradient(center 50% 50%, radius 50%, rgba(232,116,60,0.12), transparent 70%);";
        double[] delays = {0, 0.5, 1.0, 1.5};
        Pos[] positions = {Pos.TOP_LEFT, Pos.TOP_RIGHT, Pos.BOTTOM_LEFT, Pos.BOTTOM_RIGHT};
        double[][] offsets = {{-60, -60}, {60, -60}, {-60, 60}, {60, 60}};

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
                    new KeyValue(glow.scaleYProperty(), 1.05))
            );
            pulse.setDelay(Duration.seconds(delays[i]));
            pulse.setAutoReverse(true);
            pulse.setCycleCount(Animation.INDEFINITE);
            pulse.play();
        }
    }

    private VBox createContent() {
        VBox content = new VBox(0);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(0, 24, 0, 24));
        content.setMaxWidth(560);

        // Room code display (generated on scene load)
        roomCodeLabel = new Label("ROOM CODE: ------");
        roomCodeLabel.setStyle(
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 18px;" +
            "-fx-text-fill: " + GOLD + ";" +
            "-fx-letter-spacing: 4px;"
        );
        roomCodeLabel.setEffect(new javafx.scene.effect.DropShadow(30, Color.rgb(201, 168, 76, 0.5)));
        VBox.setMargin(roomCodeLabel, new Insets(0, 0, 16, 0));

        // Enlarged waiting text (title-level weight using Press Start 2P)
        Label waitingText = new Label("WAITING FOR PLAYERS...");
        waitingText.setStyle(
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 16px;" +
            "-fx-text-fill: " + GOLD + ";" +
            "-fx-letter-spacing: 4px;"
        );
        waitingText.setEffect(new javafx.scene.effect.DropShadow(30, Color.rgb(201, 168, 76, 0.5)));
        VBox.setMargin(waitingText, new Insets(0, 0, 32, 0));

        // Player sprite list section
        VBox listSection = createPlayerListSection();
        VBox.setMargin(listSection, new Insets(0, 0, 0, 0));

        // Start Game / Ready button
        // Host sees Start Game. Client sees Ready.
        startBtn = createPixelButton("▶  Start Game");
        startBtn.setOnAction(e -> onReadyClicked());
        VBox.setMargin(startBtn, new Insets(20, 0, 0, 0));

        // Tip box
        VBox tipBox = createTipBox();
        VBox.setMargin(tipBox, new Insets(28, 0, 0, 0));

        content.getChildren().addAll(roomCodeLabel, waitingText, listSection, startBtn, tipBox);
        return content;
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
            "-fx-cursor: hand;"
        );
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
            "-fx-cursor: hand;"
        ));
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
            "-fx-cursor: hand;"
        ));
        backBtn.setOnAction(e -> {
            // Leaving the lobby ends the session for this client (and the room,
            // if we are the host). Release sockets and the embedded server.
            sceneManager.shutdownNetwork();
            sceneManager.showCreateOrJoin();
        });

        StackPane.setAlignment(backBtn, Pos.TOP_LEFT);
        StackPane.setMargin(backBtn, new Insets(20, 0, 0, 20));
        root.getChildren().add(backBtn);
    }


    private Button createPixelButton(String text) {
        Button btn = new Button(text);
        btn.setPrefSize(240, 52);
        btn.setMinSize(240, 52);
        btn.setMaxSize(240, 52);
        btn.setStyle(
            "-fx-font-family: 'Cinzel', serif;" +
            "-fx-font-size: 15px;" +
            "-fx-font-weight: 700;" +
            "-fx-text-fill: " + GOLD_LIGHT + ";" +
            "-fx-background-color: #4a3a1a;" +
            "-fx-border-color: #1a0e00;" +
            "-fx-border-width: 2px;" +
            "-fx-cursor: hand;"
        );
        btn.setOnMouseEntered(e -> btn.setStyle(
            "-fx-font-family: 'Cinzel', serif;" +
            "-fx-font-size: 15px;" +
            "-fx-font-weight: 700;" +
            "-fx-text-fill: white;" +
            "-fx-background-color: #5c4920;" +
            "-fx-border-color: #1a0e00;" +
            "-fx-border-width: 2px;" +
            "-fx-cursor: hand;"
        ));
        btn.setOnMouseExited(e -> btn.setStyle(
            "-fx-font-family: 'Cinzel', serif;" +
            "-fx-font-size: 15px;" +
            "-fx-font-weight: 700;" +
            "-fx-text-fill: " + GOLD_LIGHT + ";" +
            "-fx-background-color: #4a3a1a;" +
            "-fx-border-color: #1a0e00;" +
            "-fx-border-width: 2px;" +
            "-fx-cursor: hand;"
        ));
        btn.setOnMousePressed(e -> { btn.setScaleX(0.97); btn.setScaleY(0.97); btn.setTranslateY(2); });
        btn.setOnMouseReleased(e -> { btn.setScaleX(1.0); btn.setScaleY(1.0); btn.setTranslateY(0); });
        return btn;
    }

    private VBox createPlayerListSection() {
        VBox container = new VBox(15);
        container.setAlignment(Pos.CENTER);

        playerListContainer = new FlowPane(Orientation.HORIZONTAL);
        playerListContainer.setAlignment(Pos.CENTER);
        playerListContainer.setHgap(15);
        playerListContainer.setVgap(15);
        playerListContainer.setPrefWrapLength(400);

        lobbyFullLabel = new Label("▶  LOBBY FULL  ◀");
        lobbyFullLabel.setStyle(
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 7px;" +
            "-fx-text-fill: " + GOLD_LIGHT + ";" +
            "-fx-letter-spacing: 2px;"
        );
        lobbyFullLabel.setVisible(false);
        lobbyFullLabel.setManaged(false);

        // Populate initially with just 1 placeholder card
        setLobbyPlayers(new String[]{"PLAYER 1"}, new boolean[]{false}, 1);

        container.getChildren().addAll(playerListContainer, lobbyFullLabel);
        return container;
    }

    private VBox createPlayerCard(String name, int spriteIdx, boolean isMe, boolean isReady) {
        VBox card = new VBox(8);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(10));
        
        String borderCol = isMe ? GOLD : STONE_BORDER;
        String bgCol = isMe ? "#2a2210" : STONE_PANEL;
        card.setStyle(
            "-fx-background-color: " + bgCol + ";" +
            "-fx-border-color: " + borderCol + ";" +
            "-fx-border-width: 2px;" +
            "-fx-border-radius: 4px;" +
            "-fx-background-radius: 4px;"
        );
        card.setPrefSize(90, 110);
        card.setMinSize(90, 110);

        // Render Sprite to Canvas
        Canvas canvas = new Canvas(48, 48);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        Image sheet = spriteManager.get("player_" + spriteIdx + "_idle");
        if (sheet != null) {
            gc.setImageSmoothing(false);
            // Draw first frame of idle animation
            gc.drawImage(sheet, 0, 0, 32, 32, 0, 0, 48, 48);
        } else {
            gc.setFill(Color.web("#3E8948"));
            gc.fillOval(8, 8, 32, 32);
        }

        Label nameLbl = new Label(name != null && !name.isBlank() ? name : ("P" + spriteIdx));
        nameLbl.setStyle(
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 7px;" +
            "-fx-text-fill: " + TEXT_PARCHMENT + ";"
        );
        nameLbl.setMaxWidth(80);
        nameLbl.setAlignment(Pos.CENTER);

        Label statusLbl = new Label(isMe ? "(YOU)" : (isReady ? "READY" : "WAIT"));
        statusLbl.setStyle(
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 6px;" +
            "-fx-text-fill: " + (isMe ? GOLD_LIGHT : (isReady ? "#63C74D" : TEXT_MUTED)) + ";"
        );

        card.getChildren().addAll(canvas, nameLbl, statusLbl);
        return card;
    }

    /** Called by the network layer with the actual connected players. */
    public void setLobbyPlayers(String[] names, boolean[] readyFlags, int myIndex) {
        if (playerListContainer == null) return;
        playerListContainer.getChildren().clear();

        int count = names != null ? names.length : 0;
        for (int i = 0; i < count; i++) {
            boolean isMe = ((i + 1) == myIndex) || (myIndex == 0 && i == 0);
            boolean isReady = readyFlags != null && i < readyFlags.length && readyFlags[i];
            int spriteIdx = (i % 8) + 1; // 1-8 deterministic
            
            VBox card = createPlayerCard(names[i], spriteIdx, isMe, isReady);
            playerListContainer.getChildren().add(card);
        }

        boolean isFull = count >= GameConfig.MAX_PLAYERS;
        if (lobbyFullLabel != null) {
            lobbyFullLabel.setVisible(isFull);
            lobbyFullLabel.setManaged(isFull);
        }
    }

    /** Legacy entry point if code still calls setPlayerCount directly. */
    public void setPlayerCount(int count) {
        String[] dummyNames = new String[count];
        for (int i = 0; i < count; i++) dummyNames[i] = "P" + (i + 1);
        setLobbyPlayers(dummyNames, new boolean[count], 1);
    }

    private VBox createTipBox() {
        VBox tipBox = new VBox(10);
        tipBox.setAlignment(Pos.CENTER);
        tipBox.setPadding(new Insets(20, 0, 0, 0));
        tipBox.setStyle("-fx-border-color: " + GOLD_DARK + " transparent transparent transparent; -fx-border-width: 1px 0 0 0;");
        tipBox.setMaxWidth(400);

        Label tipLabelTitle = new Label("◆   TIP   ◆");
        tipLabelTitle.setStyle(
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 6px;" +
            "-fx-text-fill: " + GOLD_DARK + ";" +
            "-fx-letter-spacing: 3px;"
        );

        tipLabel = new Label(tips[0]);
        tipLabel.setWrapText(true);
        tipLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        tipLabel.setStyle(
            "-fx-font-family: 'Crimson Pro', serif;" +
            "-fx-font-style: italic;" +
            "-fx-font-size: 15px;" +
            "-fx-text-fill: " + TEXT_MUTED + ";"
        );

        tipBox.getChildren().addAll(tipLabelTitle, tipLabel);
        return tipBox;
    }

    private void addScanlines(StackPane root) {
        Pane scanlines = new Pane();
        scanlines.setPrefSize(GameConfig.WINDOW_WIDTH, GameConfig.WINDOW_HEIGHT);
        // Use semi-transparent overlay instead of repeating-linear-gradient (not supported in JavaFX)
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
            "-fx-cursor: hand;"
        );

        fullscreenBtn.setOnMouseEntered(e -> {
            fullscreenBtn.setStyle(
                "-fx-font-family: 'Press Start 2P', monospace;" +
                "-fx-font-size: 12px;" +
                "-fx-text-fill: " + GOLD + ";" +
                "-fx-background-color: rgba(201, 168, 76, 0.1);" +
                "-fx-border-color: " + GOLD + ";" +
                "-fx-border-width: 1px;" +
                "-fx-cursor: hand;"
            );
        });

        fullscreenBtn.setOnMouseExited(e -> {
            fullscreenBtn.setStyle(
                "-fx-font-family: 'Press Start 2P', monospace;" +
                "-fx-font-size: 12px;" +
                "-fx-text-fill: " + GOLD + ";" +
                "-fx-background-color: " + STONE_PANEL + ";" +
                "-fx-border-color: " + GOLD_DARK + ";" +
                "-fx-border-width: 1px;" +
                "-fx-cursor: hand;"
            );
        });

        fullscreenBtn.setOnAction(e -> sceneManager.toggleFullscreen());

        StackPane.setAlignment(fullscreenBtn, Pos.TOP_RIGHT);
        StackPane.setMargin(fullscreenBtn, new Insets(20, 20, 0, 0));
        root.getChildren().add(fullscreenBtn);
    }

    /**
     * Called when entering the lobby scene.
     * Resets to 1 player, displays the room code from SceneManager, and starts
     * tip rotation.
     *
     * <p>The room code is set by either {@code CreateOrJoinScene.onCreateClicked}
     * (host) or {@code JoinRoomScene.onJoinClicked} (joiner) — see those
     * scenes for the producer side. If neither ran (e.g. scene opened directly
     * during dev), a placeholder is shown.
     */
    public void onEnter() {
        tipIndex = 0;

        // Pull the real room code from the SceneManager (populated by the
        // Create or Join flow). Falls back to placeholder if absent.
        roomCode = sceneManager.getRoomCode();
        if (roomCodeLabel != null) {
            roomCodeLabel.setText("ROOM CODE: " + (roomCode != null ? roomCode : "------"));
        }

        // Initial setup for the host vs joiner
        if (startBtn != null) {
            boolean isHost = sceneManager.isHost();
            startBtn.setText(isHost ? "▶  Start Game" : "▶  Ready");
            startBtn.setVisible(true);
            startBtn.setManaged(true);
        }

        // Tip rotation
        tipRotation = new Timeline(
            new KeyFrame(Duration.seconds(4), e -> {
                tipIndex = (tipIndex + 1) % tips.length;
                tipLabel.setText(tips[tipIndex]);
                FadeTransition fadeIn = new FadeTransition(Duration.millis(250), tipLabel);
                fadeIn.setFromValue(0);
                fadeIn.setToValue(1);
                fadeIn.play();
            })
        );
        tipRotation.setCycleCount(Animation.INDEFINITE);
        tipRotation.play();
    }

    /** Stops tip rotation. Call before leaving this scene. */
    public void onExit() {
        if (tipRotation != null) tipRotation.stop();
    }

    private void onReadyClicked() {
        // Phase 6: Inform server that this client is ready to start.
        if (sceneManager != null && sceneManager.getGameClient() != null) {
            sceneManager.getGameClient().sendReady();
        }
        // Button disables to prevent spam
        if (startBtn != null) {
            startBtn.setDisable(true);
            startBtn.setText("Waiting...");
        }
    }

    public void refreshLobbyDisplay() {
        // Automatically handled by setLobbyPlayers via router updates
    }

    // Legacy method for compatibility
    public Scene getScene() {
        if (scene == null) {
            scene = createScene();
        }
        return scene;
    }
}
