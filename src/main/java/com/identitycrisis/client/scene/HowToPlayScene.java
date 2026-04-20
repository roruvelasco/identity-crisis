package com.identitycrisis.client.scene;

import javafx.scene.Scene;
import javafx.scene.layout.*;
import javafx.scene.control.*;
import javafx.scene.text.*;
import javafx.geometry.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import com.identitycrisis.shared.model.GameConfig;

/**
 * How to Play screen matching how-to-play.html pixel-perfect design.
 * Sections: Controls, Round Phases, Chaos Events, Carry Mechanics.
 */
public class HowToPlayScene {

    private Scene scene;
    private SceneManager sceneManager;

    // Color constants
    private static final String GOLD = "#c9a84c";
    private static final String GOLD_DARK = "#8a6a1a";
    private static final String STONE_DARK = "#0d0d10";
    private static final String STONE_PANEL = "#1c1c26";
    private static final String STONE_BORDER = "#2a2a36";
    private static final String TEXT_PARCHMENT = "#e8dfc4";
    private static final String TEXT_MUTED = "#7a7060";
    private static final String TEXT_BODY = "#b0a890";
    private static final String CHAOS_PURPLE = "#7c5cbf";
    private static final String SAFE_GREEN = "#4a8c5c";
    private static final String DANGER_RED = "#8c3a3a";

    public HowToPlayScene(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
    }

    public Scene createScene() {
        StackPane root = new StackPane();
        root.setStyle("-fx-background-color: " + STONE_DARK + ";");
        // Responsive - fill entire scene
        root.setAlignment(Pos.CENTER);

        // Stone tile pattern background
        addStonePattern(root);

        // Torch corner glows
        addTorchGlows(root);

        // Main content scroll - centered and responsive
        ScrollPane scrollPane = createContent(root);
        root.getChildren().add(scrollPane);

        // Scanlines overlay
        addScanlines(root);

        // Fullscreen button
        addFullscreenButton(root);

        scene = new Scene(root, GameConfig.WINDOW_WIDTH, GameConfig.WINDOW_HEIGHT);
        scene.getStylesheets().add(getClass().getResource("/styles/global.css").toExternalForm());

        return scene;
    }

    private void addStonePattern(StackPane root) {
        Pane pattern = new Pane();
        // Bind to root for fullscreen responsiveness
        pattern.prefWidthProperty().bind(root.widthProperty());
        pattern.prefHeightProperty().bind(root.heightProperty());
        // Use solid background color - JavaFX doesn't support multiple gradients in background-image
        pattern.setStyle("-fx-background-color: " + STONE_DARK + ";");
        pattern.setMouseTransparent(true);
        root.getChildren().add(pattern);
    }

    private void addTorchGlows(StackPane root) {
        // Top-left glow - percent-based positioning
        Pane tlGlow = new Pane();
        tlGlow.setPrefSize(250, 250);
        tlGlow.setStyle("-fx-background-color: radial-gradient(center 50% 50%, radius 50%, rgba(232,116,60,0.09), transparent 70%);");
        tlGlow.setMouseTransparent(true);
        StackPane.setAlignment(tlGlow, Pos.TOP_LEFT);
        tlGlow.translateXProperty().bind(root.widthProperty().multiply(-0.05));
        tlGlow.translateYProperty().bind(root.heightProperty().multiply(-0.05));
        root.getChildren().add(tlGlow);

        // Top-right glow - percent-based positioning
        Pane trGlow = new Pane();
        trGlow.setPrefSize(250, 250);
        trGlow.setStyle("-fx-background-color: radial-gradient(center 50% 50%, radius 50%, rgba(232,116,60,0.09), transparent 70%);");
        trGlow.setMouseTransparent(true);
        StackPane.setAlignment(trGlow, Pos.TOP_RIGHT);
        trGlow.translateXProperty().bind(root.widthProperty().multiply(0.05));
        trGlow.translateYProperty().bind(root.heightProperty().multiply(-0.05));
        root.getChildren().add(trGlow);
    }

