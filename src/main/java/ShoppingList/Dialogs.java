package ShoppingList;

import Firebase.FirebaseConfiguration;
import com.example.demo1.UserSession;
import com.google.cloud.Timestamp;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority; // New Import for spacing
import javafx.scene.text.Font; // New Import for styling

import java.util.HashMap;
import java.util.Map;

public class Dialogs {

    public static Dialog<PantryItem> addItemDialog() {
        Dialog<PantryItem> dialog = new Dialog<>();
        dialog.setTitle("Add New Shopping Item"); // Cleaner title

        // 1. --- Set up Dialog Pane Appearance ---
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().getStyleClass().add("add-item-dialog");

        // 2. --- INPUT FIELDS & SETUP ---
        TextField name = new TextField();
        name.setPromptText("e.g., Eggs, Milk, Pasta..."); // Helpful prompt
        GridPane.setHgrow(name, Priority.ALWAYS); // Ensures the name field expands

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
        gp.setHgap(15); // Increased horizontal gap
        gp.setVgap(10); // Consistent vertical gap
        gp.setPadding(new Insets(20)); // Increased padding around the edge

        // Use a slightly bolder font for labels for emphasis
        Label nameLabel = new Label("Item Name:");
        nameLabel.setFont(Font.font("System", 13));

        Label qtyLabel = new Label("Quantity:");
        Label unitLabel = new Label("Unit:");
        Label locLabel = new Label("Location:");

        // Row 0: Item Name (spans two columns for better expansion)
        gp.add(nameLabel, 0, 0);
        gp.add(name, 1, 0, 2, 1); // Span 2 columns

        // Row 1: Quantity and Unit (put side-by-side)
        gp.add(qtyLabel, 0, 1);
        gp.add(qty, 1, 1);

        gp.add(unitLabel, 2, 1); // Shift Unit Label
        gp.add(unit, 3, 1);      // Shift Unit ComboBox

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

    /** Save the item to Firestore under users/{uid}/shoppingList */

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

    /** Shows a dialog to edit an existing Shopping List Item. */
    public static Dialog<PantryItem> editItemDialog(PantryItem existingItem) {
        Dialog<PantryItem> dialog = new Dialog<>();
        dialog.setTitle("Edit Shopping List Item: " + existingItem.getName());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // === INPUT FIELDS ===
        TextField name = new TextField(existingItem.getName());

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


        // === LAYOUT (same as addItemDialog) ===
        GridPane gp = new GridPane();
        gp.setHgap(10); gp.setVgap(10); gp.setPadding(new Insets(10));
        gp.addRow(0, new Label("Item"),      name);
        gp.addRow(1, new Label("Quantity"),  qty);
        gp.addRow(2, new Label("Unit"),      unit);
        gp.addRow(3, new Label("Location"),  location);

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
