package Controllers;

import com.example.demo1.Dialogs;
import com.example.demo1.FirebaseConfiguration;
import com.example.demo1.UserSession;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.*;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.print.PrinterJob;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TableView.TableViewSelectionModel;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Shopping List screen: now shows BOTH local ~/.smartpantry/shopping-list.json items
 * AND live Firestore items from users/{uid}/shoppingList (added by the Recipe screen).
 */
public class ShoppingListController extends BaseController {

    // ====== Pills ======
    @FXML private ToggleButton listToggle;       // "List" (local + merged remote)
    @FXML private ToggleButton expiringToggle;   // "Recommended" (expiring soon)
    @FXML private ToggleButton lowStockToggle;   // (kept for future use)

    // ====== Filters & table ======
    @FXML private TextField searchField;
    @FXML private ComboBox<String> locationFilter;
    @FXML private TableView<PantryItem> table;
    @FXML private TableColumn<PantryItem, Boolean> selectCol;
    @FXML private TableColumn<PantryItem, String>  nameCol;
    @FXML private TableColumn<PantryItem, Number>  qtyCol;
    @FXML private TableColumn<PantryItem, String>  unitCol;
    @FXML private TableColumn<PantryItem, String>  locationCol;
    @FXML private TableColumn<PantryItem, LocalDate> expirationCol;
    @FXML private TableColumn<PantryItem, Boolean> lowStockCol;

    // ====== Actions ======
    @FXML private Button addBtn;
    @FXML private Button deleteBtn;
    @FXML private Button ReturnDashboardBtn;

    // ====== Data sources ======
    private final ObservableList<PantryItem> shoppingList = FXCollections.observableArrayList();           // local + merged remote
    private final ObservableList<PantryItem> expiringSoonFirebase = FXCollections.observableArrayList();   // Firestore (recommended)
    private final ObservableList<PantryItem> lowStockFirebase = FXCollections.observableArrayList();       // Firestore (optional)
    private final ObservableList<PantryItem> pinnedRecommended = FXCollections.observableArrayList();      // user-pinned
    private final ObservableList<PantryItem> recommendedCombined = FXCollections.observableArrayList();    // expiring + pinned

    // Local persistence for List view
    private final Path LOCAL_DIR  = Paths.get(System.getProperty("user.home"), ".smartpantry");
    private final Path LOCAL_JSON = LOCAL_DIR.resolve("shopping-list.json");
    private final ObjectMapper mapper = new ObjectMapper();

    // Live updates listener for Firestore shoppingList
    private ListenerRegistration shoppingListener;

    // Flags to avoid redundant fetches
    private boolean loadedExpiring = false;
    private boolean loadedLow = false;

