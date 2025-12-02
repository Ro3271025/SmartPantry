package Recipe;

import com.google.gson.*;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.URL;
import java.util.*;
import java.util.Scanner;
public class RecipeAPIService {
    private static final String API_KEY = "34725fc9b38b4cbca0ec2721da979db8";
    private static final String BASE_URL = "https://api.spoonacular.com/recipes/findByIngredients";

    public static List<Map<String, String>> getRecipesFromIngredients(String ingredients) {
        List<Map<String, String>> recipes = new ArrayList<>();
        try {
            String encodedIngredients = URLEncoder.encode(ingredients, StandardCharsets.UTF_8);
            String urlString = BASE_URL + "?ingredients=" + encodedIngredients + "&number=5&apiKey=" + API_KEY;
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            Scanner scanner = new Scanner(conn.getInputStream());
            String json = scanner.useDelimiter("\\A").next();
            scanner.close();

            JsonArray jsonArray = JsonParser.parseString(json).getAsJsonArray();
            for (JsonElement el : jsonArray) {
                JsonObject obj = el.getAsJsonObject();
                Map<String, String> recipe = new HashMap<>();
                recipe.put("title", obj.get("title").getAsString());
                recipe.put("image", obj.get("image").getAsString());
                recipe.put("id", obj.get("id").getAsString());
                recipes.add(recipe);
            }

        } catch (IOException e) {
            System.err.println("Error fetching recipes: " + e.getMessage());
        }
        return recipes;
    }

    public static String getRecipeLink(String recipeId) {
        return "https://spoonacular.com/recipes/" + recipeId;
    }
    public static String getFullRecipeUrl(String recipeId) {
        try {
            String infoUrl = "https://api.spoonacular.com/recipes/" + recipeId + "/information?apiKey=" + API_KEY;
            URL url = new URL(infoUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            Scanner scanner = new Scanner(conn.getInputStream());
            String json = scanner.useDelimiter("\\A").next();
            scanner.close();

            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
            return jsonObject.get("sourceUrl").getAsString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    public static Map<String, String> getRecipeDetails(String recipeId) {
        Map<String, String> details = new HashMap<>();
        try {
            String urlString = "https://api.spoonacular.com/recipes/" + recipeId + "/information?apiKey=" + API_KEY;
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            Scanner scanner = new Scanner(conn.getInputStream());
            String json = scanner.useDelimiter("\\A").next();
            scanner.close();

            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            details.put("title", obj.get("title").getAsString());
            details.put("image", obj.get("image").getAsString());
            details.put("instructions", obj.has("instructions") ? obj.get("instructions").getAsString() : "No instructions available.");

        } catch (IOException e) {
            System.err.println("Error fetching recipe details: " + e.getMessage());
        }
        return details;
    }
}
