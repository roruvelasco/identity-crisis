package com.identitycrisis.client.scene;

import javafx.scene.Scene;
import javafx.scene.layout.*;
import javafx.scene.control.*;
import javafx.scene.text.*;
import javafx.geometry.*;
import javafx.animation.*;
import javafx.util.Duration;
import javafx.scene.paint.Color;
import javafx.scene.effect.DropShadow;
import com.identitycrisis.shared.model.GameConfig;

/**
 * Winner display screen.
 * Buttons: Play Again, Main Menu, Quit.
 */
public class ResultScene {

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
    private static final String BTN_IDLE = "#3b4663";
    private static final String BTN_BORDER = "#1a2030";

    public ResultScene(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
    }

    public Scene createScene() {
        StackPane root = new StackPane();
        root.setStyle("-fx-background-color: " + STONE_DARK + ";");
        root.setPrefSize(GameConfig.WINDOW_WIDTH, GameConfig.WINDOW_HEIGHT);

        // Stone tile pattern background
        addStonePattern(root);

        // Torch corner glows
        addTorchGlows(root);

        // Main content
        VBox content = createContent();
        root.getChildren().add(content);

        // Scanlines overlay
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

    private void addTorchGlows(StackPane root) {
        // Top-left glow
        Pane tlGlow = new Pane();
        tlGlow.setPrefSize(250, 250);
        tlGlow.setStyle("-fx-background-color: radial-gradient(center 50% 50%, radius 50%, rgba(232,116,60,0.09), transparent 70%);");
        tlGlow.setMouseTransparent(true);
        StackPane.setAlignment(tlGlow, Pos.TOP_LEFT);
        tlGlow.setTranslateX(-50);
        tlGlow.setTranslateY(-50);
        root.getChildren().add(tlGlow);

        // Top-right glow
        Pane trGlow = new Pane();
        trGlow.setPrefSize(250, 250);
        trGlow.setStyle("-fx-background-color: radial-gradient(center 50% 50%, radius 50%, rgba(232,116,60,0.09), transparent 70%);");
        trGlow.setMouseTransparent(true);
        StackPane.setAlignment(trGlow, Pos.TOP_RIGHT);
        trGlow.setTranslateX(50);
        trGlow.setTranslateY(-50);
        root.getChildren().add(trGlow);
    }

    private VBox createContent() {
        VBox content = new VBox(24);
        content.setAlignment(Pos.CENTER);
        content.setMaxWidth(560);
        content.setPadding(new Insets(40, 32, 60, 32));

        // Victory icon (crown)
        Text crown = new Text("👑");
        crown.setStyle("-fx-font-size: 64px;");
        VBox.setMargin(crown, new Insets(0, 0, 20, 0));

        // Winner announcement label
        Label winnerLabel = new Label("WINNER");
        winnerLabel.setStyle(
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 8px;" +
            "-fx-text-fill: " + GOLD + ";" +
            "-fx-letter-spacing: 6px;"
        );
        winnerLabel.setOpacity(0.8);

        // Winner name
        Text winnerName = new Text("Player 1");
        winnerName.setStyle(
            "-fx-font-family: 'Cinzel Decorative', serif;" +
            "-fx-font-size: 48px;" +
            "-fx-font-weight: 700;" +
            "-fx-fill: " + TEXT_PARCHMENT + ";"
        );
        DropShadow shadow = new DropShadow();
        shadow.setColor(Color.rgb(201, 168, 76, 0.6));
        shadow.setRadius(40);
        shadow.setSpread(0.2);
        winnerName.setEffect(shadow);

        // Divider
        HBox divider = new HBox();
        divider.setPrefWidth(280);
        divider.setPrefHeight(1);
        divider.setStyle("-fx-background-color: linear-gradient(to right, transparent, " + GOLD + ", transparent);");
        divider.setAlignment(Pos.CENTER);
        VBox.setMargin(divider, new Insets(20, 0, 20, 0));

        // Stats
        Label statsLabel = new Label("Survived 5 Rounds  ·  3 Eliminations");
        statsLabel.setStyle(
            "-fx-font-family: 'Crimson Pro', serif;" +
            "-fx-font-size: 16px;" +
            "-fx-font-style: italic;" +
            "-fx-text-fill: " + TEXT_MUTED + ";"
        );

        // Buttons
        VBox buttonsBox = createButtons();
        VBox.setMargin(buttonsBox, new Insets(40, 0, 0, 0));

        content.getChildren().addAll(crown, winnerLabel, winnerName, divider, statsLabel, buttonsBox);

        // Entrance animation
        content.setOpacity(0);
        content.setScaleX(0.9);
        content.setScaleY(0.9);

        Timeline entrance = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(content.opacityProperty(), 0),
                new KeyValue(content.scaleXProperty(), 0.9),
                new KeyValue(content.scaleYProperty(), 0.9)),
            new KeyFrame(Duration.seconds(0.8),
                new KeyValue(content.opacityProperty(), 1),
                new KeyValue(content.scaleXProperty(), 1.0),
                new KeyValue(content.scaleYProperty(), 1.0))
        );
        entrance.play();

