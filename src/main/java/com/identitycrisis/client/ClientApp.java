package com.identitycrisis.client;

import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/** JavaFX Application entry point. */
public class ClientApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        Label status = new Label("[HEALTH OK] Identity Crisis client is up.");
        status.setTextFill(Color.LIMEGREEN);
        status.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Label hint = new Label("GUI is rendering. SceneManager not yet wired.");
        hint.setTextFill(Color.LIGHTGRAY);
        hint.setStyle("-fx-font-size: 13px;");

        VBox root = new VBox(12, status, hint);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: #1a1a2e;");

        primaryStage.setTitle("Identity Crisis");
        primaryStage.setWidth(1280);
        primaryStage.setHeight(720);
        primaryStage.setResizable(false);
        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }

    public static void main(String[] args) { launch(args); }
}
