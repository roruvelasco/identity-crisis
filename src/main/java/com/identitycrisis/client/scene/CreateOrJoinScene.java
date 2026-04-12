package com.identitycrisis.client.scene;

import javafx.scene.Scene;
import javafx.scene.layout.*;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.geometry.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.paint.CycleMethod;
import javafx.animation.*;
import javafx.util.Duration;
import com.identitycrisis.shared.model.GameConfig;

/**
 * Create or Join scene - allows players to choose between creating a game or joining one.
 * Play → CreateOrJoin → Lobby → Loading → GameArena flow.
 * Features dungeon background with LobbyScene-style corner glow effects and Press Start 2P typography.
 */
public class CreateOrJoinScene {

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

    public CreateOrJoinScene(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
    }

    public Scene createScene() {
        StackPane root = new StackPane();
        root.setStyle("-fx-background-color: " + STONE_DARK + ";");
        root.setAlignment(Pos.CENTER);

        // Background image with vignette and gradient (like MenuScene)
        addBackground(root);

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
     * Adds dungeon background image with vignette overlay and bottom gradient.
     * Copied from MenuScene implementation.
     */
    private void addBackground(StackPane root) {
        try {
            Image bgImage = new Image(getClass().getResourceAsStream("/dungeon_bg.jpg"));
            ImageView bgView = new ImageView(bgImage);
            // Bind to root size for responsive scaling
            bgView.fitWidthProperty().bind(root.widthProperty());
            bgView.fitHeightProperty().bind(root.heightProperty());
            bgView.setPreserveRatio(false);
            bgView.setStyle("-fx-opacity: 1;");
            root.getChildren().add(bgView);
        } catch (Exception e) {
            // Fallback to dark background
            root.setStyle("-fx-background-color: " + STONE_DARK + ";");
        }

        // Vignette overlay - fill entire root
        StackPane vignette = new StackPane();
        vignette.setStyle("-fx-background-color: radial-gradient(center 50% 50%, radius 100%, transparent 30%, rgba(0,0,0,0.7) 100%);");
        vignette.setMouseTransparent(true);
        // Bind vignette to fill root
        vignette.prefWidthProperty().bind(root.widthProperty());
        vignette.prefHeightProperty().bind(root.heightProperty());
        root.getChildren().add(vignette);

        // Bottom gradient - use percent-based binding
        VBox bottomGradient = new VBox();
        LinearGradient grad = new LinearGradient(0, 1, 0, 0, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.rgb(8, 8, 12, 0.97)),
            new Stop(0.5, Color.rgb(8, 8, 12, 0.8)),
            new Stop(1, Color.TRANSPARENT));
        bottomGradient.setBackground(new Background(new BackgroundFill(grad, null, null)));
        bottomGradient.setMouseTransparent(true);
        // Bind to 55% of root height
        bottomGradient.prefHeightProperty().bind(root.heightProperty().multiply(0.55));
        bottomGradient.prefWidthProperty().bind(root.widthProperty());
        root.getChildren().add(bottomGradient);
        StackPane.setAlignment(bottomGradient, Pos.BOTTOM_CENTER);
    }

    /**
     * Adds animated corner torch glows.
     * Copied from LobbyScene implementation.
     */
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
        backBtn.setOnAction(e -> sceneManager.showMenu());

        StackPane.setAlignment(backBtn, Pos.TOP_LEFT);
        StackPane.setMargin(backBtn, new Insets(20, 0, 0, 20));
        root.getChildren().add(backBtn);
    }

    private VBox createMainContent() {
        VBox content = new VBox(40);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(40, 40, 40, 40));
        content.setMaxWidth(600);

        // Page title: "PLAY" in Press Start 2P, gold
        Label title = new Label("PLAY");
        title.setStyle(
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 32px;" +
            "-fx-text-fill: " + GOLD + ";" +
            "-fx-letter-spacing: 4px;"
        );
        VBox.setMargin(title, new Insets(0, 0, 20, 0));

        // CREATE section card
        VBox createCard = createOptionCard(
            "CREATE",
            "Create Game",
            "Start a new game and invite friends",
            () -> sceneManager.showLobby()
        );

        // JOIN section card
        VBox joinCard = createOptionCard(
            "JOIN",
            "Join Game",
            "Enter a room code to join an existing game",
            () -> sceneManager.showJoinRoom()
        );

        content.getChildren().addAll(title, createCard, joinCard);
        return content;
    }

    private VBox createOptionCard(String sectionLabel, String buttonText, String description, Runnable onAction) {
        VBox card = new VBox(16);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(30, 40, 30, 40));
        card.setMaxWidth(400);
        card.setStyle(
            "-fx-background-color: " + STONE_PANEL + ";" +
            "-fx-border-color: " + GOLD_DARK + ";" +
            "-fx-border-width: 1px;"
        );

        // Section label: CREATE or JOIN in Press Start 2P, gold
        Label label = new Label(sectionLabel);
        label.setStyle(
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 18px;" +
            "-fx-text-fill: " + GOLD + ";" +
            "-fx-letter-spacing: 4px;"
        );

        // Horizontal rule below label
        Region line = new Region();
        line.setPrefWidth(100);
        line.setPrefHeight(1);
        line.setStyle("-fx-background-color: linear-gradient(to right, transparent, " + GOLD + ", transparent);");
        VBox.setMargin(line, new Insets(-8, 0, 0, 0));

        // Description in Press Start 2P
        Label descLabel = new Label(description);
        descLabel.setStyle(
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 8px;" +
            "-fx-text-fill: " + TEXT_MUTED + ";"
        );
        descLabel.setWrapText(true);
        descLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        // Button in Press Start 2P
        Button btn = createStyledButton(buttonText);
        btn.setOnAction(e -> onAction.run());
        VBox.setMargin(btn, new Insets(8, 0, 0, 0));

        card.getChildren().addAll(label, line, descLabel, btn);
        return card;
    }

    private Button createStyledButton(String text) {
        Button btn = new Button(text);
        btn.setPrefSize(220, 48);
        btn.setMinSize(220, 48);
        btn.setMaxSize(220, 48);
        btn.setStyle(
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 10px;" +
            "-fx-text-fill: " + GOLD + ";" +
            "-fx-background-color: #4a3a1a;" +
            "-fx-border-color: #1a0e00;" +
            "-fx-border-width: 2px;" +
            "-fx-cursor: hand;"
        );

        btn.setOnMouseEntered(e -> btn.setStyle(
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 10px;" +
            "-fx-text-fill: " + GOLD_LIGHT + ";" +
            "-fx-background-color: #5c4920;" +
            "-fx-border-color: #1a0e00;" +
            "-fx-border-width: 2px;" +
            "-fx-cursor: hand;"
        ));

        btn.setOnMouseExited(e -> btn.setStyle(
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 10px;" +
            "-fx-text-fill: " + GOLD + ";" +
            "-fx-background-color: #4a3a1a;" +
            "-fx-border-color: #1a0e00;" +
            "-fx-border-width: 2px;" +
            "-fx-cursor: hand;"
        ));

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

    // Legacy method for compatibility
    public Scene getScene() {
        if (scene == null) {
            scene = createScene();
        }
        return scene;
    }
}
