package Controllers;

import AI.AiRecipeService;
import AI.RecipeDTO;
import Firebase.FirebaseConfiguration;
import Firebase.FirebaseService;
import Pantry.PantryItem;
import com.example.demo1.UserSession;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * MERGED RECIPE TAB CONTROLLER (SMART MERGE)
 * - UnifiedRecipe model
 * - AI Generation
 * - Saved Recipes Tab
 * - Favorites (+ sorting)
 * - Search & Filters
 * - Edit & Delete Recipes
 * - Pantry-aware recalculation
 * - Add Missing → Shopping List
 * - Slug-based Firestore IDs
 */
public class RecipeTabController extends BaseController {

    // ==== Tabs ====
    @FXML
    private TabPane tabs;
    @FXML
    private Tab tabDiscover;
    @FXML
    private Tab tabSaved;

    // ==== DISCOVER UI ====
    @FXML
    private Button backButton;
    @FXML
    private TextField aiInputField;
    @FXML
    private Button generateButton;
    @FXML
    private Button allRecipesBtn;
    @FXML
    private Button readyBtn;
    @FXML
    private Button favoriteBtn;
    @FXML
    private Button notReadyBtn;
    @FXML
    private Button aiRecommendedBtn;
    @FXML
    private Button addRecipeBtn;
    @FXML
    private VBox vBox;
    @FXML
    private Button generateAgainBtn;
    @FXML
    private Button seeMoreBtn;
    @FXML
    private TextField searchField;

    // ==== SAVED TAB ====
    @FXML
    private VBox savedVBox;

    // ==== SERVICES ====
    private FirebaseService firebase;
    private AiRecipeService ai;

    // ==== STATE ====
    private String currentUserId;
    private String currentFilter = "all";
    private List<UnifiedRecipe> currentUnified = new ArrayList<>();
    private int favoritesCount = 0;

    // ==== FAVORITES SORT ====
    private enum FavSort {UPDATED, MATCH}

    private FavSort favoritesSortMode = FavSort.UPDATED;
    private boolean showOnlySavedFavorites = false;

