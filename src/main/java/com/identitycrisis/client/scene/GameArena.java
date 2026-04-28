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

/** Game arena screen with TMX map rendering, player movement, and safe-zone indicators. */
public class GameArena {

    private static final int    SPRITE_NATIVE  = 32;
    private static final int    IDLE_FRAMES    = 4;
    private static final int    WALK_FRAMES    = 6;
    private static final double FRAME_DURATION = 1.0 / 8.0;

    private static final double HIT_HALF_W =  3.0;
    private static final double HIT_HALF_H =  5.0;
    private static final double HIT_OFS_X  =  0.0;
    private static final double HIT_OFS_Y  =  4.0;

    private static final String GOLD           = "#c9a84c";
    private static final String GOLD_DARK      = "#8a6a1a";
    private static final String STONE_PANEL    = "#1c1c26";
    private static final String STONE_BORDER   = "#2a2a36";
    private static final String TEXT_MUTED     = "#7a7060";
    private static final String TEXT_PARCHMENT = "#e8dfc4";

    private static final Color TIMER_DARK    = Color.web("#181425");
    private static final Color TIMER_FACE    = Color.web("#8B9BB4");
    private static final Color TIMER_HEADER  = Color.web("#C0CBDC");
    private static final Color TIMER_URGENT  = Color.web("#D04648");

    private static final int    TIMER_SCALE      = 2;
    private static final int    TIMER_W_NATIVE   = 64;
    private static final int    TIMER_H_NATIVE   = 32;
    private static final double TIMER_W          = TIMER_W_NATIVE * TIMER_SCALE;
    private static final double TIMER_H          = TIMER_H_NATIVE * TIMER_SCALE;
    private static final double FACE_X_NATIVE    = 10;
    private static final double FACE_Y_NATIVE    = 10;
    private static final double FACE_W_NATIVE    = 44;
    private static final double FACE_H_NATIVE    = 14;
    private static final double HDR_X_NATIVE     = 10;
    private static final double HDR_Y_NATIVE     =  6;
    private static final double HDR_W_NATIVE     = 44;
    private static final double HDR_H_NATIVE     =  4;
    private static final double TIMER_DURATION   = 25.0;

    private Scene  scene;
    private Canvas canvas;
    private final SceneManager sceneManager;

    private SpriteManager  spriteManager;
    private ArenaRenderer  arenaRenderer;
    private MapManager     mapManager;
    private InputManager   inputManager;
    private AnimationTimer gameLoop;
    private long           lastNano;

    private double  playerX;
    private double  playerY;
    private int     animFrame;
    private double  animTimer;
    private boolean facingLeft;
    private boolean isMoving;
    private int     currentZone;
    private double  pulseTimer;


    private int    roundNumber;
    private double roundTimer;
    private boolean timerRunning;
    private int    lastObservedServerRound;

 

    private int    offlineActiveZoneId = -1;
    private int    offlineActiveZoneRound = -1;
    private final java.util.Random offlineZoneRng = new java.util.Random();
    private Image  timerPanelImage;
    private Font   fontTimerLabel;
    private Font   fontTimerFace;

 
    private boolean roundPopupActive;
    private double roundPopupTimer;
    private int popupRoundNumber;
    private Font fontRoundPopup;
    private MediaPlayer countdownAudio;
    
    private boolean isPaused      = false;
    private boolean escWasPressed = false;
    private StackPane pauseOverlay;
    private javafx.scene.layout.VBox pauseMenu;
    private javafx.scene.layout.VBox confirmMenu;
    private javafx.scene.control.Label confirmLabel;
    private Runnable confirmAction;

    private boolean testingFakeZones      = false;
    private java.util.List<com.identitycrisis.shared.model.SafeZone> fakeZoneList = new java.util.ArrayList<>();
    private int     fakeZoneTrueId        = -1;
    private final java.util.Random fakeZoneRng = new java.util.Random();

