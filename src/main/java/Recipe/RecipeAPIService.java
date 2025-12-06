package Recipe;

import com.google.gson.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Unified recipe service: tries Spoonacular first, then falls back to TheMealDB (no key).
 * Returns normalized maps:
 *   - id         (prefixed: "spoon:<id>" | "mealdb:<id>")
 *   - title
 *   - image
 *   - [optional] sourceUrl
 *   - [optional] instructions
 */
public class RecipeAPIService {

    // Prefer JVM prop or env var; fallback is just a placeholder.
    private static final String API_KEY_FALLBACK = "34725fc9b38b4cbca0ec2721da979db8\n";

    private static final String SPOON_COMPLEX = "https://api.spoonacular.com/recipes/complexSearch";
    private static final String SPOON_BY_ING  = "https://api.spoonacular.com/recipes/findByIngredients";
    private static final String SPOON_INFO    = "https://api.spoonacular.com/recipes";

    // TheMealDB (public)
    private static final String MEALDB_SEARCH  = "https://www.themealdb.com/api/json/v1/1/search.php?s=";
    private static final String MEALDB_FILTER  = "https://www.themealdb.com/api/json/v1/1/filter.php?i=";
    private static final String MEALDB_LOOKUP  = "https://www.themealdb.com/api/json/v1/1/lookup.php?i=";

    private static final Pattern NON_WORDS = Pattern.compile("[^a-zA-Z\\s]");
    private static final Set<String> STOP = Set.of(
            "a","an","the","easy","best","homemade","quick","recipe","classic","and","with","of","for"
    );

    // ========= Public surface =========

    /** High-level search with automatic fallbacks and normalization. */
    public static List<Map<String, String>> smartSearch(String recipeName, String availableCsv, int limit) {
        String cleanName = sanitizeName(recipeName);
        String normalizedAvail = normalizeIngredientsCsv(availableCsv);
        int n = Math.max(1, limit);

        // 1) Spoonacular: name queries (complexSearch)
        if (isSpoonKeyPresent()) {
            for (String q : buildNameCandidates(cleanName)) {
                List<Map<String,String>> byName = spoonByName(q, n);
                if (!byName.isEmpty()) return byName;
            }
            // 2) Spoonacular: by ingredients
            String ingCsv = normalizedAvail.isBlank() ? ingredientsFromName(cleanName) : normalizedAvail;
            List<Map<String,String>> byIng = spoonByIngredients(ingCsv, n);
            if (!byIng.isEmpty()) return byIng;
        }

        // 3) TheMealDB: by name
        List<Map<String,String>> mealdbByName = mealdbByName(cleanName, n);
        if (!mealdbByName.isEmpty()) return mealdbByName;

        // 4) TheMealDB: by ingredients (single-ingredient filter, try each token)
        for (String ing : topTokensForIngredients(cleanName, normalizedAvail)) {
            List<Map<String,String>> m = mealdbByOneIngredient(ing, n);
            if (!m.isEmpty()) return m;
        }

        return List.of();
    }

    /** Unified details fetcher using the prefixed id returned by smartSearch. */
    public static Map<String,String> getRecipeDetailsUnified(String prefixedId) {
        if (prefixedId == null || prefixedId.isBlank()) return Map.of();
        if (prefixedId.startsWith("spoon:")) {
            String id = prefixedId.substring("spoon:".length());
            return spoonDetails(id);
        } else if (prefixedId.startsWith("mealdb:")) {
            String id = prefixedId.substring("mealdb:".length());
            return mealdbDetails(id);
        }
        // Unknown provider; return empty.
        return Map.of();
    }

    // ========= Spoonacular implementation =========

    private static List<Map<String,String>> spoonByName(String name, int limit) {
        List<Map<String,String>> out = new ArrayList<>();
        if (!isSpoonKeyPresent() || isBlank(name)) return out;

        HttpURLConnection c = null;
        try {
            String url = SPOON_COMPLEX
                    + "?query=" + enc(name)
                    + "&addRecipeInformation=true"
                    + "&number=" + limit
                    + "&apiKey=" + enc(apiKey());
            c = open(url);
            int code = c.getResponseCode();
            String body = readBody(c, code);

            if (code == 402) { System.err.println("Name search HTTP 402 for query: " + name); return List.of(); }
            if (code != 200)  { System.err.println("Name search HTTP " + code + " for query: " + name); return List.of(); }

            JsonArray results = JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("results");
            if (results == null) return out;

            for (JsonElement el : results) {
                JsonObject obj = el.getAsJsonObject();
                out.add(normalizeSpoonCard(obj));
            }
        } catch (Exception e) {
            System.err.println("Error (spoon name search): " + e.getMessage());
        } finally { if (c != null) c.disconnect(); }
        return out;
    }

