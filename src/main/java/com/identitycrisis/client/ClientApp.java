package com.identitycrisis.client;

import com.identitycrisis.client.scene.SceneManager;
import com.identitycrisis.shared.model.GameConfig;
import javafx.application.Application;
import javafx.stage.Stage;

/** JavaFX client entry point; wires SceneManager with the primary Stage. */
public class ClientApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle(GameConfig.WINDOW_TITLE);
        primaryStage.setWidth(GameConfig.WINDOW_WIDTH);
        primaryStage.setHeight(GameConfig.WINDOW_HEIGHT);
        primaryStage.setResizable(false);

        SceneManager sceneManager = new SceneManager(primaryStage);
        sceneManager.showInitialLoading();

        primaryStage.show();
    }

    public static void main(String[] args) { launch(args); }
}
