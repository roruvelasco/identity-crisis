package com.identitycrisis.client.scene;

import com.identitycrisis.client.input.InputManager;
import com.identitycrisis.client.input.InputSnapshot;
import com.identitycrisis.client.render.ArenaRenderer;
import com.identitycrisis.client.render.SpriteManager;
import com.identitycrisis.shared.model.GameConfig;
import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

/**
 * Game arena screen.
 *
 * <p>Renders a dark arena with a gold border and subtle grid lines on a
 * Canvas that fills the window (fullscreen-safe). Runs a local
 * AnimationTimer game loop that:
 * <ol>
 *   <li>Reads keyboard input (WASD / arrow keys) via {@link InputManager}</li>
 *   <li>Moves the player at {@code GameConfig.PLAYER_SPEED} px/sec with
 *       diagonal-normalisation and arena-bounds clamping</li>
 *   <li>Cycles through the correct sprite-sheet frame (idle 4-frame /
 *       walk 6-frame, 8 fps) and flips horizontally when facing left</li>
 * </ol>
 *
 * <p>No server connection is required for this visual demo.
 */
public class GameArena {

    // ── Sprite-sheet constants ───────────────────────────────────────────────
    /** Width/height of one frame in the source PNG (pixels). */
    private static final int    SPRITE_NATIVE  = 32;
    private static final int    IDLE_FRAMES    = 4;
    private static final int    WALK_FRAMES    = 6;
    /** Animation speed: 8 fps → frame advances every 0.125 s. */
    private static final double FRAME_DURATION = 1.0 / 8.0;
    /** 3× upscale: 32 px → 96 px on screen. */
    private static final double SPRITE_SCALE   = 3.0;
    private static final double SPRITE_DISPLAY = SPRITE_NATIVE * SPRITE_SCALE; // 96 px

    // ── Colour palette (shared with other scenes) ────────────────────────────
    private static final String GOLD          = "#c9a84c";
    private static final String GOLD_DARK     = "#8a6a1a";
    private static final String STONE_PANEL   = "#1c1c26";
    private static final String STONE_BORDER  = "#2a2a36";
    private static final String TEXT_MUTED    = "#7a7060";
    private static final String TEXT_PARCHMENT = "#e8dfc4";

    // ── Scene graph ──────────────────────────────────────────────────────────
    private Scene  scene;
    private Canvas canvas;
    private final SceneManager sceneManager;

    // ── Infrastructure ───────────────────────────────────────────────────────
    private SpriteManager  spriteManager;
    private ArenaRenderer  arenaRenderer;
    private InputManager   inputManager;
    private AnimationTimer gameLoop;
    private long           lastNano;

    // ── Player state ─────────────────────────────────────────────────────────
    private double  playerX;
    private double  playerY;
    private int     animFrame;
    private double  animTimer;
    private boolean facingLeft;
    private boolean isMoving;

    // ────────────────────────────────────────────────────────────────────────

    public GameArena(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
    }

    // ── Scene creation (called once, result is cached by SceneManager) ────────

