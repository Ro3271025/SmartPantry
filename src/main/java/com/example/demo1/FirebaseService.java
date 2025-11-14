package com.example.demo1;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class FirebaseService {
    private static Firestore db = null;
    private static final String COLLECTION_NAME = "pantry_items";
    private String userId;

    /**
     * Constructor - Uses the Firestore instance from MainApplication
     */
    public FirebaseService() {
        // Get Firestore from MainApplication (initialized in main())
        this.db = MainApplication.fStore;

        // Safety check
        if (this.db == null) {
            throw new IllegalStateException("Firebase not initialized! Make sure MainApplication.fStore is set.");
        }
    }

    /**
     * Alternative constructor if you want to pass Firestore directly
     */
    public FirebaseService(Firestore firestore) {
        this.db = firestore;
    }

    /**
     * Add new pantry item to Firebase
     *
     * @param item   The PantryItem to add
     * @param userId The user's ID
     * @return The document ID of the created item
     */
    public String addPantryItem(PantryItem item, String userId) throws ExecutionException, InterruptedException {
        Map<String, Object> data = new HashMap<>();
        data.put("name", item.getName());
        data.put("quantity", item.getQuantityNumeric());
        data.put("quantityLabel", item.getQuantityLabel());
        data.put("unit", item.getUnit());
        data.put("category", item.getCategory());
        data.put("dateAdded", com.google.cloud.Timestamp.now());

        // Handle expiration date - convert to string format like your old controller
        if (item.getExpires() != null) {
            data.put("expiryDate", item.getExpires().toString());
        }

        // Add location if available
        data.put("location", "Pantry"); // Default location

        // Save to nested collection: users/{userId}/pantryItems
        ApiFuture<DocumentReference> future = db.collection("users")
                .document(userId)
                .collection("pantryItems")
                .add(data);

        DocumentReference docRef = future.get();
        System.out.println("✓ Added item to Firebase with ID: " + docRef.getId());
        return docRef.getId();
    }

    /**
     * Get all pantry items for a specific user
     *
     * @param userId The user's ID
     * @return ObservableList of PantryItems
     */
    public static ObservableList<PantryItem> getPantryItems(String userId) throws ExecutionException, InterruptedException {
        ObservableList<PantryItem> items = FXCollections.observableArrayList();

        System.out.println("📥 Loading items for user: " + userId);

        // Query nested collection: users/{userId}/pantryItems
        ApiFuture<QuerySnapshot> future = db.collection("users")
                .document(userId)
                .collection("pantryItems")
                .get();

        List<QueryDocumentSnapshot> documents = future.get().getDocuments();

        System.out.println("📊 Retrieved " + documents.size() + " documents from Firebase");

        // Convert Firebase documents to PantryItem objects
        for (DocumentSnapshot document : documents) {
            try {
                PantryItem item = new PantryItem();
                item.setId(document.getId());

                // Get fields from Firebase
                item.setName(document.getString("name"));

                // Handle quantity (could be Long or Integer from Firebase)
                Object quantityObj = document.get("quantity");
                if (quantityObj instanceof Long) {
                    item.setQuantityNumeric(((Long) quantityObj).intValue());
                } else if (quantityObj instanceof Integer) {
                    item.setQuantityNumeric((Integer) quantityObj);
                }

                item.setUnit(document.getString("unit"));
                item.setCategory(document.getString("category"));

                // Build quantity label
                String quantityLabel = item.getQuantityNumeric() + " " +
                        (item.getUnit() != null ? item.getUnit() : "");
                item.setQuantityLabel(quantityLabel);

                // Parse expiration date from string format
                String expiryDateStr = document.getString("expiryDate");
                if (expiryDateStr != null && !expiryDateStr.isEmpty()) {
                    try {
                        LocalDate expiryDate = LocalDate.parse(expiryDateStr);
                        item.setExpires(expiryDate);
                    } catch (Exception e) {
                        System.err.println("Could not parse expiry date: " + expiryDateStr);
                    }
                }

                item.setUserId(userId);

                items.add(item);
                System.out.println("  ✓ Loaded: " + item.getName() + " (" + item.getQuantityLabel() + ")");

            } catch (Exception e) {
                System.err.println("❌ Error parsing document " + document.getId() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        return items;
    }

    /**
     * Update an existing pantry item
     *
     * @param itemId The document ID
     * @param item   The updated PantryItem
     */
    public void updatePantryItem(String itemId, PantryItem item) throws ExecutionException, InterruptedException {
        if (item.getUserId() == null) {
            throw new IllegalArgumentException("Item must have a userId to update");
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("name", item.getName());
        updates.put("quantity", item.getQuantityNumeric());
        updates.put("quantityLabel", item.getQuantityLabel());
        updates.put("unit", item.getUnit());
        updates.put("category", item.getCategory());

        if (item.getExpires() != null) {
            updates.put("expiryDate", item.getExpires().toString());
        }

        // Update in nested collection
        ApiFuture<WriteResult> future = db.collection("users")
                .document(item.getUserId())
                .collection("pantryItems")
                .document(itemId)
                .update(updates);

        future.get();
        System.out.println("✓ Updated item: " + itemId);
    }

    /**
     * Delete a pantry item
     *
     * @param itemId        The document ID to delete
     * @param currentUserId
     */
    public void deletePantryItem(String itemId, String currentUserId) throws ExecutionException, InterruptedException {
        // Delete from nested collection
        ApiFuture<WriteResult> future = db.collection("users")
                .document(userId)
                .collection("pantryItems")
                .document(itemId)
                .delete();

        future.get();
        System.out.println("✓ Deleted item: " + itemId);
    }

    /**
     * Get a single pantry item by ID
     *
     * @param itemId The document ID
     * @return The PantryItem or null if not found
     */
    public PantryItem getPantryItemById(String itemId) throws ExecutionException, InterruptedException {
        ApiFuture<DocumentSnapshot> future = db.collection(COLLECTION_NAME)
                .document(itemId)
                .get();

        DocumentSnapshot document = future.get();

        if (document.exists()) {
            PantryItem item = document.toObject(PantryItem.class);
            if (item != null) {
                item.setId(document.getId());
            }
            return item;
        }

        return null;
    }

    /**
     * Check if Firebase connection is working
     *
     * @return true if connected, false otherwise
     */
    public boolean testConnection() {
        try {
            CollectionReference ref = db.collection("users");
            System.out.println("✓ Firebase connection test: SUCCESS");
            return true;
        } catch (Exception e) {
            System.err.println("❌ Firebase connection test: FAILED - " + e.getMessage());
            return false;
        }
    }
    // Recipe Methods
    public String addRecipe(RecipeItem recipe, String userId)
            throws ExecutionException, InterruptedException {

        Map<String,Object> data = new HashMap<>();
        data.put("name", recipe.getName());
        data.put("availableIngredients", recipe.getAvailableIngredients());
        data.put("missingIngredients", recipe.getMissingIngredients());
        data.put("aiTip", recipe.getAiTip());
        data.put("dateAdded", com.google.cloud.Timestamp.now());

        ApiFuture<DocumentReference> future = db.collection("users")
                .document(userId)
                .collection("recipes")
                .add(data);

        DocumentReference ref = future.get();
        System.out.println("✓ Added recipe: " + recipe.getName());
        return ref.getId();
    }

    public static ObservableList<RecipeItem> getRecipes(String userId)
            throws ExecutionException, InterruptedException {

        ObservableList<RecipeItem> list = FXCollections.observableArrayList();
        ApiFuture<QuerySnapshot> future = db.collection("users")
                .document(userId)
                .collection("recipes").get();

        for (QueryDocumentSnapshot doc : future.get().getDocuments()) {
            RecipeItem r = new RecipeItem();
            r.setId(doc.getId());
            r.setName(doc.getString("name"));
            r.setAvailableIngredients(doc.getString("availableIngredients"));
            r.setMissingIngredients(doc.getString("missingIngredients"));
            r.setAiTip(doc.getString("aiTip"));
            r.setUserId(userId);
            list.add(r);
        }
        System.out.println("✓ Loaded " + list.size() + " recipes");
        return list;
    }

    public void deleteRecipe(String recipeId, String userId)
            throws ExecutionException, InterruptedException {

        ApiFuture<WriteResult> res = db.collection("users")
                .document(userId)
                .collection("recipes")
                .document(recipeId)
                .delete();
        res.get();
        System.out.println("✓ Deleted recipe " + recipeId);
    }
}