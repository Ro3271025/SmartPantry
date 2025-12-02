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
import java.util.stream.Collectors;

/**
 * Local (FREE) AI via Ollama. No OpenAI key required.
 * Endpoint defaults to http://localhost:11434 (override with OLLAMA_BASE_URL).
 * Model defaults to "phi3:mini" (override with OLLAMA_MODEL).
 *
 * Now robust to code fences and comments in model output.
 */
public class AiRecipeService {

    private final String baseUrl = System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434");
    private final String model   = System.getenv().getOrDefault("OLLAMA_MODEL", "phi3:mini");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public List<RecipeDTO> generateRecipes(List<PantryItem> pantry, String userPrompt, int count) throws Exception {
        String pantryContext = (pantry == null || pantry.isEmpty())
                ? "None"
                : pantry.stream()
                .map(p -> {
                    String qty = (p.getQuantityLabel() != null && !p.getQuantityLabel().isBlank())
                            ? p.getQuantityLabel() : String.valueOf(p.getQuantityNumeric());
                    String cat = p.getCategory() != null ? p.getCategory() : "Uncategorized";
                    return "- " + p.getName() + " (" + qty + ", " + cat + ")";
                })
                .collect(Collectors.joining("\n"));

        // Why: forbid fences/comments so we get clean JSON.
        String sys = """
            You are a recipe generator. Output MUST be a single JSON object only, no prose, no code fences, no comments.
            EXACT schema:
            {
              "recipes":[
                {
                  "title":"string",
                  "ingredients":["string"],
                  "steps":["string"],
                  "missing_ingredients":["string"],
                  "estimated_time":"string",
                  "calories":0
                }
              ]
            }
            If unsure, leave optional fields as empty strings or empty arrays.
            """;

        String user = """
            Pantry items:
            %s

            Generate %d recipe(s). %s
            """.formatted(pantryContext, Math.max(1, count), (userPrompt == null ? "" : userPrompt));

        var payload = mapper.createObjectNode()
                .put("model", model)
                .put("stream", false);
        var messages = mapper.createArrayNode();
        messages.add(mapper.createObjectNode().put("role","system").put("content", sys));
        messages.add(mapper.createObjectNode().put("role","user").put("content", user));
        payload.set("messages", messages);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/chat"))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() / 100 != 2) {
            throw new IllegalStateException("Ollama error " + res.statusCode() + ": " + res.body());
        }

        // Ollama: { message: { content: "..." }, ... }
        String raw = mapper.readTree(res.body())
                .path("message").path("content").asText("").trim();

        // Sanitize before parsing JSON
        String jsonText = sanitizeToJson(raw);

        JsonNode json = mapper.readTree(jsonText);
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
                if (n.has("calories") && n.get("calories").canConvertToInt()) {
                    r.calories = n.get("calories").asInt();
                }
                out.add(r);
            }
        }
        return out;
    }


    // Checks if Ollama is reachable and the target model is available.
    public boolean isModelAvailable() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() / 100 != 2) return false;

            JsonNode root = mapper.readTree(res.body());
            String want = model.toLowerCase();
            for (JsonNode m : root.path("models")) {
                String name = m.path("name").asText("").toLowerCase();
                // accept exact tag or same family (e.g., "phi3:mini")
                if (name.equals(want) || name.startsWith(want) || want.startsWith(name)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }


    /** Remove fences/comments, trim to outer braces, drop trailing commas. */
    private String sanitizeToJson(String s) {
        if (s == null) return "{}";

        String t = s.trim();

        // Strip markdown code fences ``` or ```json
        if (t.startsWith("```")) {
            t = t.replaceFirst("^```(?:json|JSON)?\\s*", "");
            int fence = t.lastIndexOf("```");
            if (fence >= 0) t = t.substring(0, fence);
        }

        // Remove // line comments
        t = t.replaceAll("(?m)^\\s*//.*$", "");
        // Remove /* block comments */
        t = t.replaceAll("/\\*.*?\\*/", "");

        // Trim to the first {...} block if there is surrounding text
        if (!t.startsWith("{")) {
            int sIdx = t.indexOf('{');
            int eIdx = t.lastIndexOf('}');
            if (sIdx >= 0 && eIdx > sIdx) t = t.substring(sIdx, eIdx + 1);
        }

        // Remove trailing commas before } or ]
        t = t.replaceAll(",\\s*(\\})", "$1");
        t = t.replaceAll(",\\s*(\\])", "$1");

        // Fallback: if still not starting with {, return an empty object to avoid parser crash
        if (!t.startsWith("{")) t = "{}";

        return t.trim();
    }

    private List<String> toList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node != null && node.isArray()) node.forEach(x -> list.add(x.asText()));
        return list;
    }
}
