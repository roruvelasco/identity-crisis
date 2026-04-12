package com.identitycrisis.client.scene;

import javafx.scene.Scene;
import javafx.scene.layout.*;
import javafx.scene.control.*;
import javafx.geometry.*;
import javafx.animation.*;
import javafx.util.Duration;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import com.identitycrisis.shared.model.GameConfig;
import java.util.Random;

/**
 * Lobby/waiting screen — displays while players gather before game start.
 * Animated crest, dynamic player-count donut ring, rotating tips.
 */
public class LobbyScene {

    private Scene scene;
    private SceneManager sceneManager;

    // Color constants
    private static final String GOLD = "#c9a84c";
    private static final String GOLD_LIGHT = "#e8c86a";
    private static final String GOLD_DARK = "#8a6a1a";
    private static final String STONE_DARK = "#0d0d10";
    private static final String STONE_PANEL = "#1c1c26";
    private static final String TEXT_PARCHMENT = "#e8dfc4";
    private static final String TEXT_MUTED = "#7a7060";

    private Canvas donutCanvas;
    private int playerCount = 1;
    private Label playerCountLabel;
    private Label lobbyFullLabel;
    private Label testCountDisplay;
    private Label tipLabel;
    private int tipIndex = 0;
    private Timeline tipRotation;
    private Label roomCodeLabel;
    private String roomCode;

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

        // Player donut ring section
        VBox donutSection = createPlayerDonut();
        VBox.setMargin(donutSection, new Insets(0, 0, 0, 0));

        // Start Game button - navigates to LoadingScene (then to GameArena)
        Button startBtn = createPixelButton("▶  Start Game");
        startBtn.setOnAction(e -> sceneManager.showLoading());
        VBox.setMargin(startBtn, new Insets(20, 0, 0, 0));

        // Tip box
        VBox tipBox = createTipBox();
        VBox.setMargin(tipBox, new Insets(28, 0, 0, 0));