        return content;
    }

    private VBox createButtons() {
        VBox buttonsBox = new VBox(10);
        buttonsBox.setAlignment(Pos.CENTER);

        // Play Again button (gold variant)
        Button playAgainBtn = createPixelButton("▶  Play Again", true);
        playAgainBtn.setOnAction(e -> onPlayAgainClicked());

        // Main Menu button
        Button menuBtn = createPixelButton("🏠  Main Menu", false);
        menuBtn.setOnAction(e -> onMainMenuClicked());

        // Quit button
        Button quitBtn = createPixelButton("✕  Quit", false);
        quitBtn.setOnAction(e -> onQuitClicked());

        buttonsBox.getChildren().addAll(playAgainBtn, menuBtn, quitBtn);
        return buttonsBox;
    }

    private Button createPixelButton(String text, boolean isGoldVariant) {
        Button btn = new Button(text);
        btn.setPrefSize(240, 52);
        btn.setMinSize(240, 52);
        btn.setMaxSize(240, 52);

        String bgColor = isGoldVariant ? "#4a3a1a" : BTN_IDLE;
        String borderColor = isGoldVariant ? "#1a0e00" : BTN_BORDER;
        String textColor = isGoldVariant ? GOLD_LIGHT : TEXT_PARCHMENT;
        String fontSize = isGoldVariant ? "15px" : "14px";

        btn.setStyle(
            "-fx-font-family: 'Cinzel', serif;" +
            "-fx-font-size: " + fontSize + ";" +
            "-fx-font-weight: 700;" +
            "-fx-text-fill: " + textColor + ";" +
            "-fx-background-color: " + bgColor + ";" +
            "-fx-border-color: " + borderColor + ";" +
            "-fx-border-width: 2px;" +
            "-fx-cursor: hand;"
        );

        // Hover effects
        btn.setOnMouseEntered(e -> {
            String hoverBg = isGoldVariant ? "#5c4920" : "#4a567a";
            btn.setStyle(
                "-fx-font-family: 'Cinzel', serif;" +
                "-fx-font-size: " + fontSize + ";" +
                "-fx-font-weight: 700;" +
                "-fx-text-fill: white;" +
                "-fx-background-color: " + hoverBg + ";" +
                "-fx-border-color: " + borderColor + ";" +
                "-fx-border-width: 2px;" +
                "-fx-cursor: hand;"
            );
        });

        btn.setOnMouseExited(e -> {
            btn.setStyle(
                "-fx-font-family: 'Cinzel', serif;" +
                "-fx-font-size: " + fontSize + ";" +
                "-fx-font-weight: 700;" +
                "-fx-text-fill: " + textColor + ";" +
                "-fx-background-color: " + bgColor + ";" +
                "-fx-border-color: " + borderColor + ";" +
                "-fx-border-width: 2px;" +
                "-fx-cursor: hand;"
            );
        });

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

    private void addScanlines(StackPane root) {
        Pane scanlines = new Pane();
        scanlines.setPrefSize(GameConfig.WINDOW_WIDTH, GameConfig.WINDOW_HEIGHT);
        // Use semi-transparent overlay instead of repeating-linear-gradient (not supported in JavaFX)
        scanlines.setStyle("-fx-background-color: rgba(0,0,0,0.012);");
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

    private void onPlayAgainClicked() {
        sceneManager.showLobby();
    }

    private void onMainMenuClicked() {
        sceneManager.showMenu();
    }

    private void onQuitClicked() {
        javafx.application.Platform.exit();
    }

    // Legacy method for compatibility
    public Scene getScene() {
        if (scene == null) {
            scene = createScene();
        }
        return scene;
    }
}
