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
        FXMLLoader fxmlLoader = new FXMLLoader(MainApplication.class.getResource("mainScreen.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1200, 700);
        //scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());
        stage.setResizable(true);
        stage.setScene(scene);
        stage.setTitle("SmartPantry");
        stage.show();
    }
    public static void main(String[] args) {
        fStore = FirebaseConfiguration.initialize(); // ← Firebase is initialized HERE
        launch();
    }
}
