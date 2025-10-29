package com.example.demo1;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.event.ActionEvent;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.Properties;
import java.util.List;
import java.io.InputStream;
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
