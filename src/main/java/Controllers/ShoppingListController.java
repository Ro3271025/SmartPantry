package Controllers;

import com.example.demo1.Dialogs;
import com.example.demo1.FirebaseConfiguration;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.*;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.print.PrinterJob;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
    private TableColumn<PantryItem, Boolean> selectCol;
    @FXML
    private TableView<PantryItem> table;
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
    private final Path LOCAL_DIR  = Paths.get(System.getProperty("user.home"), ".smartpantry");
    private final Path LOCAL_JSON = LOCAL_DIR.resolve("shopping-list.json");
    private final ObservableList<PantryItem> pinnedRecommended = FXCollections.observableArrayList();
    private final ObservableList<PantryItem> recommendedCombined = FXCollections.observableArrayList();
    private final Path RECO_JSON = LOCAL_DIR.resolve("recommended-pinned.json");
    private final BooleanProperty darkMode = new SimpleBooleanProperty(true); // start dark; set false if you prefer
    private final Preferences prefs = Preferences.userNodeForPackage(ShoppingListController.class);
    private static final String PREF_DARK = "shopping.dark";

    // for JSON seeding
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

    /** Load pinned Recommended rows from ~/.smartpantry/recommended-pinned.json */
    private void loadPinnedRecommended() {
        try {
            if (java.nio.file.Files.exists(RECO_JSON)) {
                String json = java.nio.file.Files.readString(RECO_JSON);
                List<PantryItemDTO> raw = mapper.readValue(json, new TypeReference<>(){});
                pinnedRecommended.setAll(
                        raw.stream().map(r -> new PantryItem(
                                r.name,
                                r.qty,
                                r.unit,
                                r.location,
                                r.expiration == null ? null : LocalDate.parse(r.expiration),
                                r.lowStock
                        )).toList()
                );
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /** Save pinned Recommended rows to ~/.smartpantry/recommended-pinned.json */
    private void savePinnedRecommended() {
        try {
            if (!java.nio.file.Files.exists(LOCAL_DIR)) {
                java.nio.file.Files.createDirectories(LOCAL_DIR);
            }
            var out = pinnedRecommended.stream().map(PantryItemDTO::from).collect(Collectors.toList());
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(out);
            java.nio.file.Files.writeString(RECO_JSON, json);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    private Button findExportButton(Parent root) {
        if (root instanceof Button b && "Export".equalsIgnoreCase(b.getText())) return b;
        if (root instanceof javafx.scene.Parent p) {
            for (javafx.scene.Node n : p.getChildrenUnmodifiable()) {
                if (n instanceof Button b && "Export".equalsIgnoreCase(b.getText())) return b;
                if (n instanceof Parent child) {
                    Button found = findExportButton(child);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }

    private void refreshRecommended() {
        fetchExpiringFromFirebase(true);
    }

    private void applyHeaderClass(String cls) {
        table.getStyleClass().removeAll("thead-green", "thead-warn", "thead-danger");
        table.getStyleClass().add(cls);
    }
    /** Show/hide columns depending on the active pill. */

    /** Show/hide columns depending on the active pill. */
    private void applyModeColumns(Toggle selected) {
        boolean hideRecommendedCols = (selected == expiringToggle);

        boolean showExpirationAndLowStock = (selected == lowStockToggle);

        expirationCol.setVisible(showExpirationAndLowStock);
        lowStockCol.setVisible(showExpirationAndLowStock);
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
    private boolean isExpiringSoon(LocalDate d) {
        if (d == null) return false;
        LocalDate now = LocalDate.now();
        return !d.isBefore(now) && !d.isAfter(now.plusDays(7));
    }
    private void upsertToFirestore(PantryItem it) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("name", it.getName());
            data.put("qty", it.getQty());                      // your readers accept qty/quantityNumeric
            data.put("unit", it.getUnit());
            data.put("location", it.getLocation());
            data.put("lowStock", it.isLowStock());
            data.put("expiration", it.getExpiration() == null ? null : it.getExpiration().toString());
            pantryColl().add(data);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @FXML
    public void initialize() {
        // ==== Table columns ====
        selectCol.setCellValueFactory(cd -> cd.getValue().selectedProperty());
        selectCol.setCellFactory(tc -> new CheckBoxTableCell<>());
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        qtyCol.setCellValueFactory(new PropertyValueFactory<>("qty"));
        unitCol.setCellValueFactory(new PropertyValueFactory<>("unit"));
        locationCol.setCellValueFactory(new PropertyValueFactory<>("location"));
        expirationCol.setCellValueFactory(new PropertyValueFactory<>("expiration"));
        lowStockCol.setCellValueFactory(new PropertyValueFactory<>("lowStock"));
        lowStockCol.setCellFactory(tc -> new CheckBoxTableCell<>());
        locationCol.setCellFactory(column -> new TableCell<PantryItem, String>() {


            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
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

        MenuItem editItem = new MenuItem("Edit Item");

        editItem.setOnAction(event -> {
            PantryItem selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                System.out.println("Editing: " + selected.getName());
            } else {

            }
        });

        ContextMenu rowMenu = new ContextMenu();
        rowMenu.getItems().addAll(editItem);

        table.setRowFactory(tv -> {
            TableRow<PantryItem> row = new TableRow<>();

            // Bind the context menu to the row. The menu only appears if the row is NOT empty.
            row.contextMenuProperty().bind(
                    Bindings.when(row.emptyProperty())
                            .then((ContextMenu)null)
                            .otherwise(rowMenu)
            );

            // Ensure right-click selects the row before showing the menu
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                    table.getSelectionModel().select(row.getIndex());
                }
            });

            return row;
        });

        editItem.setOnAction(event -> {
            PantryItem selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                // CALL THE NEW DIALOG
                Dialogs.editItemDialog(selected).showAndWait().ifPresent(updatedItem -> {

                    table.refresh();

                    saveLocalList();

                    savePinnedRecommended();
                });
            }
        });
// Bind the table to the local list by default
        table.setItems(shoppingList);

        loadLocalList();
        loadPinnedRecommended();
        recomputeRecommended();


        if (shoppingList.isEmpty()) {
            shoppingList.addAll(
                    new PantryItem("Milk", 1, "gallon", "Fridge",  LocalDate.now().plusDays(3),  false)
            );
            // Save initial demo once so it persists
            saveLocalList();
        }

// Auto-save whenever the local list changes (add/edit/delete/checkbox)
        shoppingList.addListener((ListChangeListener<PantryItem>) change -> saveLocalList());

        table.setItems(shoppingList);

        // Filters
        locationFilter.getItems().addAll("All locations", "Pantry", "Fridge", "Freezer");
        locationFilter.getSelectionModel().selectFirst();

        // Enable Delete only if anything selected in current view
        var anySelected = Bindings.createBooleanBinding(
                () -> table.getItems().stream().anyMatch(PantryItem::isSelected),
                table.itemsProperty()
        );
        // allow multi-row selection
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        var anyCheckboxChecked = Bindings.createBooleanBinding(
                () -> table.getItems() != null && table.getItems().stream().anyMatch(PantryItem::isSelected),
                table.itemsProperty()
        );

        var anyRowSelected = Bindings.isNotEmpty(table.getSelectionModel().getSelectedItems());

        deleteBtn.disableProperty().bind(anyCheckboxChecked.or(anyRowSelected).not());


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
            shoppingList.addAll(
                    new PantryItem("Milk", 1, "gallon", "Fridge", LocalDate.now().plusDays(3), false),
                    new PantryItem("Eggs", 6, "count", "Fridge", LocalDate.now().plusDays(10), false),
                    new PantryItem("Apples", 5, "count", "Pantry", LocalDate.now().plusDays(7), true)
            );
        }

        ToggleGroup pills = new ToggleGroup();
        listToggle.setToggleGroup(pills);
        expiringToggle.setToggleGroup(pills);
        lowStockToggle.setToggleGroup(pills);

        if (!(listToggle.isSelected() || expiringToggle.isSelected() || lowStockToggle.isSelected())) {
            listToggle.setSelected(true);
        }

        // Switch dataset + header color
        pills.selectedToggleProperty().addListener((obs, oldT, newT) -> {
            if (newT == listToggle) {
                table.setItems(shoppingList);
                applyHeaderClass("thead-green");
            } else if (newT == expiringToggle) { // "Recommended"
                fetchExpiringFromFirebase(false);
                table.setItems(recommendedCombined);
                applyHeaderClass("thead-warn");
            }
            applyModeColumns(newT);
            table.refresh();
        });

// Keep the combined Recommended list in sync automatically
        expiringSoonFirebase.addListener((ListChangeListener<PantryItem>) c -> recomputeRecommended());
        pinnedRecommended.addListener((ListChangeListener<PantryItem>) c -> recomputeRecommended());
        recomputeRecommended(); // initial compute
        savePinnedRecommended();

        // Initial header
        applyHeaderClass("thead-green");
        applyModeColumns(pills.getSelectedToggle());


        // Table policies
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        Platform.runLater(() -> {
            Stage stage = (Stage) table.getScene().getWindow();
            stage.setWidth(1200);
            stage.setHeight(800);
            stage.setMinWidth(300);
            stage.setMinHeight(400);
            stage.centerOnScreen();
        });
        // ----- Theme toggle -----
        Platform.runLater(() -> {
            if (table.getScene() == null) return;

            final String DARK = "shopping-dark";

            Scene  scene = table.getScene();
            Parent root  = scene.getRoot();

            // Tiny round button near Export
            Button themeBtn = new Button();
            themeBtn.getStyleClass().add("theme-toggle");
            themeBtn.setFocusTraversable(false);
            themeBtn.setTooltip(new Tooltip("Toggle theme"));

            Button export = findExportButton(root);
            boolean placed = false;
            if (export != null && export.getParent() instanceof javafx.scene.layout.Pane p) {
                int idx = Math.max(0, p.getChildren().indexOf(export) + 1);
                p.getChildren().add(Math.min(idx, p.getChildren().size()), themeBtn);
                var m = new javafx.geometry.Insets(0, 0, 0, 6);
                try { javafx.scene.layout.HBox.setMargin(themeBtn, m); } catch (Throwable ignore) {}
                try { javafx.scene.layout.FlowPane.setMargin(themeBtn, m); } catch (Throwable ignore) {}
                placed = true;
            }
            if (!placed && root instanceof javafx.scene.layout.Pane rp) {
                themeBtn.setManaged(false);
                rp.getChildren().add(themeBtn);
                double pad = 10;
                Runnable place = () -> themeBtn.relocate(
                        rp.getWidth() - themeBtn.getWidth() - pad,
                        pad
                );
                rp.widthProperty().addListener((o, ov, nv) -> place.run());
                themeBtn.widthProperty().addListener((o, ov, nv) -> place.run());
                place.run();
            }

            var prefs = java.util.prefs.Preferences.userNodeForPackage(ShoppingListController.class);
            final String PREF_DARK = "shopping.dark";
            var darkMode = new javafx.beans.property.SimpleBooleanProperty(
                    prefs.getBoolean(PREF_DARK, false) // false => light (default styling)
            );

            // Apply: add/remove ONLY the dark class
            Runnable applyTheme = () -> {
                var classes = root.getStyleClass();
                if (darkMode.get()) {
                    if (!classes.contains(DARK)) classes.add(DARK);
                    themeBtn.setText("☀"); // shows sun when you’re in dark mode (click to go light)
                    themeBtn.setTooltip(new Tooltip("Switch to light mode"));
                } else {
                    classes.remove(DARK);  // light = default styles
                    themeBtn.setText("🌙");
                    themeBtn.setTooltip(new Tooltip("Switch to dark mode"));
                }
            };

            applyTheme.run();

            themeBtn.setOnAction(e -> darkMode.set(!darkMode.get()));
            darkMode.addListener((obs, o, n) -> {
                prefs.putBoolean(PREF_DARK, n);
                applyTheme.run();
            });
        });





    }

    // ===== Actions =====

    @FXML
    public void handleAdd(ActionEvent e){
        Dialog<PantryItem> dialog = Dialogs.addItemDialog();
        dialog.showAndWait().ifPresent(item -> {
            // 1) Always add to local "List" and persist
            shoppingList.add(item);
            saveLocalList();

            // 2) Also pin into Recommended so it stays visible there across tab switches
            pinnedRecommended.add(item);
            // combined list will auto-refresh via the listener, but we can recompute explicitly:
            recomputeRecommended();
            savePinnedRecommended();
            // 3) If the Recommended pill is active, ensure the table shows the combined list
            if (expiringToggle.isSelected()) {
                table.setItems(recommendedCombined);
                table.refresh();
            }
        });
    }

    @FXML
    public void handleDelete(ActionEvent e) {
        ObservableList<PantryItem> view = table.getItems();
        if (view == null) return;

        // Collect rows to remove (selected rows + checkbox rows)
        var selectedRows = FXCollections.observableArrayList(table.getSelectionModel().getSelectedItems());
        var checkboxRows = view.filtered(PantryItem::isSelected);
        if (selectedRows.isEmpty() && checkboxRows.isEmpty()) return;

        var toRemove = FXCollections.observableArrayList(selectedRows);
        for (PantryItem p : checkboxRows) if (!toRemove.contains(p)) toRemove.add(p);

        boolean onListTab        = (view == shoppingList);
        boolean onRecommendedTab = (view == recommendedCombined) || expiringToggle.isSelected();

        // Remove from the current view immediately
        view.removeAll(toRemove);

        // Keep both backing stores consistent
        shoppingList.removeAll(toRemove);
        pinnedRecommended.removeAll(toRemove);   // things you manually added to Recommended
        expiringSoonFirebase.removeAll(toRemove); // fetched-from-Firebase list
        recomputeRecommended();                  // rebuild combined Recommended
        savePinnedRecommended();

        table.getSelectionModel().clearSelection();
        table.refresh();

        if (onListTab) {
            saveLocalList();
        }

        // ----- Firestore deletion (async) -----
        String uid = currentUserDocId();
        if (uid == null || uid.isBlank()) return;

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                Firestore db = FirebaseConfiguration.getDatabase();
                var shoppingColl = db.collection("users").document(uid).collection("shoppingList");
                var pantryColl = db.collection("users").document(uid).collection("pantryItems");

                if (onListTab || onRecommendedTab) {

                    var targetColl = shoppingColl;

                    for (PantryItem p : toRemove) {
                        try {
                            deleteFromShoppingList(targetColl, p);
                        }
                        catch (Exception ex) {
                            System.err.println("⚠ shoppingList delete failed for " + p.getName() + ": " + ex.getMessage());
                        }
                    }
                }
            } catch (Exception outer) {
                System.err.println("⚠ Delete task error: " + outer.getMessage());
            }
        });
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

    @FXML
    public void handleSearch(ActionEvent e) {
        applyFilters();
    }

    @FXML
    public void onFilterChanged(ActionEvent e) {
        applyFilters();
    }

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

    // ===== Firebase wiring =====
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
    private PantryItem fromDoc(QueryDocumentSnapshot d) {
        String name = d.getString("name");

        Long qtyL = d.getLong("qty");
        if (qtyL == null) qtyL = d.getLong("quantityNumeric");
        int qty = qtyL == null ? 0 : qtyL.intValue();

        String unit = d.getString("unit");
        String loc  = d.getString("location");
        if (loc == null) loc = d.getString("category");

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

        PantryItem it = new PantryItem(name, qty, unit, loc, expDate, low);
        it.setShoppingDocId(d.getId());          // ✅ keep Firestore id for precise delete
        return it;
    }


// ---- Helpers to work with Firestore docs flexibly ----

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
        Long q = d.getLong("quantity");
        if (q == null) q = d.getLong("qty");
        if (q == null) q = d.getLong("quantityNumeric");
        int qty = q == null ? 0 : q.intValue();

        String unit = d.getString("unit");
        String loc  = d.getString("location");
        if (loc == null) loc = d.getString("category");

        Object expRaw = d.get("expiryDate");
        if (expRaw == null) expRaw = d.get("expiration");
        if (expRaw == null) expRaw = d.get("expirationDate");
        LocalDate exp = toLocalDate(expRaw);

        boolean low = Boolean.TRUE.equals(d.getBoolean("lowStock"));

        PantryItem it = new PantryItem(name, qty, unit, loc, exp, low);
        it.setShoppingDocId(d.getId());          // ✅ keep Firestore id for precise delete
        return it;
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

                // B) Alternative names
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
    private void deleteFromShoppingList(CollectionReference shoppingColl, PantryItem p) throws Exception {
        String docId = p.getShoppingDocId();

        if (docId != null && !docId.isEmpty()) {
            shoppingColl.document(docId).delete().get();
            System.out.println("❌ Successfully deleted from shoppingList by ID: " + p.getName() + " (" + docId + ")");
        } else {
            System.out.println("ℹ️ Item " + p.getName() + " (List) skipped Firebase delete: No Document ID found.");
        }
    }

    private void deleteFromPantryItems(CollectionReference pantryColl, PantryItem p) throws Exception {
        String docId = p.getShoppingDocId();

        if (docId != null && !docId.isEmpty()) {
            // Use the document ID for direct deletion (it holds the pantryItems ID for Recommended items)
            pantryColl.document(docId).delete().get();
            System.out.println("❌ Successfully deleted from pantryItems by ID: " + p.getName() + " (" + docId + ")");
        } else {
            // This handles items like 'pinned recommended' that may not have a Firebase ID.
            System.out.println("ℹ️ Item " + p.getName() + " (Recommended) skipped Firebase delete: No Document ID found.");
        }
    }
    // ===== DTO used for optional JSON seed =====
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

