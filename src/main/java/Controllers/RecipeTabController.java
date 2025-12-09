
// File: src/main/java/Controllers/RecipeTabController.java
package Controllers;

import AI.AiRecipeService;
import AI.RecipeDTO;
import Firebase.FirebaseConfiguration;
import Firebase.FirebaseService;
import Pantry.PantryItem;
import com.example.demo1.UserSession;
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

import javafx.scene.layout.TilePane;   // grid container
import javafx.scene.layout.Region;     // for pref size clamps

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class RecipeTabController extends BaseController {

    // ==== Tabs / Saved ====
    @FXML private TabPane tabs;
    @FXML private Tab tabDiscover;
    @FXML private Tab tabSaved;
    @FXML private VBox savedVBox;

    // ==== Discover UI ====
    @FXML private Button backButton;
    @FXML private TextField aiInputField;
    @FXML private Button generateButton;
    @FXML private Button allRecipesBtn;
    @FXML private Button readyBtn;
    @FXML private Button favoriteBtn;     // shows ⭐ Favorites (N) and opens sort menu
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

    // ==== Discover: favorites view state ====
    private int favoritesCount = 0;
    private enum FavSort { UPDATED, MATCH }
    private FavSort favoritesSortMode = FavSort.UPDATED; // persisted per-user

    // Saved tab: show only favorites flag
    private boolean showOnlySavedFavorites = false;

    // ==== State ====
    private String currentUserId;
    private String currentFilter = "all";
    private List<Recipe> allRecipes = List.of(); // safe default
    private List<UnifiedRecipe> currentUnified = new ArrayList<>();

    // ==== Lifecycle ====
    @FXML
    public void initialize() {
        FirebaseConfiguration.initialize();
        currentUserId = UserSession.getCurrentUserId();
        firebase = new FirebaseService();
        ai = new AiRecipeService();

        // Load user prefs (favorites sort), then set menu & badge
        loadUserPrefs();
        attachFavoritesSortMenu();
        updateFavoritesCount();

        // Preflight local model
        generateButton.setDisable(true);
        vBox.getChildren().setAll(new Label("Checking local AI model…"));
        CompletableFuture.supplyAsync(ai::isModelAvailable)
                .whenComplete((ok, err) -> Platform.runLater(() -> {
                    if (Boolean.TRUE.equals(ok)) {
                        generateButton.setDisable(false);
                        vBox.getChildren().clear();
                        var initial = allRecipes.stream().map(this::fromSample).toList();
                        currentUnified = new ArrayList<>(initial);
                        renderUnified(applySearchAndFilters(currentUnified));
                    } else {
                        vBox.getChildren().setAll(new Label(
                                "Local model not found. Start Ollama and pull the model (e.g., phi3:mini)."));
                    }
                }));

        // Saved tab first load
        loadSavedRecipes(false);

        // Keep Saved tab fresh; reset the Saved-only flag when leaving it
        if (tabs != null) {
            tabs.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
                if (newTab == tabSaved) {
                    loadSavedRecipes(showOnlySavedFavorites);
                } else {
                    showOnlySavedFavorites = false;
                }
            });
        }
    }

    // ==== Navigation ====
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
            showError("Failed to navigate to Pantry Dashboard");
        }
    }

    // ==== Generate (Discover) ====
    @FXML
    private void handleGenerateRecipe() {
        if (!ai.isModelAvailable()) {
            showError("Local AI not ready. Run: 1) ollama pull phi3:mini  2) ollama serve");
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
                String msg = (err.getCause() != null) ? err.getCause().getMessage() : err.getMessage();
                showError("Failed to generate recipes: " + msg);
                return;
            }
            if (recipes == null || recipes.isEmpty()) {
                vBox.getChildren().setAll(new Label("No recipes returned. Try again."));
                return;
            }
            currentUnified = recipes.stream().map(this::fromAI).toList();
            renderUnified(applySearchAndFilters(currentUnified));
            showSuccess("AI recipes generated!");
        }));
    }

    // ==== Search + Filters (Discover) ====
    @FXML private void handleSearch()                 { renderUnified(applySearchAndFilters(currentUnified)); }

    @FXML private void handleFilterAll()              { loadAllSavedToDiscover(); }
    @FXML private void handleFilterReady()            { currentFilter = "ready";        renderUnified(applySearchAndFilters(currentUnified)); }
    @FXML private void handleFilterFavorites()        { currentFilter = "favorites";    loadFavoritesToDiscover(); }
    @FXML private void handleFilterNotReady()         { currentFilter = "notready";     renderUnified(applySearchAndFilters(currentUnified)); }
    @FXML private void handleFilterAIRecommended()    { currentFilter = "airecommended";renderUnified(applySearchAndFilters(currentUnified)); }
    @FXML private void handleGenerateAgain()          { handleGenerateRecipe(); }
    @FXML private void handleSeeMore()                { showSuccess("Loading more recipes..."); }
    @FXML private void handleAddRecipe()              { openPopup("/com/example/demo1/AddRecipe.fxml", "Add Recipe"); }

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
            case "ready"        -> base.stream().filter(r -> r.missingIngredients.isEmpty()).toList();
            case "notready"     -> base.stream().filter(r -> !r.missingIngredients.isEmpty()).toList();
            case "favorites"    -> base.stream().filter(r -> r.favorite).toList();
            case "airecommended"-> base.stream().sorted(Comparator.comparingInt(r -> -safeMatchPercent(r.match))).toList();
            default             -> base;
        };
    }

    private int safeMatchPercent(String matchText) {
        if (matchText == null) return 0;
        String digits = matchText.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return 0;
        try { return Integer.parseInt(digits); } catch (NumberFormatException e) { return 0; }
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

    // ==== Firestore loads for Discover ====
    private void loadFavoritesToDiscover() {
        if (currentUserId == null || currentUserId.isBlank()) {
            showError("Please log in first.");
            return;
        }
        vBox.getChildren().setAll(new Label("Loading favorite recipes…"));

        CompletableFuture.supplyAsync(() -> {
            try {
                var query = userRecipesRef().whereEqualTo("favorite", true);
                List<DocumentSnapshot> docs = new ArrayList<>(query.get().get().getDocuments());

                if (favoritesSortMode == FavSort.MATCH) {
                    docs.sort((a, b) -> {
                        String ma = Optional.ofNullable(a.getString("match")).orElse("0");
                        String mb = Optional.ofNullable(b.getString("match")).orElse("0");
                        int ia = safeMatchPercent(ma);
                        int ib = safeMatchPercent(mb);
                        return Integer.compare(ib, ia);
                    });
                } else {
                    docs.sort((a, b) -> {
                        var ta = a.getTimestamp("updatedAt");
                        var tb = b.getTimestamp("updatedAt");
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
        }).whenComplete((favs, err) -> Platform.runLater(() -> {
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

    private void loadAllSavedToDiscover() {
        if (currentUserId == null || currentUserId.isBlank()) {
            showError("Please log in first.");
            return;
        }
        vBox.getChildren().setAll(new Label("Loading all saved recipes…"));

        CompletableFuture.supplyAsync(() -> {
            try {
                List<DocumentSnapshot> docs = new ArrayList<>(userRecipesRef().get().get().getDocuments());
                docs.sort((a, b) -> {
                    var ta = a.getTimestamp("updatedAt");
                    var tb = b.getTimestamp("updatedAt");
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
        }).whenComplete((all, err) -> Platform.runLater(() -> {
            if (err != null) {
                vBox.getChildren().setAll(new Label("Failed to load saved recipes."));
                return;
            }
            currentUnified = all;
            currentFilter = "all";
            renderUnified(currentUnified);
            updateFavoritesCount();
        }));
    }

    // ==== Post-add prompt + nav ====
    private void afterAddMissingNavigate() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Added to Shopping List");
        alert.setHeaderText("Missing ingredients added to your Shopping List.");
        alert.setContentText("Do you want to open your Shopping List now?");

        ButtonType openBtn = new ButtonType("Open List");
        ButtonType stayBtn = new ButtonType("Stay Here", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(openBtn, stayBtn);

        alert.showAndWait().ifPresent(bt -> {
            if (bt == openBtn) openShoppingList();
        });
    }

    private void openShoppingList() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/demo1/PantryItemsView.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) vBox.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Shopping List");
            stage.show();
        } catch (IOException e) {
            showError("Failed to open Shopping List: " + e.getMessage());
        }
    }

    // ==== Discover renderer (uniform grid) ====
    private void renderUnified(List<UnifiedRecipe> recipes) {
        vBox.getChildren().clear();
        if (recipes == null || recipes.isEmpty()) {
            vBox.getChildren().add(new Label("No recipes to show."));
            return;
        }

        TilePane grid = new TilePane();
        grid.setHgap(20);
        grid.setVgap(20);
        grid.setPrefColumns(2);                 // default 2 columns
        grid.setTileAlignment(Pos.TOP_LEFT);
        grid.setPrefTileWidth(540);
        grid.setPrefTileHeight(240);
        grid.setPadding(new Insets(0, 0, 20, 0));

        // responsive column count
        vBox.widthProperty().addListener((obs, w, wNew) -> {
            double width = wNew.doubleValue();
            int cols = width >= 1200 ? 3 : 2;
            grid.setPrefColumns(cols);
            grid.setPrefTileWidth((width - (cols - 1) * grid.getHgap()) / cols);
        });

        for (UnifiedRecipe r : ("favorites".equals(currentFilter) ? recipes : enrichFavorites(recipes))) {
            VBox card = createUnifiedCard(r);
            card.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            card.setPrefWidth(grid.getPrefTileWidth());
            card.setPrefHeight(grid.getPrefTileHeight());
            grid.getChildren().add(card);
        }

        vBox.getChildren().add(grid);
    }

    private VBox createUnifiedCard(UnifiedRecipe r) {
        VBox card = new VBox(12);
        card.getStyleClass().add("recipe-card");
        card.setPadding(new Insets(16));

        Label name = new Label(r.title);
        name.getStyleClass().add("recipe-name");

        Label match = new Label(r.match == null ? "—" : r.match);
        match.getStyleClass().add("match-badge");

        Button star = new Button(r.favorite ? "★" : "☆");
        star.setOnAction(e -> {
            star.setDisable(true);
            boolean newVal = !r.favorite;
            CompletableFuture.runAsync(() -> toggleFavorite(r, newVal))
                    .whenComplete((v, err) -> Platform.runLater(() -> {
                        star.setDisable(false);
                        if (err != null) {
                            showError("Failed to update favorite: " + err.getMessage());
                        } else {
                            r.favorite = newVal;
                            star.setText(r.favorite ? "★" : "☆");
                            updateFavoritesCount();
                            if ("favorites".equalsIgnoreCase(currentFilter) && !r.favorite) {
                                loadFavoritesToDiscover();
                            }
                        }
                    }));
        });

        Button addMissingBtn = new Button("+ Add Missing to Shopping List");
        addMissingBtn.getStyleClass().add("add-to-list-button");
        addMissingBtn.setDisable(r.missingIngredients.isEmpty());
        addMissingBtn.setOnAction(e -> {
            addMissingBtn.setDisable(true);
            CompletableFuture.runAsync(() -> addMissingToShoppingList(r.missingIngredients))
                    .whenComplete((v, err) -> Platform.runLater(() -> {
                        if (err != null) {
                            addMissingBtn.setDisable(false);
                            showError("Failed to add to shopping list: " + err.getMessage());
                        } else {
                            showSuccess("Missing ingredients added!");
                            afterAddMissingNavigate();
                        }
                    }));
        });

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
                            if (tabs != null && tabSaved != null) {
                                loadSavedRecipes(false);
                                tabs.getSelectionModel().select(tabSaved);
                            }
                            updateFavoritesCount();
                        }
                    }));
        });

        // Delete on Discover
        Button deleteBtn = new Button("🗑 Delete");
        deleteBtn.getStyleClass().add("danger");
        deleteBtn.setOnAction(e -> {
            if (!confirmDelete(r.title)) return;
            deleteBtn.setDisable(true);
            CompletableFuture.runAsync(() -> deleteSaved(r))
                    .whenComplete((v, err) -> Platform.runLater(() -> {
                        deleteBtn.setDisable(false);
                        if (err != null) {
                            showError("Failed to delete: " + err.getMessage());
                        } else {
                            showSuccess("Recipe deleted.");
                            switch (currentFilter) {
                                case "favorites" -> loadFavoritesToDiscover();
                                case "all"       -> loadAllSavedToDiscover();
                                default          -> renderUnified(applySearchAndFilters(currentUnified));
                            }
                            updateFavoritesCount();
                        }
                    }));
        });

        HBox header = new HBox(12, name, match, star);
        header.setAlignment(Pos.CENTER_LEFT);

        String availableText = r.ingredients.isEmpty() ? "—" : String.join(", ", r.ingredients);
        HBox available = createIngredientRow("✓", "available-icon", "Ingredients:", availableText);

        String missingText = r.missingIngredients.isEmpty() ? "None" : String.join(", ", r.missingIngredients);
        HBox missing = createIngredientRow("✗", "missing-icon", "Missing:", missingText);

        String meta = ((r.estimatedTime != null && !r.estimatedTime.isBlank()) ? "⏱ " + r.estimatedTime + "  " : "")
                + ((r.calories != null) ? "🔥 " + r.calories + " kcal" : "");
        VBox metaBox = new VBox(new Label(meta.isEmpty() ? "" : meta));
        metaBox.getStyleClass().add("ai-tip");

        HBox actions = new HBox(10, addMissingBtn, saveBtn, deleteBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(header, available, missing, actions, metaBox);
        return card;
    }

    // ==== Saved tab ====
    private void loadSavedRecipes() { loadSavedRecipes(false); }

    private void loadSavedRecipes(boolean favoritesOnly) {
        if (currentUserId == null || currentUserId.isBlank() || savedVBox == null) return;
        savedVBox.getChildren().setAll(new Label("Loading saved recipes…"));

        CompletableFuture.supplyAsync(() -> {
            try {
                Query base = userRecipesRef();
                if (favoritesOnly) base = base.whereEqualTo("favorite", true);

                List<DocumentSnapshot> docs = new ArrayList<>(base.get().get().getDocuments());
                docs.sort((a, b) -> {
                    var ta = a.getTimestamp("updatedAt");
                    var tb = b.getTimestamp("updatedAt");
                    if (ta == null && tb == null) return 0;
                    if (ta == null) return 1;
                    if (tb == null) return -1;
                    return tb.compareTo(ta);
                });

                List<UnifiedRecipe> list = new ArrayList<>();
                for (DocumentSnapshot d : docs) list.add(fromDoc(d));
                return list;

            } catch (Exception first) {
                first.printStackTrace();
                throw new RuntimeException("Load failed: " + first.getMessage(), first);
            }
        }).whenComplete((list, err) -> Platform.runLater(() -> {
            if (err != null) {
                savedVBox.getChildren().setAll(new Label("Failed to load saved recipes:\n" + err.getMessage()));
            } else {
                renderSaved(list);
            }
        }));
    }

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
            if (i + 1 < recipes.size()) row.getChildren().add(createSavedCard(recipes.get(i + 1)));
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
        star.setOnAction(e -> {
            star.setDisable(true);
            boolean newVal = !r.favorite;
            CompletableFuture.runAsync(() -> toggleFavorite(r, newVal))
                    .whenComplete((v, err) -> Platform.runLater(() -> {
                        star.setDisable(false);
                        if (err != null) {
                            showError("Failed to update favorite: " + err.getMessage());
                        } else {
                            r.favorite = newVal;
                            star.setText(r.favorite ? "★" : "☆");
                            updateFavoritesCount();
                            if (showOnlySavedFavorites && !r.favorite) loadSavedRecipes(true);
                        }
                    }));
        });

        Button del = new Button("🗑 Delete");
        del.getStyleClass().add("danger");
        del.setOnAction(e -> {
            if (!confirmDelete(r.title)) return;
            del.setDisable(true);
            CompletableFuture.runAsync(() -> deleteSaved(r))
                    .whenComplete((v, err) -> Platform.runLater(() -> {
                        del.setDisable(false);
                        if (err != null) {
                            showError("Failed to delete: " + err.getMessage());
                        } else {
                            showSuccess("Deleted.");
                            loadSavedRecipes(showOnlySavedFavorites);
                            updateFavoritesCount();
                        }
                    }));
        });

        Button addMissingBtn = new Button("+ Add Missing to Shopping List");
        addMissingBtn.getStyleClass().add("add-to-list-button");
        addMissingBtn.setDisable(r.missingIngredients.isEmpty());
        addMissingBtn.setOnAction(e -> {
            addMissingBtn.setDisable(true);
            CompletableFuture.runAsync(() -> addMissingToShoppingList(r.missingIngredients))
                    .whenComplete((v, err) -> Platform.runLater(() -> {
                        if (err != null) {
                            addMissingBtn.setDisable(false);
                            showError("Failed to add to shopping list: " + err.getMessage());
                        } else {
                            showSuccess("Missing ingredients added!");
                            afterAddMissingNavigate();
                        }
                    }));
        });

        HBox header = new HBox(12, name, star, del);
        header.setAlignment(Pos.CENTER_LEFT);

        String ingredients = r.ingredients.isEmpty() ? "—" : String.join(", ", r.ingredients);
        String missing     = r.missingIngredients.isEmpty() ? "None" : String.join(", ", r.missingIngredients);
        HBox row1 = createIngredientRow("✓", "available-icon", "Ingredients:", ingredients);
        HBox row2 = createIngredientRow("✗", "missing-icon", "Missing:",     missing);

        String meta = ((r.estimatedTime != null && !r.estimatedTime.isBlank()) ? "⏱ " + r.estimatedTime + "  " : "")
                + ((r.calories != null) ? "🔥 " + r.calories + " kcal" : "");
        VBox metaBox = new VBox(new Label(meta.isEmpty() ? "" : meta));
        metaBox.getStyleClass().add("ai-tip");

        card.getChildren().addAll(header, row1, row2, addMissingBtn, metaBox);
        return card;
    }

    private boolean confirmDelete(String title) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle("Delete Recipe");
        a.setHeaderText(null);
        a.setContentText("Delete \"" + title + "\" from My Recipes?");
        a.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        Optional<ButtonType> res = a.showAndWait();
        return res.isPresent() && res.get() == ButtonType.OK;
    }

    private void deleteSaved(UnifiedRecipe r) {
        try {
            userRecipesRef().document(slug(r.title)).delete().get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private UnifiedRecipe fromDoc(DocumentSnapshot d) {
        UnifiedRecipe r = new UnifiedRecipe();
        r.title = Optional.ofNullable(d.getString("title")).orElse("Untitled Recipe");
        r.ingredients = castList(d.get("ingredients"));
        r.steps = castList(d.get("steps"));
        r.missingIngredients = castList(d.get("missingIngredients"));
        r.estimatedTime = Optional.ofNullable((String) d.get("estimatedTime")).orElse("");
        Object c = d.get("calories");
        r.calories = (c instanceof Number) ? ((Number)c).intValue() : null;
        r.source = Source.AI;
        r.match = Optional.ofNullable((String) d.get("match")).orElse("—");
        r.favorite = Boolean.TRUE.equals(d.getBoolean("favorite"));
        return r;
    }

    @SuppressWarnings("unchecked")
    private List<String> castList(Object v) {
        if (v instanceof List<?> list) {
            return list.stream().filter(Objects::nonNull).map(Object::toString).toList();
        }
        return List.of();
    }

    // ==== Mapping ====
    private UnifiedRecipe fromSample(Recipe s) {
        UnifiedRecipe r = new UnifiedRecipe();
        r.title = s.name;
        r.ingredients = splitCSV(s.available);
        r.missingIngredients = splitCSV(s.missing).stream().filter(x -> !"None".equalsIgnoreCase(x)).toList();
        r.steps = List.of();
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

    // ==== Firestore helpers ====
    private void toggleFavorite(UnifiedRecipe r, boolean newVal) {
        if (currentUserId == null || currentUserId.isBlank()) throw new IllegalStateException("No user");
        Map<String, Object> data = mapForFirestore(r);
        data.put("favorite", newVal);
        data.put("updatedAt", FieldValue.serverTimestamp());
        try {
            userRecipesRef().document(slug(r.title)).set(data, SetOptions.merge()).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

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

    private void addMissingToShoppingList(List<String> missing) {
        if (currentUserId == null || currentUserId.isBlank()) throw new IllegalStateException("No user");
        if (missing == null || missing.isEmpty()) return;

        Firestore db = FirebaseConfiguration.getDatabase();
        CollectionReference listRef = db.collection("users").document(currentUserId).collection("shoppingList");
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

    // ==== User prefs (persist favorites sort) ====
    private DocumentReference userPrefsRef() {
        Firestore db = FirebaseConfiguration.getDatabase();
        return db.collection("users").document(currentUserId).collection("meta").document("preferences");
    }

    private void loadUserPrefs() {
        if (currentUserId == null || currentUserId.isBlank()) return;
        CompletableFuture.supplyAsync(() -> {
            try { return userPrefsRef().get().get(); } catch (Exception e) { return null; }
        }).whenComplete((snap, err) -> Platform.runLater(() -> {
            if (err != null || snap == null || !snap.exists()) return;
            String mode = snap.getString("favoritesSortMode");
            if (mode != null) {
                try { favoritesSortMode = FavSort.valueOf(mode.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException ignored) {}
            }
        }));
    }

    private void saveFavoritesSortPref(FavSort mode) {
        if (currentUserId == null || currentUserId.isBlank()) return;
        Map<String, Object> data = Map.of("favoritesSortMode", mode.name().toLowerCase(Locale.ROOT));
        CompletableFuture.runAsync(() -> {
            try { userPrefsRef().set(data, SetOptions.merge()).get(); } catch (Exception ignored) {}
        });
    }

    private void attachFavoritesSortMenu() {
        if (favoriteBtn == null) return;

        var menu = new ContextMenu();
        var byUpdated = new MenuItem("Sort by Updated");
        var byMatch   = new MenuItem("Sort by Match %");

        byUpdated.setOnAction(e -> {
            favoritesSortMode = FavSort.UPDATED;
            saveFavoritesSortPref(favoritesSortMode);
            if ("favorites".equalsIgnoreCase(currentFilter)) loadFavoritesToDiscover();
        });
        byMatch.setOnAction(e -> {
            favoritesSortMode = FavSort.MATCH;
            saveFavoritesSortPref(favoritesSortMode);
            if ("favorites".equalsIgnoreCase(currentFilter)) loadFavoritesToDiscover();
        });

        menu.getItems().addAll(byUpdated, byMatch);

        // Left-click opens menu too
        favoriteBtn.setOnMouseClicked(e -> menu.show(favoriteBtn, e.getScreenX(), e.getScreenY()));
        favoriteBtn.setContextMenu(menu);
    }

    private void updateFavoritesCount() {
        if (favoriteBtn == null || currentUserId == null || currentUserId.isBlank()) return;

        CompletableFuture.supplyAsync(() -> {
            try { return userRecipesRef().whereEqualTo("favorite", true).get().get().size(); }
            catch (Exception e) { return -1; }
        }).whenComplete((n, err) -> Platform.runLater(() -> {
            if (err != null || n < 0) return;
            favoritesCount = n;
            favoriteBtn.setText("⭐ Favorites" + (favoritesCount > 0 ? " (" + favoritesCount + ")" : ""));
            favoriteBtn.setDisable(favoritesCount == 0);
        }));
    }

    // ==== Pantry filter ====
    private List<PantryItem> filterPantryItems(List<PantryItem> items) {
        LocalDate today = LocalDate.now();
        return items.stream()
                .filter(Objects::nonNull)
                .filter(p -> p.getQuantityNumeric() > 0)
                .filter(p -> p.getExpires() == null || !p.getExpires().isBefore(today))
                .toList();
    }

    // ==== UI helpers ====
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

    // ==== Sample data & models ====
    private static class Recipe {
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
}

