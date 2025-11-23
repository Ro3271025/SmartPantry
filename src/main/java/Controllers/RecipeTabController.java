// File: src/main/java/Controllers/RecipeTabController.java
package Controllers;

import ai.AiRecipeService;
import ai.RecipeDTO;
import com.example.demo1.FirebaseConfiguration;
import com.example.demo1.FirebaseService;
import com.example.demo1.PantryItem;
import com.example.demo1.UserSession;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class RecipeTabController extends BaseController {

    // ==== FXML ====
    @FXML private Button backButton;
    @FXML private TextField aiInputField;
    @FXML private Button generateButton;
    @FXML private Button allRecipesBtn;
    @FXML private Button readyBtn;
    @FXML private Button favoriteBtn;
    @FXML private Button notReadyBtn;
    @FXML private Button aiRecommendedBtn;
    @FXML private Button addRecipeBtn;
    @FXML private VBox vBox;
    @FXML private Button generateAgainBtn;
    @FXML private Button seeMoreBtn;
    @FXML private TextField searchField;

    // ==== Services ====
    private FirebaseService firebase;
    private AiRecipeService ai;

    // ==== State ====
    private String currentUserId;
    private String currentFilter = "all";
    private List<Recipe> allRecipes; // your existing sample data
    private List<UnifiedRecipe> currentUnified = new ArrayList<>(); // rendered list

    @FXML
    public void initialize() {
        FirebaseConfiguration.initialize();
        currentUserId = UserSession.getCurrentUserId();
        firebase = new FirebaseService();
        ai = new AiRecipeService();

        allRecipes = createSampleRecipes();

        // Model preflight
        generateButton.setDisable(true);
        vBox.getChildren().setAll(new Label("Checking local AI model…"));
        CompletableFuture.supplyAsync(ai::isModelAvailable)
                .whenComplete((ok, err) -> Platform.runLater(() -> {
                    if (Boolean.TRUE.equals(ok)) {
                        generateButton.setDisable(false);
                        vBox.getChildren().clear();
                        // Initial render from static samples
                        var initial = allRecipes.stream().map(this::fromSample).toList();
                        currentUnified = new ArrayList<>(initial);
                        renderUnified(applySearchAndFilters(currentUnified));
                    } else {
                        vBox.getChildren().setAll(new Label(
                                "Local model not found. Start Ollama and pull the model (e.g., phi3:mini)."));
                        showError("Ollama not running or model missing.\nRun: 1) ollama pull phi3:mini   2) ollama serve");
                    }
                }));
    }

    // ==== Back nav ====
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

    // ==== AI generate ====
    @FXML
    private void handleGenerateRecipe() {
        if (!ai.isModelAvailable()) {
            showError("Local AI not ready. Start Ollama and pull the model.\nRun: 1) ollama pull phi3:mini   2) ollama serve");
            return;
        }
        if (currentUserId == null || currentUserId.isBlank()) {
            showError("Please log in first.");
            return;
        }

        String prompt = aiInputField.getText().trim();
        generateButton.setDisable(true);
        vBox.getChildren().setAll(new Label("Generating recipes…"));

        CompletableFuture.supplyAsync(() -> {
            try {
                var items = firebase.getPantryItems(currentUserId);
                var filtered = filterPantryItems(items);
                return ai.generateRecipes(filtered, prompt, 3);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).whenComplete((recipes, err) -> Platform.runLater(() -> {
            generateButton.setDisable(false);
            aiInputField.clear();
            if (err != null) {
                String msg = err.getCause() != null ? err.getCause().getMessage() : err.getMessage();
                showError("Failed to generate recipes: " + msg);
            } else if (recipes == null || recipes.isEmpty()) {
                vBox.getChildren().setAll(new Label("No recipes returned. Try again."));
            } else {
                currentUnified = recipes.stream().map(this::fromAI).toList();
                renderUnified(applySearchAndFilters(currentUnified));
                showSuccess("AI recipes generated!");
            }
        }));
    }

    // ==== Search + Filters ====
    @FXML private void handleSearch() { renderUnified(applySearchAndFilters(currentUnified)); }
    @FXML private void handleFilterAll()          { currentFilter = "all";          renderUnified(applySearchAndFilters(currentUnified)); }
    @FXML private void handleFilterReady()        { currentFilter = "ready";        renderUnified(applySearchAndFilters(currentUnified)); }
    @FXML private void handleFilterFavorites()    { currentFilter = "favorites";    renderUnified(applySearchAndFilters(currentUnified)); }
    @FXML private void handleFilterNotReady()     { currentFilter = "notready";     renderUnified(applySearchAndFilters(currentUnified)); }
    @FXML private void handleFilterAIRecommended(){ currentFilter = "airecommended";renderUnified(applySearchAndFilters(currentUnified)); }
    @FXML private void handleGenerateAgain() { handleGenerateRecipe(); }
    @FXML private void handleSeeMore()       { showSuccess("Loading more recipes..."); }
    @FXML private void handleAddRecipe()     { openPopup("/com/example/demo1/AddRecipe.fxml", "Add Recipe"); }

    private List<UnifiedRecipe> applySearchAndFilters(List<UnifiedRecipe> input) {
        String q = (searchField == null || searchField.getText() == null) ? "" : searchField.getText().trim().toLowerCase();
        List<UnifiedRecipe> base = new ArrayList<>(input);
        if (!q.isBlank()) {
            base = base.stream().filter(r ->
                    r.title.toLowerCase().contains(q) ||
                            String.join(", ", r.ingredients).toLowerCase().contains(q) ||
                            String.join(", ", r.missingIngredients).toLowerCase().contains(q)
            ).toList();
        }
        return switch (currentFilter) {
            case "ready" -> base.stream()
                    .filter(r -> r.missingIngredients.isEmpty())
                    .toList();
            case "notready" -> base.stream()
                    .filter(r -> !r.missingIngredients.isEmpty())
                    .toList();
            case "favorites" -> {
                // Pull favorites state for current list from Firestore and enrich before filter
                var enriched = enrichFavorites(base);
                yield enriched.stream().filter(r -> r.favorite).toList();
            }
            case "airecommended" -> base.stream()
                    .sorted(Comparator.comparingInt(r -> -safeMatchPercent(((UnifiedRecipe)r).match)))
                    .toList();
            default -> base;
        };
    }

    // Parses strings like "75% match" → 75; returns 0 if missing/invalid.
    private int safeMatchPercent(String matchText) {
        if (matchText == null) return 0;
        String digits = matchText.replaceAll("[^0-9]", ""); // keep numbers only
        if (digits.isEmpty()) return 0;
        try { return Integer.parseInt(digits); } catch (NumberFormatException e) { return 0; }
    }


    // Enrich favorite flags from Firestore to current list (best-effort, non-blocking UX could be added)
    private List<UnifiedRecipe> enrichFavorites(List<UnifiedRecipe> list) {
        if (currentUserId == null || currentUserId.isBlank()) return list;
        try {
            var snapshots = userRecipesRef().get().get().getDocuments();
            Set<String> favIds = snapshots.stream()
                    .filter(d -> Boolean.TRUE.equals(d.getBoolean("favorite")))
                    .map(DocumentSnapshot::getId)
                    .collect(Collectors.toSet());
            return list.stream().map(r -> {
                r.favorite = favIds.contains(slug(r.title));
                return r;
            }).toList();
        } catch (Exception e) {
            return list;
        }
    }

    // ==== Rendering (Unified) ====
    private void renderUnified(List<UnifiedRecipe> recipes) {
        vBox.getChildren().clear();
        if (recipes == null || recipes.isEmpty()) {
            vBox.getChildren().add(new Label("No recipes to show."));
            return;
        }
        // ensure favorites state is current
        var finalList = enrichFavorites(recipes);
        for (int i = 0; i < finalList.size(); i += 2) {
            HBox row = new HBox(20);
            row.setAlignment(Pos.TOP_LEFT);
            row.getChildren().add(createUnifiedCard(finalList.get(i)));
            if (i + 1 < finalList.size()) row.getChildren().add(createUnifiedCard(finalList.get(i + 1)));
            vBox.getChildren().add(row);
        }
    }

    private VBox createUnifiedCard(UnifiedRecipe r) {
        VBox card = new VBox(12);
        card.getStyleClass().add("recipe-card");
        card.setPadding(new Insets(16));

        Label name = new Label(r.title);
        name.getStyleClass().add("recipe-name");

        Label match = new Label(r.match == null ? "—" : r.match);
        match.getStyleClass().add("match-badge");

        // ⭐ favorite toggle
        Button star = new Button(r.favorite ? "★" : "☆");
        star.setOnAction(e -> {
            star.setDisable(true);
            CompletableFuture.runAsync(() -> toggleFavorite(r, !r.favorite))
                    .whenComplete((v, err) -> Platform.runLater(() -> {
                        star.setDisable(false);
                        if (err != null) {
                            showError("Failed to update favorite: " + err.getMessage());
                        } else {
                            r.favorite = !r.favorite;
                            star.setText(r.favorite ? "★" : "☆");
                        }
                    }));
        });

        HBox header = new HBox(12, name, match, star);
        header.setAlignment(Pos.CENTER_LEFT);

        String availableText = r.ingredients.isEmpty() ? "—" : String.join(", ", r.ingredients);
        HBox available = createIngredientRow("✓", "available-icon", "Ingredients:", availableText);

        String missingText = r.missingIngredients.isEmpty() ? "None" : String.join(", ", r.missingIngredients);
        HBox missing = createIngredientRow("✗", "missing-icon", "Missing:", missingText);

        // Save button only for AI-sourced recipes
        Button saveBtn = new Button("💾 Save to My Recipes");
        saveBtn.getStyleClass().add("add-to-list-button");
        saveBtn.setDisable(r.source != Source.AI);
        saveBtn.setOnAction(e -> {
            saveBtn.setDisable(true);
            CompletableFuture.runAsync(() -> saveRecipe(r))
                    .whenComplete((v, err) -> Platform.runLater(() -> {
                        if (err != null) {
                            saveBtn.setDisable(false);
                            showError("Failed to save: " + err.getMessage());
                        } else {
                            showSuccess("Saved to My Recipes!");
                        }
                    }));
        });

        String meta = ((r.estimatedTime != null && !r.estimatedTime.isBlank()) ? "⏱ " + r.estimatedTime + "  " : "")
                + ((r.calories != null) ? "🔥 " + r.calories + " kcal" : "");
        VBox metaBox = new VBox(new Label(meta.isEmpty() ? "" : meta));
        metaBox.getStyleClass().add("ai-tip");

        card.getChildren().addAll(header, available, missing, saveBtn, metaBox);
        return card;
    }

    // ==== Mapping ====
    private UnifiedRecipe fromSample(Recipe s) {
        UnifiedRecipe r = new UnifiedRecipe();
        r.title = s.name;
        r.ingredients = splitCSV(s.available);
        r.missingIngredients = splitCSV(s.missing).stream().filter(x -> !"None".equalsIgnoreCase(x)).toList();
        r.steps = List.of();
        r.estimatedTime = "";
        r.calories = null;
        r.match = s.match;
        r.source = Source.STATIC;
        return r;
    }

    private UnifiedRecipe fromAI(RecipeDTO d) {
        UnifiedRecipe r = new UnifiedRecipe();
        r.title = (d.title == null || d.title.isBlank()) ? "Untitled Recipe" : d.title;
        r.ingredients = safeList(d.ingredients);
        r.missingIngredients = safeList(d.missingIngredients);
        r.steps = safeList(d.steps);
        r.estimatedTime = d.estimatedTime == null ? "" : d.estimatedTime;
        r.calories = d.calories;
        r.match = estimateMatch(r.ingredients, r.missingIngredients);
        r.source = Source.AI;
        return r;
    }

    private String estimateMatch(List<String> have, List<String> miss) {
        int total = Math.max(1, have.size());
        int used = Math.max(0, total - (miss == null ? 0 : miss.size()));
        int pct = Math.max(10, (int) Math.round(100.0 * used / total));
        return pct + "% match";
    }

    private List<String> splitCSV(String s) {
        if (s == null || s.isBlank()) return List.of();
        return Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isBlank()).toList();
    }

    private List<String> safeList(List<String> in) {
        return in == null ? List.of() : in.stream().filter(Objects::nonNull).map(String::trim).filter(x -> !x.isBlank()).toList();
    }

    // ==== Favorites persistence ====
    private void toggleFavorite(UnifiedRecipe r, boolean newVal) {
        if (currentUserId == null || currentUserId.isBlank()) throw new IllegalStateException("No user");
        Map<String, Object> data = mapForFirestore(r);
        data.put("favorite", newVal);
        try {
            userRecipesRef().document(slug(r.title)).set(data, SetOptions.merge()).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Save AI recipe content
    private void saveRecipe(UnifiedRecipe r) {
        if (currentUserId == null || currentUserId.isBlank()) throw new IllegalStateException("No user");
        Map<String, Object> data = mapForFirestore(r);
        data.put("favorite", r.favorite);
        try {
            userRecipesRef().document(slug(r.title)).set(data, SetOptions.merge()).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> mapForFirestore(UnifiedRecipe r) {
        Map<String, Object> m = new HashMap<>();
        m.put("title", r.title);
        m.put("ingredients", r.ingredients);
        m.put("steps", r.steps);
        m.put("missingIngredients", r.missingIngredients);
        m.put("estimatedTime", r.estimatedTime);
        m.put("calories", r.calories);
        m.put("source", r.source.name().toLowerCase());
        m.put("match", r.match);
        m.put("updatedAt", FieldValue.serverTimestamp());
        return m;
    }

    private CollectionReference userRecipesRef() {
        Firestore db = FirebaseConfiguration.getDatabase();
        return db.collection("users").document(currentUserId).collection("recipes");
    }

    private String slug(String title) {
        return title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    // ==== Pantry filter (skip expired/empty) ====
    private List<PantryItem> filterPantryItems(List<PantryItem> items) {
        LocalDate today = LocalDate.now();
        return items.stream()
                .filter(Objects::nonNull)
                .filter(p -> p.getQuantityNumeric() > 0)
                .filter(p -> p.getExpires() == null || !p.getExpires().isBefore(today))
                .toList();
    }

    // ==== UI helpers (shared) ====
    private HBox createIngredientRow(String icon, String iconStyle, String label, String items) {
        Label iconLabel = new Label(icon); iconLabel.getStyleClass().add(iconStyle);
        Label labelText = new Label(label); labelText.getStyleClass().add("ingredient-label");
        Label itemsText = new Label(items); itemsText.getStyleClass().add("ingredient-text");
        return new HBox(8, iconLabel, labelText, itemsText);
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText(null);
        alert.setTitle("Error");
        alert.showAndWait();
    }

    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setHeaderText(null);
        alert.setTitle("Success");
        alert.showAndWait();
    }

    // ==== Inner classes ====
    private static class Recipe { // your static sample model
        String name, match, available, missing, aiTip;
        Recipe(String n, String m, String a, String miss, String tip) {
            name = n; match = m; available = a; missing = miss; aiTip = tip;
        }
    }
    private enum Source { STATIC, AI }
    private static class UnifiedRecipe {
        String title;
        List<String> ingredients = List.of();
        List<String> steps = List.of();
        List<String> missingIngredients = List.of();
        String estimatedTime = "";
        Integer calories;
        String match = "—";
        boolean favorite = false;
        Source source = Source.STATIC;
    }

    private List<Recipe> createSampleRecipes() {
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
}
