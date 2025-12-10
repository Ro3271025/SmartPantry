package Controllers;
import Firebase.FirebaseConfiguration;
import com.example.demo1.UserSession;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.Timestamp;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;


import java.util.HashMap;
import java.util.Map;
public class AddRecipeController extends BaseController{
    @FXML private TextField recipeNameField;
    @FXML private TextField availableField;
    @FXML private TextField missingField;
    @FXML private TextField aiTipField;
    @FXML private Label statusLabel;

    @FXML
    private void saveRecipe() throws IOException {
        String uid = UserSession.getCurrentUserId();

        if (uid == null || uid.isEmpty()) {
            showError("Logged-In User Not Found");
            return;
        }
        String name = recipeNameField.getText().trim();
        String available = availableField.getText().trim();
        String missing = missingField.getText().trim();
        String aiTip = aiTipField.getText().trim();

        if (name.isEmpty()) {
            showError("Recipe Name is required.");
            return;
        }
        try {
            Firestore db = FirebaseConfiguration.getDatabase();

            Map<String, Object> recipeData = new HashMap<>();
            recipeData.put("title", name);
            recipeData.put("ingredients", Arrays.asList(available.split("\\s*,\\s*")));
            recipeData.put("missingIngredients", Arrays.asList(missing.split("\\s*,\\s*")));
            recipeData.put("steps", List.of(aiTip.isEmpty() ? "No tip provided." : aiTip));
            recipeData.put("createdBy", "manual");
            recipeData.put("favorite", false);
            recipeData.put("createdAt", Timestamp.now());

            db.collection("users")
                    .document(uid)
                    .collection("recipes")
                    .add(recipeData)
                    .get();

            statusLabel.setTextFill(Color.GREEN);
            statusLabel.setText("✓ Recipe saved successfully!");
            clearForm();

            // Close window after short delay
            Stage stage = (Stage) recipeNameField.getScene().getWindow();
            stage.close();
        } catch (Exception e) {
            showError("Error saving recipe: " + e.getMessage());
        }
    }
    @FXML
    private void handleCancel() {
        Stage stage = (Stage) recipeNameField.getScene().getWindow();
        stage.close();
    }

    private void showError(String msg) {
        statusLabel.setTextFill(Color.RED);
        statusLabel.setText("✗ " + msg);
    }

    private void clearForm() {
        recipeNameField.clear();
        availableField.clear();
        missingField.clear();
        aiTipField.clear();
    }

}
