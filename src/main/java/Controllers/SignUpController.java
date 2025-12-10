package Controllers;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.cloud.FirestoreClient;
import com.google.cloud.firestore.Firestore;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.Pane;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SignUpController extends BaseController {
    @FXML
    private Pane root;
    @FXML
    private TextField userNameTextField;
    @FXML
    private PasswordField passwordPasswordField1;
    @FXML
    private PasswordField passwordPasswordField2;
    @FXML
    private Button signUpButton;
    @FXML
    private TextField emailTextField;
    @FXML
    private Button cancelButton;
    @FXML
    public void cancelButtonOnAction(ActionEvent event) throws IOException {
        switchScene(event, "mainScreen");
    }

    @FXML
    private void createAccount(ActionEvent event) throws IOException {
        String fullName = userNameTextField.getText();
        String email1 = emailTextField.getText();
        String password1 = passwordPasswordField1.getText();
        String password2 = passwordPasswordField2.getText();

        if (fullName.isEmpty() || email1.isEmpty() || password1.isEmpty() || password2.isEmpty()) {
            System.out.println("Please fill in all fields.");
            return;
        }

        if (!password1.equals(password2)) {
            System.out.println("Passwords do not match");
        }
        try {
            UserRecord.CreateRequest request = new UserRecord.CreateRequest()
                    .setEmail(email1)
                    .setDisplayName(fullName)
                    .setEmailVerified(false)
                        .setPassword(password1)
                    .setDisabled(false);
            UserRecord userRecord = FirebaseAuth.getInstance().createUser(request);
            System.out.println("Firebase User Created: " + userRecord.getUid());

            Firestore db = FirestoreClient.getFirestore();
            Map<String, Object> userData = new HashMap<>();
            userData.put("FullName", fullName);
            userData.put("Email", email1);
            userData.put("Username", email1.split("@")[0]);

            db.collection("users").document(userRecord.getUid()).set(userData);
            System.out.println("User Information stored and saved to the database");
            switchScene(event, "Login");
        }catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error creating user");
        }

    }
    @FXML
    public void goBack(ActionEvent event) throws IOException {
        switchScene(event, "Login");
    }
}
