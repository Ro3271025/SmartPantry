package rodolfo.pantrydashboard;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;


import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for the Pantry Dashboard UI.
 *
 * IMPLEMENTED FEATURES:
 * ✓ Item status calculation logic (OK, Expiring, Expired, Low Stock)
 * ✓ Dashboard UI with responsive card layout
 * ✓ Filter tabs (All, Expiring, Low Stock)
 * ✓ Search functionality
 *
 * PLACEHOLDER FEATURES (UI only, no backend):
 * ⚠ Add Item, Recipes, Shopping List, Style Guide, Logout buttons
 */
public class PantryController {

    // ========================================================================
    // FXML INJECTED COMPONENTS (linked via fx:id in PantryDashboard.fxml)
    // ========================================================================

    @FXML
    private TextField searchField;          // Search bar for filtering items by name

    @FXML
    private Button addItemBtn;              // [Placeholder] Button to add new items

    @FXML
    private Button recipesBtn;              // [Placeholder] Button for recipes feature

    @FXML
    private Button shoppingBtn;             // [Placeholder] Button for shopping list

    @FXML
    private Button styleBtn;                // [Placeholder] Button for style guide

    @FXML
    private Button logoutBtn;               // [Placeholder] Button for logout

    @FXML
    private ToggleButton segAll;            // Filter tab: Show all items

    @FXML
    private ToggleButton segExpiring;       // Filter tab: Show expiring/expired items only

    @FXML
    private ToggleButton segLowStock;       // Filter tab: Show low stock items only

    @FXML
    private FlowPane cardFlow;              // Container that holds all item cards (wraps automatically)


    // ========================================================================
    // DATA & CONSTANTS
    // ========================================================================

    // In-memory storage for all pantry items
    // TODO: Replace with database or persistent storage in production
   // private final List<PantryItem> allItems = new ArrayList<>();

    // Date formatter for displaying expiration dates on cards (e.g., "Jan 4, 2025")
    private final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.US);


    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    /**
     * Automatically called by JavaFX after FXML is loaded.
     * Sets up the initial state of the dashboard.
     */
    @FXML
//    private void initialize() {
//        // Step 1: Load demo data so the UI isn't empty on startup
//        seedDemoItems();
//
//        // Step 2: Configure the filter tabs (All, Expiring, Low Stock)
//        setupFilters();
//
//        // Step 3: Wire up placeholder buttons (they just print to console for now)
//        setupPlaceholderButtons();
//
//        // Step 4: Display all items for the first time
//        render();
//    }

    /**
     * Configures the three filter tabs and sets up their behavior.
     * Only one tab can be selected at a time (ToggleGroup enforces this).
     */
    private void setupFilters() {
        // Create a toggle group so only one filter can be active
        ToggleGroup filterGroup = new ToggleGroup();
        segAll.setToggleGroup(filterGroup);
        segExpiring.setToggleGroup(filterGroup);
        segLowStock.setToggleGroup(filterGroup);

//        // When filter selection changes, re-render the cards
//        filterGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
//            render();
//        });
//
//        // When user types in search box, re-render the cards
//        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
//            render();
//        });
    }

    /**
     * Sets up click handlers for buttons that don't have real functionality yet.
     * These are just UI placeholders - clicking them prints to the console.
     */
    private void setupPlaceholderButtons() {
        addItemBtn.setOnAction(event -> {
            System.out.println("[Placeholder] Add Item button clicked - feature not implemented");
        });

        recipesBtn.setOnAction(event -> {
            System.out.println("[Placeholder] Recipes button clicked - feature not implemented");
        });

        shoppingBtn.setOnAction(event -> {
            System.out.println("[Placeholder] Shopping button clicked - feature not implemented");
        });

        styleBtn.setOnAction(event -> {
            System.out.println("[Placeholder] Style Guide button clicked - feature not implemented");
        });

        logoutBtn.setOnAction(event -> {
            System.out.println("[Placeholder] Logout button clicked - feature not implemented");
        });
    }

    /**
     * Creates sample pantry items for demonstration purposes.
     * Uses dates relative to today so you can see different status states.
     *
     * TODO: Remove this method when connecting to real data source
     */
