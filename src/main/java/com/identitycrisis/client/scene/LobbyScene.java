package com.identitycrisis.client.scene;

import javafx.scene.Scene;
import javafx.scene.layout.*;
import javafx.scene.control.*;
import javafx.geometry.*;
import javafx.animation.*;
import javafx.util.Duration;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import com.identitycrisis.shared.model.GameConfig;
import com.identitycrisis.client.render.SpriteManager;
import com.identitycrisis.client.net.GameClient;

/**
 * Lobby/waiting screen — displays while players gather before game start.
 * Animated crest, dynamic player-count donut ring, rotating tips.
 */
public class LobbyScene {

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
    private static final String STONE_BORDER = "#2a2a36";

    private Canvas donutCanvas;
    private int playerCount = 1;
    private Label playerCountLabel;
    private Label lobbyFullLabel;
    private SpriteManager spriteManager;
    private HBox playerCardsBox;
    private boolean myReadySent = false;
    private Label tipLabel;
    private int tipIndex = 0;
    private Timeline tipRotation;
    private Label roomCodeLabel;
    private String roomCode;
    private Button startBtn;

    private final String[] tips = {
        "Carry a teammate to safety — but you can't enter the safe zone while holding them.",
        "When controls are reversed, stay calm. Panic kills faster than the chaos.",
        "Safe zones shrink each round. Position yourself early.",
        "The decoy safe zones look identical. Watch other players — or trust your instincts.",
        "In Round 3+, only n-1 players survive. One must be eliminated.",
        "You can throw carried players into the safe zone before entering yourself.",
        "Control swaps are unpredictable. Learn multiple playstyles."
    };

    public LobbyScene(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
    }

    public Scene createScene() {
        spriteManager = new SpriteManager();
        spriteManager.loadAll();

        StackPane root = new StackPane();
        root.setStyle("-fx-background-color: " + STONE_DARK + ";");
        root.setPrefSize(GameConfig.WINDOW_WIDTH, GameConfig.WINDOW_HEIGHT);

        // Stone tile pattern
        addStonePattern(root);

        // Corner torch glows
        addCornerGlows(root);

        // Back button (top-left)
        addBackButton(root);

        // Main content
        VBox content = createContent();
        root.getChildren().add(content);

        // Scanlines
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

    private VBox createContent() {
        VBox content = new VBox(0);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(0, 24, 0, 24));
        content.setMaxWidth(960);

        // Room code display (generated on scene load)
        roomCodeLabel = new Label("ROOM CODE: ------");
        roomCodeLabel.setStyle(
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 18px;" +
            "-fx-text-fill: " + GOLD + ";" +
            "-fx-letter-spacing: 4px;"
        );
        roomCodeLabel.setEffect(new javafx.scene.effect.DropShadow(30, Color.rgb(201, 168, 76, 0.5)));
        VBox.setMargin(roomCodeLabel, new Insets(0, 0, 16, 0));

        // Enlarged waiting text (title-level weight using Press Start 2P)
        Label waitingText = new Label("WAITING FOR PLAYERS...");
        waitingText.setStyle(
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 16px;" +
            "-fx-text-fill: " + GOLD + ";" +
            "-fx-letter-spacing: 4px;"
        );
        waitingText.setEffect(new javafx.scene.effect.DropShadow(30, Color.rgb(201, 168, 76, 0.5)));
        VBox.setMargin(waitingText, new Insets(0, 0, 32, 0));

        // Player cards section
        VBox donutSection = createPlayerCardsSection();
        VBox.setMargin(donutSection, new Insets(0, 0, 0, 0));

        // Start Game button — sends C_READY to server (server triggers game start)
        startBtn = createPixelButton("▶  Start Game");
        startBtn.setOnAction(e -> {
            GameClient gc = sceneManager.getGameClient();
            if (gc != null && gc.isConnected()) {
                gc.sendReady();
            } else {
                sceneManager.showLoading();
            }
        });
        VBox.setMargin(startBtn, new Insets(20, 0, 0, 0));

        // Tip box
        VBox tipBox = createTipBox();
        VBox.setMargin(tipBox, new Insets(28, 0, 0, 0));

        content.getChildren().addAll(roomCodeLabel, waitingText, donutSection, startBtn, tipBox);
        return content;
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
        backBtn.setOnAction(e -> {
            // Leaving the lobby ends the session for this client (and the room,
            // if we are the host). Release sockets and the embedded server.
            sceneManager.shutdownNetwork();
            sceneManager.showCreateOrJoin();
        });

        StackPane.setAlignment(backBtn, Pos.TOP_LEFT);
        StackPane.setMargin(backBtn, new Insets(20, 0, 0, 20));
        root.getChildren().add(backBtn);
    }


