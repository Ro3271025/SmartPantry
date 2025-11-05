package Controllers;
import com.example.demo1.Dialogs;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

public class ShoppingListController extends BaseController {
    @FXML private ToggleButton allToggle;
    @FXML private ToggleButton expiringToggle;
    @FXML private ToggleButton lowStockToggle;

    @FXML private TextField searchField;
    @FXML private ComboBox<String> locationFilter;
    @FXML private TableView<PantryItem> table;
    @FXML private TableColumn<PantryItem, Boolean> selectCol;
    @FXML private TableColumn<PantryItem, String> nameCol;
    @FXML private TableColumn<PantryItem, Number> qtyCol;
    @FXML private TableColumn<PantryItem, String> unitCol;
    @FXML private TableColumn<PantryItem, String> locationCol;
    @FXML private TableColumn<PantryItem, LocalDate> expirationCol;
    @FXML private TableColumn<PantryItem, Boolean> lowStockCol;

    @FXML private Button addBtn;
    @FXML private Button consumeBtn;
    @FXML private Button deleteBtn;

    private final ObservableList<PantryItem> items = FXCollections.observableArrayList();
    private final ObjectMapper mapper = new ObjectMapper();

    @FXML
    public void initialize() {
        selectCol.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());
        selectCol.setCellFactory(tc -> new CheckBoxTableCell<>());
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        qtyCol.setCellValueFactory(new PropertyValueFactory<>("qty"));
        unitCol.setCellValueFactory(new PropertyValueFactory<>("unit"));
        locationCol.setCellValueFactory(new PropertyValueFactory<>("location"));
        expirationCol.setCellValueFactory(new PropertyValueFactory<>("expiration"));
        lowStockCol.setCellValueFactory(new PropertyValueFactory<>("lowStock"));
        lowStockCol.setCellFactory(tc -> new CheckBoxTableCell<>());

        table.setItems(items);

        locationFilter.getItems().addAll("All locations","Pantry","Fridge","Freezer");
        locationFilter.getSelectionModel().selectFirst();

        var anySelected = Bindings.createBooleanBinding(
                () -> items.stream().anyMatch(PantryItem::isSelected), items);
        consumeBtn.disableProperty().bind(anySelected.not());
        deleteBtn.disableProperty().bind(anySelected.not());

        try (InputStream in = getClass().getResourceAsStream("sample-data.json")) {
            List<PantryItemDTO> raw = mapper.readValue(in, new TypeReference<>(){});
            for (PantryItemDTO r : raw) {
                items.add(new PantryItem(r.name, r.qty, r.unit, r.location,
                        r.expiration==null?null:LocalDate.parse(r.expiration),
                        r.lowStock));
            }
        } catch (Exception e) {
            items.addAll(
                    new PantryItem("Milk", 1, "gallon", "Fridge", LocalDate.now().plusDays(3), false),
                    new PantryItem("Eggs", 6, "count", "Fridge", LocalDate.now().plusDays(10), false),
                    new PantryItem("Chicken", 2, "lb", "Freezer", LocalDate.now().plusDays(60), false),
                    new PantryItem("Apples", 5, "count", "Pantry", LocalDate.now().plusDays(7), true)
            );
        }

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    }

    @FXML public void handleAdd(ActionEvent e){
        Dialog<PantryItem> dialog = Dialogs.addItemDialog();
        dialog.showAndWait().ifPresent(items::add);
    }

    @FXML public void handleConsume(ActionEvent e){
        items.stream().filter(PantryItem::isSelected).forEach(it -> {
            int q = Math.max(0, it.getQty() - 1);
            it.setQty(q);
            it.setSelected(false);
        });
        table.refresh();
    }

    @FXML public void handleDelete(ActionEvent e){
        items.removeIf(PantryItem::isSelected);
    }

    @FXML public void handleExport(ActionEvent e) {
        try {
            FileChooser fc = new FileChooser();
            fc.setTitle("Export Pantry to JSON");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON","*.json"));
            File f = fc.showSaveDialog(table.getScene().getWindow());
            if (f != null) {
                var out = items.stream().map(PantryItemDTO::from).collect(Collectors.toList());
                Files.writeString(f.toPath(), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(out));
            }
        } catch (Exception ex){ ex.printStackTrace(); }
    }

    @FXML public void handleSearch(ActionEvent e){ applyFilters(); }
    @FXML public void onFilterChanged(ActionEvent e){ applyFilters(); }
    @FXML public void onToggleAll(ActionEvent e){ applyFilters(); }
    @FXML public void onToggleExpiring(ActionEvent e){ applyFilters(); }
    @FXML public void onToggleLowStock(ActionEvent e){ applyFilters(); }

    private void applyFilters(){
        table.setItems(items.filtered(it -> {
            String q = searchField.getText()==null?"":searchField.getText().toLowerCase();
            boolean matchesText = q.isBlank() ||
                    (it.getName()!=null && it.getName().toLowerCase().contains(q));

            boolean matchesLocation = true;
            String sel = locationFilter.getValue();
            if (sel != null && !sel.equals("All locations")) {
                matchesLocation = sel.equals(it.getLocation());
            }

            boolean expiringSoon = it.getExpiration()!=null &&
                    !it.getExpiration().isBefore(LocalDate.now()) &&
                    !it.getExpiration().isAfter(LocalDate.now().plusDays(7));

            boolean matchesToggle = allToggle.isSelected() ||
                    (expiringToggle.isSelected() && expiringSoon) ||
                    (lowStockToggle.isSelected() && it.isLowStock());

            if (!allToggle.isSelected() && !expiringToggle.isSelected() && !lowStockToggle.isSelected()) {
                matchesToggle = true;
            }

            return matchesText && matchesLocation && matchesToggle;
        }));
    }

    public static class PantryItemDTO {
        public String name;
        public int qty;
        public String unit;
        public String location;
        public String expiration;
        public boolean lowStock;

        public static PantryItemDTO from(PantryItem it){
            PantryItemDTO d = new PantryItemDTO();
            d.name = it.getName();
            d.qty = it.getQty();
            d.unit = it.getUnit();
            d.location = it.getLocation();
            d.expiration = it.getExpiration()==null?null:it.getExpiration().toString();
            d.lowStock = it.isLowStock();
            return d;
        }
    }
}