    // ====== Init ======
    @FXML
    public void initialize() {
        // Table columns
        selectCol.setCellValueFactory(cd -> cd.getValue().selectedProperty());
        selectCol.setCellFactory(tc -> new CheckBoxTableCell<>());
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        qtyCol.setCellValueFactory(new PropertyValueFactory<>("qty"));
        unitCol.setCellValueFactory(new PropertyValueFactory<>("unit"));
        locationCol.setCellValueFactory(new PropertyValueFactory<>("location"));
        expirationCol.setCellValueFactory(new PropertyValueFactory<>("expiration"));
        lowStockCol.setCellValueFactory(new PropertyValueFactory<>("lowStock"));
        lowStockCol.setCellFactory(tc -> new CheckBoxTableCell<>());

        // Bind table to main list
        table.setItems(shoppingList);

        // Load local items then start Firestore live sync
        loadLocalList();
        startShoppingLiveSync();   // <---- THIS makes items from Recipe screen appear here

        // If empty, you can seed demo items once
        if (shoppingList.isEmpty()) {
            shoppingList.addAll(new PantryItem("Milk", 1, "gallon", "Fridge", LocalDate.now().plusDays(3), false));
            saveLocalList();
        }

        // Auto-save local list on change
        shoppingList.addListener((ListChangeListener<PantryItem>) change -> saveLocalList());

        // Filters
        locationFilter.getItems().addAll("All locations", "Pantry", "Fridge", "Freezer");
        locationFilter.getSelectionModel().selectFirst();

        // Enable Delete if any checkbox ticked or row selected
        var anyCheckboxChecked = Bindings.createBooleanBinding(
                () -> table.getItems() != null && table.getItems().stream().anyMatch(PantryItem::isSelected),
                table.itemsProperty()
        );
        var anyRowSelected = Bindings.isNotEmpty(table.getSelectionModel().getSelectedItems());
        deleteBtn.disableProperty().bind(anyCheckboxChecked.or(anyRowSelected).not());

        // Toggle pills as a radio group
        ToggleGroup pills = new ToggleGroup();
        listToggle.setToggleGroup(pills);
        expiringToggle.setToggleGroup(pills);
        lowStockToggle.setToggleGroup(pills);
        if (!(listToggle.isSelected() || expiringToggle.isSelected() || lowStockToggle.isSelected())) {
            listToggle.setSelected(true);
        }

        // Switch dataset + header color per pill
        pills.selectedToggleProperty().addListener((obs, oldT, newT) -> {
            if (newT == listToggle) {
                table.setItems(shoppingList);
                applyHeaderClass("thead-green");
            } else if (newT == expiringToggle) { // Recommended (expiring soon)
                fetchExpiringFromFirebase(false);
                table.setItems(recommendedCombined);
                applyHeaderClass("thead-warn");
            } else if (newT == lowStockToggle) {
                fetchLowStockFromFirebase(false);
                table.setItems(lowStockFirebase);
                applyHeaderClass("thead-danger");
            }
            applyModeColumns(newT);
            table.refresh();
        });

        // Keep combined recommended up-to-date
        expiringSoonFirebase.addListener((ListChangeListener<PantryItem>) c -> recomputeRecommended());
        pinnedRecommended.addListener((ListChangeListener<PantryItem>) c -> recomputeRecommended());
        recomputeRecommended();

        // Initial header + columns
        applyHeaderClass("thead-green");
        applyModeColumns(pills.getSelectedToggle());

        // Table policies
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    }

    // ===== Navigation =====
    @FXML
    private void ReturnDashboardBtnOnAction(ActionEvent event) throws java.io.IOException {
        switchScene(event, "PantryDashboard");
    }

    // ===== Helpers: user / paths =====
    private String currentUserDocId() {
        String uid = UserSession.getCurrentUserId();
        return (uid == null || uid.isBlank()) ? "debug-local" : uid;
    }

    private CollectionReference pantryColl() {
        return FirebaseConfiguration.getDatabase()
                .collection("users")
                .document(currentUserDocId())
                .collection("pantryItems");
    }

