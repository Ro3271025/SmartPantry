package com.example.demo1;

import com.github.sarxos.webcam.Webcam;
import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.embed.swing.SwingFXUtils;


import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Camera-based barcode scanner using webcam and ZXing
 */
public class CameraBarcodeScanner {

    private Webcam webcam;
    private AtomicBoolean scanning = new AtomicBoolean(false);
    private Thread scannerThread;
    private Stage scannerStage;
    private ImageView imageView;
    private Label statusLabel;

    /**
     * Open camera scanner window and scan for barcodes
     * @param onBarcodeDetected Callback when barcode is found
     */
    public void startScanning(Consumer<String> onBarcodeDetected) {
        // IMPORTANT: Ensure we're on the JavaFX Application Thread
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> startScanning(onBarcodeDetected));
            return;
        }

        // Check if webcam is available
        webcam = Webcam.getDefault();
        if (webcam == null) {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.ERROR
            );
            alert.setTitle("Camera Not Found");
            alert.setHeaderText("No webcam detected");
            alert.setContentText("Please connect a webcam and try again.");
            alert.showAndWait();
            return;
        }

        createScannerWindow(onBarcodeDetected);
    }

    /**
     * Create the scanner window with camera preview
     */
    private void createScannerWindow(Consumer<String> onBarcodeDetected) {
        scannerStage = new Stage();
        scannerStage.setTitle("Scan Barcode");
        scannerStage.initModality(Modality.APPLICATION_MODAL);

        // Image view for camera feed
        imageView = new ImageView();
        imageView.setFitWidth(640);
        imageView.setFitHeight(480);
        imageView.setPreserveRatio(true);
        imageView.setStyle("-fx-border-color: #2f7a64; -fx-border-width: 3;");

        // Status label
        statusLabel = new Label("Scanning... Hold barcode in front of camera");
        statusLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #2f7a64; -fx-font-weight: bold;");

        // Instruction label
        Label instructionLabel = new Label(
                "• Hold barcode steady in front of camera\n" +
                        "• Ensure good lighting\n" +
                        "• Keep barcode flat and visible"
        );
        instructionLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");

        // Cancel button
        Button cancelButton = new Button("Cancel");
        cancelButton.setStyle(
                "-fx-background-color: #e0e0e0; -fx-text-fill: #333; " +
                        "-fx-font-weight: bold; -fx-padding: 10 30;"
        );
        cancelButton.setOnAction(e -> stopScanning());

        // Manual entry button
        Button manualButton = new Button("Enter Manually");
        manualButton.setStyle(
                "-fx-background-color: white; -fx-text-fill: #2f7a64; " +
                        "-fx-border-color: #2f7a64; -fx-border-width: 2; " +
                        "-fx-font-weight: bold; -fx-padding: 10 30;"
        );
        manualButton.setOnAction(e -> {
            stopScanning();
            showManualEntry(onBarcodeDetected);
        });

        // Button container
        HBox buttonBox = new HBox(15, manualButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(15));

        // Layout
        VBox topBox = new VBox(10, statusLabel, instructionLabel);
        topBox.setAlignment(Pos.CENTER);
        topBox.setPadding(new Insets(15));
        topBox.setStyle("-fx-background-color: #f5f5f5;");

        BorderPane root = new BorderPane();
        root.setTop(topBox);
        root.setCenter(imageView);
        root.setBottom(buttonBox);
        root.setStyle("-fx-background-color: #f5f5f5;");

        Scene scene = new Scene(root);
        scannerStage.setScene(scene);
        scannerStage.setOnCloseRequest(e -> stopScanning());

        // Start camera and scanning in background thread
        startCamera(onBarcodeDetected);

        scannerStage.show();
    }

    /**
     * Start the camera and begin scanning for barcodes
     */
    private void startCamera(Consumer<String> onBarcodeDetected) {
        scanning.set(true);

        scannerThread = new Thread(() -> {
            try {
                // Open webcam
                webcam.setViewSize(new java.awt.Dimension(640, 480));
                webcam.open();

                // Create barcode reader
                MultiFormatReader reader = new MultiFormatReader();

                while (scanning.get()) {
                    if (webcam.isOpen()) {
                        BufferedImage image = webcam.getImage();

                        if (image != null) {
                            // Update camera preview on JavaFX thread
                            Platform.runLater(() ->
                                    imageView.setImage(SwingFXUtils.toFXImage(image, null))
                            );

                            // Try to decode barcode
                            try {
                                LuminanceSource source = new BufferedImageLuminanceSource(image);
                                BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
                                Result result = reader.decode(bitmap);

                                if (result != null) {
                                    String barcode = result.getText();

                                    // Barcode found!
                                    Platform.runLater(() -> {
                                        statusLabel.setText("✓ Barcode detected: " + barcode);
                                        statusLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: green; -fx-font-weight: bold;");
                                    });

                                    // Wait a moment so user can see the success message
                                    Thread.sleep(500);

                                    // Stop scanning and return result
                                    stopScanning();
                                    Platform.runLater(() -> onBarcodeDetected.accept(barcode));
                                    break;
                                }
                            } catch (NotFoundException e) {
                                // No barcode in this frame, continue scanning
                            } catch (ReaderException e) {
                                // Decoding error, continue scanning
                            }
                        }
                    }

                    // Small delay to prevent high CPU usage
                    Thread.sleep(100);
                }

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    statusLabel.setText("✗ Error: " + e.getMessage());
                    statusLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: red; -fx-font-weight: bold;");
                });
            } finally {
                if (webcam != null && webcam.isOpen()) {
                    webcam.close();
                }
            }
        });

        scannerThread.setDaemon(true);
        scannerThread.start();
    }

    /**
     * Stop scanning and close camera
     */
    private void stopScanning() {
        scanning.set(false);

        if (webcam != null && webcam.isOpen()) {
            webcam.close();
        }

        if (scannerStage != null) {
            Platform.runLater(() -> {
                if (scannerStage.isShowing()) {
                    scannerStage.close();
                }
            });
        }
    }

    /**
     * Show manual barcode entry dialog
     */
    private void showManualEntry(Consumer<String> onBarcodeDetected) {
        Platform.runLater(() -> {
            javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog();
            dialog.setTitle("Enter Barcode Manually");
            dialog.setHeaderText("Enter Product Barcode");
            dialog.setContentText("Barcode:");

            dialog.showAndWait().ifPresent(barcode -> {
                if (!barcode.trim().isEmpty()) {
                    onBarcodeDetected.accept(barcode.trim());
                }
            });
        });
    }
}