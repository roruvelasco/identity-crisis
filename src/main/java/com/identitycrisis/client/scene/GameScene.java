package com.identitycrisis.client.scene;

import javafx.scene.Scene;
import javafx.scene.layout.*;
import javafx.scene.control.*;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.geometry.*;
import javafx.scene.paint.Color;
import javafx.animation.AnimationTimer;
import com.identitycrisis.shared.model.GameConfig;

/**
 * Core gameplay view. Canvas + InputManager + ClientGameLoop.
 * Dark background with game canvas, HUD elements, and fullscreen button.
 */
public class GameScene {

    private Scene scene;
    private Canvas canvas;
    private SceneManager sceneManager;
    private GraphicsContext gc;

    // Color constants
    private static final String GOLD = "#c9a84c";
    private static final String GOLD_DARK = "#8a6a1a";
    private static final String STONE_DARK = "#0a0a0c";
    private static final String STONE_PANEL = "#1c1c26";
    private static final String TEXT_PARCHMENT = "#e8dfc4";

    private AnimationTimer gameLoop;

    public GameScene(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
    }

    public Scene createScene() {
        StackPane root = new StackPane();
        root.setStyle("-fx-background-color: " + STONE_DARK + ";");
        root.setPrefSize(GameConfig.WINDOW_WIDTH, GameConfig.WINDOW_HEIGHT);

        // Game canvas
        canvas = new Canvas(GameConfig.WINDOW_WIDTH, GameConfig.WINDOW_HEIGHT);
        gc = canvas.getGraphicsContext2D();
        root.getChildren().add(canvas);

        // HUD overlay
        addHUD(root);

        // Fullscreen button
        addFullscreenButton(root);

        scene = new Scene(root, GameConfig.WINDOW_WIDTH, GameConfig.WINDOW_HEIGHT);
        scene.getStylesheets().add(getClass().getResource("/styles/global.css").toExternalForm());

        return scene;
    }

    private void addHUD(StackPane root) {
        // Top bar with game info
        HBox hudBar = new HBox(20);
        hudBar.setAlignment(Pos.CENTER_LEFT);
        hudBar.setPadding(new Insets(10, 20, 10, 20));
        hudBar.setStyle("-fx-background-color: rgba(0,0,0,0.5);");
        hudBar.setMaxWidth(GameConfig.WINDOW_WIDTH);
        hudBar.setPrefHeight(40);

        // Round timer
        Label timerLabel = new Label("⏱ 15:00");
        timerLabel.setStyle(
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 10px;" +
            "-fx-text-fill: " + TEXT_PARCHMENT + ";"
        );

        // Player count
        Label playerLabel = new Label("👥 4/8");
        playerLabel.setStyle(
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 10px;" +
            "-fx-text-fill: " + TEXT_PARCHMENT + ";"
        );

        // Round info
        Label roundLabel = new Label("ROUND 1");
        roundLabel.setStyle(
            "-fx-font-family: 'Cinzel', serif;" +
            "-fx-font-size: 14px;" +
            "-fx-font-weight: 700;" +
            "-fx-text-fill: " + GOLD + ";" +
            "-fx-letter-spacing: 3px;"
        );

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Chat button
        Button chatBtn = new Button("💬 Chat");
        chatBtn.setStyle(
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 7px;" +
            "-fx-text-fill: " + GOLD + ";" +
            "-fx-background-color: " + STONE_PANEL + ";" +
            "-fx-border-color: " + GOLD_DARK + ";" +
            "-fx-border-width: 1px;" +
            "-fx-cursor: hand;"
        );

        hudBar.getChildren().addAll(timerLabel, playerLabel, spacer, roundLabel, chatBtn);

        StackPane.setAlignment(hudBar, Pos.TOP_CENTER);
        root.getChildren().add(hudBar);

        // Controls hint at bottom
        Label controlsHint = new Label("WASD: Move  ·  E: Pick Up  ·  Q: Throw  ·  Enter: Chat");
        controlsHint.setStyle(
            "-fx-font-family: 'Cinzel', serif;" +
            "-fx-font-size: 11px;" +
            "-fx-text-fill: rgba(255,255,255,0.5);"
        );
        StackPane.setAlignment(controlsHint, Pos.BOTTOM_CENTER);
        StackPane.setMargin(controlsHint, new Insets(0, 0, 20, 0));
        root.getChildren().add(controlsHint);
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
        StackPane.setMargin(fullscreenBtn, new Insets(60, 20, 0, 0));
        root.getChildren().add(fullscreenBtn);
    }

    /**
     * Attach input, start ClientGameLoop.
     * Called when transitioning to this scene.
     */
    public void onEnter() {
        // Clear canvas
        gc.setFill(Color.web(STONE_DARK));
        gc.fillRect(0, 0, GameConfig.WINDOW_WIDTH, GameConfig.WINDOW_HEIGHT);

        // Start simple game loop for demo
        gameLoop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                render();
            }
        };
        gameLoop.start();

        // Set up input handling
        scene.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case ESCAPE:
                    // Go back to menu
                    onExit();
                    sceneManager.showMenu();
                    break;
                default:
                    // Game input handled by InputManager
                    break;
            }
        });
    }

    /**
     * Stop loop, detach input.
     * Called when leaving this scene.
     */
    public void onExit() {
        if (gameLoop != null) {
            gameLoop.stop();
        }
    }

    private void render() {
        // Clear background
        gc.setFill(Color.web(STONE_DARK));
        gc.fillRect(0, 0, GameConfig.WINDOW_WIDTH, GameConfig.WINDOW_HEIGHT);

        // Draw placeholder arena
        gc.setStroke(Color.web(GOLD_DARK));
        gc.setLineWidth(2);
        gc.strokeRect(50, 100, GameConfig.WINDOW_WIDTH - 100, GameConfig.WINDOW_HEIGHT - 150);

        // Draw placeholder safe zone
        gc.setFill(Color.rgb(74, 140, 92, 0.3));
        gc.setStroke(Color.rgb(74, 140, 92, 0.8));
        gc.fillOval(GameConfig.WINDOW_WIDTH / 2 - 50, GameConfig.WINDOW_HEIGHT / 2 - 50, 100, 100);
        gc.strokeOval(GameConfig.WINDOW_WIDTH / 2 - 50, GameConfig.WINDOW_HEIGHT / 2 - 50, 100, 100);

        // Draw placeholder players
        gc.setFill(Color.web(GOLD));
        gc.fillOval(200, 300, 16, 16);
        gc.fillOval(400, 250, 16, 16);
        gc.fillOval(600, 350, 16, 16);
        gc.fillOval(800, 280, 16, 16);

        // Draw "Game in Progress" text
        gc.setFill(Color.web(TEXT_PARCHMENT));
        gc.setFont(new javafx.scene.text.Font("Cinzel", 24));
        gc.fillText("Game in Progress", GameConfig.WINDOW_WIDTH / 2 - 80, GameConfig.WINDOW_HEIGHT / 2);
    }

    public Canvas getCanvas() {
        return canvas;
    }

    public GraphicsContext getGraphicsContext() {
        return gc;
    }

    // Legacy method for compatibility
    public Scene getScene() {
        if (scene == null) {
            scene = createScene();
        }
        return scene;
    }
}
