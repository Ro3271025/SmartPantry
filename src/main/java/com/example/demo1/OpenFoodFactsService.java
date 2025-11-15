package com.example.demo1;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;

/**
 * Service to fetch product data from Open Food Facts API
 */
public class OpenFoodFactsService {

    private static final String API_BASE_URL = "https://world.openfoodfacts.org/api/v2/product/";
    private static final String USER_AGENT = "SmartPantry - JavaFX App - Version 1.0";

    /**
     * Product data returned from Open Food Facts
     */
    public static class ProductData {
        private String name;
        private String category;
        private String quantity;
        private Integer expirationDays; // estimated days until expiration
        private boolean found;
        private String errorMessage;

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

        public LocalDate getEstimatedExpirationDate() {
            if (expirationDays != null) {
                return LocalDate.now().plusDays(expirationDays);
            }
            return LocalDate.now().plusMonths(6); // default to 6 months
        }
    }

    /**
     * Fetch product data by barcode
     * @param barcode The product barcode (UPC/EAN)
     * @return ProductData object with product information
     */
    public ProductData getProductByBarcode(String barcode) {
        ProductData product = new ProductData();

        try {
            String urlString = API_BASE_URL + barcode + ".json";
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int responseCode = conn.getResponseCode();

            if (responseCode == 404) {
                product.setFound(false);
                product.setErrorMessage("Product not found in database. Barcode: " + barcode);
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

                // Parse JSON response
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

                    // Estimate expiration based on category
                    product.setExpirationDays(estimateExpirationDays(product.getCategory(), productObj));

                } else {
                    product.setFound(false);
                    product.setErrorMessage("Product not found in Open Food Facts database");
                }

            } else {
                product.setFound(false);
                product.setErrorMessage("API request failed with code: " + responseCode);
            }

            conn.disconnect();

        } catch (Exception e) {
            product.setFound(false);
            product.setErrorMessage("Error: " + e.getMessage());
            e.printStackTrace();
        }

        return product;
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
     * Estimate expiration days based on category and product data
     */
    private Integer estimateExpirationDays(String category, JsonObject productObj) {
        // Check if product has expiration date info
        if (productObj.has("expiration_date") && !productObj.get("expiration_date").isJsonNull()) {
            // You could parse this if available
        }

        // Default estimates based on category
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