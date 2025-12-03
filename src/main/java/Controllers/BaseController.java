package Controllers;

import javafx.scene.Scene;

import java.io.IOException;
import java.net.URL;

import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import com.example.demo1.MainApplication;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class BaseController {
    public void switchScene(Event event, String fxmlName) throws IOException {
        Node source = (Node) event.getSource();
        Scene currentScene = source.getScene();
        Stage primaryStage = (Stage) currentScene.getWindow();

        String path = "/XMLFiles/" + fxmlName + (fxmlName.endsWith(".fxml") ? "" : ".fxml");
        URL fxmlUrl = MainApplication.class.getResource(path);

        if (fxmlUrl == null) {
            throw new IOException("FXML not found at path: " + path);
        }

        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent root = loader.load();

        Scene newScene = new Scene(root);

        URL css = MainApplication.class.getResource("/CSSFiles/style.css");
        if (css != null) {
            newScene.getStylesheets().add(css.toExternalForm());
        } else {
            System.err.println("CSS not found at /CSSFiles/style.css");
        }
        primaryStage.setScene(newScene);
        primaryStage.show();
    }
    public void openPopup(String fxmlPath, String title) {
        try {
            URL fxmlUrl = getClass().getResource(fxmlPath);
            if (fxmlUrl == null) {
                System.err.println("❌ FXML not found: " + fxmlPath);
                return;
            }

            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();

            Stage popupStage = new Stage();
            popupStage.setTitle(title);
            popupStage.setScene(new Scene(root));
            popupStage.show();
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to open popup: " + e.getMessage());
        }
    }


}
