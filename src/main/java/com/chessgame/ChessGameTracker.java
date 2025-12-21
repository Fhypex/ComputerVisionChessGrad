package com.chessgame;

import java.util.*;

public class ChessGameTracker {

    // Piece constants
    public static final int EMPTY = 0;
    public static final int W_PAWN = 1, W_KNIGHT = 2, W_BISHOP = 3;
    public static final int W_ROOK = 4, W_QUEEN = 5, W_KING = 6;
    public static final int B_PAWN = -1, B_KNIGHT = -2, B_BISHOP = -3;
    public static final int B_ROOK = -4, B_QUEEN = -5, B_KING = -6;

    private int[][] board;
    private boolean whiteToMove;
    private boolean whiteKingMoved, blackKingMoved;
    private boolean whiteRookA1Moved, whiteRookH1Moved;
    private boolean blackRookA8Moved, blackRookH8Moved;
    private String lastEnPassantSquare;
    private List<String> moveHistory;
    
    // NEW: Game Over Flag
    private boolean isGameOver;

    public ChessGameTracker() {
        this.board = new int[8][8];
        this.whiteToMove = true; // Game always starts with white
        this.moveHistory = new ArrayList<>();
        this.isGameOver = false;
        initializeBoard();
        initializeCastlingRights();
    }

    private void initializeBoard() {
        // Initialize empty board
        for (int i = 0; i < 8; i++) {
            Arrays.fill(board[i], EMPTY);
        }

        // White pieces (rank 0 and 1)
        board[0][0] = W_ROOK; board[0][7] = W_ROOK;
        board[0][1] = W_KNIGHT; board[0][6] = W_KNIGHT;
        board[0][2] = W_BISHOP; board[0][5] = W_BISHOP;
        board[0][3] = W_QUEEN; board[0][4] = W_KING;
        for (int i = 0; i < 8; i++) board[1][i] = W_PAWN;

        // Black pieces (rank 6 and 7)
        for (int i = 0; i < 8; i++) board[6][i] = B_PAWN;
        board[7][0] = B_ROOK; board[7][7] = B_ROOK;
        board[7][1] = B_KNIGHT; board[7][6] = B_KNIGHT;
        board[7][2] = B_BISHOP; board[7][5] = B_BISHOP;
        board[7][3] = B_QUEEN; board[7][4] = B_KING;
    }

    private void initializeCastlingRights() {
        whiteKingMoved = false;
        blackKingMoved = false;
        whiteRookA1Moved = false;
        whiteRookH1Moved = false;
        blackRookA8Moved = false;
        blackRookH8Moved = false;
        lastEnPassantSquare = "-";
    }

    /**
     * ROBUST MOVE DETECTION:
     * Validates moves against the board state to ignore noise (splashes).
     */
    public String processChangedSquares(List<String> changedSquares) {
        if (isGameOver) {
            System.out.println(">>> GAME IS OVER. No more moves accepted. <<<");
            return null;
        }

        if (changedSquares == null || changedSquares.isEmpty()) {
            return null;
        }

        List<int[]> allChanges = new ArrayList<>();
        // FIX: Convert to lowercase to prevent IndexOutOfBoundsException
        for (String s : changedSquares) {
            allChanges.add(squareToCoords(s));
        }

        // 1. Identify potential Actors (Squares with current player's pieces)
        List<int[]> candidatesFrom = new ArrayList<>();
        for (int[] c : allChanges) {
            int p = board[c[0]][c[1]];
            if (p != EMPTY && isCorrectColor(p)) {
                candidatesFrom.add(c);
            }
        }

        // 2. CHECK CASTLING (Priority)
        String castlingMove = findCastlingMove(allChanges);
        if (castlingMove != null) return castlingMove;

        // 3. CHECK NORMAL MOVES
        // Try every combination of From -> To within the changed list
        for (int[] from : candidatesFrom) {
            for (int[] to : allChanges) {
                if (Arrays.equals(from, to)) continue;

                int piece = board[from[0]][from[1]];
                if (isLegalMove(piece, from, to)) {
                    // Valid move found. Ignore any other "splash" squares.
                    return executeMove(from, to, piece);
                }
            }
        }

        // 4. CHECK EN PASSANT
        String epMove = findEnPassantMove(candidatesFrom, allChanges);
        if (epMove != null) return epMove;

        return null;
    }

