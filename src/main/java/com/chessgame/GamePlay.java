package com.chessgame;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public class GamePlay extends Application {

    @Override
    public void start(Stage stage) {
        ChessBoard chessBoard = new ChessBoard();

        Button suggestButton = new Button("Suggest Move");
        suggestButton.setOnAction(e -> {
            String fen = chessBoard.getFEN();
            System.out.println("Current FEN: " + fen);

            // Depth 15 is a good start
            String response = StockfishAPI.getBestMove(fen, 15);
            System.out.println("♟️ Suggested Move: " + response);
        });

        BorderPane root = new BorderPane();
        root.setCenter(chessBoard.getBoardUI());
        root.setBottom(suggestButton);

        Scene scene = new Scene(root, 500, 500);
        stage.setScene(scene);
        stage.setTitle("Chess Game with Stockfish Suggestion");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
