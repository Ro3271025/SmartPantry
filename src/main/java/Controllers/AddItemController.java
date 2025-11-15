package Controllers;

import com.example.demo1.FirebaseService;
import com.example.demo1.PantryItem;
import com.example.demo1.OpenFoodFactsService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import com.example.demo1.CameraBarcodeScanner;

import com.example.demo1.UserSession;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

public class AddItemController {

    @FXML private TextField itemNameField;
    @FXML private TextField quantityField;
    @FXML private ComboBox<String> unitComboBox;
    @FXML private ComboBox<String> locationComboBox;
    @FXML private ComboBox<String> categoryComboBox;
    @FXML private DatePicker expiryDatePicker;
    @FXML private Label statusLabel;

    private FirebaseService firebaseService;
    private OpenFoodFactsService foodFactsService;
    private CameraBarcodeScanner cameraScanner;
    private String currentUserId;
    private PantryItem itemToEdit;
    private boolean isEditMode = false;

    @FXML
    public void initialize() {
        firebaseService = new FirebaseService();
        foodFactsService = new OpenFoodFactsService();

        unitComboBox.getItems().addAll("pcs", "kg", "L", "oz", "box", "bottles", "cans");
        locationComboBox.getItems().addAll("Pantry", "Fridge", "Freezer");
        categoryComboBox.getItems().addAll("Dairy", "Vegetables", "Fruits", "Meat",
                "Grains", "Beverages", "Snacks", "Other");

        // Fallback to session if PantryController didn't inject UID
        if (currentUserId == null || currentUserId.isBlank()) {
            currentUserId = UserSession.getCurrentUserId();
        }

        if (currentUserId == null || currentUserId.isBlank()) {
            showError("No user ID set. Please log in first.");
        }
    }

    public void setCurrentUserId(String userId) {
        this.currentUserId = userId;
    }

    public void setEditMode(PantryItem item) {
        this.itemToEdit = item;
        this.isEditMode = true;

        // Pre-fill form with existing data
        itemNameField.setText(item.getName());
        quantityField.setText(String.valueOf(item.getQuantityNumeric()));
        unitComboBox.setValue(item.getUnit());
        categoryComboBox.setValue(item.getCategory());

        if (item.getExpires() != null) {
            expiryDatePicker.setValue(item.getExpires());
        }

        statusLabel.setText("Editing: " + item.getName());
        statusLabel.setTextFill(Color.BLUE);
    }

    @FXML
    private void handleScanBarcode() {
        // Initialize camera scanner if not already done
        if (cameraScanner == null) {
            cameraScanner = new CameraBarcodeScanner();
        }

        // Start camera scanning with callback
        cameraScanner.startScanning(barcode -> {
            if (barcode.trim().isEmpty()) {
                showError("Please enter a valid barcode");
                return;
            }

            // Show loading status
            Platform.runLater(() -> {
                statusLabel.setText("🔍 Searching Open Food Facts database...");
                statusLabel.setTextFill(Color.BLUE);
            });

            // Fetch product data in background thread
            new Thread(() -> {
                try {
                    OpenFoodFactsService.ProductData product = foodFactsService.getProductByBarcode(barcode.trim());

                    // Update UI on JavaFX thread
                    Platform.runLater(() -> {
                        if (product.isFound()) {
                            populateFormWithProductData(product);
                            statusLabel.setText("✓ Product found! Please verify and adjust the details as needed.");
                            statusLabel.setTextFill(Color.GREEN);
                        } else {
                            // Show user-friendly error with option to enter manually
                            String message = "Product not found in database.\n\n" +
                                    "This could mean:\n" +
                                    "• The barcode is not yet in Open Food Facts\n" +
                                    "• The barcode was scanned incorrectly\n\n" +
                                    "You can still add this item manually below.";

                            Alert alert = new Alert(Alert.AlertType.INFORMATION);
                            alert.setTitle("Product Not Found");
                            alert.setHeaderText("Barcode: " + barcode);
                            alert.setContentText(message);
                            alert.showAndWait();

                            statusLabel.setText("Product not found. Please enter details manually.");
                            statusLabel.setTextFill(Color.ORANGE);
                        }
                    });

                } catch (Exception e) {
                    Platform.runLater(() -> {
                        showError("Error connecting to database: " + e.getMessage());
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Connection Error");
                        alert.setHeaderText("Could not reach Open Food Facts");
                        alert.setContentText("Please check your internet connection and try again.");
                        alert.showAndWait();
                    });
                    e.printStackTrace();
                }
            }).start();
        });
    }

