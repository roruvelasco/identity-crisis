package com.identitycrisis.client.scene;

import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.control.*;
import javafx.scene.paint.*;
import javafx.geometry.*;
import com.identitycrisis.shared.model.GameConfig;

/**
 * Game arena screen - the actual gameplay arena where players compete.
 * Plain black background with centered placeholder text during development.
 */
public class GameArena {

    private Scene scene;
    private SceneManager sceneManager;

    // Color constants
    private static final String GOLD = "#c9a84c";
    private static final String GOLD_DARK = "#8a6a1a";
    private static final String STONE_PANEL = "#1c1c26";
    private static final String STONE_DARK = "#121218";
    private static final String TEXT_PARCHMENT = "#e8dfc4";
    private static final String TEXT_MUTED = "#7a7060";
    private static final String STONE_BORDER = "#2a2a36";

    public GameArena(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
    }

    public Scene createScene() {
        StackPane root = new StackPane();
        // Plain solid black background
        root.setStyle("-fx-background-color: black;");
        root.setPrefSize(GameConfig.WINDOW_WIDTH, GameConfig.WINDOW_HEIGHT);

        // Centered placeholder text
        Label placeholderLabel = new Label("GAME ARENA");
        placeholderLabel.setStyle(
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 16px;" +
            "-fx-text-fill: " + GOLD + ";" +
            "-fx-letter-spacing: 4px;"
        );

        root.getChildren().add(placeholderLabel);
        StackPane.setAlignment(placeholderLabel, Pos.CENTER);

        addBackground(root);

        // Back button top-left
        addBackButton(root);

        // Fullscreen button top-right
        addFullscreenButton(root);

        scene = new Scene(root, GameConfig.WINDOW_WIDTH, GameConfig.WINDOW_HEIGHT);
        scene.getStylesheets().add(getClass().getResource("/styles/global.css").toExternalForm());

        return scene;
    }

    private void addBackground(StackPane root) {
        try {
            Image bgImage = new Image(getClass().getResourceAsStream("/ArenaMap.tmx"));
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
     * Back button - styled exactly like LobbyScene.
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
            // Returning to menu ends the session for this client.
            sceneManager.shutdownNetwork();
            sceneManager.showMenu();
        });

        StackPane.setAlignment(backBtn, Pos.TOP_LEFT);
        StackPane.setMargin(backBtn, new Insets(20, 0, 0, 20));
        root.getChildren().add(backBtn);
    }

    /**
     * Called when entering this scene.
     * Structure preserved for future game initialization.
     */
    public void onEnter() {
        // Future: Initialize game arena, load map, spawn players
    }

    /**
     * Called when leaving this scene.
     * Structure preserved for future cleanup.
     */
    public void onExit() {
        // Future: Cleanup game resources
    }

    // Legacy method for compatibility
    public Scene getScene() {
        if (scene == null) {
            scene = createScene();
        }
        return scene;
    }
}
