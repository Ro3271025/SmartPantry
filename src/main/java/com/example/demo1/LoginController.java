package com.example.demo1;

import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.event.ActionEvent;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

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

public class LoginController extends BaseController{
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
    private Button ForgotPasswordButton;
    @FXML
    private Button googleLoginButton;
    @FXML
    private Button facebookLoginButton;
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
        if (authenticateUser(username, password)) {
            System.out.println("Login successful!");
            switchScene(event, "PantryDashboard");
        } else {
            System.out.println("Login failed. Check your credentials.");
        }
    }
    public void forgotPasswordButtonOnAction(ActionEvent event) throws IOException {
        String email = usernameTextField.getText();

        if (email.isBlank() || email.isEmpty()) {
            loginErrorLabel.setText("Username or Email is incorrect. Please try again.");
        }
        boolean emailSent = sendPasswordResetEmail(email);
        if (emailSent) {
            loginErrorLabel.setText("Email for Password reset was sent successfully.");
        } else {
            loginErrorLabel.setText("Email for Password reset was not successfully sent. Please try again.");
        }
    }
    private boolean sendPasswordResetEmail(String email) {
        try {
            URL url = new URL("https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=" + API_KEY);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            String jsonInputString = String.format(
                    "{\"requestType\":\"PASSWORD_RESET\",\"email\":\"%s\"}",
                    email
            );

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                System.out.println("Password reset email sent.");
                return true;
            } else {
                Scanner errorScanner = new Scanner(connection.getErrorStream(), "UTF-8");
                String errorResponse = errorScanner.useDelimiter("\\A").next();
                errorScanner.close();

                JsonObject errorObj = JsonParser.parseString(errorResponse).getAsJsonObject();
                String message = errorObj.getAsJsonObject("error").get("message").getAsString();

                if (message.contains("EMAIL_NOT_FOUND")) {
                    loginErrorLabel.setText("Email not found. Please check your address.");
                } else {
                    loginErrorLabel.setText("Error: " + message);
                }

                System.out.println("Firebase error: " + message);
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            loginErrorLabel.setText("Network error. Please try again.");
            return false;
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
    @FXML
    public void facebookLoginButtonOnAction(ActionEvent event) throws IOException {
        // Step 1: Open Facebook OAuth
        String authUrl = "https://www.facebook.com/v18.0/dialog/oauth"
                + "?client_id=" + OAuthKeys.FACEBOOK_APP_ID
                + "&redirect_uri=" + OAuthKeys.FACEBOOK_REDIRECT_URI
                + "&response_type=code"
                + "&scope=email,public_profile";

        System.out.println("Opening browser for Facebook login...");
        java.awt.Desktop.getDesktop().browse(java.net.URI.create(authUrl));

        System.out.println("Waiting for Facebook redirect...");
        String code = waitForOAuthCode(); // same method you used for Google
        System.out.println("Received Facebook authorization code: " + code);

        // Step 2: Exchange code for Facebook access token
        exchangeFacebookCodeForToken(code, event);
    }
    private void exchangeFacebookCodeForToken(String code, ActionEvent event) {
        try {
            // Step 2: Get Facebook access token
            String tokenUrl = "https://graph.facebook.com/v18.0/oauth/access_token"
                    + "?client_id=" + OAuthKeys.FACEBOOK_APP_ID
                    + "&redirect_uri=" + OAuthKeys.FACEBOOK_REDIRECT_URI
                    + "&client_secret=" + OAuthKeys.FACEBOOK_APP_SECRET
                    + "&code=" + code;

            URL url = new URL(tokenUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(response.toString()).getAsJsonObject();
            String accessToken = json.get("access_token").getAsString();

            System.out.println("Facebook Access Token: " + accessToken);

            // Step 3: Authenticate with Firebase
            signInWithFacebookToFirebase(accessToken, event);

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error exchanging Facebook code for token: " + e.getMessage());
        }
    }
    private void signInWithFacebookToFirebase(String accessToken, ActionEvent event) {
        try {
            String firebaseUrl = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=" + OAuthKeys.FIREBASE_API_KEY;

            String postData = String.format(
                    "{"
                            + "\"postBody\": \"access_token=%s&providerId=facebook.com\","
                            + "\"requestUri\": \"http://localhost\","
                            + "\"returnSecureToken\": true"
                            + "}",
                    accessToken
            );


            HttpURLConnection conn = (HttpURLConnection) new URL(firebaseUrl).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = postData.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Success: Firebase authenticated user
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line.trim());
                }
                br.close();

                com.google.gson.JsonObject firebaseResponse = com.google.gson.JsonParser.parseString(response.toString()).getAsJsonObject();
                String email = firebaseResponse.has("email") ? firebaseResponse.get("email").getAsString() : "Unknown";
                String idToken = firebaseResponse.get("idToken").getAsString();

                System.out.println("Firebase Facebook Sign-In Successful for: " + email);
                System.out.println("Firebase ID Token: " + idToken);

                // Step 4: Store user info in Firestore
                try {
                    Firestore db = FirestoreClient.getFirestore();
                    Map<String, Object> userData = new HashMap<>();
                    userData.put("Email", email);
                    userData.put("Provider", "Facebook");
                    db.collection("users").document(email).set(userData);
                    System.out.println("User stored in Firestore.");
                } catch (Exception ex) {
                    System.out.println("Firestore write skipped: " + ex.getMessage());
                }

                // Step 5: Go to dashboard
                switchScene(event, "PantryDashboard");

            } else {
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "utf-8"));
                StringBuilder errorResponse = new StringBuilder();
                String errorLine;
                while ((errorLine = errorReader.readLine()) != null) {
                    errorResponse.append(errorLine.trim());
                }
                errorReader.close();
                System.out.println("Firebase Error: " + errorResponse);
            }

            conn.disconnect();

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error signing in with Firebase: " + e.getMessage());
        }
    }




    private static final String API_KEY = FireBaseKeys.WEB_API_KEY;

    private boolean authenticateUser(String email, String password) {
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
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }


    }

}