//    private void seedDemoItems() {
//        LocalDate today = LocalDate.now();
//
//        // Item(name, quantityLabel, quantityNumeric, expirationDate)
//        allItems.add(new PantryItem("Milk",     "1 gallon", 1, today.plusDays(3)));    // Will show as "Expiring"
//        allItems.add(new PantryItem("Bread",    "1 loaf",   1, today.minusDays(1)));   // Will show as "Expired"
//        allItems.add(new PantryItem("Eggs",     "3 count",  3, today.plusDays(10)));   // Will show as "OK"
//        allItems.add(new PantryItem("Chicken",  "2 lbs",    2, today.plusDays(8)));    // Will show as "Low Stock"
//        allItems.add(new PantryItem("Yogurt",   "4 cups",   4, today.plusDays(12)));   // Will show as "OK"
//        allItems.add(new PantryItem("Bananas",  "2 count",  2, today.plusDays(2)));    // Will show as "Expiring"
//        allItems.add(new PantryItem("Rice",     "5 lbs",    5, today.plusMonths(18))); // Will show as "OK"
//        allItems.add(new PantryItem("Cheese",   "1 block",  1, today.plusDays(15)));   // Will show as "Low Stock"
//    }


    // ========================================================================
    // CORE FEATURE: ITEM STATUS CALCULATION
    // ========================================================================

    /**
     * Calculates the current status of a pantry item based on:
     * 1. How many days until it expires
     * 2. How much quantity remains
     *
     * This is the main business logic for determining item health.
     *
     * @param expirationDate The date when this item expires
     * @param quantity The current quantity/count of this item
     * @return ItemStatus enum (EXPIRED, EXPIRING, LOW_STOCK, or OK)
     */
    private ItemStatus calculateStatus(LocalDate expirationDate, int quantity) {
        LocalDate today = LocalDate.now();

        // Calculate days until expiration (negative = already expired)
        long daysUntilExpiration = ChronoUnit.DAYS.between(today, expirationDate);

        // Priority order matters: check most urgent conditions first

        // 1. Already expired?
        if (daysUntilExpiration < 0) {
            return ItemStatus.EXPIRED;
        }

        // 2. Expiring soon (within 7 days)?
        if (daysUntilExpiration <= 7) {
            return ItemStatus.EXPIRING;
        }

        // 3. Low quantity (2 or fewer units)?
        if (quantity <= 2) {
            return ItemStatus.LOW_STOCK;
        }

        // 4. Otherwise, everything is fine
        return ItemStatus.OK;
    }


    // ========================================================================
    // UI RENDERING LOGIC
    // ========================================================================

    /**
     * Main rendering method - rebuilds the entire card display.
     * Called whenever filters, search, or data changes.
     *
     * Process:
     * 1. Filter by selected tab (All/Expiring/Low Stock)
     * 2. Apply search query
     * 3. Sort by expiration date (soonest first)
     * 4. Draw cards in the UI
     */
//    private void render() {
//        // Start with all items
//        List<PantryItem> filteredItems = allItems;
//
//        // Apply segment filter (All, Expiring, or Low Stock)
//        filteredItems = filterBySegment(filteredItems);
//
//        // Apply search filter (if user typed something)
//        String searchQuery = searchField.getText();
//        filteredItems = searchItems(searchQuery, filteredItems);
//
//        // Sort by expiration date (items expiring soon appear first)
//        filteredItems = filteredItems.stream()
//                .sorted(Comparator.comparing(PantryItem::getExpires))
//                .collect(Collectors.toList());
//
//        // Clear old cards and draw new ones
//        drawCards(filteredItems);
//    }

    /**
     * Filters items based on which segment tab is selected.
     *
     * @param items The list of items to filter
     * @return Filtered list based on active tab
     */
//    private List<PantryItem> filterBySegment(List<PantryItem> items) {
//        // Check which toggle button is selected
//
//        if (segExpiring.isSelected()) {
//            // "Expiring" tab: Show items that are expiring OR already expired
//            return items.stream()
//                    .filter(item -> {
//                        ItemStatus status = calculateStatus(
//                                item.getExpires(),
//                                item.getQuantityNumeric()
//                        );
//                        // Include both EXPIRING and EXPIRED statuses
//                        return status == ItemStatus.EXPIRING || status == ItemStatus.EXPIRED;
//                    })
//                    .collect(Collectors.toList());
//        }
//
//        if (segLowStock.isSelected()) {
//            // "Low Stock" tab: Show only items with low quantity
//            return items.stream()
//                    .filter(item -> {
//                        ItemStatus status = calculateStatus(
//                                item.getExpires(),
//                                item.getQuantityNumeric()
//                        );
//                        return status == ItemStatus.LOW_STOCK;
//                    })
//                    .collect(Collectors.toList());
//        }
//
//        // "All" tab (or nothing selected): Show everything
//        return items;
//    }

    /**
     * Filters items by search query (case-insensitive name matching).
     *
     * @param query The search text entered by the user
     * @param items The list of items to search through
     * @return Items whose names contain the query
     */
//    private List<PantryItem> searchItems(String query, List<PantryItem> items) {
//        // If search box is empty, return all items
//        if (query == null || query.isBlank()) {
//            return items;
//        }
//
//        // Convert query to lowercase for case-insensitive matching
//        String lowercaseQuery = query.toLowerCase(Locale.ROOT);
//
//        // Keep only items whose name contains the query
//        return items.stream()
//                .filter(item -> {
//                    String itemName = item.getName().toLowerCase(Locale.ROOT);
//                    return itemName.contains(lowercaseQuery);
//                })
//                .collect(Collectors.toList());
//    }

    /**
     * Clears the card container and creates new cards for each item.
     *
     * @param items The list of items to display
     */
