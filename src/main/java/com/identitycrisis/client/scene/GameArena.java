package com.identitycrisis.client.scene;

import com.identitycrisis.client.input.InputManager;
import com.identitycrisis.client.input.InputSnapshot;
import com.identitycrisis.client.render.ArenaRenderer;
import com.identitycrisis.client.render.MapManager;
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
import javafx.scene.text.Font;

/**
 * Game arena screen.
 *
 * <p>Renders the full TMX map (scaled to fit the window at all times) and a
 * single local player that:
 * <ol>
 *   <li>Moves with WASD / arrow keys at {@code GameConfig.PLAYER_SPEED} px/sec
 *       in <em>world-pixel</em> coordinates.</li>
 *   <li>Is blocked by wall, water, and void tiles via {@link MapManager#isSolid}.
 *       Axis-separated collision lets the player slide along walls.</li>
 *   <li>Shows a pulsing safe-zone indicator when standing on one of the 8 zones.</li>
 *   <li>Is rendered at the correct screen position for both windowed and fullscreen modes.</li>
 * </ol>
 */
public class GameArena {

    // ── Sprite-sheet constants ───────────────────────────────────────────────
    /** Width/height of one frame in the source PNG (pixels). */
    private static final int    SPRITE_NATIVE  = 32;
    private static final int    IDLE_FRAMES    = 4;
    private static final int    WALK_FRAMES    = 6;
    /** Animation speed: 8 fps → frame advances every 0.125 s. */
    private static final double FRAME_DURATION = 1.0 / 8.0;

    // ── Tight hitbox — ALL values are in WORLD-PIXEL units (same space as playerX/Y) ──────────
    //
    // The tile grid uses TILE_SIZE = 16 world px per tile.  The character sprite is
    // displayed at SPRITE_NATIVE (32 px) = 2 tiles wide, but the actual body art only
    // covers about 6 px of that width and ~10 px of the height.  The centre of the
    // sprite frame is the origin; the body sits in the lower-centre portion.
    //
    // A 1-tile-wide door/corridor is 16 world px. Half-width must stay well below 8 px
    // so the player can squeeze through.  3 px gives comfortable passage while still
    // blocking on solid tiles on either side.
    //
    //   hitbox half-extents (world px)
    private static final double HIT_HALF_W =  3.0;   // body ≈ 6 px wide  (fits 1-tile door)
    private static final double HIT_HALF_H =  5.0;   // body ≈ 10 px tall
    //   hitbox centre offset from the sprite-frame centre (world px)
    private static final double HIT_OFS_X  =  0.0;   // horizontally centred
    private static final double HIT_OFS_Y  =  4.0;   // feet/body in lower half of the 32-px frame

    // ── Colour palette (shared with other scenes) ────────────────────────────
    private static final String GOLD           = "#c9a84c";
    private static final String GOLD_DARK      = "#8a6a1a";
    private static final String STONE_PANEL    = "#1c1c26";
    private static final String STONE_BORDER   = "#2a2a36";
    private static final String TEXT_MUTED     = "#7a7060";
    private static final String TEXT_PARCHMENT = "#e8dfc4";

    // ── Scene graph ──────────────────────────────────────────────────────────
    private Scene  scene;
    private Canvas canvas;
    private final SceneManager sceneManager;

    // ── Infrastructure ───────────────────────────────────────────────────────
    private SpriteManager  spriteManager;
    private ArenaRenderer  arenaRenderer;
    private MapManager     mapManager;
    private InputManager   inputManager;
    private AnimationTimer gameLoop;
    private long           lastNano;

    // ── Player state (world-pixel coordinates, native 16 px/tile scale) ──────
    private double  playerX;
    private double  playerY;
    private int     animFrame;
    private double  animTimer;
    private boolean facingLeft;
    private boolean isMoving;
    /** Safe-zone id (1–8) the player is currently in, or -1. */
    private int     currentZone;
    /** Accumulator for the safe-zone glow pulse animation. */
    private double  pulseTimer;

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

        // ArenaRenderer loads the TMX map internally
        arenaRenderer = new ArenaRenderer(spriteManager);
        mapManager    = arenaRenderer.getMapManager();

