package Controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.event.ActionEvent;
import rodolfo.pantrydashboard.ItemStatus;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

public class PantryController extends BaseController{

    @FXML private TextField searchField;
    @FXML private Button addItemBtn;
    @FXML private Button recipesBtn;
    @FXML private Button shoppingBtn;
    @FXML private Button styleBtn;
    @FXML private Button logoutBtn;
    @FXML private ToggleButton segAll;
    @FXML private ToggleButton segExpiring;
    @FXML private ToggleButton segLowStock;
    @FXML private FlowPane cardFlow;

    private final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.US);

    // 🔑 Dynamic user ID (set after login)
    private String currentUserId;

    public void setCurrentUserId(String uid) {
        this.currentUserId = uid;
    }

    /** Opens the Add Item screen in a new popup window */
    @FXML
    private void addItemBtnOnAction(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/demo1/addItem.fxml"));
            Scene addItemScene = new Scene(loader.load(), 400, 650);

            // Pass current user ID into AddItemController
            AddItemController controller = loader.getController();
           // controller.setCurrentUserId(currentUserId);

            Stage addItemStage = new Stage();
            addItemStage.setTitle("Add New Item");
            addItemStage.setScene(addItemScene);
            addItemStage.initModality(Modality.APPLICATION_MODAL);
            addItemStage.showAndWait();


            // render();

        } catch (IOException e) {
            System.err.println("Error opening Add Item window: " + e.getMessage());
            e.printStackTrace();
        }
    }
    /** Opens the Recipe Tab screen */
    @FXML
    private void recipesBtnOnAction(ActionEvent event) throws IOException {
        switchScene(event, "Recipe");
    }


    private void setupFilters() {
        ToggleGroup filterGroup = new ToggleGroup();
        segAll.setToggleGroup(filterGroup);
        segExpiring.setToggleGroup(filterGroup);
        segLowStock.setToggleGroup(filterGroup);
    }

    private void setupPlaceholderButtons() {
        recipesBtn.setOnAction(event -> System.out.println("[Placeholder] Recipes button clicked"));
        shoppingBtn.setOnAction(event -> System.out.println("[Placeholder] Shopping button clicked"));
        styleBtn.setOnAction(event -> System.out.println("[Placeholder] Style Guide button clicked"));
        logoutBtn.setOnAction(event -> System.out.println("[Placeholder] Logout button clicked"));
    }

    private ItemStatus calculateStatus(LocalDate expirationDate, int quantity) {
        LocalDate today = LocalDate.now();
        long daysUntilExpiration = ChronoUnit.DAYS.between(today, expirationDate);

        if (daysUntilExpiration < 0) return ItemStatus.EXPIRED;
        if (daysUntilExpiration <= 7) return ItemStatus.EXPIRING;
        if (quantity <= 2) return ItemStatus.LOW_STOCK;
        return ItemStatus.OK;
    }

    private String statusToLabel(ItemStatus status) {
        return switch (status) {
            case OK        -> "OK";
            case EXPIRING  -> "Expiring";
            case EXPIRED   -> "Expired";
            case LOW_STOCK -> "Low Stock";
        };
    }

    /**
     * Show error alert dialog
     */
    private void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    private String statusToChipClass(ItemStatus status) {
        return switch (status) {
            case OK        -> "chip-ok";
            case EXPIRING  -> "chip-expiring";
            case EXPIRED   -> "chip-danger";
            case LOW_STOCK -> "chip-warn";
        };
    }
}