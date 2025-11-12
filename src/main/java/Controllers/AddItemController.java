package Controllers;

import com.example.demo1.FirebaseService;
import com.example.demo1.PantryItem;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

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
    private String currentUserId;
    private PantryItem itemToEdit; // For edit mode
    private boolean isEditMode = false;

    @FXML
    public void initialize() {
        firebaseService = new FirebaseService();

        // Initialize dropdowns
        unitComboBox.getItems().addAll("pcs", "kg", "L", "oz", "box", "bottles", "cans");
        locationComboBox.getItems().addAll("Pantry", "Fridge", "Freezer");
        categoryComboBox.getItems().addAll("Dairy", "Vegetables", "Fruits", "Meat",
                "Grains", "Beverages", "Snacks", "Other");
    }

    /**
     * ✅ ADDED: Set the current user ID (called from PantryController)
     */
    public void setCurrentUserId(String userId) {
        this.currentUserId = userId;
    }

    /**
     * ✅ ADDED: Set edit mode with existing item
     */
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
    private void handleSaveItem() {
        try {
            // Validate user ID
            if (currentUserId == null || currentUserId.isEmpty()) {
                showError("No user ID set. Please log in first.");
                return;
            }

            // Get form values
            String name = itemNameField.getText();
            String quantityText = quantityField.getText();
            String unit = unitComboBox.getValue();
            String location = locationComboBox.getValue();
            String category = categoryComboBox.getValue();
            LocalDate expiryDate = expiryDatePicker.getValue();

            // Validate inputs
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

            // Convert LocalDate to Date for Firebase
            Date expirationDate = Date.from(expiryDate.atStartOfDay(ZoneId.systemDefault()).toInstant());

            if (isEditMode && itemToEdit != null) {
                // ✅ UPDATE existing item
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
                // ✅ ADD new item
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

            // Close window after 1 second
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                    javafx.application.Platform.runLater(this::closeWindow);
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
    private void handleScanBarcode() {
        statusLabel.setText("📷 Barcode scanning feature coming soon!");
        statusLabel.setTextFill(Color.BLUE);
    }

    @FXML
    private void handleCancel() {
        closeWindow();
    }
}