        // InputManager created here; attached/detached in onEnter/onExit
        inputManager = new InputManager();

        return scene;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Called by SceneManager each time this scene becomes active.
     * Resets player to the world centre and starts the render loop.
     */
    public void onEnter() {
        // Spawn at centre of the active map content (floor area), not the full grid
        if (mapManager != null && mapManager.getWorldWidth() > 0) {
            playerX = mapManager.getActiveContentCenterX();
            playerY = mapManager.getActiveContentCenterY();
        } else {
            double w = (canvas != null) ? canvas.getWidth()  : GameConfig.WINDOW_WIDTH;
            double h = (canvas != null) ? canvas.getHeight() : GameConfig.WINDOW_HEIGHT;
            playerX = w / 2.0;
            playerY = h / 2.0;
        }

        animFrame  = 0;
        animTimer  = 0.0;
        facingLeft = false;
        isMoving   = false;
        currentZone = -1;
        pulseTimer  = 0.0;
        lastNano    = 0L;

        // Attach keyboard handlers to the permanent scene (the one JavaFX actually delivers events to)
        if (inputManager != null) {
            inputManager.attachToScene(sceneManager.getPermanentScene());
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
        if (inputManager != null) {
            inputManager.detachFromScene(sceneManager.getPermanentScene());
        }
    }

    // ── Update (game logic) ──────────────────────────────────────────────────

    private void update(double dt) {
        InputSnapshot input = inputManager.snapshot();

        // ── Direction ────────────────────────────────────────────────────────
        double dx = 0, dy = 0;
        if (input.up())    dy -= 1;
        if (input.down())  dy += 1;
        if (input.left())  dx -= 1;
        if (input.right()) dx += 1;

        // Normalise diagonal
        if (dx != 0 && dy != 0) {
            double inv = 1.0 / Math.sqrt(2.0);
            dx *= inv;
            dy *= inv;
        }

        isMoving = (dx != 0 || dy != 0);

        if (isMoving) {
            double speed  = GameConfig.PLAYER_SPEED;

            // ── Axis-separated collision (tight hitbox — radius param unused) ─
            // Try X
            double newX = playerX + dx * speed * dt;
            if (!isBlocked(newX, playerY, 0)) {
                playerX = newX;
            }
            // Try Y (independently — allows sliding along walls)
            double newY = playerY + dy * speed * dt;
            if (!isBlocked(playerX, newY, 0)) {
                playerY = newY;
            }

            if (dx < 0) facingLeft = true;
            if (dx > 0) facingLeft = false;
        }

        // ── Animation ────────────────────────────────────────────────────────
        animTimer += dt;
        if (animTimer >= FRAME_DURATION) {
            animTimer -= FRAME_DURATION;
            int totalFrames = isMoving ? WALK_FRAMES : IDLE_FRAMES;
            animFrame = (animFrame + 1) % totalFrames;
        }

        // ── Safe-zone detection ───────────────────────────────────────────────
        currentZone = (mapManager != null) ? mapManager.getSafeZoneAt(playerX, playerY) : -1;
        pulseTimer += dt;
    }

    /**
     * Returns {@code true} if the player's tight hitbox rectangle overlaps any
     * solid tile.  {@code (cx, cy)} is the world-pixel position of the sprite-
     * frame centre (i.e. {@link #playerX} / {@link #playerY}).
     *
     * <p>All HIT_* constants are in <b>world-pixel</b> units — the same coordinate
     * space as {@link #playerX}/{@link #playerY} and {@link MapManager#isSolid}.
     * They must <em>not</em> be multiplied by the screen scale factor (which converts
     * world→screen pixels) because {@code isSolid} works purely in world space.
     *
     * <p>The hitbox is static: it does not change with animation state.
     */
    private boolean isBlocked(double cx, double cy, double radius) {
        if (mapManager == null) return false;
        // Hitbox corners in world-pixel space — no screen-scale multiplication needed.
        double left   = cx + HIT_OFS_X - HIT_HALF_W;
        double right  = cx + HIT_OFS_X + HIT_HALF_W;
        double top    = cy + HIT_OFS_Y - HIT_HALF_H;
        double bottom = cy + HIT_OFS_Y + HIT_HALF_H;
        return mapManager.isSolid(left,  top)
            || mapManager.isSolid(right, top)
            || mapManager.isSolid(left,  bottom)
            || mapManager.isSolid(right, bottom);
    }

    // ── Render ───────────────────────────────────────────────────────────────

    private void render() {
        if (canvas == null) return;
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        // 1. Arena map (tiles, fit-to-window)
        arenaRenderer.render(gc, w, h);

        // 2. Safe-zone indicator
        if (currentZone != -1) {
            drawSafeZoneIndicator(gc, w, h);
        }

        // 3. Player sprite
        drawPlayer(gc, w, h);
    }

    // ── Player rendering ─────────────────────────────────────────────────────

    private void drawPlayer(GraphicsContext gc, double viewW, double viewH) {
        // Convert world-pixel position → screen position
        double screenX, screenY, displaySize;

        if (mapManager != null && mapManager.getWorldWidth() > 0) {
            double scale = mapManager.getScale(viewW, viewH);
            screenX     = mapManager.worldToScreenX(playerX, viewW, viewH);
            screenY     = mapManager.worldToScreenY(playerY, viewW, viewH);
            // Display sprite proportionally: 2 tiles wide at the current map scale
            displaySize = SPRITE_NATIVE * scale;
        } else {
            // Fallback: no map, render at 3× native in screen space
            screenX     = playerX;
            screenY     = playerY;
            displaySize = SPRITE_NATIVE * 3.0;
        }

        String key   = isMoving ? "player_1_walk" : "player_1_idle";
        Image  sheet = spriteManager.get(key);

        int maxFrames = isMoving ? WALK_FRAMES : IDLE_FRAMES;
        int frame     = Math.min(animFrame, maxFrames - 1);
        double srcX   = frame * SPRITE_NATIVE;
        double drawX  = screenX - displaySize / 2.0;
        double drawY  = screenY - displaySize / 2.0;

        if (sheet != null) {
            gc.save();
            if (facingLeft) {
                gc.translate(drawX + displaySize, drawY);
                gc.scale(-1, 1);
                gc.drawImage(sheet,
                        srcX, 0, SPRITE_NATIVE, SPRITE_NATIVE,
                        0, 0, displaySize, displaySize);
            } else {
                gc.drawImage(sheet,
                        srcX, 0, SPRITE_NATIVE, SPRITE_NATIVE,
                        drawX, drawY, displaySize, displaySize);
            }
            gc.restore();
        } else {
            // Fallback circle
            gc.setFill(Color.web("#3E8948"));
            double r = displaySize / 2.0;
            gc.fillOval(screenX - r, screenY - r, displaySize, displaySize);
            gc.setStroke(Color.web("#63C74D"));
            gc.setLineWidth(2);
            double eyeX = facingLeft ? screenX - r * 0.3 : screenX + r * 0.3;
            gc.strokeLine(screenX, screenY, eyeX, screenY - r * 0.4);
        }
    }

    // ── Safe-zone indicator ───────────────────────────────────────────────────

    private void drawSafeZoneIndicator(GraphicsContext gc, double viewW, double viewH) {
        double screenX, screenY;
        if (mapManager != null && mapManager.getWorldWidth() > 0) {
            screenX = mapManager.worldToScreenX(playerX, viewW, viewH);
            screenY = mapManager.worldToScreenY(playerY, viewW, viewH);
        } else {
            screenX = playerX;
            screenY = playerY;
        }

        // Pulsing glow
        double pulse = 0.18 + 0.12 * Math.sin(pulseTimer * 4.0);
        gc.setFill(Color.rgb(74, 140, 92, pulse));
        gc.fillOval(screenX - 44, screenY - 44, 88, 88);

        // Label
        gc.setFill(Color.web("#4a8c5c"));
        try {
            gc.setFont(Font.font("Press Start 2P", 7));
        } catch (Exception ignored) {
            gc.setFont(Font.font(9));
        }
        String label = "◆ SAFE ZONE " + currentZone + " ◆";
        gc.fillText(label, screenX - 38, screenY - 48);
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

    /** Legacy accessor kept for SceneManager compatibility. */
    public Scene getScene() {
        if (scene == null) scene = createScene();
        return scene;
    }
}
