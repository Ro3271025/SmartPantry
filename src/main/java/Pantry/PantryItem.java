package Pantry;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

public class PantryItem {
    private String id;              // Firebase document ID
    private String name;
    private String quantityLabel;   // e.g., "2 bottles", "500g"
    private int quantityNumeric;    // Numeric quantity for calculations
    private String unit;            // e.g., "kg", "lbs", "bottles"
    private Date expirationDate;    // Firebase uses Date, not LocalDate
    private String category;        // e.g., "Dairy", "Vegetables"
    private String userId;          // Which user owns this item
    private Date dateAdded;         // When item was added

    // ✅ DEFAULT CONSTRUCTOR (Required by Firebase)
    public PantryItem() {
        this.dateAdded = new Date();
    }

    // ✅ Constructor matching your original structure
    public PantryItem(String name, String quantityLabel, int quantityNumeric, LocalDate expires) {
        this.name = name;
        this.quantityLabel = quantityLabel;
        this.quantityNumeric = quantityNumeric;

        // Convert LocalDate to Date for Firebase
        if (expires != null) {
            this.expirationDate = Date.from(expires.atStartOfDay(ZoneId.systemDefault()).toInstant());
        }

        this.dateAdded = new Date();
    }

    // ✅ Full constructor with all fields
    public PantryItem(String name, String quantityLabel, int quantityNumeric, String unit,
                      Date expirationDate, String category, String userId) {
        this.name = name;
        this.quantityLabel = quantityLabel;
        this.quantityNumeric = quantityNumeric;
        this.unit = unit;
        this.expirationDate = expirationDate;
        this.category = category;
        this.userId = userId;
        this.dateAdded = new Date();
    }

    // ========================
    // GETTERS (Keep your originals + add new ones)
    // ========================

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getQuantityLabel() {
        return quantityLabel;
    }

    public int getQuantityNumeric() {
        return quantityNumeric;
    }

    public String getUnit() {
        return unit;
    }

    public Date getExpirationDate() {
        return expirationDate;
    }

    // Helper method to get LocalDate (for backward compatibility)
    public LocalDate getExpires() {
        if (expirationDate != null) {
            return expirationDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        return null;
    }

    public String getCategory() {
        return category;
    }

    public String getUserId() {
        return userId;
    }

    public Date getDateAdded() {
        return dateAdded;
    }

    // Alias for compatibility (matches your original method name)
    public int getQuantity() {
        return quantityNumeric;
    }

    // ========================
    // SETTERS (Required by Firebase)
    // ========================

    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setQuantityLabel(String quantityLabel) {
        this.quantityLabel = quantityLabel;
    }

    public void setQuantityNumeric(int quantityNumeric) {
        this.quantityNumeric = quantityNumeric;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public void setExpirationDate(Date expirationDate) {
        this.expirationDate = expirationDate;
    }

    // Helper setter that accepts LocalDate
    public void setExpires(LocalDate expires) {
        if (expires != null) {
            this.expirationDate = Date.from(expires.atStartOfDay(ZoneId.systemDefault()).toInstant());
        } else {
            this.expirationDate = null;
        }
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setDateAdded(Date dateAdded) {
        this.dateAdded = dateAdded;
    }

    // Alias setter for compatibility
    public void setQuantity(int quantity) {
        this.quantityNumeric = quantity;
    }

    // ========================
    // UTILITY METHODS
    // ========================

    @Override
    public String toString() {
        return "PantryItem{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", quantityLabel='" + quantityLabel + '\'' +
                ", quantityNumeric=" + quantityNumeric +
                ", unit='" + unit + '\'' +
                ", expirationDate=" + expirationDate +
                ", category='" + category + '\'' +
                ", userId='" + userId + '\'' +
                ", dateAdded=" + dateAdded +
                '}';
    }
}