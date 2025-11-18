package ai;

import java.util.List;

/** DTO for AI-generated recipes. */
public class RecipeDTO {
    public String title;
    public List<String> ingredients;
    public List<String> steps;
    public List<String> missingIngredients;
    public String estimatedTime;
    public Integer calories;

    @Override public String toString() { return title != null ? title : "Untitled"; }
}