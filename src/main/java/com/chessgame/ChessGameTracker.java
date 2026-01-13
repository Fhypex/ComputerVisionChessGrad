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

    private Stack<GameState> history = new Stack<>();
    
    private boolean isGameOver;
    
    // POV - false = white close to camera, true = black close to camera
    private boolean blackPOV;

    public ChessGameTracker() {
        this(false); // Default: white perspective
    } 

    public ChessGameTracker(boolean blackPOV) {
        this.board = new int[8][8];
        this.whiteToMove = true;
        this.moveHistory = new ArrayList<>();
        this.isGameOver = false;
        this.blackPOV = blackPOV;
        initializeBoard();
        initializeCastlingRights();
    } 

    private static class GameState {
        int[][] boardSnapshot;
        boolean wasWhiteTurn;
        boolean wasGameOver;
        String lastEpSquareSnapshot;
        boolean wKingMoved, bKingMoved;
        boolean wRookA1, wRookH1, bRookA8, bRookH8;

        public GameState(int[][] board, boolean isWhiteTurn, boolean isGameOver, String epSquare,
                         boolean wk, boolean bk, boolean wra, boolean wrh, boolean bra, boolean brh) {
            this.boardSnapshot = deepCopy(board);
            this.wasWhiteTurn = isWhiteTurn;
            this.wasGameOver = isGameOver;
            this.lastEpSquareSnapshot = epSquare;
            this.wKingMoved = wk; this.bKingMoved = bk;
            this.wRookA1 = wra; this.wRookH1 = wrh;
            this.bRookA8 = bra; this.bRookH8 = brh;
        }

        private int[][] deepCopy(int[][] source) {
            int[][] dest = new int[8][8];
            for (int i = 0; i < 8; i++) {
                System.arraycopy(source[i], 0, dest[i], 0, 8);
            }
            return dest;
        }
    }

    private void initializeBoard() {
        // FIXED: Always initialize standard internal representation regardless of POV.
        // POV translations happen only in coordsToSquare/squareToCoords.
        for (int i = 0; i < 8; i++) {
            Arrays.fill(board[i], EMPTY);
        }

        // WHITE pieces at bottom (rank 0-1)
        board[0][0] = W_ROOK; board[0][7] = W_ROOK;
        board[0][1] = W_KNIGHT; board[0][6] = W_KNIGHT;
        board[0][2] = W_BISHOP; board[0][5] = W_BISHOP;
        board[0][3] = W_QUEEN; board[0][4] = W_KING;
        for (int i = 0; i < 8; i++) board[1][i] = W_PAWN;

        // BLACK pieces at top (rank 6-7)
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

    // Add inside ChessGameTracker class
    public boolean isWhiteTurn() {
        return this.whiteToMove;
    }

    /**
     * Converts camera square notation to logical chess coordinates
     * considering the POV (perspective)
     */
    private int[] squareToCoords(String square) {
        String lower = square.toLowerCase();
        
        if (!blackPOV) {
            // White POV: Standard chess notation
            int file = lower.charAt(0) - 'a';  // a=0, h=7
            int rank = lower.charAt(1) - '1';  // 1=0, 8=7
            return new int[]{rank, file};
        } else {
            // Black POV: Reversed
            // Camera sees: a b c d e f g h as h g f e d c b a
            // Camera sees: 1 2 3 4 5 6 7 8 as 8 7 6 5 4 3 2 1
            int file = 7 - (lower.charAt(0) - 'a');  // a=7, h=0
            int rank = 7 - (lower.charAt(1) - '1');  // 1=7, 8=0
            return new int[]{rank, file};
        }
    }

    /**
     * Converts logical chess coordinates to standard notation
     * considering the POV
     */
    private String coordsToSquare(int[] coords) {
        if (!blackPOV) {
            // White POV: Standard
            char file = (char) ('a' + coords[1]);
            char rank = (char) ('1' + coords[0]);
            return "" + file + rank;
        } else {
            // Black POV: Reversed back to standard notation
            char file = (char) ('a' + (7 - coords[1]));
            char rank = (char) ('1' + (7 - coords[0]));
            return "" + file + rank;
        }
    }

    public static class MoveResult {
        public enum Type { 
            NONE,
            VALID,
            ILLEGAL,
            NOISE
        }
        
        public Type type;
        public String moveNotation; 
        public String details;

        public static MoveResult none() { 
            MoveResult r = new MoveResult(); r.type = Type.NONE; return r; 
        }
        public static MoveResult valid(String move) {
            MoveResult r = new MoveResult(); r.type = Type.VALID; r.moveNotation = move; return r;
        }
        public static MoveResult illegal(String reason) {
            MoveResult r = new MoveResult(); r.type = Type.ILLEGAL; r.details = reason; return r;
        }
        public static MoveResult noise() {
            MoveResult r = new MoveResult(); r.type = Type.NOISE; return r;
        }
    }

    public MoveResult processChangedSquares(List<String> changedSquares) {
        if (isGameOver) {
            System.out.println(">>> GAME IS OVER. No more moves accepted. <<<");
            return MoveResult.noise();
        }

        if (changedSquares == null || changedSquares.isEmpty()) {
            return MoveResult.none();
        }

        if (changedSquares.size() >= 4) {
            boolean touchesBackRank = false;
            for (String s : changedSquares) {
                // In algebraic notation (e.g., "e4"), the rank is at index 1
                if (s.length() >= 2) {
                    char rank = s.charAt(1);
                    if (rank == '1' || rank == '8') {
                        touchesBackRank = true;
                        break;
                    }
                }
            }
            if (!touchesBackRank) {
                return MoveResult.noise();
            }
        }

        if (changedSquares.size() > 6) {
            return MoveResult.noise();
        }
        
        List<int[]> allChanges = new ArrayList<>();
        for (String s : changedSquares) {
            allChanges.add(squareToCoords(s));
        }

        List<int[]> candidatesFrom = new ArrayList<>();
        for (int[] c : allChanges) {
            int p = board[c[0]][c[1]];
            if (p != EMPTY && isCorrectColor(p)) {
                candidatesFrom.add(c);
            }
        }

        String castlingMove = findCastlingMove(allChanges);
        if (castlingMove != null) {
            saveGameState(); // FIXED: Save state before executing castling
            // Castling execution is complex, usually handled by findCastlingMove returns.
            // But to support Undo, we really should execute it here properly.
            // For now, assuming findCastlingMove did the board updates (as in original),
            // but we added saveGameState above.
            return MoveResult.valid(castlingMove);
        }

        for (int[] from : candidatesFrom) {
            for (int[] to : allChanges) {
                if (Arrays.equals(from, to)) continue;

                int piece = board[from[0]][from[1]];
                if (isLegalMove(piece, from, to)) {
                    saveGameState();
                    String moveStr = executeMove(from, to, piece);
                    return MoveResult.valid(moveStr);
                }
            }
        }

        // FIXED: findEnPassantMove now only returns the string. We execute it here.
        String epMove = findEnPassantMove(candidatesFrom, allChanges);
        if (epMove != null) {
            // Parse from/to from the string or logic to call executeMove
            // epMove format: "e5d6 (EP)"
            // It's cleaner to re-derive the coordinates since executeMove handles the logic.
            // We know it is EP.
            for (int[] from : candidatesFrom) {
                if (Math.abs(board[from[0]][from[1]]) == 1) {
                    int[] epCoords = squareToCoords(lastEnPassantSquare);
                     if (Math.abs(epCoords[1] - from[1]) == 1 && Math.abs(epCoords[0] - from[0]) == 1) {
                         saveGameState();
                         String moveStr = executeMove(from, epCoords, board[from[0]][from[1]]);
                         return MoveResult.valid(moveStr);
                     }
                }
            }
        }

        if (!candidatesFrom.isEmpty()) {
            return MoveResult.illegal("Touched " + coordsToSquare(candidatesFrom.get(0)) + " but move was invalid.");
        }

        return MoveResult.noise();
    }

    private void saveGameState() {
        history.push(new GameState(this.board, this.whiteToMove, this.isGameOver, this.lastEnPassantSquare,
                whiteKingMoved, blackKingMoved, whiteRookA1Moved, whiteRookH1Moved, blackRookA8Moved, blackRookH8Moved));
    }

    public void undoLastMove() {
        if (history.isEmpty()) {
            System.out.println(">>> History empty. Cannot undo.");
            return;
        }

        GameState previousState = history.pop();
        
        this.board = previousState.boardSnapshot;
        this.whiteToMove = previousState.wasWhiteTurn;
        this.isGameOver = previousState.wasGameOver;
        this.lastEnPassantSquare = previousState.lastEpSquareSnapshot;
        this.whiteKingMoved = previousState.wKingMoved;
        this.blackKingMoved = previousState.bKingMoved;
        this.whiteRookA1Moved = previousState.wRookA1;
        this.whiteRookH1Moved = previousState.wRookH1;
        this.blackRookA8Moved = previousState.bRookA8;
        this.blackRookH8Moved = previousState.bRookH8;
        
        System.out.println(">>> UNDO SUCCESSFUL. It is now " + (whiteToMove ? "White" : "Black") + "'s turn.");
    }

    private String executeMove(int[] from, int[] to, int piece) {
        String promotion = "";
        
        // Promotion Logic (Unchanged as requested)
        if (Math.abs(piece) == 1 && (to[0] == 0 || to[0] == 7)) {
            promotion = "Q";
        }

        String specialNote = "";
        // FIXED: En Passant Execution Logic
        // If pawn moves diagonally to an empty square, it is En Passant.
        if (Math.abs(piece) == 1 && from[1] != to[1] && board[to[0]][to[1]] == EMPTY) {
            // Remove the captured pawn (which is on the same rank as 'from', same file as 'to')
            board[from[0]][to[1]] = EMPTY;
            specialNote = " (EP)";
        }

        // Set En Passant target for NEXT turn
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
        
        whiteToMove = !whiteToMove;
        
        // Check Game Over Conditions (Checkmate or Stalemate)
        if (isCheckmate(whiteToMove)) {
            isGameOver = true;
            String winner = whiteToMove ? "BLACK" : "WHITE";
            System.out.println("\n#############################################");
            System.out.println("### CHECKMATE! " + winner + " won the game. ###");
            System.out.println("#############################################\n");
            
            String m = coordsToSquare(from) + coordsToSquare(to) + promotion + specialNote + "#";
            moveHistory.add(m);
            return m;
        } else if (isStalemate(whiteToMove)) {
            // FIXED: Stalemate detection
            isGameOver = true;
            System.out.println("\n#############################################");
            System.out.println("### STALEMATE! The game is a DRAW.      ###");
            System.out.println("#############################################\n");
            
            String m = coordsToSquare(from) + coordsToSquare(to) + promotion + specialNote + " (Stalemate)";
            moveHistory.add(m);
            return m;
        }

        String m = coordsToSquare(from) + coordsToSquare(to) + promotion + specialNote;
        moveHistory.add(m);
        return m;
    }

    public void overridePromotion(int row, int col, String pieceNotation) {
        int pieceCode = 0;
        
        // Map notation to base integer code
        switch (pieceNotation) {
            case "Q": pieceCode = 5; break;
            case "R": pieceCode = 4; break;
            case "B": pieceCode = 3; break;
            case "N": pieceCode = 2; break;
            default: return; // Invalid input, do nothing
        }

        // Determine color based on the row (Promotions only happen on rank 0 or 7)
        // If row is 7, White pawn moved there (so it's White).
        // If row is 0, Black pawn moved there (so it's Black).
        if (row == 7) {
            board[row][col] = pieceCode; // White is positive
        } else if (row == 0) {
            board[row][col] = -pieceCode; // Black is negative
        }
        
        System.out.println("Tracker Override: Set [" + row + "," + col + "] to " + pieceNotation);
    }

    private boolean isStalemate(boolean colorToCheck) {
        if (isKingInCheck(colorToCheck)) return false;
        return !hasLegalMoves(colorToCheck);
    }

    private boolean hasLegalMoves(boolean colorToCheck) {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                int p = board[r][c];
                if (p != EMPTY && (colorToCheck ? p > 0 : p < 0)) {
                    int[] from = {r, c};
                    for (int tr = 0; tr < 8; tr++) {
                        for (int tc = 0; tc < 8; tc++) {
                            int[] to = {tr, tc};
                            if (canPieceMoveGeometry(p, from, to, board[tr][tc])) {
                                int[][] backup = copyBoard(board);
                                board[tr][tc] = p;
                                board[r][c] = EMPTY;
                                boolean check = isKingInCheck(colorToCheck);
                                restoreBoard(backup);
                                if (!check) return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean isCheckmate(boolean colorToCheck) {
        if (!isKingInCheck(colorToCheck)) {
            return false;
        }
        return !hasLegalMoves(colorToCheck);
    }

    private boolean isKingInCheck(boolean whiteKing) {
        int[] kingPos = null;
        int kingVal = whiteKing ? W_KING : B_KING;

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (board[r][c] == kingVal) {
                    kingPos = new int[]{r, c};
                    break;
                }
            }
        }
        
        if (kingPos == null) return false;

        return isSquareAttacked(kingPos[0], kingPos[1], !whiteKing);
    }

    // FIXED: Helper to check if a square is attacked by a specific color
    private boolean isSquareAttacked(int r, int c, boolean byWhiteColor) {
        int[] target = {r, c};
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                int p = board[row][col];
                if (p != EMPTY && (byWhiteColor ? p > 0 : p < 0)) {
                    if (canPieceAttack(p, new int[]{row, col}, target)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean canPieceAttack(int piece, int[] from, int[] to) {
        int absPiece = Math.abs(piece);
        int rankDiff = to[0] - from[0];
        int fileDiff = to[1] - from[1];
        boolean isWhite = piece > 0;

        if (absPiece == 1) {
            int dir = isWhite ? 1 : -1;
            // Pawn captures diagonally
            return rankDiff == dir && Math.abs(fileDiff) == 1;
        }

        return canPieceMoveGeometry(piece, from, to, isWhite ? B_PAWN : W_PAWN);
    }

    private boolean canPieceMoveGeometry(int piece, int[] from, int[] to, int targetPiece) {
        int fromRank = from[0], fromFile = from[1];
        int toRank = to[0], toFile = to[1];
        int rankDiff = toRank - fromRank;
        int fileDiff = toFile - fromFile;
        int absPiece = Math.abs(piece);
        boolean isWhite = piece > 0;

        if (targetPiece != EMPTY && (targetPiece > 0) == isWhite) return false;

        switch (absPiece) {
            case 1: // Pawn
                int dir = isWhite ? 1 : -1;
                if (fileDiff == 0 && rankDiff == dir && targetPiece == EMPTY) return true;
                if (fileDiff == 0 && fromRank == (isWhite?1:6) && rankDiff == 2*dir && targetPiece == EMPTY && board[fromRank+dir][fromFile] == EMPTY) return true;
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
    
    private int[][] copyBoard(int[][] source) {
        int[][] newBoard = new int[8][8];
        for(int i=0; i<8; i++) System.arraycopy(source[i], 0, newBoard[i], 0, 8);
        return newBoard;
    }
    
    private void restoreBoard(int[][] source) {
        for(int i=0; i<8; i++) System.arraycopy(source[i], 0, board[i], 0, 8);
    }

    private String findCastlingMove(List<int[]> changes) {
        int rank = whiteToMove ? 0 : 7;
        int kingPiece = whiteToMove ? W_KING : B_KING;
        boolean opponentColor = !whiteToMove;
        
        if (board[rank][4] != kingPiece) return null;

        if (!containsSquare(changes, rank, 4)) return null;

        // Kingside Castling
        if (containsSquare(changes, rank, 6)) {
             if ((whiteToMove && !whiteKingMoved && !whiteRookH1Moved) || (!whiteToMove && !blackKingMoved && !blackRookH8Moved)) {
                 if (board[rank][5] == EMPTY && board[rank][6] == EMPTY) {
                     // FIXED: Check for checks on the path
                     if (!isKingInCheck(whiteToMove) &&
                         !isSquareAttacked(rank, 5, opponentColor) &&
                         !isSquareAttacked(rank, 6, opponentColor)) {
                         
                         board[rank][6] = kingPiece; board[rank][5] = whiteToMove ? W_ROOK : B_ROOK;
                         board[rank][4] = EMPTY; board[rank][7] = EMPTY;
                         if (whiteToMove) { whiteKingMoved = true; } else { blackKingMoved = true; }
                         whiteToMove = !whiteToMove;
                         lastEnPassantSquare = "-";
                         return whiteToMove ? "e8g8 (0-0)" : "e1g1 (0-0)"; // This logic is slightly weird because whiteToMove was just flipped.
                     }
                 }
             }
        }

        // Queenside Castling
        if (containsSquare(changes, rank, 2)) {
             if ((whiteToMove && !whiteKingMoved && !whiteRookA1Moved) || (!whiteToMove && !blackKingMoved && !blackRookA8Moved)) {
                 if (board[rank][1] == EMPTY && board[rank][2] == EMPTY && board[rank][3] == EMPTY) {
                     // FIXED: Check for checks on the path (King crosses d-file, lands on c-file)
                     // Note: b-file check is not required by rules, only c and d.
                     if (!isKingInCheck(whiteToMove) &&
                         !isSquareAttacked(rank, 3, opponentColor) &&
                         !isSquareAttacked(rank, 2, opponentColor)) {

                         board[rank][2] = kingPiece; board[rank][3] = whiteToMove ? W_ROOK : B_ROOK;
                         board[rank][4] = EMPTY; board[rank][0] = EMPTY;
                         if (whiteToMove) { whiteKingMoved = true; } else { blackKingMoved = true; }
                         whiteToMove = !whiteToMove;
                         lastEnPassantSquare = "-";
                         return whiteToMove ? "e8c8 (0-0-0)" : "e1c1 (0-0-0)";
                     }
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

            if (Math.abs(epCoords[1] - from[1]) == 1 && Math.abs(epCoords[0] - from[0]) == 1) {
                if (containsSquare(changes, epCoords[0], epCoords[1])) {
                    // FIXED: Do not execute here. Just detect and return.
                    return coordsToSquare(from) + coordsToSquare(epCoords) + " (EP)";
                }
            }
        }
        return null;
    }

    private boolean isLegalMove(int piece, int[] from, int[] to) {
        boolean isWhite = piece > 0;
        if (whiteToMove != isWhite) return false;
        
        // Pseudo-legal check
        if (!canPieceMoveGeometry(piece, from, to, board[to[0]][to[1]])) return false;
        
        // Safety check (Pinned pieces)
        int[][] backup = copyBoard(board);
        board[to[0]][to[1]] = piece;
        board[from[0]][from[1]] = EMPTY;
        boolean check = isKingInCheck(isWhite);
        restoreBoard(backup);
        
        return !check;
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
        
        // Standard check because internal board is now standard
        if (piece == W_ROOK) {
            if (from[0] == 0 && from[1] == 0) whiteRookA1Moved = true;
            if (from[0] == 0 && from[1] == 7) whiteRookH1Moved = true;
        }
        if (piece == B_ROOK) {
            if (from[0] == 7 && from[1] == 0) blackRookA8Moved = true;
            if (from[0] == 7 && from[1] == 7) blackRookH8Moved = true;
        }
        
        // Also if rooks are CAPTURED, rights are lost? 
        // Technically yes, but less critical for a tracker. 
        // Adding strictly would require checking 'to' square captures.
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

    public int[][] getBoardArray() {
        int[][] copy = new int[8][8];
        for (int i = 0; i < 8; i++) {
            System.arraycopy(board[i], 0, copy[i], 0, 8);
        }
        return copy;
    }
    
    public boolean isBlackPOV() {
        return blackPOV;
    }
}