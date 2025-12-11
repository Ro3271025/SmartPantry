package ShoppingList;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import java.time.LocalDate;

public class PantryItem {
    private BooleanProperty selected;
    private String name;
    private int qty;
    private String unit;
    private String location;
    private LocalDate expiration;
    private boolean lowStock;
    private String shoppingDocId; // NEW: Store Firestore document ID

    // Constructor
    public PantryItem(String name, int qty, String unit, String location, LocalDate expiration, boolean lowStock) {
        this.name = name;
        this.qty = qty;
        this.unit = unit;
        this.location = location;
        this.expiration = expiration;
        this.lowStock = lowStock;
        this.selected = new SimpleBooleanProperty(false);
    }

    public PantryItem() {
        this.name = "";
        this.qty = 1;
        this.unit = "";
        this.location = "";
        this.expiration = null;
        this.lowStock = false;
        this.selected = new SimpleBooleanProperty(false);
    }


    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getQty() {
        return qty;
    }

    public void setQty(int qty) {
        this.qty = qty;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public LocalDate getExpiration() {
        return expiration;
    }

    public void setExpiration(LocalDate expiration) {
        this.expiration = expiration;
    }

    public boolean isLowStock() {
        return lowStock;
    }

    public void setLowStock(boolean lowStock) {
        this.lowStock = lowStock;
    }

    // Selected property methods
    public BooleanProperty selectedProperty() {
        if (selected == null) {
            selected = new SimpleBooleanProperty(false);
        }
        return selected;
    }

    public boolean isSelected() {
        return selected != null && selected.get();
    }

    public void setSelected(boolean selected) {
        selectedProperty().set(selected);
    }

    // NEW: Shopping document ID getter and setter
    public String getShoppingDocId() {
        return shoppingDocId;
    }

    public void setShoppingDocId(String shoppingDocId) {
        this.shoppingDocId = shoppingDocId;
    }

    @Override
    public String toString() {
        return "PantryItem{" +
                "name='" + name + '\'' +
                ", qty=" + qty +
                ", unit='" + unit + '\'' +
                ", location='" + location + '\'' +
                ", expiration=" + expiration +
                ", lowStock=" + lowStock +
                ", selected=" + (selected != null ? selected.get() : false) +
                '}';
    }
}