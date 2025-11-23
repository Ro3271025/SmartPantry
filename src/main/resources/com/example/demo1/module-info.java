module com.example.demo1 {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.base;
    requires com.google.auth.oauth2;
    requires google.cloud.core;
    requires google.cloud.firestore;
    requires com.google.api.apicommon;
    requires firebase.admin;
    requires com.google.gson;
    requires java.desktop;
    requires javafx.media;
    requires com.google.auth;
    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires org.kordamp.bootstrapfx.core;

    requires java.net.http;
    requires com.fasterxml.jackson.databind;
    opens Controllers to javafx.fxml;
    opens ai;

    opens com.example.demo1 to javafx.fxml;
    exports com.example.demo1;
}