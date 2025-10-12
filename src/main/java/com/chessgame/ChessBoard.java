package com.chessgame;

import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

public class ChessBoard {

    private final int SIZE = 8;
    private GridPane boardUI;
    private Image pawnImage;
    private String[][] boardState = new String[SIZE][SIZE]; // FEN representation

    public ChessBoard() {
        boardUI = new GridPane();
        loadImages();
        createBoard();
    }

    private void loadImages() {
        try {
            pawnImage = new Image(getClass().getResourceAsStream("/pieces/pawn.jpg"));
        } catch (Exception e) {
            System.out.println("❌ Could not load pawn image: " + e.getMessage());
        }
    }

    private void createBoard() {
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                Color color = (row + col) % 2 == 0 ? Color.BEIGE : Color.SADDLEBROWN;
                Square square = new Square(row, col, color);

                String piece = ""; // empty square

                // Example basic setup (only pawns)
                if (row == 1) piece = "p"; // black pawn
                if (row == 6) piece = "P"; // white pawn

                if (!piece.isEmpty()) {
                    ImageView pawnView = new ImageView(pawnImage);
                    pawnView.setFitWidth(55);
                    pawnView.setFitHeight(55);
                    square.getUI().getChildren().add(pawnView);
                }

                boardState[row][col] = piece;
                boardUI.add(square.getUI(), col, row);
            }
        }
    }

    // FEN representation (simplified)
    public String getFEN() {
        StringBuilder fen = new StringBuilder();
        for (int row = 0; row < SIZE; row++) {
            int empty = 0;
            for (int col = 0; col < SIZE; col++) {
                String piece = boardState[row][col];
                if (piece.isEmpty()) {
                    empty++;
                } else {
                    if (empty > 0) {
                        fen.append(empty);
                        empty = 0;
                    }
                    fen.append(piece);
                }
            }
            if (empty > 0) fen.append(empty);
            if (row < SIZE - 1) fen.append('/');
        }
        fen.append(" w KQkq - 0 1"); // default additional info
        return fen.toString();
    }

    public GridPane getBoardUI() {
        return boardUI;
    }
}
