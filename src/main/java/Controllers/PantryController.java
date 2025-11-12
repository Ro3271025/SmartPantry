package Controllers;

import javafx.fxml.Initializable;
import com.example.demo1.PantryItem;
import com.example.demo1.FirebaseConfiguration;
import com.example.demo1.FirebaseService;
import javafx.collections.ObservableList;

import java.net.URL;
import java.time.ZoneId;
import java.util.Date;
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
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.event.ActionEvent;
import com.example.demo1.ItemStatus;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

public class PantryController extends BaseController implements Initializable {
    // NEW - Added these three fields
    private FirebaseService firebaseService;
    private ObservableList<PantryItem> allItems;
    private ToggleGroup filterGroup;
    @FXML private TextField searchField;
    @FXML private Button addItemBtn;
    @FXML private Button recipesBtn;
    @FXML private Button shoppingBtn;
    @FXML private Button styleBtn;
    @FXML private Button logoutBtn;
    @FXML private ToggleButton segAll;
    @FXML private ToggleButton segExpiring;
    @FXML private ToggleButton segLowStock;
    @FXML private FlowPane cardFlow;

    private final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.US);

    // 🔑 Dynamic user ID (set after login)
    private String currentUserId;

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

    /**
     * Create a card UI for a single pantry item
     */
    private VBox createItemCard(PantryItem item) {
        VBox card = new VBox(12);
        card.getStyleClass().add("card");
        card.setPrefWidth(320);
        card.setPadding(new Insets(16));

        // Item name
        Label nameLabel = new Label(item.getName());
        nameLabel.getStyleClass().add("card-title");
        nameLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        // Category
        Label categoryLabel = new Label("📂 " + (item.getCategory() != null ? item.getCategory() : "Uncategorized"));
        categoryLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #7f8c8d;");

        // Quantity
        Label quantityLabel = new Label("📦 " + (item.getQuantityLabel() != null ? item.getQuantityLabel() :
                "Quantity: " + item.getQuantityNumeric()));
        quantityLabel.setStyle("-fx-font-size: 14px;");

        // Expiration date
        LocalDate expirationDate = item.getExpires();

        String expirationText = expirationDate != null ?
                "📅 Expires: " + expirationDate.format(DATE_FMT) :
                "📅 No expiration date";
        Label expirationLabel = new Label(expirationText);
        expirationLabel.setStyle("-fx-font-size: 14px;");

        // Status chip
        ItemStatus status = calculateStatus(expirationDate, item.getQuantityNumeric());
        Label statusChip = new Label(statusToLabel(status));
        statusChip.getStyleClass().addAll("chip", statusToChipClass(status));

        // Action buttons
        HBox actionButtons = new HBox(8);
        actionButtons.setAlignment(Pos.CENTER_RIGHT);

        Button editBtn = new Button("✏️ Edit");
        editBtn.getStyleClass().add("secondary");
        editBtn.setOnAction(e -> handleEditItem(item));

        Button deleteBtn = new Button("🗑️ Delete");
        deleteBtn.getStyleClass().add("danger");
        deleteBtn.setOnAction(e -> handleDeleteItem(item));

        actionButtons.getChildren().addAll(editBtn, deleteBtn);

        // Add all components to card
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


    /**
     * Setup search field listener
     */
    private void setupSearchListener() {
        searchField.textProperty().addListener((observable, oldValue, newValue) -> applyFilters());
    }





    /**
     * Load all pantry items from Firebase for the current user
     */
    private void loadPantryItems() {
        try {
            allItems = firebaseService.getPantryItems(currentUserId);            renderCards(allItems);
            System.out.println("Loaded " + allItems.size() + " items from Firebase");
        } catch (ExecutionException | InterruptedException e) {
            System.err.println("Error loading items from Firebase: " + e.getMessage());
            e.printStackTrace();
            showErrorAlert("Database Error", "Failed to load pantry items: " + e.getMessage());
        }
    }

    public void setCurrentUserId(String uid) {
        this.currentUserId = uid;
        loadPantryItems();
    }


    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        try {
            firebaseService = new FirebaseService();
        } catch (Exception e) {
            System.err.println("Failed to initialize FirebaseService: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Apply search and filter to items
     */
    private void applyFilters() {
        if (allItems == null) return;

        String searchText = searchField.getText().toLowerCase();
        ObservableList<PantryItem> filteredItems = allItems.filtered(item -> {
            // Apply search filter
            boolean matchesSearch = searchText.isEmpty() ||
                    item.getName().toLowerCase().contains(searchText) ||
                    (item.getCategory() != null && item.getCategory().toLowerCase().contains(searchText));

            if (!matchesSearch) return false;

            // Apply status filter
            if (segExpiring.isSelected()) {
                LocalDate expDate = item.getExpires();
                ItemStatus status = calculateStatus(expDate, item.getQuantityNumeric());
                return status == ItemStatus.EXPIRING || status == ItemStatus.EXPIRED;
            } else if (segLowStock.isSelected()) {
                return item.getQuantityNumeric() <= 2;
            }

            return true; // "All" filter
        });

        renderCards(filteredItems);
    }


    /** Opens the Add Item screen in a new popup window */
//    @FXML
//    private void addItemBtnOnAction(ActionEvent event) {
//        try {
//            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/demo1/addItem.fxml"));
//            Scene addItemScene = new Scene(loader.load(), 400, 650);
//
//            // Pass current user ID into AddItemController
//            AddItemController controller = loader.getController();
//           // controller.setCurrentUserId(currentUserId);
//
//            Stage addItemStage = new Stage();
//            addItemStage.setTitle("Add New Item");
//            addItemStage.setScene(addItemScene);
//            addItemStage.initModality(Modality.APPLICATION_MODAL);
//            addItemStage.showAndWait();
//
//
//            // render();
//
//        } catch (IOException e) {
//            System.err.println("Error opening Add Item window: " + e.getMessage());
//            e.printStackTrace();
//        }
//    }

    @FXML
    private void addItemBtnOnAction(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/demo1/addItem.fxml"));
            Scene addItemScene = new Scene(loader.load(), 400, 650);

            AddItemController controller = loader.getController();
            controller.setCurrentUserId(currentUserId); // UNCOMMENTED THIS

            Stage addItemStage = new Stage();
            addItemStage.setTitle("Add New Item");
            addItemStage.setScene(addItemScene);
            addItemStage.initModality(Modality.APPLICATION_MODAL);
            addItemStage.showAndWait();

            loadPantryItems(); // ADDED THIS - Reload items after adding

        } catch (IOException e) {
            System.err.println("Error opening Add Item window: " + e.getMessage());
            e.printStackTrace();
        }
    }


    /** Opens the Recipe Tab screen */
    @FXML
    private void recipesBtnOnAction(ActionEvent event) throws IOException {
        switchScene(event, "Recipe");
    }


//    private void setupFilters() {
//        ToggleGroup filterGroup = new ToggleGroup();
//        segAll.setToggleGroup(filterGroup);
//        segExpiring.setToggleGroup(filterGroup);
//        segLowStock.setToggleGroup(filterGroup);
//    }

    private void setupFilters() {
        filterGroup = new ToggleGroup(); // REMOVED 'ToggleGroup' keyword (uses field instead)
        segAll.setToggleGroup(filterGroup);
        segExpiring.setToggleGroup(filterGroup);
        segLowStock.setToggleGroup(filterGroup);

        // ADDED THESE THREE LINES - Add listeners to filter buttons
        segAll.setOnAction(e -> applyFilters());
        segExpiring.setOnAction(e -> applyFilters());
        segLowStock.setOnAction(e -> applyFilters());
    }

//    private void setupPlaceholderButtons() {
//        recipesBtn.setOnAction(event -> System.out.println("[Placeholder] Recipes button clicked"));
//        shoppingBtn.setOnAction(event -> System.out.println("[Placeholder] Shopping button clicked"));
//        styleBtn.setOnAction(event -> System.out.println("[Placeholder] Style Guide button clicked"));
//        logoutBtn.setOnAction(event -> System.out.println("[Placeholder] Logout button clicked"));
//    }

//    private ItemStatus calculateStatus(LocalDate expirationDate, int quantity) {
//        LocalDate today = LocalDate.now();
//        long daysUntilExpiration = ChronoUnit.DAYS.between(today, expirationDate);
//
//        if (daysUntilExpiration < 0) return ItemStatus.EXPIRED;
//        if (daysUntilExpiration <= 7) return ItemStatus.EXPIRING;
//        if (quantity <= 2) return ItemStatus.LOW_STOCK;
//        return ItemStatus.OK;
//    }


    private ItemStatus calculateStatus(LocalDate expirationDate, int quantity) {
        // ADDED THIS CHECK - Handle null expiration dates
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


//    private String statusToLabel(ItemStatus status) {
//        return switch (status) {
//            case OK        -> "OK";
//            case EXPIRING  -> "Expiring";
//            case EXPIRED   -> "Expired";
//            case LOW_STOCK -> "Low Stock";
//        };
//    }


    private String statusToLabel(ItemStatus status) {
        return switch (status) {
            case OK        -> "✓ OK";           // ADDED EMOJI
            case EXPIRING  -> "⚠️ Expiring Soon"; // ADDED EMOJI + "Soon"
            case EXPIRED   -> "❌ Expired";      // ADDED EMOJI
            case LOW_STOCK -> "⬇️ Low Stock";    // ADDED EMOJI
        };
    }

    /**
     * Show error alert dialog
     */
    private void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    private String statusToChipClass(ItemStatus status) {
        return switch (status) {
            case OK        -> "chip-ok";
            case EXPIRING  -> "chip-expiring";
            case EXPIRED   -> "chip-danger";
            case LOW_STOCK -> "chip-warn";
        };
    }
    @FXML
    private void goToShoppingList(Event event) throws IOException {
        switchScene(event, "PantryItemsView");
    }


    /**
     * Handle editing an item
     */
    private void handleEditItem(PantryItem item) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/demo1/addItem.fxml"));
            Scene editItemScene = new Scene(loader.load(), 400, 650);

            AddItemController controller = loader.getController();
            controller.setCurrentUserId(currentUserId);
            // controller.setEditMode(item); // Uncomment when you add this method to AddItemController

            Stage editItemStage = new Stage();
            editItemStage.setTitle("Edit Item");
            editItemStage.setScene(editItemScene);
            editItemStage.initModality(Modality.APPLICATION_MODAL);
            editItemStage.showAndWait();

            // Reload items after editing
            loadPantryItems();

        } catch (IOException e) {
            System.err.println("Error opening Edit Item window: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handle deleting an item
     */
    private void handleDeleteItem(PantryItem item) {
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Delete Item");
        confirmDialog.setHeaderText("Delete " + item.getName() + "?");
        confirmDialog.setContentText("This action cannot be undone.");

        confirmDialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    firebaseService.deletePantryItem(item.getId());
                    allItems.remove(item);
                    renderCards(allItems);
                    System.out.println("Deleted item: " + item.getName());
                } catch (ExecutionException | InterruptedException e) {
                    System.err.println("Error deleting item: " + e.getMessage());
                    e.printStackTrace();
                    showErrorAlert("Delete Error", "Failed to delete item: " + e.getMessage());
                }
            }
        });
    }
}