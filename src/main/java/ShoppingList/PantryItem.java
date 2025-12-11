package ShoppingList;

import javafx.beans.property.*;

import java.time.LocalDate;

public class PantryItem {
    private final StringProperty name = new SimpleStringProperty();
    private final IntegerProperty qty = new SimpleIntegerProperty(1);
    private final StringProperty unit = new SimpleStringProperty("");
    private final StringProperty location = new SimpleStringProperty("Pantry");
    private final ObjectProperty<LocalDate> expiration = new SimpleObjectProperty<>(null);
    private final BooleanProperty lowStock = new SimpleBooleanProperty(false);
    private final BooleanProperty selected = new SimpleBooleanProperty(false);

    public PantryItem() {}

    // The existing 6-argument constructor
    public PantryItem(String name, int qty, String unit, String location, LocalDate expiration, boolean lowStock) {
        this.name.set(name);
        this.qty.set(qty);
        this.unit.set(unit);
        this.location.set(location);
        this.expiration.set(expiration);
        this.lowStock.set(lowStock);
    }

    // ⭐ NEW 7-argument constructor to resolve the error in ShoppingListController's fromDoc method ⭐
    public PantryItem(String shoppingDocId, String name, int qty, String unit, String location, LocalDate expiration, boolean lowStock) {
        // Call the 6-argument constructor for the properties
        this(name, qty, unit, location, expiration, lowStock);

        // Set the unique Firebase ID
        this.shoppingDocId = shoppingDocId;
    }

    private String shoppingDocId;
    public String getShoppingDocId() { return shoppingDocId; }
    public void setShoppingDocId(String shoppingDocId) { this.shoppingDocId = shoppingDocId; }

    public String getName() { return name.get(); }
    public void setName(String v) { name.set(v); }
    public StringProperty nameProperty(){ return name; }

    public int getQty(){ return qty.get(); }
    public void setQty(int v){ qty.set(v); }
    public IntegerProperty qtyProperty(){ return qty; }

    public String getUnit(){ return unit.get(); }
    public void setUnit(String v){ unit.set(v); }
    public StringProperty unitProperty(){ return unit; }

    public String getLocation(){ return location.get(); }
    public void setLocation(String v){ location.set(v); }
    public StringProperty locationProperty(){ return location; }

    public LocalDate getExpiration(){ return expiration.get(); }
    public void setExpiration(LocalDate v){ expiration.set(v); }
    public ObjectProperty<LocalDate> expirationProperty(){ return expiration; }

    public boolean isLowStock(){ return lowStock.get(); }
    public void setLowStock(boolean v){ lowStock.set(v); }
    public BooleanProperty lowStockProperty(){ return lowStock; }

    public boolean isSelected(){ return selected.get(); }
    public void setSelected(boolean v){ selected.set(v); }
    public BooleanProperty selectedProperty(){ return selected; }
}