        content.getChildren().addAll(roomCodeLabel, waitingText, donutSection, startBtn, tipBox);
        return content;
    }

    private void addBackButton(StackPane root) {
        Button backBtn = new Button("◄  BACK");
        backBtn.setStyle(
            "-fx-font-family: 'Cinzel', serif;" +
            "-fx-font-size: 14px;" +
            "-fx-font-weight: 700;" +
            "-fx-text-fill: " + GOLD + ";" +
            "-fx-background-color: transparent;" +
            "-fx-cursor: hand;"
        );

        backBtn.setOnMouseEntered(e -> backBtn.setStyle(
            "-fx-font-family: 'Cinzel', serif;" +
            "-fx-font-size: 14px;" +
            "-fx-font-weight: 700;" +
            "-fx-text-fill: white;" +
            "-fx-background-color: transparent;" +
            "-fx-cursor: hand;"
        ));

        backBtn.setOnMouseExited(e -> backBtn.setStyle(
            "-fx-font-family: 'Cinzel', serif;" +
            "-fx-font-size: 14px;" +
            "-fx-font-weight: 700;" +
            "-fx-text-fill: " + GOLD + ";" +
            "-fx-background-color: transparent;" +
            "-fx-cursor: hand;"
        ));

        backBtn.setOnAction(e -> sceneManager.showCreateOrJoin());

        StackPane.setAlignment(backBtn, Pos.TOP_LEFT);
        StackPane.setMargin(backBtn, new Insets(20, 0, 0, 20));
        root.getChildren().add(backBtn);
    }

    /**
     * Generate a random 6-character alphanumeric room code.
     * Uses client-side random generation - no networking.
     */
    private String generateRoomCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }
        return code.toString();
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

    private VBox createPlayerDonut() {
        VBox container = new VBox(10);
        container.setAlignment(Pos.CENTER);

        // Canvas for the donut ring (180×180 px)
        donutCanvas = new Canvas(180, 180);
        drawDonut(playerCount);

        // "X / 8 PLAYERS" label
        playerCountLabel = new Label(playerCount + " / " + GameConfig.MAX_PLAYERS + " PLAYERS");
        playerCountLabel.setStyle(
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 8px;" +
            "-fx-text-fill: " + GOLD + ";" +
            "-fx-letter-spacing: 2px;"
        );

        // LOBBY FULL indicator (hidden until max)
        lobbyFullLabel = new Label("▶  LOBBY FULL  ◀");
        lobbyFullLabel.setStyle(
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 7px;" +
            "-fx-text-fill: " + GOLD_LIGHT + ";" +
            "-fx-letter-spacing: 2px;"
        );
        lobbyFullLabel.setVisible(false);
        lobbyFullLabel.setManaged(false);

        // Mock test controls: [−] count [+]
        HBox testControls = createTestControls();

        container.getChildren().addAll(donutCanvas, playerCountLabel, lobbyFullLabel, testControls);
        return container;
    }

    private void drawDonut(int count) {
        GraphicsContext gc = donutCanvas.getGraphicsContext2D();
        double W = donutCanvas.getWidth();
        double H = donutCanvas.getHeight();
        double cx = W / 2.0;
        double cy = H / 2.0;
        double outerR = 76.0;
        double innerR = 48.0;
        double gapDeg = (count > 1) ? 5.0 : 0.0;

        gc.clearRect(0, 0, W, H);

        for (int i = 0; i < count; i++) {
            double sliceAngle = 360.0 / count;
            double startAngle = 90.0 + i * sliceAngle + gapDeg / 2.0;
            double extent = sliceAngle - gapDeg;

            // Outer glow pass — semi-transparent, slightly oversized
            gc.setFill(Color.rgb(201, 168, 76, 0.22));
            gc.fillArc(cx - outerR - 5, cy - outerR - 5,
                       (outerR + 5) * 2, (outerR + 5) * 2,
                       startAngle, extent, ArcType.ROUND);

            // Main gold arc
            gc.setFill(Color.web(GOLD));
            gc.fillArc(cx - outerR, cy - outerR, outerR * 2, outerR * 2,
                       startAngle, extent, ArcType.ROUND);

            // Inner highlight — lighter gold strip near outer edge
            gc.setFill(Color.rgb(232, 200, 106, 0.30));
            gc.fillArc(cx - outerR + 5, cy - outerR + 5,
                       (outerR - 5) * 2, (outerR - 5) * 2,
                       startAngle + 3, Math.max(extent - 6, 1.0), ArcType.ROUND);
        }

        // Donut hole — paint center with background color
        gc.setFill(Color.web(STONE_DARK));
        gc.fillOval(cx - innerR, cy - innerR, innerR * 2, innerR * 2);

        // Thin inner ring border
        gc.setStroke(Color.web(GOLD_DARK));
        gc.setLineWidth(1.0);
        gc.strokeOval(cx - innerR, cy - innerR, innerR * 2, innerR * 2);
    }

    private HBox createTestControls() {
        HBox controls = new HBox(10);
        controls.setAlignment(Pos.CENTER);
        controls.setPadding(new Insets(6, 0, 0, 0));

        String btnBase =
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 11px;" +
            "-fx-text-fill: " + GOLD + ";" +
            "-fx-background-color: " + STONE_PANEL + ";" +
            "-fx-border-color: " + GOLD_DARK + ";" +
            "-fx-border-width: 1px;" +
            "-fx-cursor: hand;" +
            "-fx-pref-width: 30px;" +
            "-fx-pref-height: 26px;";

        String btnHover =
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 11px;" +
            "-fx-text-fill: " + GOLD_LIGHT + ";" +
            "-fx-background-color: rgba(201,168,76,0.12);" +
            "-fx-border-color: " + GOLD + ";" +
            "-fx-border-width: 1px;" +
            "-fx-cursor: hand;" +
            "-fx-pref-width: 30px;" +
            "-fx-pref-height: 26px;";

        Button minus = new Button("−");
        minus.setStyle(btnBase);
        minus.setOnMouseEntered(e -> minus.setStyle(btnHover));
        minus.setOnMouseExited(e -> minus.setStyle(btnBase));
        minus.setOnAction(e -> {
            if (playerCount > 1) {
                playerCount--;
                updatePlayerCount();
            }
        });

        testCountDisplay = new Label(String.valueOf(playerCount));
        testCountDisplay.setStyle(
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 9px;" +
            "-fx-text-fill: " + TEXT_MUTED + ";" +
            "-fx-pref-width: 22px;" +
            "-fx-alignment: center;"
        );

        Button plus = new Button("+");
        plus.setStyle(btnBase);
        plus.setOnMouseEntered(e -> plus.setStyle(btnHover));
        plus.setOnMouseExited(e -> plus.setStyle(btnBase));
        plus.setOnAction(e -> {
            if (playerCount < GameConfig.MAX_PLAYERS) {
                playerCount++;
                updatePlayerCount();
            }
        });


        controls.getChildren().addAll(minus, testCountDisplay, plus);
        return controls;
    }

    private void updatePlayerCount() {
        drawDonut(playerCount);
        playerCountLabel.setText(playerCount + " / " + GameConfig.MAX_PLAYERS + " PLAYERS");
        if (testCountDisplay != null) {
            testCountDisplay.setText(String.valueOf(playerCount));
        }
        boolean isFull = playerCount >= GameConfig.MAX_PLAYERS;
        lobbyFullLabel.setVisible(isFull);
        lobbyFullLabel.setManaged(isFull);
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
     * Resets to 1 player, generates room code, and starts tip rotation.
     */
    public void onEnter() {
        tipIndex = 0;
        playerCount = 1;

        // Generate and display room code
        roomCode = generateRoomCode();
        if (roomCodeLabel != null) {
            roomCodeLabel.setText("ROOM CODE: " + roomCode);
        }
        if (donutCanvas != null) {
            drawDonut(playerCount);
        }
        if (playerCountLabel != null) {
            playerCountLabel.setText(playerCount + " / " + GameConfig.MAX_PLAYERS + " PLAYERS");
        }
        if (testCountDisplay != null) {
            testCountDisplay.setText("1");
        }
        if (lobbyFullLabel != null) {
            lobbyFullLabel.setVisible(false);
            lobbyFullLabel.setManaged(false);
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

    /** Called by the network layer with the real player count from the server. */
    public void setPlayerCount(int count) {
        this.playerCount = Math.max(1, Math.min(count, GameConfig.MAX_PLAYERS));
        if (donutCanvas != null) updatePlayerCount();
    }

    private void onReadyClicked() {
        // Ready functionality for multiplayer lobby
    }

    public void refreshLobbyDisplay() {
        // Update player list display
    }

    // Legacy method for compatibility
    public Scene getScene() {
        if (scene == null) {
            scene = createScene();
        }
        return scene;
    }
}
