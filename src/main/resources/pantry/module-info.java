module rodolfo.pantrydashboard {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires org.kordamp.bootstrapfx.core;

    opens rodolfo.pantrydashboard to javafx.fxml;
    exports rodolfo.pantrydashboard;
}