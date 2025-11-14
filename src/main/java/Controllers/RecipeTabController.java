package Controllers;

import com.example.demo1.FirebaseConfiguration;
import com.example.demo1.UserSession;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.Modality;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class RecipeTabController extends BaseController{
    @FXML private Button backButton;
    @FXML private TextField aiInputField;
    @FXML private Button generateButton;
    @FXML private Button allRecipesBtn;
    @FXML private Button byPantryBtn;
    @FXML private Button missingIngrBtn;
    @FXML private Button addRecipeBtn;
    @FXML private VBox vBox;
    @FXML private Button generateAgainBtn;
    @FXML private Button seeMoreBtn;

    private String currentFilter = "all";
    private List<Recipe> allRecipes = new ArrayList<>();
    private String currentUserId;

    @FXML
    public void initialize() {
        FirebaseConfiguration.initialize();
        currentUserId = UserSession.getCurrentUserId();

        if (currentUserId == null || currentUserId.isBlank()) {
            showError("User session not found");
            return;
        }

        loadRecipesFromFirebase();
    }

    // Loads recipes stored in Firestore for this user
    private void loadRecipesFromFirebase() {
        Firestore db = FirebaseConfiguration.getDatabase();
        CollectionReference recipesRef = db.collection("users")
                .document(currentUserId)
                .collection("recipes");

        ApiFuture<QuerySnapshot> future = recipesRef.get();
        try {
            List<QueryDocumentSnapshot> docs = future.get().getDocuments();
            allRecipes.clear();
            for (QueryDocumentSnapshot doc : docs) {
                Recipe r = new Recipe(
                        doc.getString("name"),
                        "Match pending",
                        doc.getString("available"),
                        doc.getString("missing"),
                        doc.getString("aiTip")
                );
                r.id = doc.getId();
                allRecipes.add(r);
            }
            loadRecipes();
            showSuccess("Loaded " + allRecipes.size() + " recipes from Firebase");
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            showError("Failed to load recipes: " + e.getMessage());
        }
    }

    // Reloads recipes after adding one
    @FXML
    private void handleAddRecipe() {
        openPopup("/com/example/demo1/AddRecipe.fxml", "Add Recipe");
        loadRecipesFromFirebase(); // refresh
    }

    // Navigate back to pantry *
    @FXML
    private void handleBackToPantry() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/demo1/PantryDashboard.fxml"));
            Parent root = loader.load();
            PantryController controller = loader.getController();
            controller.setCurrentUserId(currentUserId);
            Stage stage = (Stage) backButton.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Pantry Dashboard");
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            showError("Failed to navigate to Pantry Dashboard");
        }
    }

    // Filter control
    @FXML private void handleFilterAll() { currentFilter = "all"; updateFilterButtons(); loadRecipes(); }
    @FXML private void handleFilterByPantry() { currentFilter = "pantry"; updateFilterButtons(); loadRecipes(); }
    @FXML private void handleFilterMissing() { currentFilter = "missing"; updateFilterButtons(); loadRecipes(); }

    private void updateFilterButtons() {
        allRecipesBtn.getStyleClass().remove("filter-selected");
        byPantryBtn.getStyleClass().remove("filter-selected");
        missingIngrBtn.getStyleClass().remove("filter-selected");

        switch (currentFilter) {
            case "all" -> allRecipesBtn.getStyleClass().add("filter-selected");
            case "pantry" -> byPantryBtn.getStyleClass().add("filter-selected");
            case "missing" -> missingIngrBtn.getStyleClass().add("filter-selected");
        }
    }

    // Generate mock AI recipe suggestion
    @FXML
    private void handleGenerateRecipe() {
        String prompt = aiInputField.getText().trim();
        if (prompt.isEmpty()) {
            showError("Please enter a recipe idea first!");
            return;
        }
        showSuccess("Generating recipes for: " + prompt);
        aiInputField.clear();
    }

    @FXML private void handleGenerateAgain() { loadRecipesFromFirebase(); }
    @FXML private void handleSeeMore() { showSuccess("Feature coming soon!"); }

    /** 🍳 Create and display recipe cards */
    private void loadRecipes() {
        vBox.getChildren().clear();
        for (int i = 0; i < allRecipes.size(); i += 2) {
            HBox row = new HBox(20);
            row.setAlignment(Pos.TOP_LEFT);
            VBox card1 = createRecipeCard(allRecipes.get(i));
            row.getChildren().add(card1);
            if (i + 1 < allRecipes.size()) {
                VBox card2 = createRecipeCard(allRecipes.get(i + 1));
                row.getChildren().add(card2);
            }
            vBox.getChildren().add(row);
        }
    }

    // Build each card UI
    private VBox createRecipeCard(Recipe recipe) {
        VBox card = new VBox(12);
        card.getStyleClass().add("recipe-card");
        card.setPadding(new Insets(16));

        Label name = new Label(recipe.name);
        name.getStyleClass().add("recipe-name");

        Label match = new Label(recipe.match);
        match.getStyleClass().add("match-badge");

        HBox header = new HBox(12, name, match);
        header.setAlignment(Pos.CENTER_LEFT);

        HBox available = createIngredientRow("✓", "available-icon", "Available:", recipe.available);
        HBox missing = createIngredientRow("✗", "missing-icon", "Missing:", recipe.missing);

        Button deleteBtn = new Button("🗑 Delete Recipe");
        deleteBtn.getStyleClass().add("delete-button");
        deleteBtn.setOnAction(e -> handleDeleteRecipe(recipe));

        VBox aiTip = new VBox(new Label("✨ AI Tip: " + recipe.aiTip));
        aiTip.getStyleClass().add("ai-tip");

        card.getChildren().addAll(header, available, missing, deleteBtn, aiTip);
        return card;
    }

    // Delete recipe from Firebase
    private void handleDeleteRecipe(Recipe recipe) {
        try {
            Firestore db = FirebaseConfiguration.getDatabase();
            db.collection("users")
                    .document(currentUserId)
                    .collection("recipes")
                    .document(recipe.id)
                    .delete();
            loadRecipesFromFirebase();
        } catch (Exception e) {
            showError("Failed to delete recipe: " + e.getMessage());
        }
    }

    /** Helper for icons */
    private HBox createIngredientRow(String icon, String iconStyle, String label, String items) {
        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add(iconStyle);
        Label labelText = new Label(label);
        labelText.getStyleClass().add("ingredient-label");
        Label itemsText = new Label(items);
        itemsText.getStyleClass().add("ingredient-text");
        return new HBox(8, iconLabel, labelText, itemsText);
    }

    // Alerts
    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    private void showSuccess(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    /** Inner recipe record */
    private static class Recipe {
        String id, name, match, available, missing, aiTip;
        Recipe(String n, String m, String a, String miss, String tip) {
            name = n; match = m; available = a; missing = miss; aiTip = tip;
        }
    }
}
