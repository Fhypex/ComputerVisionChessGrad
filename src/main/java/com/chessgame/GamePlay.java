package com.chessgame;

import java.io.File;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.core.Mat;

public class GamePlay extends Application {

    @Override
    public void start(Stage stage) {
        ChessBoard chessBoard = new ChessBoard();

        // ✅ Create camera viewer (live iPhone/Camo or webcam feed)
        CameraViewer cameraViewer = new CameraViewer();
        cameraViewer.startCamera();

        // ✅ Create Suggest Move button
        Button suggestButton = new Button("Suggest Move");
        suggestButton.setStyle("-fx-font-size: 16px; -fx-padding: 8px 16px;");

        // When clicked, send FEN to Stockfish
        suggestButton.setOnAction(e -> {
            String fen = chessBoard.getFEN();
            System.out.println("Current FEN: " + fen);

            String response = StockfishAPI.getBestMove(fen, 15);
            System.out.println("Suggested Move: " + response);
        });

        // ✅ Analyze from File button (your original one)
        Button analyzeButton = new Button("Analyze Board");
        analyzeButton.setStyle("-fx-font-size: 16px; -fx-padding: 8px 16px;");
        analyzeButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Chessboard Screenshot");
            fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg")
            );
            File file = fileChooser.showOpenDialog(stage);
            if (file != null) {
                ChessVision.analyzeBoard(file.getAbsolutePath());
            }
        });

        // ✅ NEW: Analyze from Live Camera button
        Button analyzeLiveButton = new Button("Analyze from Camera");
        analyzeLiveButton.setStyle("-fx-font-size: 16px; -fx-padding: 8px 16px;");
        analyzeLiveButton.setOnAction(e -> {
            Mat currentFrame = cameraViewer.captureCurrentFrame();
            if (currentFrame != null) {
                String filename = "camera_snapshot.jpg";
                Imgcodecs.imwrite(filename, currentFrame);
                System.out.println("📸 Snapshot saved: " + filename);
                ChessVision.analyzeBoard(filename);
            } else {
                System.out.println("❌ Could not capture frame from camera.");
            }
        });

        // ✅ Bottom bar
        HBox bottomBox = new HBox();
        bottomBox.setPadding(new Insets(10));
        bottomBox.setSpacing(10);
        bottomBox.getChildren().addAll(suggestButton, analyzeButton, analyzeLiveButton);
        bottomBox.setStyle("-fx-alignment: center; -fx-background-color: #333;");

        // ✅ Main layout
        BorderPane root = new BorderPane();
        root.setCenter(chessBoard.getBoardUI());
        root.setRight(cameraViewer); // show camera feed beside the board
        root.setBottom(bottomBox);

        Scene scene = new Scene(root, 1000, 650);
        stage.setScene(scene);
        stage.setTitle("Chess Game with Stockfish and Live Camera");
        stage.setOnCloseRequest(e -> cameraViewer.stopCamera());
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
