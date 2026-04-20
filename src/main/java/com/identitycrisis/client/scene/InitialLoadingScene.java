package com.identitycrisis.client.scene;

import javafx.scene.Scene;
import javafx.scene.layout.*;
import javafx.scene.control.*;
import javafx.geometry.*;
import javafx.animation.*;
import javafx.util.Duration;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.Group;
import com.identitycrisis.shared.model.GameConfig;

/**
 * Initial loading screen shown when the application starts.
 * Preloads assets and initializes resources before showing the main menu.
 */
public class InitialLoadingScene {

    private Scene scene;
    private SceneManager sceneManager;

    // Color constants
    private static final String GOLD = "#c9a84c";
    private static final String GOLD_LIGHT = "#e8c86a";
    private static final String GOLD_DARK = "#8a6a1a";
    private static final String STONE_DARK = "#0d0d10";
    private static final String TEXT_PARCHMENT = "#e8dfc4";
    private static final String TEXT_MUTED = "#7a7060";

    private Label statusLabel;
    private Label percentLabel;
    private Rectangle barFill;
    private int progress = 0;
    private int statusIndex = 0;

    private final String[] statuses = {
        "Initializing game engine...",
        "Loading fonts...",
        "Loading textures...",
        "Preparing audio...",
        "Ready!"
    };

    private Timeline loadingAnimation;

    public InitialLoadingScene(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
    }

    public Scene createScene() {
        StackPane root = new StackPane();
        root.setStyle("-fx-background-color: " + STONE_DARK + ";");
        root.setPrefSize(GameConfig.WINDOW_WIDTH, GameConfig.WINDOW_HEIGHT);

        addBackgroundEffect(root);
        addCornerGlows(root);

        VBox content = createContent();
        root.getChildren().add(content);

        addScanlines(root);

        scene = new Scene(root, GameConfig.WINDOW_WIDTH, GameConfig.WINDOW_HEIGHT);
        scene.getStylesheets().add(getClass().getResource("/styles/global.css").toExternalForm());

        return scene;
    }

    private void addBackgroundEffect(StackPane root) {
        Pane pattern = new Pane();
        pattern.setPrefSize(GameConfig.WINDOW_WIDTH, GameConfig.WINDOW_HEIGHT);
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

        // Crest icon (matching LoadingScene)
        Group logoIcon = createCrest();
        VBox.setMargin(logoIcon, new Insets(0, 0, 20, 0));

        // Title
        Label title = new Label("Identity Crisis");
        title.setStyle(
            "-fx-font-family: 'Cinzel Decorative', serif;" +
            "-fx-font-size: 42px;" +
            "-fx-font-weight: 700;" +
            "-fx-text-fill: " + TEXT_PARCHMENT + ";"
        );
        title.setEffect(new javafx.scene.effect.DropShadow(30, Color.rgb(201, 168, 76, 0.5)));
        VBox.setMargin(title, new Insets(0, 0, 8, 0));

        // Subtitle
        Label subtitle = new Label("Preparing the arena...");
        subtitle.setStyle(
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 8px;" +
            "-fx-text-fill: " + GOLD + ";" +
            "-fx-letter-spacing: 4px;"
        );
        subtitle.setOpacity(0.7);
        VBox.setMargin(subtitle, new Insets(0, 0, 48, 0));

        // Loading bar
        VBox barContainer = createLoadingBar();
        VBox.setMargin(barContainer, new Insets(0, 0, 16, 0));

        // Status label
        statusLabel = new Label(statuses[0]);
        statusLabel.setStyle(
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 8px;" +
            "-fx-text-fill: " + TEXT_MUTED + ";" +
            "-fx-letter-spacing: 1px;"
        );
        VBox.setMargin(statusLabel, new Insets(4, 0, 0, 0));

        content.getChildren().addAll(logoIcon, title, subtitle, barContainer, statusLabel);
        return content;
    }

