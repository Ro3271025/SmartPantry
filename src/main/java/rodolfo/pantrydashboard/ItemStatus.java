package rodolfo.pantrydashboard;

/**
 * Enumeration of possible status states for pantry items.
 */
public enum ItemStatus {
    OK,
    EXPIRING,
    EXPIRED,
    LOW_STOCK;

    public boolean isUrgent() {
        return this == EXPIRED || this == EXPIRING;
    }


    public boolean isSafeToUse() {
        return this == OK || this == LOW_STOCK;
    }

    public int getUrgencyLevel() {
        return switch (this) {
            case EXPIRED -> 3;
            case EXPIRING -> 2;
            case LOW_STOCK -> 1;
            case OK -> 0;
        };
    }

    public String getDescription() {
        return switch (this) {
            case OK -> "Item is in good condition with adequate quantity and time before expiration.";
            case EXPIRING -> "Item will expire within the next 7 days. Plan to use it soon.";
            case EXPIRED -> "Item has passed its expiration date. Discard or use immediately with caution.";
            case LOW_STOCK -> "Item quantity is running low (2 or fewer units). Consider restocking.";
        };
    }
}