    private static List<Map<String,String>> spoonByIngredients(String ingredientsCsv, int limit) {
        List<Map<String,String>> out = new ArrayList<>();
        if (!isSpoonKeyPresent() || isBlank(ingredientsCsv)) return out;

        HttpURLConnection c = null;
        try {
            String url = SPOON_BY_ING
                    + "?ingredients=" + enc(normalizeIngredientsCsv(ingredientsCsv))
                    + "&ranking=2&ignorePantry=true"
                    + "&number=" + limit
                    + "&apiKey=" + enc(apiKey());
            c = open(url);
            int code = c.getResponseCode();
            String body = readBody(c, code);

            if (code == 402) { System.err.println("Error fetching recipes (ingredients). HTTP 402 URL: " + url); return List.of(); }
            if (code != 200)  { System.err.println("Error fetching recipes (ingredients). HTTP " + code + " URL: " + url); return List.of(); }

            JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
            if (arr == null) return out;

            for (JsonElement el : arr) {
                JsonObject obj = el.getAsJsonObject();
                Map<String,String> card = new LinkedHashMap<>();
                card.put("id", "spoon:" + getString(obj, "id"));
                card.put("title", getString(obj, "title"));
                card.put("image", getString(obj, "image"));
                out.add(card);
            }
        } catch (Exception e) {
            System.err.println("Error (spoon ingredients): " + e.getMessage());
        } finally { if (c != null) c.disconnect(); }
        return out;
    }

    private static Map<String,String> spoonDetails(String id) {
        Map<String,String> details = new LinkedHashMap<>();
        if (!isSpoonKeyPresent() || isBlank(id)) return details;

        HttpURLConnection c = null;
        try {
            String url = SPOON_INFO + "/" + enc(id) + "/information?apiKey=" + enc(apiKey());
            c = open(url);
            int code = c.getResponseCode();
            String body = readBody(c, code);
            if (code == 402) { System.err.println("getRecipeDetails HTTP 402 id=" + id); return Map.of(); }
            if (code != 200)  { System.err.println("getRecipeDetails HTTP " + code + " id=" + id); return Map.of(); }

            JsonObject o = JsonParser.parseString(body).getAsJsonObject();
            details.put("title", getString(o, "title"));
            details.put("image", getString(o, "image"));
            details.put("instructions", getString(o, "instructions").isBlank() ? "No instructions available." : getString(o, "instructions"));
            details.put("sourceUrl", getString(o, "sourceUrl"));
        } catch (Exception e) {
            System.err.println("Error fetching spoon details: " + e.getMessage());
        } finally { if (c != null) c.disconnect(); }
        return details;
    }

    private static Map<String,String> normalizeSpoonCard(JsonObject obj) {
        Map<String,String> card = new LinkedHashMap<>();
        card.put("id", "spoon:" + getString(obj, "id"));
        card.put("title", getString(obj, "title"));
        card.put("image", getString(obj, "image"));
        // optional extras if available
        if (obj.has("sourceUrl")) card.put("sourceUrl", getString(obj, "sourceUrl"));
        if (obj.has("instructions") && !obj.get("instructions").isJsonNull())
            card.put("instructions", obj.get("instructions").getAsString());
        return card;
    }

    // ========= TheMealDB implementation =========

    private static List<Map<String,String>> mealdbByName(String name, int limit) {
        List<Map<String,String>> out = new ArrayList<>();
        if (isBlank(name)) return out;

        HttpURLConnection c = null;
        try {
            String url = MEALDB_SEARCH + enc(name);
            c = open(url);
            int code = c.getResponseCode();
            String body = readBody(c, code);
            if (code != 200) return out;

            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonArray meals = root.getAsJsonArray("meals");
            if (meals == null) return out;

            int count = 0;
            for (JsonElement el : meals) {
                if (count >= limit) break;
                JsonObject m = el.getAsJsonObject();
                out.add(normalizeMealDbCard(m));
                count++;
            }
        } catch (Exception ignored) {
        } finally { if (c != null) c.disconnect(); }
        return out;
    }

    private static List<Map<String,String>> mealdbByOneIngredient(String ingredient, int limit) {
        List<Map<String,String>> out = new ArrayList<>();
        if (isBlank(ingredient)) return out;

        HttpURLConnection c = null;
        try {
            String url = MEALDB_FILTER + enc(ingredient);
            c = open(url);
            int code = c.getResponseCode();
            String body = readBody(c, code);
            if (code != 200) return out;

            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonArray meals = root.getAsJsonArray("meals");
            if (meals == null) return out;

            int count = 0;
            for (JsonElement el : meals) {
                if (count >= limit) break;
                JsonObject m = el.getAsJsonObject();
                out.add(normalizeMealDbCard(m));
                count++;
            }
        } catch (Exception ignored) {
        } finally { if (c != null) c.disconnect(); }
        return out;
    }

    private static Map<String,String> mealdbDetails(String id) {
        Map<String,String> details = new LinkedHashMap<>();
        if (isBlank(id)) return details;

        HttpURLConnection c = null;
        try {
            String url = MEALDB_LOOKUP + enc(id);
            c = open(url);
            int code = c.getResponseCode();
            String body = readBody(c, code);
            if (code != 200) return details;

            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonArray meals = root.getAsJsonArray("meals");
            if (meals == null || meals.size() == 0) return details;

            JsonObject m = meals.get(0).getAsJsonObject();
            details.put("title", getString(m, "strMeal"));
            details.put("image", getString(m, "strMealThumb"));
            String instr = getString(m, "strInstructions");
            details.put("instructions", instr.isBlank() ? "No instructions available." : instr);

            String src = getString(m, "strSource");
            if (src.isBlank()) src = getString(m, "strYoutube");
            if (src.isBlank()) src = "https://www.themealdb.com/meal/" + id;
            details.put("sourceUrl", src);
        } catch (Exception ignored) {
        } finally { if (c != null) c.disconnect(); }
        return details;
    }

