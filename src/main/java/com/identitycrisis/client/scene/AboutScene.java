package com.identitycrisis.client.scene;

import javafx.scene.Scene;
import javafx.scene.layout.*;
import javafx.scene.control.*;
import javafx.scene.text.*;
import javafx.geometry.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.Group;
import com.identitycrisis.shared.model.GameConfig;

/**
 * About screen matching about.html pixel-perfect design.
 * Game info, team credits, and tech stack.
 */
public class AboutScene {

    private Scene scene;
    private SceneManager sceneManager;

    // Color constants
    private static final String GOLD = "#c9a84c";
    private static final String GOLD_LIGHT = "#e8c86a";
    private static final String GOLD_DARK = "#8a6a1a";
    private static final String STONE_DARK = "#0d0d10";
    private static final String STONE_MID = "#161620";
    private static final String STONE_PANEL = "#1c1c26";
    private static final String STONE_BORDER = "#2a2a36";
    private static final String TORCH_ORANGE = "#e8743c";
    private static final String TEXT_PARCHMENT = "#e8dfc4";
    private static final String TEXT_MUTED = "#7a7060";
    private static final String TEXT_BODY = "#b0a890";

    public AboutScene(SceneManager sceneManager) {
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

        // Main content - wrapped in ScrollPane for fullscreen responsiveness
        VBox content = createContent();
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        // Bind scrollPane to root size
        scrollPane.prefWidthProperty().bind(root.widthProperty());
        scrollPane.prefHeightProperty().bind(root.heightProperty());
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
        pattern.setStyle(
            "-fx-background-color: " + STONE_DARK + ";" +
            "-fx-background-image: linear-gradient(rgba(255,255,255,0.012) 1px, transparent 1px)," +
            "linear-gradient(90deg, rgba(255,255,255,0.012) 1px, transparent 1px);" +
            "-fx-background-size: 48px 48px;"
        );
        pattern.setMouseTransparent(true);
        root.getChildren().add(pattern);
    }

    private void addTorchGlows(StackPane root) {
        // Top-left glow
        Pane tlGlow = new Pane();
        tlGlow.setPrefSize(250, 250);
        tlGlow.setStyle("-fx-background-color: radial-gradient(center 50% 50%, radius 50%, rgba(232,116,60,0.1), transparent 70%);");
        tlGlow.setMouseTransparent(true);
        StackPane.setAlignment(tlGlow, Pos.TOP_LEFT);
        tlGlow.setTranslateX(-50);
        tlGlow.setTranslateY(-50);
        root.getChildren().add(tlGlow);

        // Top-right glow
        Pane trGlow = new Pane();
        trGlow.setPrefSize(250, 250);
        trGlow.setStyle("-fx-background-color: radial-gradient(center 50% 50%, radius 50%, rgba(232,116,60,0.1), transparent 70%);");
        trGlow.setMouseTransparent(true);
        StackPane.setAlignment(trGlow, Pos.TOP_RIGHT);
        trGlow.setTranslateX(50);
        trGlow.setTranslateY(-50);
        root.getChildren().add(trGlow);
    }

    private VBox createContent() {
        VBox content = new VBox(0);
        content.setAlignment(Pos.TOP_CENTER);
        content.setMaxWidth(820);
        content.setPadding(new Insets(0, 32, 60, 32));

        // Header
        HBox header = createHeader();
        content.getChildren().add(header);

        // Game Header Section
        VBox gameHeader = createGameHeader();
        VBox.setMargin(gameHeader, new Insets(40, 0, 40, 0));
        content.getChildren().add(gameHeader);

        // Concept Section
        VBox conceptSection = createConceptSection();
        VBox.setMargin(conceptSection, new Insets(0, 0, 36, 0));
        content.getChildren().add(conceptSection);

        // Team Section
        VBox teamSection = createTeamSection();
        VBox.setMargin(teamSection, new Insets(0, 0, 36, 0));
        content.getChildren().add(teamSection);

        // Tech Stack Section
        VBox techSection = createTechSection();
        content.getChildren().add(techSection);

        return content;
    }

    private HBox createHeader() {
        HBox header = new HBox(24);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(20, 32, 20, 32));
        header.setStyle("-fx-border-color: transparent transparent " + STONE_BORDER + " transparent; -fx-border-width: 0 0 1px 0; -fx-background-color: rgba(13,13,16,0.9);");
        // Fill available width
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
        Label title = new Label("About");
        title.setStyle(
            "-fx-font-family: 'Cinzel', serif;" +
            "-fx-font-size: 18px;" +
            "-fx-font-weight: 700;" +
            "-fx-text-fill: " + TEXT_PARCHMENT + ";" +
            "-fx-letter-spacing: 4px;"
        );

