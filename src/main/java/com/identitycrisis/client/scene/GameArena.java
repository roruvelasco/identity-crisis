package com.identitycrisis.client.scene;

import com.identitycrisis.client.input.InputManager;
import com.identitycrisis.client.input.InputSnapshot;
import com.identitycrisis.client.render.ArenaRenderer;
import com.identitycrisis.client.render.MapManager;
import com.identitycrisis.client.render.SpriteManager;
import com.identitycrisis.shared.model.ChaosEventType;
import com.identitycrisis.shared.model.GameConfig;
import com.identitycrisis.shared.model.Player;
import com.identitycrisis.shared.model.PlayerState;
import com.identitycrisis.client.net.GameClient;
import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
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
 * Renders the full TMX map (scaled to fit the window at all times) and a
 * single local player that:
 * Moves with WASD / arrow keys at {@code GameConfig.PLAYER_SPEED} px/sec
 * in world-pixel coordinates.
 * Is blocked by wall, water, and void tiles via {@link MapManager#isSolid}.
 * Axis-separated collision lets the player slide along walls.
 * Shows a pulsing safe-zone indicator when standing on one of the 8 zones.
 * Is rendered at the correct screen position for both windowed and fullscreen
 * modes.
 */
public class GameArena {

    // ── Sprite-sheet constants ───────────────────────────────────────────────
    /** Width/height of one frame in the source PNG (pixels). */
    private static final int SPRITE_NATIVE = 32;
    private static final int IDLE_FRAMES = 4;
    private static final int WALK_FRAMES = 6;
    /** Animation speed: 8 fps → frame advances every 0.125 s. */
    private static final double FRAME_DURATION = 1.0 / 8.0;

    // ── Tight hitbox — ALL values are in WORLD-PIXEL units (same space as
    // playerX/Y) ──────────
    //
    // The tile grid uses TILE_SIZE = 16 world px per tile. The character sprite is
    // displayed at SPRITE_NATIVE (32 px) = 2 tiles wide, but the actual body art
    // only
    // covers about 6 px of that width and ~10 px of the height. The centre of the
    // sprite frame is the origin; the body sits in the lower-centre portion.
    //
    // A 1-tile-wide door/corridor is 16 world px. Half-width must stay well below 8
    // px
    // so the player can squeeze through. 3 px gives comfortable passage while still
    // blocking on solid tiles on either side.
    //
    // hitbox half-extents (world px)
    private static final double HIT_HALF_W = 3.0; // body ≈ 6 px wide (fits 1-tile door)
    private static final double HIT_HALF_H = 5.0; // body ≈ 10 px tall
    // hitbox centre offset from the sprite-frame centre (world px)
    private static final double HIT_OFS_X = 0.0; // horizontally centred
    private static final double HIT_OFS_Y = 4.0; // feet/body in lower half of the 32-px frame

    // ── Colour palette (shared with other scenes) ────────────────────────────
    private static final String GOLD = "#c9a84c";
    private static final String GOLD_DARK = "#8a6a1a";
    private static final String STONE_PANEL = "#1c1c26";
    private static final String STONE_BORDER = "#2a2a36";
    private static final String TEXT_MUTED = "#7a7060";
    private static final String TEXT_PARCHMENT = "#e8dfc4";

    // ── Timer-panel colours (sampled directly from timer_ui.svg) ─────────────
    /** Dark outline / text on the panel face. */
    private static final Color TIMER_DARK = Color.web("#181425");
    /** Panel face background (used to clear behind text). */
    private static final Color TIMER_FACE = Color.web("#8B9BB4");
    /** Header strip text colour. */
    private static final Color TIMER_HEADER = Color.web("#C0CBDC");
    /** Urgent colour when ≤ 10 s remain. */
    private static final Color TIMER_URGENT = Color.web("#D04648");

    // ── Timer-panel layout (native SVG px → screen at TIMER_SCALE ×) ─────────
    /** Scale factor applied to the 64×32 SVG panel. */
    private static final int TIMER_SCALE = 2;
    /** Native panel dimensions (px). */
    private static final int TIMER_W_NATIVE = 64;
    private static final int TIMER_H_NATIVE = 32;
    /** Panel screen size. */
    private static final double TIMER_W = TIMER_W_NATIVE * TIMER_SCALE; // 128
    private static final double TIMER_H = TIMER_H_NATIVE * TIMER_SCALE; // 64
    /** Face area in native px (where countdown text lives). */
    private static final double FACE_X_NATIVE = 10;
    private static final double FACE_Y_NATIVE = 10;
    private static final double FACE_W_NATIVE = 44;
    private static final double FACE_H_NATIVE = 14;
    /** Header strip in native px (where "ROUND N" label lives). */
    private static final double HDR_X_NATIVE = 10;
    private static final double HDR_Y_NATIVE = 6;
    private static final double HDR_W_NATIVE = 44;
    private static final double HDR_H_NATIVE = 4;
    /** Warm-up round duration (seconds). */
    private static final double TIMER_DURATION = 25.0;
    private static final boolean LOCAL_CHAOS_ENABLED = Boolean.getBoolean("identitycrisis.localChaos");
    private static final ChaosEventType[] LOCAL_CHAOS_EVENTS = {
            ChaosEventType.REVERSED_CONTROLS,
            ChaosEventType.FAKE_SAFE_ZONES
    };

    // ── Scene graph ──────────────────────────────────────────────────────────
    private Scene scene;
    private Canvas canvas;
    private final SceneManager sceneManager;

    // ── Infrastructure ───────────────────────────────────────────────────────
    private SpriteManager spriteManager;
    private ArenaRenderer arenaRenderer;
    private MapManager mapManager;
    private InputManager inputManager;
    private AnimationTimer gameLoop;
    private long lastNano;

    // ── Player state (world-pixel coordinates, native 16 px/tile scale) ──────
    private double playerX;
    private double playerY;
    private int animFrame;
    private double animTimer;
    private boolean facingLeft;
    private boolean isMoving;
    /** Safe-zone id (1–8) the player is currently in, or -1. */
    private int currentZone;
    /** Accumulator for the safe-zone glow pulse animation. */
    private double pulseTimer;

    // ── Round / timer state ───────────────────────────────────────────────────
    //
    // The server is authoritative for {@link #roundNumber}, {@link #roundTimer},
    // and the round phase whenever the client is connected. These fields then
    // act as a render-only cache populated each frame from
    // {@code LocalGameState}. When no server snapshot has yet been received
    // (pure offline / dev mode) the same fields are driven by the local
    // {@link #lastNano} delta so the scene still works without networking.

    /** Current round number (1-based). Rounds 1–2 are timer-based (45 s). */
    private int roundNumber;
    /** Seconds remaining in the current timer-based round. */
    private double roundTimer;
    /**
     * True while the round countdown is actively ticking (offline fallback only).
     */
    private boolean timerRunning;
    /**
     * Previous server-observed round number — used to detect a round transition
     * and trigger the {@link #triggerRoundPopup(int)} animation. {@code 0}
     * means we have not yet seen a server snapshot.
     */
    private int lastObservedServerRound;


    private int offlineActiveZoneId = -1;
    /** {@link #roundNumber} at the moment the current offline zone was picked. */
    private int offlineActiveZoneRound = -1;
    /**
     * RNG dedicated to offline zone selection — separate from any other randomness
     * in the scene.
     */
    private final java.util.Random offlineZoneRng = new java.util.Random();
    /** The timer_ui.png panel image, loaded once. */
    private Image timerPanelImage;
    /** Cached "Press Start 2P" font at various sizes for the timer HUD. */
    private Font fontTimerLabel;
    private Font fontTimerFace;

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
    private StackPane gameOverOverlay;
    private Label gameOverWinnerLabel;
    private boolean gameOverShown;

    // ── Fake safe-zones chaos state ───────────────────────────────────────────
    //
    // Local debug toggle (key O) that mirrors the server-side FAKE_SAFE_ZONES
    // chaos event. When active:
    // • Every TMX safe-zone rectangle is rendered.
    // • Exactly one is the TRUE zone (fakeZoneTrueId); the rest are DECOYS.
    // • True zone = normal green indicator; decoys = red/orange indicator.
    // • A HUD banner announces the chaos event.
    //
    /** True while the local FAKE_SAFE_ZONES chaos test is active. */
    private boolean testingFakeZones = false;
    /** All zones to display during the chaos event (true + decoys). */
    private java.util.List<com.identitycrisis.shared.model.SafeZone> fakeZoneList = new java.util.ArrayList<>();
    /** Id of the ONE true safe zone inside fakeZoneList. */
    private int fakeZoneTrueId = -1;
    /** RNG for decoy zone selection. */
    private final java.util.Random fakeZoneRng = new java.util.Random();

    // ── Reversed-controls chaos state ─────────────────────────────────────────
    /** True this frame when REVERSED_CONTROLS chaos is active (local or server). */
    private boolean reversedControlsActive = false;
    private ChaosEventType localChaosEvent = ChaosEventType.NONE;
    private double localChaosTimer = 0.0;
    private final java.util.Random localChaosRng = new java.util.Random();

    // ── Chaos toast state ──────────────────────────────────────────────────────
    // A 288×96 px pixel-art toast (3× the 96×32 PNG) slides in from the
    // top-right for 3 seconds whenever a new chaos event activates.
    // Images are loaded from /sprites/ui/toasts/*.png in createScene().
    //
    /** Toast images indexed by {@code ChaosEventType.ordinal() - 1} (NONE has no toast). */
    private Image[] toastImages;
    /** Font pre-loaded for the toast label. */
    private Font fontToast;
    /** Last effective chaos type seen — used for rising-edge detection. */
    private com.identitycrisis.shared.model.ChaosEventType lastToastChaos =
            com.identitycrisis.shared.model.ChaosEventType.NONE;
    /** True while the toast is visible. */
    private boolean toastActive = false;
    /** Counts down from 3.0 → 0.0 while the toast is visible. */
    private double  toastTimer  = 0.0;
    /** Which event the current toast is announcing. */
    private com.identitycrisis.shared.model.ChaosEventType toastEventType =
            com.identitycrisis.shared.model.ChaosEventType.NONE;

    /** Per-remote-player animation state (keyed by playerId). */
    private final java.util.Map<Integer, RemotePlayerAnim> remoteAnims = new java.util.HashMap<>();

    // ────────────────────────────────────────────────────────────────────────

    private static class RemotePlayerAnim {
        int animFrame = 0;
        double animTimer = 0;
        boolean facingLeft = false;
        boolean isMoving = false;
        double lastX, lastY;
        boolean hasLastPosition = false;
    }

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
        createGameOverOverlay(root);

        scene = new Scene(root, GameConfig.WINDOW_WIDTH, GameConfig.WINDOW_HEIGHT);
        scene.getStylesheets().add(
                getClass().getResource("/styles/global.css").toExternalForm());

        // Load sprites once
        spriteManager = new SpriteManager();
        spriteManager.loadAll();

        // ArenaRenderer loads the TMX map internally
        arenaRenderer = new ArenaRenderer(spriteManager);
        mapManager = arenaRenderer.getMapManager();

        // InputManager created here; attached/detached in onEnter/onExit
        inputManager = new InputManager();

        // Timer panel sprite
        try (InputStream is = getClass().getResourceAsStream("/sprites/ui/toasts/timer_ui.png")) {
            if (is != null)
                timerPanelImage = new Image(is);
        } catch (Exception ignored) {
        }

        // Pre-load the 3 chaos-event toast PNGs (96×32 px each, indexed by
        // ChaosEventType.ordinal()-1: REVERSED_CONTROLS=0, CONTROL_SWAP=1, FAKE_SAFE_ZONES=2).
        String[] toastPaths = {
            "/sprites/ui/toasts/toast_reverseControls.png",
            "/sprites/ui/toasts/toast_swapLocation.png",
            "/sprites/ui/toasts/toast_fakeSafezone.png"
        };
        toastImages = new Image[toastPaths.length];
        for (int i = 0; i < toastPaths.length; i++) {
            try (InputStream ts = getClass().getResourceAsStream(toastPaths[i])) {
                if (ts != null) toastImages[i] = new Image(ts);
            } catch (Exception e) {
                System.err.println("[GameArena] Failed to load toast image: " + toastPaths[i]);
            }
        }
        fontToast = loadFont("Press Start 2P", 7);

        // Load countdown audio (3 seconds)
        try {
            java.net.URL audioUrl = getClass().getResource("/sprites/ui/3sectimer.wav");
            if (audioUrl != null) {
                countdownAudio = new MediaPlayer(new Media(audioUrl.toExternalForm()));
                countdownAudio.setVolume(0.8);
            }
        } catch (Exception ignored) {
        }

        // Pre-build fonts (Press Start 2P — loaded via global.css)
        fontTimerLabel = loadFont("Press Start 2P", 6); // header strip: "ROUND N"
        fontTimerFace = loadFont("Press Start 2P", 10); // face area: countdown
        fontRoundPopup = loadFont("Press Start 2P", 24); // round announcement popup

        return scene;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Called by SceneManager each time this scene becomes active.
     * Resets player to the world centre and starts the render loop.
     */
    public void onEnter() {
        // ── Reset networked state for a clean game session ──────────────────
        // Clears stale game-over / elimination data so a second game in the
        // same session doesn't show leftover results from the previous one.
        GameClient existingClient = (sceneManager != null) ? sceneManager.getGameClient() : null;
        boolean networkedSession = existingClient != null && existingClient.isConnected();
        if (!networkedSession && sceneManager != null && sceneManager.getLocalGameState() != null) {
            sceneManager.getLocalGameState().resetForNewGame();
        }

        // Spawn at centre of the active map content (floor area), not the full grid
        if (mapManager != null && mapManager.getWorldWidth() > 0) {
            playerX = mapManager.getActiveContentCenterX();
            playerY = mapManager.getActiveContentCenterY();
        } else {
            double w = (canvas != null) ? canvas.getWidth() : GameConfig.WINDOW_WIDTH;
            double h = (canvas != null) ? canvas.getHeight() : GameConfig.WINDOW_HEIGHT;
            playerX = w / 2.0;
            playerY = h / 2.0;
        }

        animFrame = 0;
        animTimer = 0.0;
        facingLeft = false;
        isMoving = false;
        currentZone = -1;
        pulseTimer = 0.0;
        lastNano = 0L;
        remoteAnims.clear();

        // Default round/timer state (used as the offline fallback until the
        // first server snapshot arrives). Once snapshots stream in,
        // {@link #syncRoundStateFromServer} replaces these every frame.
        roundNumber = 1;
        roundTimer = TIMER_DURATION;
        timerRunning = true;
        lastObservedServerRound = 0;

        // Reset toast so no stale notification bleeds in from a previous session
        toastActive    = false;
        toastTimer     = 0;
        lastToastChaos = com.identitycrisis.shared.model.ChaosEventType.NONE;
        toastEventType = com.identitycrisis.shared.model.ChaosEventType.NONE;
        resetLocalChaosCycle();

        // Reset offline-mode safe-zone selection so onEnter() forces a fresh
        // pick on the very first render frame (offlineActiveZoneRound != roundNumber).
        offlineActiveZoneId = -1;
        offlineActiveZoneRound = -1;

        isPaused = false;
        gameOverShown = false;
        if (pauseOverlay != null)
            pauseOverlay.setVisible(false);
        if (gameOverOverlay != null)
            gameOverOverlay.setVisible(false);

        // Show round start popup for round 1
        triggerRoundPopup(1);

        // Attach keyboard handlers to the permanent scene (the one JavaFX actually
        // delivers events to)
        if (inputManager != null) {
            inputManager.attachToScene(sceneManager.getPermanentScene());
        }

        stopLoop();
        gameLoop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastNano == 0L) {
                    lastNano = now;
                    return;
                }
                double dt = (now - lastNano) / 1_000_000_000.0;
                lastNano = now;
                if (dt > 0.05)
                    dt = 0.05; // cap after window pause
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
        if (syncGameOverFromServer()) {
            return;
        }

        // Handle ESC key for pausing
        boolean escPressed = inputManager != null && inputManager.isPressed(KeyCode.ESCAPE);
        if (escPressed && !escWasPressed) {
            togglePause();
        }
        escWasPressed = escPressed;

        if (isPaused)
            return;

        // ── Round start popup countdown ─────────────────────────────────────
        // Tick the countdown first so its state is up-to-date this frame.
        if (roundPopupActive) {
            roundPopupTimer -= dt;
            if (roundPopupTimer <= 0) {
                roundPopupActive = false;
                roundPopupTimer = 0;
            }
        }

        // ── FREEZE: no movement, zone detection, or round advances during popup
        if (roundPopupActive) {
            // Still tick the idle animation so the character breathes in place.
            isMoving = false;
            animTimer += dt;
            if (animTimer >= FRAME_DURATION) {
                animTimer -= FRAME_DURATION;
                animFrame = (animFrame + 1) % IDLE_FRAMES;
            }
            pulseTimer += dt;
            // Sync round state from server (read-only, no side-effects on movement).
            if (!syncRoundStateFromServer()) {
                // offline: also tick the room timer so the HUD doesn't freeze
                tickLocalRoundTimer(dt);
            }
            return; // skip movement & zone-advance logic below
        }

        ChaosEventType effectiveChaos = updateEffectiveChaos(dt);

        // ── Fake safe-zones chaos detection (O key) ───────────────────────────
        // Reads local debug flag from InputManager. Also activates whenever
        // the server broadcasts FAKE_SAFE_ZONES so connected clients
        // automatically enter the chaos visual without pressing O.
        boolean wantFakeZones = effectiveChaos == ChaosEventType.FAKE_SAFE_ZONES
                || (inputManager != null && inputManager.isTestingFakeZones());

        if (wantFakeZones != testingFakeZones) {
            testingFakeZones = wantFakeZones;
            if (testingFakeZones) {
                buildFakeZones();
            } else {
                fakeZoneList.clear();
                fakeZoneTrueId = -1;
            }
        }

        InputSnapshot input = inputManager.snapshot();

        boolean reversed = effectiveChaos == ChaosEventType.REVERSED_CONTROLS;
        if (inputManager != null && inputManager.isTestingReversed()) {
            reversed = true;
        }
        reversedControlsActive = reversed; // persist for render()

        // Tick the chaos event toast (rising-edge detection + countdown).
        // Called here for the OFFLINE path; the connected path calls it after
        // syncNetworkedPlayerState() returns true and then returns early.
        // tickChaosToast is also called below for the online early-return path.
        // For offline, defer it to after the send/sync block below.

        if (reversed) {
            input = new InputSnapshot(
                    input.down(), input.up(), input.right(), input.left(),
                    input.carry(), input.throwAction(), input.release(), input.chatToggle());
        }

        GameClient client = (sceneManager != null) ? sceneManager.getGameClient() : null;
        boolean connected = client != null && client.isConnected();
        if (connected) {
            client.sendInput(input.up(), input.down(), input.left(), input.right(),
                             input.carry(), input.throwAction(), input.release());
        }

        if (connected && syncNetworkedPlayerState(dt)) {
            pulseTimer += dt;
            // Sync round and chaos state from server even when in networked mode
            syncRoundStateFromServer();
            // Tick chaos toast so HUD banners display for all players
            tickChaosToast(effectiveChaos, dt);
            return;
        }

        // ── Direction ────────────────────────────────────────────────────────
        double dx = 0, dy = 0;
        if (input.up())
            dy -= 1;
        if (input.down())
            dy += 1;
        if (input.left())
            dx -= 1;
        if (input.right())
            dx += 1;

        // Normalise diagonal
        if (dx != 0 && dy != 0) {
            double inv = 1.0 / Math.sqrt(2.0);
            dx *= inv;
            dy *= inv;
        }

        isMoving = (dx != 0 || dy != 0);

        if (isMoving) {
            double speed = GameConfig.PLAYER_SPEED;

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

            if (dx < 0)
                facingLeft = true;
            if (dx > 0)
                facingLeft = false;
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

        // ── Toast + round/timer ───────────────────────────────────────────────
        tickChaosToast(effectiveChaos, dt);
        if (!syncRoundStateFromServer()) {
            tickLocalRoundTimer(dt);
            ensureOfflineZone();
            tryAdvanceFromOfflineZoneEntry();
        }
    }

    private boolean syncNetworkedPlayerState(double dt) {
        if (sceneManager == null) return false;
        com.identitycrisis.client.game.LocalGameState lgs = sceneManager.getLocalGameState();
        if (lgs == null || !lgs.hasReceivedSnapshot() || lgs.getPlayers() == null) return false;

        int myId = lgs.getControlledPlayerId() > 0 ? lgs.getControlledPlayerId() : lgs.getMyPlayerId();
        for (Player p : lgs.getPlayers()) {
            if (p.getPlayerId() == myId) {
                double dx = p.getPosition().x() - playerX;
                double dy = p.getPosition().y() - playerY;
                playerX = p.getPosition().x();
                playerY = p.getPosition().y();
                isMoving = p.getVelocity().magnitude() > GameConfig.VELOCITY_STOP_THRESHOLD
                        || Math.abs(dx) > 0.1 || Math.abs(dy) > 0.1;
                if (p.getFacingDirection() == 3 || dx < -0.1) facingLeft = true;
                if (p.getFacingDirection() == 1 || dx > 0.1) facingLeft = false;
                animTimer += dt;
                if (animTimer >= FRAME_DURATION) {
                    animTimer -= FRAME_DURATION;
                    int totalFrames = isMoving ? WALK_FRAMES : IDLE_FRAMES;
                    animFrame = (animFrame + 1) % totalFrames;
                }
                currentZone = (mapManager != null) ? mapManager.getSafeZoneAt(playerX, playerY) : -1;
            } else {
                RemotePlayerAnim ra = remoteAnims.computeIfAbsent(
                        p.getPlayerId(), k -> new RemotePlayerAnim());
                double dx = ra.hasLastPosition ? p.getPosition().x() - ra.lastX : 0;
                double dy = ra.hasLastPosition ? p.getPosition().y() - ra.lastY : 0;
                ra.isMoving = p.getVelocity().magnitude() > GameConfig.VELOCITY_STOP_THRESHOLD
                        || Math.abs(dx) > 0.1 || Math.abs(dy) > 0.1;
                if (p.getFacingDirection() == 3 || dx < -0.1) ra.facingLeft = true;
                if (p.getFacingDirection() == 1 || dx > 0.1) ra.facingLeft = false;
                ra.lastX = p.getPosition().x();
                ra.lastY = p.getPosition().y();
                ra.hasLastPosition = true;
                ra.animTimer += dt;
                if (ra.animTimer >= FRAME_DURATION) {
                    ra.animTimer -= FRAME_DURATION;
                    int frames = ra.isMoving ? WALK_FRAMES : IDLE_FRAMES;
                    ra.animFrame = (ra.animFrame + 1) % frames;
                }
            }
        }
        return true;
    }

    /**
     * Pulls round number, round timer, and round-start-popup trigger from the
     * latest server snapshot held in {@code LocalGameState}. Returns
     * {@code true} if a snapshot was available (server is authoritative) so
     * the caller can skip the local fallback timer.
     *
     * <p>
     * The popup fires on the rising edge of an observed round change so
     * connected clients still see the 3-2-1 countdown — but only once per
     * round transition, regardless of how many snapshots arrive in between.
     */
    private boolean syncRoundStateFromServer() {
        if (sceneManager == null)
            return false;
        com.identitycrisis.client.game.LocalGameState lgs = sceneManager.getLocalGameState();
        if (lgs == null || !lgs.hasReceivedSnapshot())
            return false;

        int serverRound = lgs.getRoundNumber();
        double serverTimer = lgs.getTimerRemaining();

        // Detect a fresh round and trigger the popup once. We use a
        // non-zero "lastObservedServerRound" sentinel to skip the very first
        // synchronisation (the join itself counts as round 1; otherwise we'd
        // double-trigger the popup over the existing onEnter() invocation).
        if (lastObservedServerRound != 0 && serverRound != lastObservedServerRound) {
            triggerRoundPopup(serverRound);
        }
        lastObservedServerRound = serverRound;

        roundNumber = serverRound;
        roundTimer = Math.max(0, serverTimer);
        timerRunning = false; // server owns the clock; local field unused
        return true;
    }

    private ChaosEventType updateEffectiveChaos(double dt) {
        if (sceneManager != null) {
            com.identitycrisis.client.game.LocalGameState lgs = sceneManager.getLocalGameState();
            if (lgs != null && lgs.hasReceivedSnapshot()) {
                localChaosEvent = ChaosEventType.NONE;
                localChaosTimer = GameConfig.CHAOS_EVENT_DURATION;
                ChaosEventType serverChaos = lgs.getActiveChaos();
                return serverChaos != null ? serverChaos : ChaosEventType.NONE;
            }
        }

        if (!LOCAL_CHAOS_ENABLED) {
            localChaosEvent = ChaosEventType.NONE;
            localChaosTimer = 0;
            return ChaosEventType.NONE;
        }

        if (localChaosEvent == ChaosEventType.NONE) {
            localChaosTimer -= dt;
            if (localChaosTimer <= 0) {
                activateNextLocalChaos(ChaosEventType.NONE);
            }
        } else {
            localChaosTimer -= dt;
            if (localChaosTimer <= 0) {
                activateNextLocalChaos(localChaosEvent);
            }
        }
        return localChaosEvent;
    }

    /**
     * Ticks the chaos-event toast (rising-edge detection + countdown). Called
     * from both the connected and offline update paths so every client sees the
     * HUD toast banner when a chaos event activates.
     *
     * @param effectiveChaos the chaos type active this frame (NONE if no event)
     * @param dt             frame delta in seconds
     */
    private void tickChaosToast(ChaosEventType effectiveChaos, double dt) {
        // Supplement with local debug toggles when offline
        if (effectiveChaos == ChaosEventType.NONE) {
            if (inputManager != null && inputManager.isTestingReversed())
                effectiveChaos = ChaosEventType.REVERSED_CONTROLS;
            else if (inputManager != null && inputManager.isTestingFakeZones())
                effectiveChaos = ChaosEventType.FAKE_SAFE_ZONES;
        }
        // Rising edge: a new or changed event
        if (effectiveChaos != ChaosEventType.NONE && effectiveChaos != lastToastChaos) {
            toastActive    = true;
            toastTimer     = 3.0;
            toastEventType = effectiveChaos;
        }
        lastToastChaos = effectiveChaos;
        if (toastActive) {
            toastTimer -= dt;
            if (toastTimer <= 0) {
                toastActive = false;
                toastTimer  = 0;
            }
        }
    }

    private boolean syncGameOverFromServer() {
        if (gameOverShown)
            return true;
        if (sceneManager == null)
            return false;
        com.identitycrisis.client.game.LocalGameState lgs = sceneManager.getLocalGameState();
        if (lgs == null)
            return false;
        boolean over = lgs.isGameOver()
                || lgs.getPhase() == com.identitycrisis.shared.model.RoundPhase.GAME_OVER;
        if (!over)
            return false;
        String winnerName = lgs.getWinnerName();
        showGameOverOverlay(winnerName == null || winnerName.isBlank() ? "WINNER: UNKNOWN" : "WINNER: " + winnerName);
        return true;
    }

    private void showGameOverOverlay(String resultText) {
        gameOverShown = true;
        isPaused = false;
        if (pauseOverlay != null)
            pauseOverlay.setVisible(false);
        if (gameOverWinnerLabel != null)
            gameOverWinnerLabel.setText(resultText);
        if (gameOverOverlay != null)
            gameOverOverlay.setVisible(true);
    }

    private void activateNextLocalChaos(ChaosEventType previous) {
        ChaosEventType next;
        do {
            next = LOCAL_CHAOS_EVENTS[localChaosRng.nextInt(LOCAL_CHAOS_EVENTS.length)];
        } while (LOCAL_CHAOS_EVENTS.length > 1 && next == previous);
        localChaosEvent = next;
        localChaosTimer = GameConfig.CHAOS_EVENT_DURATION;
    }

    private void resetLocalChaosCycle() {
        localChaosEvent = ChaosEventType.NONE;
        localChaosTimer = GameConfig.CHAOS_EVENT_DURATION;
        reversedControlsActive = false;
        testingFakeZones = false;
        fakeZoneList.clear();
        fakeZoneTrueId = -1;
    }

    /**
     * Returns the offline-mode placeholder safe zone for the current round,
     * picking a fresh random TMX rectangle whenever the round number changes.
     * Returns {@code null} only when the {@link MapManager} hasn't loaded any
     * safe-zone rectangles (e.g. before TMX parsing completes).
     *
     * <p>
     * Selection is stable for the duration of a round so the indicator
     * doesn't flicker between candidates each frame. In single-player this
     * mirrors the server's single-zone-per-round behaviour without requiring
     * a running server.
     */
    private com.identitycrisis.shared.model.SafeZone ensureOfflineZone() {
        if (mapManager == null)
            return null;
        java.util.List<MapManager.SafeZoneRect> spots = mapManager.getSafeZones();
        if (spots == null || spots.isEmpty())
            return null;

        if (offlineActiveZoneRound != roundNumber || offlineActiveZoneId < 0) {
            // After a round advance the player is still standing on the
            // *previous* zone for a brief moment. Excluding zones the player
            // is currently inside prevents the new pick from immediately
            // re-triggering {@link #tryAdvanceFromOfflineZoneEntry} on the
            // next frame, which would auto-skip rounds without any walking.
            int playerZone = currentZone;
            java.util.List<MapManager.SafeZoneRect> candidates = new java.util.ArrayList<>();
            for (MapManager.SafeZoneRect r : spots) {
                if (r.id() != playerZone)
                    candidates.add(r);
            }
            if (candidates.isEmpty())
                candidates = spots; // safety: only one spot exists
            MapManager.SafeZoneRect pick = candidates.get(offlineZoneRng.nextInt(candidates.size()));
            offlineActiveZoneId = pick.id();
            offlineActiveZoneRound = roundNumber;
        }

        for (MapManager.SafeZoneRect rect : spots) {
            if (rect.id() == offlineActiveZoneId) {
                return new com.identitycrisis.shared.model.SafeZone(
                        rect.id(), rect.x(), rect.y(), rect.width(), rect.height());
            }
        }
        // Fallback: id no longer in pool (shouldn't happen) — pick first.
        MapManager.SafeZoneRect first = spots.get(0);
        offlineActiveZoneId = first.id();
        offlineActiveZoneRound = roundNumber;
        return new com.identitycrisis.shared.model.SafeZone(
                first.id(), first.x(), first.y(), first.width(), first.height());
    }

    /**
     * Single-player offline win condition: when the player walks into the
     * round's offline-active zone, immediately end the round and advance to
     * the next one. This mirrors the server-side warm-up behaviour
     * ({@code RoundManager.tick} fast-forwards the timer when every alive
     * player is in a zone) but works without networking.
     *
     * <p>
     * The advance only fires when:
     * <ul>
     * <li>No server zones are being received (purely offline play).</li>
     * <li>An offline zone has been picked for the current round.</li>
     * <li>The player's {@link #currentZone} matches that zone's id.</li>
     * <li>The round-start popup is not already animating (otherwise the
     * 3-2-1 countdown would skip rounds before the player can react).</li>
     * </ul>
     *
     * <p>
     * After advance the popup plays for the new round and
     * {@link #ensureOfflineZone} re-rolls a different rectangle.
     */
    private void tryAdvanceFromOfflineZoneEntry() {
        // Server connected → server owns round transitions.
        if (sceneManager != null) {
            com.identitycrisis.client.game.LocalGameState lgs = sceneManager.getLocalGameState();
            if (lgs != null && lgs.hasReceivedSnapshot())
                return;
        }
        if (roundPopupActive)
            return;
        if (offlineActiveZoneId < 0)
            return;
        if (currentZone != offlineActiveZoneId)
            return;

        // Advance the round. Cap the round number at WARMUP_ROUNDS + 1 to
        // surface the elimination-round HUD switch; without a true game-over
        // condition the milestone-A solo flow is endless on purpose.
        roundNumber += 1;
        triggerRoundPopup(roundNumber);
        if (roundNumber <= GameConfig.WARMUP_ROUNDS) {
            roundTimer = TIMER_DURATION;
            timerRunning = true;
        } else {
            // Elimination rounds have no countdown panel locally — the popup
            // and re-rolled zone are the only feedback.
            roundTimer = 0;
            timerRunning = false;
        }
    }

    /**
     * Offline fallback: decrements the local round timer using the frame
     * delta and advances to the next round when it reaches zero. Only used
     * before the first server snapshot arrives.
     */
    private void tickLocalRoundTimer(double dt) {
        if (!timerRunning || roundPopupActive)
            return;
        if (roundNumber > GameConfig.WARMUP_ROUNDS)
            return;

        roundTimer -= dt;
        if (roundTimer <= 0) {
            roundTimer = 0;
            timerRunning = false;
            endGameOffline();
        }
    }

    private void endGameOffline() {
        showGameOverOverlay("YOU SURVIVED");
    }

    /**
     * Returns {@code true} if the player's tight hitbox rectangle overlaps any
     * solid tile. {@code (cx, cy)} is the world-pixel position of the sprite-
     * frame centre (i.e. {@link #playerX} / {@link #playerY}).
     *
     * <p>
     * All HIT_* constants are in <b>world-pixel</b> units — the same coordinate
     * space as {@link #playerX}/{@link #playerY} and {@link MapManager#isSolid}.
     * They must <em>not</em> be multiplied by the screen scale factor (which
     * converts
     * world→screen pixels) because {@code isSolid} works purely in world space.
     *
     * <p>
     * The hitbox is static: it does not change with animation state.
     */
    private boolean isBlocked(double cx, double cy, double radius) {
        if (mapManager == null)
            return false;
        // Hitbox corners in world-pixel space — no screen-scale multiplication needed.
        double left   = cx + HIT_OFS_X - HIT_HALF_W;
        double right  = cx + HIT_OFS_X + HIT_HALF_W;
        double top    = cy + HIT_OFS_Y - HIT_HALF_H;
        double bottom = cy + HIT_OFS_Y + HIT_HALF_H;
        if (left < 0 || top < 0 || right >= mapManager.getWorldWidth() || bottom >= mapManager.getWorldHeight()) {
            return true;
        }
        // isSolidPixel() uses the per-tile alpha bitmask for wall tiles (pixel-perfect)
        // and falls back to the broad-phase solid[][] grid for water/void — so both
        // hazard types still block the player correctly.
        return mapManager.isSolidPixel(left,  top)
            || mapManager.isSolidPixel(right, top)
            || mapManager.isSolidPixel(left,  bottom)
            || mapManager.isSolidPixel(right, bottom);
    }

    // ── Render ───────────────────────────────────────────────────────────────

    private void render() {
        if (canvas == null)
            return;
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        // 1. Arena map (tiles, fit-to-window)
        arenaRenderer.render(gc, w, h);

        // 2. Safe-zone indicators
        //
        // Hidden during the between-round countdown so the new safe zone is
        // not revealed before the round actually starts. Once the popup
        // dismisses (roundPopupActive == false) they appear normally.
        //
        // When FAKE_SAFE_ZONES chaos is active we override normal zone drawing:
        // all 8 TMX zones are shown, only one is real (green), the rest are decoys
        // (red).
        if (!roundPopupActive) {
            if (testingFakeZones && !fakeZoneList.isEmpty()) {
                // Draw every zone; colour depends on whether it is the true zone.
                for (com.identitycrisis.shared.model.SafeZone z : fakeZoneList) {
                    boolean isTrue = (z.id() == fakeZoneTrueId);
                    drawSafeZoneIndicator(gc, w, h, z, isTrue);
                }
            } else {
                // Normal rendering — source priority: server → offline placeholder.
                java.util.List<com.identitycrisis.shared.model.SafeZone> szs = null;
                if (sceneManager != null && sceneManager.getLocalGameState() != null) {
                    szs = sceneManager.getLocalGameState().getSafeZones();
                }
                if (szs != null && !szs.isEmpty()) {
                    for (com.identitycrisis.shared.model.SafeZone z : szs) {
                        drawSafeZoneIndicator(gc, w, h, z, true);
                    }
                } else {
                    com.identitycrisis.shared.model.SafeZone offline = ensureOfflineZone();
                    if (offline != null)
                        drawSafeZoneIndicator(gc, w, h, offline, true);
                }
            }
        }

        // 3. Player sprites (local + remote)
        drawAllPlayers(gc, w, h);

        // 4. Round timer HUD (top-centre)
        drawTimerHud(gc, w, h);

        // 4b. Chaos HUD banners (stacked below timer, each 34 px tall)
        int bannerSlot = 0;
        if (reversedControlsActive)
            drawReversedControlsBanner(gc, w, h, bannerSlot++);
        if (testingFakeZones)
            drawFakeZonesBanner(gc, w, h, bannerSlot);

        drawCarriedReleasePrompt(gc, w, h);

        // 5. Chaos event toast (top-right, above round popup)
        drawToast(gc, w, h);

        // 6. Round start popup overlay (center)
        drawRoundPopup(gc, w, h);
    }

    private void drawCarriedReleasePrompt(GraphicsContext gc, double viewW, double viewH) {
        com.identitycrisis.client.game.LocalGameState lgs =
                (sceneManager != null) ? sceneManager.getLocalGameState() : null;
        if (lgs == null || !lgs.hasReceivedSnapshot() || lgs.getPlayers() == null) {
            return;
        }
        int playerId = lgs.getControlledPlayerId() > 0 ? lgs.getControlledPlayerId() : lgs.getMyPlayerId();
        boolean carried = false;
        for (Player p : lgs.getPlayers()) {
            if (p.getPlayerId() == playerId && p.getState() == PlayerState.CARRIED) {
                carried = true;
                break;
            }
        }
        if (!carried) {
            return;
        }

        double boxW = 520;
        double boxH = 86;
        double x = (viewW - boxW) / 2.0;
        double y = viewH - boxH - 42;

        gc.save();
        gc.setGlobalAlpha(0.92);
        gc.setFill(Color.web("#181425"));
        gc.fillRoundRect(x, y, boxW, boxH, 18, 18);
        gc.setGlobalAlpha(1.0);
        gc.setStroke(Color.web("#F6C177"));
        gc.setLineWidth(3);
        gc.strokeRoundRect(x, y, boxW, boxH, 18, 18);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.web("#FFF2CC"));
        gc.setFont(loadFont("Press Start 2P", 13));
        gc.fillText("PRESS Y TO RELEASE", viewW / 2.0, y + 34);
        gc.setFill(Color.web("#C0CBDC"));
        gc.setFont(loadFont("Press Start 2P", 8));
        gc.fillText("MASH 7 TIMES TO BREAK FREE", viewW / 2.0, y + 61);
        gc.restore();
    }

    /**
     * Draws the chaos-event toast in the top-right corner.
     *
     * <p>The toast PNG (96×32 native) is rendered at 3× (288×96 screen px) with
     * nearest-neighbour upscaling for a crisp pixel-art look. The chaos event
     * name is drawn centred inside the dark right-panel area of the image.
     *
     * <p>Animation: slides in from the right over 0.3 s (ease-out quadratic) and
     * slides back out over the last 0.3 s (ease-in quadratic). Total: 3 seconds.
     */
    private void drawToast(GraphicsContext gc, double viewW, double viewH) {
        // Null + NONE guard — defensive against race conditions on the volatile fields
        if (!toastActive || toastEventType == null
                || toastEventType == com.identitycrisis.shared.model.ChaosEventType.NONE) return;

        final double W        = 288;  // 96 native × 3
        final double H        = 96;   // 32 native × 3
        final double PAD      = 16;
        final double ANIM_DUR = 0.3;
        final double DURATION = 3.0;

        // ── Slide animation ──────────────────────────────────────────────
        double finalX = viewW - W - PAD;
        double drawY  = PAD;
        double drawX;
        double elapsed = DURATION - toastTimer;

        if (elapsed < ANIM_DUR) {
            double t  = elapsed / ANIM_DUR;
            double et = 1.0 - (1.0 - t) * (1.0 - t);   // ease-out
            drawX = viewW - et * (W + PAD);
        } else if (toastTimer < ANIM_DUR) {
            double t  = 1.0 - (toastTimer / ANIM_DUR);
            double et = t * t;                            // ease-in
            drawX = finalX + et * (W + PAD);
        } else {
            drawX = finalX;
        }

        gc.save();

        // ── 1. Draw PNG sprite at 3× (nearest-neighbour) ────────────────
        int idx = toastEventType.ordinal() - 1; // 0=REVERSED,1=SWAP,2=FAKE
        boolean hasImage = toastImages != null && idx >= 0
                && idx < toastImages.length && toastImages[idx] != null;
        if (hasImage) {
            gc.setImageSmoothing(false);
            gc.drawImage(toastImages[idx], 0, 0, 96, 32, drawX, drawY, W, H);
        } else {
            // Fallback solid rect so something appears if PNG failed to load
            gc.setFill(Color.web("#1c1c26"));
            gc.fillRect(drawX, drawY, W, H);
            gc.setFill(Color.web("#8B9BB4"));
            gc.fillRect(drawX + 6, drawY + 6, W - 12, H - 12);
        }

        // ── 2. Event name text centred in the dark right panel ───────────
        // Native panel: x 30–86 (56 px) → 3× = +90..+258 (168 px wide)
        //               y 10–24 (14 px) → 3× = +30..+72  (42 px tall)
        String label = switch (toastEventType) {
            case REVERSED_CONTROLS -> "REVERSED!";
            case CONTROL_SWAP      -> "PLAYER SWAP!";
            case FAKE_SAFE_ZONES   -> "FAKE ZONES!";
            default                -> "";
        };
        if (!label.isEmpty()) {
            double textCX = drawX + 90 + 84;  // midpoint of 168-px panel (90 + 84 = 174)
            double textCY = drawY + 51;        // midpoint of panel (30 + 21) + font baseline
            Font tf = (fontToast != null) ? fontToast : loadFont("Press Start 2P", 7);
            gc.setFont(tf);
            gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
            gc.setFill(Color.web("#181425"));
            gc.fillText(label, textCX + 1, textCY + 1); // pixel-art drop shadow
            gc.setFill(Color.web("#C0CBDC"));
            gc.fillText(label, textCX, textCY);
        }

        gc.restore();
    }

    // ── Player rendering ─────────────────────────────────────────────────────

    private void drawPlayerSprite(GraphicsContext gc, double viewW, double viewH,
                                  double worldX, double worldY,
                                  int spriteIdx, int frame, boolean moving,
                                  boolean left, String displayName) {
        double screenX, screenY, displaySize;
        if (mapManager != null && mapManager.getWorldWidth() > 0) {
            double scale = mapManager.getScale(viewW, viewH);
            screenX = mapManager.worldToScreenX(worldX, viewW, viewH);
            screenY = mapManager.worldToScreenY(worldY, viewW, viewH);
            displaySize = SPRITE_NATIVE * scale;
        } else {
            screenX = worldX;
            screenY = worldY;
            displaySize = SPRITE_NATIVE * 3.0;
        }
        String key = moving ? "player_" + spriteIdx + "_walk"
                            : "player_" + spriteIdx + "_idle";
        Image sheet = spriteManager.get(key);
        int maxFrames = moving ? WALK_FRAMES : IDLE_FRAMES;
        int f = Math.min(frame, maxFrames - 1);
        double srcX = f * SPRITE_NATIVE;
        double drawX = screenX - displaySize / 2.0;
        double drawY = screenY - displaySize / 2.0;

        if (sheet != null) {
            gc.save();
            gc.setImageSmoothing(false);
            if (left) {
                gc.translate(drawX + displaySize, drawY);
                gc.scale(-1, 1);
                gc.drawImage(sheet, srcX, 0, SPRITE_NATIVE, SPRITE_NATIVE,
                             0, 0, displaySize, displaySize);
            } else {
                gc.drawImage(sheet, srcX, 0, SPRITE_NATIVE, SPRITE_NATIVE,
                             drawX, drawY, displaySize, displaySize);
            }
            gc.restore();
        } else {
            gc.setFill(Color.web("#3E8948"));
            double r = displaySize / 2.0;
            gc.fillOval(screenX - r, screenY - r, displaySize, displaySize);
        }

        if (displayName != null && !displayName.isEmpty()) {
            gc.save();
            gc.setFont(loadFont("Press Start 2P", 5));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setFill(Color.web("#e8dfc4"));
            gc.fillText(displayName, screenX, drawY - 4);
            gc.restore();
        }
    }

    private void drawAllPlayers(GraphicsContext gc, double viewW, double viewH) {
        com.identitycrisis.client.game.LocalGameState lgs =
                (sceneManager != null) ? sceneManager.getLocalGameState() : null;
        if (lgs != null && lgs.hasReceivedSnapshot() && lgs.getPlayers() != null) {
            int myId = lgs.getControlledPlayerId() > 0 ? lgs.getControlledPlayerId() : lgs.getMyPlayerId();
            for (Player p : lgs.getPlayers()) {
                if (p.getState() == PlayerState.ELIMINATED) continue;
                int spriteIdx = ((p.getPlayerId() - 1) % 8) + 1;
                if (p.getPlayerId() == myId) {
                    drawPlayerSprite(gc, viewW, viewH,
                            p.getPosition().x(), p.getPosition().y(), spriteIdx,
                            animFrame, isMoving, facingLeft, p.getDisplayName());
                } else {
                    RemotePlayerAnim ra = remoteAnims.get(p.getPlayerId());
                    if (ra == null) ra = new RemotePlayerAnim();
                    drawPlayerSprite(gc, viewW, viewH,
                            p.getPosition().x(), p.getPosition().y(),
                            spriteIdx, ra.animFrame, ra.isMoving, ra.facingLeft,
                            p.getDisplayName());
                }
            }
        } else {
            drawPlayerSprite(gc, viewW, viewH,
                    playerX, playerY, 1, animFrame, isMoving, facingLeft, null);
        }
    }

    // ── Safe-zone indicator ───────────────────────────────────────────────────

    /**
     * Draws a single rectangular safe zone with a translucent green fill, a
     * pulsing dashed border, and a centred {@code "◆ SAFE ZONE N ◆"} label.
     *
     * <p>
     * The rectangle's coordinates
     * ({@link com.identitycrisis.shared.model.SafeZone#x()},
     * {@code y()}, {@code w()}, {@code h()}) are in <em>world-pixel</em> space
     * — the same coordinate system as {@link #playerX}/{@link #playerY}. We
     * convert each corner via {@link MapManager#worldToScreenX} /
     * {@link MapManager#worldToScreenY} so the indicator stays pinned to the
     * map regardless of viewport size or fullscreen mode.
     */
    private void drawSafeZoneIndicator(GraphicsContext gc, double viewW, double viewH,
            com.identitycrisis.shared.model.SafeZone zone,
            boolean isTrue) {
        double sx, sy, ex, ey;
        if (mapManager != null && mapManager.getWorldWidth() > 0) {
            sx = mapManager.worldToScreenX(zone.x(), viewW, viewH);
            sy = mapManager.worldToScreenY(zone.y(), viewW, viewH);
            ex = mapManager.worldToScreenX(zone.x() + zone.w(), viewW, viewH);
            ey = mapManager.worldToScreenY(zone.y() + zone.h(), viewW, viewH);
        } else {
            sx = zone.x();
            sy = zone.y();
            ex = zone.x() + zone.w();
            ey = zone.y() + zone.h();
        }
        double rectX = Math.min(sx, ex);
        double rectY = Math.min(sy, ey);
        double rectW = Math.abs(ex - sx);
        double rectH = Math.abs(ey - sy);

        // Pulsing translucent fill — opacity oscillates between 0.18 and 0.30.
        double pulse = 0.18 + 0.12 * Math.sin(pulseTimer * 4.0);
        if (isTrue) {
            // Real zone: classic green
            gc.setFill(Color.rgb(74, 140, 92, pulse));
        } else {
            // Decoy zone: red/orange tint — looks enticing but is a trap
            gc.setFill(Color.rgb(180, 60, 40, pulse));
        }
        gc.fillRect(rectX, rectY, rectW, rectH);

        // Dashed border — animated dash offset for movement.
        gc.save();
        gc.setStroke(isTrue ? Color.web("#c9a84c") : Color.web("#e05030"));
        gc.setLineWidth(2.0);
        gc.setLineDashes(8.0, 6.0);
        gc.setLineDashOffset(-(pulseTimer * 14.0) % 14.0);
        gc.strokeRect(rectX + 1, rectY + 1, Math.max(0, rectW - 2), Math.max(0, rectH - 2));
        gc.restore();
    }

    // ── Fake-zone chaos helpers ────────────────────────────────────────────────

    /**
     * Populates {@link #fakeZoneList} with SafeZone objects for every TMX
     * rectangle. Randomly selects one as the true zone ({@link #fakeZoneTrueId});
     * all others are decoys. If a server snapshot is present its active zones
     * define the true zone(s) — the rest become decoys drawn from the TMX pool.
     */
    private void buildFakeZones() {
        fakeZoneList.clear();
        fakeZoneTrueId = -1;
        if (mapManager == null)
            return;

        java.util.List<MapManager.SafeZoneRect> all = mapManager.getSafeZones();
        if (all == null || all.isEmpty())
            return;

        // Convert every TMX rectangle into a SafeZone object
        for (MapManager.SafeZoneRect r : all) {
            fakeZoneList.add(new com.identitycrisis.shared.model.SafeZone(
                    r.id(), r.x(), r.y(), r.width(), r.height()));
        }

        // Determine the true zone.
        // Server snapshot available → use the first server-reported zone's id.
        // Otherwise → pick one at random from the full TMX pool.
        if (sceneManager != null && sceneManager.getLocalGameState() != null) {
            java.util.List<com.identitycrisis.shared.model.SafeZone> serverZones = sceneManager.getLocalGameState()
                    .getSafeZones();
            if (serverZones != null && !serverZones.isEmpty()) {
                fakeZoneTrueId = serverZones.get(0).id();
            }
        }
        if (fakeZoneTrueId < 0) {
            // Offline / no snapshot — pick randomly
            fakeZoneTrueId = fakeZoneList
                    .get(fakeZoneRng.nextInt(fakeZoneList.size())).id();
        }
        System.out.println("[CHAOS] FAKE_SAFE_ZONES active — true zone id=" + fakeZoneTrueId
                + ", decoys=" + (fakeZoneList.size() - 1));
    }

    /**
     * Draws a small "⚠ CHAOS: REVERSED CONTROLS" warning banner below the timer
     * panel.
     * Uses a purple/violet palette to distinguish from the red fake-zones banner.
     *
     * @param slot vertical stack slot (0 = first banner directly below timer, 1 =
     *             second, …)
     */
    private void drawReversedControlsBanner(GraphicsContext gc, double viewW, double viewH, int slot) {
        double bannerW = 340;
        double bannerH = 28;
        double bannerX = Math.round((viewW - bannerW) / 2.0);
        double bannerY = 16 + TIMER_H + 6 + slot * 34; // 34 px per banner slot

        double pulse = 0.7 + 0.3 * Math.abs(Math.sin(pulseTimer * 3.0));

        gc.save();
        gc.setGlobalAlpha(pulse);

        // Background pill — same red as fake-zones banner
        gc.setFill(Color.rgb(160, 30, 20, 0.85));
        gc.fillRoundRect(bannerX, bannerY, bannerW, bannerH, 6, 6);

        // Border
        gc.setStroke(Color.web("#e05030"));
        gc.setLineWidth(1.5);
        gc.strokeRoundRect(bannerX + 1, bannerY + 1, bannerW - 2, bannerH - 2, 5, 5);

        // Text
        gc.setFont(loadFont("Press Start 2P", 7));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.web("#ffe0c0"));
        gc.fillText("⚠  CHAOS: REVERSED CONTROLS", viewW / 2.0, bannerY + 18);

        gc.restore();
    }

    /**
     * Draws a small "⚠ CHAOS: FAKE ZONES" warning banner below the timer panel
     * so the player knows the chaos event is active.
     *
     * @param slot vertical stack slot (0 = first banner directly below timer, 1 =
     *             second, …)
     */
    private void drawFakeZonesBanner(GraphicsContext gc, double viewW, double viewH, int slot) {
        // Pulsing red banner centred horizontally, just below the timer panel area
        double bannerW = 320;
        double bannerH = 28;
        double bannerX = Math.round((viewW - bannerW) / 2.0);
        double bannerY = 16 + TIMER_H + 6 + slot * 34; // 34 px per banner slot

        double pulse = 0.7 + 0.3 * Math.abs(Math.sin(pulseTimer * 3.0));

        gc.save();
        gc.setGlobalAlpha(pulse);

        // Background pill
        gc.setFill(Color.rgb(160, 30, 20, 0.85));
        gc.fillRoundRect(bannerX, bannerY, bannerW, bannerH, 6, 6);

        // Border
        gc.setStroke(Color.web("#e05030"));
        gc.setLineWidth(1.5);
        gc.strokeRoundRect(bannerX + 1, bannerY + 1, bannerW - 2, bannerH - 2, 5, 5);

        // Text
        gc.setFont(loadFont("Press Start 2P", 7));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.web("#ffe0c0"));
        gc.fillText("⚠  CHAOS: FAKE SAFE ZONES", viewW / 2.0, bannerY + 18);

        gc.restore();
    }

    // ── Timer HUD ────────────────────────────────────────────────────────────

    /**
     * Draws the pixel-art timer panel (timer_ui.png, scaled 2×) centred at the
     * top of the screen. Only visible during timer-based rounds (1–2).
     * Hidden entirely for round 3+ (safe-zone-based rounds).
     */
    private void drawTimerHud(GraphicsContext gc, double viewW, double viewH) {
        // Only shown during warm-up timer rounds
        if (roundNumber > GameConfig.WARMUP_ROUNDS)
            return;
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
     * 
     * @param round the round number to announce
     */
    private void triggerRoundPopup(int round) {
        popupRoundNumber = round;
        roundPopupTimer = GameConfig.COUNTDOWN_SECONDS; // 3 seconds - matches audio length
        roundPopupActive = true;
        resetLocalChaosCycle();

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
        if (!roundPopupActive)
            return;

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
        if (countdownValue == 3)
            countdownColor = Color.web("#4a8c5c");
        else if (countdownValue == 2)
            countdownColor = Color.web("#d4a017");
        else if (countdownValue == 1)
            countdownColor = Color.web("#d04648");
        else
            countdownColor = Color.web("#c9a84c");

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
        gc.setFill(Color.web("#181425"));
        gc.fillRect(ox, oy, 64 * s, 2 * s);
        gc.fillRect(ox, oy + 2 * s, 2 * s, 30 * s);
        gc.fillRect(ox + 62 * s, oy + 2 * s, 2 * s, 30 * s);
        gc.fillRect(ox + 2 * s, oy + 30 * s, 60 * s, 2 * s);
        // Panel face
        gc.setFill(Color.web("#8B9BB4"));
        gc.fillRect(ox + 4 * s, oy + 4 * s, 56 * s, 24 * s);
        // Top highlight
        gc.setFill(Color.web("#FFFFFF"));
        gc.fillRect(ox + 4 * s, oy + 4 * s, 56 * s, 2 * s);
        // Side shadows
        gc.setFill(Color.web("#8B9BB4"));
        gc.fillRect(ox + 2 * s, oy + 4 * s, 2 * s, 24 * s);
        gc.fillRect(ox + 60 * s, oy + 4 * s, 2 * s, 24 * s);
        // Header strip
        gc.setFill(Color.web("#5A6988"));
        gc.fillRect(ox + 10 * s, oy + 6 * s, 44 * s, 4 * s);
        gc.setFill(Color.web("#3A4466"));
        gc.fillRect(ox + 8 * s, oy + 6 * s, 2 * s, 4 * s);
        gc.fillRect(ox + 54 * s, oy + 6 * s, 2 * s, 4 * s);
        // Bottom strip
        gc.setFill(Color.web("#C0CBDC"));
        gc.fillRect(ox + 8 * s, oy + 24 * s, 48 * s, 4 * s);
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
        btn.setOnMouseExited(e -> btn.setStyle(backStyle(false)));
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
        btn.setOnMouseExited(e -> btn.setStyle(fsStyle(false)));
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
            if (confirmAction != null)
                confirmAction.run();
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

    private void createGameOverOverlay(StackPane root) {
        gameOverOverlay = new StackPane();
        gameOverOverlay.setVisible(false);
        gameOverOverlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.78);");

        try (InputStream is = getClass().getResourceAsStream("/sprites/effects/Veins_1.png")) {
            if (is != null) {
                ImageView veins = new ImageView(new Image(is));
                veins.fitWidthProperty().bind(root.widthProperty());
                veins.fitHeightProperty().bind(root.heightProperty());
                veins.setPreserveRatio(false);
                veins.setOpacity(0.9);
                veins.setMouseTransparent(true);
                gameOverOverlay.getChildren().add(veins);
            }
        } catch (Exception ignored) {
        }

        VBox content = new VBox(22);
        content.setAlignment(Pos.CENTER);
        content.setMaxWidth(620);

        // Logo image
        try (InputStream logoIs = getClass().getResourceAsStream("/logo.png")) {
            if (logoIs != null) {
                ImageView logoView = new ImageView(new Image(logoIs));
                logoView.setFitWidth(80);
                logoView.setFitHeight(80);
                logoView.setPreserveRatio(true);
                logoView.setSmooth(true);
                // Red-tinged glow matching the game-over palette
                javafx.scene.effect.DropShadow logoGlow = new javafx.scene.effect.DropShadow();
                logoGlow.setColor(Color.rgb(208, 70, 72, 0.7));
                logoGlow.setRadius(24);
                logoGlow.setSpread(0.2);
                logoView.setEffect(logoGlow);
                content.getChildren().add(logoView);
            }
        } catch (Exception ignored) {}

        Label title = new Label("GAME OVER");
        title.setStyle("-fx-font-family: 'Press Start 2P', monospace;" +
                "-fx-font-size: 42px;" +
                "-fx-text-fill: #d04648;" +
                "-fx-effect: dropshadow(gaussian, rgba(110, 0, 18, 0.95), 28, 0.45, 0, 0);");

        gameOverWinnerLabel = new Label("WINNER: UNKNOWN");
        gameOverWinnerLabel.setStyle("-fx-font-family: 'Press Start 2P', monospace;" +
                "-fx-font-size: 13px;" +
                "-fx-text-fill: #e8dfc4;" +
                "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.8), 8, 0.25, 0, 2);");

        Button menuBtn = createMenuButton("MAIN MENU");
        menuBtn.setOnAction(e -> {
            onExit();
            sceneManager.shutdownNetwork();
            sceneManager.showMenu();
        });

        content.getChildren().addAll(title, gameOverWinnerLabel, menuBtn);
        gameOverOverlay.getChildren().add(content);
        root.getChildren().add(gameOverOverlay);
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
            pauseArenaAudio();
            showPauseMenu();
        } else {
            resumeArenaAudio();
        }
    }

    private void pauseArenaAudio() {
        if (countdownAudio != null && countdownAudio.getStatus() == MediaPlayer.Status.PLAYING) {
            countdownAudio.pause();
        }
        if (sceneManager != null && sceneManager.getAudioManager() != null) {
            sceneManager.getAudioManager().pauseBGM();
        }
    }

    private void resumeArenaAudio() {
        if (countdownAudio != null && countdownAudio.getStatus() == MediaPlayer.Status.PAUSED) {
            countdownAudio.play();
        }
        if (sceneManager != null && sceneManager.getAudioManager() != null) {
            sceneManager.getAudioManager().resumeBGM();
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
        if (scene == null)
            scene = createScene();
        return scene;
    }
}