package com.chessgame;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

public class ChessBoard {

    private final int SIZE = 8;
    private GridPane boardUI;
    private String[][] boardState = new String[SIZE][SIZE]; // boardState[row][col], row 0 = rank 8

    public ChessBoard() {
        boardUI = new GridPane();
        initStartingPosition(); // fill boardState with starting pieces
        createBoardUI();
    }

    /**
     * Initialize the boardState with the standard chess starting position.
     * row 0 = Black's back rank (r n b q k b n r)
     * row 1 = Black pawns (p)
     * row 6 = White pawns (P)
     * row 7 = White's back rank (R N B Q K B N R)
     */
    private void initStartingPosition() {
        // Black back rank (row 0)
        boardState[0] = new String[] { "r", "n", "b", "q", "k", "b", "n", "r" };
        // Black pawns (row 1)
        boardState[1] = new String[] { "p", "p", "p", "p", "p", "p", "p", "p" };
        // Empty ranks (rows 2..5)
        for (int r = 2; r <= 5; r++) {
            for (int c = 0; c < SIZE; c++) {
                boardState[r][c] = "";
            }
        }
        // White pawns (row 6)
        boardState[6] = new String[] { "P", "P", "P", "P", "P", "P", "P", "P" };
        // White back rank (row 7)
        boardState[7] = new String[] { "R", "N", "B", "Q", "K", "B", "N", "R" };
    }

    /** Builds the GridPane UI based on current boardState. */
    private void createBoardUI() {
        boardUI.getChildren().clear();
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                Color color = (row + col) % 2 == 0 ? Color.BEIGE : Color.SADDLEBROWN;
                Square square = new Square(row, col, color);

                String piece = boardState[row][col];
                if (piece != null && !piece.isEmpty()) {
                    Label pieceLabel = new Label(piece);
                    pieceLabel.setStyle("-fx-font-size: 32px; -fx-font-weight: bold;");
                    pieceLabel.setAlignment(Pos.CENTER);

                    // color letter: uppercase = white (use black text), lowercase = black (use black text too)
                    // (You can change colors here if you want different text colors per side)
                    pieceLabel.setTextFill(Color.BLACK);

                    StackPane.setAlignment(pieceLabel, Pos.CENTER);
                    square.getUI().getChildren().add(pieceLabel);
                }

                boardUI.add(square.getUI(), col, row);
            }
        }
    }

    /** Resets the logical board and UI to the default starting position. */
    public void resetBoard() {
        initStartingPosition();
        createBoardUI();
    }

    /**
     * Returns a FEN string for the current boardState.
     * Example starting FEN:
     * rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1
     */
    public String getFEN() {
        StringBuilder fen = new StringBuilder();
        for (int row = 0; row < SIZE; row++) {
            int empty = 0;
            for (int col = 0; col < SIZE; col++) {
                String piece = boardState[row][col];
                if (piece == null || piece.isEmpty()) {
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
        // default side to move and extras; you can change these later programmatically
        fen.append(" w KQkq - 0 1");
        return fen.toString();
    }

    /** Returns the GridPane to place in the Scene. */
    public GridPane getBoardUI() {
        return boardUI;
    }

    /** Optional: update the board state at a given square and refresh UI */
    public void setPieceAt(int row, int col, String piece) {
        boardState[row][col] = piece == null ? "" : piece;
        createBoardUI();
    }

    /** Optional: get piece at a given square */
    public String getPieceAt(int row, int col) {
        return boardState[row][col];
    }
}