    private boolean reversedControlsActive = false;

    public GameArena(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
    }

    public Scene createScene() {
        StackPane root = new StackPane();
        root.setStyle("-fx-background-color: black;");
        root.setPrefSize(GameConfig.WINDOW_WIDTH, GameConfig.WINDOW_HEIGHT);

        canvas = new Canvas(GameConfig.WINDOW_WIDTH, GameConfig.WINDOW_HEIGHT);
        canvas.widthProperty().bind(root.widthProperty());
        canvas.heightProperty().bind(root.heightProperty());
        root.getChildren().add(canvas);

        addFullscreenButton(root);

        createPauseOverlay(root);

        scene = new Scene(root, GameConfig.WINDOW_WIDTH, GameConfig.WINDOW_HEIGHT);
        scene.getStylesheets().add(
                getClass().getResource("/styles/global.css").toExternalForm());

        spriteManager = new SpriteManager();
        spriteManager.loadAll();

        arenaRenderer = new ArenaRenderer(spriteManager);
        mapManager    = arenaRenderer.getMapManager();

        inputManager = new InputManager();

        try (InputStream is = getClass().getResourceAsStream("/sprites/ui/toasts/timer_ui.png")) {
            if (is != null) timerPanelImage = new Image(is);
        } catch (Exception ignored) {}

        try {
            java.net.URL audioUrl = getClass().getResource("/sprites/ui/3sectimer.wav");
            if (audioUrl != null) {
                countdownAudio = new MediaPlayer(new Media(audioUrl.toExternalForm()));
                countdownAudio.setVolume(0.8);
            }
        } catch (Exception ignored) {}

        fontTimerLabel = loadFont("Press Start 2P",  6);
        fontTimerFace  = loadFont("Press Start 2P", 10);
        fontRoundPopup = loadFont("Press Start 2P", 24);

        return scene;
    }


