package com.identitycrisis.client.scene;

import javafx.scene.Scene;
import javafx.scene.layout.*;
import javafx.scene.control.*;
import javafx.geometry.*;
import javafx.animation.*;
import javafx.util.Duration;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Line;
import javafx.scene.Group;
import com.identitycrisis.shared.model.GameConfig;

/**
 * Lobby/Loading screen matching loading.html pixel-perfect design.
 * Animated crest, loading bar, status text, rotating tips.
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

    private Label statusLabel;
    private Label percentLabel;
    private Label tipLabel;
    private Rectangle barFill;
    private int progress = 0;
    private int statusIndex = 0;
    private int tipIndex = 0;

    private final String[] statuses = {
        "Connecting to server...",
        "Loading arena tiles...",
        "Spawning players...",
        "Initializing safe zones...",
        "Preparing chaos events...",
        "Syncing game state...",
        "Ready!"
    };

    private final String[] tips = {
        "Carry a teammate to safety — but you can't enter the safe zone while holding them.",
        "When controls are reversed, stay calm. Panic kills faster than the chaos.",
        "Safe zones shrink each round. Position yourself early.",
        "The decoy safe zones look identical. Watch other players — or trust your instincts.",
        "In Round 3+, only n-1 players survive. One must be eliminated.",
        "You can throw carried players into the safe zone before entering yourself.",
        "Control swaps are unpredictable. Learn multiple playstyles."
    };

    private Timeline loadingAnimation;
    private Timeline tipRotation;

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
        pattern.setStyle(
            "-fx-background-color: " + STONE_DARK + ";" +
            "-fx-background-image: linear-gradient(rgba(255,255,255,0.015) 1px, transparent 1px)," +
            "linear-gradient(90deg, rgba(255,255,255,0.015) 1px, transparent 1px);" +
            "-fx-background-size: 48px 48px;"
        );
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

        // Animated Crest
        Group crest = createCrest();
        VBox.setMargin(crest, new Insets(0, 0, 20, 0));

        // Title
        Label title = new Label("Identity Crisis");
        title.setStyle(
            "-fx-font-family: 'Cinzel Decorative', serif;" +
            "-fx-font-size: 48px;" +
            "-fx-font-weight: 700;" +
            "-fx-text-fill: " + TEXT_PARCHMENT + ";"
        );
        title.setEffect(new javafx.scene.effect.DropShadow(30, Color.rgb(201, 168, 76, 0.5)));
        VBox.setMargin(title, new Insets(0, 0, 8, 0));

        // Subtitle
        Label subtitle = new Label("Loading Arena...");
        subtitle.setStyle(
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 8px;" +
            "-fx-text-fill: " + GOLD + ";" +
            "-fx-letter-spacing: 4px;"
        );
        subtitle.setOpacity(0.7);
        VBox.setMargin(subtitle, new Insets(0, 0, 48, 0));

        // Loading bar container
        VBox barContainer = createLoadingBar();
        VBox.setMargin(barContainer, new Insets(0, 0, 16, 0));

        // Status text
        statusLabel = new Label("Connecting to server...");
        statusLabel.setStyle(
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 8px;" +
            "-fx-text-fill: " + TEXT_MUTED + ";" +
            "-fx-letter-spacing: 1px;"
        );
        VBox.setMargin(statusLabel, new Insets(4, 0, 0, 0));

        // Tip box
        VBox tipBox = createTipBox();
        VBox.setMargin(tipBox, new Insets(48, 0, 0, 0));

        content.getChildren().addAll(crest, title, subtitle, barContainer, statusLabel, tipBox);
        return content;
    }

    private Group createCrest() {
        Group crest = new Group();

        // Outer circle
        Circle outer = new Circle(40, 40, 38);
        outer.setFill(Color.TRANSPARENT);
        outer.setStroke(Color.web(GOLD));
        outer.setStrokeWidth(1);
        outer.setOpacity(0.3);

        // Inner circle
        Circle inner = new Circle(40, 40, 32);
        inner.setFill(Color.TRANSPARENT);
        inner.setStroke(Color.web(GOLD));
        inner.setStrokeWidth(0.5);
        inner.setOpacity(0.2);

        // Diamond marks around ring
        Group diamonds = new Group();
        double[][] diamondPositions = {
            {40, 4}, {76, 40}, {40, 76}, {4, 40},
            {63.6, 16.4}, {63.6, 63.6}, {16.4, 63.6}, {16.4, 16.4}
        };
        double[][] diamondOffsets = {
            {0, 4, 0, -4, -4, 0, 4, 0},
            {4, 0, 0, 4, 0, -4, 0, -4},
            {0, -4, 0, 4, 4, 0, -4, 0},
            {-4, 0, 0, -4, 0, 4, 0, 4},
            {1.4, -3.6, -1.4, -0.6, -4, 0, 0, -4},
            {1.4, 3.6, -0.6, -1.4, -4, 0, 0, 4},
            {-3.6, 1.4, 0.6, -1.4, 4, 0, 0, 4},
            {-3.6, -1.4, 0.6, 1.4, 4, 0, 0, -4}
        };

        for (int i = 0; i < diamondPositions.length; i++) {
            double cx = diamondPositions[i][0];
            double cy = diamondPositions[i][1];
            Polygon diamond = new Polygon(
                cx, cy + diamondOffsets[i][1],
                cx + diamondOffsets[i][2], cy,
                cx, cy + diamondOffsets[i][3],
                cx + diamondOffsets[i][0], cy
            );
            diamond.setFill(Color.web(GOLD));
            diamond.setOpacity(0.7);
            diamonds.getChildren().add(diamond);
        }

        // Center mask icon (simplified)
        Group mask = new Group();
        mask.setTranslateX(28);
        mask.setTranslateY(24);

        // Mask outline
        Rectangle maskRect = new Rectangle(4, 0, 16, 20);
        maskRect.setArcWidth(16);
        maskRect.setArcHeight(16);
        maskRect.setFill(Color.TRANSPARENT);
        maskRect.setStroke(Color.web(GOLD));
        maskRect.setStrokeWidth(1.5);
        maskRect.setOpacity(0.8);

        // Eyes
        Rectangle leftEye = new Rectangle(6, 7, 4, 4);
        leftEye.setFill(Color.web(GOLD));
        leftEye.setOpacity(0.7);
        Rectangle rightEye = new Rectangle(14, 7, 4, 4);
        rightEye.setFill(Color.web(GOLD));
        rightEye.setOpacity(0.7);

        // Mouth
        Rectangle mouth = new Rectangle(8, 15, 8, 2);
        mouth.setFill(Color.web(GOLD));
        mouth.setOpacity(0.5);

        mask.getChildren().addAll(maskRect, leftEye, rightEye, mouth);

        crest.getChildren().addAll(outer, inner, diamonds, mask);

        // Spin animation
        RotateTransition spin = new RotateTransition(Duration.seconds(8), crest);
        spin.setByAngle(360);
        spin.setCycleCount(Animation.INDEFINITE);
        spin.play();

        return crest;
    }

    private VBox createLoadingBar() {
        VBox container = new VBox(8);
        container.setMaxWidth(400);
        container.setAlignment(Pos.CENTER);

        // Label row
        HBox labelRow = new HBox();
        labelRow.setAlignment(Pos.CENTER);

        Label initLabel = new Label("Initializing");
        initLabel.setStyle(
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 7px;" +
            "-fx-text-fill: " + TEXT_MUTED + ";"
        );

        percentLabel = new Label("0%");
        percentLabel.setStyle(
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 8px;" +
            "-fx-text-fill: " + GOLD + ";"
        );

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        labelRow.getChildren().addAll(initLabel, spacer, percentLabel);

        // Bar track
        StackPane track = new StackPane();
        track.setPrefHeight(20);
        track.setMaxWidth(400);
        track.setStyle(
            "-fx-background-color: #1a1a22;" +
            "-fx-border-color: #2a2a36;" +
            "-fx-border-width: 1px;"
        );

        // Tick marks
        for (double pos : new double[]{0.25, 0.5, 0.75}) {
            Line tick = new Line(0, 0, 0, 18);
            tick.setStroke(Color.rgb(255, 255, 255, 0.08));
            tick.setTranslateX((pos - 0.5) * 400);
            track.getChildren().add(tick);
        }

        // Bar fill
        barFill = new Rectangle(0, 18);
        barFill.setFill(javafx.scene.paint.LinearGradient.valueOf("linear-gradient(to right, " + GOLD_DARK + ", " + GOLD + ", " + GOLD_LIGHT + ")"));
        StackPane.setAlignment(barFill, Pos.CENTER_LEFT);
        track.getChildren().add(barFill);

        container.getChildren().addAll(labelRow, track);
        return container;
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
        scanlines.setStyle(
            "-fx-background-color: repeating-linear-gradient(to bottom, transparent, transparent 2px, rgba(0,0,0,0.03) 2px, rgba(0,0,0,0.03) 4px);"
        );
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
     * Starts the loading animation and tip rotation.
     */
    public void onEnter() {
        progress = 0;
        statusIndex = 0;
        tipIndex = 0;

        // Loading animation
        loadingAnimation = new Timeline(
            new KeyFrame(Duration.millis(200), e -> {
                progress += Math.random() * 8 + 2;
                if (progress >= 100) {
                    progress = 100;
                    loadingAnimation.stop();
                    statusLabel.setText("Ready!");
                    // Stay on lobby screen - no auto-transition
                }

                barFill.setWidth(4 + (progress / 100.0) * 392); // Account for clip-path effect
                percentLabel.setText((int) progress + "%");

                int newStatusIdx = (int) ((progress / 100.0) * statuses.length);
                if (newStatusIdx != statusIndex && newStatusIdx < statuses.length) {
                    statusIndex = newStatusIdx;
                    statusLabel.setText(statuses[statusIndex]);
                }
            })
        );
        loadingAnimation.setCycleCount(Animation.INDEFINITE);
        loadingAnimation.play();

        // Tip rotation
        tipRotation = new Timeline(
            new KeyFrame(Duration.seconds(4), e -> {
                tipIndex = (tipIndex + 1) % tips.length;
                tipLabel.setText(tips[tipIndex]);
                // Fade animation for tip
                FadeTransition fadeOut = new FadeTransition(Duration.millis(250), tipLabel);
                fadeOut.setFromValue(0);
                fadeOut.setToValue(1);
                fadeOut.play();
            })
        );
        tipRotation.setCycleCount(Animation.INDEFINITE);
        tipRotation.play();
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
