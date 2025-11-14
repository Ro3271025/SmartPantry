package Controllers;

import javafx.scene.Scene;

import java.io.IOException;
import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import com.example.demo1.MainApplication;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class BaseController {
    public void switchScene(Event event, String newScene) throws IOException {
        Node source = (Node) event.getSource();
        Scene scene = source.getScene();
        Stage primaryStage = (Stage) scene.getWindow();

        FXMLLoader loader = new FXMLLoader(MainApplication.class.getResource(newScene + ".fxml"));
        Parent root = loader.load();
        Scene new_scene = new Scene(root);
        primaryStage.setScene(new_scene);
    }
    protected void openPopup(String fxmlPath, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(MainApplication.class.getResource(fxmlPath));
            Parent popupContent = loader.load();

            Stage popupStage = new Stage();
            popupStage.setTitle(title);
            popupStage.setScene(new Scene(popupContent));
            popupStage.initModality(Modality.APPLICATION_MODAL);
            popupStage.setResizable(false);
            popupStage.showAndWait();

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to open popup: " + e.getMessage());
        }
    }

}
