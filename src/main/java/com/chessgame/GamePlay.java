package com.chessgame;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

public class GamePlay extends Application {

    @Override
    public void start(Stage stage) {
        ChessBoard chessBoard = new ChessBoard();

        // ✅ Create Suggest Move button
        Button suggestButton = new Button("Suggest Move");
        suggestButton.setStyle("-fx-font-size: 16px; -fx-padding: 8px 16px;");

        // When clicked, send FEN to Stockfish
        suggestButton.setOnAction(e -> {
            String fen = chessBoard.getFEN();
            System.out.println("Current FEN: " + fen);

            String response = StockfishAPI.getBestMove(fen, 15);
            System.out.println("♟️ Suggested Move: " + response);
        });

        // ✅ Use an HBox for the bottom area so it's always visible
        HBox bottomBox = new HBox();
        bottomBox.setPadding(new Insets(10));
        bottomBox.setSpacing(10);
        bottomBox.getChildren().add(suggestButton);
        bottomBox.setStyle("-fx-alignment: center; -fx-background-color: #333;");

        // ✅ Main layout
        BorderPane root = new BorderPane();
        root.setCenter(chessBoard.getBoardUI());
        root.setBottom(bottomBox);

        Scene scene = new Scene(root, 600, 650);
        stage.setScene(scene);
        stage.setTitle("Chess Game with Stockfish Suggestion");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
