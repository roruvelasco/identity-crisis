package com.identitycrisis.client.scene;

import javafx.scene.Scene;
import javafx.scene.layout.*;
import javafx.scene.control.*;
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

        // Fullscreen button top-right
        addFullscreenButton(root);

        scene = new Scene(root, GameConfig.WINDOW_WIDTH, GameConfig.WINDOW_HEIGHT);
        scene.getStylesheets().add(getClass().getResource("/styles/global.css").toExternalForm());

        return scene;
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
