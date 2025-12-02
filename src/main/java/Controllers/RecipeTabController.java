package Controllers;

import Firebase.FirebaseConfiguration;
import com.example.demo1.UserSession;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import Recipe.RecipeAPIService;
import com.google.firebase.cloud.FirestoreClient;
import com.google.cloud.firestore.DocumentSnapshot;
import java.util.Map;
import java.util.HashMap;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import javafx.scene.image.ImageView;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
    @FXML private TextField searchField;
    @FXML private Button readyBtn;
    @FXML private Button favoriteBtn;
    @FXML private Button notReadyBtn;
    @FXML private Button aiRecommendedBtn;
    @FXML private Label recipeTitle;
    @FXML private TextField searchbar;



    private String currentFilter = "all";
    private List<Recipe> allRecipes = new ArrayList<>();
    private String currentUserId;

    @FXML
    public void initialize() {
        FirebaseConfiguration.initialize();
        currentUserId = UserSession.getCurrentUserId();
        String userName = UserSession.getCurrentUserName();

        if (currentUserId == null || currentUserId.isBlank()) {
            showError("User session not found");
            return;
        }
        if (userName !=  null && !userName.isBlank()) {
            recipeTitle.setText(userName + "'s Recipes");
        } else {
            recipeTitle.setText("Your Recipes");
        }
        // Load pantry items and recipes dynamically
        List<String> pantryItems = getPantryItemNames();
        loadRecipesFromFirebase(pantryItems);
    }

    // Loads recipes stored in Firestore for this user
    private void loadRecipesFromFirebase(List<String> pantryItems) {
        Firestore db = FirebaseConfiguration.getDatabase();
        CollectionReference recipesRef = db.collection("users")
                .document(currentUserId)
                .collection("recipes");

        try {
            List<QueryDocumentSnapshot> docs = recipesRef.get().get().getDocuments();
            allRecipes.clear();

            for (QueryDocumentSnapshot doc : docs) {
                String name = doc.getString("name");
                String available = doc.getString("available");
                String missing = doc.getString("missing");
                String aiTip = doc.getString("aiTip");

                // Combine all ingredients into one normalized list
                String combined = ((available != null ? available : "") + "," + (missing != null ? missing : ""))
                        .replaceAll("\\s+", " ")
                        .toLowerCase()
                        .trim();

                List<String> allIngredients = Arrays.stream(combined.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();

                if (allIngredients.isEmpty()) continue;

                // Recalculate what’s actually available/missing based on the pantry
                List<String> actualAvailable = new ArrayList<>();
                List<String> actualMissing = new ArrayList<>();

                for (String ing : allIngredients) {
                    boolean match = pantryItems.stream().anyMatch(p ->
                            p.equals(ing) ||
                                    p.equals(ing.replaceAll("s$", "")) ||
                                    p.replaceAll("s$", "").equals(ing) ||
                                    p.contains(ing) || ing.contains(p)
                    );

                    if (match) actualAvailable.add(ing);
                    else actualMissing.add(ing);
                }

                // Compute true match percentage
                int matchPercent = (int) Math.round((double) actualAvailable.size() / allIngredients.size() * 100);

                // Create recipe with updated fields
                Recipe r = new Recipe(
                        name,
                        matchPercent + "% match",
                        String.join(", ", actualAvailable),
                        String.join(", ", actualMissing),
                        aiTip
                );
                r.id = doc.getId();
                allRecipes.add(r);
            }

            // Display recipes in the UI
            loadRecipes();
            System.out.println(" Loaded " + allRecipes.size() + " recipes from Firebase (with live pantry matching)");

        } catch (Exception e) {
            e.printStackTrace();
            showError("Failed to load recipes: " + e.getMessage());
        }
    }


    // Reloads recipes after adding one
    @FXML
    private void handleAddRecipe() {
        openPopup("/XMLFiles/AddRecipe.fxml", "Add Recipe");
        List<String> pantryItems = getPantryItemNames();
        loadRecipesFromFirebase(pantryItems);
    }

    // Navigate back to pantry *
    @FXML
    private void handleBackToPantry() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/XMLFiles/PantryDashboard.fxml"));
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

    @FXML
    private void handleFilterAll() {
        currentFilter = "all";
        updateFilterButtons();
        displayFilteredRecipes();
    }

    @FXML
    private void handleFilterReady() {
        currentFilter = "ready";
        updateFilterButtons();
        displayFilteredRecipes();
    }

    @FXML
    private void handleFilterFavorites() {
        currentFilter = "favorites";
        updateFilterButtons();
        displayFilteredRecipes();
    }

    @FXML
    private void handleFilterNotReady() {
        currentFilter = "notready";
        updateFilterButtons();
        displayFilteredRecipes();
    }

    @FXML
    private void handleFilterAIRecommended() {
        currentFilter = "AI";
        updateFilterButtons();
        displayFilteredRecipes();
    }

    private void updateFilterButtons() {
        // remove highlight
        List<Button> filters = List.of(allRecipesBtn, readyBtn, favoriteBtn, notReadyBtn, aiRecommendedBtn);
        filters.forEach(btn -> btn.getStyleClass().remove("filter-selected"));

        // highlight active one
        switch (currentFilter) {
            case "ready" -> readyBtn.getStyleClass().add("filter-selected");
            case "favorites" -> favoriteBtn.getStyleClass().add("filter-selected");
            case "notready" -> notReadyBtn.getStyleClass().add("filter-selected");
            case "AI" -> aiRecommendedBtn.getStyleClass().add("filter-selected");
            default -> allRecipesBtn.getStyleClass().add("filter-selected");
        }
    }

    /** Apply filter logic */
    private void displayFilteredRecipes() {
        List<Recipe> filtered;

        switch (currentFilter) {
            case "ready" -> filtered = allRecipes.stream()
                    .filter(r -> parseMatch(r.match) == 100)
                    .toList();

            case "favorites" -> filtered = allRecipes.stream()
                    .filter(r -> r.favorite)
                    .toList();

            case "notready" -> filtered = allRecipes.stream()
                    .filter(r -> parseMatch(r.match) < 100)
                    .toList();

            case "AI" -> filtered = allRecipes.stream()
                    .filter(r -> r.aiRecommended)
                    .toList();

            default -> filtered = allRecipes;
        }

        displayRecipes(filtered);
    }


    /** Helper to safely parse match percentage text like "80% match" */
    private int parseMatch(String match) {
        try {
            return Integer.parseInt(match.replace("% match", "").trim());
        } catch (Exception e) {
            return 0;
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

    @FXML private void handleGenerateAgain() {
        List<String> pantryItems = getPantryItemNames();
        loadRecipesFromFirebase(pantryItems);
    }
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
    /** Displays a provided list of recipes in the VBox */
    private void displayRecipes(List<Recipe> recipesToShow) {
        vBox.getChildren().clear();

        for (int i = 0; i < recipesToShow.size(); i += 2) {
            HBox row = new HBox(20);
            row.setAlignment(Pos.TOP_LEFT);

            VBox card1 = createRecipeCard(recipesToShow.get(i));
            row.getChildren().add(card1);

            if (i + 1 < recipesToShow.size()) {
                VBox card2 = createRecipeCard(recipesToShow.get(i + 1));
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

        Button editBtn = new Button("Edit");
        editBtn.getStyleClass().add("edit-button");
        editBtn.setOnAction(e -> handleEditRecipe(recipe));

        Button deleteBtn = new Button("🗑 Delete Recipe");
        deleteBtn.getStyleClass().add("delete-button");
        deleteBtn.setOnAction(e -> handleDeleteRecipe(recipe));

        Button favButton = new Button(recipe.favorite ? "Favorite" : "Add to Favorites");
        favButton.getStyleClass().add("favorite-button");
        favButton.setOnAction(e -> {
            recipe.favorite = !recipe.favorite;
            favButton.setText(recipe.favorite ? "Favorite" : "Add to Favorites");
            updateRecipeInFirebase(recipe);
        });


        HBox buttons = new HBox(10, editBtn, deleteBtn, favButton);
        buttons.setAlignment(Pos.CENTER_LEFT);

        VBox aiTip = new VBox(new Label("✨ AI Tip: " + recipe.aiTip));
        aiTip.getStyleClass().add("ai-tip");

        card.getChildren().addAll(header, available, missing, buttons, aiTip);
        card.setOnMouseClicked(e -> {
            List<Map<String, String>> apiResults = RecipeAPIService.getRecipesFromIngredients(recipe.available);
            if (!apiResults.isEmpty()) {
                showRecipeListPopup(apiResults); // ✅ Pass the whole list, not just one recipe
            } else {
                showError("No recipes found for these ingredients.");
            }
        });

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
                    .delete()
                    .get(); // Wait for deletion to complete before refreshing

            // Refresh recipes with updated pantry data
            List<String> pantryItems = getPantryItemNames();
            loadRecipesFromFirebase(pantryItems);

            showSuccess("Recipe deleted successfully!");
        } catch (Exception e) {
            showError("Failed to delete recipe: " + e.getMessage());
        }
    }
    private void handleEditRecipe(Recipe recipe) {
        Dialog<Recipe> dialog = new Dialog<>();
        dialog.setTitle("Edit Recipe");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Input fields
        TextField nameField = new TextField(recipe.name);
        TextField availableField = new TextField(recipe.available);
        TextField missingField = new TextField(recipe.missing);
        TextArea aiTipField = new TextArea(recipe.aiTip);
        aiTipField.setPrefRowCount(3);

        // Layout
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        grid.addRow(0, new Label("Name:"), nameField);
        grid.addRow(1, new Label("Available:"), availableField);
        grid.addRow(2, new Label("Missing:"), missingField);
        grid.addRow(3, new Label("AI Tip:"), aiTipField);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                recipe.name = nameField.getText().trim();
                recipe.available = availableField.getText().trim();
                recipe.missing = missingField.getText().trim();
                recipe.aiTip = aiTipField.getText().trim();
                return recipe;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(updated -> {
            // Recalculate match % immediately
            List<String> pantryItems = getPantryItemNames();
            int newMatch = computeMatchPercentage(
                    updated.available,
                    updated.missing,
                    pantryItems
            );
            updated.match = newMatch + "% match";

            // Update Firestore in background
            updateRecipeInFirebase(updated);

            // Refresh only this card visually
            refreshRecipeCard(updated);
        });
    }
    private void refreshRecipeCard(Recipe updated) {
        // Find the existing card in VBox and replace it
        for (int i = 0; i < vBox.getChildren().size(); i++) {
            if (vBox.getChildren().get(i) instanceof HBox row) {
                for (int j = 0; j < row.getChildren().size(); j++) {
                    if (row.getChildren().get(j) instanceof VBox card) {
                        // Find the card by matching the recipe name and ID
                        Label nameLabel = (Label) ((HBox) card.getChildren().get(0)).getChildren().get(0);
                        if (nameLabel.getText().equals(updated.name)) {
                            // Rebuild and replace the card
                            VBox newCard = createRecipeCard(updated);
                            row.getChildren().set(j, newCard);
                            return;
                        }
                    }
                }
            }
        }
    }

    private void updateRecipeInFirebase(Recipe recipe) {
        new Thread(() -> {
            try {
                Firestore db = FirebaseConfiguration.getDatabase();
                db.collection("users")
                        .document(currentUserId)
                        .collection("recipes")
                        .document(recipe.id)
                        .update(
                                "name", recipe.name,
                                "available", recipe.available,
                                "missing", recipe.missing,
                                "aiTip", recipe.aiTip
                        ).get(); // wait for completion

                System.out.println("✅ Updated recipe in Firebase: " + recipe.name);
                Platform.runLater(() -> showSuccess("Recipe updated successfully!"));
            } catch (Exception e) {
                Platform.runLater(() -> showError("Failed to update recipe: " + e.getMessage()));
                e.printStackTrace();
            }
        }).start();
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
    // Load the user's pantry items (names only)
    private List<String> getPantryItemNames() {
        List<String> pantryNames = new ArrayList<>();
        try {
            Firestore db = FirebaseConfiguration.getDatabase();
            CollectionReference pantryRef = db.collection("users")
                    .document(currentUserId)
                    .collection("pantryItems");

            List<QueryDocumentSnapshot> docs = pantryRef.get().get().getDocuments();
            for (QueryDocumentSnapshot doc : docs) {
                String name = doc.getString("name");
                if (name != null && !name.isBlank()) {
                    pantryNames.add(name.trim().toLowerCase());
                }
            }
        } catch (Exception e) {
            System.err.println("Error loading pantry items: " + e.getMessage());
        }
        return pantryNames;
    }
    @FXML
    private void handleSearch() {
        String query = searchField.getText().toLowerCase().trim();
        if (query.isEmpty()) {
            loadRecipes(); // show all if search is cleared
            return;
        }

        vBox.getChildren().clear();

        // Filter recipes based on the query (name, available, missing, or aiTip)
        List<Recipe> filtered = allRecipes.stream()
                .filter(r -> r.name.toLowerCase().contains(query)
                        || r.available.toLowerCase().contains(query)
                        || r.missing.toLowerCase().contains(query)
                        || r.aiTip.toLowerCase().contains(query))
                .toList();

        for (int i = 0; i < filtered.size(); i += 2) {
            HBox row = new HBox(20);
            row.setAlignment(Pos.TOP_LEFT);
            VBox card1 = createRecipeCard(filtered.get(i));
            row.getChildren().add(card1);
            if (i + 1 < filtered.size()) {
                VBox card2 = createRecipeCard(filtered.get(i + 1));
                row.getChildren().add(card2);
            }
            vBox.getChildren().add(row);
        }
    }
    // Compute true match % using flexible word comparison
    private int computeMatchPercentage(String available, String missing, List<String> pantryItems) {
        String combined = ((available != null ? available : "") + "," + (missing != null ? missing : ""))
                .replaceAll("\\s+", " ")  // clean spacing
                .trim()
                .toLowerCase();

        if (combined.isBlank()) return 0;

        List<String> allIngredients = Arrays.stream(combined.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        if (allIngredients.isEmpty()) return 0;

        long matches = allIngredients.stream()
                .filter(ingredient -> pantryItems.stream().anyMatch(pantry ->
                        // ✅ flexible comparison:
                        pantry.equals(ingredient) ||
                                pantry.equals(ingredient.replaceAll("s$", "")) ||       // plural → singular
                                pantry.replaceAll("s$", "").equals(ingredient) ||       // singular → plural
                                ingredient.contains(pantry) || pantry.contains(ingredient)
                ))
                .count();

        return (int) Math.round((double) matches / allIngredients.size() * 100);
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

    /**
     * Fetches a real recipe from Spoonacular that matches Firebase ingredients.
     */
    private void fetchAndOpenRealRecipe(Recipe recipe) {
        try {
            Firestore db = FirebaseConfiguration.getDatabase();
            // Get this recipe's Firestore document
            DocumentSnapshot snapshot = db.collection("users")
                    .document(currentUserId)
                    .collection("recipes")
                    .document(recipe.id)
                    .get()
                    .get();

            List<String> ingredients = new ArrayList<>();

            // Pull from Firebase fields (available + missing)
            if (snapshot.exists()) {
                String available = snapshot.getString("available");
                String missing = snapshot.getString("missing");

                if (available != null) ingredients.addAll(Arrays.asList(available.split(",")));
                if (missing != null) ingredients.addAll(Arrays.asList(missing.split(",")));
            }

            if (ingredients.isEmpty()) {
                showError("No ingredients found for this recipe.");
                return;
            }

            // Call Spoonacular API
            String ingredientString = String.join(",", ingredients);
            List<Map<String, String>> results = RecipeAPIService.getRecipesFromIngredients(ingredientString);

            if (results.isEmpty()) {
                showError("No matching online recipes found.");
                return;
            }

            // Take the top match and open its real recipe page
            Map<String, String> bestMatch = results.get(0);
            String realUrl = RecipeAPIService.getFullRecipeUrl(bestMatch.get("id"));
            if (realUrl != null && !realUrl.isBlank()) {
                System.out.println("🌐 Opening real recipe: " + realUrl);
                java.awt.Desktop.getDesktop().browse(new java.net.URI(realUrl));
            } else {
                showError("Could not retrieve recipe link.");
            }

        } catch (Exception e) {
            e.printStackTrace();
            showError("Error fetching online recipe: " + e.getMessage());
        }
    }
    private void showRecipeListPopup(List<Map<String, String>> recipeList) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Matching Recipes");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox container = new VBox(15);
        container.setPadding(new Insets(10));

        for (Map<String, String> recipe : recipeList) {
            VBox card = new VBox(8);
            card.setPadding(new Insets(10));
            card.setStyle("-fx-border-color: #ccc; -fx-background-color: #fafafa; -fx-border-radius: 6; -fx-background-radius: 6;");

            Label title = new Label(recipe.get("title"));
            title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

            ImageView imageView = new ImageView(recipe.get("image"));
            imageView.setFitWidth(250);
            imageView.setPreserveRatio(true);

            // Load full recipe info on click
            card.setOnMouseClicked(e -> {
                Map<String, String> details = RecipeAPIService.getRecipeDetails(recipe.get("id"));
                showRecipeDetailPopup(details);
            });

            card.getChildren().addAll(imageView, title);
            container.getChildren().add(card);
        }

        ScrollPane scrollPane = new ScrollPane(container);
        scrollPane.setFitToWidth(true);

        dialog.getDialogPane().setContent(scrollPane);
        dialog.showAndWait();
    }
    private void showRecipeDetailPopup(Map<String, String> recipeDetails) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(recipeDetails.get("title"));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        ImageView imageView = new ImageView(recipeDetails.get("image"));
        imageView.setFitWidth(350);
        imageView.setPreserveRatio(true);

        Label title = new Label(recipeDetails.get("title"));
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        TextArea instructions = new TextArea(recipeDetails.get("instructions"));
        instructions.setWrapText(true);
        instructions.setEditable(false);
        instructions.setPrefHeight(300);

        VBox content = new VBox(10, imageView, title, instructions);
        content.setPadding(new Insets(10));

        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    /** Inner recipe record */
    private static class Recipe {
        String id, name, match, available, missing, aiTip;
        boolean favorite;
        boolean aiRecommended;

        Recipe(String n, String m, String a, String miss, String tip) {
            name = n;
            match = m;
            available = a;
            missing = miss;
            aiTip = tip;
            favorite = false;
            aiRecommended = false;
        }
    }
}
