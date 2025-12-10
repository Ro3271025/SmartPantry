package Controllers;

import com.example.demo1.ThemeManager;
import com.example.demo1.MainApplication;
import javafx.scene.Scene;
import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

/**
 * Base controller with multi-location resource loading.
 * Handles the split file structure:
 *   - /com/example/demo1/  (primary)
 *   - /XMLFiles/           (FXML files)
 *   - /CSSFiles/           (CSS files)
 */
public class BaseController {

    // ThemeManager instance for all controllers to use
    protected ThemeManager themeManager = ThemeManager.getInstance();

    /**
     * Switch to a new scene, searching multiple locations for the FXML file.
     */
    public void switchScene(Event event, String newScene) throws IOException {
        Node source = (Node) event.getSource();
        Scene scene = source.getScene();
        Stage primaryStage = (Stage) scene.getWindow();

        // Try to find FXML in multiple locations
        URL fxmlUrl = findFXML(newScene);

        if (fxmlUrl == null) {
            System.err.println("ERROR: Could not find FXML file: " + newScene + ".fxml");
            System.err.println("Searched in:");
            System.err.println("  - /com/example/demo1/" + newScene + ".fxml");
            System.err.println("  - /XMLFiles/" + newScene + ".fxml");
            throw new IOException("FXML file not found: " + newScene + ".fxml");
        }

        System.out.println("✓ Found FXML at: " + fxmlUrl);

        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent root = loader.load();
        Scene new_scene = new Scene(root);

        // Register the new scene with ThemeManager to apply correct theme
        themeManager.registerScene(new_scene);

        primaryStage.setScene(new_scene);
    }

    /**
     * Open a popup window, searching multiple locations for the FXML file.
     */
    protected void openPopup(String fxmlPath, String title) {
        try {
            // Clean up the path - remove leading slash and .fxml extension if present
            String cleanName = fxmlPath;
            if (cleanName.startsWith("/")) {
                cleanName = cleanName.substring(1);
            }
            if (cleanName.contains("/")) {
                cleanName = cleanName.substring(cleanName.lastIndexOf("/") + 1);
            }
            if (cleanName.endsWith(".fxml")) {
                cleanName = cleanName.replace(".fxml", "");
            }

            URL fxmlUrl = findFXML(cleanName);

            // Also try the exact path provided
            if (fxmlUrl == null) {
                fxmlUrl = MainApplication.class.getResource(fxmlPath);
            }
            if (fxmlUrl == null) {
                fxmlUrl = getClass().getResource(fxmlPath);
            }

            if (fxmlUrl == null) {
                System.err.println("ERROR: Could not find FXML file: " + fxmlPath);
                System.err.println("Also tried: " + cleanName);
                return;
            }

            System.out.println("✓ Opening popup from: " + fxmlUrl);

            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent popupContent = loader.load();

            Stage popupStage = new Stage();
            popupStage.setTitle(title);
            Scene popupScene = new Scene(popupContent);

            // Register popup scene with ThemeManager
            themeManager.registerScene(popupScene);

            popupStage.setScene(popupScene);
            popupStage.initModality(Modality.APPLICATION_MODAL);
            popupStage.setResizable(false);

            // Unregister when popup closes
            popupStage.setOnHidden(e -> themeManager.unregisterScene(popupScene));

            popupStage.showAndWait();

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to open popup: " + e.getMessage());
        }
    }

    /**
     * Find FXML file in multiple possible locations.
     * Search order:
     *   1. /com/example/demo1/{name}.fxml
     *   2. /XMLFiles/{name}.fxml
     *   3. /{name}.fxml (root)
     */
    protected URL findFXML(String name) {
        // Remove .fxml extension if present
        String baseName = name.endsWith(".fxml") ? name.replace(".fxml", "") : name;

        URL url = null;

        // 1. Try primary location: /com/example/demo1/
        url = MainApplication.class.getResource(baseName + ".fxml");
        if (url != null) {
            System.out.println("  Found in /com/example/demo1/");
            return url;
        }

        // 2. Try XMLFiles folder: /XMLFiles/
        url = MainApplication.class.getResource("/XMLFiles/" + baseName + ".fxml");
        if (url != null) {
            System.out.println("  Found in /XMLFiles/");
            return url;
        }

        // 3. Try using getClass() for XMLFiles
        url = getClass().getResource("/XMLFiles/" + baseName + ".fxml");
        if (url != null) {
            System.out.println("  Found in /XMLFiles/ (via getClass)");
            return url;
        }

        // 4. Try root
        url = MainApplication.class.getResource("/" + baseName + ".fxml");
        if (url != null) {
            System.out.println("  Found in root");
            return url;
        }

        return null;
    }

    /**
     * Find CSS file in multiple possible locations.
     * Search order:
     *   1. /com/example/demo1/{name}.css
     *   2. /CSSFiles/{name}.css
     *   3. /{name}.css (root)
     */
    protected URL findCSS(String name) {
        // Remove .css extension if present
        String baseName = name.endsWith(".css") ? name.replace(".css", "") : name;

        URL url = null;

        // 1. Try primary location
        url = MainApplication.class.getResource(baseName + ".css");
        if (url != null) return url;

        // 2. Try CSSFiles folder
        url = MainApplication.class.getResource("/CSSFiles/" + baseName + ".css");
        if (url != null) return url;

        // 3. Try using getClass()
        url = getClass().getResource("/CSSFiles/" + baseName + ".css");
        if (url != null) return url;

        // 4. Try root
        url = MainApplication.class.getResource("/" + baseName + ".css");
        if (url != null) return url;

        return null;
    }
}