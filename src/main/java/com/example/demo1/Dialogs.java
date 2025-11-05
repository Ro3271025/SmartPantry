package com.example.demo1;

import Controllers.PantryItem;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

public class Dialogs {
    public static Dialog<PantryItem> addItemDialog(){
        Dialog<PantryItem> dialog = new Dialog<>();
        dialog.setTitle("Add Pantry Item");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField name = new TextField();
        name.setPromptText("Item name");

        Spinner<Integer> qty = new Spinner<>(1, 999, 1);
        TextField unit = new TextField();
        unit.setPromptText("unit (e.g., cups)");
        ComboBox<String> location = new ComboBox<>();
        location.getItems().addAll("Pantry","Fridge","Freezer");
        location.getSelectionModel().selectFirst();
        DatePicker exp = new DatePicker();

        GridPane gp = new GridPane();
        gp.setHgap(10); gp.setVgap(10); gp.setPadding(new Insets(10));
        gp.addRow(0, new Label("Name"), name);
        gp.addRow(1, new Label("Qty"), qty);
        gp.addRow(2, new Label("Unit"), unit);
        gp.addRow(3, new Label("Location"), location);
        gp.addRow(4, new Label("Expiration"), exp);

        dialog.getDialogPane().setContent(gp);
        dialog.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                PantryItem it = new PantryItem();
                it.setName(name.getText());
                it.setQty(qty.getValue());
                it.setUnit(unit.getText());
                it.setLocation(location.getValue());
                it.setExpiration(exp.getValue());
                it.setLowStock(false);
                return it;
            }
            return null;
        });
        return dialog;
    }
}
