package com.example.demo1;

import Firebase.FirebaseConfiguration;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.FirebaseAuth;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public class MainApplication extends Application {
    public static Firestore fStore;
    public static FirebaseAuth fAuth;
    public static FirebaseConfiguration contextFirebase = new FirebaseConfiguration();

    @Override
    public void start(Stage stage) throws IOException {
        // Try to find mainScreen.fxml in multiple locations
        URL fxmlUrl = findFXML("mainScreen");

        if (fxmlUrl == null) {
            System.err.println("FATAL: Could not find mainScreen.fxml!");
            System.err.println("Searched in:");
            System.err.println("  - /com/example/demo1/mainScreen.fxml");
            System.err.println("  - /XMLFiles/mainScreen.fxml");
            throw new IOException("mainScreen.fxml not found");
        }

        System.out.println("✓ Loading mainScreen from: " + fxmlUrl);

        FXMLLoader fxmlLoader = new FXMLLoader(fxmlUrl);
        Scene scene = new Scene(fxmlLoader.load(), 1200, 700);

        // Register the initial scene with ThemeManager
        ThemeManager.getInstance().registerScene(scene);

        stage.setResizable(true);
        stage.setScene(scene);
        stage.setTitle("SmartPantry");
        stage.show();
    }

    /**
     * Find FXML file in multiple possible locations.
     */
    private URL findFXML(String name) {
        URL url = null;

        // 1. Try primary location: /com/example/demo1/
        url = MainApplication.class.getResource(name + ".fxml");
        if (url != null) {
            System.out.println("  Found in /com/example/demo1/");
            return url;
        }

        // 2. Try XMLFiles folder: /XMLFiles/
        url = MainApplication.class.getResource("/XMLFiles/" + name + ".fxml");
        if (url != null) {
            System.out.println("  Found in /XMLFiles/");
            return url;
        }

        // 3. Try using getClass()
        url = getClass().getResource("/XMLFiles/" + name + ".fxml");
        if (url != null) {
            System.out.println("  Found in /XMLFiles/ (via getClass)");
            return url;
        }

        // 4. Try root
        url = MainApplication.class.getResource("/" + name + ".fxml");
        if (url != null) {
            System.out.println("  Found in root");
            return url;
        }

        return null;
    }

    public static void main(String[] args) {
        fStore = FirebaseConfiguration.initialize(); // Firebase is initialized HERE
        launch();
    }
}