    /** Resets player to world centre and starts the render loop. */
    public void onEnter() {
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

        roundNumber              = 1;
        roundTimer               = TIMER_DURATION;
        timerRunning             = true;
        lastObservedServerRound  = 0;

        offlineActiveZoneId      = -1;
        offlineActiveZoneRound   = -1;

        isPaused = false;
        if (pauseOverlay != null) pauseOverlay.setVisible(false);

        triggerRoundPopup(1);

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

    public void onExit() {
        stopLoop();
        if (inputManager != null) {
            inputManager.detachFromScene(sceneManager.getPermanentScene());
        }
    }

    private void update(double dt) {
        boolean escPressed = inputManager != null && inputManager.isPressed(KeyCode.ESCAPE);
        if (escPressed && !escWasPressed) {
            togglePause();
        }
        escWasPressed = escPressed;

        if (isPaused) return;

        if (roundPopupActive) {
            roundPopupTimer -= dt;
            if (roundPopupTimer <= 0) {
                roundPopupActive = false;
                roundPopupTimer  = 0;
            }
        }

        if (roundPopupActive) {
            isMoving = false;
            animTimer += dt;
            if (animTimer >= FRAME_DURATION) {
                animTimer -= FRAME_DURATION;
                animFrame = (animFrame + 1) % IDLE_FRAMES;
            }
            pulseTimer += dt;
            if (!syncRoundStateFromServer()) {
                tickLocalRoundTimer(dt);
            }
            return;
        }

        boolean serverFakeZones = false;
        if (sceneManager != null && sceneManager.getLocalGameState() != null) {
            serverFakeZones = (sceneManager.getLocalGameState().getActiveChaos()
                    == com.identitycrisis.shared.model.ChaosEventType.FAKE_SAFE_ZONES);
        }
        boolean wantFakeZones = serverFakeZones
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

        boolean reversed = false;
        if (sceneManager != null && sceneManager.getLocalGameState() != null) {
            reversed = (sceneManager.getLocalGameState().getActiveChaos() == com.identitycrisis.shared.model.ChaosEventType.REVERSED_CONTROLS);
        }
        if (inputManager != null && inputManager.isTestingReversed()) {
            reversed = true;
        }
        reversedControlsActive = reversed;

        if (reversed) {
            input = new InputSnapshot(
                input.down(), input.up(), input.right(), input.left(),
                input.carry(), input.throwAction(), input.chatToggle()
            );
        }


        double dx = 0, dy = 0;
        if (input.up())    dy -= 1;
        if (input.down())  dy += 1;
        if (input.left())  dx -= 1;
        if (input.right()) dx += 1;

        if (dx != 0 && dy != 0) {
            double inv = 1.0 / Math.sqrt(2.0);
            dx *= inv;
            dy *= inv;
        }

        isMoving = (dx != 0 || dy != 0);

        if (isMoving) {
            double speed  = GameConfig.PLAYER_SPEED;

            double newX = playerX + dx * speed * dt;
            if (!isBlocked(newX, playerY, 0)) {
                playerX = newX;
            }
            double newY = playerY + dy * speed * dt;
            if (!isBlocked(playerX, newY, 0)) {
                playerY = newY;
            }

            if (dx < 0) facingLeft = true;
            if (dx > 0) facingLeft = false;
        }

        animTimer += dt;
        if (animTimer >= FRAME_DURATION) {
            animTimer -= FRAME_DURATION;
            int totalFrames = isMoving ? WALK_FRAMES : IDLE_FRAMES;
            animFrame = (animFrame + 1) % totalFrames;
        }


        currentZone = (mapManager != null) ? mapManager.getSafeZoneAt(playerX, playerY) : -1;
        pulseTimer += dt;


        if (!syncRoundStateFromServer()) {
            tickLocalRoundTimer(dt);
            ensureOfflineZone();
            tryAdvanceFromOfflineZoneEntry();
        }
    }

    private boolean syncRoundStateFromServer() {
        if (sceneManager == null) return false;
        com.identitycrisis.client.game.LocalGameState lgs = sceneManager.getLocalGameState();
        if (lgs == null || !lgs.hasReceivedSnapshot()) return false;

        int    serverRound = lgs.getRoundNumber();
        double serverTimer = lgs.getTimerRemaining();

        if (lastObservedServerRound != 0 && serverRound != lastObservedServerRound) {
            triggerRoundPopup(serverRound);
        }
        lastObservedServerRound = serverRound;

        roundNumber  = serverRound;
        roundTimer   = Math.max(0, serverTimer);
        timerRunning = false;
        return true;
    }


    private com.identitycrisis.shared.model.SafeZone ensureOfflineZone() {
        if (mapManager == null) return null;
        java.util.List<MapManager.SafeZoneRect> spots = mapManager.getSafeZones();
        if (spots == null || spots.isEmpty()) return null;

        if (offlineActiveZoneRound != roundNumber || offlineActiveZoneId < 0) {

            int playerZone = currentZone;
            java.util.List<MapManager.SafeZoneRect> candidates = new java.util.ArrayList<>();
            for (MapManager.SafeZoneRect r : spots) {
                if (r.id() != playerZone) candidates.add(r);
            }
            if (candidates.isEmpty()) candidates = spots;
            MapManager.SafeZoneRect pick = candidates.get(offlineZoneRng.nextInt(candidates.size()));
            offlineActiveZoneId    = pick.id();
            offlineActiveZoneRound = roundNumber;
        }

        for (MapManager.SafeZoneRect rect : spots) {
            if (rect.id() == offlineActiveZoneId) {
                return new com.identitycrisis.shared.model.SafeZone(
                        rect.id(), rect.x(), rect.y(), rect.width(), rect.height());
            }
        }
        MapManager.SafeZoneRect first = spots.get(0);
        offlineActiveZoneId    = first.id();
        offlineActiveZoneRound = roundNumber;
        return new com.identitycrisis.shared.model.SafeZone(
                first.id(), first.x(), first.y(), first.width(), first.height());
    }

    private void tryAdvanceFromOfflineZoneEntry() {
        if (sceneManager != null) {
            com.identitycrisis.client.game.LocalGameState lgs = sceneManager.getLocalGameState();
            if (lgs != null && lgs.hasReceivedSnapshot()) return;
        }
        if (roundPopupActive) return;
        if (offlineActiveZoneId < 0) return;
        if (currentZone != offlineActiveZoneId) return;

        roundNumber += 1;
        triggerRoundPopup(roundNumber);
        if (roundNumber <= GameConfig.WARMUP_ROUNDS) {
            roundTimer   = TIMER_DURATION;
            timerRunning = true;
        } else {
            roundTimer   = 0;
            timerRunning = false;
        }
    }


    private void tickLocalRoundTimer(double dt) {
        if (!timerRunning || roundPopupActive) return;
        if (roundNumber > GameConfig.WARMUP_ROUNDS) return;

        roundTimer -= dt;
        if (roundTimer <= 0) {
            roundTimer   = 0;
            timerRunning = false;
            endGameOffline();
        }
    }

    private void endGameOffline() {
        if (sceneManager == null) return;
        onExit();
        sceneManager.shutdownNetwork();
        javafx.application.Platform.runLater(sceneManager::showMenu);
    }


    private boolean isBlocked(double cx, double cy, double radius) {
        if (mapManager == null) return false;
        double left   = cx + HIT_OFS_X - HIT_HALF_W;
        double right  = cx + HIT_OFS_X + HIT_HALF_W;
        double top    = cy + HIT_OFS_Y - HIT_HALF_H;
        double bottom = cy + HIT_OFS_Y + HIT_HALF_H;
        return mapManager.isSolid(left,  top)
            || mapManager.isSolid(right, top)
            || mapManager.isSolid(left,  bottom)
            || mapManager.isSolid(right, bottom);
    }

    private void render() {
        if (canvas == null) return;
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        arenaRenderer.render(gc, w, h);

        if (!roundPopupActive) {
            if (testingFakeZones && !fakeZoneList.isEmpty()) {
                for (com.identitycrisis.shared.model.SafeZone z : fakeZoneList) {
                    boolean isTrue = (z.id() == fakeZoneTrueId);
                    drawSafeZoneIndicator(gc, w, h, z, isTrue);
                }
            } else {
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
                    if (offline != null) drawSafeZoneIndicator(gc, w, h, offline, true);
                }
            }
        }

        drawPlayer(gc, w, h);

        drawTimerHud(gc, w, h);

        int bannerSlot = 0;
        if (reversedControlsActive) drawReversedControlsBanner(gc, w, h, bannerSlot++);
        if (testingFakeZones)       drawFakeZonesBanner(gc, w, h, bannerSlot);

        drawRoundPopup(gc, w, h);
    }