    private Button createPixelButton(String text) {
        Button btn = new Button(text);
        btn.setPrefSize(240, 52);
        btn.setMinSize(240, 52);
        btn.setMaxSize(240, 52);
        btn.setStyle(
            "-fx-font-family: 'Cinzel', serif;" +
            "-fx-font-size: 15px;" +
            "-fx-font-weight: 700;" +
            "-fx-text-fill: " + GOLD_LIGHT + ";" +
            "-fx-background-color: #4a3a1a;" +
            "-fx-border-color: #1a0e00;" +
            "-fx-border-width: 2px;" +
            "-fx-cursor: hand;"
        );
        btn.setOnMouseEntered(e -> btn.setStyle(
            "-fx-font-family: 'Cinzel', serif;" +
            "-fx-font-size: 15px;" +
            "-fx-font-weight: 700;" +
            "-fx-text-fill: white;" +
            "-fx-background-color: #5c4920;" +
            "-fx-border-color: #1a0e00;" +
            "-fx-border-width: 2px;" +
            "-fx-cursor: hand;"
        ));
        btn.setOnMouseExited(e -> btn.setStyle(
            "-fx-font-family: 'Cinzel', serif;" +
            "-fx-font-size: 15px;" +
            "-fx-font-weight: 700;" +
            "-fx-text-fill: " + GOLD_LIGHT + ";" +
            "-fx-background-color: #4a3a1a;" +
            "-fx-border-color: #1a0e00;" +
            "-fx-border-width: 2px;" +
            "-fx-cursor: hand;"
        ));
        btn.setOnMousePressed(e -> { btn.setScaleX(0.97); btn.setScaleY(0.97); btn.setTranslateY(2); });
        btn.setOnMouseReleased(e -> { btn.setScaleX(1.0); btn.setScaleY(1.0); btn.setTranslateY(0); });
        return btn;
    }

    private VBox createPlayerCardsSection() {
        VBox container = new VBox(10);
        container.setAlignment(Pos.CENTER);

        playerCardsBox = new HBox(18);
        playerCardsBox.setAlignment(Pos.CENTER);
        playerCardsBox.setPadding(new Insets(14));
        playerCardsBox.setMinWidth(680);

        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(playerCardsBox);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scroll.setPrefHeight(250);
        scroll.setMaxWidth(960);

        playerCountLabel = new Label("1 / " + GameConfig.MAX_PLAYERS + " PLAYERS");
        playerCountLabel.setStyle(
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 10px;" +
            "-fx-text-fill: " + GOLD + ";" +
            "-fx-letter-spacing: 2px;"
        );

        lobbyFullLabel = new Label("▶  LOBBY FULL  ◀");
        lobbyFullLabel.setStyle(
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 9px;" +
            "-fx-text-fill: " + GOLD_LIGHT + ";" +
            "-fx-letter-spacing: 2px;"
        );
        lobbyFullLabel.setVisible(false);
        lobbyFullLabel.setManaged(false);

        container.getChildren().addAll(scroll, playerCountLabel, lobbyFullLabel);
        return container;
    }

