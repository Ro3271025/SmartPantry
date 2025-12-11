package Controllers;

import AI.AiRecipeService;
import AI.RecipeDTO;
import Firebase.FirebaseConfiguration;
import Firebase.FirebaseService;
import Pantry.PantryItem;
import Recipe.RecipeAPIService;
import com.example.demo1.UserSession;
import com.google.cloud.firestore.*;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

public class RecipeTabController extends BaseController {

    // ===== Tabs / Saved =====
    @FXML private TabPane tabs;
    @FXML private Tab tabDiscover;
    @FXML private Tab tabSaved;
    @FXML private VBox savedVBox;

    // ===== Discover (legacy) UI =====
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

    // ===== Services =====
    private FirebaseService firebase;
    private AiRecipeService ai;

    // ===== State =====
    private String currentUserId;

    // legacy section
    private String currentFilter = "all";
    private final List<LegacyRecipe> allRecipes = new ArrayList<>();

    // saved section
    private final List<UnifiedRecipe> savedUnified = new ArrayList<>();
    private int favoritesCount = 0;
    private enum FavSort { UPDATED, MATCH }
    private FavSort favoritesSortMode = FavSort.UPDATED;

    // background threads
    private final ExecutorService io = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "recipe-io");
        t.setDaemon(true);
        return t;
    });

    // ===== Lifecycle =====
    @FXML
    public void initialize() {
        FirebaseConfiguration.initialize();
        currentUserId = UserSession.getCurrentUserId();
        if (currentUserId == null || currentUserId.isBlank()) {
            showError("User session not found");
            return;
        }

        firebase = new FirebaseService();
        ai = new AiRecipeService();

        installWindowCloseCleanup();

        // Discover (legacy)
        loadLegacyFromFirestore();

        // Saved (unified)
        loadSavedRecipes(false);

        // keep Saved fresh when tab switches
        if (tabs != null) {
            tabs.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
                if (newTab == tabSaved) {
                    loadSavedRecipes(false);
                }
            });
        }

        // AI preflight
        if (generateButton != null) generateButton.setDisable(true);
        CompletableFuture.supplyAsync(ai::isModelAvailable, io)
                .whenComplete((ok, err) ->
                        Platform.runLater(() -> { if (Boolean.TRUE.equals(ok) && generateButton != null) generateButton.setDisable(false); })
                );
    }

    private void installWindowCloseCleanup() {
        if (vBox == null) return;
        vBox.sceneProperty().addListener((obsScene, oldScene, newScene) -> {
            if (newScene == null) return;
            newScene.windowProperty().addListener((obsWin, oldWin, newWin) -> {
                if (newWin == null) return;
                newWin.setOnHidden(e -> io.shutdownNow());
            });
        });
    }

    // ===== Navigation =====
    @FXML
    private void handleBackToPantry(ActionEvent event) throws IOException {
        switchScene(event, "PantryDashboard");
    }

    // ======== DISCOVER (legacy list) ========

    private void loadLegacyFromFirestore() {
        List<String> pantryItems = getPantryItemNames();
        Firestore db = FirebaseConfiguration.getDatabase();
        CollectionReference recipesRef = db.collection("users").document(currentUserId).collection("recipes");

        CompletableFuture.supplyAsync(() -> {
            try {
                List<QueryDocumentSnapshot> docs = recipesRef.get().get().getDocuments();
                List<LegacyRecipe> out = new ArrayList<>();
                for (QueryDocumentSnapshot doc : docs) {
                    // only legacy docs (or docs that still have legacy fields)
                    String name = doc.getString("name");
                    String available = doc.getString("available");
                    String missing = doc.getString("missing");
                    String aiTip = doc.getString("aiTip");
                    if (name == null && available == null && missing == null) continue;

                    String combined = ((available != null ? available : "") + "," + (missing != null ? missing : ""))
                            .replaceAll("\\s+", " ").toLowerCase(Locale.ROOT).trim();

                    List<String> allIngredients = Arrays.stream(combined.split(","))
                            .map(String::trim).filter(s -> !s.isEmpty()).toList();
                    if (allIngredients.isEmpty()) continue;

                    List<String> actualAvailable = new ArrayList<>();
                    List<String> actualMissing = new ArrayList<>();
                    for (String ing : allIngredients) {
                        boolean match = pantryItems.stream().anyMatch(p ->
                                p.equals(ing) ||
                                        p.equals(ing.replaceAll("s$", "")) ||
                                        p.replaceAll("s$", "").equals(ing) ||
                                        p.contains(ing) || ing.contains(p)
                        );
                        if (match) actualAvailable.add(ing); else actualMissing.add(ing);
                    }
                    int matchPercent = (int)Math.round((double)actualAvailable.size()/allIngredients.size()*100);

                    LegacyRecipe r = new LegacyRecipe(
                            name,
                            matchPercent + "% match",
                            String.join(", ", actualAvailable),
                            String.join(", ", actualMissing),
                            aiTip
                    );
                    r.id = doc.getId();
                    r.favorite = Boolean.TRUE.equals(doc.getBoolean("favorite"));
                    r.aiRecommended = Boolean.TRUE.equals(doc.getBoolean("aiRecommended"));
                    out.add(r);
                }
                return out;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, io).whenComplete((list, err) -> Platform.runLater(() -> {
            if (err != null) {
                showError("Failed to load recipes: " + err.getMessage());
                return;
            }
            allRecipes.clear();
            allRecipes.addAll(list);
            renderLegacyFiltered();
        }));
    }

    private void renderLegacyFiltered() {
        List<LegacyRecipe> filtered = switch (currentFilter) {
            case "ready"     -> allRecipes.stream().filter(r -> parseMatch(r.match) == 100).toList();
            case "favorites" -> allRecipes.stream().filter(r -> r.favorite).toList();
            case "notready"  -> allRecipes.stream().filter(r -> parseMatch(r.match) < 100).toList();
            case "ai"        -> allRecipes.stream().filter(r -> r.aiRecommended).toList();
            default          -> allRecipes;
        };
        renderLegacyCards(filtered);
    }

    private int parseMatch(String match) {
        try { return Integer.parseInt(match.replace("% match","").trim()); }
        catch (Exception e) { return 0; }
    }

    private void renderLegacyCards(List<LegacyRecipe> recipesToShow) {
        if (vBox == null) return;
        vBox.getChildren().clear();

        if (recipesToShow.isEmpty()) {
            vBox.getChildren().add(new Label("No recipes in this view."));
            return;
        }

        for (int i = 0; i < recipesToShow.size(); i += 2) {
            HBox row = new HBox(20);
            row.setAlignment(Pos.TOP_LEFT);
            row.getChildren().add(createLegacyCard(recipesToShow.get(i)));
            if (i + 1 < recipesToShow.size()) row.getChildren().add(createLegacyCard(recipesToShow.get(i + 1)));
            vBox.getChildren().add(row);
        }
    }

    private VBox createLegacyCard(LegacyRecipe recipe) {
        VBox card = new VBox(12);
        card.getStyleClass().add("recipe-card");
        card.setPadding(new Insets(16));
        card.setUserData(recipe.id);

        Label name = new Label(recipe.name == null ? "(Untitled)" : recipe.name);
        name.getStyleClass().add("recipe-name");

        Label match = new Label(recipe.match == null ? "—" : recipe.match);
        match.getStyleClass().add("match-badge");

        HBox header = new HBox(12, name, match);
        header.setAlignment(Pos.CENTER_LEFT);

        HBox available = createIngredientRow("✓","available-icon","Available:",
                recipe.available == null ? "" : recipe.available);
        HBox missing   = createIngredientRow("✗","missing-icon","Missing:",
                recipe.missing == null ? "" : recipe.missing);

        Button editBtn = new Button("Edit");
        editBtn.getStyleClass().add("edit-button");
        editBtn.setOnAction(e -> handleEditRecipe(recipe));

        Button deleteBtn = new Button("🗑 Delete");
        deleteBtn.getStyleClass().add("delete-button");
        deleteBtn.setOnAction(e -> handleDeleteRecipe(recipe));

        Button favButton = new Button(recipe.favorite ? "Favorite" : "Add to Favorites");
        favButton.getStyleClass().add("favorite-button");
        favButton.setOnAction(e -> {
            recipe.favorite = !recipe.favorite;
            favButton.setText(recipe.favorite ? "Favorite" : "Add to Favorites");
            updateLegacyRecipeInFirebase(recipe);
        });

        HBox buttons = new HBox(10, editBtn, deleteBtn, favButton);
        buttons.setAlignment(Pos.CENTER_LEFT);

        String tip = recipe.aiTip == null ? "" : recipe.aiTip;
        VBox aiTip = new VBox(new Label("✨ AI Tip: " + tip));
        aiTip.getStyleClass().add("ai-tip");

        // === NEW: Click card → Online Recipes modal (RecipeAPIService) ===
        card.setOnMouseClicked(me -> {
            Node tgt = me.getPickResult() == null ? null : me.getPickResult().getIntersectedNode();
            if (tgt instanceof Button || isChildOf(tgt, buttons)) return; // ignore clicks on buttons
            openOnlineRecipesModal(name.getText(), recipe.available == null ? "" : recipe.available);
        });

        card.getChildren().addAll(header, available, missing, buttons, aiTip);
        return card;
    }

    private boolean isChildOf(Node node, Node container) {
        for (Node cur = node; cur != null; cur = cur.getParent()) if (cur == container) return true;
        return false;
    }

    private void handleDeleteRecipe(LegacyRecipe recipe) {
        CompletableFuture.runAsync(() -> {
            try {
                Firestore db = FirebaseConfiguration.getDatabase();
                db.collection("users").document(currentUserId)
                        .collection("recipes").document(recipe.id).delete().get();
            } catch (Exception e) { throw new RuntimeException(e); }
        }, io).whenComplete((v, err) -> Platform.runLater(() -> {
            if (err != null) showError("Failed to delete recipe: " + err.getMessage());
            else {
                showSuccess("Recipe deleted.");
                loadLegacyFromFirestore();
                loadSavedRecipes(false); // keep Saved in sync too
            }
        }));
    }

    private void handleEditRecipe(LegacyRecipe recipe) {
        Dialog<LegacyRecipe> dialog = new Dialog<>();
        dialog.setTitle("Edit Recipe");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField nameField = new TextField(recipe.name);
        TextField availableField = new TextField(recipe.available);
        TextField missingField = new TextField(recipe.missing);
        TextArea aiTipField = new TextArea(recipe.aiTip);
        aiTipField.setPrefRowCount(3);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(10));
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
            int newMatch = computeMatchPercentage(updated.available, updated.missing, getPantryItemNames());
            updated.match = newMatch + "% match";
            updateLegacyRecipeInFirebase(updated);
            renderLegacyFiltered();
            loadSavedRecipes(false); // Saved reflects updatedAt and favorite flags
        });
    }

    private void updateLegacyRecipeInFirebase(LegacyRecipe recipe) {
        CompletableFuture.runAsync(() -> {
            try {
                Firestore db = FirebaseConfiguration.getDatabase();
                db.collection("users").document(currentUserId).collection("recipes").document(recipe.id)
                        .update(
                                "name", recipe.name,
                                "available", recipe.available,
                                "missing", recipe.missing,
                                "aiTip", recipe.aiTip,
                                "favorite", recipe.favorite,
                                "updatedAt", FieldValue.serverTimestamp()
                        ).get();
            } catch (Exception e) { throw new RuntimeException(e); }
        }, io).whenComplete((v, err) ->
                Platform.runLater(() -> {
                    if (err != null) showError("Failed to update recipe: " + err.getMessage());
                    else showSuccess("Recipe updated.");
                })
        );
    }

    @FXML private void handleSearch() {
        String q = (searchField == null || searchField.getText() == null) ? "" : searchField.getText().toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) { renderLegacyFiltered(); return; }
        List<LegacyRecipe> filtered = allRecipes.stream()
                .filter(r -> (r.name != null && r.name.toLowerCase(Locale.ROOT).contains(q))
                        || (r.available != null && r.available.toLowerCase(Locale.ROOT).contains(q))
                        || (r.missing != null && r.missing.toLowerCase(Locale.ROOT).contains(q))
                        || (r.aiTip != null && r.aiTip.toLowerCase(Locale.ROOT).contains(q)))
                .toList();
        renderLegacyCards(filtered);
    }

    @FXML private void handleFilterAll()           { currentFilter="all";       renderLegacyFiltered(); }
    @FXML private void handleFilterReady()         { currentFilter="ready";     renderLegacyFiltered(); }
    @FXML private void handleFilterFavorites()     { currentFilter="favorites"; renderLegacyFiltered(); }
    @FXML private void handleFilterNotReady()      { currentFilter="notready";  renderLegacyFiltered(); }
    @FXML private void handleFilterAIRecommended() { currentFilter="ai";        renderLegacyFiltered(); }

    @FXML private void handleAddRecipe() {
        // try both possible locations
        boolean opened = tryOpenPopup("/XMLFiles/AddRecipe.fxml", "Add Recipe");
        if (!opened) opened = tryOpenPopup("/com/example/demo1/AddRecipe.fxml", "Add Recipe");
        if (!opened) { showError("Could not find Add Recipe view."); return; }

        // refresh both tabs after modal closes
        loadLegacyFromFirestore();
        loadSavedRecipes(false);
    }

    private boolean tryOpenPopup(String resource, String title) {
        try {
            Parent root = FXMLLoader.load(Objects.requireNonNull(getClass().getResource(resource)));
            Stage stage = new Stage();
            stage.initModality(Modality.WINDOW_MODAL);
            if (vBox != null && vBox.getScene() != null) stage.initOwner(vBox.getScene().getWindow());
            stage.setTitle(title);
            stage.setScene(new Scene(root));
            stage.showAndWait();
            return true;
        } catch (Exception ignore) {
            return false;
        }
    }

    private int computeMatchPercentage(String available, String missing, List<String> pantryItems) {
        String combined = ((available != null ? available : "") + "," + (missing != null ? missing : ""))
                .replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        if (combined.isBlank()) return 0;
        List<String> allIngredients = Arrays.stream(combined.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (allIngredients.isEmpty()) return 0;
        long matches = allIngredients.stream().filter(ingredient ->
                pantryItems.stream().anyMatch(pantry ->
                        pantry.equals(ingredient) ||
                                pantry.equals(ingredient.replaceAll("s$","")) ||
                                pantry.replaceAll("s$","").equals(ingredient) ||
                                ingredient.contains(pantry) || pantry.contains(ingredient)
                )
        ).count();
        return (int)Math.round((double)matches / allIngredients.size() * 100);
    }

    private List<String> getPantryItemNames() {
        List<String> pantryNames = new ArrayList<>();
        try {
            Firestore db = FirebaseConfiguration.getDatabase();
            CollectionReference pantryRef = db.collection("users").document(currentUserId).collection("pantryItems");
            for (QueryDocumentSnapshot doc : pantryRef.get().get().getDocuments()) {
                String name = doc.getString("name");
                if (name != null && !name.isBlank()) pantryNames.add(name.trim().toLowerCase(Locale.ROOT));
            }
        } catch (Exception e) {
            System.err.println("Error loading pantry items: " + e.getMessage());
        }
        return pantryNames;
    }

    private HBox createIngredientRow(String icon, String iconStyle, String label, String items) {
        Label iconLabel = new Label(icon); iconLabel.getStyleClass().add(iconStyle);
        Label labelText = new Label(label); labelText.getStyleClass().add("ingredient-label");
        Label itemsText = new Label(items == null ? "" : items); itemsText.getStyleClass().add("ingredient-text");
        return new HBox(8, iconLabel, labelText, itemsText);
    }

    // ======== SAVED (unified list) ========

    private void loadSavedRecipes(boolean favoritesOnly) {
        if (savedVBox == null) return;
        savedVBox.getChildren().setAll(new Label("Loading saved recipes…"));

        CompletableFuture.supplyAsync(() -> {
            try {
                Query base = userRecipesRef();
                if (favoritesOnly) base = base.whereEqualTo("favorite", true);

                // we can sort in-memory for simplicity
                List<QueryDocumentSnapshot> docs = new ArrayList<>(base.get().get().getDocuments());

                if (favoritesOnly && favoritesSortMode == FavSort.MATCH) {
                    docs.sort((a, b) -> {
                        int ib = safeMatchPercent(Optional.ofNullable(b.getString("match")).orElse("0"));
                        int ia = safeMatchPercent(Optional.ofNullable(a.getString("match")).orElse("0"));
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
                for (QueryDocumentSnapshot d : docs) list.add(fromDoc(d));
                return list;

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, io).whenComplete((list, err) -> Platform.runLater(() -> {
            if (err != null) {
                savedVBox.getChildren().setAll(new Label("Failed to load saved recipes:\n" + err.getMessage()));
            } else {
                savedUnified.clear();
                savedUnified.addAll(list);
                renderSaved(savedUnified);
                updateFavoritesCount(); // updates button text "(N)"
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
            CompletableFuture.runAsync(() -> toggleFavorite(r, newVal), io)
                    .whenComplete((v, err) -> Platform.runLater(() -> {
                        star.setDisable(false);
                        if (err != null) showError("Failed to update favorite: " + err.getMessage());
                        else {
                            r.favorite = newVal;
                            star.setText(r.favorite ? "★" : "☆");
                            updateFavoritesCount();
                        }
                    }));
        });

        Button del = new Button("🗑 Delete");
        del.getStyleClass().add("danger");
        del.setOnAction(e -> {
            if (!confirmDelete(r.title)) return;
            del.setDisable(true);
            CompletableFuture.runAsync(() -> deleteSaved(r), io)
                    .whenComplete((v, err) -> Platform.runLater(() -> {
                        del.setDisable(false);
                        if (err != null) showError("Failed to delete: " + err.getMessage());
                        else {
                            showSuccess("Deleted.");
                            loadSavedRecipes(false);
                            loadLegacyFromFirestore(); // keep legacy in sync if it was a legacy-style doc
                        }
                    }));
        });

        HBox header = new HBox(12, name, star, del);
        header.setAlignment(Pos.CENTER_LEFT);

        String ingredients = r.ingredients.isEmpty() ? "—" : String.join(", ", r.ingredients);
        String missing     = r.missingIngredients.isEmpty() ? "None" : String.join(", ", r.missingIngredients);
        HBox row1 = createIngredientRow("✓","available-icon","Ingredients:", ingredients);
        HBox row2 = createIngredientRow("✗","missing-icon","Missing:",     missing);

        String meta = ((r.estimatedTime != null && !r.estimatedTime.isBlank()) ? "⏱ " + r.estimatedTime + "  " : "")
                + ((r.calories != null) ? "🔥 " + r.calories + " kcal" : "");
        VBox metaBox = new VBox(new Label(meta.isEmpty() ? "" : meta));
        metaBox.getStyleClass().add("ai-tip");

        // === NEW: click saved card → Online modal too ===
        card.setOnMouseClicked(me -> {
            Node tgt = me.getPickResult() == null ? null : me.getPickResult().getIntersectedNode();
            if (tgt instanceof Button || isChildOf(tgt, header)) return; // ignore star/delete clicks
            openOnlineRecipesModal(r.title, String.join(", ", r.ingredients));
        });

        card.getChildren().addAll(header, row1, row2, metaBox);
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
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private int safeMatchPercent(String matchText) {
        if (matchText == null) return 0;
        String digits = matchText.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return 0;
        try { return Integer.parseInt(digits); } catch (NumberFormatException e) { return 0; }
    }

    private UnifiedRecipe fromDoc(DocumentSnapshot d) {
        UnifiedRecipe r = new UnifiedRecipe();
        r.title = Optional.ofNullable(d.getString("title"))
                .orElse(Optional.ofNullable(d.getString("name")).orElse("Untitled Recipe"));
        r.ingredients = castList(d.get("ingredients"));
        if (r.ingredients.isEmpty()) {
            String available = d.getString("available");
            if (available != null) r.ingredients = splitCSV(available);
        }
        r.missingIngredients = castList(d.get("missingIngredients"));
        if (r.missingIngredients.isEmpty()) {
            String missing = d.getString("missing");
            if (missing != null) r.missingIngredients = splitCSV(missing).stream()
                    .filter(x -> !"None".equalsIgnoreCase(x)).toList();
        }
        r.steps = castList(d.get("steps"));
        r.estimatedTime = Optional.ofNullable((String) d.get("estimatedTime")).orElse("");
        Object c = d.get("calories");
        r.calories = (c instanceof Number) ? ((Number)c).intValue() : null;
        r.match = Optional.ofNullable(d.getString("match")).orElse("—");
        r.favorite = Boolean.TRUE.equals(d.getBoolean("favorite"));
        r.source = Source.STATIC;
        return r;
    }

    @SuppressWarnings("unchecked")
    private List<String> castList(Object v) {
        if (v instanceof List<?> list) {
            return list.stream().filter(Objects::nonNull).map(Object::toString).toList();
        }
        return List.of();
    }

    private List<String> splitCSV(String s) {
        if (s == null || s.isBlank()) return List.of();
        return Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isBlank()).toList();
    }

    private void toggleFavorite(UnifiedRecipe r, boolean newVal) {
        Map<String, Object> data = mapForFirestore(r);
        data.put("favorite", newVal);
        data.put("updatedAt", FieldValue.serverTimestamp());
        try {
            userRecipesRef().document(slug(r.title)).set(data, SetOptions.merge()).get();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private Map<String, Object> mapForFirestore(UnifiedRecipe r) {
        Map<String, Object> m = new HashMap<>();
        m.put("title", r.title);
        m.put("ingredients", r.ingredients);
        m.put("steps", r.steps);
        m.put("missingIngredients", r.missingIngredients);
        m.put("estimatedTime", r.estimatedTime);
        m.put("calories", r.calories);
        m.put("source", r.source.name().toLowerCase(Locale.ROOT));
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

    private void updateFavoritesCount() {
        if (favoriteBtn == null) return;
        CompletableFuture.supplyAsync(() -> {
            try { return userRecipesRef().whereEqualTo("favorite", true).get().get().size(); }
            catch (Exception e) { return -1; }
        }, io).whenComplete((n, err) -> Platform.runLater(() -> {
            if (err != null || n < 0) return;
            favoritesCount = n;
            favoriteBtn.setText("⭐ Favorites" + (favoritesCount > 0 ? " (" + favoritesCount + ")" : ""));
            favoriteBtn.setDisable(favoritesCount == 0);
        }));
    }

    // ====== Generate (AI)
    @FXML
    private void handleGenerateRecipe() {
        if (!ai.isModelAvailable()) { showError("Local AI not ready. Run: 1) ollama pull phi3:mini  2) ollama serve"); return; }
        String prompt = (aiInputField == null || aiInputField.getText() == null) ? "" : aiInputField.getText().trim();
        if (generateButton != null) generateButton.setDisable(true);

        CompletableFuture.supplyAsync(() -> {
            try {
                var items = firebase.getPantryItems(currentUserId);
                var filtered = filterPantryItems(items);
                return ai.generateRecipes(filtered, prompt, 3);
            } catch (Exception e) { throw new RuntimeException(e); }
        }, io).whenComplete((recipes, err) -> Platform.runLater(() -> {
            if (generateButton != null) generateButton.setDisable(false);
            if (aiInputField != null) aiInputField.clear();

            if (err != null) {
                String msg = (err.getCause() != null) ? err.getCause().getMessage() : err.getMessage();
                showError("Failed to generate recipes: " + msg);
                return;
            }
            if (recipes == null || recipes.isEmpty()) {
                showSuccess("No recipes returned. Try again with a different prompt.");
                return;
            }

            for (RecipeDTO d : recipes) {
                UnifiedRecipe r = fromAI(d);
                boolean saveIt = confirmSave(r.title);
                if (saveIt) saveUnified(r);
            }
            loadSavedRecipes(false);
            tabs.getSelectionModel().select(tabSaved);
            showSuccess("Done.");
        }));
    }

    private boolean confirmSave(String title) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle("Save Recipe");
        a.setHeaderText("Save generated recipe?");
        a.setContentText(title);
        a.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        Optional<ButtonType> res = a.showAndWait();
        return res.isPresent() && res.get() == ButtonType.OK;
    }

    private void saveUnified(UnifiedRecipe r) {
        Map<String, Object> data = mapForFirestore(r);
        data.put("favorite", r.favorite);
        try {
            userRecipesRef().document(slug(r.title)).set(data, SetOptions.merge()).get();
        } catch (Exception e) { /* ignore in loop; surfaced later */ }
    }

    @FXML private void handleGenerateAgain() { handleGenerateRecipe(); }
    @FXML private void handleSeeMore() { showSuccess("See more not implemented yet."); }

    private List<PantryItem> filterPantryItems(List<PantryItem> items) {
        LocalDate today = LocalDate.now();
        return items.stream()
                .filter(Objects::nonNull)
                .filter(p -> p.getQuantityNumeric() > 0)
                .filter(p -> p.getExpires() == null || !p.getExpires().isBefore(today))
                .toList();
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

    private List<String> safeList(List<String> in) {
        return in == null ? List.of() :
                in.stream().filter(Objects::nonNull).map(String::trim).filter(x -> !x.isBlank()).toList();
    }

    private String estimateMatch(List<String> have, List<String> miss) {
        int total = Math.max(1, have.size());
        int used  = Math.max(0, total - (miss == null ? 0 : miss.size()));
        int pct   = Math.max(10, (int)Math.round(100.0 * used / total));
        return pct + "% match";
    }

    private void showError(String msg){ Alert a=new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK); a.setHeaderText(null); a.showAndWait(); }
    private void showSuccess(String msg){ Alert a=new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK); a.setHeaderText(null); a.showAndWait(); }

    // ===== Models =====
    private static class LegacyRecipe {
        String id, name, match, available, missing, aiTip;
        boolean favorite=false, aiRecommended=false;
        LegacyRecipe(String n,String m,String a,String miss,String tip){ name=n; match=m; available=a; missing=miss; aiTip=tip; }
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

    // ======== Online Recipes modal (RecipeAPIService) ========
    private void openOnlineRecipesModal(String nameQuery, String availableCsv) {
        String q = nameQuery == null ? "" : nameQuery.trim();

        // Left: list of results
        ListView<OnlineItem> listView = new ListView<>();
        listView.setPrefWidth(360);

        // Right: details pane
        VBox detailPane = new VBox(10);
        detailPane.setPadding(new Insets(12));
        detailPane.getChildren().setAll(new Label("Searching online: " + (q.isBlank() ? "(blank)" : q)));

        SplitPane split = new SplitPane(listView, new ScrollPane(detailPane));
        split.setDividerPositions(0.35);

        Stage stage = new Stage();
        stage.setTitle("Online Recipes — " + (q.isBlank() ? "Search" : q));
        stage.initModality(Modality.WINDOW_MODAL);
        if (vBox != null && vBox.getScene() != null && vBox.getScene().getWindow() != null)
            stage.initOwner(vBox.getScene().getWindow());
        stage.setScene(new Scene(split, 1000, 700));
        stage.show();

        // search (async)
        CompletableFuture
                .supplyAsync(() -> RecipeAPIService.smartSearch(q, availableCsv == null ? "" : availableCsv, 10), io)
                .thenAccept(results -> Platform.runLater(() -> {
                    List<OnlineItem> items = new ArrayList<>();
                    for (Map<String,String> m : results) {
                        items.add(new OnlineItem(
                                m.getOrDefault("id",""),
                                m.getOrDefault("title","(Untitled)"),
                                m.getOrDefault("image","")
                        ));
                    }
                    listView.getItems().setAll(items);
                    listView.setCellFactory(_ -> new OnlineCell());
                    listView.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
                        if (sel != null) populateDetail(detailPane, sel);
                    });
                    if (!items.isEmpty()) listView.getSelectionModel().selectFirst();
                    else detailPane.getChildren().setAll(new Label("No results from providers."));
                }))
                .exceptionally(ex -> { Platform.runLater(() -> showError("Online recipe search failed: " + ex.getMessage())); return null; });
    }

    private void populateDetail(VBox detailPane, OnlineItem item) {
        detailPane.getChildren().clear();

        Label title = new Label(item.title);
        title.getStyleClass().add("recipe-name");

        ImageView image = new ImageView();
        if (item.imageUrl != null && !item.imageUrl.isBlank()) image.setImage(new Image(item.imageUrl, true));
        image.setPreserveRatio(true);
        image.setFitWidth(520);

        // fetch details (async)
        CompletableFuture
                .supplyAsync(() -> RecipeAPIService.getRecipeDetailsUnified(item.id), io)
                .thenAccept(details -> Platform.runLater(() -> {
                    String instructions = details.getOrDefault("instructions", "No instructions available.");
                    String sourceUrl = details.getOrDefault("sourceUrl", "");

                    TextArea instructionsArea = new TextArea(stripHtml(instructions).trim());
                    instructionsArea.setEditable(false);
                    instructionsArea.setWrapText(true);
                    instructionsArea.setPrefRowCount(18);

                    Button openSource = new Button("Open Source");
                    openSource.setDisable(sourceUrl.isBlank());
                    openSource.setOnAction(e -> openExternalUrl(sourceUrl));

                    detailPane.getChildren().setAll(
                            title,
                            image,
                            new Label("Instructions:"),
                            instructionsArea,
                            openSource,
                            new Label("Source URL:"),
                            new Label(sourceUrl)
                    );
                }))
                .exceptionally(ex -> { Platform.runLater(() -> showError("Failed to load details: " + ex.getMessage())); return null; });
    }

    private String stripHtml(String s) { return s == null ? "" : s.replaceAll("<[^>]*>", " "); }

    private void openExternalUrl(String url) {
        if (url == null || url.isBlank()) return;
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop d = Desktop.getDesktop();
                if (d.isSupported(Desktop.Action.BROWSE)) { d.browse(URI.create(url)); return; }
            }
            String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
            if (os.contains("win")) new ProcessBuilder("rundll32","url.dll,FileProtocolHandler",url).start();
            else if (os.contains("mac")) new ProcessBuilder("open", url).start();
            else new ProcessBuilder("xdg-open", url).start();
        } catch (Exception e) {
            Platform.runLater(() -> showError("Could not open link: " + e.getMessage()));
        }
    }

    private static final class OnlineItem {
        final String id; final String title; final String imageUrl;
        OnlineItem(String id, String title, String imageUrl){ this.id=id; this.title=title; this.imageUrl=imageUrl; }
        @Override public String toString(){ return title; }
    }
    private static final class OnlineCell extends ListCell<OnlineItem> {
        private final HBox root = new HBox(10);
        private final ImageView img = new ImageView();
        private final Label title = new Label();
        OnlineCell(){
            img.setFitWidth(64); img.setFitHeight(64); img.setPreserveRatio(true);
            root.setAlignment(Pos.CENTER_LEFT);
            root.getChildren().addAll(img, title);
        }
        @Override protected void updateItem(OnlineItem item, boolean empty){
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
            } else {
                title.setText(item.title == null ? "(Untitled)" : item.title);
                if (item.imageUrl != null && !item.imageUrl.isBlank()) img.setImage(new Image(item.imageUrl, true));
                else img.setImage(null);
                setGraphic(root);
            }
        }
    }
}




























































