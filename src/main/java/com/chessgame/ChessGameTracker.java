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

    public ChessGameTracker() {
        this.board = new int[8][8];
        this.whiteToMove = true; // Game always starts with white
        this.moveHistory = new ArrayList<>();
        initializeBoard();
        initializeCastlingRights();
    }

    private void initializeBoard() {
        // Initialize empty board
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                board[i][j] = EMPTY;
            }
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
     * Process changed squares from BoardDetector and identify the move.
     * This logic improves accuracy by cross-referencing visual changes with legal chess moves.
     *
     * @param changedSquares List of squares that changed (e.g., ["a2", "a4"])
     * @return The move in algebraic notation, or null if move couldn't be identified
     */
    public String processChangedSquares(List<String> changedSquares) {
        if (changedSquares == null || changedSquares.isEmpty()) {
            return null;
        }

        // Convert square names to coordinates
        List<int[]> coords = new ArrayList<>();
        for (String square : changedSquares) {
            coords.add(squareToCoords(square));
        }

        // Identify the move type based on number of changed squares
        String move = null;

        if (coords.size() == 2) {
            // Normal move or capture (Source square becomes empty, Dest square changes)
            move = identifyNormalMove(coords);
        } else if (coords.size() == 3) {
            // En passant (Start, End, and Captured Pawn square all change)
            move = identifyEnPassant(coords);
        } else if (coords.size() == 4) {
            // Castling (King Start/End, Rook Start/End)
            move = identifyCastling(coords);
        }

        if (move != null) {
            moveHistory.add(move);
            whiteToMove = !whiteToMove;
            // Reset en passant target unless specifically set by a double pawn push inside identifyNormalMove
            if (!move.endsWith(" (EP)")) { 
                // Note: Logic inside identifyNormalMove handles setting the specific target,
                // here we essentially clear it if a non-pawn move happened, 
                // but for simplicity we rely on the tracker state.
                // A robust engine would reset 'lastEnPassantSquare' to "-" every turn 
                // unless a double push just happened.
            }
        }

        return move;
    }

    private String identifyNormalMove(List<int[]> coords) {
        int[] from = null, to = null;

        // Determine which square is 'from' and which is 'to'
        // The 'from' square MUST contain a piece of the current player's color BEFORE the move.
        // The 'to' square is the other one.
        for (int[] coord : coords) {
            int piece = board[coord[0]][coord[1]];
            if (piece != EMPTY && isCorrectColor(piece)) {
                from = coord;
            }
        }

        // Fallback: If logic is ambiguous, try swapping
        if (from == null) {
            // Try assuming first is from
            int p1 = board[coords.get(0)[0]][coords.get(0)[1]];
             if (p1 != EMPTY && isCorrectColor(p1)) {
                 from = coords.get(0);
                 to = coords.get(1);
             } else {
                 from = coords.get(1);
                 to = coords.get(0);
             }
        } else {
            // Found 'from', so 'to' is the other one
            to = (coords.get(0)[0] == from[0] && coords.get(0)[1] == from[1]) ? coords.get(1) : coords.get(0);
        }

        int piece = board[from[0]][from[1]];

        // Basic validation
        if (piece == EMPTY || !isCorrectColor(piece)) {
            System.err.println("Invalid Move: No piece at source or wrong color.");
            return null;
        }

        // Check if it's a legal move for this piece type
        if (!isLegalMove(piece, from, to)) {
            System.err.println("Invalid Move: Illegal move for piece " + pieceToChar(piece));
            return null;
        }

        // Check for pawn promotion (Pawn reaches last rank)
        String promotion = "";
        if (Math.abs(piece) == 1 && (to[0] == 0 || to[0] == 7)) {
            promotion = "Q"; // Default to Queen for auto-tracking
        }

        // Check for double pawn push (Set En Passant target)
        if (Math.abs(piece) == 1 && Math.abs(from[0] - to[0]) == 2) {
            int epRank = whiteToMove ? 2 : 5;
            lastEnPassantSquare = coordsToSquare(new int[]{epRank, from[1]});
        } else {
            lastEnPassantSquare = "-";
        }

        // Execute the move on internal board
        board[to[0]][to[1]] = promotion.isEmpty() ? piece : (whiteToMove ? W_QUEEN : B_QUEEN);
        board[from[0]][from[1]] = EMPTY;

        updateCastlingRights(piece, from);

        String fromSquare = coordsToSquare(from);
        String toSquare = coordsToSquare(to);

        return fromSquare + toSquare + promotion;
    }

    private String identifyEnPassant(List<int[]> coords) {
        // En Passant involves 3 squares changing: 
        // 1. Pawn Start
        // 2. Pawn End
        // 3. Captured Pawn (which becomes empty)
        
        int[] pawnFrom = null, pawnTo = null, capturedPawn = null;

        // Find the friendly pawn
        for (int[] coord : coords) {
            int piece = board[coord[0]][coord[1]];
            if (Math.abs(piece) == 1 && isCorrectColor(piece)) {
                pawnFrom = coord;
            }
        }

        if (pawnFrom == null) return null;

        // Find destination and captured square
        for (int[] coord : coords) {
            if (coord[0] == pawnFrom[0] && coord[1] == pawnFrom[1]) continue;
            
            // Logic: Destination is empty or was empty. Captured square had enemy pawn.
            // This is tricky visually because AFTER the move, both Start and Captured are empty,
            // and End has the pawn.
            // So we look for the square that ends up with the pawn (which is diagonal).
            
            // Simplified: The destination must be diagonal
            if (Math.abs(coord[1] - pawnFrom[1]) == 1 && Math.abs(coord[0] - pawnFrom[0]) == 1) {
                // This is likely the destination
                pawnTo = coord;
            } else {
                capturedPawn = coord;
            }
        }

        if (pawnFrom == null || pawnTo == null) return null;

        // Execute
        int pawn = board[pawnFrom[0]][pawnFrom[1]];
        board[pawnTo[0]][pawnTo[1]] = pawn;
        board[pawnFrom[0]][pawnFrom[1]] = EMPTY;
        if (capturedPawn != null) {
            board[capturedPawn[0]][capturedPawn[1]] = EMPTY;
        }
        
        lastEnPassantSquare = "-";
        return coordsToSquare(pawnFrom) + coordsToSquare(pawnTo) + " (EP)";
    }

    private String identifyCastling(List<int[]> coords) {
        // Sort coordinates by file (a=0 to h=7)
        coords.sort(Comparator.comparingInt(a -> a[1]));

        // There should be 4 changes. 
        // Queenside: a(RookStart), c(KingEnd), d(RookEnd), e(KingStart) -> Indices 0, 2, 3, 4
        // Kingside: e(KingStart), f(RookEnd), g(KingEnd), h(RookStart) -> Indices 4, 5, 6, 7 (relative to board)
        
        int rank = coords.get(0)[0];
        boolean isWhite = rank == 0;

        if (isWhite != whiteToMove) return null;

        // Check if the last changed square is on the H file (file 7) -> Kingside
        boolean isKingside = coords.get(3)[1] == 7;

        if (isKingside) {
            // Execute Kingside
            board[rank][6] = isWhite ? W_KING : B_KING; // g
            board[rank][5] = isWhite ? W_ROOK : B_ROOK; // f
            board[rank][4] = EMPTY; // e
            board[rank][7] = EMPTY; // h

            if (isWhite) { whiteKingMoved = true; whiteRookH1Moved = true; } 
            else { blackKingMoved = true; blackRookH8Moved = true; }

            lastEnPassantSquare = "-";
            return isWhite ? "e1g1 (0-0)" : "e8g8 (0-0)";
        } else {
            // Execute Queenside
            board[rank][2] = isWhite ? W_KING : B_KING; // c
            board[rank][3] = isWhite ? W_ROOK : B_ROOK; // d
            board[rank][4] = EMPTY; // e
            board[rank][0] = EMPTY; // a

            if (isWhite) { whiteKingMoved = true; whiteRookA1Moved = true; } 
            else { blackKingMoved = true; blackRookA8Moved = true; }

            lastEnPassantSquare = "-";
            return isWhite ? "e1c1 (0-0-0)" : "e8c8 (0-0-0)";
        }
    }

    private boolean isLegalMove(int piece, int[] from, int[] to) {
        int fromRank = from[0], fromFile = from[1];
        int toRank = to[0], toFile = to[1];
        int rankDiff = toRank - fromRank;
        int fileDiff = toFile - fromFile;

        int absPiece = Math.abs(piece);
        boolean isWhite = piece > 0;

        switch (absPiece) {
            case 1: // Pawn
                int direction = isWhite ? 1 : -1;
                // Push
                if (fileDiff == 0 && rankDiff == direction && board[toRank][toFile] == EMPTY) return true;
                // Double Push
                int startRank = isWhite ? 1 : 6;
                if (fileDiff == 0 && fromRank == startRank && rankDiff == 2 * direction &&
                    board[toRank][toFile] == EMPTY && board[fromRank + direction][fromFile] == EMPTY) return true;
                // Capture
                if (Math.abs(fileDiff) == 1 && rankDiff == direction) {
                    int target = board[toRank][toFile];
                    return target != EMPTY && (target > 0) != isWhite;
                }
                return false;

            case 2: // Knight
                return (Math.abs(rankDiff) == 2 && Math.abs(fileDiff) == 1) ||
                       (Math.abs(rankDiff) == 1 && Math.abs(fileDiff) == 2);

            case 3: // Bishop
                return Math.abs(rankDiff) == Math.abs(fileDiff) && isPathClear(from, to);

            case 4: // Rook
                return (rankDiff == 0 || fileDiff == 0) && isPathClear(from, to);

            case 5: // Queen
                return (rankDiff == 0 || fileDiff == 0 || Math.abs(rankDiff) == Math.abs(fileDiff)) && isPathClear(from, to);

            case 6: // King
                return Math.abs(rankDiff) <= 1 && Math.abs(fileDiff) <= 1;
        }
        return false;
    }

    private boolean isPathClear(int[] from, int[] to) {
        int rankStep = Integer.compare(to[0] - from[0], 0);
        int fileStep = Integer.compare(to[1] - from[1], 0);

        int rank = from[0] + rankStep;
        int file = from[1] + fileStep;

        while (rank != to[0] || file != to[1]) {
            if (board[rank][file] != EMPTY) return false;
            rank += rankStep;
            file += fileStep;
        }
        return true;
    }

    private boolean isCorrectColor(int piece) {
        return whiteToMove ? piece > 0 : piece < 0;
    }

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

    // --- FIX IS HERE: Handles both Uppercase and Lowercase ---
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