    private ScrollPane createContent(StackPane root) {
        // Main container that centers everything
        VBox mainContainer = new VBox(0);
        mainContainer.setAlignment(Pos.TOP_CENTER);
        mainContainer.setFillWidth(true);

        // Header - full width
        HBox header = createHeader();
        mainContainer.getChildren().add(header);

        // Content wrapper - centered with max-width 860px
        VBox contentWrapper = new VBox(0);
        contentWrapper.setStyle("-fx-background-color: transparent;");
        contentWrapper.setAlignment(Pos.TOP_CENTER);
        contentWrapper.setMaxWidth(860);
        contentWrapper.setPadding(new Insets(40, 32, 80, 32)); // Match HTML: 40px top, 32px sides, 80px bottom

        // Objective Banner
        VBox objectiveBanner = createObjectiveBanner();
        VBox.setMargin(objectiveBanner, new Insets(0, 0, 40, 0));
        contentWrapper.getChildren().add(objectiveBanner);

        // Controls Section
        VBox controlsSection = createControlsSection();
        VBox.setMargin(controlsSection, new Insets(0, 0, 40, 0));
        contentWrapper.getChildren().add(controlsSection);

        // Round Phases Section
        VBox roundsSection = createRoundsSection();
        VBox.setMargin(roundsSection, new Insets(0, 0, 40, 0));
        contentWrapper.getChildren().add(roundsSection);

        // Chaos Events Section
        VBox chaosSection = createChaosSection();
        VBox.setMargin(chaosSection, new Insets(0, 0, 40, 0));
        contentWrapper.getChildren().add(chaosSection);

        // Carry Mechanics Section
        VBox mechanicsSection = createMechanicsSection();
        contentWrapper.getChildren().add(mechanicsSection);

        // Center the content wrapper
        HBox centeredWrapper = new HBox();
        centeredWrapper.setAlignment(Pos.TOP_CENTER);
        HBox.setHgrow(contentWrapper, Priority.NEVER);
        centeredWrapper.getChildren().add(contentWrapper);
        VBox.setVgrow(centeredWrapper, Priority.ALWAYS);

        mainContainer.getChildren().add(centeredWrapper);

        ScrollPane scrollPane = new ScrollPane(mainContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-vbar-policy: never;");
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setPadding(new Insets(0));
        // Bind to root for fullscreen
        scrollPane.prefWidthProperty().bind(root.widthProperty());
        scrollPane.prefHeightProperty().bind(root.heightProperty());

        return scrollPane;
    }

    private HBox createHeader() {
        HBox header = new HBox(24);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(20, 32, 20, 32));
        header.setStyle("-fx-border-color: transparent transparent " + STONE_BORDER + " transparent; -fx-border-width: 0 0 1px 0; -fx-background-color: rgba(13,13,16,0.9);");
        // Fill available width - make it span full width
        header.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(header, Priority.ALWAYS);

        // Back button
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
        backBtn.setOnAction(e -> sceneManager.showMenu());

        // Title
        Label title = new Label("How to Play");
        title.setStyle(
            "-fx-font-family: 'Cinzel', serif;" +
            "-fx-font-size: 18px;" +
            "-fx-font-weight: 700;" +
            "-fx-text-fill: " + TEXT_PARCHMENT + ";" +
            "-fx-letter-spacing: 4px;"
        );

        // Divider
        HBox divider = new HBox();
        HBox.setHgrow(divider, Priority.ALWAYS);
        divider.setStyle("-fx-background-color: linear-gradient(to right, " + STONE_BORDER + ", transparent);");
        divider.setPrefHeight(1);
        divider.setMaxHeight(1);

        header.getChildren().addAll(backBtn, title, divider);
        return header;
    }

    private VBox createObjectiveBanner() {
        VBox banner = new VBox(10);
        banner.setAlignment(Pos.CENTER);
        banner.setPadding(new Insets(20, 24, 20, 24));
        banner.setStyle(
            "-fx-background-color: rgba(201,168,76,0.08);" +
            "-fx-border-color: rgba(201,168,76,0.25);" +
            "-fx-border-width: 1px;"
        );

        Label label = new Label("◆  OBJECTIVE  ◆");
        label.setStyle(
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 6px;" +
            "-fx-text-fill: " + GOLD_DARK + ";" +
            "-fx-letter-spacing: 3px;"
        );

        Label text = new Label("Reach the safe zone. Survive every round. Be the last one standing.");
        text.setStyle(
            "-fx-font-family: 'Cinzel', serif;" +
            "-fx-font-size: 18px;" +
            "-fx-font-weight: 700;" +
            "-fx-text-fill: " + TEXT_PARCHMENT + ";"
        );
        text.setWrapText(true);
        text.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        banner.getChildren().addAll(label, text);
        return banner;
    }

