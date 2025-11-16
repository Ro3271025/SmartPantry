package com.example.demo1;

import Controllers.PantryItem;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.Firestore;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.util.HashMap;
import java.util.Map;

public class Dialogs {

    public static Dialog<PantryItem> addItemDialog() {
        Dialog<PantryItem> dialog = new Dialog<>();
        dialog.setTitle("Add Shopping List Item");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // === INPUT FIELDS ===
        TextField name = new TextField();
        name.setPromptText("Item name");

        Spinner<Integer> qty = new Spinner<>(1, 999, 1);
        qty.setEditable(true);

        // Unit (editable with some common choices)
        ComboBox<String> unit = new ComboBox<>();
        unit.getItems().addAll(
                "pcs", "bottles", "sticks",
                "count", "pack", "box",
                "g", "kg", "oz", "lb",
                "ml", "l", "gallon"
        );
        unit.setEditable(true);
        unit.getSelectionModel().selectFirst(); // default "pcs"

        ComboBox<String> location = new ComboBox<>();
        location.getItems().addAll("Pantry", "Fridge", "Freezer");
        location.getSelectionModel().selectFirst();

        // === LAYOUT ===
        GridPane gp = new GridPane();
        gp.setHgap(10);
        gp.setVgap(10);
        gp.setPadding(new Insets(10));
        gp.addRow(0, new Label("Item"),      name);
        gp.addRow(1, new Label("Quantity"),  qty);
        gp.addRow(2, new Label("Unit"),      unit);
        gp.addRow(3, new Label("Location"),  location);

        dialog.getDialogPane().setContent(gp);

        // Disable OK unless item name is non-empty
        final Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setDisable(true);
        name.textProperty().addListener((obs, oldV, newV) ->
                okBtn.setDisable(newV == null || newV.trim().isEmpty()));

        // Focus the name field when dialog shows
        dialog.setOnShown(e -> name.requestFocus());

        // === RESULT CONVERTER ===
        // B) in dialog.setResultConverter(...) attach the returned id to the item
        dialog.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                PantryItem item = new PantryItem();
                item.setName(name.getText().trim());
                item.setQty(qty.getValue());
                item.setUnit(unit.getValue());        // keep Unit in the popup
                item.setLocation(location.getValue());

                String id = saveToFirebase(item);     // write to Firestore
                item.setShoppingDocId(id);            // remember its doc id

                return item;
            }
            return null;
        });


        return dialog;
    }

    /** Save the item to Firestore under users/{uid}/shoppingList */
    // A) change saveToFirebase to RETURN the created doc id
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