    private Group createCrest() {
        Group crest = new Group();

        Circle outer = new Circle(40, 40, 38);
        outer.setFill(Color.TRANSPARENT);
        outer.setStroke(Color.web(GOLD));
        outer.setStrokeWidth(1);
        outer.setOpacity(0.3);

        Circle inner = new Circle(40, 40, 32);
        inner.setFill(Color.TRANSPARENT);
        inner.setStroke(Color.web(GOLD));
        inner.setStrokeWidth(0.5);
        inner.setOpacity(0.2);

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

        Group mask = new Group();
        mask.setTranslateX(28);
        mask.setTranslateY(24);

        Rectangle maskRect = new Rectangle(4, 0, 16, 20);
        maskRect.setArcWidth(16);
        maskRect.setArcHeight(16);
        maskRect.setFill(Color.TRANSPARENT);
        maskRect.setStroke(Color.web(GOLD));
        maskRect.setStrokeWidth(1.5);
        maskRect.setOpacity(0.8);

        Rectangle leftEye = new Rectangle(6, 7, 4, 4);
        leftEye.setFill(Color.web(GOLD));
        leftEye.setOpacity(0.7);
        Rectangle rightEye = new Rectangle(14, 7, 4, 4);
        rightEye.setFill(Color.web(GOLD));
        rightEye.setOpacity(0.7);

        Rectangle mouth = new Rectangle(8, 15, 8, 2);
        mouth.setFill(Color.web(GOLD));
        mouth.setOpacity(0.5);

        mask.getChildren().addAll(maskRect, leftEye, rightEye, mouth);
        crest.getChildren().addAll(outer, inner, diamonds, mask);

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

        HBox labelRow = new HBox();
        labelRow.setAlignment(Pos.CENTER);

        Label initLabel = new Label("Loading");
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

        StackPane track = new StackPane();
        track.setPrefHeight(20);
        track.setMaxWidth(400);
        track.setStyle(
            "-fx-background-color: #1a1a22;" +
            "-fx-border-color: #2a2a36;" +
            "-fx-border-width: 1px;"
        );

        barFill = new Rectangle(0, 18);
        barFill.setFill(javafx.scene.paint.LinearGradient.valueOf(
            "linear-gradient(to right, " + GOLD_DARK + ", " + GOLD + ", " + GOLD_LIGHT + ")"));
        StackPane.setAlignment(barFill, Pos.CENTER_LEFT);
        track.getChildren().add(barFill);

        container.getChildren().addAll(labelRow, track);
        return container;
    }

    private void addScanlines(StackPane root) {
        Pane scanlines = new Pane();
        scanlines.setPrefSize(GameConfig.WINDOW_WIDTH, GameConfig.WINDOW_HEIGHT);
        scanlines.setStyle("-fx-background-color: rgba(0,0,0,0.015);");
        scanlines.setMouseTransparent(true);
        root.getChildren().add(scanlines);
    }

    /**
     * Called when entering this scene. Starts the loading animation
     * and transitions to menu when complete.
     */
    public void onEnter() {
        progress = 0;
        statusIndex = 0;

        // Preload critical resources here
        preloadResources();

        loadingAnimation = new Timeline(
            new KeyFrame(Duration.millis(150), e -> {
                progress += Math.random() * 6 + 2;
                if (progress >= 100) {
                    progress = 100;
                    loadingAnimation.stop();
                    statusLabel.setText("Ready!");
                    // Small delay before showing menu
                    PauseTransition pause = new PauseTransition(Duration.millis(300));
                    pause.setOnFinished(event -> sceneManager.showMenu());
                    pause.play();
                }

                barFill.setWidth(4 + (progress / 100.0) * 392);
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
    }

    /**
     * Preload critical resources like fonts and stylesheets
     */
    private void preloadResources() {
        // Force stylesheet loading
        try {
            getClass().getResource("/styles/global.css").toExternalForm();
        } catch (Exception e) {
            // Stylesheet will be loaded when scene is created
        }
    }

    public void onExit() {
        if (loadingAnimation != null) loadingAnimation.stop();
    }

    public Scene getScene() {
        if (scene == null) scene = createScene();
        return scene;
    }
}
