package com.identitycrisis.client.scene;

import javafx.scene.Scene;
import javafx.scene.layout.*;
import javafx.scene.control.*;
import javafx.scene.text.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.geometry.*;
import javafx.animation.*;
import javafx.util.Duration;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.paint.CycleMethod;
import javafx.scene.shape.Circle;
import javafx.scene.effect.DropShadow;
import com.identitycrisis.shared.model.GameConfig;

/**
 * Main menu: Play, How to Play, About, Quit.
 * Matches home.html pixel-perfect design.
 */
public class MenuScene {

    private Scene scene;
    private SceneManager sceneManager;

    // Color constants matching CSS
    private static final String GOLD = "#c9a84c";
    private static final String GOLD_LIGHT = "#e8c86a";
    private static final String GOLD_DARK = "#8a6a1a";
    private static final String STONE_DARK = "#0d0d10";
    private static final String STONE_MID = "#1a1a1e";
    private static final String TEXT_PARCHMENT = "#e8dfc4";
    private static final String TEXT_MUTED = "#7a7060";
    private static final String BTN_IDLE = "#3b4663";
    private static final String BTN_BORDER = "#1a2030";

    public MenuScene(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
    }

    public Scene createScene() {
        StackPane root = new StackPane();
        root.setStyle("-fx-background-color: " + STONE_DARK + ";");
        // Let root fill entire scene - no fixed pref size
        root.setAlignment(Pos.CENTER);

        // Background layers - now responsive
        addBackground(root);

        // Torch glows
        addTorchGlows(root);

        // Torch particles
        addTorchParticles(root);

        // Main content area - centered both horizontally and vertically
        VBox mainContent = createMainContent(root);
        mainContent.prefWidthProperty().bind(root.widthProperty());
        mainContent.prefHeightProperty().bind(root.heightProperty());
        root.getChildren().add(mainContent);
        StackPane.setAlignment(mainContent, Pos.CENTER);

        // Corner tags
        addCornerTags(root);

        // Fullscreen button
        addFullscreenButton(root);

        // Mute button
        addMuteButton(root);

        // Scanlines overlay

        addScanlines(root);

        scene = new Scene(root, GameConfig.WINDOW_WIDTH, GameConfig.WINDOW_HEIGHT);
        scene.getStylesheets().add(getClass().getResource("/styles/global.css").toExternalForm());

        return scene;
    }

