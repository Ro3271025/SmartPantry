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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class RecipeTabController extends BaseController{
    @FXML private Button backButton;
    @FXML private TextField aiInputField;
    @FXML private Button generateButton;
    @FXML private Button allRecipesBtn;
    @FXML private Button byPantryBtn;
    @FXML private Button missingIngrBtn;
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
            suggestRecipesBasedOnPantry();
        } catch (InterruptedException | ExecutionException e){
            e.printStackTrace();
            showError("Failed to load pantry items: " + e.getMessage());
        }
    }

    /**
     * Handle back to pantry navigation
     */
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

        // Simulate adding a new recipe
        Recipe newRecipe = new Recipe(
                "AI Generated Recipe",
                "95% match",
                prompt,
                "None",
                "AI generated this recipe based on your request!"
        );
        allRecipes.add(0, newRecipe);
        loadRecipes();
    }

    /**
     * Filter handlers
     */
    @FXML
    private void handleFilterAll() {
        currentFilter = "all";
        updateFilterButtons();
        loadRecipes();
    }

    @FXML
    private void handleFilterByPantry() {
        currentFilter = "pantry";
        updateFilterButtons();
        loadRecipes();
    }

    @FXML
    private void handleFilterMissing() {
        currentFilter = "missing";
        updateFilterButtons();
        loadRecipes();
    }

    /**
     * Update filter button styles
     */
    private void updateFilterButtons() {
        allRecipesBtn.getStyleClass().remove("filter-selected");
        byPantryBtn.getStyleClass().remove("filter-selected");
        missingIngrBtn.getStyleClass().remove("filter-selected");

        switch (currentFilter) {
            case "all":
                allRecipesBtn.getStyleClass().add("filter-selected");
                break;
            case "pantry":
                byPantryBtn.getStyleClass().add("filter-selected");
                break;
            case "missing":
                missingIngrBtn.getStyleClass().add("filter-selected");
                break;
        }
    }

    /**
     * Handle generate again
     */
    @FXML
    private void handleGenerateAgain() {
        // Refresh recipes
        allRecipes = createSampleRecipes();
        loadRecipes();
        showSuccess("Generated new recipe suggestions!");
    }

    /**
     * Handle see more recipes
     */
    @FXML
    private void handleSeeMore() {
        // TODO: Load more recipes or navigate to expanded view
        showSuccess("Loading more recipes...");
    }

    /**
     * Load and display recipes based on current filter
     */
    private void loadRecipes() {
        vBox.getChildren().clear();

        List<Recipe> filteredRecipes = filterRecipes();

        // Create cards in pairs (2 per row)
        for (int i = 0; i < filteredRecipes.size(); i += 2) {
            HBox row = new HBox(20);
            row.setAlignment(Pos.TOP_LEFT);

            VBox card1 = createRecipeCard(filteredRecipes.get(i));
            row.getChildren().add(card1);
            HBox.setHgrow(card1, Priority.ALWAYS);

            if (i + 1 < filteredRecipes.size()) {
                VBox card2 = createRecipeCard(filteredRecipes.get(i + 1));
                row.getChildren().add(card2);
                HBox.setHgrow(card2, Priority.ALWAYS);
            }

            vBox.getChildren().add(row);
        }
    }

    /**
     * Filter recipes based on current filter
     */
    private List<Recipe> filterRecipes() {
        // For now, return all recipes
        // TODO: Implement actual filtering logic based on user's pantry items
        return allRecipes;
    }

    /**
     * Create a single recipe card
     */
    private VBox createRecipeCard(Recipe recipe) {
        VBox card = new VBox(12);
        card.getStyleClass().add("recipe-card");
        card.setPadding(new Insets(16));

        // Recipe image placeholder
        Pane imagePlaceholder = new Pane();
        imagePlaceholder.setPrefHeight(150);
        imagePlaceholder.getStyleClass().add("recipe-image");
        Label imageLabel = new Label("Recipe Image");
        imageLabel.getStyleClass().add("image-placeholder-text");
        imagePlaceholder.getChildren().add(imageLabel);
        imageLabel.setLayoutX(10);
        imageLabel.setLayoutY(10);

        // Header with name and badge
        HBox recipeHeader = new HBox(12);
        recipeHeader.setAlignment(Pos.CENTER_LEFT);

        Label recipeName = new Label(recipe.name);
        recipeName.getStyleClass().add("recipe-name");

        Label badge = new Label(recipe.match);
        badge.getStyleClass().add("match-badge");

        recipeHeader.getChildren().addAll(recipeName, badge);
        HBox.setHgrow(recipeName, Priority.ALWAYS);

        // Available ingredients
        HBox availableRow = createIngredientRow("✓", "available-icon", "Available:", recipe.available);

        // Missing ingredients
        HBox missingRow = createIngredientRow("✗", "missing-icon", "Missing:", recipe.missing);

        // Add to shopping list button
        Button addButton = new Button("+ Add Missing to Shopping List");
        addButton.getStyleClass().add("add-to-list-button");
        addButton.setOnAction(e -> handleAddToShoppingList(recipe));

        // AI tip
        HBox aiTip = new HBox(8);
        aiTip.getStyleClass().add("ai-tip");
        aiTip.setPadding(new Insets(12));

        Label sparkle = new Label("✨");
        Label tipText = new Label("AI tip: " + recipe.aiTip);
        tipText.setWrapText(true);
        tipText.getStyleClass().add("ai-tip-text");

        aiTip.getChildren().addAll(sparkle, tipText);
        HBox.setHgrow(tipText, Priority.ALWAYS);

        card.getChildren().addAll(imagePlaceholder, recipeHeader, availableRow, missingRow, addButton, aiTip);
        return card;
    }

    /**
     * Create an ingredient row with icon
     */
    private HBox createIngredientRow(String icon, String iconStyle, String label, String ingredients) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);

        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add(iconStyle);

        Label labelText = new Label(label);
        labelText.getStyleClass().add("ingredient-label");

        Label ingredientsText = new Label(ingredients);
        ingredientsText.setWrapText(true);
        ingredientsText.getStyleClass().add("ingredient-text");

        row.getChildren().addAll(iconLabel, labelText, ingredientsText);
        return row;
    }

    /**
     * Handle adding missing ingredients to shopping list
     */
    private void handleAddToShoppingList(Recipe recipe) {
        // TODO: Implement shopping list integration with Firebase
        showSuccess("Added missing ingredients to shopping list!");
    }

    /**
     * Create sample recipe data
     */
    private List<Recipe> createSampleRecipes() {
        List<Recipe> recipes = new ArrayList<>();

        recipes.add(new Recipe(
                "Cheese Omelette", "75% match",
                "Eggs, Cheese, Milk", "Butter",
                "This uses your low-stock cheese and eggs. Perfect quick breakfast!"
        ));

        recipes.add(new Recipe(
                "Banana Pancakes", "60% match",
                "Bananas, Eggs, Milk", "Flour, Maple Syrup",
                "Perfect for using your expiring bananas! AI suggests adding a dash of cinnamon for extra flavor."
        ));

        recipes.add(new Recipe(
                "Chicken Rice Bowl", "50% match",
                "Chicken Breast, Rice", "Broccoli, Soy Sauce",
                "Great match with your pantry! You have plenty of rice and chicken available."
        ));

        recipes.add(new Recipe(
                "Fried Rice", "40% match",
                "Rice, Eggs", "Vegetables, Soy Sauce, Oil",
                "AI adapted this to use your abundant rice supply. Great for meal prep!"
        ));

        recipes.add(new Recipe(
                "Grilled Cheese Sandwich", "33% match",
                "Cheese", "Bread, Butter",
                "Simple comfort food! Note: Your bread is expired, get fresh bread from the store."
        ));

        recipes.add(new Recipe(
                "Yogurt Parfait", "25% match",
                "Yogurt", "Granola, Berries, Honey",
                "Healthy breakfast option using your yogurt. Substitute honey with maple syrup if preferred."
        ));

        return recipes;
    }

    /**
     * Show error message
     */
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Show success message
     */
    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Inner class for Recipe data
     */
    private static class Recipe {
        String name;
        String match;
        String available;
        String missing;
        String aiTip;

        Recipe(String name, String match, String available, String missing, String aiTip) {
            this.name = name;
            this.match = match;
            this.available = available;
            this.missing = missing;
            this.aiTip = aiTip;
        }
    }
}
