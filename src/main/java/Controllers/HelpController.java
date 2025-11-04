package Controllers;
import javafx.fxml.FXML;
import javafx.event.ActionEvent;

import java.io.IOException;

import javafx.scene.control.Button;
public class HelpController extends BaseController {
    @FXML
    private Button goBackButton;
    @FXML
    private Button twoSignUpButton;
    @FXML
    public void goBackButtonOnAction(ActionEvent event) throws IOException {
        switchScene(event, "Login");
    }
    public void twoSignUpButtonOnAction(ActionEvent event) throws IOException {
        switchScene(event, "SignUp");
    }

}