    // =====================================================================================
    // INITIALIZATION
    // =====================================================================================
    @FXML
    public void initialize() {
        FirebaseConfiguration.initialize();
        currentUserId = UserSession.getCurrentUserId();
        firebase = new FirebaseService();
        ai = new AiRecipeService();

        if (currentUserId == null || currentUserId.isBlank()) {
            showError("User session not found.");
            return;
        }

        // Load prefs
        loadUserPrefs();
        attachFavoritesSortMenu();

        // Discover: prepare AI model
        prepareAIModelStartup();

        // Saved tab: load default
        loadSavedRecipes(false);

        // Tab switch listener
        if (tabs != null) {
            tabs.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
                if (n == tabSaved) {
                    loadSavedRecipes(showOnlySavedFavorites);
                } else {
                    showOnlySavedFavorites = false;
                }
            });
        }
    }

    private void prepareAIModelStartup() {
        generateButton.setDisable(true);
        vBox.getChildren().setAll(new Label("Checking local AI model…"));

        CompletableFuture
                .supplyAsync(ai::isModelAvailable)
                .whenComplete((ok, err) -> Platform.runLater(() -> {
                    if (Boolean.TRUE.equals(ok)) {
                        generateButton.setDisable(false);
                        vBox.getChildren().clear();
                        loadAllSavedToDiscover();
                    } else {
                        vBox.getChildren().setAll(new Label(
                                "Local model not found. Start Ollama and pull phi3:mini."
                        ));
                    }
                }));
    }

    // NAVIGATION
    @FXML
    public void handleBackToPantry(ActionEvent event) throws IOException {
        switchScene(event, "PantryDashboard");
    }
    // AI GENERATION

    @FXML
    private void handleGenerateRecipe() {
        if (!ai.isModelAvailable()) {
            showError("Local AI model not ready.\nRun: ollama pull phi3:mini");
            return;
        }
        if (currentUserId == null || currentUserId.isBlank()) {
            showError("Please log in first.");
            return;
        }

        String prompt = aiInputField.getText().trim();
        if (prompt.isBlank()) {
            showError("Enter a recipe idea first!");
            return;
        }

        generateButton.setDisable(true);
        vBox.getChildren().setAll(new Label("Generating recipes…"));

        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        List<PantryItem> pantry = firebase.getPantryItems(currentUserId);
                        List<PantryItem> filtered = filterPantryItems(pantry);
                        return ai.generateRecipes(filtered, prompt, 3);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .whenComplete((recipes, err) -> Platform.runLater(() -> {
                    generateButton.setDisable(false);
                    aiInputField.clear();

                    if (err != null) {
                        showError("Failed to generate recipes: " + err.getMessage());
                        return;
                    }
                    if (recipes == null || recipes.isEmpty()) {
                        vBox.getChildren().setAll(new Label("No recipes returned. Try again."));
                        return;
                    }

                    currentUnified = recipes.stream()
                            .map(this::fromAI)
                            .collect(Collectors.toList());

                    renderUnified(applySearchAndFilters(currentUnified));
                    showSuccess("AI recipes generated!");
                }));
    }

    @FXML
    private void handleGenerateAgain() {
        handleGenerateRecipe();
    }

    @FXML private void handleSeeMore() {
        showSuccess("Loading more recipes... (future update)");
    }

    @FXML private void handleAddRecipe() {
        openPopup("../XMLFiles/AddRecipe.fxml", "Add Recipe");
    }

    // FILTERS + SEARCH

    @FXML
    private void handleFilterAll() {
        currentFilter = "all";
        loadAllSavedToDiscover();
    }

    @FXML
    private void handleFilterReady() {
        currentFilter = "ready";
        renderUnified(applySearchAndFilters(currentUnified));
    }

    @FXML
    private void handleFilterFavorites() {
        currentFilter = "favorites";
        loadFavoritesToDiscover();
    }

    @FXML
    private void handleFilterNotReady() {
        currentFilter = "notready";
        renderUnified(applySearchAndFilters(currentUnified));
    }

    @FXML
    private void handleFilterAIRecommended() {
        currentFilter = "airecommended";
        renderUnified(applySearchAndFilters(currentUnified));
    }

    @FXML
    private void handleSearch() {
        renderUnified(applySearchAndFilters(currentUnified));
    }

    private List<UnifiedRecipe> applySearchAndFilters(List<UnifiedRecipe> input) {
        String q = (searchField == null || searchField.getText() == null)
                ? ""
                : searchField.getText().trim().toLowerCase();

        List<UnifiedRecipe> filtered = input;

        // Search
        if (!q.isBlank()) {
            filtered = filtered.stream()
                    .filter(r ->
                            r.title.toLowerCase().contains(q) ||
                                    String.join(", ", r.ingredients).toLowerCase().contains(q) ||
                                    String.join(", ", r.missingIngredients).toLowerCase().contains(q)
                    )
                    .toList();
        }

        // Filters
        return switch (currentFilter) {
            case "ready" -> filtered.stream()
                    .filter(r -> r.missingIngredients.isEmpty())
                    .toList();

            case "notready" -> filtered.stream()
                    .filter(r -> !r.missingIngredients.isEmpty())
                    .toList();

            case "favorites" -> filtered.stream()
                    .filter(r -> r.favorite)
                    .toList();

            case "airecommended" -> filtered.stream()
                    .sorted(Comparator.comparingInt(r -> -safeMatchPercent(r.match)))
                    .toList();

            default -> filtered;
        };
    }

    private int safeMatchPercent(String matchText) {
        if (matchText == null) return 0;
        String digits = matchText.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return 0;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    // PANTRY FILTERING (FOR AI)

    private List<PantryItem> filterPantryItems(List<PantryItem> items) {
        LocalDate today = LocalDate.now();
        return items.stream()
                .filter(Objects::nonNull)
                .filter(p -> p.getQuantityNumeric() > 0)
                .filter(p -> p.getExpires() == null || !p.getExpires().isBefore(today))
                .toList();
    }
    // DISCOVER RENDERER

    private void renderUnified(List<UnifiedRecipe> recipes) {
        vBox.getChildren().clear();

        if (recipes == null || recipes.isEmpty()) {
            vBox.getChildren().add(new Label("No recipes to show."));
            return;
        }

        // Enrich favorites (unless already filtered)
        List<UnifiedRecipe> list =
                ("favorites".equalsIgnoreCase(currentFilter)) ? recipes : enrichFavorites(recipes);

        for (int i = 0; i < list.size(); i += 2) {
            HBox row = new HBox(20);
            row.setAlignment(Pos.TOP_LEFT);

            row.getChildren().add(createUnifiedCard(list.get(i)));
            if (i + 1 < list.size()) {
                row.getChildren().add(createUnifiedCard(list.get(i + 1)));
            }

            vBox.getChildren().add(row);
        }
    }
    // Pull favorite flags from Firestore and set r.favorite for each
    private List<UnifiedRecipe> enrichFavorites(List<UnifiedRecipe> list) {
        if (currentUserId == null || currentUserId.isBlank()) return list;
        try {
            var snapshots = userRecipesRef().get().get().getDocuments();
            Set<String> favIds = snapshots.stream()
                    .filter(d -> Boolean.TRUE.equals(d.getBoolean("favorite")))
                    .map(DocumentSnapshot::getId)
                    .collect(Collectors.toSet());
            return list.stream().map(r -> { r.favorite = favIds.contains(slug(r.title)); return r; }).toList();
        } catch (Exception e) {
            return list;
        }
    }

    private VBox createUnifiedCard(UnifiedRecipe r) {
        VBox card = new VBox(12);
        card.getStyleClass().add("recipe-card");
        card.setPadding(new Insets(16));

        // ==== HEADER ====
        Label name = new Label(r.title);
        name.getStyleClass().add("recipe-name");

        Label match = new Label(r.match == null ? "—" : r.match);
        match.getStyleClass().add("match-badge");

        Button star = new Button(r.favorite ? "★" : "☆");
        star.setOnAction(e -> toggleFavoriteAsync(r, star));

        HBox header = new HBox(12, name, match, star);
        header.setAlignment(Pos.CENTER_LEFT);

        // ==== INGREDIENTS ====
        HBox available = createIngredientRow(
                "✓", "available-icon",
                "Ingredients:", String.join(", ", r.ingredients)
        );

        HBox missing = createIngredientRow(
                "✗", "missing-icon",
                "Missing:",
                r.missingIngredients.isEmpty() ? "None" : String.join(", ", r.missingIngredients)
        );

        // ==== BUTTON: ADD MISSING → SHOPPING LIST ====
        Button addMissingBtn = new Button("+ Add Missing to Shopping List");
        addMissingBtn.getStyleClass().add("add-to-list-button");
        addMissingBtn.setDisable(r.missingIngredients.isEmpty());
        addMissingBtn.setOnAction(e -> addMissingAsync(r, addMissingBtn));

        // ==== BUTTON: SAVE TO MY RECIPES ====
        Button saveBtn = new Button("💾 Save to My Recipes");
        saveBtn.getStyleClass().add("add-to-list-button");
        saveBtn.setDisable(r.source != Source.AI);
        saveBtn.setOnAction(e -> saveRecipeAsync(r, saveBtn));

        // ==== AI META ====
        String meta = "";
        if (r.estimatedTime != null && !r.estimatedTime.isBlank()) meta += "⏱ " + r.estimatedTime + "  ";
        if (r.calories != null) meta += "🔥 " + r.calories + " kcal";

        Label metaLabel = new Label(meta);
        VBox metaBox = new VBox(metaLabel);
        metaBox.getStyleClass().add("ai-tip");

        // ==== ASSEMBLE ====
        card.getChildren().addAll(header, available, missing, addMissingBtn, saveBtn, metaBox);
        return card;
    }

    private void toggleFavoriteAsync(UnifiedRecipe r, Button star) {
        star.setDisable(true);

        boolean newVal = !r.favorite;

        CompletableFuture.runAsync(() -> toggleFavorite(r, newVal))
                .whenComplete((v, err) -> Platform.runLater(() -> {
                    star.setDisable(false);
                    if (err != null) {
                        showError("Failed to update favorite: " + err.getMessage());
                        return;
                    }

                    r.favorite = newVal;
                    star.setText(r.favorite ? "★" : "☆");
                    updateFavoritesCount();

                    // Remove from Discover favorites list instantly
                    if ("favorites".equalsIgnoreCase(currentFilter) && !r.favorite) {
                        loadFavoritesToDiscover();
                    }
                }));
    }

    private void addMissingAsync(UnifiedRecipe r, Button btn) {
        btn.setDisable(true);

        CompletableFuture.runAsync(() -> addMissingToShoppingList(r.missingIngredients))
                .whenComplete((v, err) -> Platform.runLater(() -> {
                    if (err != null) {
                        btn.setDisable(false);
                        showError("Failed to add to shopping list: " + err.getMessage());
                        return;
                    }

                    showSuccess("Missing ingredients added!");
                    afterAddMissingNavigate();
                }));
    }

    private void saveRecipeAsync(UnifiedRecipe r, Button btn) {
        btn.setDisable(true);

        CompletableFuture.runAsync(() -> saveRecipe(r))
                .whenComplete((v, err) -> Platform.runLater(() -> {
                    if (err != null) {
                        btn.setDisable(false);
                        showError("Failed to save recipe: " + err.getMessage());
                        return;
                    }

                    showSuccess("Saved to My Recipes!");
                    loadSavedRecipes(false);
                    tabs.getSelectionModel().select(tabSaved);
                    updateFavoritesCount();
                }));
    }

    // =====================================================================================
    // SAVED TAB RENDERING
    // =====================================================================================

    private void renderSaved(List<UnifiedRecipe> recipes) {
        savedVBox.getChildren().clear();

        if (recipes == null || recipes.isEmpty()) {
            savedVBox.getChildren().add(new Label("No saved recipes yet."));
            return;
        }

        for (int i = 0; i < recipes.size(); i += 2) {
            HBox row = new HBox(20);
            row.setAlignment(Pos.TOP_LEFT);

            row.getChildren().add(createSavedCard(recipes.get(i)));
            if (i + 1 < recipes.size()) {
                row.getChildren().add(createSavedCard(recipes.get(i + 1)));
            }

            savedVBox.getChildren().add(row);
        }
    }

    private VBox createSavedCard(UnifiedRecipe r) {
        VBox card = new VBox(12);
        card.getStyleClass().add("recipe-card");
        card.setPadding(new Insets(16));

        Label name = new Label(r.title);
        name.getStyleClass().add("recipe-name");

        Button star = new Button(r.favorite ? "★" : "☆");
        star.setOnAction(e -> toggleFavoriteAsync(r, star));

        Button del = new Button("🗑 Delete");
        del.getStyleClass().add("danger");
        del.setOnAction(e -> deleteSavedAsync(r, del));

        Button edit = new Button("✏ Edit");
        edit.getStyleClass().add("edit-button");
        edit.setOnAction(e -> openEditDialog(r));

        HBox header = new HBox(12, name, star, edit, del);
        header.setAlignment(Pos.CENTER_LEFT);

        HBox available = createIngredientRow(
                "✓", "available-icon",
                "Ingredients:", String.join(", ", r.ingredients)
        );

        HBox missing = createIngredientRow(
                "✗", "missing-icon",
                "Missing:",
                r.missingIngredients.isEmpty() ? "None" : String.join(", ", r.missingIngredients)
        );

        Button addMissingBtn = new Button("+ Add Missing to Shopping List");
        addMissingBtn.getStyleClass().add("add-to-list-button");
        addMissingBtn.setDisable(r.missingIngredients.isEmpty());
        addMissingBtn.setOnAction(e -> addMissingAsync(r, addMissingBtn));

        String meta = "";
        if (r.estimatedTime != null && !r.estimatedTime.isBlank()) meta += "⏱ " + r.estimatedTime + "  ";
        if (r.calories != null) meta += "🔥 " + r.calories + " kcal";

        Label metaLabel = new Label(meta);
        VBox metaBox = new VBox(metaLabel);
        metaBox.getStyleClass().add("ai-tip");

        card.getChildren().addAll(header, available, missing, addMissingBtn, metaBox);
        return card;
    }

    private void deleteSavedAsync(UnifiedRecipe r, Button del) {
        del.setDisable(true);

        CompletableFuture.runAsync(() -> deleteSaved(r))
                .whenComplete((v, err) -> Platform.runLater(() -> {
                    if (err != null) {
                        del.setDisable(false);
                        showError("Failed to delete: " + err.getMessage());
                        return;
                    }

                    showSuccess("Recipe deleted.");
                    loadSavedRecipes(showOnlySavedFavorites);
                    updateFavoritesCount();
                }));
    }
    // FIRESTORE: DELETE, SAVE, FAVORITES TOGGLE
    private void deleteSaved(UnifiedRecipe r) {
        try {
            userRecipesRef()
                    .document(slug(r.title))
                    .delete()
                    .get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void toggleFavorite(UnifiedRecipe r, boolean newVal) {
        if (currentUserId == null || currentUserId.isBlank())
            throw new IllegalStateException("No user");

        Map<String, Object> data = mapForFirestore(r);
        data.put("favorite", newVal);
        data.put("updatedAt", FieldValue.serverTimestamp());

        try {
            userRecipesRef()
                    .document(slug(r.title))
                    .set(data, SetOptions.merge())
                    .get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void saveRecipe(UnifiedRecipe r) {
        Map<String, Object> data = mapForFirestore(r);
        data.put("favorite", r.favorite);

        try {
            userRecipesRef()
                    .document(slug(r.title))
                    .set(data, SetOptions.merge())
                    .get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // =====================================================================================
    // FIRESTORE: ADD MISSING INGREDIENTS → SHOPPING LIST
    // =====================================================================================

    private void addMissingToShoppingList(List<String> missing) {
        if (missing == null || missing.isEmpty()) return;
        if (currentUserId == null || currentUserId.isBlank())
            throw new IllegalStateException("No user");

        Firestore db = FirebaseConfiguration.getDatabase();
        CollectionReference listRef =
                db.collection("users").document(currentUserId).collection("shoppingList");

        WriteBatch batch = db.batch();

        for (String raw : missing) {
            if (raw == null) continue;

            String name = raw.trim();
            if (name.isEmpty()) continue;

            String id = slug(name);

            Map<String, Object> data = new HashMap<>();
            data.put("name", name);
            data.put("quantity", 1);
            data.put("status", "pending");
            data.put("updatedAt", FieldValue.serverTimestamp());

            batch.set(listRef.document(id), data, SetOptions.merge());
        }

        try {
            batch.commit().get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // =====================================================================================
    // LOAD SAVED RECIPES (FULL + FAVORITES)
    // =====================================================================================

    private void loadSavedRecipes() {
        loadSavedRecipes(false);
    }

    private void loadSavedRecipes(boolean favoritesOnly) {
        if (currentUserId == null || currentUserId.isBlank() || savedVBox == null) return;

        savedVBox.getChildren().setAll(new Label("Loading saved recipes…"));

        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        Query base = userRecipesRef();
                        if (favoritesOnly) base = base.whereEqualTo("favorite", true);

                        List<DocumentSnapshot> docs =
                                new ArrayList<>(base.get().get().getDocuments());

                        // Sort by updatedAt DESC (locally)
                        docs.sort((a, b) -> {
                            Timestamp ta = a.getTimestamp("updatedAt");
                            Timestamp tb = b.getTimestamp("updatedAt");
                            if (ta == null && tb == null) return 0;
                            if (ta == null) return 1;
                            if (tb == null) return -1;
                            return tb.compareTo(ta);
                        });

                        List<UnifiedRecipe> list = new ArrayList<>();
                        for (DocumentSnapshot d : docs) list.add(fromDoc(d));
                        return list;
                    } catch (Exception ex) {
                        throw new RuntimeException("Load failed: " + ex.getMessage(), ex);
                    }
                })
                .whenComplete((list, err) -> Platform.runLater(() -> {
                    if (err != null) {
                        savedVBox.getChildren().setAll(
                                new Label("Failed to load saved recipes:\n" + err.getMessage())
                        );
                        return;
                    }
                    renderSaved(list);
                }));
    }

    // Load ALL saved documents into Discover tab
    private void loadAllSavedToDiscover() {
        if (currentUserId == null || currentUserId.isBlank()) {
            showError("Please log in first.");
            return;
        }

        vBox.getChildren().setAll(new Label("Loading recipes…"));

        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        List<DocumentSnapshot> docs =
                                new ArrayList<>(userRecipesRef().get().get().getDocuments());

                        docs.sort((a, b) -> {
                            Timestamp ta = a.getTimestamp("updatedAt");
                            Timestamp tb = b.getTimestamp("updatedAt");
                            if (ta == null && tb == null) return 0;
                            if (ta == null) return 1;
                            if (tb == null) return -1;
                            return tb.compareTo(ta);
                        });

                        List<UnifiedRecipe> list = new ArrayList<>();
                        for (DocumentSnapshot d : docs) list.add(fromDoc(d));
                        return list;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .whenComplete((all, err) -> Platform.runLater(() -> {
                    if (err != null) {
                        vBox.getChildren().setAll(
                                new Label("Failed to load recipes.")
                        );
                        return;
                    }

                    currentUnified = all;
                    currentFilter = "all";
                    renderUnified(currentUnified);
                    updateFavoritesCount();
                }));
    }

    // Load FAVORITE recipes into Discover
    private void loadFavoritesToDiscover() {
        if (currentUserId == null || currentUserId.isBlank()) {
            showError("Please log in first.");
            return;
        }

        vBox.getChildren().setAll(new Label("Loading favorites…"));

        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        Query q = userRecipesRef().whereEqualTo("favorite", true);
                        List<DocumentSnapshot> docs =
                                new ArrayList<>(q.get().get().getDocuments());

                        // Sort favorite docs based on user preference
                        if (favoritesSortMode == FavSort.MATCH) {
                            docs.sort((a, b) -> {
                                String ma = Optional.ofNullable(a.getString("match")).orElse("0");
                                String mb = Optional.ofNullable(b.getString("match")).orElse("0");
                                return Integer.compare(
                                        safeMatchPercent(mb),
                                        safeMatchPercent(ma)
                                );
                            });
                        } else {
                            // UPDATED sort
                            docs.sort((a, b) -> {
                                Timestamp ta = a.getTimestamp("updatedAt");
                                Timestamp tb = b.getTimestamp("updatedAt");
                                if (ta == null && tb == null) return 0;
                                if (ta == null) return 1;
                                if (tb == null) return -1;
                                return tb.compareTo(ta);
                            });
                        }

                        List<UnifiedRecipe> list = new ArrayList<>();
                        for (DocumentSnapshot d : docs) {
                            UnifiedRecipe r = fromDoc(d);
                            r.favorite = true;
                            list.add(r);
                        }

                        return list;

                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .whenComplete((favs, err) -> Platform.runLater(() -> {
                    if (err != null) {
                        vBox.getChildren().setAll(new Label("Failed to load favorites."));
                        return;
                    }

                    currentUnified = favs;
                    currentFilter = "favorites";
                    renderUnified(currentUnified);
                    updateFavoritesCount();
                }));
    }

    // =====================================================================================
    // RECIPE EDIT DIALOG
    // =====================================================================================

    private void openEditDialog(UnifiedRecipe r) {
        Dialog<UnifiedRecipe> dialog = new Dialog<>();
        dialog.setTitle("Edit Recipe");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Fields
        TextField titleField = new TextField(r.title);
        TextArea stepsArea = new TextArea(String.join("\n", r.steps));
        TextField timeField = new TextField(r.estimatedTime);
        TextField caloriesField = new TextField(r.calories == null ? "" : r.calories.toString());

        TextField ingField = new TextField(String.join(", ", r.ingredients));
        TextField missingField = new TextField(String.join(", ", r.missingIngredients));

        stepsArea.setPrefRowCount(5);

        // Layout
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(10));

        grid.addRow(0, new Label("Title:"), titleField);
        grid.addRow(1, new Label("Ingredients:"), ingField);
        grid.addRow(2, new Label("Missing:"), missingField);
        grid.addRow(3, new Label("Steps:"), stepsArea);
        grid.addRow(4, new Label("Time:"), timeField);
        grid.addRow(5, new Label("Calories:"), caloriesField);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;

            r.title = titleField.getText().trim();
            r.ingredients = splitCSV(ingField.getText());
            r.missingIngredients = splitCSV(missingField.getText());
            r.steps = Arrays.stream(stepsArea.getText().split("\n"))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();

            r.estimatedTime = timeField.getText().trim();
            try {
                r.calories = caloriesField.getText().isBlank()
                        ? null
                        : Integer.valueOf(caloriesField.getText().trim());
            } catch (NumberFormatException e) {
                r.calories = null;
            }

            // Recompute match %
            r.match = estimateMatch(r.ingredients, r.missingIngredients);

            return r;
        });

        dialog.showAndWait().ifPresent(updated -> {
            CompletableFuture.runAsync(() -> saveRecipe(updated))
                    .whenComplete((v, err) -> Platform.runLater(() -> {
                        if (err != null) {
                            showError("Failed to update recipe: " + err.getMessage());
                            return;
                        }
                        showSuccess("Recipe updated!");
                        loadSavedRecipes(showOnlySavedFavorites);
                        loadAllSavedToDiscover();
                    }));
        });
    }
    // =====================================================================================
    // MAPPING HELPERS (AI → UnifiedRecipe, Firestore → UnifiedRecipe)
    // =====================================================================================

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
        int total = Math.max(1, have.size() + miss.size());
        int haveCount = have.size();
        int pct = (int) Math.round((haveCount * 100.0) / total);

        return pct + "% match";
    }

    private UnifiedRecipe fromDoc(DocumentSnapshot d) {
        UnifiedRecipe r = new UnifiedRecipe();

        r.title = Optional.ofNullable(d.getString("title")).orElse("Untitled Recipe");
        r.ingredients = castList(d.get("ingredients"));
        r.steps = castList(d.get("steps"));
        r.missingIngredients = castList(d.get("missingIngredients"));

        r.estimatedTime = Optional.ofNullable(d.getString("estimatedTime")).orElse("");
        Object c = d.get("calories");
        r.calories = (c instanceof Number) ? ((Number) c).intValue() : null;

        r.match = Optional.ofNullable(d.getString("match")).orElse("—");
        r.favorite = Boolean.TRUE.equals(d.getBoolean("favorite"));
        r.source = Source.AI; // saved recipes are AI or added manually

        return r;
    }

    @SuppressWarnings("unchecked")
    private List<String> castList(Object v) {
        if (v instanceof List<?> list) {
            return list.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .toList();
        }
        return List.of();
    }

    private List<String> splitCSV(String s) {
        if (s == null || s.isBlank()) return List.of();

        return Arrays.stream(s.split(","))
                .map(String::trim)
                .filter(str -> !str.isBlank())
                .toList();
    }

    private List<String> safeList(List<String> in) {
        return in == null
                ? List.of()
                : in.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(x -> !x.isBlank())
                .toList();
    }

    // =====================================================================================
    // FIRESTORE HELPERS
    // =====================================================================================

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
        m.put("favorite", r.favorite);
        m.put("updatedAt", FieldValue.serverTimestamp());
        return m;
    }

    private CollectionReference userRecipesRef() {
        Firestore db = FirebaseConfiguration.getDatabase();
        return db.collection("users")
                .document(currentUserId)
                .collection("recipes");
    }

    private String slug(String title) {
        return title.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    // =====================================================================================
    // USER PREFERENCES (FAVORITES SORT MODE)
    // =====================================================================================

    private DocumentReference userPrefsRef() {
        Firestore db = FirebaseConfiguration.getDatabase();
        return db.collection("users")
                .document(currentUserId)
                .collection("meta")
                .document("preferences");
    }

    private void loadUserPrefs() {
        if (currentUserId == null || currentUserId.isBlank()) return;

        CompletableFuture.supplyAsync(() -> {
            try {
                return userPrefsRef().get().get();
            } catch (Exception e) {
                return null;
            }
        }).whenComplete((snap, err) -> Platform.runLater(() -> {
            if (snap == null || !snap.exists()) return;

            String mode = snap.getString("favoritesSortMode");
            if (mode != null) {
                try {
                    favoritesSortMode = FavSort.valueOf(mode.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {}
            }
        }));
    }

    private void saveFavoritesSortPref(FavSort mode) {
        if (currentUserId == null || currentUserId.isBlank()) return;

        Map<String, Object> data =
                Map.of("favoritesSortMode", mode.name().toLowerCase(Locale.ROOT));

        CompletableFuture.runAsync(() -> {
            try {
                userPrefsRef().set(data, SetOptions.merge()).get();
            } catch (Exception ignored) {}
        });
    }

    private void attachFavoritesSortMenu() {
        if (favoriteBtn == null) return;

        ContextMenu menu = new ContextMenu();
        MenuItem byUpdated = new MenuItem("Sort by Updated");
        MenuItem byMatch = new MenuItem("Sort by Match %");

        byUpdated.setOnAction(e -> {
            favoritesSortMode = FavSort.UPDATED;
            saveFavoritesSortPref(favoritesSortMode);
            if ("favorites".equalsIgnoreCase(currentFilter))
                loadFavoritesToDiscover();
        });

        byMatch.setOnAction(e -> {
            favoritesSortMode = FavSort.MATCH;
            saveFavoritesSortPref(favoritesSortMode);
            if ("favorites".equalsIgnoreCase(currentFilter))
                loadFavoritesToDiscover();
        });

        menu.getItems().addAll(byUpdated, byMatch);

        favoriteBtn.setOnMouseClicked(e ->
                menu.show(favoriteBtn, e.getScreenX(), e.getScreenY()));

        favoriteBtn.setContextMenu(menu);
    }

    private void updateFavoritesCount() {
        if (favoriteBtn == null || currentUserId == null || currentUserId.isBlank()) return;

        CompletableFuture.supplyAsync(() -> {
            try {
                return userRecipesRef()
                        .whereEqualTo("favorite", true)
                        .get()
                        .get()
                        .size();
            } catch (Exception e) {
                return -1;
            }
        }).whenComplete((count, err) -> Platform.runLater(() -> {
            if (count < 0) return;

            favoritesCount = count;
            favoriteBtn.setText(
                    "⭐ Favorites" + (favoritesCount > 0 ? " (" + favoritesCount + ")" : "")
            );
            favoriteBtn.setDisable(favoritesCount == 0);
        }));
    }

    // =====================================================================================
    // UI HELPER COMPONENTS
    // =====================================================================================

    private HBox createIngredientRow(String icon, String iconStyle, String label, String items) {
        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add(iconStyle);

        Label labelText = new Label(label);
        labelText.getStyleClass().add("ingredient-label");

        Label itemsText = new Label(items);
        itemsText.getStyleClass().add("ingredient-text");

        return new HBox(8, iconLabel, labelText, itemsText);
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.setHeaderText(null);
        alert.setTitle("Error");
        alert.showAndWait();
    }

    private void showSuccess(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        alert.setHeaderText(null);
        alert.setTitle("Success");
        alert.showAndWait();
    }

    private void afterAddMissingNavigate() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Added to Shopping List");
        alert.setHeaderText("Missing ingredients added.");
        alert.setContentText("Open your Shopping List now?");

        ButtonType openBtn = new ButtonType("Open List");
        ButtonType stayBtn = new ButtonType("Stay", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(openBtn, stayBtn);

        alert.showAndWait().ifPresent(bt -> {
            if (bt == openBtn) openShoppingList();
        });
    }

    private void openShoppingList() {
        try {
            FXMLLoader loader =
                    new FXMLLoader(getClass().getResource("/com/example/demo1/PantryItemsView.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) vBox.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Shopping List");
            stage.show();
        } catch (IOException e) {
            showError("Failed to open Shopping List: " + e.getMessage());
        }
    }

    // =====================================================================================
    // DATA MODELS
    // =====================================================================================

    private enum Source { STATIC, AI }

    private static class UnifiedRecipe {
        String title;
        List<String> ingredients = new ArrayList<>();
        List<String> steps = new ArrayList<>();
        List<String> missingIngredients = new ArrayList<>();

        String estimatedTime = "";
        Integer calories;

        String match = "—";
        boolean favorite = false;

        Source source = Source.STATIC;
    }
}