    /** Updates the lobby UI with the current player list from the server. */
    public void setLobbyPlayers(String[] names, boolean[] ready, int myIndex) {
        if (playerCardsBox == null) return;
        javafx.application.Platform.runLater(() -> {
            playerCardsBox.getChildren().clear();
            int count = (names != null) ? names.length : 0;
            this.playerCount = Math.max(1, Math.min(count, GameConfig.MAX_PLAYERS));
            if (playerCountLabel != null) {
                playerCountLabel.setText(playerCount + " / " + GameConfig.MAX_PLAYERS + " PLAYERS");
            }
            boolean full = (playerCount >= GameConfig.MAX_PLAYERS);
            if (lobbyFullLabel != null) {
                lobbyFullLabel.setVisible(full);
                lobbyFullLabel.setManaged(full);
            }
            if (names != null) {
                for (int i = 0; i < names.length; i++) {
                    int spriteIdx = (i % 8) + 1;
                    boolean isMe = (i == myIndex);
                    boolean isReady = (ready != null && i < ready.length && ready[i]);
                    playerCardsBox.getChildren().add(buildPlayerCard(names[i], spriteIdx, isReady, isMe));
                }
            }
        });
    }

    private VBox buildPlayerCard(String name, int spriteIdx, boolean isReady, boolean isMe) {
        String borderColor = isReady ? "#63C74D" : (isMe ? GOLD : STONE_BORDER);
        String borderWidth = isMe ? "2px" : "1px";

        VBox card = new VBox(7);
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(160);
        card.setMinWidth(160);
        card.setMinHeight(215);
        card.setPadding(new Insets(14, 10, 14, 10));
        card.setStyle(
            "-fx-background-color: " + STONE_PANEL + ";" +
            "-fx-border-color: " + borderColor + ";" +
            "-fx-border-width: " + borderWidth + ";"
        );

        if (spriteManager != null) {
            Image idleSheet = spriteManager.get("player_" + spriteIdx + "_idle");
            if (idleSheet != null) {
                ImageView sprite = new ImageView(idleSheet);
                sprite.setViewport(new javafx.geometry.Rectangle2D(0, 0, 32, 32));
                sprite.setFitWidth(120);
                sprite.setFitHeight(120);
                sprite.setSmooth(false);
                card.getChildren().add(sprite);
            } else {
                Pane ph = new Pane();
                ph.setPrefSize(120, 120);
                ph.setStyle("-fx-background-color: #3a3a50;");
                card.getChildren().add(ph);
            }
        }

        if (isMe) {
            Label youLabel = new Label("YOU");
            youLabel.setMaxWidth(Double.MAX_VALUE);
            youLabel.setAlignment(Pos.CENTER);
            youLabel.setStyle(
                "-fx-font-family: 'Press Start 2P', monospace;" +
                "-fx-font-size: 8px;" +
                "-fx-text-fill: " + GOLD + ";" +
                "-fx-letter-spacing: 1px;"
            );
            card.getChildren().add(youLabel);
        }

        Label nameLabel = new Label(name != null ? name : "Player");
        nameLabel.setMaxWidth(Double.MAX_VALUE);
        nameLabel.setAlignment(Pos.CENTER);
        nameLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        nameLabel.setStyle(
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 7px;" +
            "-fx-text-fill: " + TEXT_PARCHMENT + ";" +
            "-fx-letter-spacing: 1px;"
        );
        card.getChildren().add(nameLabel);

        if (isReady) {
            Label readyLbl = new Label("✓ READY");
            readyLbl.setMaxWidth(Double.MAX_VALUE);
            readyLbl.setAlignment(Pos.CENTER);
            readyLbl.setStyle(
                "-fx-font-family: 'Press Start 2P', monospace;" +
                "-fx-font-size: 7px;" +
                "-fx-text-fill: #63C74D;"
            );
            card.getChildren().add(readyLbl);
        } else if (isMe && !sceneManager.isHost()) {
            Button readyBtn = new Button("READY UP");
            readyBtn.setMaxWidth(Double.MAX_VALUE);
            readyBtn.setStyle(
                "-fx-font-family: 'Press Start 2P', monospace;" +
                "-fx-font-size: 7px;" +
                "-fx-text-fill: " + GOLD + ";" +
                "-fx-background-color: #1e2e1e;" +
                "-fx-border-color: #63C74D;" +
                "-fx-border-width: 1px;" +
                "-fx-cursor: hand;" +
                "-fx-padding: 4px 6px;"
            );
            readyBtn.setDisable(myReadySent);
            readyBtn.setOnAction(e -> {
                GameClient gc2 = sceneManager.getGameClient();
                if (gc2 != null && gc2.isConnected()) {
                    gc2.sendReady();
                    myReadySent = true;
                    readyBtn.setDisable(true);
                }
            });
            card.getChildren().add(readyBtn);
        } else if (!isMe) {
            Label notReadyLbl = new Label("NOT READY");
            notReadyLbl.setMaxWidth(Double.MAX_VALUE);
            notReadyLbl.setAlignment(Pos.CENTER);
            notReadyLbl.setStyle(
                "-fx-font-family: 'Press Start 2P', monospace;" +
                "-fx-font-size: 7px;" +
                "-fx-text-fill: " + TEXT_MUTED + ";"
            );
            card.getChildren().add(notReadyLbl);
        }

        return card;
    }

