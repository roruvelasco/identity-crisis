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
import javafx.scene.text.TextAlignment;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.layout.VBox;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import java.io.InputStream;

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

    // ── Timer-panel colours (sampled directly from timer_ui.svg) ─────────────
    /** Dark outline / text on the panel face. */
    private static final Color TIMER_DARK    = Color.web("#181425");
    /** Panel face background (used to clear behind text). */
    private static final Color TIMER_FACE    = Color.web("#8B9BB4");
    /** Header strip text colour. */
    private static final Color TIMER_HEADER  = Color.web("#C0CBDC");
    /** Urgent colour when ≤ 10 s remain. */
    private static final Color TIMER_URGENT  = Color.web("#D04648");

    // ── Timer-panel layout (native SVG px → screen at TIMER_SCALE ×) ─────────
    /** Scale factor applied to the 64×32 SVG panel. */
    private static final int    TIMER_SCALE      = 2;
    /** Native panel dimensions (px). */
    private static final int    TIMER_W_NATIVE   = 64;
    private static final int    TIMER_H_NATIVE   = 32;
    /** Panel screen size. */
    private static final double TIMER_W          = TIMER_W_NATIVE * TIMER_SCALE;  // 128
    private static final double TIMER_H          = TIMER_H_NATIVE * TIMER_SCALE;  //  64
    /** Face area in native px (where countdown text lives). */
    private static final double FACE_X_NATIVE    = 10;
    private static final double FACE_Y_NATIVE    = 10;
    private static final double FACE_W_NATIVE    = 44;
    private static final double FACE_H_NATIVE    = 14;
    /** Header strip in native px (where "ROUND N" label lives). */
    private static final double HDR_X_NATIVE     = 10;
    private static final double HDR_Y_NATIVE     =  6;
    private static final double HDR_W_NATIVE     = 44;
    private static final double HDR_H_NATIVE     =  4;
    /** Warm-up round duration (seconds). */
    private static final double TIMER_DURATION   = 45.0;

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

    // ── Round / timer state ───────────────────────────────────────────────────
    /** Current round number (1-based). Rounds 1–2 are timer-based (45 s). */
    private int    roundNumber;
    /** Seconds remaining in the current timer-based round. */
    private double roundTimer;
    /** True while the round countdown is actively ticking. */
    private boolean timerRunning;
    /** The timer_ui.png panel image, loaded once. */
    private Image  timerPanelImage;
    /** Cached "Press Start 2P" font at various sizes for the timer HUD. */
    private Font   fontTimerLabel;
    private Font   fontTimerFace;

    // ── Round start popup state ────────────────────────────────────────────
    /** True while the round start popup is being displayed. */
    private boolean roundPopupActive;
    /** Countdown timer for the round start popup (3.0 → 0.0). */
    private double roundPopupTimer;
    /** The round number being announced (for display consistency). */
    private int popupRoundNumber;
    /** Font for the large round announcement. */
    private Font fontRoundPopup;
    /** Audio player for the 3-second countdown timer sound. */
    private MediaPlayer countdownAudio;
    
    // ── Pause state ──────────────────────────────────────────────────────────
    private boolean isPaused = false;
    private boolean escWasPressed = false;
    private StackPane pauseOverlay;
    private javafx.scene.layout.VBox pauseMenu;
    private javafx.scene.layout.VBox confirmMenu;
    private javafx.scene.control.Label confirmLabel;
    private Runnable confirmAction;

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
        addFullscreenButton(root);

        // Pause Overlay (Initially hidden)
        createPauseOverlay(root);

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

        // Timer panel sprite
        try (InputStream is = getClass().getResourceAsStream("/sprites/ui/toasts/timer_ui.png")) {
            if (is != null) timerPanelImage = new Image(is);
        } catch (Exception ignored) {}

        // Load countdown audio (3 seconds)
        try {
            java.net.URL audioUrl = getClass().getResource("/sprites/ui/3sectimer.mp3");
            if (audioUrl != null) {
                countdownAudio = new MediaPlayer(new Media(audioUrl.toExternalForm()));
                countdownAudio.setVolume(0.8);
            }
        } catch (Exception ignored) {}

        // Pre-build fonts (Press Start 2P — loaded via global.css)
        fontTimerLabel = loadFont("Press Start 2P",  6);   // header strip: "ROUND N"
        fontTimerFace  = loadFont("Press Start 2P", 10);   // face area:   countdown
        fontRoundPopup = loadFont("Press Start 2P", 24);   // round announcement popup

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

        // Start from round 1 with a full 45-second timer
        roundNumber  = 1;
        roundTimer   = TIMER_DURATION;
        timerRunning = true;

        isPaused = false;
        if (pauseOverlay != null) pauseOverlay.setVisible(false);

        // Show round start popup for round 1
        triggerRoundPopup(1);

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
        // Handle ESC key for pausing
        boolean escPressed = inputManager != null && inputManager.isPressed(KeyCode.ESCAPE);
        if (escPressed && !escWasPressed) {
            togglePause();
        }
        escWasPressed = escPressed;

        if (isPaused) return;

        InputSnapshot input = inputManager.snapshot();

        boolean reversed = false;
        if (sceneManager != null && sceneManager.getLocalGameState() != null) {
            reversed = (sceneManager.getLocalGameState().getActiveChaos() == com.identitycrisis.shared.model.ChaosEventType.REVERSED_CONTROLS);
        }
        if (inputManager != null && inputManager.isTestingReversed()) {
            reversed = true;
        }

        if (reversed) {
            input = new InputSnapshot(
                input.down(), input.up(), input.right(), input.left(),
                input.carry(), input.throwAction(), input.chatToggle()
            );
        }


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

        // ── Round timer (only ticks during warm-up rounds 1–2) ───────────────
        if (timerRunning && roundNumber <= GameConfig.WARMUP_ROUNDS) {
            roundTimer -= dt;
            if (roundTimer <= 0) {
                roundTimer   = 0;
                timerRunning = false;
                // Advance to next round and restart timer if still a warm-up round
                roundNumber++;
                // Show round start popup for the new round
                triggerRoundPopup(roundNumber);
                if (roundNumber <= GameConfig.WARMUP_ROUNDS) {
                    roundTimer   = TIMER_DURATION;
                    timerRunning = true;
                }
            }
        }

        // ── Round start popup countdown ─────────────────────────────────────
        if (roundPopupActive) {
            roundPopupTimer -= dt;
            if (roundPopupTimer <= 0) {
                roundPopupActive = false;
                roundPopupTimer = 0;
            }
        }
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

        // 4. Round timer HUD (top-centre)
        drawTimerHud(gc, w, h);

        // 5. Round start popup overlay (center)
        drawRoundPopup(gc, w, h);
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

    // ── Timer HUD ────────────────────────────────────────────────────────────

    /**
     * Draws the pixel-art timer panel (timer_ui.png, scaled 2×) centred at the
     * top of the screen.  Only visible during timer-based rounds (1–2).
     * Hidden entirely for round 3+ (safe-zone-based rounds).
     */
    private void drawTimerHud(GraphicsContext gc, double viewW, double viewH) {
        // Only shown during warm-up timer rounds
        if (roundNumber > GameConfig.WARMUP_ROUNDS) return;
        // Panel top-left position — centred horizontally, 16 px from top
        double panelX = Math.round((viewW - TIMER_W) / 2.0);
        double panelY = 16;

        // ── Background panel ─────────────────────────────────────────────────
        if (timerPanelImage != null) {
            gc.drawImage(timerPanelImage, panelX, panelY, TIMER_W, TIMER_H);
        } else {
            // Fallback: hand-draw the panel using SVG colours
            drawFallbackPanel(gc, panelX, panelY);
        }


        // ── Header strip — "ROUND N" ──────────────────────────────────────────
        double hdrCX = panelX + (HDR_X_NATIVE + HDR_W_NATIVE / 2.0) * TIMER_SCALE;
        double hdrCY = panelY + (HDR_Y_NATIVE + HDR_H_NATIVE / 2.0) * TIMER_SCALE + 4;
        gc.save();
        gc.setFont(fontTimerLabel);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(TIMER_HEADER);
        gc.fillText("ROUND " + roundNumber, hdrCX, hdrCY);
        gc.restore();

        // ── Face area — countdown ───────────────────────────────────────────────
        double faceCX = panelX + (FACE_X_NATIVE + FACE_W_NATIVE / 2.0) * TIMER_SCALE;
        double faceCY = panelY + (FACE_Y_NATIVE + FACE_H_NATIVE / 2.0) * TIMER_SCALE + 5;

        gc.save();
        gc.setFont(fontTimerFace);
        gc.setTextAlign(TextAlignment.CENTER);
        // Countdown: urgent red in the last 10 seconds, dark otherwise
        boolean urgent = (roundTimer <= 10.0);
        if (urgent) {
            // Subtle flicker on the text every half-second
            double flicker = Math.sin(roundTimer * Math.PI * 2) > 0 ? 1.0 : 0.75;
            gc.setGlobalAlpha(flicker);
        }
        gc.setFill(urgent ? TIMER_URGENT : TIMER_DARK);
        gc.fillText(formatTime(roundTimer), faceCX, faceCY);
        gc.restore();
    }

    /**
     * Formats a time value as {@code M:SS} (e.g. 45 → "0:45", 9 → "0:09").
     * Clamps to zero so no negative values are displayed.
     */
    private String formatTime(double seconds) {
        int total = Math.max(0, (int) Math.ceil(seconds));
        int m = total / 60;
        int s = total % 60;
        return m + ":" + String.format("%02d", s);
    }

    /**
     * Triggers the round start popup with a 3-2-1 countdown.
     * @param round the round number to announce
     */
    private void triggerRoundPopup(int round) {
        popupRoundNumber = round;
        roundPopupTimer = GameConfig.COUNTDOWN_SECONDS; // 3 seconds - matches audio length
        roundPopupActive = true;

        // Play countdown audio
        if (countdownAudio != null) {
            countdownAudio.seek(javafx.util.Duration.ZERO);
            countdownAudio.play();
        }
    }

    /**
     * Draws an aesthetic round start popup at the center of the screen.
     * Shows "ROUND X" with a pulsing countdown (3... 2... 1...).
     */
    private void drawRoundPopup(GraphicsContext gc, double viewW, double viewH) {
        if (!roundPopupActive) return;

        double centerX = viewW / 2.0;
        double centerY = viewH / 2.0;

        // Popup dimensions
        double popupW = 400;
        double popupH = 180;
        double popupX = centerX - popupW / 2.0;
        double popupY = centerY - popupH / 2.0;

        // Calculate countdown number (3, 2, 1, or GO!)
        int countdownValue = (int) Math.ceil(roundPopupTimer);
        String countdownText = countdownValue > 0 ? String.valueOf(countdownValue) : "GO!";

        // Pulsing scale effect for countdown
        double pulse = 1.0 + 0.15 * Math.sin(roundPopupTimer * Math.PI * 4);
        double countdownScale = pulse;

        // ── Semi-transparent overlay ──────────────────────────────────────────
        gc.setFill(Color.rgb(0, 0, 0, 0.4));
        gc.fillRect(0, 0, viewW, viewH);

        // ── Main panel with stone border aesthetic ────────────────────────────
        // Main panel background
        gc.setFill(Color.web("#1c1c26")); // STONE_PANEL
        gc.fillRoundRect(popupX, popupY, popupW, popupH, 12, 12);

        // Inner border
        gc.setStroke(Color.web("#c9a84c")); // GOLD
        gc.setLineWidth(3);
        gc.strokeRoundRect(popupX + 4, popupY + 4, popupW - 8, popupH - 8, 8, 8);

        // ── "ROUND X" header ────────────────────────────────────────────────
        gc.save();
        gc.setFont(fontRoundPopup);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.web("#e8dfc4")); // TEXT_PARCHMENT
        gc.fillText("ROUND " + popupRoundNumber, centerX, popupY + 55);
        gc.restore();

        // ── Decorative line ───────────────────────────────────────────────────
        gc.setStroke(Color.web("#8a6a1a")); // GOLD_DARK
        gc.setLineWidth(2);
        gc.strokeLine(centerX - 80, popupY + 70, centerX + 80, popupY + 70);

        // ── "starts in" subtitle ────────────────────────────────────────────
        gc.save();
        gc.setFont(loadFont("Press Start 2P", 12));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.web("#7a7060")); // TEXT_MUTED
        gc.fillText("starts in", centerX, popupY + 95);
        gc.restore();

        // ── Countdown number with pulse ───────────────────────────────────────
        gc.save();
        gc.setFont(Font.font("Press Start 2P", 48 * countdownScale));
        gc.setTextAlign(TextAlignment.CENTER);

        // Color based on countdown: 3=green, 2=yellow, 1=red, GO!=gold
        Color countdownColor;
        if (countdownValue == 3) countdownColor = Color.web("#4a8c5c");
        else if (countdownValue == 2) countdownColor = Color.web("#d4a017");
        else if (countdownValue == 1) countdownColor = Color.web("#d04648");
        else countdownColor = Color.web("#c9a84c");

        gc.setFill(countdownColor);
        gc.fillText(countdownText, centerX, popupY + 145);
        gc.restore();
    }

    /**
     * Fallback panel drawn entirely via {@link GraphicsContext} rectangles,
     * matching the timer_ui.svg geometry at {@code TIMER_SCALE} ×.
     * Used only if timer_ui.png fails to load.
     */
    private void drawFallbackPanel(GraphicsContext gc, double ox, double oy) {
        double s = TIMER_SCALE;
        gc.save();
        // Outer dark border
        gc.setFill(Color.web("#181425")); gc.fillRect(ox, oy, 64*s, 2*s);
        gc.fillRect(ox, oy+2*s, 2*s, 30*s);
        gc.fillRect(ox+62*s, oy+2*s, 2*s, 30*s);
        gc.fillRect(ox+2*s, oy+30*s, 60*s, 2*s);
        // Panel face
        gc.setFill(Color.web("#8B9BB4")); gc.fillRect(ox+4*s, oy+4*s, 56*s, 24*s);
        // Top highlight
        gc.setFill(Color.web("#FFFFFF")); gc.fillRect(ox+4*s, oy+4*s, 56*s, 2*s);
        // Side shadows
        gc.setFill(Color.web("#8B9BB4")); gc.fillRect(ox+2*s, oy+4*s, 2*s, 24*s);
        gc.fillRect(ox+60*s, oy+4*s, 2*s, 24*s);
        // Header strip
        gc.setFill(Color.web("#5A6988")); gc.fillRect(ox+10*s, oy+6*s, 44*s, 4*s);
        gc.setFill(Color.web("#3A4466")); gc.fillRect(ox+8*s,  oy+6*s,  2*s, 4*s);
        gc.fillRect(ox+54*s, oy+6*s, 2*s, 4*s);
        // Bottom strip
        gc.setFill(Color.web("#C0CBDC")); gc.fillRect(ox+8*s, oy+24*s, 48*s, 4*s);
        gc.restore();
    }

    /**
     * Loads a {@link Font} by family name, falling back to the system default
     * if the named font is not available on this JVM.
     */
    private Font loadFont(String family, double size) {
        Font f = Font.font(family, size);
        // Font.font returns a fallback silently; check the name to detect it
        return f;
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

    private void createPauseOverlay(StackPane root) {
        pauseOverlay = new StackPane();
        pauseOverlay.setVisible(false);
        pauseOverlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.4);"); // Subtle dimming

        // ── Main Box (Small panel in the center) ─────────────────────────────
        VBox box = new VBox();
        box.setAlignment(Pos.CENTER);
        box.setMaxSize(360, 280);
        box.setMinSize(360, 280);
        box.setStyle("-fx-background-color: #1c1c26; " +
                     "-fx-border-color: #3a3a50; " +
                     "-fx-border-width: 3px; " +
                     "-fx-effect: dropshadow(gaussian, black, 20, 0, 0, 0);");

        // Use a StackPane to swap menus in the same spot
        StackPane menuContainer = new StackPane();
        menuContainer.setAlignment(Pos.CENTER);

        // ── Pause Menu ───────────────────────────────────────────────────────
        pauseMenu = new VBox(20);
        pauseMenu.setAlignment(Pos.CENTER);

        Label pausedLabel = new Label("PAUSED");
        pausedLabel.setStyle("-fx-font-family: 'Press Start 2P'; -fx-font-size: 18px; -fx-text-fill: #e8dfc4;");

        Button resumeBtn = createMenuButton("RESUME");
        resumeBtn.setOnAction(e -> togglePause());

        Button menuBtn = createMenuButton("MAIN MENU");
        menuBtn.setOnAction(e -> showConfirmMenu("RETURN TO MENU?", () -> {
            onExit();
            sceneManager.shutdownNetwork();
            sceneManager.showMenu();
        }));

        Button quitBtn = createMenuButton("QUIT GAME");
        quitBtn.setOnAction(e -> showConfirmMenu("QUIT TO DESKTOP?", () -> {
            javafx.application.Platform.exit();
            System.exit(0);
        }));

        pauseMenu.getChildren().addAll(pausedLabel, resumeBtn, menuBtn, quitBtn);

        // ── Confirmation Menu ────────────────────────────────────────────────
        confirmMenu = new VBox(25);
        confirmMenu.setAlignment(Pos.CENTER);
        confirmMenu.setVisible(false);

        confirmLabel = new Label("ARE YOU SURE?");
        confirmLabel.setStyle("-fx-font-family: 'Press Start 2P'; -fx-font-size: 11px; -fx-text-fill: #e8dfc4;");
        confirmLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        confirmLabel.setAlignment(Pos.CENTER);
        confirmLabel.setMaxWidth(300);
        confirmLabel.setWrapText(true);

        javafx.scene.layout.HBox confirmButtons = new javafx.scene.layout.HBox(20);
        confirmButtons.setAlignment(Pos.CENTER);

        Button yesBtn = createMenuButton("YES");
        yesBtn.setMinWidth(110);
        yesBtn.setOnAction(e -> {
            if (confirmAction != null) confirmAction.run();
        });

        Button noBtn = createMenuButton("NO");
        noBtn.setMinWidth(110);
        noBtn.setOnAction(e -> showPauseMenu());

        confirmButtons.getChildren().addAll(yesBtn, noBtn);
        confirmMenu.getChildren().addAll(confirmLabel, confirmButtons);

        menuContainer.getChildren().addAll(pauseMenu, confirmMenu);
        box.getChildren().add(menuContainer);
        pauseOverlay.getChildren().add(box);
        root.getChildren().add(pauseOverlay);
    }

    private Button createMenuButton(String text) {
        Button btn = new Button(text);
        btn.setMinWidth(220);
        btn.setStyle(pauseButtonStyle(false));
        btn.setOnMouseEntered(e -> btn.setStyle(pauseButtonStyle(true)));
        btn.setOnMouseExited(e -> btn.setStyle(pauseButtonStyle(false)));
        return btn;
    }

    private String pauseButtonStyle(boolean hover) {
        return "-fx-font-family: 'Press Start 2P', monospace;" +
               "-fx-font-size: 10px;" +
               "-fx-text-fill: " + (hover ? "#ffffff" : "#b0a890") + ";" +
               "-fx-background-color: " + (hover ? "#3a3a50" : "#252535") + ";" +
               "-fx-border-color: " + (hover ? "#e8dfc4" : "#3a3a50") + ";" +
               "-fx-border-width: 2px;" +
               "-fx-padding: 12px 15px;" +
               "-fx-cursor: hand;" +
               "-fx-background-radius: 0;" +
               "-fx-border-radius: 0;";
    }

    private void togglePause() {
        isPaused = !isPaused;
        pauseOverlay.setVisible(isPaused);
        if (isPaused) {
            showPauseMenu();
        }
    }

    private void showPauseMenu() {
        pauseMenu.setVisible(true);
        confirmMenu.setVisible(false);
    }

    private void showConfirmMenu(String question, Runnable action) {
        confirmLabel.setText(question);
        confirmAction = action;
        pauseMenu.setVisible(false);
        confirmMenu.setVisible(true);
    }

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