//    private void drawCards(List<PantryItem> items) {
//        // Remove all existing cards
//        cardFlow.getChildren().clear();
//
//        // Create a card for each item
//        for (PantryItem item : items) {
//            // Calculate status for this item
//            ItemStatus status = calculateStatus(
//                    item.getExpires(),
//                    item.getQuantityNumeric()
//            );
//
//            // Build the visual card
//            StackPane card = buildCard(item, status);
//
//            // Add card to the display
//            cardFlow.getChildren().add(card);
//        }
//    }


    // ========================================================================
    // CARD BUILDING (Visual Components)
    // ========================================================================

    /**
     * Builds a single item card with all its visual components.
     *
     * Card structure:
     * ┌─────────────────────────┐
     * │ [Title]      [Status]   │  ← Header row
     * │                         │
     * │ Quantity:    1 gallon   │  ← Grid row 1
     * │ Expires:     Jan 4      │  ← Grid row 2
     * └─────────────────────────┘
     *
     * @param item The pantry item data
     * @param status The calculated status for this item
     * @return A fully styled card (StackPane) ready to display
     */
//    private StackPane buildCard(PantryItem item, ItemStatus status) {
//
//        // --- Status Chip (colored badge) ---
//        Label statusChip = new Label(statusToLabel(status));
//        statusChip.getStyleClass().addAll("chip", statusToChipClass(status));
//
//        // --- Header Row (Title on left, chip on right) ---
//        HBox header = new HBox(8);  // 8px spacing between children
//        header.setAlignment(Pos.TOP_LEFT);
//
//        // Item title
//        Label title = new Label(item.getName());
//        title.getStyleClass().add("card-title");
//
//        // Spacer pushes the chip to the right edge
//        Region spacer = new Region();
//        HBox.setHgrow(spacer, Priority.ALWAYS);
//
//        header.getChildren().addAll(title, spacer, statusChip);
//
//        // --- Details Grid (Quantity and Expiration info) ---
//        GridPane detailsGrid = new GridPane();
//        detailsGrid.setHgap(8);  // Horizontal gap between columns
//        detailsGrid.setVgap(2);  // Vertical gap between rows
//
//        // Row 1: Quantity
//        Label quantityLabel = new Label("Quantity:");
//        quantityLabel.getStyleClass().add("muted");
//        Label quantityValue = new Label(item.getQuantityLabel());
//
//        // Row 2: Expiration date
//        Label expiresLabel = new Label("Expires:");
//        expiresLabel.getStyleClass().add("muted");
//        Label expiresValue = new Label(DATE_FMT.format(item.getExpires()));
//
//        // Add labels to grid (column, row)
//        detailsGrid.add(quantityLabel, 0, 0);
//        detailsGrid.add(quantityValue, 1, 0);
//        detailsGrid.add(expiresLabel,  0, 1);
//        detailsGrid.add(expiresValue,  1, 1);
//
//        // --- Vertical Layout (header above grid) ---
//        VBox cardContent = new VBox(8, header, detailsGrid);  // 8px spacing
//
//        // --- Card Container with styling ---
//        StackPane card = new StackPane(cardContent);
//        card.getStyleClass().add("card");
//
//        // Add status-specific border color class
//        switch (status) {
//            case EXPIRED   -> card.getStyleClass().add("state-expired");
//            case EXPIRING  -> card.getStyleClass().add("state-expiring");
//            case LOW_STOCK -> card.getStyleClass().add("state-low");
//            case OK        -> card.getStyleClass().add("state-ok");
//        }
//
//        // Add padding inside the card
//        StackPane.setMargin(cardContent, new Insets(12, 14, 12, 14));
//
//        return card;
//    }


    // ========================================================================
    // HELPER METHODS (Status to UI Mappings)
    // ========================================================================

    /**
     * Converts ItemStatus enum to human-readable text for display.
     *
     * @param status The item status enum
     * @return Display text (e.g., "Expiring")
     */
    private String statusToLabel(ItemStatus status) {
        return switch (status) {
            case OK        -> "OK";
            case EXPIRING  -> "Expiring";
            case EXPIRED   -> "Expired";
            case LOW_STOCK -> "Low Stock";
        };
    }

    /**
     * Maps ItemStatus to CSS class name for chip styling.
     * These classes are defined in pantry.css.
     *
     * @param status The item status enum
     * @return CSS class name (e.g., "chip-expiring")
     */
    private String statusToChipClass(ItemStatus status) {
        return switch (status) {
            case OK        -> "chip-ok";       // Green background
            case EXPIRING  -> "chip-expiring"; // Orange background
            case EXPIRED   -> "chip-danger";   // Red background
            case LOW_STOCK -> "chip-warn";     // Yellow/orange background
        };
    }
}