    private void drawDonut(int count) {
        GraphicsContext gc = donutCanvas.getGraphicsContext2D();
        double W = donutCanvas.getWidth();
        double H = donutCanvas.getHeight();
        double cx = W / 2.0;
        double cy = H / 2.0;
        double outerR = 76.0;
        double innerR = 48.0;
        double gapDeg = (count > 1) ? 5.0 : 0.0;

        gc.clearRect(0, 0, W, H);

        for (int i = 0; i < count; i++) {
            double sliceAngle = 360.0 / count;
            double startAngle = 90.0 + i * sliceAngle + gapDeg / 2.0;
            double extent = sliceAngle - gapDeg;

            // Outer glow pass — semi-transparent, slightly oversized
            gc.setFill(Color.rgb(201, 168, 76, 0.22));
            gc.fillArc(cx - outerR - 5, cy - outerR - 5,
                       (outerR + 5) * 2, (outerR + 5) * 2,
                       startAngle, extent, ArcType.ROUND);

            // Main gold arc
            gc.setFill(Color.web(GOLD));
            gc.fillArc(cx - outerR, cy - outerR, outerR * 2, outerR * 2,
                       startAngle, extent, ArcType.ROUND);

            // Inner highlight — lighter gold strip near outer edge
            gc.setFill(Color.rgb(232, 200, 106, 0.30));
            gc.fillArc(cx - outerR + 5, cy - outerR + 5,
                       (outerR - 5) * 2, (outerR - 5) * 2,
                       startAngle + 3, Math.max(extent - 6, 1.0), ArcType.ROUND);
        }

        // Donut hole — paint center with background color
        gc.setFill(Color.web(STONE_DARK));
        gc.fillOval(cx - innerR, cy - innerR, innerR * 2, innerR * 2);

        // Thin inner ring border
        gc.setStroke(Color.web(GOLD_DARK));
        gc.setLineWidth(1.0);
        gc.strokeOval(cx - innerR, cy - innerR, innerR * 2, innerR * 2);
    }

    private void updatePlayerCount() {
        drawDonut(playerCount);
        playerCountLabel.setText(playerCount + " / " + GameConfig.MAX_PLAYERS + " PLAYERS");
        boolean isFull = playerCount >= GameConfig.MAX_PLAYERS;
        lobbyFullLabel.setVisible(isFull);
        lobbyFullLabel.setManaged(isFull);
    }

