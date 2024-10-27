package org.example.threadsvisualiser;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class ThreadVisualizer extends Application {

    private Controller controller;

    @Override
    public void start(Stage stage) throws IOException {

        FXMLLoader fxmlLoader = new FXMLLoader(ThreadVisualizer.class.getResource("view.fxml"));
        Scene scene = new Scene(fxmlLoader.load());
        controller = fxmlLoader.getController();


        stage.setTitle("Thread Manager");
        stage.setScene(scene);
        stage.show();

        stage.setOnCloseRequest((event) -> {
            controller.shutdownScheduler();
        });
    }

    public static void main(String[] args) {
        launch();
    }
}