    private void drawPlayer(GraphicsContext gc, double viewW, double viewH) {
        double screenX, screenY, displaySize;

        if (mapManager != null && mapManager.getWorldWidth() > 0) {
            double scale = mapManager.getScale(viewW, viewH);
            screenX     = mapManager.worldToScreenX(playerX, viewW, viewH);
            screenY     = mapManager.worldToScreenY(playerY, viewW, viewH);
            displaySize = SPRITE_NATIVE * scale;
        } else {
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
            gc.setFill(Color.web("#3E8948"));
            double r = displaySize / 2.0;
            gc.fillOval(screenX - r, screenY - r, displaySize, displaySize);
            gc.setStroke(Color.web("#63C74D"));
            gc.setLineWidth(2);
            double eyeX = facingLeft ? screenX - r * 0.3 : screenX + r * 0.3;
            gc.strokeLine(screenX, screenY, eyeX, screenY - r * 0.4);
        }
    }

    private void drawSafeZoneIndicator(GraphicsContext gc, double viewW, double viewH,
                                        com.identitycrisis.shared.model.SafeZone zone,
                                        boolean isTrue) {
        double sx, sy, ex, ey;
        if (mapManager != null && mapManager.getWorldWidth() > 0) {
            sx = mapManager.worldToScreenX(zone.x(),               viewW, viewH);
            sy = mapManager.worldToScreenY(zone.y(),               viewW, viewH);
            ex = mapManager.worldToScreenX(zone.x() + zone.w(),    viewW, viewH);
            ey = mapManager.worldToScreenY(zone.y() + zone.h(),    viewW, viewH);
        } else {
            sx = zone.x();           sy = zone.y();
            ex = zone.x() + zone.w(); ey = zone.y() + zone.h();
        }
        double rectX = Math.min(sx, ex);
        double rectY = Math.min(sy, ey);
        double rectW = Math.abs(ex - sx);
        double rectH = Math.abs(ey - sy);

        double pulse = 0.18 + 0.12 * Math.sin(pulseTimer * 4.0);
        if (isTrue) {
            gc.setFill(Color.rgb(74, 140, 92, pulse));
        } else {
            gc.setFill(Color.rgb(180, 60, 40, pulse));
        }
        gc.fillRect(rectX, rectY, rectW, rectH);

        gc.save();
        gc.setStroke(isTrue ? Color.web("#c9a84c") : Color.web("#e05030"));
        gc.setLineWidth(2.0);
        gc.setLineDashes(8.0, 6.0);
        gc.setLineDashOffset(-(pulseTimer * 14.0) % 14.0);
        gc.strokeRect(rectX + 1, rectY + 1, Math.max(0, rectW - 2), Math.max(0, rectH - 2));
        gc.restore();
    }