    private VBox createTipBox() {
        VBox tipBox = new VBox(10);
        tipBox.setAlignment(Pos.CENTER);
        tipBox.setPadding(new Insets(20, 0, 0, 0));
        tipBox.setStyle("-fx-border-color: " + GOLD_DARK + " transparent transparent transparent; -fx-border-width: 1px 0 0 0;");
        tipBox.setMaxWidth(400);

        Label tipLabelTitle = new Label("◆   TIP   ◆");
        tipLabelTitle.setStyle(
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 6px;" +
            "-fx-text-fill: " + GOLD_DARK + ";" +
            "-fx-letter-spacing: 3px;"
        );

        tipLabel = new Label(tips[0]);
        tipLabel.setWrapText(true);
        tipLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        tipLabel.setStyle(
            "-fx-font-family: 'Crimson Pro', serif;" +
            "-fx-font-style: italic;" +
            "-fx-font-size: 15px;" +
            "-fx-text-fill: " + TEXT_MUTED + ";"
        );

        tipBox.getChildren().addAll(tipLabelTitle, tipLabel);
        return tipBox;
    }

    private void addScanlines(StackPane root) {
        Pane scanlines = new Pane();
        scanlines.setPrefSize(GameConfig.WINDOW_WIDTH, GameConfig.WINDOW_HEIGHT);
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

    /**
     * Called when entering the lobby scene.
     * Resets to 1 player, displays the room code from SceneManager, and starts
     * tip rotation.
     *
     * <p>The room code is set by either {@code CreateOrJoinScene.onCreateClicked}
     * (host) or {@code JoinRoomScene.onJoinClicked} (joiner) — see those
     * scenes for the producer side. If neither ran (e.g. scene opened directly
     * during dev), a placeholder is shown.
     */
    public void onEnter() {
        tipIndex = 0;
        playerCount = 1;
        myReadySent = false;

        // Pull the real room code from the SceneManager (populated by the
        // Create or Join flow). Falls back to placeholder if absent.
        roomCode = sceneManager.getRoomCode();
        if (roomCodeLabel != null) {
            roomCodeLabel.setText("ROOM CODE: " + (roomCode != null ? roomCode : "------"));
        }
        if (playerCardsBox != null) {
            playerCardsBox.getChildren().clear();
        }
        if (playerCountLabel != null) {
            playerCountLabel.setText("1 / " + GameConfig.MAX_PLAYERS + " PLAYERS");
        }
        if (lobbyFullLabel != null) {
            lobbyFullLabel.setVisible(false);
            lobbyFullLabel.setManaged(false);
        }

        if (startBtn != null) {
            boolean isHost = sceneManager.isHost();
            startBtn.setVisible(isHost);
            startBtn.setManaged(isHost);
        }

        // Tip rotation
        tipRotation = new Timeline(
            new KeyFrame(Duration.seconds(4), e -> {
                tipIndex = (tipIndex + 1) % tips.length;
                tipLabel.setText(tips[tipIndex]);
                FadeTransition fadeIn = new FadeTransition(Duration.millis(250), tipLabel);
                fadeIn.setFromValue(0);
                fadeIn.setToValue(1);
                fadeIn.play();
            })
        );
        tipRotation.setCycleCount(Animation.INDEFINITE);
        tipRotation.play();
    }

    /** Stops tip rotation. Call before leaving this scene. */
    public void onExit() {
        if (tipRotation != null) tipRotation.stop();
    }

    /** Called by the network layer with the real player count from the server. */
    public void setPlayerCount(int count) {
        this.playerCount = Math.max(1, Math.min(count, GameConfig.MAX_PLAYERS));
        if (donutCanvas != null) updatePlayerCount();
    }

    private void onReadyClicked() {
        // Ready functionality for multiplayer lobby
    }

    public void refreshLobbyDisplay() {
        // Update player list display
    }

    // Legacy method for compatibility
    public Scene getScene() {
        if (scene == null) {
            scene = createScene();
        }
        return scene;
    }
}
