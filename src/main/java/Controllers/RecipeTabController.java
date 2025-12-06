package Controllers;

import Firebase.FirebaseConfiguration;
import com.example.demo1.UserSession;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;

import Recipe.RecipeAPIService;

public class RecipeTabController extends BaseController {

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

    private String currentFilter = "all";
    private final List<Recipe> allRecipes = new ArrayList<>();
    private String currentUserId;

    private final ExecutorService io = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "recipe-io");
        t.setDaemon(true);
        return t;
    });

    @FXML public void initialize() {
        FirebaseConfiguration.initialize();
        currentUserId = UserSession.getCurrentUserId();
        if (currentUserId == null || currentUserId.isBlank()) { showError("User session not found"); return; }
        installWindowCloseCleanup();
        List<String> pantryItems = getPantryItemNames();
        loadRecipesFromFirebase(pantryItems);
    }

    private void installWindowCloseCleanup() {
        vBox.sceneProperty().addListener((obsScene, oldScene, newScene) -> {
            if (newScene == null) return;
            newScene.windowProperty().addListener((obsWin, oldWin, newWin) -> {
                if (newWin == null) return;
                newWin.setOnHidden(e -> io.shutdownNow());
            });
        });
    }

    private void loadRecipesFromFirebase(List<String> pantryItems) {
        Firestore db = FirebaseConfiguration.getDatabase();
        CollectionReference recipesRef = db.collection("users").document(currentUserId).collection("recipes");
        try {
            List<QueryDocumentSnapshot> docs = recipesRef.get().get().getDocuments();
            allRecipes.clear();
            for (QueryDocumentSnapshot doc : docs) {
                String name = doc.getString("name");
                String available = doc.getString("available");
                String missing = doc.getString("missing");
                String aiTip = doc.getString("aiTip");

                String combined = ((available != null ? available : "") + "," + (missing != null ? missing : ""))
                        .replaceAll("\\s+", " ").toLowerCase().trim();

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

                Recipe r = new Recipe(name, matchPercent + "% match",
                        String.join(", ", actualAvailable),
                        String.join(", ", actualMissing),
                        aiTip);
                r.id = doc.getId();
                allRecipes.add(r);
            }
            loadRecipes();
        } catch (Exception e) {
            showError("Failed to load recipes: " + e.getMessage());
        }
    }

    @FXML public void handleBackToPantry(ActionEvent event) throws IOException { switchScene(event, "PantryDashboard"); }
    @FXML private void handleFilterAll() { currentFilter="all"; updateFilterButtons(); displayFilteredRecipes(); }
    @FXML private void handleFilterReady() { currentFilter="ready"; updateFilterButtons(); displayFilteredRecipes(); }
    @FXML private void handleFilterFavorites() { currentFilter="favorites"; updateFilterButtons(); displayFilteredRecipes(); }
    @FXML private void handleFilterNotReady() { currentFilter="notready"; updateFilterButtons(); displayFilteredRecipes(); }
    @FXML private void handleFilterAIRecommended() { currentFilter="ai"; updateFilterButtons(); displayFilteredRecipes(); }

    private void updateFilterButtons() {
        List<Button> filters = List.of(allRecipesBtn, readyBtn, favoriteBtn, notReadyBtn, aiRecommendedBtn);
        filters.forEach(btn -> btn.getStyleClass().remove("filter-selected"));
        switch (currentFilter) {
            case "ready" -> readyBtn.getStyleClass().add("filter-selected");
            case "favorites" -> favoriteBtn.getStyleClass().add("filter-selected");
            case "notready" -> notReadyBtn.getStyleClass().add("filter-selected");
            case "ai" -> aiRecommendedBtn.getStyleClass().add("filter-selected");
            default -> allRecipesBtn.getStyleClass().add("filter-selected");
        }
    }

    private void displayFilteredRecipes() {
        List<Recipe> filtered = switch (currentFilter) {
            case "ready" -> allRecipes.stream().filter(r -> parseMatch(r.match) == 100).toList();
            case "favorites" -> allRecipes.stream().filter(r -> r.favorite).toList();
            case "notready" -> allRecipes.stream().filter(r -> parseMatch(r.match) < 100).toList();
            case "ai" -> allRecipes.stream().filter(r -> r.aiRecommended).toList();
            default -> allRecipes;
        };
        displayRecipes(filtered);
    }

    private int parseMatch(String match) {
        try { return Integer.parseInt(match.replace("% match","").trim()); }
        catch (Exception e) { return 0; }
    }

    @FXML private void handleGenerateRecipe() {
        String prompt = aiInputField.getText().trim();
        if (prompt.isEmpty()) { showError("Please enter a recipe idea first!"); return; }
        showSuccess("Generating recipes for: " + prompt);
        aiInputField.clear();
    }
    @FXML private void handleGenerateAgain() { List<String> pantryItems = getPantryItemNames(); loadRecipesFromFirebase(pantryItems); }
    @FXML private void handleSeeMore() { showSuccess("Feature coming soon!"); }

    @FXML private void handleAddRecipe(ActionEvent event) {
        openPopup("/XMLFiles/AddRecipe.fxml", "Add Recipe");
        List<String> pantryItems = getPantryItemNames();
        loadRecipesFromFirebase(pantryItems);
    }

    private void loadRecipes() {
        vBox.getChildren().clear();
        for (int i = 0; i < allRecipes.size(); i += 2) {
            HBox row = new HBox(20);
            row.setAlignment(Pos.TOP_LEFT);
            VBox card1 = createRecipeCard(allRecipes.get(i));
            row.getChildren().add(card1);
            if (i + 1 < allRecipes.size()) row.getChildren().add(createRecipeCard(allRecipes.get(i+1)));
            vBox.getChildren().add(row);
        }
    }

    private void displayRecipes(List<Recipe> recipesToShow) {
        vBox.getChildren().clear();
        for (int i = 0; i < recipesToShow.size(); i += 2) {
            HBox row = new HBox(20);
            row.setAlignment(Pos.TOP_LEFT);
            VBox card1 = createRecipeCard(recipesToShow.get(i));
            row.getChildren().add(card1);
            if (i + 1 < recipesToShow.size()) row.getChildren().add(createRecipeCard(recipesToShow.get(i+1)));
            vBox.getChildren().add(row);
        }
    }

    private VBox createRecipeCard(Recipe recipe) {
        VBox card = new VBox(12);
        card.getStyleClass().add("recipe-card");
        card.setPadding(new Insets(16));
        card.setUserData(recipe.id);

        Label name = new Label(recipe.name); name.getStyleClass().add("recipe-name");
        Label match = new Label(recipe.match); match.getStyleClass().add("match-badge");
        HBox header = new HBox(12, name, match); header.setAlignment(Pos.CENTER_LEFT);

        HBox available = createIngredientRow("✓","available-icon","Available:",recipe.available);
        HBox missing   = createIngredientRow("✗","missing-icon","Missing:",recipe.missing);

        Button editBtn = new Button("Edit"); editBtn.getStyleClass().add("edit-button");
        editBtn.setOnAction(e -> handleEditRecipe(recipe));

        Button deleteBtn = new Button("🗑 Delete Recipe"); deleteBtn.getStyleClass().add("delete-button");
        deleteBtn.setOnAction(e -> handleDeleteRecipe(recipe));

        Button favButton = new Button(recipe.favorite ? "Favorite" : "Add to Favorites");
        favButton.getStyleClass().add("favorite-button");
        favButton.setOnAction(e -> { recipe.favorite = !recipe.favorite; favButton.setText(recipe.favorite ? "Favorite" : "Add to Favorites"); updateRecipeInFirebase(recipe); });

        HBox buttons = new HBox(10, editBtn, deleteBtn, favButton); buttons.setAlignment(Pos.CENTER_LEFT);

        VBox aiTip = new VBox(new Label("✨ AI Tip: " + recipe.aiTip)); aiTip.getStyleClass().add("ai-tip");

        card.setOnMouseClicked(me -> {
            Node target = me.getPickResult() != null ? me.getPickResult().getIntersectedNode() : null;
            if (target != null && (isInside(target, buttons) || target instanceof Button)) return;
            openOnlineRecipesModal(recipe);
        });

        card.getChildren().addAll(header, available, missing, buttons, aiTip);
        return card;
    }

    private HBox createIngredientRow(String icon, String iconStyle, String label, String items) {
        Label iconLabel = new Label(icon); iconLabel.getStyleClass().add(iconStyle);
        Label labelText = new Label(label); labelText.getStyleClass().add("ingredient-label");
        Label itemsText = new Label(items == null ? "" : items); itemsText.getStyleClass().add("ingredient-text");
        return new HBox(8, iconLabel, labelText, itemsText);
    }

    private boolean isInside(Node node, Node container) {
        for (Node cur = node; cur != null; cur = cur.getParent()) if (cur == container) return true;
        return false;
    }

    private void handleDeleteRecipe(Recipe recipe) {
        try {
            Firestore db = FirebaseConfiguration.getDatabase();
            db.collection("users").document(currentUserId).collection("recipes").document(recipe.id).delete().get();
            List<String> pantryItems = getPantryItemNames();
            loadRecipesFromFirebase(pantryItems);
            showSuccess("Recipe deleted successfully!");
        } catch (Exception e) { showError("Failed to delete recipe: " + e.getMessage()); }
    }

    private void handleEditRecipe(Recipe recipe) {
        Dialog<Recipe> dialog = new Dialog<>();
        dialog.setTitle("Edit Recipe");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField nameField = new TextField(recipe.name);
        TextField availableField = new TextField(recipe.available);
        TextField missingField = new TextField(recipe.missing);
        TextArea aiTipField = new TextArea(recipe.aiTip); aiTipField.setPrefRowCount(3);

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
            List<String> pantryItems = getPantryItemNames();
            int newMatch = computeMatchPercentage(updated.available, updated.missing, pantryItems);
            updated.match = newMatch + "% match";
            updateRecipeInFirebase(updated);
            refreshRecipeCard(updated);
        });
    }

    private void refreshRecipeCard(Recipe updated) {
        for (int i = 0; i < vBox.getChildren().size(); i++) {
            if (vBox.getChildren().get(i) instanceof HBox row) {
                for (int j = 0; j < row.getChildren().size(); j++) {
                    if (row.getChildren().get(j) instanceof VBox card) {
                        Label nameLabel = (Label)((HBox)card.getChildren().get(0)).getChildren().get(0);
                        if (nameLabel.getText().equals(updated.name)) { row.getChildren().set(j, createRecipeCard(updated)); return; }
                    }
                }
            }
        }
    }

    private void updateRecipeInFirebase(Recipe recipe) {
        new Thread(() -> {
            try {
                Firestore db = FirebaseConfiguration.getDatabase();
                db.collection("users").document(currentUserId).collection("recipes").document(recipe.id)
                        .update(
                                "name", recipe.name,
                                "available", recipe.available,
                                "missing", recipe.missing,
                                "aiTip", recipe.aiTip,
                                "favorite", recipe.favorite,
                                "aiRecommended", recipe.aiRecommended
                        ).get();
                Platform.runLater(() -> showSuccess("Recipe updated successfully!"));
            } catch (Exception e) {
                Platform.runLater(() -> showError("Failed to update recipe: " + e.getMessage()));
            }
        }).start();
    }

    private List<String> getPantryItemNames() {
        List<String> pantryNames = new ArrayList<>();
        try {
            Firestore db = FirebaseConfiguration.getDatabase();
            CollectionReference pantryRef = db.collection("users").document(currentUserId).collection("pantryItems");
            for (var doc : pantryRef.get().get().getDocuments()) {
                String name = doc.getString("name");
                if (name != null && !name.isBlank()) pantryNames.add(name.trim().toLowerCase());
            }
        } catch (Exception e) { System.err.println("Error loading pantry items: " + e.getMessage()); }
        return pantryNames;
    }

    @FXML private void handleSearch() {
        String query = searchField.getText().toLowerCase().trim();
        if (query.isEmpty()) { loadRecipes(); return; }
        List<Recipe> filtered = allRecipes.stream().filter(r ->
                r.name.toLowerCase().contains(query)
                        || r.available.toLowerCase().contains(query)
                        || r.missing.toLowerCase().contains(query)
                        || r.aiTip.toLowerCase().contains(query)).toList();
        displayRecipes(filtered);
    }

    private int computeMatchPercentage(String available, String missing, List<String> pantryItems) {
        String combined = ((available != null ? available : "") + "," + (missing != null ? missing : ""))
                .replaceAll("\\s+", " ").trim().toLowerCase();
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

    private void showError(String msg) { Alert a = new Alert(Alert.AlertType.ERROR); a.setHeaderText(null); a.setContentText(msg); a.showAndWait(); }
    private void showSuccess(String msg) { Alert a = new Alert(Alert.AlertType.INFORMATION); a.setHeaderText(null); a.setContentText(msg); a.showAndWait(); }

    // ===== Online Recipes modal (now uses smartSearch + unified details) =====
    private void openOnlineRecipesModal(Recipe r) {
        String nameQuery = r.name == null ? "" : r.name.trim();

        ListView<OnlineRecipeItem> listView = new ListView<>();
        listView.setPrefWidth(380);

        VBox detailPane = new VBox(10);
        detailPane.setPadding(new Insets(12));
        Label status = new Label("Searching online: " + nameQuery);
        detailPane.getChildren().setAll(status);

        SplitPane split = new SplitPane(listView, new ScrollPane(detailPane));
        split.setDividerPositions(0.35);

        Stage stage = new Stage();
        stage.setTitle("Online Recipes — " + r.name);
        stage.initModality(Modality.WINDOW_MODAL);
        if (vBox.getScene() != null && vBox.getScene().getWindow() != null) stage.initOwner(vBox.getScene().getWindow());
        stage.setScene(new Scene(split, 1000, 700));
        stage.show();

        CompletableFuture
                .supplyAsync(() -> RecipeAPIService.smartSearch(r.name, r.available, 10), io)
                .thenAccept(results -> Platform.runLater(() -> {
                    List<OnlineRecipeItem> items = new ArrayList<>();
                    for (Map<String,String> m : results) {
                        items.add(new OnlineRecipeItem(
                                m.getOrDefault("id",""),
                                m.getOrDefault("title","(Untitled)"),
                                m.getOrDefault("image","")
                        ));
                    }
                    listView.getItems().setAll(items);
                    listView.setCellFactory(_ -> new OnlineRecipeCell());
                    listView.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
                        if (sel != null) populateDetail(detailPane, sel);
                    });
                    if (!items.isEmpty()) {
                        listView.getSelectionModel().selectFirst();
                    } else {
                        detailPane.getChildren().setAll(new Label(
                                "No online results.\n"
                                        + "Spoonacular may be out of quota (HTTP 402) or key missing.\n"
                                        + "Fallback provider: TheMealDB (public) also returned no results."
                        ));
                    }
                }))
                .exceptionally(ex -> { Platform.runLater(() -> showError("Online recipe search failed: " + ex.getMessage())); return null; });
    }

    private void populateDetail(VBox detailPane, OnlineRecipeItem item) {
        detailPane.getChildren().clear();

        Label title = new Label(item.title); title.getStyleClass().add("recipe-name");

        ImageView image = new ImageView();
        if (item.imageUrl != null && !item.imageUrl.isBlank()) image.setImage(new Image(item.imageUrl, true));
        image.setPreserveRatio(true); image.setFitWidth(520);

        CompletableFuture
                .supplyAsync(() -> RecipeAPIService.getRecipeDetailsUnified(item.id), io)
                .thenAccept(details -> Platform.runLater(() -> {
                    String instructions = details.getOrDefault("instructions", "No instructions available.");
                    String sourceUrl = details.getOrDefault("sourceUrl", "");

                    TextArea instructionsArea = new TextArea(stripHtml(instructions).trim());
                    instructionsArea.setEditable(false); instructionsArea.setWrapText(true); instructionsArea.setPrefRowCount(18);

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
                .exceptionally(ex -> { Platform.runLater(() -> showError("Failed to load recipe details: " + ex.getMessage())); return null; });
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
        } catch (Exception e) { Platform.runLater(() -> showError("Could not open link: " + e.getMessage())); }
    }

    // ===== Models =====
    private static class Recipe {
        String id, name, match, available, missing, aiTip;
        boolean favorite = false;
        boolean aiRecommended = false;
        Recipe(String n, String m, String a, String miss, String tip) { name=n; match=m; available=a; missing=miss; aiTip=tip; }
    }
    private static final class OnlineRecipeItem {
        final String id; final String title; final String imageUrl;
        OnlineRecipeItem(String id, String title, String imageUrl){ this.id=id; this.title=title; this.imageUrl=imageUrl; }
        @Override public String toString(){ return title; }
    }
    private static final class OnlineRecipeCell extends ListCell<OnlineRecipeItem> {
        private final HBox root = new HBox(10);
        private final ImageView img = new ImageView();
        private final Label title = new Label();
        OnlineRecipeCell(){ img.setFitWidth(64); img.setFitHeight(64); img.setPreserveRatio(true); root.setAlignment(Pos.CENTER_LEFT); root.getChildren().addAll(img,title); }
        @Override protected void updateItem(OnlineRecipeItem item, boolean empty){
            super.updateItem(item, empty);
            if (empty || item == null) setGraphic(null);
            else {
                title.setText(item.title == null ? "(Untitled)" : item.title);
                if (item.imageUrl != null && !item.imageUrl.isBlank()) img.setImage(new Image(item.imageUrl, true));
                else img.setImage(null);
                setGraphic(root);
            }
        }
    }
}