    private String executeMove(int[] from, int[] to, int piece) {
        String promotion = "";
        
        // Auto-promote to Queen for simplicity
        if (Math.abs(piece) == 1 && (to[0] == 0 || to[0] == 7)) {
            promotion = "Q";
        }

        // Handle En Passant Target
        if (Math.abs(piece) == 1 && Math.abs(from[0] - to[0]) == 2) {
            int epRank = whiteToMove ? 2 : 5;
            lastEnPassantSquare = coordsToSquare(new int[]{epRank, from[1]});
        } else {
            lastEnPassantSquare = "-";
        }

        int targetPiece = promotion.isEmpty() ? piece : (whiteToMove ? W_QUEEN : B_QUEEN);
        board[to[0]][to[1]] = targetPiece;
        board[from[0]][from[1]] = EMPTY;

        updateCastlingRights(piece, from);
        
        // --- TURN FLIP ---
        whiteToMove = !whiteToMove;
        
        // --- NEW: CHECKMATE DETECTION ---
        // Check if the player who just received the turn (whiteToMove) is in Checkmate
        if (isCheckmate(whiteToMove)) {
            isGameOver = true;
            String winner = whiteToMove ? "BLACK" : "WHITE"; // If White is mated, Black won
            System.out.println("\n#############################################");
            System.out.println("### CHECKMATE! " + winner + " won the game. ###");
            System.out.println("#############################################\n");
            
            String m = coordsToSquare(from) + coordsToSquare(to) + promotion + "#";
            moveHistory.add(m);
            return m;
        }

        String m = coordsToSquare(from) + coordsToSquare(to) + promotion;
        moveHistory.add(m);
        return m;
    }