    private void buildFakeZones() {
        fakeZoneList.clear();
        fakeZoneTrueId = -1;
        if (mapManager == null) return;

        java.util.List<MapManager.SafeZoneRect> all = mapManager.getSafeZones();
        if (all == null || all.isEmpty()) return;

        for (MapManager.SafeZoneRect r : all) {
            fakeZoneList.add(new com.identitycrisis.shared.model.SafeZone(
                    r.id(), r.x(), r.y(), r.width(), r.height()));
        }


        if (sceneManager != null && sceneManager.getLocalGameState() != null) {
            java.util.List<com.identitycrisis.shared.model.SafeZone> serverZones =
                    sceneManager.getLocalGameState().getSafeZones();
            if (serverZones != null && !serverZones.isEmpty()) {
                fakeZoneTrueId = serverZones.get(0).id();
            }
        }
        if (fakeZoneTrueId < 0) {
            fakeZoneTrueId = fakeZoneList
                    .get(fakeZoneRng.nextInt(fakeZoneList.size())).id();
        }
        System.out.println("[CHAOS] FAKE_SAFE_ZONES active — true zone id=" + fakeZoneTrueId
                + ", decoys=" + (fakeZoneList.size() - 1));
    }


    private void drawReversedControlsBanner(GraphicsContext gc, double viewW, double viewH, int slot) {
        double bannerW = 340;
        double bannerH = 28;
        double bannerX = Math.round((viewW - bannerW) / 2.0);
        double bannerY = 16 + TIMER_H + 6 + slot * 34;

        double pulse = 0.7 + 0.3 * Math.abs(Math.sin(pulseTimer * 3.0));

        gc.save();
        gc.setGlobalAlpha(pulse);

        gc.setFill(Color.rgb(160, 30, 20, 0.85));
        gc.fillRoundRect(bannerX, bannerY, bannerW, bannerH, 6, 6);

        gc.setStroke(Color.web("#e05030"));
        gc.setLineWidth(1.5);
        gc.strokeRoundRect(bannerX + 1, bannerY + 1, bannerW - 2, bannerH - 2, 5, 5);

        // Text
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.web("#ffe0c0"));
        gc.fillText("⚠  CHAOS: REVERSED CONTROLS", viewW / 2.0, bannerY + 18);

