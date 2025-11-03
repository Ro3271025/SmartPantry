package Controllers;

import com.example.demo1.FirebaseConfiguration;
import com.google.cloud.firestore.Firestore;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import com.google.cloud.Timestamp;


public class AddItemController {

    @FXML private TextField itemNameField;
    @FXML private TextField quantityField;
    @FXML private ComboBox<String> unitComboBox;
    @FXML private ComboBox<String> locationComboBox;
    @FXML private ComboBox<String> categoryComboBox;
    @FXML private DatePicker expiryDatePicker;
    @FXML private Label statusLabel;


    // ID from Firebase
    private static final String CURRENT_USER_ID = "J9UNwz3YrJYHs97APHFoQmrZdG73";

    @FXML
    public void initialize() {
        FirebaseConfiguration.initialize();

        unitComboBox.getItems().addAll("pcs", "kg", "L", "oz", "box");
        locationComboBox.getItems().addAll("Pantry", "Fridge", "Freezer");
        categoryComboBox.getItems().addAll("Dairy", "Produce", "Meat", "Snacks", "Beverages");

        System.out.println("AddItemController initialized successfully!");
    }

    @FXML
    private void handleSaveItem() {
        try {
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

            Map<String, Object> item = new HashMap<>();
            item.put("name", name);
            item.put("quantity", quantity);
            item.put("unit", unit);
            item.put("location", location != null ? location : "Not specified");
            item.put("category", category != null ? category : "Not specified");
            item.put("expiryDate", expiryDate.toString());
            item.put("dateAdded", com.google.cloud.Timestamp.now());

            // Saving to  Firebase
            Firestore db = FirebaseConfiguration.getDatabase();
            db.collection("users")
                    .document(CURRENT_USER_ID)
                    .collection("pantryItems")
                    .add(item)
                    .get(); // Wait for completion

            statusLabel.setText("✓ Item saved successfully to Firebase!");
            statusLabel.setTextFill(Color.GREEN);
            System.out.println("Item saved: " + item);
            clearForm();

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
    }

    @FXML
    private void handleScanBarcode() {
        statusLabel.setText("📷 Barcode scanning feature coming soon!");
        statusLabel.setTextFill(Color.BLUE);
    }

    @FXML
    private void handleCancel() {
        clearForm();
        statusLabel.setText("Action canceled.");
        statusLabel.setTextFill(Color.GRAY);
    }
}
