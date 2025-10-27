package com.example.demo1;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
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
import javafx.scene.input.MouseEvent;

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