    // ===== Local JSON persistence =====
    private void loadLocalList() {
        try {
            if (java.nio.file.Files.exists(LOCAL_JSON)) {
                String json = java.nio.file.Files.readString(LOCAL_JSON);
                List<PantryItemDTO> raw = mapper.readValue(json, new TypeReference<>() {});
                shoppingList.setAll(raw.stream().map(r ->
                        new PantryItem(
                                r.name,
                                r.qty,
                                r.unit,
                                r.location,
                                r.expiration == null ? null : LocalDate.parse(r.expiration),
                                r.lowStock
                        )
                ).toList());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void saveLocalList() {
        try {
            if (!java.nio.file.Files.exists(LOCAL_DIR)) {
                java.nio.file.Files.createDirectories(LOCAL_DIR);
            }
            var out = shoppingList.stream().map(PantryItemDTO::from).collect(Collectors.toList());
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(out);
            java.nio.file.Files.writeString(LOCAL_JSON, json);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // ===== Recommended (expiring soon) composition =====
    private void recomputeRecommended() {
        var seen = new HashSet<String>();
        var merged = Stream.concat(expiringSoonFirebase.stream(), pinnedRecommended.stream())
                .filter(p -> seen.add(p.getName() + "|" + p.getUnit() + "|" + p.getLocation() + "|" + p.getExpiration()))
                .toList();
        recommendedCombined.setAll(merged);
    }

    // ===== Table cosmetics =====
    private void applyHeaderClass(String cls) {
        table.getStyleClass().removeAll("thead-green", "thead-warn", "thead-danger");
        table.getStyleClass().add(cls);
    }

    private void applyModeColumns(Toggle selected) {
        boolean showRecommendedCols = (selected == expiringToggle);
        expirationCol.setVisible(showRecommendedCols);
        lowStockCol.setVisible(showRecommendedCols);
    }

    // ===== Actions =====
    @FXML
    public void handleAdd(ActionEvent e) {
        Dialog<PantryItem> dialog = Dialogs.addItemDialog();
        dialog.showAndWait().ifPresent(item -> {
            // 1) Always add to local "List" and persist
            shoppingList.add(item);
            saveLocalList();

            // 2) Also pin into Recommended so it stays visible there across tab switches
            pinnedRecommended.add(item);
            recomputeRecommended();

            // 3) If the Recommended pill is active, ensure the table shows the combined list
            if (expiringToggle.isSelected()) {
                table.setItems(recommendedCombined);
                table.refresh();
            }
        });
    }

    @FXML
    public void handleDelete(ActionEvent e){
        ObservableList<PantryItem> current = table.getItems();
        if (current == null) return;

        var selectedRows = FXCollections.observableArrayList(table.getSelectionModel().getSelectedItems());
        var checkboxRows = current.filtered(PantryItem::isSelected);
        if (selectedRows.isEmpty() && checkboxRows.isEmpty()) return;

        var toRemove = FXCollections.observableArrayList(selectedRows);
        for (PantryItem p : checkboxRows) if (!toRemove.contains(p)) toRemove.add(p);

        boolean onListTab = (current == shoppingList);

        // remove from UI immediately
        current.removeAll(toRemove);

        // unpin from Recommended, then recompute
        pinnedRecommended.removeAll(toRemove);
        recomputeRecommended();

        table.getSelectionModel().clearSelection();
        table.refresh();

        if (onListTab) {
            saveLocalList();
            // also delete from Firestore if IDs are known
            String uid = currentUserDocId();
            if (!"debug-local".equals(uid)) {
                var db = FirebaseConfiguration.getDatabase();
                var coll = db.collection("users").document(uid).collection("shoppingList");
                CompletableFuture.runAsync(() -> {
                    for (PantryItem p : toRemove) {
                        try {
                            String id = p.getShoppingDocId();
                            if (id != null && !id.isBlank()) {
                                coll.document(id).delete().get();
                            } else {
                                // fallback match by fields if no id
                                var q = coll.whereEqualTo("name", p.getName())
                                        .whereEqualTo("quantity", p.getQty())
                                        .whereEqualTo("unit", p.getUnit())
                                        .whereEqualTo("location", p.getLocation());
                                var snap = q.get().get();
                                for (var d : snap.getDocuments()) d.getReference().delete().get();
                            }
                        } catch (Exception ex) {
                            System.err.println("⚠ Delete failed: " + ex.getMessage());
                        }
                    }
                });
            }
        }
    }

    @FXML
    public void handleExport(ActionEvent e) {
        try {
            PrinterJob job = PrinterJob.createPrinterJob();
            if (job == null) return;

            boolean proceed = job.showPrintDialog(table.getScene().getWindow());
            if (!proceed) return;

            double pw = job.getJobSettings().getPageLayout().getPrintableWidth();
            double ph = job.getJobSettings().getPageLayout().getPrintableHeight();
            double sx = pw / table.getWidth();
            double sy = ph / table.getHeight();
            double scale = Math.min(1.0, Math.min(sx, sy));  // do not upscale

            table.setScaleX(scale);
            table.setScaleY(scale);

            boolean ok = job.printPage(table);

            table.setScaleX(1);
            table.setScaleY(1);

            if (ok) job.endJob();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // Wire these to search/location controls
    @FXML public void handleSearch(ActionEvent e) { applyFilters(); }
    @FXML public void onFilterChanged(ActionEvent e) { applyFilters(); }
    @FXML public void onToggleAll(ActionEvent e) { /* no-op */ }
    @FXML public void onToggleExpiring(ActionEvent e) { /* no-op */ }
    @FXML public void onToggleLowStock(ActionEvent e) { /* no-op */ }

    private void applyFilters() {
        ObservableList<PantryItem> source = table.getItems();
        if (source == null) return;

        String q = (searchField.getText() == null ? "" : searchField.getText().toLowerCase()).trim();
        String sel = locationFilter.getValue();

        table.setItems(source.filtered(it -> {
            boolean matchesText = q.isBlank() ||
                    (it.getName() != null && it.getName().toLowerCase().contains(q));

            boolean matchesLocation = true;
            if (sel != null && !"All locations".equals(sel)) {
                matchesLocation = sel.equals(it.getLocation());
            }

            return matchesText && matchesLocation;
        }));
    }

    // ===== Firestore live sync for users/{uid}/shoppingList =====

    /** Start listening to users/{uid}/shoppingList for live updates. */
    private void startShoppingLiveSync() {
        String uid = currentUserDocId();
        if ("debug-local".equals(uid)) return; // no user yet

        Firestore db = FirebaseConfiguration.getDatabase();
        CollectionReference listRef = db.collection("users").document(uid).collection("shoppingList");
        stopShoppingLiveSync(); // remove existing

        shoppingListener = listRef.addSnapshotListener((snap, err) -> {
            if (err != null || snap == null) return;
            List<PantryItem> remote = snap.getDocuments().stream()
                    .map(this::fromShoppingDoc)
                    .collect(Collectors.toList());
            Platform.runLater(() -> {
                mergeRemoteShopping(remote);
                table.refresh();
            });
        });
    }

    /** Stop the listener (call on screen close if you have a lifecycle hook). */
    private void stopShoppingLiveSync() {
        if (shoppingListener != null) {
            try { shoppingListener.remove(); } catch (Exception ignored) {}
            shoppingListener = null;
        }
    }

    /** Convert a Firestore doc in shoppingList -> PantryItem (handles multiple schemas). */
    private PantryItem fromShoppingDoc(DocumentSnapshot d) {
        String name = Optional.ofNullable(d.getString("name"))
                .orElse(Optional.ofNullable(d.getString("itemName")).orElse("Unnamed"));

        Long q = Optional.ofNullable(d.getLong("quantity"))
                .orElse(Optional.ofNullable(d.getLong("qty"))
                        .orElse(d.getLong("quantityNumeric")));
        int qty = q == null ? 1 : q.intValue();

        String unit = Optional.ofNullable(d.getString("unit")).orElse("");
        String loc  = Optional.ofNullable(d.getString("location"))
                .orElse(Optional.ofNullable(d.getString("category")).orElse("General"));

        Object expRaw = d.get("expirationDate");
        if (expRaw == null) expRaw = d.get("expiration");
        if (expRaw == null) expRaw = d.get("expiryDate");

        LocalDate exp = null;
        if (expRaw instanceof com.google.cloud.Timestamp ts) {
            exp = ts.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        } else if (expRaw instanceof java.util.Date dt) {
            exp = dt.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        } else if (expRaw instanceof String s) {
            try { exp = LocalDate.parse(s); } catch (Exception ignored) {}
        }

        boolean low = Boolean.TRUE.equals(d.getBoolean("lowStock"));

        PantryItem it = new PantryItem(name, qty, unit, loc, exp, low);
        try {
            var m = PantryItem.class.getMethod("setShoppingDocId", String.class);
            m.invoke(it, d.getId());
        } catch (Exception ignored) { /* method may not exist */ }

        return it;
    }

    /** Merge remote Firestore items into the main shoppingList, preserving unique rows. */
    private void mergeRemoteShopping(List<PantryItem> remote) {
        var seen = new HashSet<String>();
        var merged = new ArrayList<PantryItem>();

        // Prefer remote rows (cloud is source of truth)
        for (PantryItem r : remote) {
            String k = (r.getName() + "|" + r.getUnit() + "|" + r.getLocation()).toLowerCase();
            if (seen.add(k)) merged.add(r);
        }

        // Keep local-only rows that aren't present in remote
        for (PantryItem l : shoppingList) {
            String k = (l.getName() + "|" + l.getUnit() + "|" + l.getLocation()).toLowerCase();
            if (!seen.contains(k)) merged.add(l);
        }

        shoppingList.setAll(merged);
    }

    // ===== Firebase "pantryItems" helpers (Recommended / Expiring) =====

    private boolean isExpiringSoon(LocalDate d) {
        if (d == null) return false;
        LocalDate now = LocalDate.now();
        return !d.isBefore(now) && !d.isAfter(now.plusDays(7));
    }

    private PantryItem fromDoc(QueryDocumentSnapshot d) {
        String name = d.getString("name");
        Long qtyL = d.getLong("qty");
        if (qtyL == null) qtyL = d.getLong("quantityNumeric");
        int qty = qtyL == null ? 0 : qtyL.intValue();
        String unit = d.getString("unit");
        String loc = Optional.ofNullable(d.getString("location")).orElse(d.getString("category"));
        boolean low = Boolean.TRUE.equals(d.getBoolean("lowStock"));

        LocalDate expDate = null;
        Object expRaw = d.get("expirationDate");
        if (expRaw == null) expRaw = d.get("expiration");
        if (expRaw instanceof com.google.cloud.Timestamp ts) {
            expDate = ts.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        } else if (expRaw instanceof java.util.Date dt) {
            expDate = dt.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        } else if (expRaw instanceof String s) {
            try { expDate = LocalDate.parse(s); } catch (Exception ignored) {}
        }

        return new PantryItem(name, qty, unit, loc, expDate, low);
    }

    private static LocalDate toLocalDate(Object value) {
        if (value == null) return null;
        if (value instanceof com.google.cloud.Timestamp ts) {
            return ts.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        if (value instanceof java.util.Date dt) {
            return dt.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        if (value instanceof String s) {
            try { return LocalDate.parse(s); } catch (Exception ignore) {}
        }
        return null;
    }

    private PantryItem fromDoc(DocumentSnapshot d) {
        String name = d.getString("name");
        Long q = d.getLong("quantity");
        if (q == null) q = d.getLong("qty");
        if (q == null) q = d.getLong("quantityNumeric");
        int qty = q == null ? 0 : q.intValue();
        String unit = d.getString("unit");
        String loc  = Optional.ofNullable(d.getString("location")).orElse(d.getString("category"));
        Object expRaw = Optional.ofNullable(d.get("expiryDate"))
                .orElse(Optional.ofNullable(d.get("expiration"))
                        .orElse(d.get("expirationDate")));
        LocalDate exp = toLocalDate(expRaw);
        boolean low = Boolean.TRUE.equals(d.getBoolean("lowStock"));
        return new PantryItem(name, qty, unit, loc, exp, low);
    }

    private List<PantryItem> runQueryToItems(Query q) throws Exception {
        return q.get().get().getDocuments().stream().map(this::fromDoc).collect(Collectors.toList());
    }

    private void mergeInto(ObservableList<PantryItem> target, List<PantryItem> adds) {
        target.addAll(adds);
        var seen = new HashSet<String>();
        var deduped = target.stream().filter(p -> {
            String k = (p.getName() + "|" + p.getUnit() + "|" + p.getLocation() + "|" + p.getExpiration());
            return seen.add(k);
        }).collect(Collectors.toList());
        target.setAll(deduped);
    }

    private void fetchExpiringFromFirebase(boolean forceRefresh) {
        if (loadedExpiring && !forceRefresh) return;
        loadedExpiring = true;
        expiringSoonFirebase.clear();

        new Thread(() -> {
            try {
                var coll = pantryColl();
                LocalDate now = LocalDate.now();
                LocalDate in7 = now.plusDays(7);

                Query qIso = coll.whereGreaterThanOrEqualTo("expiryDate", now.toString())
                        .whereLessThanOrEqualTo("expiryDate", in7.toString());

                com.google.cloud.Timestamp tsStart = com.google.cloud.Timestamp.now();
                com.google.cloud.Timestamp tsEnd   = com.google.cloud.Timestamp.of(
                        java.util.Date.from(in7.atStartOfDay(ZoneId.systemDefault()).toInstant()));
                Query qTs  = coll.whereGreaterThanOrEqualTo("expirationDate", tsStart)
                        .whereLessThanOrEqualTo("expirationDate", tsEnd);

                Query qStr = coll.whereGreaterThanOrEqualTo("expiration", now.toString())
                        .whereLessThanOrEqualTo("expiration", in7.toString());

                var items = new ArrayList<PantryItem>();
                try { items.addAll(runQueryToItems(qIso)); } catch (Exception ignore) {}
                try { items.addAll(runQueryToItems(qTs));  } catch (Exception ignore) {}
                try { items.addAll(runQueryToItems(qStr)); } catch (Exception ignore) {}

                Platform.runLater(() -> {
                    expiringSoonFirebase.setAll(items);
                    recomputeRecommended();
                });

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }).start();
    }

    private static final int LOW_STOCK_THRESHOLD = 1;

    private void fetchLowStockFromFirebase(boolean forceRefresh) {
        if (loadedLow && !forceRefresh) return;
        loadedLow = true;
        lowStockFirebase.clear();

        new Thread(() -> {
            try {
                var coll = pantryColl();
                Query qFlag = coll.whereEqualTo("lowStock", true);
                Query qQty1 = coll.whereLessThanOrEqualTo("quantity", LOW_STOCK_THRESHOLD);
                Query qQty2 = coll.whereLessThanOrEqualTo("qty", LOW_STOCK_THRESHOLD);
                Query qQty3 = coll.whereLessThanOrEqualTo("quantityNumeric", LOW_STOCK_THRESHOLD);

                var items = new ArrayList<PantryItem>();
                try { items.addAll(runQueryToItems(qFlag)); }  catch (Exception ignore) {}
                try { items.addAll(runQueryToItems(qQty1)); }  catch (Exception ignore) {}
                try { items.addAll(runQueryToItems(qQty2)); }  catch (Exception ignore) {}
                try { items.addAll(runQueryToItems(qQty3)); }  catch (Exception ignore) {}

                Platform.runLater(() -> mergeInto(lowStockFirebase, items));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }).start();
    }

    // ===== DTO for local JSON =====
    public static class PantryItemDTO {
        public String name;
        public int qty;
        public String unit;
        public String location;
        public String expiration;
        public boolean lowStock;

        public static PantryItemDTO from(PantryItem it) {
            PantryItemDTO d = new PantryItemDTO();
            d.name = it.getName();
            d.qty = it.getQty();
            d.unit = it.getUnit();
            d.location = it.getLocation();
            d.expiration = it.getExpiration() == null ? null : it.getExpiration().toString();
            d.lowStock = it.isLowStock();
            return d;
        }
    }

    // ===== Optional: Firestore bootstrap if you need it here (not used if FirebaseConfiguration already did) =====
    private static class FirebaseClient {
        private static volatile boolean inited = false;
        static Firestore db() {
            if (!inited) {
                try (InputStream in = ShoppingListController.class.getResourceAsStream("/firebase-service-account.json")) {
                    FirebaseOptions opts = FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.fromStream(in))
                            .build();
                    FirebaseApp.initializeApp(opts);
                    inited = true;
                } catch (Exception e) {
                    throw new RuntimeException("Firebase init failed", e);
                }
            }
            return FirestoreClient.getFirestore();
        }
    }
}
