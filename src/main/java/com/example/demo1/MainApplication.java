package com.example.demo1;

import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.FirebaseAuth;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class MainApplication extends Application {
    public static Firestore fStore;
    public static FirebaseAuth fAuth;
    public static FirebaseConfiguration contextFirebase = new FirebaseConfiguration();

    @Override
    public void start(Stage stage) throws IOException {
        // Initialize theme manager
        ThemeManager themeManager = ThemeManager.getInstance();

        FXMLLoader fxmlLoader = new FXMLLoader(MainApplication.class.getResource("mainScreen.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1200, 700);

        // Register the initial scene with ThemeManager
        themeManager.registerScene(scene);

        stage.setResizable(true);
        stage.setScene(scene);
        stage.setTitle("SmartPantry");

        // Cleanup when window closes
        stage.setOnCloseRequest(e -> {
            themeManager.unregisterScene(scene);
        });

        stage.show();
    }

    public static void main(String[] args) {
        fStore = FirebaseConfiguration.initialize(); // Firebase is initialized HERE
        launch();
    }
}