package com.chessgame;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class ChessBoard {

    private final GridPane grid;
    private final Map<Integer, Image> pieceImages;
    private boolean isWhitePerspective = true; // Default: White at bottom

    // Colors for the board squares
    private final Color lightSquareColor = Color.web("#F0D9B5");
    private final Color darkSquareColor = Color.web("#B58863");

    public ChessBoard() {
        this.grid = new GridPane();
        this.grid.setAlignment(Pos.CENTER);
        this.pieceImages = new HashMap<>();
        
        loadResources();
        initializeBoard();
    }

    public Node getBoardUI() {
        return grid;
    }

    public void setPerspective(boolean whiteAtBottom) {
        this.isWhitePerspective = whiteAtBottom;
        // Re-render immediately if we have data, otherwise just waits for next update
        refreshUI(); 
    }
    
    public boolean isWhitePerspective() {
        return isWhitePerspective;
    }

    private void loadResources() {
        // Map integer constants from ChessGameTracker to filenames
        // Assuming files are named like "w_pawn.png", "b_knight.png" in resources/pieces/
        mapImage(ChessGameTracker.W_PAWN, "w_pawn.png");
        mapImage(ChessGameTracker.W_ROOK, "w_rook.png");
        mapImage(ChessGameTracker.W_KNIGHT, "w_knight.png");
        mapImage(ChessGameTracker.W_BISHOP, "w_bishop.png");
        mapImage(ChessGameTracker.W_QUEEN, "w_queen.png");
        mapImage(ChessGameTracker.W_KING, "w_king.png");

        mapImage(ChessGameTracker.B_PAWN, "b_pawn.png");
        mapImage(ChessGameTracker.B_ROOK, "b_rook.png");
        mapImage(ChessGameTracker.B_KNIGHT, "b_knight.png");
        mapImage(ChessGameTracker.B_BISHOP, "b_bishop.png");
        mapImage(ChessGameTracker.B_QUEEN, "b_queen.png");
        mapImage(ChessGameTracker.B_KING, "b_king.png");
    }

    private void mapImage(int pieceCode, String filename) {
        try {
            String path = "/pieces/" + filename;
            InputStream is = getClass().getResourceAsStream(path);
            if (is != null) {
                pieceImages.put(pieceCode, new Image(is));
            } else {
                System.err.println("Could not find image: " + path);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Initializes the grid structure (8x8) with background rectangles.
     */
    private void initializeBoard() {
        grid.getChildren().clear();
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                StackPane square = new StackPane();
                Rectangle bg = new Rectangle(60, 60); // Square size
                
                // Color logic (checkerboard pattern)
                boolean isLight = (row + col) % 2 == 0;
                bg.setFill(isLight ? lightSquareColor : darkSquareColor);
                
                square.getChildren().add(bg);
                grid.add(square, col, row);
            }
        }
    }

    /**
     * Updates the visual board based on the Tracker's logical board state.
     * Handles the coordinate transformation for perspective.
     */
    public void updateBoard(int[][] logicalBoard) {
        // Clear only the pieces, keep the background squares
        // Actually simpler to just rebuild the contents of the StackPanes
        
        for (int logicalRow = 0; logicalRow < 8; logicalRow++) {
            for (int logicalCol = 0; logicalCol < 8; logicalCol++) {
                
                // 1. Determine Visual Coordinates based on perspective
                int visualRow, visualCol;
                
                if (isWhitePerspective) {
                    // White at bottom (Rank 0 is at bottom visual row 7)
                    visualRow = 7 - logicalRow;
                    visualCol = logicalCol;
                } else {
                    // Black at bottom (Rank 7 is at bottom visual row 7)
                    // So Logic Row 0 (White) is at top visual row 0
                    visualRow = logicalRow;
                    // Logic Col 0 (File A) is on the RIGHT side for Black perspective
                    visualCol = 7 - logicalCol;
                }

                // 2. Get the StackPane at this grid position
                StackPane square = getSquareAt(visualRow, visualCol);
                if (square == null) continue;

                // 3. Clear previous piece image (keep background rectangle at index 0)
                if (square.getChildren().size() > 1) {
                    square.getChildren().remove(1, square.getChildren().size());
                }

                // 4. Add new piece image
                int piece = logicalBoard[logicalRow][logicalCol];
                if (piece != ChessGameTracker.EMPTY) {
                    Image img = pieceImages.get(piece);
                    if (img != null) {
                        ImageView iv = new ImageView(img);
                        iv.setFitWidth(50);
                        iv.setFitHeight(50);
                        iv.setPreserveRatio(true);
                        square.getChildren().add(iv);
                    } else {
                        // Fallback text if image missing
                        Label l = new Label(String.valueOf(piece));
                        square.getChildren().add(l);
                    }
                }
                
                // Optional: Add coordinate labels on edges
                addCoordinates(square, logicalRow, logicalCol, visualRow, visualCol);
            }
        }
    }
    
    // Helper to refresh with current state (used when flipping)
    // Note: requires the main app to pass the board, or we store a local copy.
    // For simplicity, we assume the main loop calls updateBoard() frequently.
    private void refreshUI() {
        // Logic handled by next GamePlay loop update
    }

    private StackPane getSquareAt(int row, int col) {
        for (Node node : grid.getChildren()) {
            if (GridPane.getRowIndex(node) == row && GridPane.getColumnIndex(node) == col) {
                return (StackPane) node;
            }
        }
        return null;
    }
    
    private void addCoordinates(StackPane square, int logRow, int logCol, int visRow, int visCol) {
        // Add File letters on bottom rank (Visual Row 7)
        if (visRow == 7) {
            String file = String.valueOf((char)('a' + logCol));
            Label l = new Label(file);
            StackPane.setAlignment(l, Pos.BOTTOM_RIGHT);
            l.setStyle("-fx-text-fill: " + ((visRow+visCol)%2==0 ? "#B58863" : "#F0D9B5") + "; -fx-font-size: 10px; -fx-padding: 2px;");
            square.getChildren().add(l);
        }
        // Add Rank numbers on left file (Visual Col 0)
        if (visCol == 0) {
            String rank = String.valueOf(logRow + 1);
            Label l = new Label(rank);
            StackPane.setAlignment(l, Pos.TOP_LEFT);
            l.setStyle("-fx-text-fill: " + ((visRow+visCol)%2==0 ? "#B58863" : "#F0D9B5") + "; -fx-font-size: 10px; -fx-padding: 2px;");
            square.getChildren().add(l);
        }
    }
}