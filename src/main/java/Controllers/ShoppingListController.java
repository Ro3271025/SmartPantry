package Controllers;
import java.nio.file.Path;
import java.nio.file.Paths;
import ShoppingList.PantryItem;
import javafx.collections.ListChangeListener;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.CollectionReference;
import Firebase.FirebaseConfiguration;
import java.util.Map;
import java.util.HashMap;
import com.example.demo1.UserSession;
import javafx.application.Platform;
import java.time.ZoneId;
import java.util.concurrent.CompletableFuture;
import ShoppingList.Dialogs;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.print.PrinterJob;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton; // Added for right-click handling
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.io.File;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import javafx.scene.control.ToggleGroup;
import java.util.HashSet;
import java.util.stream.Stream;
import java.util.Comparator; // Added for sorting

// ---- Firebase / Firestore (add firebase-admin to pom.xml) ----
import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.*;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;

public class ShoppingListController extends BaseController {

    // ====== Pills ======
    @FXML
    private ToggleButton listToggle;       // renamed from allToggle
    @FXML
    private ToggleButton expiringToggle;
    @FXML
    private ToggleButton lowStockToggle;

    // ====== Filters & table ======
    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<String> locationFilter;
    @FXML
    private TableView<PantryItem> table;

    // @FXML private TableColumn<PantryItem, Boolean> selectCol; <-- DELETED

    @FXML
    private TableColumn<PantryItem, String> nameCol;
    @FXML
    private TableColumn<PantryItem, Number> qtyCol;
    @FXML
    private TableColumn<PantryItem, String> unitCol;
    @FXML
    private TableColumn<PantryItem, String> locationCol;
    @FXML
    private TableColumn<PantryItem, LocalDate> expirationCol;
    @FXML
    private TableColumn<PantryItem, Boolean> lowStockCol;

    // ====== Actions ======
    @FXML
    private Button addBtn;
    @FXML
    private Button deleteBtn;
    @FXML
    private Button ReturnDashboardBtn;

    // ====== Data sources ======
    private final ObservableList<PantryItem> shoppingList = FXCollections.observableArrayList(); // local +Add
    private final ObservableList<PantryItem> expiringSoonFirebase = FXCollections.observableArrayList(); // Firestore
    private final ObservableList<PantryItem> lowStockFirebase = FXCollections.observableArrayList(); // Firestore
    // Where we persist the local "List" view
    private final Path LOCAL_DIR  = Paths.get(System.getProperty("user.home"), ".smartpantry");
    private final Path LOCAL_JSON = LOCAL_DIR.resolve("shopping-list.json");
    // Keep user's ad-hoc “Recommended” items visible across tab switches
    private final ObservableList<PantryItem> pinnedRecommended = FXCollections.observableArrayList();
    // Fetched-from-Firebase (expiringSoonFirebase) + pinnedRecommended merged for display
    private final ObservableList<PantryItem> recommendedCombined = FXCollections.observableArrayList();

    // for JSON seeding if desired
    private final ObjectMapper mapper = new ObjectMapper();

