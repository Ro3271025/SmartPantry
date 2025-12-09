package Controllers;

import com.example.demo1.ThemeManager;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class ThemeSettingsController extends BaseController implements Initializable {

    @FXML
    private Button backButton;

    @FXML
    private Button lightModeButton;

    @FXML
    private Button darkModeButton;

    @FXML
    private Label currentThemeIcon;

    @FXML
    private Label currentThemeLabel;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        updateCurrentThemeDisplay();
        updateButtonStates();
    }

    /**
     * Handle back to dashboard button
     */
    @FXML
    private void handleBackToDashboard(ActionEvent event) throws IOException {
        switchScene(event, "PantryDashboard");
    }

    /**
     * Set light mode
     */
    @FXML
    private void handleSetLightMode(ActionEvent event) {
        System.out.println("☀️ Switching to Light Mode");
        themeManager.setLightTheme();
        updateCurrentThemeDisplay();
        updateButtonStates();

        // Show feedback
        showThemeChangeSuccess("Light Mode");
    }

    /**
     * Set dark mode
     */
    @FXML
    private void handleSetDarkMode(ActionEvent event) {
        System.out.println("🌙 Switching to Dark Mode");
        themeManager.setDarkTheme();
        updateCurrentThemeDisplay();
        updateButtonStates();

        // Show feedback
        showThemeChangeSuccess("Dark Mode");
    }

    /**
     * Update the current theme display
     */
    private void updateCurrentThemeDisplay() {
        if (themeManager.isDarkMode()) {
            currentThemeIcon.setText("🌙");
            currentThemeLabel.setText("Dark Mode");
        } else {
            currentThemeIcon.setText("☀️");
            currentThemeLabel.setText("Light Mode");
        }
    }

    /**
     * Update button states (show which is active)
     */
    private void updateButtonStates() {
        if (themeManager.isDarkMode()) {
            // Dark mode is active
            darkModeButton.setText("✓ Active");
            darkModeButton.getStyleClass().add("active");
            darkModeButton.setDisable(false);

            lightModeButton.setText("Use Light Mode");
            lightModeButton.getStyleClass().remove("active");
            lightModeButton.setDisable(false);
        } else {
            // Light mode is active
            lightModeButton.setText("✓ Active");
            lightModeButton.getStyleClass().add("active");
            lightModeButton.setDisable(false);

            darkModeButton.setText("Use Dark Mode");
            darkModeButton.getStyleClass().remove("active");
            darkModeButton.setDisable(false);
        }
    }

    /**
     * Show success message when theme changes
     */
    private void showThemeChangeSuccess(String themeName) {
        System.out.println("✓ Theme changed to: " + themeName);
        System.out.println("✓ Preference saved automatically");
    }
}