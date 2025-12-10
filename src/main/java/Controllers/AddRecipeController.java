package Controllers;

import Firebase.FirebaseConfiguration;
import com.example.demo1.UserSession;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.util.*;

public class AddRecipeController extends BaseController {

    @FXML private TextField recipeNameField;
    @FXML private TextField availableField;
    @FXML private TextField missingField;
    @FXML private TextField aiTipField;
    @FXML private Label statusLabel;

    @FXML
    private void saveRecipe() {
        String uid = UserSession.getCurrentUserId();
        if (uid == null || uid.isEmpty()) { showInlineError("Logged-In User Not Found"); return; }

        String name = safe(recipeNameField.getText());
        String available = safe(availableField.getText());
        String missing = safe(missingField.getText());
        String aiTip = safe(aiTipField.getText());

        if (name.isBlank()) { showInlineError("Recipe Name is required."); return; }

        try {
            Firestore db = FirebaseConfiguration.getDatabase();

            // Unified fields
            Map<String, Object> data = new HashMap<>();
            data.put("title", name);
            data.put("ingredients", splitCSV(available));
            data.put("missingIngredients", splitCSV(missing));
            data.put("steps", List.of(aiTip.isBlank() ? "No tip provided." : aiTip));
            data.put("createdBy", "manual");
            data.put("favorite", false);
            data.put("createdAt", Timestamp.now());
            data.put("updatedAt", FieldValue.serverTimestamp());

            // Legacy fields so Discover (legacy) lists it too
            data.put("name", name);
            data.put("available", available);
            data.put("missing", missing);
            data.put("aiTip", aiTip);

            // Use slug (stable id) so "Saved" tab lines up with legacy edits
            String docId = name.toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9]+","-")
                    .replaceAll("(^-|-$)","");

            db.collection("users").document(uid).collection("recipes")
                    .document(docId)          // stable id
                    .set(data)                // upsert
                    .get();

            statusLabel.setTextFill(Color.GREEN);
            statusLabel.setText("✓ Recipe saved successfully!");

            // close
            Stage stage = (Stage) recipeNameField.getScene().getWindow();
            stage.close();

        } catch (Exception e) {
            showInlineError("Error saving recipe: " + e.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        Stage stage = (Stage) recipeNameField.getScene().getWindow();
        stage.close();
    }

    private void showInlineError(String msg) {
        statusLabel.setTextFill(Color.RED);
        statusLabel.setText("✗ " + msg);
    }

    private static String safe(String s){ return s == null ? "" : s.trim(); }

    private static List<String> splitCSV(String s) {
        if (s == null || s.isBlank()) return List.of();
        String[] parts = s.split("\\s*,\\s*");
        List<String> out = new ArrayList<>();
        for (String p : parts) if (!p.isBlank()) out.add(p);
        return out;
    }
}