    /**
     * Populate form fields with data from Open Food Facts
     */
    private void populateFormWithProductData(OpenFoodFactsService.ProductData product) {
        // Set product name
        if (product.getName() != null && !product.getName().isEmpty()) {
            itemNameField.setText(product.getName());
        }

        // Parse and set quantity
        if (product.getQuantity() != null && !product.getQuantity().isEmpty()) {
            OpenFoodFactsService.ParsedQuantity parsedQty =
                    new OpenFoodFactsService.ParsedQuantity(product.getQuantity());

            quantityField.setText(String.valueOf(parsedQty.numeric));

            // Set unit if it matches one of our options
            if (unitComboBox.getItems().contains(parsedQty.unit)) {
                unitComboBox.setValue(parsedQty.unit);
            } else {
                unitComboBox.setValue("pcs"); // default
            }
        } else {
            quantityField.setText("1");
            unitComboBox.setValue("pcs");
        }

        // Set category
        if (product.getCategory() != null) {
            categoryComboBox.setValue(product.getCategory());
        }

        // Set estimated expiration date
        if (product.getEstimatedExpirationDate() != null) {
            expiryDatePicker.setValue(product.getEstimatedExpirationDate());
        }

        // Set default location based on category
        String defaultLocation = getDefaultLocationForCategory(product.getCategory());
        locationComboBox.setValue(defaultLocation);
    }

    /**
     * Get default storage location based on category
     */
    private String getDefaultLocationForCategory(String category) {
        if (category == null) return "Pantry";

        switch (category) {
            case "Dairy":
            case "Meat":
            case "Vegetables":
            case "Fruits":
                return "Fridge";
            case "Grains":
            case "Snacks":
            case "Beverages":
                return "Pantry";
            default:
                return "Pantry";
        }
    }

    @FXML
    private void handleSaveItem() {
        try {
            // Try session fallback before failing
            if (currentUserId == null || currentUserId.isBlank()) {
                currentUserId = UserSession.getCurrentUserId();
            }
            if (currentUserId == null || currentUserId.isBlank()) {
                showError("No user ID set. Please log in first.");
                return;
            }

            String name = itemNameField.getText();
            String quantityText = quantityField.getText();
            String unit = unitComboBox.getValue();
            String location = locationComboBox.getValue();
            String category = categoryComboBox.getValue();
            LocalDate expiryDate = expiryDatePicker.getValue();

            if (name == null || name.trim().isEmpty()) {
                showError("Please enter an item name.");
                return;
            }
            if (quantityText == null || quantityText.trim().isEmpty()) {
                showError("Please enter a quantity.");
                return;
            }

            int quantity;
            try {
                quantity = Integer.parseInt(quantityText);
                if (quantity <= 0) {
                    showError("Quantity must be greater than 0.");
                    return;
                }
            } catch (NumberFormatException e) {
                showError("Please enter a valid number for quantity.");
                return;
            }

            if (unit == null) {
                showError("Please select a unit.");
                return;
            }
            if (expiryDate == null) {
                showError("Please select an expiration date.");
                return;
            }

            Date expirationDate = Date.from(expiryDate.atStartOfDay(ZoneId.systemDefault()).toInstant());

            if (isEditMode && itemToEdit != null) {
                itemToEdit.setUserId(currentUserId);
                itemToEdit.setName(name);
                itemToEdit.setQuantityNumeric(quantity);
                itemToEdit.setQuantityLabel(quantity + " " + unit);
                itemToEdit.setUnit(unit);
                itemToEdit.setCategory(category != null ? category : "Other");
                itemToEdit.setExpirationDate(expirationDate);

                firebaseService.updatePantryItem(itemToEdit.getId(), itemToEdit);

                statusLabel.setText("✓ Item updated successfully!");
                statusLabel.setTextFill(Color.GREEN);
            } else {
                PantryItem newItem = new PantryItem();
                newItem.setName(name);
                newItem.setQuantityNumeric(quantity);
                newItem.setQuantityLabel(quantity + " " + unit);
                newItem.setUnit(unit);
                newItem.setCategory(category != null ? category : "Other");
                newItem.setExpirationDate(expirationDate);
                newItem.setUserId(currentUserId);
                newItem.setDateAdded(new Date());

                String itemId = firebaseService.addPantryItem(newItem, currentUserId);

                statusLabel.setText("✓ Item saved successfully! ID: " + itemId);
                statusLabel.setTextFill(Color.GREEN);
            }

            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                    Platform.runLater(this::closeWindow);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();

        } catch (Exception e) {
            showError("Failed to save item: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showError(String message) {
        statusLabel.setText("✗ " + message);
        statusLabel.setTextFill(Color.RED);
    }

    private void clearForm() {
        itemNameField.clear();
        quantityField.clear();
        unitComboBox.setValue(null);
        locationComboBox.setValue(null);
        categoryComboBox.setValue(null);
        expiryDatePicker.setValue(null);
        statusLabel.setText("");
    }

    private void closeWindow() {
        Stage stage = (Stage) itemNameField.getScene().getWindow();
        stage.close();
    }

    @FXML
    private void handleCancel() {
        closeWindow();
    }
}