    public Scene createScene() {
        StackPane root = new StackPane();
        root.setStyle("-fx-background-color: black;");
        root.setPrefSize(GameConfig.WINDOW_WIDTH, GameConfig.WINDOW_HEIGHT);

        // Canvas fills the pane so it scales correctly in fullscreen
        canvas = new Canvas(GameConfig.WINDOW_WIDTH, GameConfig.WINDOW_HEIGHT);
        canvas.widthProperty().bind(root.widthProperty());
        canvas.heightProperty().bind(root.heightProperty());
        root.getChildren().add(canvas);

        // HUD overlay buttons (drawn in JavaFX, not on canvas)
        addBackButton(root);
        addFullscreenButton(root);

        scene = new Scene(root, GameConfig.WINDOW_WIDTH, GameConfig.WINDOW_HEIGHT);
        scene.getStylesheets().add(
                getClass().getResource("/styles/global.css").toExternalForm());

        // Load sprites once
        spriteManager = new SpriteManager();
        spriteManager.loadAll();
        arenaRenderer = new ArenaRenderer(spriteManager);

        // InputManager created here; attached/detached in onEnter/onExit
        inputManager = new InputManager();

        return scene;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Called by SceneManager each time this scene becomes active.
     * Resets player to canvas centre and starts the render loop.
     */
    public void onEnter() {
        double w = (canvas != null) ? canvas.getWidth()  : GameConfig.WINDOW_WIDTH;
        double h = (canvas != null) ? canvas.getHeight() : GameConfig.WINDOW_HEIGHT;

        // Reset player
        playerX    = w / 2.0;
        playerY    = h / 2.0;
        animFrame  = 0;
        animTimer  = 0.0;
        facingLeft = false;
        isMoving   = false;
        lastNano   = 0L;

        // Attach keyboard handlers (uses addEventHandler, safe alongside F11)
        if (scene != null && inputManager != null) {
            inputManager.attachToScene(scene);
        }

        stopLoop();
        gameLoop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastNano == 0L) { lastNano = now; return; }
                double dt = (now - lastNano) / 1_000_000_000.0;
                lastNano = now;
                if (dt > 0.05) dt = 0.05; // cap after window pause
                update(dt);
                render();
            }
        };
        gameLoop.start();
    }

    /**
     * Called when leaving this scene (back button or scene switch).
     * Stops the game loop and detaches input handlers.
     */
    public void onExit() {
        stopLoop();
        if (scene != null && inputManager != null) {
            inputManager.detachFromScene(scene);
        }
    }

    // ── Update (game logic) ──────────────────────────────────────────────────

    private void update(double dt) {
        InputSnapshot input = inputManager.snapshot();

        double w      = canvas.getWidth();
        double h      = canvas.getHeight();
        double margin = SPRITE_DISPLAY / 2.0;

        // ── Direction ────────────────────────────────────────────────────────
        double dx = 0, dy = 0;
        if (input.up())    dy -= 1;
        if (input.down())  dy += 1;
        if (input.left())  dx -= 1;
        if (input.right()) dx += 1;

        // Normalise diagonal so diagonal speed == cardinal speed
        if (dx != 0 && dy != 0) {
            double inv = 1.0 / Math.sqrt(2.0);
            dx *= inv;
            dy *= inv;
        }

        isMoving = (dx != 0 || dy != 0);

        if (isMoving) {
            playerX += dx * GameConfig.PLAYER_SPEED * dt;
            playerY += dy * GameConfig.PLAYER_SPEED * dt;

            // Facing: only update when there is horizontal input
            if (dx < 0) facingLeft = true;
            if (dx > 0) facingLeft = false;

            // Clamp within arena bounds (with half-sprite margin)
            playerX = clamp(playerX, margin, w - margin);
            playerY = clamp(playerY, margin, h - margin);
        }

        // ── Animation ────────────────────────────────────────────────────────
        // Reset frame index when animation state changes so we start at frame 0
        animTimer += dt;
        if (animTimer >= FRAME_DURATION) {
            animTimer -= FRAME_DURATION;
            int totalFrames = isMoving ? WALK_FRAMES : IDLE_FRAMES;
            animFrame = (animFrame + 1) % totalFrames;
        }
    }

    // ── Render ───────────────────────────────────────────────────────────────

    private void render() {
        if (canvas == null) return;
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        // 1. Arena: dark bg + grid + gold border
        arenaRenderer.render(gc, w, h);

        // 2. Player sprite
        drawPlayer(gc);
    }

    private void drawPlayer(GraphicsContext gc) {
        String key    = isMoving ? "player_1_walk" : "player_1_idle";
        Image  sheet  = spriteManager.get(key);

        // Clamp frame index defensively
        int maxFrames = isMoving ? WALK_FRAMES : IDLE_FRAMES;
        int frame     = Math.min(animFrame, maxFrames - 1);

        double srcX = frame * SPRITE_NATIVE;      // X offset in sprite sheet
        double drawX = playerX - SPRITE_DISPLAY / 2.0;
        double drawY = playerY - SPRITE_DISPLAY / 2.0;

        if (sheet != null) {
            gc.save();
            if (facingLeft) {
                // Mirror: translate right edge to draw position, then flip X
                gc.translate(drawX + SPRITE_DISPLAY, drawY);
                gc.scale(-1, 1);
                gc.drawImage(sheet,
                        srcX, 0, SPRITE_NATIVE, SPRITE_NATIVE,
                        0, 0, SPRITE_DISPLAY, SPRITE_DISPLAY);
            } else {
                gc.drawImage(sheet,
                        srcX, 0, SPRITE_NATIVE, SPRITE_NATIVE,
                        drawX, drawY, SPRITE_DISPLAY, SPRITE_DISPLAY);
            }
            gc.restore();
        } else {
            // Fallback placeholder: green circle with directional line
            gc.setFill(Color.web("#3E8948"));
            double r = SPRITE_DISPLAY / 2.0;
            gc.fillOval(playerX - r, playerY - r, SPRITE_DISPLAY, SPRITE_DISPLAY);
            gc.setStroke(Color.web("#63C74D"));
            gc.setLineWidth(2);
            double eyeX = facingLeft ? playerX - r * 0.3 : playerX + r * 0.3;
            gc.strokeLine(playerX, playerY, eyeX, playerY - r * 0.4);
        }
    }

    // ── HUD overlay buttons ──────────────────────────────────────────────────

    private void addBackButton(StackPane root) {
        Button btn = new Button("◀  Back");
        btn.setStyle(backStyle(false));
        btn.setOnMouseEntered(e -> btn.setStyle(backStyle(true)));
        btn.setOnMouseExited(e  -> btn.setStyle(backStyle(false)));
        btn.setOnAction(e -> {
            onExit();
            sceneManager.shutdownNetwork();
            sceneManager.showMenu();
        });
        StackPane.setAlignment(btn, Pos.TOP_LEFT);
        StackPane.setMargin(btn, new Insets(20, 0, 0, 20));
        root.getChildren().add(btn);
    }

    private void addFullscreenButton(StackPane root) {
        Button btn = new Button("⛶");
        btn.setPrefSize(32, 32);
        btn.setMinSize(32, 32);
        btn.setMaxSize(32, 32);
        btn.setStyle(fsStyle(false));
        btn.setOnMouseEntered(e -> btn.setStyle(fsStyle(true)));
        btn.setOnMouseExited(e  -> btn.setStyle(fsStyle(false)));
        btn.setOnAction(e -> sceneManager.toggleFullscreen());
        StackPane.setAlignment(btn, Pos.TOP_RIGHT);
        StackPane.setMargin(btn, new Insets(20, 20, 0, 0));
        root.getChildren().add(btn);
    }

    // ── Style helpers ────────────────────────────────────────────────────────

    private String backStyle(boolean hover) {
        return "-fx-font-family: 'Cinzel', serif;" +
               "-fx-font-size: 11px;" +
               "-fx-font-weight: 700;" +
               "-fx-text-fill: " + (hover ? TEXT_PARCHMENT : TEXT_MUTED) + ";" +
               "-fx-letter-spacing: 2px;" +
               "-fx-background-color: transparent;" +
               "-fx-border-color: " + (hover ? GOLD_DARK : STONE_BORDER) + ";" +
               "-fx-border-width: 1px;" +
               "-fx-padding: 7px 14px;" +
               "-fx-cursor: hand;";
    }

    private String fsStyle(boolean hover) {
        return "-fx-font-family: 'Press Start 2P', monospace;" +
               "-fx-font-size: 12px;" +
               "-fx-text-fill: " + GOLD + ";" +
               "-fx-background-color: " + (hover ? "rgba(201,168,76,0.1)" : STONE_PANEL) + ";" +
               "-fx-border-color: " + (hover ? GOLD : GOLD_DARK) + ";" +
               "-fx-border-width: 1px;" +
               "-fx-cursor: hand;";
    }

    // ── Utilities ────────────────────────────────────────────────────────────

    private void stopLoop() {
        if (gameLoop != null) {
            gameLoop.stop();
            gameLoop = null;
        }
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    /** Legacy accessor kept for SceneManager compatibility. */
    public Scene getScene() {
        if (scene == null) scene = createScene();
        return scene;
    }
}
