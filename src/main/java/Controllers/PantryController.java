package Controllers;

import javafx.fxml.Initializable;
import Pantry.PantryItem;
import Firebase.FirebaseConfiguration;
import Firebase.FirebaseService;
import javafx.collections.ObservableList;
import com.example.demo1.UserSession;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutionException;

import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.event.ActionEvent;
import Pantry.ItemStatus;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import com.google.cloud.firestore.Firestore;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.QuerySnapshot;

public class PantryController extends BaseController implements Initializable {

    private FirebaseService firebaseService;
    private ObservableList<PantryItem> allItems;
    private ToggleGroup filterGroup;

    // ===== Header UI =====
    @FXML private Label dashboardTitle;   // shows "Pantry Dashboard — <name>"
    @FXML private Label userNameLabel;    // optional "Hi, <name>!"
    @FXML private TextField searchField;
    @FXML private Button addItemBtn;
    @FXML private Button recipesBtn;
    @FXML private Button shoppingBtn;
    @FXML private Button styleBtn;
    @FXML private Button logoutBtn;
    @FXML private Button accountBtn;      // <-- Account button (must exist in FXML)

    // ===== Filters & grid =====
    @FXML private ToggleButton segAll;
    @FXML private ToggleButton segExpiring;
    @FXML private ToggleButton segLowStock;
    @FXML private FlowPane cardFlow;