        gc.restore();
    }

    private void drawFakeZonesBanner(GraphicsContext gc, double viewW, double viewH, int slot) {
        double bannerW  = 320;
        double bannerH  = 28;
        double bannerX  = Math.round((viewW - bannerW) / 2.0);
        double bannerY  = 16 + TIMER_H + 6 + slot * 34;

        double pulse = 0.7 + 0.3 * Math.abs(Math.sin(pulseTimer * 3.0));

        gc.save();
        gc.setGlobalAlpha(pulse);

        gc.setFill(Color.rgb(160, 30, 20, 0.85));
        gc.fillRoundRect(bannerX, bannerY, bannerW, bannerH, 6, 6);

        gc.setStroke(Color.web("#e05030"));
        gc.setLineWidth(1.5);
        gc.strokeRoundRect(bannerX + 1, bannerY + 1, bannerW - 2, bannerH - 2, 5, 5);

        gc.setFont(loadFont("Press Start 2P", 7));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.web("#ffe0c0"));
        gc.fillText("⚠  CHAOS: FAKE SAFE ZONES", viewW / 2.0, bannerY + 18);

        gc.restore();
    }




    private void drawTimerHud(GraphicsContext gc, double viewW, double viewH) {
        if (roundNumber > GameConfig.WARMUP_ROUNDS) return;
        double panelX = Math.round((viewW - TIMER_W) / 2.0);
        double panelY = 16;

        if (timerPanelImage != null) {
            gc.drawImage(timerPanelImage, panelX, panelY, TIMER_W, TIMER_H);
        } else {
            drawFallbackPanel(gc, panelX, panelY);
        }


        double hdrCX = panelX + (HDR_X_NATIVE + HDR_W_NATIVE / 2.0) * TIMER_SCALE;
        double hdrCY = panelY + (HDR_Y_NATIVE + HDR_H_NATIVE / 2.0) * TIMER_SCALE + 4;
        gc.save();
        gc.setFont(fontTimerLabel);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(TIMER_HEADER);
        gc.fillText("ROUND " + roundNumber, hdrCX, hdrCY);
        gc.restore();

        double faceCX = panelX + (FACE_X_NATIVE + FACE_W_NATIVE / 2.0) * TIMER_SCALE;
        double faceCY = panelY + (FACE_Y_NATIVE + FACE_H_NATIVE / 2.0) * TIMER_SCALE + 5;

        gc.save();
        gc.setFont(fontTimerFace);
        gc.setTextAlign(TextAlignment.CENTER);
        boolean urgent = (roundTimer <= 10.0);
        if (urgent) {
            double flicker = Math.sin(roundTimer * Math.PI * 2) > 0 ? 1.0 : 0.75;
            gc.setGlobalAlpha(flicker);
        }
        gc.setFill(urgent ? TIMER_URGENT : TIMER_DARK);
        gc.fillText(formatTime(roundTimer), faceCX, faceCY);
        gc.restore();
    }


    private String formatTime(double seconds) {
        int total = Math.max(0, (int) Math.ceil(seconds));
        int m = total / 60;
        int s = total % 60;
        return m + ":" + String.format("%02d", s);
    }


    private void triggerRoundPopup(int round) {
        popupRoundNumber = round;
        roundPopupTimer = GameConfig.COUNTDOWN_SECONDS;
        roundPopupActive = true;

        if (countdownAudio != null) {
            countdownAudio.seek(javafx.util.Duration.ZERO);
            countdownAudio.play();
        }
    }

    /** Draws round start popup at screen center. */
    private void drawRoundPopup(GraphicsContext gc, double viewW, double viewH) {
        if (!roundPopupActive) return;

        double centerX = viewW / 2.0;
        double centerY = viewH / 2.0;

        double popupW = 400;
        double popupH = 180;
        double popupX = centerX - popupW / 2.0;
        double popupY = centerY - popupH / 2.0;

        int countdownValue = (int) Math.ceil(roundPopupTimer);
        String countdownText = countdownValue > 0 ? String.valueOf(countdownValue) : "GO!";

        double pulse = 1.0 + 0.15 * Math.sin(roundPopupTimer * Math.PI * 4);
        double countdownScale = pulse;

        gc.setFill(Color.rgb(0, 0, 0, 0.4));
        gc.fillRect(0, 0, viewW, viewH);

        gc.setFill(Color.web("#1c1c26"));
        gc.fillRoundRect(popupX, popupY, popupW, popupH, 12, 12);

        gc.setStroke(Color.web("#c9a84c"));
        gc.setLineWidth(3);
        gc.strokeRoundRect(popupX + 4, popupY + 4, popupW - 8, popupH - 8, 8, 8);

        gc.save();
        gc.setFont(fontRoundPopup);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.web("#e8dfc4"));
        gc.fillText("ROUND " + popupRoundNumber, centerX, popupY + 55);
        gc.restore();

        gc.setStroke(Color.web("#8a6a1a"));
        gc.setLineWidth(2);
        gc.strokeLine(centerX - 80, popupY + 70, centerX + 80, popupY + 70);

        gc.save();
        gc.setFont(loadFont("Press Start 2P", 12));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.web("#7a7060"));
        gc.fillText("starts in", centerX, popupY + 95);
        gc.restore();

        gc.save();
        gc.setFont(Font.font("Press Start 2P", 48 * countdownScale));
        gc.setTextAlign(TextAlignment.CENTER);

        Color countdownColor;
        if (countdownValue == 3) countdownColor = Color.web("#4a8c5c");
        else if (countdownValue == 2) countdownColor = Color.web("#d4a017");
        else if (countdownValue == 1) countdownColor = Color.web("#d04648");
        else countdownColor = Color.web("#c9a84c");

        gc.setFill(countdownColor);
        gc.fillText(countdownText, centerX, popupY + 145);
        gc.restore();
    }


    private void drawFallbackPanel(GraphicsContext gc, double ox, double oy) {
        double s = TIMER_SCALE;
        gc.save();
        gc.setFill(Color.web("#181425")); gc.fillRect(ox, oy, 64*s, 2*s);
        gc.fillRect(ox, oy+2*s, 2*s, 30*s);
        gc.fillRect(ox+62*s, oy+2*s, 2*s, 30*s);
        gc.fillRect(ox+2*s, oy+30*s, 60*s, 2*s);
        gc.setFill(Color.web("#8B9BB4")); gc.fillRect(ox+4*s, oy+4*s, 56*s, 24*s);
        gc.setFill(Color.web("#FFFFFF")); gc.fillRect(ox+4*s, oy+4*s, 56*s, 2*s);
        gc.setFill(Color.web("#8B9BB4")); gc.fillRect(ox+2*s, oy+4*s, 2*s, 24*s);
        gc.fillRect(ox+60*s, oy+4*s, 2*s, 24*s);
        gc.setFill(Color.web("#5A6988")); gc.fillRect(ox+10*s, oy+6*s, 44*s, 4*s);
        gc.setFill(Color.web("#3A4466")); gc.fillRect(ox+8*s,  oy+6*s,  2*s, 4*s);
        gc.fillRect(ox+54*s, oy+6*s, 2*s, 4*s);
        gc.setFill(Color.web("#C0CBDC")); gc.fillRect(ox+8*s, oy+24*s, 48*s, 4*s);
        gc.restore();
    }

    /**
     * Loads a {@link Font} by family name, falling back to the system default
     * if the named font is not available on this JVM.
     */
    private Font loadFont(String family, double size) {
        Font f = Font.font(family, size);
        return f;
    }

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
