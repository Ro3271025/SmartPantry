package rodolfo.pantrydashboard;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;


import java.util.Objects;

/**
 * Main application entry point for the Pantry Dashboard.
 */
public class App extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Load FXML
        Parent root = FXMLLoader.load(
                Objects.requireNonNull(
                        App.class.getResource("/pantry/PantryDashboard.fxml"),
                        "Cannot find /pantry/PantryDashboard.fxml on classpath"
                )
        );

        // Create and configure scene
        Scene scene = new Scene(root);
        primaryStage.setTitle("Pantry Dashboard");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}