    /** Load local "List" items from ~/.smartpantry/shopping-list.json (if present). */
    private void loadLocalList() {
        try {
            if (java.nio.file.Files.exists(LOCAL_JSON)) {
                String json = java.nio.file.Files.readString(LOCAL_JSON);
                List<PantryItemDTO> raw = mapper.readValue(json, new TypeReference<>(){});
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

    /** Merge fetched (expiringSoonFirebase) + pinnedRecommended into recommendedCombined with de-dupe. */
    private void recomputeRecommended() {
        var seen = new HashSet<String>();
        var merged = Stream.concat(expiringSoonFirebase.stream(), pinnedRecommended.stream())
                .filter(p -> seen.add(p.getName() + "|" + p.getUnit() + "|" + p.getLocation() + "|" + p.getExpiration()))
                .toList();
        recommendedCombined.setAll(merged);
    }

    /** Save local "List" items to ~/.smartpantry/shopping-list.json. */
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

    private void refreshRecommended() {
        // your existing method that fills the Recommended list
        fetchExpiringFromFirebase(true);   // or fetchRecommendedFromFirebase(true) if you renamed it
    }

    // header color helper
    private void applyHeaderClass(String cls) {
        table.getStyleClass().removeAll("thead-green", "thead-warn", "thead-danger");
        table.getStyleClass().add(cls);
    }

    /** Show/hide columns depending on the active pill. */
    private void applyModeColumns(Toggle selected) {
        // Show expiration/low stock columns ONLY if the Recommended (expiringToggle) pill is selected.
        boolean showExpiringCols = (selected == expiringToggle);

        expirationCol.setVisible(showExpiringCols);
        lowStockCol.setVisible(showExpiringCols);
    }

    @FXML
    private void ReturnDashboardBtnOnAction(ActionEvent event) throws IOException {
        switchScene(event, "PantryDashboard");
    }

    // --- Firestore path helpers (users/{id}/pantryItems) ---
    private String currentUserDocId() {
        // TODO: swap this for your session user if you have one.
        return "lewidt@farmingdale.edu";
    }

    private CollectionReference pantryColl() {
        return FirebaseConfiguration.getDatabase()
                .collection("users")
                .document(currentUserDocId())
                .collection("pantryItems");
    }

    // Decides if an item belongs in the “Expiring soon” pill
    private boolean isExpiringSoon(LocalDate d) {
        if (d == null) return false;
        LocalDate now = LocalDate.now();
        return !d.isBefore(now) && !d.isAfter(now.plusDays(7));
    }

    // Write a new (or edited) item to Firestore so it shows up in the pills
    private void upsertToFirestore(PantryItem it) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("name", it.getName());
            data.put("qty", it.getQty());                      // your readers accept qty/quantityNumeric
            data.put("unit", it.getUnit());
            data.put("location", it.getLocation());
            data.put("lowStock", it.isLowStock());
            data.put("expiration", it.getExpiration() == null ? null : it.getExpiration().toString());
            // simplest: let Firestore assign an ID
            pantryColl().add(data);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @FXML
    public void initialize() {
        // ==== 1. Table Columns Setup ====
        // selectCol.setCellValueFactory(cd -> cd.getValue().selectedProperty()); <-- DELETED
        // selectCol.setCellFactory(tc -> new CheckBoxTableCell<>()); <-- DELETED

        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        qtyCol.setCellValueFactory(new PropertyValueFactory<>("qty"));
        unitCol.setCellValueFactory(new PropertyValueFactory<>("unit"));
        locationCol.setCellValueFactory(new PropertyValueFactory<>("location"));
        expirationCol.setCellValueFactory(new PropertyValueFactory<>("expiration"));
        lowStockCol.setCellValueFactory(new PropertyValueFactory<>("lowStock"));
        lowStockCol.setCellFactory(tc -> new CheckBoxTableCell<>());

        // --- Location Column Cell Factory (Icon Fix) ---
        locationCol.setCellFactory(column -> new TableCell<PantryItem, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                } else {
                    // Use the same consistent icon for Fridge and Freezer for stable alignment
                    String icon = switch (item) {
                        case "Pantry" -> "🧺 ";
                        case "Fridge" -> "🧊 ";
                        case "Freezer" -> "🧊 ";
                        default -> "";
                    };
                    setText(icon + item);
                }
            }
        });

        // --- Name Column Cell Factory (Light Mode Selection Bug Fix) ---
        nameCol.setCellFactory(column -> new TableCell<PantryItem, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    setStyle(null);
                } else {
                    setText(item);

                    // Force text color to dark when selected to fix the glitch
                    if (getTableRow() != null && getTableRow().isSelected()) {
                        setStyle("-fx-text-fill: #333333;");
                    } else {
                        setStyle(null);
                    }
                }
            }
        });

        // ==== 2. Data Loading and Sorting ====

        // Load what we had last time
        loadLocalList();

        // Optional: seed local list from bundled JSON or fallback demo data
        try (InputStream in = getClass().getResourceAsStream("sample-data.json")) {
            if (in != null) {
                List<PantryItemDTO> raw = mapper.readValue(in, new TypeReference<>() {
                });
                for (PantryItemDTO r : raw) {
                    shoppingList.add(new PantryItem(
                            r.name, r.qty, r.unit, r.location,
                            r.expiration == null ? null : LocalDate.parse(r.expiration),
                            r.lowStock
                    ));
                }
            }
        } catch (Exception ignore) {
            // fallback demo items if resource missing
            if (shoppingList.isEmpty()) { // Only add if loading failed and list is empty
                shoppingList.addAll(
                        new PantryItem("Milk", 1, "gallon", "Fridge", LocalDate.now().plusDays(3), false),
                        new PantryItem("Eggs", 6, "count", "Fridge", LocalDate.now().plusDays(10), false),
                        new PantryItem("Apples", 5, "count", "Pantry", LocalDate.now().plusDays(7), true)
                );
            }
        }

        // --- Location Grouping Comparator and Initial Sorting ---
        Comparator<PantryItem> locationComparator = (item1, item2) -> {
            // Primary sort by location
            int locationComparison = item1.getLocation().compareTo(item2.getLocation());

            if (locationComparison != 0) {
                return locationComparison;
            }

            // Secondary sort by item name
            return item1.getName().compareTo(item2.getName());
        };

        // Apply the sort logic to the main list
        FXCollections.sort(shoppingList, locationComparator);

        // Auto-save whenever the local list changes (add/edit/delete/checkbox)
        shoppingList.addListener((ListChangeListener<PantryItem>) change -> saveLocalList());

        // Bind the table to the local list by default
        table.setItems(shoppingList);

        // Filters
        locationFilter.getItems().addAll("All locations", "Pantry", "Fridge", "Freezer");
        locationFilter.getSelectionModel().selectFirst();

        // Allow multi-row selection
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // Delete button binding logic
        var anyCheckboxChecked = Bindings.createBooleanBinding(
                () -> table.getItems() != null && table.getItems().stream().anyMatch(PantryItem::isSelected),
                table.itemsProperty()
        );

        var anyRowSelected = Bindings.isNotEmpty(table.getSelectionModel().getSelectedItems());

        deleteBtn.disableProperty().bind(anyCheckboxChecked.or(anyRowSelected).not());

        // ==== 3. Toggle/Pill Setup (Persistent Filter Logic) ====

        // Pills act like radio buttons (single click)
        ToggleGroup pills = new ToggleGroup();
        listToggle.setToggleGroup(pills);
        expiringToggle.setToggleGroup(pills);
        lowStockToggle.setToggleGroup(pills);

        if (!(listToggle.isSelected() || expiringToggle.isSelected() || lowStockToggle.isSelected())) {
            listToggle.setSelected(true);
        }

        pills.selectedToggleProperty().addListener((obs, oldT, newT) -> {
            if (newT == listToggle) {
                // When switching to List, re-sort the local list
                FXCollections.sort(shoppingList, locationComparator());
                table.setItems(shoppingList);
                applyHeaderClass("thead-green");
            } else if (newT == expiringToggle) { // "Recommended" (Expiring)
                fetchExpiringFromFirebase(false);
                table.setItems(recommendedCombined);
                applyHeaderClass("thead-warn");
            } else if (newT == lowStockToggle) { // "Low Stock"
                fetchLowStockFromFirebase(false);
                table.setItems(lowStockFirebase);
                applyHeaderClass("thead-danger");
            }

            // Apply column visibility based on the new pill selection
            applyModeColumns(newT);

            // Re-apply filter to force the table to update its display
            applyFilters();

            // Optional: Force focus to table for row header artifact fix
            Platform.runLater(table::requestFocus);
        });

        // Keep the combined Recommended list in sync automatically
        expiringSoonFirebase.addListener((ListChangeListener<PantryItem>) c -> recomputeRecommended());
        pinnedRecommended.addListener((ListChangeListener<PantryItem>) c -> recomputeRecommended());
        recomputeRecommended(); // initial compute


        // Initial header and column display
        applyHeaderClass("thead-green");
        applyModeColumns(pills.getSelectedToggle());

        // Table policies
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        // ==== 4. Set Row Factory (Context Menu Binding & Visual Grouping Separator) ====
        table.setRowFactory(tv -> {
            TableRow<PantryItem> row = new TableRow<>();

            // --- Context Menu Binding ---
            row.contextMenuProperty().bind(
                    Bindings.when(row.emptyProperty())
                            .then((ContextMenu)null)
                            .otherwise(rowMenu(row.getItem())) // Pass item for context
            );

            // Ensure right-click selects the row
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.SECONDARY) {
                    table.getSelectionModel().select(row.getIndex());
                }
            });

            // --- Visual Grouping Separator Logic (Fixed the getTableView() access) ---
            row.getStyleClass().removeAll("group-header-row");

            if (row.getItem() != null) {
                int index = row.getIndex();

                // Access the TableView items using row.getTableView().getItems()
                if (index > 0 && index < row.getTableView().getItems().size()) {
                    PantryItem previousItem = row.getTableView().getItems().get(index - 1);

                    if (!row.getItem().getLocation().equals(previousItem.getLocation())) {
                        row.getStyleClass().add("group-header-row");
                    }
                } else if (index == 0) {
                    // The very first row should always be a header
                    row.getStyleClass().add("group-header-row");
                }
            }
            return row;
        });


        // ==== 5. Final Fix: Hiding the Row Header (Aggressive Java Fix) ====
        table.skinProperty().addListener((obs, oldSkin, newSkin) -> {
            if (newSkin != null) {
                javafx.scene.Node columnHeader = table.lookup(".column-header-background");
                if (columnHeader != null) {
                    // Inject style to collapse the row-header artifact
                    columnHeader.setStyle(
                            columnHeader.getStyle() +
                                    " .row-header { -fx-size: 0; -fx-padding: 0; }"
                    );
                }
            }
        });

    } // End of initialize()

    // ===== Context Menu Helper Method =====
    private ContextMenu rowMenu(PantryItem item) {
        if (item == null) return null;

        MenuItem editItem = new MenuItem("Edit Item");

        editItem.setOnAction(event -> {
            Dialogs.editItemDialog(item).showAndWait().ifPresent(updatedItem -> {
                // Since updateFirebase is called in the Dialogs, just refresh UI
                table.refresh();
            });
        });

        ContextMenu menu = new ContextMenu();
        menu.getItems().addAll(editItem);
        return menu;
    }


    // ===== Actions =====

    @FXML
    public void handleAdd(ActionEvent e){
        Dialog<PantryItem> dialog = Dialogs.addItemDialog();
        dialog.showAndWait().ifPresent(item -> {
            // 1) Always add to local "List" and persist
            shoppingList.add(item);
            FXCollections.sort(shoppingList, locationComparator()); // Re-sort after adding
            saveLocalList();

            // 2) Also pin into Recommended so it stays visible there across tab switches
            pinnedRecommended.add(item);
            recomputeRecommended();

            // 3) If the List pill is active, refresh the table to show the new sorted item
            if (listToggle.isSelected()) {
                table.refresh();
            }
        });
    }

    private Comparator<PantryItem> locationComparator() {
        return (item1, item2) -> {
            int locationComparison = item1.getLocation().compareTo(item2.getLocation());
            if (locationComparison != 0) {
                return locationComparison;
            }
            return item1.getName().compareTo(item2.getName());
        };
    }

    @FXML
    public void handleDelete(ActionEvent e){
        ObservableList<PantryItem> current = table.getItems();
        if (current == null) return;

        var selectedRows = FXCollections.observableArrayList(
                table.getSelectionModel().getSelectedItems()
        );
        var checkboxRows = current.filtered(PantryItem::isSelected);
        if (selectedRows.isEmpty() && checkboxRows.isEmpty()) return;

        var toRemove = FXCollections.observableArrayList(selectedRows);
        for (PantryItem p : checkboxRows) if (!toRemove.contains(p)) toRemove.add(p);

        boolean onListTab = (current == shoppingList);

        // remove from UI immediately
        current.removeAll(toRemove);

        // NEW: also unpin from Recommended, then recompute the combined view
        pinnedRecommended.removeAll(toRemove);
        recomputeRecommended();

        table.getSelectionModel().clearSelection();
        table.refresh();

        if (onListTab) {
            saveLocalList();

            // Delete from Firebase (using the efficient deleteFromFirebase from Dialogs)
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                for (PantryItem p : toRemove) {
                    Dialogs.deleteFromFirebase(p);
                }
            });
        }
    }

    // [ ... existing handleExport, handleSearch, onFilterChanged methods ... ]

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

    // Keep these two wired to search / location UI
    @FXML
    public void handleSearch(ActionEvent e) {
        applyFilters();
    }

    @FXML
    public void onFilterChanged(ActionEvent e) {
        applyFilters();
    }

    // (Legacy toggle handlers not needed anymore; ToggleGroup handles switching)
    @FXML
    public void onToggleAll(ActionEvent e) { /* no-op */ }

    @FXML
    public void onToggleExpiring(ActionEvent e) { /* no-op */ }

    @FXML
    public void onToggleLowStock(ActionEvent e) { /* no-op */ }

    private void applyFilters() {
        ObservableList<PantryItem> source = table.getItems();
        if (source == null) return;

        String q = (searchField.getText() == null ? "" : searchField.getText().toLowerCase()).trim();
        String sel = locationFilter.getValue();

        // --- Filtering Logic ---
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

    // ===== Firebase wiring (Unchanged) =====
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

    private boolean loadedExpiring = false;
    private boolean loadedLow = false;

    // [ ... existing toLocalDate and fromDoc methods ... ]

    private static LocalDate toLocalDate(Object value) {
        if (value == null) return null;
        if (value instanceof com.google.cloud.Timestamp ts) {
            return ts.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        if (value instanceof java.util.Date dt) {
            return dt.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        if (value instanceof String s) {
            try { return LocalDate.parse(s); } catch (Exception ignore) { }
        }
        return null;
    }

    /** Convert a Firestore doc to your UI PantryItem, handling alternate field names. */
    private PantryItem fromDoc(DocumentSnapshot d) {
        String name = d.getString("name");

        // quantity can be "quantity", "qty", or "quantityNumeric"
        Long q = d.getLong("quantity");
        if (q == null) q = d.getLong("qty");
        if (q == null) q = d.getLong("quantityNumeric");
        int qty = q == null ? 0 : q.intValue();

        String unit = d.getString("unit");
        String loc  = d.getString("location");
        if (loc == null) loc = d.getString("category");

        // expiration may be "expiryDate" (String), "expiration" (String),
        // or "expirationDate" (Timestamp/Date)
        Object expRaw = d.get("expiryDate");
        if (expRaw == null) expRaw = d.get("expiration");
        if (expRaw == null) expRaw = d.get("expirationDate");
        LocalDate exp = toLocalDate(expRaw);

        boolean low = Boolean.TRUE.equals(d.getBoolean("lowStock")); // may be absent

        return new PantryItem(d.getId(), name, qty, unit, loc, exp, low); // Ensure doc ID is captured
    }


    /** Run a query and map to PantryItem list. */
    private List<PantryItem> runQueryToItems(Query q) throws Exception {
        return q.get().get().getDocuments().stream()
                .map(this::fromDoc)
                .collect(Collectors.toList());
    }

    /** Merge results into target list (simple append, then distinct by name+unit+location+exp). */
    private void mergeInto(ObservableList<PantryItem> target, List<PantryItem> adds) {
        target.addAll(adds);
        // de-dupe by a simple composite key
        var seen = new java.util.HashSet<String>();
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

                // A) ISO string field "expiryDate"
                Query qIso = coll.whereGreaterThanOrEqualTo("expiryDate", now.toString())
                        .whereLessThanOrEqualTo("expiryDate", in7.toString());

                // B) Alternative names (keep these in case other docs use them)
                com.google.cloud.Timestamp tsStart = com.google.cloud.Timestamp.now();
                com.google.cloud.Timestamp tsEnd   = com.google.cloud.Timestamp.of(
                        java.util.Date.from(in7.atStartOfDay(ZoneId.systemDefault()).toInstant()));
                Query qTs  = coll.whereGreaterThanOrEqualTo("expirationDate", tsStart)
                        .whereLessThanOrEqualTo("expirationDate", tsEnd);
                Query qStr = coll.whereGreaterThanOrEqualTo("expiration", now.toString())
                        .whereLessThanOrEqualTo("expiration", in7.toString());

                var items = new java.util.ArrayList<PantryItem>();
                try { items.addAll(runQueryToItems(qIso)); } catch (Exception ignore) {}
                try { items.addAll(runQueryToItems(qTs));  } catch (Exception ignore) {}
                try { items.addAll(runQueryToItems(qStr)); } catch (Exception ignore) {}

                Platform.runLater(() -> {
                    // replace the fetched list with fresh results
                    expiringSoonFirebase.setAll(items);
                    // rebuild the combined "Recommended" view so pinned items persist
                    recomputeRecommended();
                });

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }).start();
    }


    private static final int LOW_STOCK_THRESHOLD = 1; // tweak if desired

    private void fetchLowStockFromFirebase(boolean forceRefresh) {
        if (loadedLow && !forceRefresh) return;
        loadedLow = true;
        lowStockFirebase.clear();

        new Thread(() -> {
            try {
                var coll = pantryColl();

                // Prefer a boolean if present
                Query qFlag = coll.whereEqualTo("lowStock", true);

                // Or compute by quantity number (supports multiple field names)
                Query qQty1 = coll.whereLessThanOrEqualTo("quantity", LOW_STOCK_THRESHOLD);
                Query qQty2 = coll.whereLessThanOrEqualTo("qty", LOW_STOCK_THRESHOLD);
                Query qQty3 = coll.whereLessThanOrEqualTo("quantityNumeric", LOW_STOCK_THRESHOLD);

                var items = new java.util.ArrayList<PantryItem>();
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


    // ===== DTO used for optional JSON seed (Unchanged) =====
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
}

