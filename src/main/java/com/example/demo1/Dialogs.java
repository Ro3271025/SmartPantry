package com.example.demo1;

import Controllers.PantryItem;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import Controllers.PantryItem;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.Timestamp;
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

        ComboBox<String> location = new ComboBox<>();
        location.getItems().addAll("Pantry", "Fridge", "Freezer");
        location.getSelectionModel().selectFirst();

        // === LAYOUT ===
        GridPane gp = new GridPane();
        gp.setHgap(10);
        gp.setVgap(10);
        gp.setPadding(new Insets(10));
        gp.addRow(0, new Label("Item"), name);
        gp.addRow(1, new Label("Quantity"), qty);
        gp.addRow(2, new Label("Location"), location);

        dialog.getDialogPane().setContent(gp);

        // === RESULT CONVERTER ===
        dialog.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                PantryItem item = new PantryItem();
                item.setName(name.getText().trim());
                item.setQty(qty.getValue());
                item.setUnit(""); // not needed here
                item.setLocation(location.getValue());

                // ✅ Save the item to Firestore
                saveToFirebase(item);

                return item;
            }
            return null;
        });

        return dialog;
    }

    /** ✅ Actually save to Firebase */
    private static void saveToFirebase(PantryItem item) {
        try {
            String uid = UserSession.getCurrentUserId();
            if (uid == null || uid.isBlank()) {
                System.err.println("⚠ Cannot save item — no logged-in user found.");
                return;
            }

            Firestore db = FirebaseConfiguration.getDatabase();
            CollectionReference shoppingListRef = db.collection("users")
                    .document(uid)
                    .collection("shoppingList");

            Map<String, Object> data = new HashMap<>();
            data.put("item", item.getName());
            data.put("quantity", item.getQty());
            data.put("location", item.getLocation());
            data.put("createdAt", Timestamp.now());

            shoppingListRef.add(data)
                    .get(); // wait for completion before continuing

            System.out.println("✅ Saved to Firebase shoppingList: " + item.getName());

        } catch (Exception e) {
            System.err.println("❌ Firebase save error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