    private static Map<String,String> normalizeMealDbCard(JsonObject m) {
        Map<String,String> card = new LinkedHashMap<>();
        card.put("id", "mealdb:" + getString(m, "idMeal"));
        card.put("title", getString(m, "strMeal"));
        card.put("image", getString(m, "strMealThumb"));
        return card;
    }

    // ========= Name → ingredients helpers =========

    public static String sanitizeName(String s) {
        String t = NON_WORDS.matcher(nz(s).toLowerCase()).replaceAll(" ").replaceAll("\\s+"," ").trim();
        if (t.isBlank()) return t;
        List<String> keep = new ArrayList<>();
        for (String tok : t.split("\\s+")) if (!STOP.contains(tok)) keep.add(tok);
        return String.join(" ", keep);
    }

    public static List<String> buildNameCandidates(String base) {
        if (isBlank(base)) return List.of();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        out.add(base);
        out.add(base + " recipe");
        String[] tokens = base.split("\\s+");
        if (tokens.length >= 2) {
            out.add(tokens[1] + " " + tokens[0]);
            for (int i = 0; i < tokens.length; i++)
                for (int j = i + 1; j < tokens.length; j++)
                    out.add(tokens[i] + " " + tokens[j]);
        }
        return new ArrayList<>(out).subList(0, Math.min(out.size(), 8));
    }

    public static String normalizeIngredientsCsv(String csv) {
        if (isBlank(csv)) return "";
        List<String> toks = Arrays.stream(csv.split(","))
                .map(String::trim).map(String::toLowerCase)
                .map(x -> x.replaceAll("\\s+", " "))
                .filter(s -> !s.isBlank())
                .distinct().limit(7).toList();
        return String.join(", ", toks);
    }

    public static String ingredientsFromName(String cleanName) {
        String mapped = DISH_TO_ING.get(cleanName);
        if (mapped != null) return mapped;
        return tokensAsIngredientsCsv(cleanName);
    }

    private static String tokensAsIngredientsCsv(String cleanName) {
        if (isBlank(cleanName)) return "";
        List<String> toks = Arrays.stream(cleanName.split("\\s+"))
                .filter(t -> !STOP.contains(t))
                .map(String::trim)
                .filter(t -> !t.isBlank())
                .distinct().limit(5).toList();
        return String.join(", ", toks);
    }

    private static List<String> topTokensForIngredients(String cleanName, String normalizedAvail) {
        if (!isBlank(normalizedAvail)) {
            return Arrays.stream(normalizedAvail.split(","))
                    .map(String::trim).filter(s -> !s.isBlank()).limit(5).toList();
        }
        if (isBlank(cleanName)) return List.of();
        return Arrays.stream(cleanName.split("\\s+"))
                .filter(t -> !STOP.contains(t)).limit(5).toList();
    }

    private static final Map<String,String> DISH_TO_ING = Map.of(
            "pepperoni pizza", "pepperoni, mozzarella, pizza dough, tomato sauce",
            "margherita pizza", "mozzarella, basil, pizza dough, tomato sauce",
            "grilled cheese", "bread, cheddar cheese, butter",
            "fettuccine alfredo", "fettuccine, parmesan, cream, butter, garlic",
            "cheese quesadilla", "tortillas, cheese, butter",
            "pancakes", "flour, eggs, milk, butter"
    );

    // ========= HTTP & key =========

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout((int) Duration.ofSeconds(8).toMillis());
        c.setReadTimeout((int) Duration.ofSeconds(12).toMillis());
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "SmartPantry/1.0 (+javafx)");
        return c;
    }

    private static String readBody(HttpURLConnection c, int code) throws IOException {
        InputStream in = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
        if (in == null) return "";
        try (Scanner sc = new Scanner(in, StandardCharsets.UTF_8)) {
            sc.useDelimiter("\\A");
            return sc.hasNext() ? sc.next() : "";
        }
    }

    private static String enc(String s) { return URLEncoder.encode(nz(s), StandardCharsets.UTF_8); }
    private static String getString(JsonObject o, String k) {
        return (o != null && o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsString() : "";
    }
    private static String nz(String s) { return s == null ? "" : s; }
    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    private static String apiKey() {
        String fromProp = System.getProperty("spoon.key");
        if (!isBlank(fromProp)) return fromProp.trim();
        String fromEnv = System.getenv("SPOONACULAR_API_KEY");
        if (!isBlank(fromEnv)) return fromEnv.trim();
        return API_KEY_FALLBACK.trim();
    }
    private static boolean isSpoonKeyPresent() {
        String k = apiKey();
        return k != null && !k.isBlank() && !k.equalsIgnoreCase("random numbers");
    }
}