    private VBox createControlsSection() {
        VBox section = new VBox(18);

        // Section title
        HBox titleRow = createSectionTitle("◆  Controls");
        section.getChildren().add(titleRow);

        // Bindings grid
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setAlignment(Pos.CENTER);

        // WASD binding
        HBox wasdCard = createBindingCard(createWasdVisual(), "Move", "Up / Left / Down / Right");
        grid.add(wasdCard, 0, 0);

        // E key binding
        HBox eCard = createBindingCard(createKeyBadge("E"), "Pick Up", "Carry a nearby player");
        grid.add(eCard, 1, 0);

        // Q key binding
        HBox qCard = createBindingCard(createKeyBadge("Q"), "Throw", "Launch carried player forward");
        grid.add(qCard, 0, 1);

        // Enter key binding
        HBox enterCard = createBindingCard(createKeyBadge("Enter", true), "Chat", "Open / send message");
        grid.add(enterCard, 1, 1);

        section.getChildren().add(grid);
        return section;
    }

    private VBox createWasdVisual() {
        VBox visual = new VBox(3);
        visual.setAlignment(Pos.CENTER);

        // W row
        HBox wRow = new HBox();
        wRow.setAlignment(Pos.CENTER);
        Label w = createWasdKey("W");
        wRow.getChildren().add(w);

        // ASD row
        HBox asdRow = new HBox(3);
        asdRow.setAlignment(Pos.CENTER);
        Label a = createWasdKey("A");
        Label s = createWasdKey("S");
        Label d = createWasdKey("D");
        asdRow.getChildren().addAll(a, s, d);

        visual.getChildren().addAll(wRow, asdRow);
        return visual;
    }

    private Label createWasdKey(String key) {
        Label label = new Label(key);
        label.setPrefSize(32, 32);
        label.setAlignment(Pos.CENTER);
        label.setStyle(
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 8px;" +
            "-fx-text-fill: " + TEXT_PARCHMENT + ";" +
            "-fx-background-color: #1a1a2a;" +
            "-fx-border-color: #3a3a50;" +
            "-fx-border-width: 1px;" +
            "-fx-border-style: solid solid solid solid;" +
            "-fx-border-width: 1 1 3 1;" +
            "-fx-border-color: #3a3a50 #3a3a50 #0a0a14 #3a3a50;"
        );
        return label;
    }

    private Label createKeyBadge(String key, boolean wide) {
        Label badge = new Label(key);
        badge.setMinWidth(wide ? 70 : 36);
        badge.setAlignment(Pos.CENTER);
        badge.setStyle(
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 9px;" +
            "-fx-text-fill: " + TEXT_PARCHMENT + ";" +
            "-fx-background-color: #1a1a2a;" +
            "-fx-border-color: #3a3a50 #3a3a50 #0a0a14 #3a3a50;" +
            "-fx-border-width: 1 1 3 1;" +
            "-fx-padding: 6px 10px;"
        );
        return badge;
    }

    private Label createKeyBadge(String key) {
        return createKeyBadge(key, false);
    }

    private HBox createBindingCard(javafx.scene.Node visual, String action, String desc) {
        HBox card = new HBox(14);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(14, 16, 14, 16));
        card.setStyle(
            "-fx-background-color: " + STONE_PANEL + ";" +
            "-fx-border-color: " + STONE_BORDER + ";" +
            "-fx-border-width: 1px;"
        );

        VBox actionBox = new VBox(2);
        Label actionLabel = new Label(action);
        actionLabel.setStyle(
            "-fx-font-family: 'Cinzel', serif;" +
            "-fx-font-size: 12px;" +
            "-fx-font-weight: 700;" +
            "-fx-text-fill: " + TEXT_PARCHMENT + ";"
        );
        Label descLabel = new Label(desc);
        descLabel.setStyle(
            "-fx-font-family: 'Crimson Pro', serif;" +
            "-fx-font-size: 14px;" +
            "-fx-text-fill: " + TEXT_BODY + ";"
        );
        actionBox.getChildren().addAll(actionLabel, descLabel);