    /**
     * NEW: Check if the specified color is in Checkmate.
     * Checkmate = King is in Check AND has NO legal moves to escape.
     */
    private boolean isCheckmate(boolean colorToCheck) {
        // 1. Is King in Check?
        if (!isKingInCheck(colorToCheck)) {
            return false;
        }

        // 2. Can any piece move to remove the check?
        // Brute force: Try ALL legal moves for this color. 
        // If any move results in King NOT being in check, it's not mate.
        
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                int p = board[r][c];
                // If piece belongs to the player in check
                if (p != EMPTY && (colorToCheck ? p > 0 : p < 0)) {
                    int[] from = {r, c};
                    
                    // Try moving to every square on the board
                    for (int tr = 0; tr < 8; tr++) {
                        for (int tc = 0; tc < 8; tc++) {
                            int[] to = {tr, tc};
                            
                            // Check basic geometry legality (ignoring check for a moment)
                            if (canPieceMoveGeometry(p, from, to, board[tr][tc])) {
                                
                                // SIMULATE MOVE
                                int[][] backupBoard = copyBoard(board);
                                board[tr][tc] = p; // simplified (no promotion logic needed for escape check usually)
                                board[r][c] = EMPTY;
                                
                                // Did this fix the check?
                                boolean stillInCheck = isKingInCheck(colorToCheck);
                                
                                // RESTORE BOARD
                                restoreBoard(backupBoard);
                                
                                if (!stillInCheck) {
                                    // Found an escape!
                                    return false; 
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // No escape found
        return true;
    }

    /**
     * NEW: Checks if the King of the given color is currently under attack.
     */
    private boolean isKingInCheck(boolean whiteKing) {
        int[] kingPos = null;
        int kingVal = whiteKing ? W_KING : B_KING;

        // Find King
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (board[r][c] == kingVal) {
                    kingPos = new int[]{r, c};
                    break;
                }
            }
        }
        
        if (kingPos == null) return false; // Should not happen

        // Check if any enemy piece attacks the King's square
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                int p = board[r][c];
                // If it's an enemy piece
                if (p != EMPTY && (whiteKing ? p < 0 : p > 0)) {
                    // Can this enemy piece attack the king?
                    if (canPieceAttack(p, new int[]{r, c}, kingPos)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * NEW: simplified move checker for attacks.
     * Unlike isLegalMove, this doesn't check whose turn it is, just geometry.
     */
    private boolean canPieceAttack(int piece, int[] from, int[] to) {
        int absPiece = Math.abs(piece);
        int rankDiff = to[0] - from[0];
        int fileDiff = to[1] - from[1];
        boolean isWhite = piece > 0;

        if (absPiece == 1) { // Pawn (Special case for attacking)
            int dir = isWhite ? 1 : -1;
            // Pawn only attacks diagonals
            return rankDiff == dir && Math.abs(fileDiff) == 1;
        }

        // For all other pieces, attack logic is same as move logic (assuming target is enemy/king)
        return canPieceMoveGeometry(piece, from, to, isWhite ? B_PAWN : W_PAWN); // Dummy target to simulate capture
    }

    /**
     * NEW: Refactored geometry logic.
     * Separated from isLegalMove so we can use it for checkmate simulation.
     */
    private boolean canPieceMoveGeometry(int piece, int[] from, int[] to, int targetPiece) {
        int fromRank = from[0], fromFile = from[1];
        int toRank = to[0], toFile = to[1];
        int rankDiff = toRank - fromRank;
        int fileDiff = toFile - fromFile;
        int absPiece = Math.abs(piece);
        boolean isWhite = piece > 0;

        // Standard rule: Cannot land on own piece
        if (targetPiece != EMPTY && (targetPiece > 0) == isWhite) return false;

        switch (absPiece) {
            case 1: // Pawn
                int dir = isWhite ? 1 : -1;
                // Push 1
                if (fileDiff == 0 && rankDiff == dir && targetPiece == EMPTY) return true;
                // Push 2
                if (fileDiff == 0 && fromRank == (isWhite?1:6) && rankDiff == 2*dir && targetPiece == EMPTY && board[fromRank+dir][fromFile] == EMPTY) return true;
                // Capture
                if (Math.abs(fileDiff) == 1 && rankDiff == dir && targetPiece != EMPTY) return true;
                return false;
            case 2: // Knight
                return (Math.abs(rankDiff)==2 && Math.abs(fileDiff)==1) || (Math.abs(rankDiff)==1 && Math.abs(fileDiff)==2);
            case 3: // Bishop
                return Math.abs(rankDiff) == Math.abs(fileDiff) && isPathClear(from, to);
            case 4: // Rook
                return (rankDiff == 0 || fileDiff == 0) && isPathClear(from, to);
            case 5: // Queen
                return (rankDiff==0 || fileDiff==0 || Math.abs(rankDiff)==Math.abs(fileDiff)) && isPathClear(from, to);
            case 6: // King
                return Math.abs(rankDiff) <= 1 && Math.abs(fileDiff) <= 1;
        }
        return false;
    }
    
    // Helper to copy board for simulation
    private int[][] copyBoard(int[][] source) {
        int[][] newBoard = new int[8][8];
        for(int i=0; i<8; i++) System.arraycopy(source[i], 0, newBoard[i], 0, 8);
        return newBoard;
    }
    
    // Helper to restore board
    private void restoreBoard(int[][] source) {
        for(int i=0; i<8; i++) System.arraycopy(source[i], 0, board[i], 0, 8);
    }

    private String findCastlingMove(List<int[]> changes) {
        int rank = whiteToMove ? 0 : 7;
        int kingPiece = whiteToMove ? W_KING : B_KING;
        
        // Verify King is on start square
        if (board[rank][4] != kingPiece) return null;

        // King start must be in changed squares
        if (!containsSquare(changes, rank, 4)) return null;

        // Check Kingside (Target g-file: index 6)
        if (containsSquare(changes, rank, 6)) {
             if ((whiteToMove && !whiteKingMoved && !whiteRookH1Moved) || (!whiteToMove && !blackKingMoved && !blackRookH8Moved)) {
                 if (board[rank][5] == EMPTY && board[rank][6] == EMPTY) {
                     board[rank][6] = kingPiece; board[rank][5] = whiteToMove ? W_ROOK : B_ROOK;
                     board[rank][4] = EMPTY; board[rank][7] = EMPTY;
                     if (whiteToMove) { whiteKingMoved = true; } else { blackKingMoved = true; }
                     whiteToMove = !whiteToMove;
                     lastEnPassantSquare = "-";
                     return whiteToMove ? "e8g8 (0-0)" : "e1g1 (0-0)"; 
                 }
             }
        }

        // Check Queenside (Target c-file: index 2)
        if (containsSquare(changes, rank, 2)) {
             if ((whiteToMove && !whiteKingMoved && !whiteRookA1Moved) || (!whiteToMove && !blackKingMoved && !blackRookA8Moved)) {
                 if (board[rank][1] == EMPTY && board[rank][2] == EMPTY && board[rank][3] == EMPTY) {
                     board[rank][2] = kingPiece; board[rank][3] = whiteToMove ? W_ROOK : B_ROOK;
                     board[rank][4] = EMPTY; board[rank][0] = EMPTY;
                     if (whiteToMove) { whiteKingMoved = true; } else { blackKingMoved = true; }
                     whiteToMove = !whiteToMove;
                     lastEnPassantSquare = "-";
                     return whiteToMove ? "e8c8 (0-0-0)" : "e1c1 (0-0-0)";
                 }
             }
        }
        return null;
    }

    private String findEnPassantMove(List<int[]> candidatesFrom, List<int[]> changes) {
        if (lastEnPassantSquare.equals("-")) return null;
        int[] epCoords = squareToCoords(lastEnPassantSquare);

        for (int[] from : candidatesFrom) {
            int piece = board[from[0]][from[1]];
            if (Math.abs(piece) != 1) continue;

            // Geometry check
            if (Math.abs(epCoords[1] - from[1]) == 1 && Math.abs(epCoords[0] - from[0]) == 1) {
                if (containsSquare(changes, epCoords[0], epCoords[1])) {
                    int capRank = whiteToMove ? 4 : 3;
                    board[epCoords[0]][epCoords[1]] = piece;
                    board[from[0]][from[1]] = EMPTY;
                    board[capRank][epCoords[1]] = EMPTY; 
                    whiteToMove = !whiteToMove;
                    lastEnPassantSquare = "-";
                    return coordsToSquare(from) + coordsToSquare(epCoords) + " (EP)";
                }
            }
        }
        return null;
    }

    private boolean isLegalMove(int piece, int[] from, int[] to) {
        // Wrapper for canPieceMoveGeometry that enforces turn color
        boolean isWhite = piece > 0;
        if (whiteToMove != isWhite) return false;
        
        return canPieceMoveGeometry(piece, from, to, board[to[0]][to[1]]);
    }

    private boolean isPathClear(int[] from, int[] to) {
        int rStep = Integer.compare(to[0] - from[0], 0);
        int cStep = Integer.compare(to[1] - from[1], 0);
        int r = from[0] + rStep, c = from[1] + cStep;
        while (r != to[0] || c != to[1]) {
            if (board[r][c] != EMPTY) return false;
            r += rStep; c += cStep;
        }
        return true;
    }

    private boolean containsSquare(List<int[]> list, int r, int c) {
        for (int[] coord : list) if (coord[0] == r && coord[1] == c) return true;
        return false;
    }

    private boolean isCorrectColor(int p) { return whiteToMove ? p > 0 : p < 0; }

    private void updateCastlingRights(int piece, int[] from) {
        if (piece == W_KING) whiteKingMoved = true;
        if (piece == B_KING) blackKingMoved = true;
        if (piece == W_ROOK) {
            if (from[0] == 0 && from[1] == 0) whiteRookA1Moved = true;
            if (from[0] == 0 && from[1] == 7) whiteRookH1Moved = true;
        }
        if (piece == B_ROOK) {
            if (from[0] == 7 && from[1] == 0) blackRookA8Moved = true;
            if (from[0] == 7 && from[1] == 7) blackRookH8Moved = true;
        }
    }

    public String getFEN() {
        StringBuilder fen = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            int emptyCount = 0;
            for (int file = 0; file < 8; file++) {
                int piece = board[rank][file];
                if (piece == EMPTY) {
                    emptyCount++;
                } else {
                    if (emptyCount > 0) { fen.append(emptyCount); emptyCount = 0; }
                    fen.append(pieceToFEN(piece));
                }
            }
            if (emptyCount > 0) fen.append(emptyCount);
            if (rank > 0) fen.append('/');
        }
        fen.append(whiteToMove ? " w " : " b ");
        String castling = getCastlingRights();
        fen.append(castling.isEmpty() ? "-" : castling).append(" ");
        fen.append(lastEnPassantSquare).append(" ");
        fen.append("0 ").append((moveHistory.size() / 2) + 1);
        return fen.toString();
    }

    private String getCastlingRights() {
        StringBuilder rights = new StringBuilder();
        if (!whiteKingMoved) {
            if (!whiteRookH1Moved) rights.append('K');
            if (!whiteRookA1Moved) rights.append('Q');
        }
        if (!blackKingMoved) {
            if (!blackRookH8Moved) rights.append('k');
            if (!blackRookA8Moved) rights.append('q');
        }
        return rights.toString();
    }

    private char pieceToFEN(int piece) {
        char c = switch (Math.abs(piece)) {
            case 1 -> 'p'; case 2 -> 'n'; case 3 -> 'b';
            case 4 -> 'r'; case 5 -> 'q'; case 6 -> 'k';
            default -> ' ';
        };
        return piece > 0 ? Character.toUpperCase(c) : c;
    }

    // Handles Uppercase (F4) and Lowercase (f4) to prevent crashes
    private int[] squareToCoords(String square) {
        String lower = square.toLowerCase();
        int file = lower.charAt(0) - 'a';
        int rank = lower.charAt(1) - '1';
        return new int[]{rank, file};
    }

    private String coordsToSquare(int[] coords) {
        char file = (char) ('a' + coords[1]);
        char rank = (char) ('1' + coords[0]);
        return "" + file + rank;
    }

    public void printBoard() {
        System.out.println("\n  a b c d e f g h");
        System.out.println("  ---------------");
        for (int rank = 7; rank >= 0; rank--) {
            System.out.print((rank + 1) + "|");
            for (int file = 0; file < 8; file++) {
                System.out.print(pieceToChar(board[rank][file]) + " ");
            }
            System.out.println("|" + (rank + 1));
        }
        System.out.println("  ---------------");
        System.out.println("  a b c d e f g h\n");
    }

    private char pieceToChar(int piece) {
        if (piece == EMPTY) return '.';
        char c = switch (Math.abs(piece)) {
            case 1 -> 'P'; case 2 -> 'N'; case 3 -> 'B';
            case 4 -> 'R'; case 5 -> 'Q'; case 6 -> 'K';
            default -> '?';
        };
        return piece > 0 ? c : Character.toLowerCase(c);
    }
}