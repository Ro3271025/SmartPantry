package Controllers;

import com.example.demo1.FireBaseKeys;
import com.example.demo1.OAuthKeys;
import com.example.demo1.UserSession;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;
import javafx.fxml.FXML;
import javafx.event.ActionEvent;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;

import java.io.*;
import java.util.*;

import javafx.scene.control.Button;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.OutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.util.Scanner;

public class  LoginController extends BaseController {
    @FXML
    private Pane root;
    @FXML
    private Button loginButton;
    @FXML
    private Button signUpButton;
    @FXML
    private Button HelpButton;
    @FXML
    private Button LCancelButton;
    @FXML
    private Button googleLoginButton;
    @FXML
    private Label loginErrorLabel;
    @FXML
    private TextField usernameTextField;
    @FXML
    private PasswordField passwordTextField;

    //@FXML
    public void loginErrorLabelOnAction(ActionEvent event) throws IOException {
        String username = usernameTextField.getText();
        String password = passwordTextField.getText();

        if (username.isBlank() || password.isBlank()) {
            loginErrorLabel.setText("Username or Password is incorrect. Please try again.");
            return;
        }
    }

    @FXML
    public void LCancelButtonOnAction(ActionEvent event) throws IOException {
        switchScene(event, "MainScreen");
    }

    @FXML
    public void signUpButtonOnAction(ActionEvent event) throws IOException {
        switchScene(event, "SignUp");
    }

    public void HelpButtonOnAction(ActionEvent event) throws IOException {
        switchScene(event, "Help");
    }

    public void loginButtonOnAction(ActionEvent event) throws IOException {
        String username = usernameTextField.getText();
        String password = passwordTextField.getText();

        if (username.isBlank() || password.isBlank()) {
            loginErrorLabel.setText("Username or Password is incorrect. Please try again.");
            return;
        }

        String userId = authenticateUser(username, password);
        if (userId != null) {
            System.out.println("Login successful!");
            UserSession.setCurrentUserId(userId);
            switchScene(event, "PantryDashboard");
        } else {
            System.out.println("Login failed. Check your credentials.");
            loginErrorLabel.setText("Login failed. Check your credentials.");
        }
    }


    @FXML
    public void googleLoginButtonOnAction(ActionEvent event) throws IOException {
        String authUrl = "https://accounts.google.com/o/oauth2/v2/auth"
                + "?client_id=" + OAuthKeys.GOOGLE_CLIENT_ID
                + "&redirect_uri=http://localhost:8080"
                + "&response_type=code"
                + "&scope=openid%20email%20profile";

        System.out.println("Opening browser for Google login...");
        java.awt.Desktop.getDesktop().browse(java.net.URI.create(authUrl));

        // Automatically wait for Google redirect
        System.out.println("Waiting for Google redirect...");
        String code = waitForOAuthCode();
        System.out.println("Received authorization code: " + code);

        // Exchange code for tokens and authenticate with Firebase
        exchangeCodeForToken(code, event);
    }

    // Exchange auth code for ID token
    private void exchangeCodeForToken(String code, ActionEvent event) throws IOException {
        URL url = new URL("https://oauth2.googleapis.com/token");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setDoOutput(true);

        String params = String.format(
                "code=%s&client_id=%s&client_secret=%s&redirect_uri=http://localhost:8080&grant_type=authorization_code",
                code, OAuthKeys.GOOGLE_CLIENT_ID, OAuthKeys.GOOGLE_CLIENT_SECRET
        );

        try (OutputStream os = conn.getOutputStream()) {
            os.write(params.getBytes());
        }

        Scanner scanner = new Scanner(conn.getInputStream(), "UTF-8");
        String jsonResponse = scanner.useDelimiter("\\A").next();
        scanner.close();

        com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(jsonResponse).getAsJsonObject();
        String idToken = json.get("id_token").getAsString();

        // Now authenticate with Firebase
        firebaseAuthWithGoogle(idToken, event);
    }

    private void firebaseAuthWithGoogle(String idToken, ActionEvent event) throws IOException {
        String firebaseUrl = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=" + API_KEY;

        URL url = new URL(firebaseUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setDoOutput(true);

        // Firebase signInWithIdp request body
        String jsonInput = String.format(
                "{"
                        + "\"postBody\":\"id_token=%s&providerId=google.com\","
                        + "\"requestUri\":\"http://localhost\","
                        + "\"returnSecureToken\":true,"
                        + "\"returnIdpCredential\":true"
                        + "}",
                idToken
        );

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonInput.getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        int responseCode = conn.getResponseCode();

        if (responseCode == HttpURLConnection.HTTP_OK) {
            // Success: user authenticated
            Scanner scanner = new Scanner(conn.getInputStream(), "UTF-8");
            String jsonResponse = scanner.useDelimiter("\\A").next();
            scanner.close();

            JsonObject response = JsonParser.parseString(jsonResponse).getAsJsonObject();

            String email = response.get("email").getAsString();
            String firebaseIdToken = response.get("idToken").getAsString();

            System.out.println("Google Sign-In successful for: " + email);
            System.out.println("Firebase ID Token: " + firebaseIdToken);

            // Store user session
            UserSession.setCurrentUserId(email);

            // (Optional) Store user info in Firestore
            try {
                Firestore db = FirestoreClient.getFirestore();
                Map<String, Object> userData = new HashMap<>();
                userData.put("Email", email);
                userData.put("Provider", "Google");
                db.collection("users").document(email).set(userData);
                System.out.println("User stored in Firestore.");
            } catch (Exception ex) {
                System.out.println("Firestore write skipped: " + ex.getMessage());
            }

            // Switch to Dashboard scene
            switchScene(event, "PantryDashboard");

        } else {
            // Handle error
            Scanner errorScanner = new Scanner(conn.getErrorStream(), "UTF-8");
            String errorResponse = errorScanner.useDelimiter("\\A").next();
            errorScanner.close();

            System.out.println("Firebase Error: " + errorResponse);
        }

        conn.disconnect();
    }

    private String waitForOAuthCode() throws IOException {
        HttpServer server = HttpServer.create(new java.net.InetSocketAddress(8080), 0);
        final StringBuilder codeHolder = new StringBuilder();

        server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String query = exchange.getRequestURI().getQuery();
                String response = "<html><body><h2>You may close this tab now.</h2></body></html>";

                if (query != null && query.contains("code=")) {
                    String code = query.split("code=")[1].split("&")[0];
                    codeHolder.append(code);
                    response = "<html><body><h2>Login successful! You can close this tab.</h2></body></html>";
                }

                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                // Stop the server after handling once
                new Thread(() -> {
                    try { Thread.sleep(1000); server.stop(0); } catch (InterruptedException ignored) {}
                }).start();
            }
        });

        server.start();
        System.out.println("Listening on http://localhost:8080 for OAuth redirect...");

        // Wait until the code is received
        while (codeHolder.length() == 0) {
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        }

        return codeHolder.toString();
    }

    private static final String API_KEY = FireBaseKeys.WEB_API_KEY;

    private String authenticateUser(String email, String password) {
        try {
            URL url = new URL("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + API_KEY);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);


            String jsonInputString = String.format(
                    "{\"email\":\"%s\",\"password\":\"%s\",\"returnSecureToken\":true}", email, password
            );


            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            Scanner scanner = new Scanner(conn.getInputStream(), "UTF-8");
            String jsonResponse = scanner.useDelimiter("\\A").next();
            scanner.close();


            JsonObject response = JsonParser.parseString(jsonResponse).getAsJsonObject();
            String idToken = response.get("idToken").getAsString();

            // Return the email as the user ID
            return email;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}