        card.getChildren().addAll(visual, actionBox);
        return card;
    }

    private VBox createRoundsSection() {
        VBox section = new VBox(18);

        // Section title
        HBox titleRow = createSectionTitle("◆  Round Phases");
        section.getChildren().add(titleRow);

        // Round items
        VBox timeline = new VBox(0);

        // Warm-up round
        HBox warmupItem = createRoundItem(
            "ROUNDS 1-2\n\nWARM-UP",
            "A safe zone appears at a random location on the arena. All players who reach the safe zone before time expires survive. These rounds let you learn the arena and movement before elimination begins.",
            true
        );
        timeline.getChildren().add(warmupItem);

        // Elimination round
        HBox elimItem = createRoundItem(
            "ROUND 3+\n\nELIMINATION",
            "The safe zone now fits exactly n–1 players — one player is eliminated each round. The arena grows more dangerous. Chaos Events may trigger at any moment. Last player standing wins.",
            false
        );
        timeline.getChildren().add(elimItem);

        section.getChildren().add(timeline);
        return section;
    }

    private HBox createRoundItem(String badgeText, String description, boolean isWarmup) {
        HBox item = new HBox(20);
        item.setAlignment(Pos.TOP_LEFT);
        item.setPadding(new Insets(16, 0, 16, 0));
        item.setStyle("-fx-border-color: transparent transparent rgba(255,255,255,0.05) transparent; -fx-border-width: 0 0 1px 0;");

        Label badge = new Label(badgeText);
        badge.setMinWidth(90);
        badge.setAlignment(Pos.CENTER);
        if (isWarmup) {
            badge.setStyle(
                "-fx-font-family: 'Press Start 2P', monospace;" +
                "-fx-font-size: 7px;" +
                "-fx-text-fill: #6ab080;" +
                "-fx-background-color: rgba(74, 140, 92, 0.2);" +
                "-fx-border-color: rgba(74, 140, 92, 0.4);" +
                "-fx-border-width: 1px;" +
                "-fx-padding: 8px 10px;" +
                "-fx-letter-spacing: 1px;"
            );
        } else {
            badge.setStyle(
                "-fx-font-family: 'Press Start 2P', monospace;" +
                "-fx-font-size: 7px;" +
                "-fx-text-fill: #c07070;" +
                "-fx-background-color: rgba(140, 58, 58, 0.2);" +
                "-fx-border-color: rgba(140, 58, 58, 0.4);" +
                "-fx-border-width: 1px;" +
                "-fx-padding: 8px 10px;" +
                "-fx-letter-spacing: 1px;"
            );
        }

        Label desc = new Label(description);
        desc.setWrapText(true);
        desc.setStyle(
            "-fx-font-family: 'Crimson Pro', serif;" +
            "-fx-font-size: 15px;" +
            "-fx-text-fill: " + TEXT_BODY + ";" +
            "-fx-line-spacing: 1.7;"
        );
        HBox.setHgrow(desc, Priority.ALWAYS);

        item.getChildren().addAll(badge, desc);
        return item;
    }

    private VBox createChaosSection() {
        VBox section = new VBox(18);

        // Section title
        HBox titleRow = createSectionTitle("◆  Chaos Events");
        section.getChildren().add(titleRow);

        // Chaos cards grid
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setAlignment(Pos.CENTER);

        // Chaos cards
        VBox card1 = createChaosCard("EVENT 01", "Reversed Controls", "The server flips all movement directions globally. Left becomes right, up becomes down. Affects every player simultaneously.");
        VBox card2 = createChaosCard("EVENT 02", "Control Swap", "Your controls are swapped with another random player. You move their character; they move yours. Identity truly becomes a crisis.");
        VBox card3 = createChaosCard("EVENT 03", "Decoy Zones", "Multiple safe zones appear on your screen — but only one is real. The server knows which one. Trust nothing, observe everything.");

        grid.add(card1, 0, 0);
        grid.add(card2, 1, 0);
        grid.add(card3, 0, 1);
        GridPane.setColumnSpan(card3, 2);

        section.getChildren().add(grid);
        return section;
    }

    private VBox createChaosCard(String eventNum, String name, String desc) {
        VBox card = new VBox(6);
        card.setPadding(new Insets(16, 18, 16, 18));
        card.setStyle(
            "-fx-background-color: " + STONE_PANEL + ";" +
            "-fx-border-color: " + STONE_BORDER + ";" +
            "-fx-border-width: 1px;" +
            "-fx-border-style: solid solid solid solid;" +
            "-fx-border-width: 1 1 1 3;" +
            "-fx-border-color: " + STONE_BORDER + " " + STONE_BORDER + " " + STONE_BORDER + " " + CHAOS_PURPLE + ";"
        );

        Label icon = new Label(eventNum);
        icon.setStyle(
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 7px;" +
            "-fx-text-fill: #9a7cd0;" +
            "-fx-letter-spacing: 2px;"
        );

        Label nameLabel = new Label(name);
        nameLabel.setStyle(
            "-fx-font-family: 'Cinzel', serif;" +
            "-fx-font-size: 14px;" +
            "-fx-font-weight: 700;" +
            "-fx-text-fill: " + TEXT_PARCHMENT + ";"
        );

        Label descLabel = new Label(desc);
        descLabel.setWrapText(true);
        descLabel.setStyle(
            "-fx-font-family: 'Crimson Pro', serif;" +
            "-fx-font-size: 14px;" +
            "-fx-text-fill: " + TEXT_MUTED + ";" +
            "-fx-line-spacing: 1.6;"
        );

        card.getChildren().addAll(icon, nameLabel, descLabel);
        return card;
    }

    private VBox createMechanicsSection() {
        VBox section = new VBox(18);

        // Section title
        HBox titleRow = createSectionTitle("◆  Carry Mechanics");
        section.getChildren().add(titleRow);

        // Mechanic items
        VBox list = new VBox(12);

        list.getChildren().add(createMechanicItem("01", "Press E near a player to pick them up and carry them. The carried player cannot move independently."));
        list.getChildren().add(createMechanicItem("02", "Press Q to throw the carried player in the direction you are facing. Use this to launch allies into the safe zone."));
        list.getChildren().add(createMechanicItem("03", "A carrying player cannot be marked safe until the carried player is released. You must throw or drop them first."));

        section.getChildren().add(list);
        return section;
    }

    private HBox createMechanicItem(String num, String text) {
        HBox item = new HBox(16);
        item.setAlignment(Pos.TOP_LEFT);
        item.setPadding(new Insets(14, 18, 14, 18));
        item.setStyle(
            "-fx-background-color: " + STONE_PANEL + ";" +
            "-fx-border-color: " + STONE_BORDER + ";" +
            "-fx-border-width: 1px;"
        );

        Label numLabel = new Label(num);
        numLabel.setStyle(
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 9px;" +
            "-fx-text-fill: " + GOLD_DARK + ";"
        );
        numLabel.setPadding(new Insets(3, 0, 0, 0));

        // Parse text to bold parts
        TextFlow textFlow = new TextFlow();
        textFlow.setStyle(
            "-fx-font-family: 'Crimson Pro', serif;" +
            "-fx-font-size: 15px;" +
            "-fx-line-spacing: 1.7;"
        );

        // Simple text for now (can be enhanced to parse <strong> tags)
        Label textLabel = new Label(text);
        textLabel.setWrapText(true);
        textLabel.setStyle(
            "-fx-font-family: 'Crimson Pro', serif;" +
            "-fx-font-size: 15px;" +
            "-fx-text-fill: " + TEXT_BODY + ";" +
            "-fx-line-spacing: 1.7;"
        );
        HBox.setHgrow(textLabel, Priority.ALWAYS);

        item.getChildren().addAll(numLabel, textLabel);
        return item;
    }

    private HBox createSectionTitle(String title) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);

        Label label = new Label(title);
        label.setStyle(
            "-fx-font-family: 'Cinzel', serif;" +
            "-fx-font-size: 11px;" +
            "-fx-font-weight: 700;" +
            "-fx-text-fill: " + GOLD + ";" +
            "-fx-letter-spacing: 4px;"
        );

        HBox line = new HBox();
        HBox.setHgrow(line, Priority.ALWAYS);
        line.setPrefHeight(1);
        line.setStyle("-fx-background-color: linear-gradient(to right, rgba(201,168,76,0.2), transparent);");

        row.getChildren().addAll(label, line);
        return row;
    }

    private void addScanlines(StackPane root) {
        Pane scanlines = new Pane();
        // Bind to root for fullscreen
        scanlines.prefWidthProperty().bind(root.widthProperty());
        scanlines.prefHeightProperty().bind(root.heightProperty());
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

    // Legacy method for compatibility
    public Scene getScene() {
        if (scene == null) {
            scene = createScene();
        }
        return scene;
    }
}
