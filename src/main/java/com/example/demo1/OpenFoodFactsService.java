package com.example.demo1;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;

/**
 * Service to fetch product data from UPCItemDB (primary) with Open Food Facts fallback
 */
public class OpenFoodFactsService {

    private static final String UPC_ITEM_DB_URL = "https://api.upcitemdb.com/prod/trial/lookup";
    private static final String OPEN_FOOD_FACTS_URL = "https://world.openfoodfacts.org/api/v2/product/";
    private static final String USER_AGENT = "SmartPantry - JavaFX App - Version 1.0";

    /**
     * Product data returned from APIs
     */
    public static class ProductData {
        private String name;
        private String category;
        private String quantity;
        private Integer expirationDays;
        private boolean found;
        private String errorMessage;
        private String source; // Track which API provided the data

        public ProductData() {
            this.found = false;
        }

        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public String getQuantity() { return quantity; }
        public void setQuantity(String quantity) { this.quantity = quantity; }

        public Integer getExpirationDays() { return expirationDays; }
        public void setExpirationDays(Integer expirationDays) { this.expirationDays = expirationDays; }

        public boolean isFound() { return found; }
        public void setFound(boolean found) { this.found = found; }

        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }

        public LocalDate getEstimatedExpirationDate() {
            if (expirationDays != null) {
                return LocalDate.now().plusDays(expirationDays);
            }
            return LocalDate.now().plusMonths(6);
        }
    }

    /**
     * Fetch product data by barcode - tries UPCItemDB first, then Open Food Facts
     * @param barcode The product barcode (UPC/EAN)
     * @return ProductData object with product information
     */
    public ProductData getProductByBarcode(String barcode) {
        ProductData product;

        // STEP 1: Try UPCItemDB first (best for US products)
        System.out.println("🔍 Searching UPCItemDB for barcode: " + barcode);
        product = searchUPCItemDB(barcode);

        if (product.isFound()) {
            System.out.println("✓ Found in UPCItemDB!");
            product.setSource("UPCItemDB (US Database)");
            return product;
        }

        // STEP 2: If not found, try Open Food Facts as fallback (best for European products)
        System.out.println("⚠ Not found in UPCItemDB. Trying Open Food Facts...");
        product = searchOpenFoodFacts(barcode);

        if (product.isFound()) {
            System.out.println("✓ Found in Open Food Facts!");
            product.setSource("Open Food Facts");
            return product;
        }

        // STEP 3: Product not found in either database
        System.out.println("✗ Product not found in any database");
        product.setErrorMessage("Product not found in UPCItemDB or Open Food Facts databases");
        return product;
    }

    /**
     * Search UPCItemDB database (PRIMARY - US products)
     * Free tier: 100 requests/day, no API key needed
     */
    private ProductData searchUPCItemDB(String barcode) {
        ProductData product = new ProductData();

        try {
            String urlString = UPC_ITEM_DB_URL + "?upc=" + barcode;
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int responseCode = conn.getResponseCode();

            if (responseCode == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String inputLine;

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                JsonObject jsonResponse = JsonParser.parseString(response.toString()).getAsJsonObject();

                // Check if product was found
                if (jsonResponse.has("code") &&
                        jsonResponse.get("code").getAsString().equals("OK") &&
                        jsonResponse.has("items") &&
                        jsonResponse.get("items").isJsonArray()) {

                    JsonArray items = jsonResponse.getAsJsonArray("items");

                    if (items.size() > 0) {
                        product.setFound(true);
                        JsonObject item = items.get(0).getAsJsonObject();

                        // Extract product name (title)
                        if (item.has("title")) {
                            product.setName(item.get("title").getAsString());
                        }

                        // Extract brand (prepend to name if available)
                        if (item.has("brand") && !item.get("brand").isJsonNull()) {
                            String brand = item.get("brand").getAsString();
                            String currentName = product.getName();
                            if (currentName != null && !currentName.toLowerCase().contains(brand.toLowerCase())) {
                                product.setName(brand + " " + currentName);
                            }
                        }

                        // Extract category
                        String category = "Other";
                        if (item.has("category")) {
                            String upcCategory = item.get("category").getAsString();
                            category = mapUPCCategory(upcCategory);
                        }
                        product.setCategory(category);

                        // Extract quantity/size
                        if (item.has("size")) {
                            product.setQuantity(item.get("size").getAsString());
                        } else {
                            product.setQuantity("1 pcs"); // default
                        }

                        product.setExpirationDays(estimateExpirationDays(category, null));
                    }
                }
            } else if (responseCode == 404) {
                product.setFound(false);
            }

            conn.disconnect();

        } catch (Exception e) {
            product.setFound(false);
            e.printStackTrace();
        }

        return product;
    }

    /**
     * Search Open Food Facts database (FALLBACK - European products)
     */
    private ProductData searchOpenFoodFacts(String barcode) {
        ProductData product = new ProductData();

        try {
            String urlString = OPEN_FOOD_FACTS_URL + barcode + ".json";
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int responseCode = conn.getResponseCode();

            if (responseCode == 404) {
                product.setFound(false);
                conn.disconnect();
                return product;
            }

            if (responseCode == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String inputLine;

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                JsonObject jsonResponse = JsonParser.parseString(response.toString()).getAsJsonObject();

                if (jsonResponse.has("status") && jsonResponse.get("status").getAsInt() == 1) {
                    product.setFound(true);
                    JsonObject productObj = jsonResponse.getAsJsonObject("product");

                    // Extract product name
                    if (productObj.has("product_name") && !productObj.get("product_name").isJsonNull()) {
                        product.setName(productObj.get("product_name").getAsString());
                    } else if (productObj.has("product_name_en") && !productObj.get("product_name_en").isJsonNull()) {
                        product.setName(productObj.get("product_name_en").getAsString());
                    }

                    // Extract quantity
                    if (productObj.has("quantity") && !productObj.get("quantity").isJsonNull()) {
                        product.setQuantity(productObj.get("quantity").getAsString());
                    }

                    // Extract category
                    if (productObj.has("categories") && !productObj.get("categories").isJsonNull()) {
                        String categories = productObj.get("categories").getAsString();
                        product.setCategory(mapCategory(categories));
                    }

                    product.setExpirationDays(estimateExpirationDays(product.getCategory(), productObj));
                }
            }

            conn.disconnect();

        } catch (Exception e) {
            product.setFound(false);
            e.printStackTrace();
        }

        return product;
    }

    /**
     * Map UPCItemDB categories to our app categories
     */
    private String mapUPCCategory(String upcCategory) {
        if (upcCategory == null || upcCategory.isEmpty()) return "Other";

        upcCategory = upcCategory.toLowerCase();

        // UPCItemDB uses categories like "Food > Beverages", "Health & Beauty > Food & Nutrition"
        if (upcCategory.contains("dairy") || upcCategory.contains("milk") ||
                upcCategory.contains("cheese") || upcCategory.contains("yogurt")) {
            return "Dairy";
        } else if (upcCategory.contains("vegetable") || upcCategory.contains("produce")) {
            return "Vegetables";
        } else if (upcCategory.contains("fruit")) {
            return "Fruits";
        } else if (upcCategory.contains("meat") || upcCategory.contains("poultry") ||
                upcCategory.contains("seafood")) {
            return "Meat";
        } else if (upcCategory.contains("grain") || upcCategory.contains("bread") ||
                upcCategory.contains("cereal") || upcCategory.contains("pasta")) {
            return "Grains";
        } else if (upcCategory.contains("beverage") || upcCategory.contains("drink") ||
                upcCategory.contains("soda") || upcCategory.contains("juice")) {
            return "Beverages";
        } else if (upcCategory.contains("snack") || upcCategory.contains("candy") ||
                upcCategory.contains("chip") || upcCategory.contains("cookie")) {
            return "Snacks";
        } else if (upcCategory.contains("food")) {
            return "Other"; // Generic food category
        }

        return "Other";
    }

    /**
     * Map Open Food Facts categories to our app categories
     */
    private String mapCategory(String categories) {
        if (categories == null || categories.isEmpty()) return "Other";

        categories = categories.toLowerCase();

        if (categories.contains("dairy") || categories.contains("milk") ||
                categories.contains("cheese") || categories.contains("yogurt")) {
            return "Dairy";
        } else if (categories.contains("vegetable") || categories.contains("veggies")) {
            return "Vegetables";
        } else if (categories.contains("fruit")) {
            return "Fruits";
        } else if (categories.contains("meat") || categories.contains("poultry") ||
                categories.contains("fish") || categories.contains("seafood")) {
            return "Meat";
        } else if (categories.contains("grain") || categories.contains("bread") ||
                categories.contains("pasta") || categories.contains("cereal")) {
            return "Grains";
        } else if (categories.contains("beverage") || categories.contains("drink") ||
                categories.contains("juice") || categories.contains("soda")) {
            return "Beverages";
        } else if (categories.contains("snack") || categories.contains("chip") ||
                categories.contains("cookie") || categories.contains("candy")) {
            return "Snacks";
        }

        return "Other";
    }

    /**
     * Estimate expiration days based on category
     */
    private Integer estimateExpirationDays(String category, JsonObject productObj) {
        if (productObj != null && productObj.has("expiration_date") &&
                !productObj.get("expiration_date").isJsonNull()) {
            // Could parse this if available
        }

        if (category == null) return 180;

        switch (category) {
            case "Dairy":
                return 14; // 2 weeks
            case "Vegetables":
                return 7; // 1 week
            case "Fruits":
                return 7; // 1 week
            case "Meat":
                return 5; // 5 days
            case "Grains":
                return 180; // 6 months
            case "Beverages":
                return 90; // 3 months
            case "Snacks":
                return 90; // 3 months
            default:
                return 180; // 6 months default
        }
    }

    /**
     * Parse quantity string to extract numeric value and unit
     * Examples: "1L", "500g", "12 oz"
     */
    public static class ParsedQuantity {
        public int numeric = 1;
        public String unit = "pcs";

        public ParsedQuantity(String quantityStr) {
            if (quantityStr == null || quantityStr.isEmpty()) return;

            try {
                // Remove spaces and convert to lowercase
                quantityStr = quantityStr.trim().toLowerCase();

                // Extract numbers
                String numericPart = quantityStr.replaceAll("[^0-9.]", "");
                if (!numericPart.isEmpty()) {
                    this.numeric = (int) Double.parseDouble(numericPart);
                }

                // Extract unit
                if (quantityStr.contains("kg")) {
                    this.unit = "kg";
                } else if (quantityStr.contains("g")) {
                    this.unit = "kg";
                    this.numeric = this.numeric / 1000; // convert g to kg
                } else if (quantityStr.contains("l")) {
                    this.unit = "L";
                } else if (quantityStr.contains("ml")) {
                    this.unit = "L";
                    this.numeric = this.numeric / 1000; // convert ml to L
                } else if (quantityStr.contains("oz")) {
                    this.unit = "oz";
                }

            } catch (Exception e) {
                // Keep defaults
            }
        }
    }
}