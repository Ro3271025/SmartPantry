package com.example.demo1;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class FirebaseService {
    private static Firestore db = null;
    private static final String COLLECTION_NAME = "pantry_items";

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
     * @param item The PantryItem to add
     * @param userId The user's ID
     * @return The document ID of the created item
     */
    public String addPantryItem(PantryItem item, String userId) throws ExecutionException, InterruptedException {
        Map<String, Object> data = new HashMap<>();
        data.put("name", item.getName());
        data.put("quantity", item.getQuantity());
        data.put("unit", item.getUnit());
        data.put("expirationDate", item.getExpirationDate());
        data.put("category", item.getCategory());
        data.put("userId", userId);
        data.put("dateAdded", item.getDateAdded());

        ApiFuture<DocumentReference> future = db.collection(COLLECTION_NAME).add(data);
        DocumentReference docRef = future.get();

        System.out.println("Added item with ID: " + docRef.getId());
        return docRef.getId();
    }

    /**
     * Get all pantry items for a specific user
     * @param userId The user's ID
     * @return ObservableList of PantryItems
     */
    public static ObservableList<PantryItem> getPantryItems(String userId) throws ExecutionException, InterruptedException {
        ObservableList<PantryItem> items = FXCollections.observableArrayList();

        // Query Firestore for items belonging to this user
        ApiFuture<QuerySnapshot> future = db.collection(COLLECTION_NAME)
                .whereEqualTo("userId", userId)
                .get();

        // Wait for query to complete
        List<QueryDocumentSnapshot> documents = future.get().getDocuments();

        System.out.println("Retrieved " + documents.size() + " documents from Firebase");

        // Convert Firestore documents to PantryItem objects
        for (DocumentSnapshot document : documents) {
            try {
                PantryItem item = document.toObject(PantryItem.class);
                if (item != null) {
                    item.setId(document.getId());
                    items.add(item);
                }
            } catch (Exception e) {
                System.err.println("Error parsing document " + document.getId() + ": " + e.getMessage());
            }
        }

        return items;
    }

    /**
     * Update an existing pantry item
     * @param itemId The document ID
     * @param item The updated PantryItem
     */
    public void updatePantryItem(String itemId, PantryItem item) throws ExecutionException, InterruptedException {
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", item.getName());
        updates.put("quantity", item.getQuantity());
        updates.put("unit", item.getUnit());
        updates.put("expirationDate", item.getExpirationDate());
        updates.put("category", item.getCategory());

        ApiFuture<WriteResult> future = db.collection(COLLECTION_NAME)
                .document(itemId)
                .update(updates);

        future.get();
        System.out.println("Updated item: " + itemId);
    }

    /**
     * Delete a pantry item
     * @param itemId The document ID to delete
     */
    public void deletePantryItem(String itemId) throws ExecutionException, InterruptedException {
        ApiFuture<WriteResult> future = db.collection(COLLECTION_NAME)
                .document(itemId)
                .delete();

        future.get();
        System.out.println("Deleted item: " + itemId);
    }

    /**
     * Get a single pantry item by ID
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
     * @return true if connected, false otherwise
     */
    public boolean testConnection() {
        try {
            // Try to get a reference to the collection
            CollectionReference ref = db.collection(COLLECTION_NAME);
            System.out.println("Firebase connection test: SUCCESS");
            return true;
        } catch (Exception e) {
            System.err.println("Firebase connection test: FAILED - " + e.getMessage());
            return false;
        }
    }
}