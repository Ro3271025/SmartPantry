package com.example.demo1;

import javafx.beans.property.*;

public class RecipeItem {
    private final StringProperty id = new SimpleStringProperty();
    private final StringProperty name = new SimpleStringProperty();
    private final StringProperty availableIngredients = new SimpleStringProperty();
    private final StringProperty missingIngredients = new SimpleStringProperty();
    private final StringProperty aiTip = new SimpleStringProperty();
    private final StringProperty userId = new SimpleStringProperty();

    public RecipeItem() {}
    public RecipeItem(String name, String available, String missing, String tip, String uid) {
        this.name.set(name);
        this.availableIngredients.set(available);
        this.missingIngredients.set(missing);
        this.aiTip.set(tip);
        this.userId.set(uid);
    }

    public String getId() { return id.get(); }
    public void setId(String v) { id.set(v); }
    public StringProperty idProperty() { return id; }

    public String getName() { return name.get(); }
    public void setName(String v) { name.set(v); }
    public StringProperty nameProperty() { return name; }

    public String getAvailableIngredients() { return availableIngredients.get(); }
    public void setAvailableIngredients(String v) { availableIngredients.set(v); }
    public StringProperty availableIngredientsProperty() { return availableIngredients; }

    public String getMissingIngredients() { return missingIngredients.get(); }
    public void setMissingIngredients(String v) { missingIngredients.set(v); }
    public StringProperty missingIngredientsProperty() { return missingIngredients; }

    public String getAiTip() { return aiTip.get(); }
    public void setAiTip(String v) { aiTip.set(v); }
    public StringProperty aiTipProperty() { return aiTip; }

    public String getUserId() { return userId.get(); }
    public void setUserId(String v) { userId.set(v); }
    public StringProperty userIdProperty() { return userId; }
}
