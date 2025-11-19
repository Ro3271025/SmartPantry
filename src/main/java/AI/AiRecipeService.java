package AI;

import Pantry.PantryItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Calls an LLM to generate recipes based on pantry items.
 * Returns strict JSON parsed into RecipeDTOs.
 */
public class AiRecipeService {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    private final String apiKey;
    private final String model;

    public AiRecipeService() {
        this.apiKey = Objects.requireNonNullElse(System.getenv("OPENAI_API_KEY"), "");
        if (apiKey.isBlank()) throw new IllegalStateException("OPENAI_API_KEY env var is required");
        this.model = System.getenv().getOrDefault("OPENAI_MODEL", "gpt-4o-mini");
    }

    public List<RecipeDTO> generateRecipes(List<PantryItem> pantry, String userPrompt, int count) throws Exception {
        String pantryContext = (pantry == null || pantry.isEmpty())
                ? "None"
                : pantry.stream()
                .map(p -> {
                    String qty = (p.getQuantityLabel() != null && !p.getQuantityLabel().isBlank())
                            ? p.getQuantityLabel()
                            : String.valueOf(p.getQuantityNumeric());
                    String cat = p.getCategory() != null ? p.getCategory() : "Uncategorized";
                    return "- " + p.getName() + " (" + qty + ", " + cat + ")";
                })
                .collect(Collectors.joining("\n"));

        String sys = """
            You are a helpful recipe generator. Return strictly JSON in this shape:
            {
              "recipes":[
                {
                  "title": "string",
                  "ingredients": ["string", "..."],
                  "steps": ["string", "..."],
                  "missing_ingredients": ["string", "..."],
                  "estimated_time": "string",
                  "calories": 500
                }
              ]
            }
            Use pantry items when possible; list extra needed items in "missing_ingredients".
            No prose, no markdown—JSON object only.
            """;

        String user = """
            Pantry items:
            %s

            Request:
            Generate %d recipe(s). %s
            """.formatted(pantryContext, Math.max(1, count), (userPrompt == null ? "" : userPrompt));

        String body = """
            {
              "model": %s,
              "temperature": 0.6,
              "response_format": { "type": "json_object" },
              "messages": [
                {"role":"system","content":%s},
                {"role":"user","content":%s}
              ]
            }
            """.formatted(
                mapper.writeValueAsString(model),
                mapper.writeValueAsString(sys),
                mapper.writeValueAsString(user)
        );

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() / 100 != 2) {
            throw new IllegalStateException("AI API error: " + res.statusCode() + " -> " + res.body());
        }

        String content = mapper.readTree(res.body())
                .path("choices").path(0).path("message").path("content").asText("{}");

        JsonNode json = mapper.readTree(content);
        ArrayNode arr = (ArrayNode) json.path("recipes");

        List<RecipeDTO> out = new ArrayList<>();
        if (arr != null) {
            for (JsonNode n : arr) {
                RecipeDTO r = new RecipeDTO();
                r.title = n.path("title").asText("");
                r.ingredients = toList(n.path("ingredients"));
                r.steps = toList(n.path("steps"));
                r.missingIngredients = toList(n.path("missing_ingredients"));
                r.estimatedTime = n.path("estimated_time").asText("");
                if (n.has("calories") && n.get("calories").isInt()) r.calories = n.get("calories").asInt();
                out.add(r);
            }
        }
        return out;
    }

    private List<String> toList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node != null && node.isArray()) node.forEach(x -> list.add(x.asText()));
        return list;
    }
}
