package com.identitycrisis.client;

import com.identitycrisis.client.scene.SceneManager;
import com.identitycrisis.shared.model.GameConfig;
import javafx.application.Application;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/**
 * JavaFX Application entry point and <strong>client-side Composition Root</strong>.
 *
 * <p>This is the only place on the client that creates top-level collaborating
 * objects. {@link SceneManager} is constructed here with the {@link Stage}, then
 * asked to show the first scene. Everything else ({@code GameClient},
 * {@code LocalGameState}, {@code InputManager}) is wired inside
 * {@code SceneManager} when the player navigates to the lobby or game scene.
 */
public class ClientApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle(GameConfig.WINDOW_TITLE);
        primaryStage.setWidth(GameConfig.WINDOW_WIDTH);
        primaryStage.setHeight(GameConfig.WINDOW_HEIGHT);
        primaryStage.setResizable(false);

        // Set application icon (taskbar + title bar)
        try (var is = getClass().getResourceAsStream("/logo.png")) {
            if (is != null) {
                primaryStage.getIcons().add(new Image(is));
            }
        } catch (Exception ignored) {}

        SceneManager sceneManager = new SceneManager(primaryStage);
        sceneManager.showInitialLoading();

        primaryStage.show();
    }

    public static void main(String[] args) { launch(args); }
}