        // Divider
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        header.getChildren().addAll(backBtn, title, spacer);
        return header;
    }

    private VBox createGameHeader() {
        VBox header = new VBox(6);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(0, 0, 32, 0));
        header.setStyle("-fx-border-color: transparent transparent rgba(201,168,76,0.15) transparent; -fx-border-width: 0 0 1px 0;");

        // Gold star icon at top (matching screenshot)
        Label starIcon = new Label("✦");
        starIcon.setStyle(
            "-fx-font-size: 32px;" +
            "-fx-text-fill: " + GOLD + ";"
        );
        VBox.setMargin(starIcon, new Insets(0, 0, 16, 0));

        // Game title
        Label gameTitle = new Label("Identity Crisis");
        gameTitle.setStyle(
            "-fx-font-family: 'Cinzel Decorative', serif;" +
            "-fx-font-size: 36px;" +
            "-fx-font-weight: 700;" +
            "-fx-text-fill: " + TEXT_PARCHMENT + ";"
        );
        javafx.scene.effect.DropShadow shadow = new javafx.scene.effect.DropShadow();
        shadow.setColor(Color.rgb(201, 168, 76, 0.3));
        shadow.setRadius(30);
        gameTitle.setEffect(shadow);

        // Course info
        Label courseLabel = new Label("CMSC 137 · Networked Game · AY 2025–2026");
        courseLabel.setStyle(
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 7px;" +
            "-fx-text-fill: " + GOLD + ";" +
            "-fx-letter-spacing: 3px;"
        );
        courseLabel.setOpacity(0.7);
        VBox.setMargin(courseLabel, new Insets(6, 0, 16, 0));

        // Description
        Label descLabel = new Label("A multiplayer arena survival game where the safe zone shrinks, controls betray you, and only the last player standing wins.");
        descLabel.setWrapText(true);
        descLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        descLabel.setMaxWidth(520);
        descLabel.setStyle(
            "-fx-font-family: 'Crimson Pro', serif;" +
            "-fx-font-size: 17px;" +
            "-fx-font-style: italic;" +
            "-fx-text-fill: " + TEXT_MUTED + ";" +
            "-fx-line-spacing: 0.3em;"
        );

        header.getChildren().addAll(starIcon, gameTitle, courseLabel, descLabel);
        return header;
    }

    // Kept for reference - replaced with simple star icon above
    private Group createMiniCrest() {
        Group crest = new Group();

        // Outer diamond shape
        Polygon diamond = new Polygon(
            30, 4, 34, 14, 44, 14, 36, 21, 39, 32, 30, 26, 21, 32, 24, 21, 16, 14, 26, 14
        );
        diamond.setFill(Color.TRANSPARENT);
        diamond.setStroke(Color.web(GOLD));
        diamond.setStrokeWidth(1.2);
        diamond.setOpacity(0.7);

        // Two small squares inside
        Rectangle leftRect = new Rectangle(24, 20, 6, 6);
        leftRect.setFill(Color.web(GOLD));
        leftRect.setOpacity(0.4);

        Rectangle rightRect = new Rectangle(30, 20, 6, 6);
        rightRect.setFill(Color.web(GOLD));
        rightRect.setOpacity(0.4);

        crest.getChildren().addAll(diamond, leftRect, rightRect);
        return crest;
    }

    private VBox createConceptSection() {
        VBox section = new VBox(16);
        section.setAlignment(Pos.TOP_LEFT);

        // Section title
        HBox titleBox = new HBox(12);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("◆   The Game");
        title.setStyle(
            "-fx-font-family: 'Cinzel', serif;" +
            "-fx-font-size: 11px;" +
            "-fx-font-weight: 700;" +
            "-fx-text-fill: " + GOLD + ";" +
            "-fx-letter-spacing: 4px;"
        );
        Region line = new Region();
        HBox.setHgrow(line, Priority.ALWAYS);
        line.setStyle("-fx-background-color: linear-gradient(to right, rgba(201,168,76,0.25), transparent);");
        line.setPrefHeight(1);
        titleBox.getChildren().addAll(title, line);

        // Body text
        Label body = new Label("Identity Crisis is a networked top-down 2D arena game supporting a minimum of four players. Each round, a safe zone appears somewhere on the map. Reach it in time — or be eliminated. Starting at Round 3, the safe zone only fits n–1 players, guaranteeing one elimination per round. Chaos Events keep every round unpredictable: controls reverse, identities swap, and decoy safe zones flood the arena to create confusion. The last player standing claims the arena.");
        body.setWrapText(true);
        body.setStyle(
            "-fx-font-family: 'Crimson Pro', serif;" +
            "-fx-font-size: 16px;" +
            "-fx-text-fill: " + TEXT_BODY + ";" +
            "-fx-line-spacing: 0.4em;"
        );

        section.getChildren().addAll(titleBox, body);
        return section;
    }

    private VBox createTeamSection() {
        VBox section = new VBox(16);
        section.setAlignment(Pos.TOP_LEFT);

        // Section title
        HBox titleBox = new HBox(12);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("◆   Team");
        title.setStyle(
            "-fx-font-family: 'Cinzel', serif;" +
            "-fx-font-size: 11px;" +
            "-fx-font-weight: 700;" +
            "-fx-text-fill: " + GOLD + ";" +
            "-fx-letter-spacing: 4px;"
        );
        Region line = new Region();
        HBox.setHgrow(line, Priority.ALWAYS);
        line.setStyle("-fx-background-color: linear-gradient(to right, rgba(201,168,76,0.25), transparent);");
        line.setPrefHeight(1);
        titleBox.getChildren().addAll(title, line);

        // Team grid
        HBox teamGrid = new HBox(16);
        teamGrid.setAlignment(Pos.CENTER);

        // Team card 1
        VBox card1 = createTeamCard("Member 1 · Lead Developer", "Network & Backend", "Server architecture, game state, client-server sync", false);
        // Team card 2
        VBox card2 = createTeamCard("Member 2 · Game Developer", "Gameplay Systems", "Arena logic, chaos events, collision & physics", false);
        // Team card 3 (highlighted - James)
        VBox card3 = createTeamCard("Member 3 · UI/UX Designer", "James", "Navigation, typography, screens & sprites", true);

        teamGrid.getChildren().addAll(card1, card2, card3);

        section.getChildren().addAll(titleBox, teamGrid);
        return section;
    }

    private VBox createTeamCard(String role, String name, String focus, boolean highlight) {
        VBox card = new VBox(6);
        card.setPadding(new Insets(16, 20, 16, 20));
        card.setPrefWidth(220);
        card.setMinWidth(220);
        card.setMaxWidth(220);

        if (highlight) {
            card.setStyle(
                "-fx-background-color: rgba(201,168,76,0.06);" +
                "-fx-border-color: rgba(201,168,76,0.3);" +
                "-fx-border-width: 1px;"
            );
        } else {
            card.setStyle(
                "-fx-background-color: " + STONE_PANEL + ";" +
                "-fx-border-color: " + STONE_BORDER + ";" +
                "-fx-border-width: 1px;"
            );
        }

        // Role
        Label roleLabel = new Label(role);
        roleLabel.setStyle(
            "-fx-font-family: 'Press Start 2P', monospace;" +
            "-fx-font-size: 6px;" +
            "-fx-text-fill: " + (highlight ? GOLD : GOLD_DARK) + ";" +
            "-fx-letter-spacing: 2px;"
        );

        // Name
        Label nameLabel = new Label(name);
        nameLabel.setStyle(
            "-fx-font-family: 'Cinzel', serif;" +
            "-fx-font-size: 15px;" +
            "-fx-font-weight: 700;" +
            "-fx-text-fill: " + TEXT_PARCHMENT + ";"
        );

        // Focus
        Label focusLabel = new Label(focus);
        focusLabel.setWrapText(true);
        focusLabel.setStyle(
            "-fx-font-family: 'Crimson Pro', serif;" +
            "-fx-font-size: 13px;" +
            "-fx-font-style: italic;" +
            "-fx-text-fill: " + TEXT_MUTED + ";"
        );

        card.getChildren().addAll(roleLabel, nameLabel, focusLabel);
        return card;
    }

    private VBox createTechSection() {
        VBox section = new VBox(12);
        section.setAlignment(Pos.TOP_LEFT);

        // Section title
        HBox titleBox = new HBox(12);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("◆   Built With");
        title.setStyle(
            "-fx-font-family: 'Cinzel', serif;" +
            "-fx-font-size: 11px;" +
            "-fx-font-weight: 700;" +
            "-fx-text-fill: " + GOLD + ";" +
            "-fx-letter-spacing: 4px;"
        );
        Region line = new Region();
        HBox.setHgrow(line, Priority.ALWAYS);
        line.setStyle("-fx-background-color: linear-gradient(to right, rgba(201,168,76,0.25), transparent);");
        line.setPrefHeight(1);
        titleBox.getChildren().addAll(title, line);

        // Tech pills
        HBox pillBox = new HBox(8);
        pillBox.setAlignment(Pos.CENTER_LEFT);

        String[] techs = {"Client-Server", "Top-Down 2D", "Networked Multiplayer", "Java"};
        for (String tech : techs) {
            Label pill = new Label(tech);
            pill.setStyle(
                "-fx-font-family: 'Press Start 2P', monospace;" +
                "-fx-font-size: 6px;" +
                "-fx-text-fill: " + TEXT_MUTED + ";" +
                "-fx-letter-spacing: 1px;" +
                "-fx-background-color: " + STONE_PANEL + ";" +
                "-fx-border-color: " + STONE_BORDER + ";" +
                "-fx-border-width: 1px;" +
                "-fx-padding: 5px 10px;"
            );
            pillBox.getChildren().add(pill);
        }

        section.getChildren().addAll(titleBox, pillBox);
        return section;
    }

    private void addScanlines(StackPane root) {
        Pane scanlines = new Pane();
        // Bind to root for fullscreen
        scanlines.prefWidthProperty().bind(root.widthProperty());
        scanlines.prefHeightProperty().bind(root.heightProperty());
        scanlines.setStyle(
            "-fx-background-color: repeating-linear-gradient(to bottom, transparent, transparent 2px, rgba(0,0,0,0.025) 2px, rgba(0,0,0,0.025) 4px);"
        );
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
