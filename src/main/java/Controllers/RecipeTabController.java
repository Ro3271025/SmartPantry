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
    private List<Recipe> allRecipes;
    private String currentUserId; // Dynamic user ID (set from PantryController)

   // public void setCurrentUserId(String uid) {
        //this.currentUserId = uid;
    //}

    @FXML
    public void initialize() {
        FirebaseConfiguration.initialize();
        currentUserId = UserSession.getCurrentUserId();
        // Initialize recipe data
        allRecipes = createSampleRecipes();

        if (currentUserId != null && !currentUserId.isEmpty()) {
            loadPantryItemsFromFirebase();
        } else {
            loadRecipes();
        }
    }
    private void loadPantryItemsFromFirebase() {
        Firestore db = FirebaseConfiguration.getDatabase();
        CollectionReference recipesRef = db.collection("users")
                .document(currentUserId).collection("pantryItems");
        ApiFuture<QuerySnapshot> future = recipesRef.get();
        try {
            List<QueryDocumentSnapshot> documents = future.get().getDocuments();
            List<String> pantryItems = new ArrayList<>();
            for (QueryDocumentSnapshot document : documents) {
                String name = document.getString("name");
                if (name != null) {
                    pantryItems.add(name.trim().toLowerCase());
                }
            }
            suggestRecipesBasedOnPantry(pantryItems);
        } catch (InterruptedException | ExecutionException e){
            e.printStackTrace();
            showError("Failed to load pantry items: " + e.getMessage());
        }
    }
    private void suggestRecipesBasedOnPantry(List<String> pantryItems) {
        for (Recipe recipe : allRecipes) {
            List<String> recipeIngredients = Arrays.stream(recipe.available.toLowerCase().split(","))
                    .map(String::trim)
                    .collect(Collectors.toList());
            long matchCount = recipeIngredients.stream().filter(pantryItems::contains).count();

            int percent = (int) ((double) matchCount / recipeIngredients.size() * 100);
            recipe.match = percent + "% match";
        }
        loadRecipes();
        showSuccess("Personalized recipes loaded based on your pantry!");
    }
    //Handle back to pantry navigation

    @FXML
    private void handleBackToPantry() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/demo1/PantryDashboard.fxml"));
            Parent root = loader.load();

            // Pass user ID back to PantryController
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

    /**
     * Handle AI recipe generation
     */
    @FXML
    private void handleGenerateRecipe() {
        String prompt = aiInputField.getText().trim();

        if (prompt.isEmpty()) {
            showError("Please enter a recipe prompt");
            return;
        }
        // TODO: Implement AI recipe generation with Firebase/backend
        // For now, just show a message
        showSuccess("Generating recipes with: " + prompt);
        aiInputField.clear();
    }
    @FXML
    private void handleAddRecipe() {
        openPopup("/com/example/demo1/AddRecipe.fxml", "Add Recipe");
        loadPantryItemsFromFirebase(); // optional refresh afterward
    }

    // Filter handlers
    @FXML private void handleFilterAll() { currentFilter = "all"; updateFilterButtons(); loadRecipes(); }

    @FXML private void handleFilterByPantry() { currentFilter = "pantry"; updateFilterButtons(); loadRecipes(); }

    @FXML private void handleFilterMissing() { currentFilter = "missing"; updateFilterButtons(); loadRecipes(); }

    // Update filter button styles
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

    // Handle generate again
    @FXML
    private void handleGenerateAgain() { loadPantryItemsFromFirebase(); }

    // Handle see more recipes
    @FXML
    private void handleSeeMore() { showSuccess("Loading more recipes..."); }

    // Load and display recipes based on current filter
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

    // Create a single recipe card
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

        Button addBtn = new Button("+ Add Missing to Shopping List");
        addBtn.getStyleClass().add("add-to-list-button");
        addBtn.setOnAction(e -> showSuccess("Added missing ingredients to shopping list!"));

        VBox aiTip = new VBox(new Label("✨ AI Tip: " + recipe.aiTip));
        aiTip.getStyleClass().add("ai-tip");

        card.getChildren().addAll(header, available, missing, addBtn, aiTip);
        return card;
    }

    /**
     * Create an ingredient row with icon
     */
    private HBox createIngredientRow(String icon, String iconStyle, String label, String items) {
        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add(iconStyle);

        Label labelText = new Label(label);
        labelText.getStyleClass().add("ingredient-label");

        Label itemsText = new Label(items);
        itemsText.getStyleClass().add("ingredient-text");

        return new HBox(8, iconLabel, labelText, itemsText);
    }
    // Handle adding missing ingredients to shopping list
    //private void handleAddToShoppingList(Recipe recipe) { showSuccess("Added missing ingredients to shopping list!"); }

    /**
     * Create sample recipe data
     */
    private List<Recipe> createSampleRecipes() {
        //List<Recipe> recipes = new ArrayList<>();

        //recipes.add(new Recipe(
                //"Cheese Omelette", "75% match",
                //"Eggs, Cheese, Milk", "Butter",
                //"This uses your low-stock cheese and eggs. Perfect quick breakfast!"
        //));

        //recipes.add(new Recipe(
                //"Banana Pancakes", "60% match",
                //"Bananas, Eggs, Milk", "Flour, Maple Syrup",
                //"Perfect for using your expiring bananas! AI suggests adding a dash of cinnamon for extra flavor."
        //));

        //recipes.add(new Recipe(
                //"Chicken Rice Bowl", "50% match",
                //"Chicken Breast, Rice", "Broccoli, Soy Sauce",
                //"Great match with your pantry! You have plenty of rice and chicken available."
        //));

        //recipes.add(new Recipe(
                //"Fried Rice", "40% match",
                //"Rice, Eggs", "Vegetables, Soy Sauce, Oil",
                //"AI adapted this to use your abundant rice supply. Great for meal prep!"
        //));

        //recipes.add(new Recipe(
                //"Grilled Cheese Sandwich", "33% match",
                //"Cheese", "Bread, Butter",
                //"Simple comfort food! Note: Your bread is expired, get fresh bread from the store."
        //));

        //recipes.add(new Recipe(
                //"Yogurt Parfait", "25% match",
                //"Yogurt", "Granola, Berries, Honey",
                //"Healthy breakfast option using your yogurt. Substitute honey with maple syrup if preferred."
        //));

        //return recipes;
        return List.of(
                new Recipe("Cheese Omelette", "75% match", "Eggs, Cheese, Milk", "Butter",
                        "Quick breakfast using your low-stock cheese!"),
                new Recipe("Banana Pancakes", "60% match", "Bananas, Eggs, Milk", "Flour, Maple Syrup",
                        "Use expiring bananas for this one."),
                new Recipe("Chicken Rice Bowl", "50% match", "Chicken Breast, Rice", "Broccoli, Soy Sauce",
                        "High protein, easy to make."),
                new Recipe("Grilled Cheese", "33% match", "Cheese", "Bread, Butter",
                        "Classic comfort food, best fresh bread.")
        );
    }
    // Show error message
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Show success message
    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Inner class for Recipe data
    private static class Recipe {
        String name, match, available, missing, aiTip;
        Recipe(String n, String m, String a, String miss, String tip) {
            name = n; match = m; available = a; missing = miss; aiTip = tip;
        }
    }
}