    private void addBackground(StackPane root) {
        try {
            Image bgImage = new Image(getClass().getResourceAsStream("/bg_img.jpg"));
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
        vignette.setStyle(
                "-fx-background-color: radial-gradient(center 50% 50%, radius 100%, transparent 30%, rgba(0,0,0,0.7) 100%);");
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

    private void addTorchGlows(StackPane root) {
        // Left torch glow - use percent-based positioning
        Pane leftGlow = new Pane();
        leftGlow.setPrefSize(120, 200);
        leftGlow.setStyle(
                "-fx-background-color: radial-gradient(center 50% 50%, radius 50%, rgba(232, 116, 60, 0.15), transparent 70%);");
        leftGlow.setMouseTransparent(true);
        root.getChildren().add(leftGlow);
        StackPane.setAlignment(leftGlow, Pos.TOP_LEFT);
        // Bind translate to root size (5% from left, 30% from top)
        leftGlow.translateXProperty().bind(root.widthProperty().multiply(0.05));
        leftGlow.translateYProperty().bind(root.heightProperty().multiply(0.30));

        // Animate left glow
        Timeline leftFlicker = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(leftGlow.opacityProperty(), 0.7)),
                new KeyFrame(Duration.seconds(1.5), new KeyValue(leftGlow.opacityProperty(), 1.0)));
        leftFlicker.setAutoReverse(true);
        leftFlicker.setCycleCount(Animation.INDEFINITE);
        leftFlicker.play();

        // Right torch glow - use percent-based positioning
        Pane rightGlow = new Pane();
        rightGlow.setPrefSize(120, 200);
        rightGlow.setStyle(
                "-fx-background-color: radial-gradient(center 50% 50%, radius 50%, rgba(232, 116, 60, 0.12), transparent 70%);");
        rightGlow.setMouseTransparent(true);
        root.getChildren().add(rightGlow);
        StackPane.setAlignment(rightGlow, Pos.TOP_RIGHT);
        // Bind translate to root size (negative = from right edge)
        rightGlow.translateXProperty().bind(root.widthProperty().multiply(-0.08));
        rightGlow.translateYProperty().bind(root.heightProperty().multiply(0.25));

        // Animate right glow
        Timeline rightFlicker = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(rightGlow.opacityProperty(), 0.7)),
                new KeyFrame(Duration.seconds(1.8), new KeyValue(rightGlow.opacityProperty(), 1.0)));
        rightFlicker.setAutoReverse(true);
        rightFlicker.setCycleCount(Animation.INDEFINITE);
        rightFlicker.play();
    }

    private void addTorchParticles(StackPane root) {
        for (int i = 0; i < 4; i++) {
            Circle particle = new Circle(1.5, Color.rgb(232, 116, 60));
            particle.setOpacity(0);
            root.getChildren().add(particle);

            boolean isLeft = i < 2;
            // Bind position to root size for fullscreen responsiveness
            final int idx = i;
            particle.translateXProperty().bind(root.widthProperty().multiply(
                    isLeft ? (0.08 + idx * 0.005 - 0.5) : (0.90 - (idx - 2) * 0.005 - 0.5)));
            particle.translateYProperty().bind(root.heightProperty().multiply(
                    isLeft ? (0.32 - idx * 0.02 - 0.5) : (0.28 - (idx - 2) * 0.02 - 0.5)));

            // Flicker animation - adjust Y by fixed pixel amount, not bound value
            Timeline flicker = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(particle.opacityProperty(), 0)),
                    new KeyFrame(Duration.seconds(0.4),
                            new KeyValue(particle.opacityProperty(), 0.8)),
                    new KeyFrame(Duration.seconds(2.0),
                            new KeyValue(particle.opacityProperty(), 0)));
            // Add translateY animation separately to avoid binding conflicts
            flicker.setOnFinished(e -> {
                // Reset animation - will use current bound position
            });
            flicker.setDelay(Duration.seconds(Math.random() * 2));
            flicker.setCycleCount(Animation.INDEFINITE);
            flicker.play();
        }
    }

    private VBox createMainContent(StackPane root) {
        VBox content = new VBox(0);
        content.setAlignment(Pos.CENTER); // Center vertically and horizontally
        // No padding that pushes content down - let it truly center

        // Logo area - pass root for responsive font scaling
        VBox logoArea = createLogoArea(root);
        VBox.setMargin(logoArea, new Insets(0, 0, 20, 0));

        // Player badge
        Label playerBadge = new Label("⚔ 4+ PLAYERS  ·  ARENA BATTLE");
        playerBadge.setStyle(
                "-fx-font-family: 'Press Start 2P', monospace;" +
                        "-fx-font-size: 7px;" +
                        "-fx-text-fill: " + GOLD + ";" +
                        "-fx-background-color: rgba(0,0,0,0.6);" +
                        "-fx-border-color: " + GOLD_DARK + ";" +
                        "-fx-border-width: 1px;" +
                        "-fx-padding: 5px 10px;" +
                        "-fx-letter-spacing: 2px;");
        playerBadge.setOpacity(0.8);
        VBox.setMargin(playerBadge, new Insets(0, 0, 20, 0));

        // Buttons container
        VBox buttonsBox = createButtons();
        VBox.setMargin(buttonsBox, new Insets(0, 0, 20, 0));

        content.getChildren().addAll(logoArea, playerBadge, buttonsBox);

        // Entrance animation
        content.setOpacity(0);
        content.setTranslateY(20);
        Timeline entrance = new Timeline(
                new KeyFrame(Duration.seconds(0.3),
                        new KeyValue(content.opacityProperty(), 0),
                        new KeyValue(content.translateYProperty(), 20)),
                new KeyFrame(Duration.seconds(1.0),
                        new KeyValue(content.opacityProperty(), 1),
                        new KeyValue(content.translateYProperty(), 0)));
        entrance.play();

        return content;
    }

    private VBox createLogoArea(StackPane root) {
        VBox logoArea = new VBox(8);
        logoArea.setAlignment(Pos.CENTER);

        // ── Logo image (skull) ────────────────────────────────────────────────
        try (var is = getClass().getResourceAsStream("/logo.png")) {
            if (is != null) {
                ImageView logoView = new ImageView(new Image(is));
                logoView.setFitWidth(96);
                logoView.setFitHeight(96);
                logoView.setPreserveRatio(true);
                logoView.setSmooth(true);
                // Gold drop-shadow to match the scene palette
                DropShadow logoGlow = new DropShadow();
                logoGlow.setColor(Color.rgb(201, 168, 76, 0.65));
                logoGlow.setRadius(28);
                logoGlow.setSpread(0.15);
                logoView.setEffect(logoGlow);

                // Subtle floating animation
                TranslateTransition float_ = new TranslateTransition(Duration.seconds(2.5), logoView);
                float_.setFromY(-4);
                float_.setToY(4);
                float_.setAutoReverse(true);
                float_.setCycleCount(Animation.INDEFINITE);
                float_.play();

                VBox.setMargin(logoView, new Insets(0, 0, 10, 0));
                logoArea.getChildren().add(logoView);
            }
        } catch (Exception ignored) {
        }

        // Title image
        try (var titleIs = getClass().getResourceAsStream("/title.png")) {
            if (titleIs != null) {
                ImageView titleView = new ImageView(new Image(titleIs));
                // Responsive width: 55% of stage width, capped at 560px, min 280px
                titleView.fitWidthProperty().bind(
                        javafx.beans.binding.Bindings.createDoubleBinding(
                                () -> Math.max(280, Math.min(560, root.widthProperty().get() * 0.55)),
                                root.widthProperty()));
                titleView.setPreserveRatio(true);
                titleView.setSmooth(true);

                DropShadow titleGlow = new DropShadow();
                titleGlow.setColor(Color.rgb(201, 168, 76, 0.65));
                titleGlow.setRadius(36);
                titleGlow.setSpread(0.18);
                titleView.setEffect(titleGlow);

                // Subtle floating animation (same as logo)
                TranslateTransition float2 = new TranslateTransition(Duration.seconds(3.0), titleView);
                float2.setFromY(-3);
                float2.setToY(3);
                float2.setAutoReverse(true);
                float2.setCycleCount(Animation.INDEFINITE);
                float2.play();

                VBox.setMargin(titleView, new Insets(0, 0, 10, 0));
                logoArea.getChildren().add(titleView);
            }
        } catch (Exception ignored) {
        }

        // Gold divider line - scale width with screen
        HBox divider = new HBox();
        divider.prefWidthProperty().bind(root.widthProperty().multiply(0.25).add(80)); // 25% of width + base
        divider.setMaxWidth(400);
        divider.setMinWidth(200);
        divider.setPrefHeight(1);
        divider.setStyle("-fx-background-color: linear-gradient(to right, transparent, " + GOLD + ", transparent);");
        divider.setAlignment(Pos.CENTER);

        // Diamond ornament
        Text diamond = new Text("◆");
        diamond.setStyle("-fx-fill: " + GOLD + "; -fx-font-size: 10px;");
        diamond.setTranslateY(-8);
        StackPane dividerWithDiamond = new StackPane(divider, diamond);
        dividerWithDiamond.maxWidthProperty().bind(divider.prefWidthProperty());

        // Tagline - scale font size slightly
        Text tagline = new Text("Who will survive the arena?");
        tagline.styleProperty().bind(
                javafx.beans.binding.Bindings.createStringBinding(
                        () -> String.format(
                                "-fx-font-family: 'Crimson Pro', serif;" +
                                        "-fx-font-style: italic;" +
                                        "-fx-font-size: %.0fpx;" +
                                        "-fx-fill: %s;" +
                                        "-fx-letter-spacing: 2px;",
                                Math.max(13, Math.min(18, root.heightProperty().get() * 0.018)),
                                TEXT_MUTED),
                        root.heightProperty()));
        VBox.setMargin(tagline, new Insets(12, 0, 0, 0));

        logoArea.getChildren().addAll(tagline);
        return logoArea;
    }

    private VBox createButtons() {
        VBox buttonsBox = new VBox(10);
        buttonsBox.setAlignment(Pos.CENTER);

        // Play button (gold variant)
        Button playBtn = createPixelButton("▶  Play", true);
        playBtn.setOnAction(e -> onPlayClicked());

        // How to Play button
        Button howToPlayBtn = createPixelButton("?  How to Play", false);
        howToPlayBtn.setOnAction(e -> onHowToPlayClicked());

        // About button
        Button aboutBtn = createPixelButton("✦  About", false);
        aboutBtn.setOnAction(e -> onAboutClicked());

        // Quit button (red gradient variant)
        Button quitBtn = createQuitButton("✕  Quit");
        quitBtn.setOnAction(e -> onQuitClicked());

        buttonsBox.getChildren().addAll(playBtn, howToPlayBtn, aboutBtn, quitBtn);
        return buttonsBox;
    }

    private Button createPixelButton(String text, boolean isPlayButton) {
        Button btn = new Button(text);
        btn.setPrefSize(240, 52);
        btn.setMinSize(240, 52);
        btn.setMaxSize(240, 52);

        String bgColor = isPlayButton ? "#4a3a1a" : BTN_IDLE;
        String borderColor = isPlayButton ? "#1a0e00" : BTN_BORDER;
        String textColor = isPlayButton ? GOLD_LIGHT : TEXT_PARCHMENT;

        btn.setStyle(
                "-fx-font-family: 'Cinzel', serif;" +
                        "-fx-font-size: " + (isPlayButton ? "15px" : "14px") + ";" +
                        "-fx-font-weight: 700;" +
                        "-fx-text-fill: " + textColor + ";" +
                        "-fx-background-color: " + bgColor + ";" +
                        "-fx-border-color: " + borderColor + ";" +
                        "-fx-border-width: 2px;" +
                        "-fx-cursor: hand;");

        // Hover effects
        btn.setOnMouseEntered(e -> {
            String hoverBg = isPlayButton ? "#5c4920" : "#4a567a";
            btn.setStyle(
                    "-fx-font-family: 'Cinzel', serif;" +
                            "-fx-font-size: " + (isPlayButton ? "15px" : "14px") + ";" +
                            "-fx-font-weight: 700;" +
                            "-fx-text-fill: white;" +
                            "-fx-background-color: " + hoverBg + ";" +
                            "-fx-border-color: " + borderColor + ";" +
                            "-fx-border-width: 2px;" +
                            "-fx-cursor: hand;");
        });

        btn.setOnMouseExited(e -> {
            btn.setStyle(
                    "-fx-font-family: 'Cinzel', serif;" +
                            "-fx-font-size: " + (isPlayButton ? "15px" : "14px") + ";" +
                            "-fx-font-weight: 700;" +
                            "-fx-text-fill: " + textColor + ";" +
                            "-fx-background-color: " + bgColor + ";" +
                            "-fx-border-color: " + borderColor + ";" +
                            "-fx-border-width: 2px;" +
                            "-fx-cursor: hand;");
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

    private Button createQuitButton(String text) {
        Button btn = new Button(text);
        btn.setPrefSize(240, 52);
        btn.setMinSize(240, 52);
        btn.setMaxSize(240, 52);

        // Crimson red gradient colors
        String bgColor = "#5c1a1a";
        String borderColor = "#2a0a0a";
        String textColor = "#e8a4a4";

        btn.setStyle(
                "-fx-font-family: 'Cinzel', serif;" +
                        "-fx-font-size: 14px;" +
                        "-fx-font-weight: 700;" +
                        "-fx-text-fill: " + textColor + ";" +
                        "-fx-background-color: " + bgColor + ";" +
                        "-fx-border-color: " + borderColor + ";" +
                        "-fx-border-width: 2px;" +
                        "-fx-cursor: hand;");

        // Hover effects
        btn.setOnMouseEntered(e -> {
            String hoverBg = "#702020";
            btn.setStyle(
                    "-fx-font-family: 'Cinzel', serif;" +
                            "-fx-font-size: 14px;" +
                            "-fx-font-weight: 700;" +
                            "-fx-text-fill: white;" +
                            "-fx-background-color: " + hoverBg + ";" +
                            "-fx-border-color: " + borderColor + ";" +
                            "-fx-border-width: 2px;" +
                            "-fx-cursor: hand;");
        });

        btn.setOnMouseExited(e -> {
            btn.setStyle(
                    "-fx-font-family: 'Cinzel', serif;" +
                            "-fx-font-size: 14px;" +
                            "-fx-font-weight: 700;" +
                            "-fx-text-fill: " + textColor + ";" +
                            "-fx-background-color: " + bgColor + ";" +
                            "-fx-border-color: " + borderColor + ";" +
                            "-fx-border-width: 2px;" +
                            "-fx-cursor: hand;");
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

    private void addCornerTags(StackPane root) {
        // Course tag (bottom left)
        Label courseTag = new Label("CMSC 137 · AY 2025-2026");
        courseTag.setStyle(
                "-fx-font-family: 'Press Start 2P', monospace;" +
                        "-fx-font-size: 7px;" +
                        "-fx-text-fill: " + TEXT_MUTED + ";" +
                        "-fx-opacity: 0.5;");
        StackPane.setAlignment(courseTag, Pos.BOTTOM_LEFT);
        // Use percent-based margins for fullscreen
        courseTag.translateXProperty().bind(root.widthProperty().multiply(0.02));
        courseTag.translateYProperty().bind(root.heightProperty().multiply(-0.02));
        root.getChildren().add(courseTag);

        // Version tag (bottom right)
        Label versionTag = new Label("v1.0.0");
        versionTag.setStyle(
                "-fx-font-family: 'Press Start 2P', monospace;" +
                        "-fx-font-size: 7px;" +
                        "-fx-text-fill: " + TEXT_MUTED + ";" +
                        "-fx-opacity: 0.5;");
        StackPane.setAlignment(versionTag, Pos.BOTTOM_RIGHT);
        // Use percent-based margins for fullscreen
        versionTag.translateXProperty().bind(root.widthProperty().multiply(-0.02));
        versionTag.translateYProperty().bind(root.heightProperty().multiply(-0.02));
        root.getChildren().add(versionTag);
    }

    private void addFullscreenButton(StackPane root) {
        Button fullscreenBtn = new Button(sceneManager.isFullscreen() ? "⛶" : "⛶");
        fullscreenBtn.setPrefSize(32, 32);
        fullscreenBtn.setMinSize(32, 32);
        fullscreenBtn.setMaxSize(32, 32);
        fullscreenBtn.setStyle(
                "-fx-font-family: 'Press Start 2P', monospace;" +
                        "-fx-font-size: 12px;" +
                        "-fx-text-fill: " + GOLD + ";" +
                        "-fx-background-color: " + STONE_MID + ";" +
                        "-fx-border-color: " + GOLD_DARK + ";" +
                        "-fx-border-width: 1px;" +
                        "-fx-cursor: hand;");

        fullscreenBtn.setOnMouseEntered(e -> {
            fullscreenBtn.setStyle(
                    "-fx-font-family: 'Press Start 2P', monospace;" +
                            "-fx-font-size: 12px;" +
                            "-fx-text-fill: " + GOLD + ";" +
                            "-fx-background-color: rgba(201, 168, 76, 0.1);" +
                            "-fx-border-color: " + GOLD + ";" +
                            "-fx-border-width: 1px;" +
                            "-fx-cursor: hand;");
        });

        fullscreenBtn.setOnMouseExited(e -> {
            fullscreenBtn.setStyle(
                    "-fx-font-family: 'Press Start 2P', monospace;" +
                            "-fx-font-size: 12px;" +
                            "-fx-text-fill: " + GOLD + ";" +
                            "-fx-background-color: " + STONE_MID + ";" +
                            "-fx-border-color: " + GOLD_DARK + ";" +
                            "-fx-border-width: 1px;" +
                            "-fx-cursor: hand;");
        });

        fullscreenBtn.setOnAction(e -> sceneManager.toggleFullscreen());

        StackPane.setAlignment(fullscreenBtn, Pos.TOP_RIGHT);
        StackPane.setMargin(fullscreenBtn, new Insets(20, 20, 0, 0));
        root.getChildren().add(fullscreenBtn);
    }

    private void addMuteButton(StackPane root) {
        var audioManager = sceneManager.getAudioManager();

        Image onIcon = new Image(getClass().getResourceAsStream("/sprites/ui/volume_on.png"));
        Image offIcon = new Image(getClass().getResourceAsStream("/sprites/ui/volume_off.png"));

        ImageView iconView = new ImageView(audioManager.isMuted() ? offIcon : onIcon);
        iconView.setFitWidth(18);
        iconView.setFitHeight(18);
        iconView.setPreserveRatio(true);

        Button muteBtn = new Button();
        muteBtn.setGraphic(iconView);
        muteBtn.setPrefSize(32, 32);
        muteBtn.setMinSize(32, 32);
        muteBtn.setMaxSize(32, 32);
        muteBtn.setStyle(muteButtonStyle(false));

        muteBtn.setOnMouseEntered(e -> muteBtn.setStyle(muteButtonStyle(true)));
        muteBtn.setOnMouseExited(e -> muteBtn.setStyle(muteButtonStyle(false)));

        muteBtn.setOnAction(e -> {
            audioManager.toggleMute();
            iconView.setImage(audioManager.isMuted() ? offIcon : onIcon);
        });

        StackPane.setAlignment(muteBtn, Pos.TOP_LEFT);
        StackPane.setMargin(muteBtn, new Insets(20, 0, 0, 20));
        root.getChildren().add(muteBtn);
    }

    private String muteButtonStyle(boolean hover) {
        return "-fx-font-family: 'Segoe UI Symbol', 'Press Start 2P', monospace;" +
                "-fx-font-size: 14px;" +
                "-fx-text-fill: " + GOLD + ";" +
                "-fx-background-color: " + (hover ? "rgba(201,168,76,0.1)" : STONE_MID) + ";" +
                "-fx-border-color: " + (hover ? GOLD : GOLD_DARK) + ";" +
                "-fx-border-width: 1px;" +
                "-fx-cursor: hand;";
    }

    private void addScanlines(StackPane root) {
        Pane scanlines = new Pane();
        // Bind to root size for fullscreen
        scanlines.prefWidthProperty().bind(root.widthProperty());
        scanlines.prefHeightProperty().bind(root.heightProperty());
        // Use semi-transparent overlay instead of repeating-linear-gradient (not
        // supported in JavaFX)
        scanlines.setStyle("-fx-background-color: rgba(0,0,0,0.02);");
        scanlines.setMouseTransparent(true);
        root.getChildren().add(scanlines);
    }

    private void onPlayClicked() {
        sceneManager.showCreateOrJoin();
    }

    private void onHowToPlayClicked() {
        sceneManager.showHowToPlay();
    }

    private void onAboutClicked() {
        sceneManager.showAbout();
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