    private final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.US);

    // Dynamic user ID (set after login)
    private String currentUserId = null;

    // ---------- show username in header ----------
    private void updateUserHeader() {
        String name = UserSession.getCurrentUserName();
        if (dashboardTitle != null) {
            dashboardTitle.setText(
                    (name != null && !name.isBlank())
                            ? "Pantry Dashboard — " + name
                            : "Pantry Dashboard"
            );
        }
        if (userNameLabel != null) {
            userNameLabel.setText(
                    (name == null || name.isBlank()) ? "" : "Hi, " + name + "!"
            );
        }
    }
    // --------------------------------------------

    private void renderCards(ObservableList<PantryItem> items) {
        cardFlow.getChildren().clear();

        if (items.isEmpty()) {
            Label emptyLabel = new Label("No items in your pantry. Click '+ Add Item' to get started!");
            emptyLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #7f8c8d; -fx-padding: 40px;");
            cardFlow.getChildren().add(emptyLabel);
            return;
        }

        for (PantryItem item : items) {
            VBox card = createItemCard(item);
            cardFlow.getChildren().add(card);
        }
    }

    private VBox createItemCard(PantryItem item) {
        VBox card = new VBox(12);
        card.getStyleClass().add("card");
        card.setPrefWidth(320);
        card.setPadding(new Insets(16));

        Label nameLabel = new Label(item.getName());
        nameLabel.getStyleClass().add("card-title");
        nameLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Label categoryLabel = new Label("📂 " + (item.getCategory() != null ? item.getCategory() : "Uncategorized"));
        categoryLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #7f8c8d;");

        Label quantityLabel = new Label("📦 " + (item.getQuantityLabel() != null ? item.getQuantityLabel() :
                "Quantity: " + item.getQuantityNumeric()));
        quantityLabel.setStyle("-fx-font-size: 14px;");

        LocalDate expirationDate = item.getExpires();
        String expirationText = expirationDate != null ?
                "📅 Expires: " + expirationDate.format(DATE_FMT) :
                "📅 No expiration date";
        Label expirationLabel = new Label(expirationText);
        expirationLabel.setStyle("-fx-font-size: 14px;");

        ItemStatus status = calculateStatus(expirationDate, item.getQuantityNumeric());
        Label statusChip = new Label(statusToLabel(status));
        statusChip.getStyleClass().addAll("chip", statusToChipClass(status));

        HBox actionButtons = new HBox(8);
        actionButtons.setAlignment(Pos.CENTER_RIGHT);

        Button editBtn = new Button("✏️ Edit");
        editBtn.getStyleClass().add("secondary");
        editBtn.setOnAction(e -> handleEditItem(item));

        Button deleteBtn = new Button("🗑️ Delete");
        deleteBtn.getStyleClass().add("danger");
        deleteBtn.setOnAction(e -> handleDeleteItem(item));

        actionButtons.getChildren().addAll(editBtn, deleteBtn);

        card.getChildren().addAll(
                nameLabel,
                categoryLabel,
                quantityLabel,
                expirationLabel,
                statusChip,
                actionButtons
        );

        return card;
    }

    private void setupSearchListener() {
        searchField.textProperty().addListener((observable, oldValue, newValue) -> applyFilters());
    }

    private void loadPantryItems() {
        if (currentUserId == null || currentUserId.isBlank()) {
            showErrorAlert("Not signed in", "Missing user id. Please login.");
            return;
        }
        try {
            allItems = firebaseService.getPantryItems(currentUserId);
            renderCards(allItems);
            System.out.println("Loaded " + allItems.size() + " items from Firebase");
        } catch (ExecutionException | InterruptedException e) {
            System.err.println("Error loading items from Firebase: " + e.getMessage());
            e.printStackTrace();
            showErrorAlert("Database Error", "Failed to load pantry items: " + e.getMessage());
        }
    }

    public void setCurrentUserId(String uid) {
        this.currentUserId = uid;
        updateUserHeader();
        loadPantryItems();
        updateShoppingBadge();
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        try {
            firebaseService = new FirebaseService();
        } catch (Exception e) {
            System.err.println("Failed to initialize FirebaseService: " + e.getMessage());
            e.printStackTrace();
        }

        setupFilters();
        setupSearchListener();

        if (currentUserId == null || currentUserId.isBlank()) {
            currentUserId = UserSession.getCurrentUserId();
        }

        updateUserHeader();

        if (currentUserId != null && !currentUserId.isBlank()) {
            loadPantryItems();
            updateShoppingBadge();
        } else {
            showErrorAlert("Not signed in", "No current user found. Please login.");
        }
    }

    private void updateShoppingBadge() {
        if (shoppingBtn == null) return;

        try {
            String uid = (currentUserId != null && !currentUserId.isBlank())
                    ? currentUserId
                    : UserSession.getCurrentUserId();

            if (uid == null || uid.isBlank()) {
                shoppingBtn.setGraphic(null);
                return;
            }

            Firestore db = FirebaseConfiguration.getDatabase();
            ApiFuture<QuerySnapshot> future = db.collection("users")
                    .document(uid)
                    .collection("shoppingList")
                    .get();

            QuerySnapshot snapshot = future.get();
            int count = snapshot.size();

            if (count > 0) {
                StackPane badgeContainer = new StackPane();
                badgeContainer.getStyleClass().add("badge-container");

                Label badge = new Label(String.valueOf(count));
                badge.getStyleClass().add("badge");

                Label spacer = new Label(" ");

                badgeContainer.getChildren().addAll(spacer, badge);
                shoppingBtn.setGraphic(badgeContainer);
            } else {
                shoppingBtn.setGraphic(null);
            }

        } catch (Exception e) {
            System.err.println("Error updating shopping badge: " + e.getMessage());
            e.printStackTrace();
            shoppingBtn.setGraphic(null);
        }
    }

    private void applyFilters() {
        if (allItems == null) return;

        String searchText = searchField.getText().toLowerCase();
        ObservableList<PantryItem> filteredItems = allItems.filtered(item -> {
            boolean matchesSearch = searchText.isEmpty() ||
                    item.getName().toLowerCase().contains(searchText) ||
                    (item.getCategory() != null && item.getCategory().toLowerCase().contains(searchText));

            if (!matchesSearch) return false;

            if (segExpiring.isSelected()) {
                LocalDate expDate = item.getExpires();
                ItemStatus status = calculateStatus(expDate, item.getQuantityNumeric());
                return status == ItemStatus.EXPIRING || status == ItemStatus.EXPIRED;
            } else if (segLowStock.isSelected()) {
                return item.getQuantityNumeric() <= 2;
            }

            return true;
        });

        renderCards(filteredItems);
    }

    @FXML
    private void addItemBtnOnAction(ActionEvent event) {
        try {
            URL fxmlUrl = findFXML("addItem");
            if (fxmlUrl == null) {
                System.err.println("Could not find addItem.fxml");
                return;
            }

            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Scene addItemScene = new Scene(loader.load(), 400, 650);

            AddItemController controller = loader.getController();
            String uid = (currentUserId != null && !currentUserId.isBlank())
                    ? currentUserId
                    : UserSession.getCurrentUserId();
            controller.setCurrentUserId(uid);

            Stage addItemStage = new Stage();
            addItemStage.setTitle("Add New Item");
            addItemStage.setScene(addItemScene);

            themeManager.registerScene(addItemScene);

            addItemStage.initModality(Modality.APPLICATION_MODAL);
            addItemStage.setOnHidden(e -> {
                themeManager.unregisterScene(addItemScene);
                loadPantryItems();
                updateShoppingBadge();
            });
            addItemStage.showAndWait();

        } catch (IOException e) {
            System.err.println("Error opening Add Item window: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void recipesBtnOnAction(ActionEvent event) throws IOException {
        switchScene(event, "Recipe");
    }

    @FXML
    private void styleBtnOnAction(ActionEvent event) throws IOException {
        switchScene(event, "ThemeSettings");
    }

    // === NEW: Account button handler (fixes FXML onAction error) ===
    @FXML
    private void accountBtnOnAction(ActionEvent event) {
        String name = UserSession.getCurrentUserName();
        String uid  = (currentUserId != null && !currentUserId.isBlank())
                ? currentUserId : UserSession.getCurrentUserId();

        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Account");
        d.setHeaderText(name == null || name.isBlank() ? "Account" : name);

        Label info = new Label(
                (name == null || name.isBlank() ? "" : "Name: " + name + "\n") +
                        (uid  == null || uid.isBlank()  ? "" : "User ID: " + uid)
        );
        info.setStyle("-fx-font-size: 13px;");

        d.getDialogPane().setContent(new VBox(10, info));
        d.getDialogPane().getButtonTypes().addAll(new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE),
                new ButtonType("Log out", ButtonBar.ButtonData.OK_DONE));

        d.showAndWait().ifPresent(bt -> {
            if (bt.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                // Reuse existing logout
                logoutBtnOnAction(event);
            }
        });
    }
    // =================================================================

    private void setupFilters() {
        filterGroup = new ToggleGroup();
        segAll.setToggleGroup(filterGroup);
        segExpiring.setToggleGroup(filterGroup);
        segLowStock.setToggleGroup(filterGroup);

        segAll.setOnAction(e -> applyFilters());
        segExpiring.setOnAction(e -> applyFilters());
        segLowStock.setOnAction(e -> applyFilters());
    }

    private ItemStatus calculateStatus(LocalDate expirationDate, int quantity) {
        if (expirationDate == null) {
            return quantity <= 2 ? ItemStatus.LOW_STOCK : ItemStatus.OK;
        }

        LocalDate today = LocalDate.now();
        long daysUntilExpiration = ChronoUnit.DAYS.between(today, expirationDate);

        if (daysUntilExpiration < 0) return ItemStatus.EXPIRED;
        if (daysUntilExpiration <= 7) return ItemStatus.EXPIRING;
        if (quantity <= 2) return ItemStatus.LOW_STOCK;
        return ItemStatus.OK;
    }

    private String statusToLabel(ItemStatus status) {
        return switch (status) {
            case OK -> "✓ OK";
            case EXPIRING -> "⚠️ Expiring Soon";
            case EXPIRED -> "❌ Expired";
            case LOW_STOCK -> "⬇️ Low Stock";
        };
    }

    private void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private String statusToChipClass(ItemStatus status) {
        return switch (status) {
            case OK -> "chip-ok";
            case EXPIRING -> "chip-expiring";
            case EXPIRED -> "chip-danger";
            case LOW_STOCK -> "chip-warn";
        };
    }

    @FXML
    private void goToShoppingList(Event event) throws IOException {
        switchScene(event, "PantryItemsView");
    }

    private void handleEditItem(PantryItem item) {
        try {
            URL fxmlUrl = findFXML("addItem");
            if (fxmlUrl == null) {
                System.err.println("Could not find addItem.fxml");
                return;
            }

            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Scene editItemScene = new Scene(loader.load(), 400, 650);

            AddItemController controller = loader.getController();
            controller.setCurrentUserId(currentUserId);
            controller.setEditMode(item);

            Stage editItemStage = new Stage();
            editItemStage.setTitle("Edit Item");
            editItemStage.setScene(editItemScene);

            themeManager.registerScene(editItemScene);

            editItemStage.initModality(Modality.APPLICATION_MODAL);
            editItemStage.setOnHidden(e -> {
                themeManager.unregisterScene(editItemScene);
                loadPantryItems();
            });
            editItemStage.showAndWait();

        } catch (IOException e) {
            System.err.println("Error opening Edit Item window: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleDeleteItem(PantryItem item) {
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Delete Item");
        confirmDialog.setHeaderText("Delete " + item.getName() + "?");
        confirmDialog.setContentText("This action cannot be undone.");

        confirmDialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    String uid = (currentUserId != null && !currentUserId.isBlank())
                            ? currentUserId
                            : com.example.demo1.UserSession.getCurrentUserId();
                    if (uid == null || uid.isBlank()) {
                        showErrorAlert("Delete Error", "No user is signed in.");
                        return;
                    }
                    if (item.getId() == null || item.getId().isBlank()) {
                        showErrorAlert("Delete Error", "Item has no id (cannot delete).");
                        return;
                    }

                    firebaseService.deletePantryItem(item.getId(), uid);

                    allItems.remove(item);
                    renderCards(allItems);
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                    showErrorAlert("Delete Error", "Failed to delete item: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    private void logoutBtnOnAction(ActionEvent event) {
        try {
            // Optional: clear session data if you want
            // UserSession.clearSession();
            switchScene(event, "mainScreen");
        } catch (IOException ex) {
            showErrorAlert("Navigation Error", "Failed to open main screen: " + ex.getMessage());
        }
    }
}
