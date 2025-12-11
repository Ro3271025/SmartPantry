package ShoppingList;

import Firebase.FirebaseConfiguration;
import com.example.demo1.UserSession;
import com.google.cloud.Timestamp;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.text.Font;

import java.util.HashMap;
import java.util.Map;

public class Dialogs {

    public static Dialog<PantryItem> addItemDialog() {
        Dialog<PantryItem> dialog = new Dialog<>();
        dialog.setTitle("Add New Shopping Item");

        // 1. --- Set up Dialog Pane Appearance ---
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().getStyleClass().add("add-item-dialog");

        // 2. --- INPUT FIELDS & SETUP ---
        TextField name = new TextField();
        name.setPromptText("e.g., Eggs, Milk, Pasta...");
        GridPane.setHgrow(name, Priority.ALWAYS);

        Spinner<Integer> qty = new Spinner<>(1, 999, 1);
        qty.setEditable(true);

        ComboBox<String> unit = new ComboBox<>();
        unit.getItems().addAll(
                "pcs", "bottles", "sticks",
                "count", "pack", "box",
                "g", "kg", "oz", "lb",
                "ml", "l", "gallon"
        );
        unit.setEditable(true);
        unit.setPromptText("Unit");
        unit.getSelectionModel().selectFirst();

        ComboBox<String> location = new ComboBox<>();
        location.getItems().addAll("Pantry", "Fridge", "Freezer");
        location.setPromptText("Storage Location");
        location.getSelectionModel().selectFirst();


        // 3. --- LAYOUT (Cleaner GridPane) ---
        GridPane gp = new GridPane();
        gp.setHgap(15);
        gp.setVgap(10);
        gp.setPadding(new Insets(20));

        // Use a slightly bolder font for labels for emphasis
        Label nameLabel = new Label("Item Name:");
        nameLabel.setFont(Font.font("System", 13));

        Label qtyLabel = new Label("Quantity:");
        Label unitLabel = new Label("Unit:");
        Label locLabel = new Label("Location:");

        // Row 0: Item Name (spans two columns for better expansion)
        gp.add(nameLabel, 0, 0);
        gp.add(name, 1, 0, 3, 1); // Span 3 columns

        // Row 1: Quantity and Unit (put side-by-side)
        gp.add(qtyLabel, 0, 1);
        gp.add(qty, 1, 1);

        gp.add(unitLabel, 2, 1);
        gp.add(unit, 3, 1);

        // Row 2: Location
        gp.add(locLabel, 0, 2);
        gp.add(location, 1, 2, 3, 1); // Span 3 columns for consistency

        dialog.getDialogPane().setContent(gp);
        final Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setDisable(true);
        name.textProperty().addListener((obs, oldV, newV) ->
                okBtn.setDisable(newV == null || newV.trim().isEmpty()));

        dialog.setOnShown(e -> name.requestFocus());

        // === RESULT CONVERTER ===
        dialog.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                PantryItem item = new PantryItem();
                item.setName(name.getText().trim());
                item.setQty(qty.getValue());
                item.setUnit(unit.getValue());
                item.setLocation(location.getValue());

                String id = saveToFirebase(item);
                item.setShoppingDocId(id);

                return item;
            }
            return null;
        });


        return dialog;
    }

    /** Shows a dialog to edit an existing Shopping List Item. */
    public static Dialog<PantryItem> editItemDialog(PantryItem existingItem) {
        Dialog<PantryItem> dialog = new Dialog<>();
        dialog.setTitle("Edit Shopping List Item: " + existingItem.getName());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().getStyleClass().add("add-item-dialog"); // Apply styling

        // === INPUT FIELDS ===
        TextField name = new TextField(existingItem.getName());
        GridPane.setHgrow(name, Priority.ALWAYS); // Ensure it expands

        // Set up Spinner with existing value
        SpinnerValueFactory<Integer> valueFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 999, existingItem.getQty());
        Spinner<Integer> qty = new Spinner<>(valueFactory);
        qty.setEditable(true);

        // Set up Unit ComboBox with existing value
        ComboBox<String> unit = new ComboBox<>();
        unit.getItems().addAll(
                "pcs", "bottles", "sticks",
                "count", "pack", "box",
                "g", "kg", "oz", "lb",
                "ml", "l", "gallon"
        );
        unit.setEditable(true);
        unit.getSelectionModel().select(existingItem.getUnit());

        // Set up Location ComboBox with existing value
        ComboBox<String> location = new ComboBox<>();
        location.getItems().addAll("Pantry", "Fridge", "Freezer");
        location.getSelectionModel().select(existingItem.getLocation());


        // === LAYOUT (Cleaner GridPane - Matching addItemDialog) ===
        GridPane gp = new GridPane();
        gp.setHgap(15);
        gp.setVgap(10);
        gp.setPadding(new Insets(20));

        Label nameLabel = new Label("Item Name:");
        nameLabel.setFont(Font.font("System", 13));

        Label qtyLabel = new Label("Quantity:");
        Label unitLabel = new Label("Unit:");
        Label locLabel = new Label("Location:");

        // Row 0: Item Name
        gp.add(nameLabel, 0, 0);
        gp.add(name, 1, 0, 3, 1);

        // Row 1: Quantity and Unit
        gp.add(qtyLabel, 0, 1);
        gp.add(qty, 1, 1);

        gp.add(unitLabel, 2, 1);
        gp.add(unit, 3, 1);

        // Row 2: Location
        gp.add(locLabel, 0, 2);
        gp.add(location, 1, 2, 3, 1);

        dialog.getDialogPane().setContent(gp);

        // === RESULT CONVERTER ===
        dialog.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                existingItem.setName(name.getText().trim());
                existingItem.setQty(qty.getValue());
                existingItem.setUnit(unit.getValue());
                existingItem.setLocation(location.getValue());

                updateFirebase(existingItem);

                return existingItem;
            }
            return null;
        });

        return dialog;
    }

    // --- Firebase Methods (Kept identical to your original code) ---

    /** Delete the item from Firestore based on its document ID */
    public static void deleteFromFirebase(PantryItem item) {
        if (item.getShoppingDocId() == null || item.getShoppingDocId().isEmpty()) {
            System.err.println("⚠ Cannot delete item — item has no document ID.");
            return;
        }

        try {
            String uid = UserSession.getCurrentUserId();
            if (uid == null || uid.isBlank()) {
                System.err.println("⚠ Cannot delete item — no logged-in user found.");
                return;
            }

            var db = FirebaseConfiguration.getDatabase();

            // 1. Build the exact path to the document to be deleted
            var docRef = db.collection("users")
                    .document(uid)
                    .collection("shoppingList")
                    .document(item.getShoppingDocId()); // Use the stored ID

            // 2. Execute the delete operation and wait for it to complete
            docRef.delete().get();

            System.out.println("❌ Deleted from shoppingList: " + item.getName() + " (" + item.getShoppingDocId() + ")");

        } catch (Exception e) {
            System.err.println("Error deleting item " + item.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Update the existing item in Firestore under users/{uid}/shoppingList */
    private static void updateFirebase(PantryItem item) {
        if (item.getShoppingDocId() == null || item.getShoppingDocId().isBlank()) {
            System.err.println("⚠ Cannot update item — missing document ID.");
            return;
        }

        try {
            String uid = UserSession.getCurrentUserId();
            if (uid == null || uid.isBlank()) {
                System.err.println("⚠ Cannot update item — no logged-in user found.");
                return;
            }

            var db = FirebaseConfiguration.getDatabase();

            // Target the specific document using its ID
            var docRef = db.collection("users")
                    .document(uid)
                    .collection("shoppingList")
                    .document(item.getShoppingDocId());

            // Create the map of fields to update
            Map<String, Object> data = new HashMap<>();
            data.put("item", item.getName());
            data.put("quantity", item.getQty());
            data.put("unit", item.getUnit());
            data.put("location", item.getLocation());
            docRef.update(data).get();

            System.out.println("✏️ Updated shoppingList: " + item.getName() + " (" + item.getShoppingDocId() + ")");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String saveToFirebase(PantryItem item) {
        try {
            String uid = UserSession.getCurrentUserId();
            if (uid == null || uid.isBlank()) {
                System.err.println("⚠ Cannot save item — no logged-in user found.");
                return null;
            }

            var db = FirebaseConfiguration.getDatabase();
            var shoppingListRef = db.collection("users")
                    .document(uid)
                    .collection("shoppingList");

            Map<String, Object> data = new HashMap<>();
            data.put("item", item.getName());
            data.put("quantity", item.getQty());
            data.put("unit", item.getUnit());
            data.put("location", item.getLocation());
            data.put("createdAt", Timestamp.now());

            var docRef = shoppingListRef.add(data).get();  // wait; returns docRef
            System.out.println("✅ Saved to shoppingList: " + item.getName() + " (" + docRef.getId() + ")");
            return docRef.getId();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}
