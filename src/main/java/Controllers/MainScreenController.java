package Controllers;

import javafx.fxml.FXML;
import javafx.event.ActionEvent;
import javafx.stage.Stage;
import java.io.IOException;

import javafx.scene.control.Button;

public class MainScreenController extends BaseController {
    @FXML
    private Button msLoginButton;
    @FXML
    private Button msSignUpButton;
    @FXML
    private Button ExitButton;
    @FXML
    public void msLoginButtonOnAction(ActionEvent event) throws IOException {
        switchScene(event, "Login");
    }
    public void msSignUpButtonOnAction(ActionEvent event) throws IOException {
        switchScene(event, "SignUp");
    }
    public void ExitButtonOnAction(ActionEvent event) {
        Stage stage = (Stage) ExitButton.getScene().getWindow();
        stage.close();
    }
}