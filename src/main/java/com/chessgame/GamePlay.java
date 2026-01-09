package com.chessgame;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.imgcodecs.Imgcodecs;

import com.chessgame.ChessGameTracker.MoveResult;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GamePlay extends Application {

    private ChessGameTracker tracker;
    private CameraViewer cameraViewer;
    private ChessBoard chessBoardUI; // Your UI visualization of the board
    private TextArea logArea;
    private Label statusLabel;
    private HttpHandDetector handDetector;

    // Game State Variables
    private Point[] boardCorners;
    private Mat prevWarpedImage;
    private ScheduledExecutorService gameLoopExecutor;
    private boolean isTracking = false;
    private boolean computerIsBlack = false;
    @Override
    public void start(Stage stage) {
        // Initialize Core Logic
        tracker = new ChessGameTracker(computerIsBlack);
        chessBoardUI = new ChessBoard();

        // Initialize UI Components
        cameraViewer = new CameraViewer();
        cameraViewer.startCamera();

        // Initialize hand detector (expects local mediapipe server)
        handDetector = new HttpHandDetector("http://127.0.0.1:8000/detect");

        chessBoardUI.updateBoard(tracker.getBoardArray());

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(150);
        logArea.setText(">>> Welcome. Align camera and click 'Start Realtime Game'.\n");

        statusLabel = new Label("Status: IDLE");
        statusLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");

        // --- Buttons ---
        Button btnStartGame = new Button("START REALTIME GAME");
        btnStartGame.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
        btnStartGame.setOnAction(e -> startCalibrationSequence());

        Button btnStopGame = new Button("STOP / RESET");
        btnStopGame.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
        btnStopGame.setOnAction(e -> stopGameLoop());

        Button btnUndo = new Button("Undo Last Move");
        btnUndo.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; -fx-font-weight: bold;");
        btnUndo.setOnAction(e -> performUndo());

        Button btnFlip = new Button("Flip View");
        btnFlip.setStyle("-fx-background-color: #555; -fx-text-fill: white;");
        btnFlip.setOnAction(e -> {
            boolean current = chessBoardUI.isWhitePerspective();
            chessBoardUI.setPerspective(!current);
            // Force an immediate redraw using the tracker's current internal board
            chessBoardUI.updateBoard(tracker.getBoardArray());             
            computerIsBlack = !computerIsBlack
            tracker = new ChessGameTracker(computerIsBlack);
        });

    // Add btnFlip to your controlBox HBox
        HBox controlBox = new HBox(15, btnStartGame, btnStopGame, btnFlip, btnUndo , statusLabel);
        // Layouts
        controlBox.setPadding(new Insets(10));
        controlBox.setStyle("-fx-background-color: #333; -fx-alignment: center-left;");

        VBox rightPane = new VBox(10);
        rightPane.getChildren().addAll(new Label("Camera Feed"), cameraViewer, logArea);
        rightPane.setPadding(new Insets(10));

        BorderPane root = new BorderPane();
        root.setCenter(chessBoardUI.getBoardUI()); // Assuming getBoardUI returns the JavaFX Grid/Pane
        root.setRight(rightPane);
        root.setBottom(controlBox);

        Scene scene = new Scene(root, 1200, 800);
        stage.setScene(scene);
        stage.setTitle("Realtime Chess Simulation");
        stage.setOnCloseRequest(e -> {
            stopGameLoop();
            cameraViewer.stopCamera();
            Platform.exit();
            System.exit(0);
        });
        stage.show();
    }

    /**
     * PHASE 1: CALIBRATION
     * Detects board, confirms with user, and saves initial state.
     * Runs in a separate thread to avoid freezing UI during manual picking.
     */
    private void startCalibrationSequence() {
        if (isTracking) return;

        log("Starting Calibration...");
        statusLabel.setText("Status: CALIBRATING...");

        new Thread(() -> {
            // 1. Capture Image
            Mat frame = cameraViewer.captureCurrentFrame();
            if (frame == null || frame.empty()) {
                Platform.runLater(() -> showError("Camera Error", "Could not capture frame."));
                return;
            }

            // 2. Detect Corners (Auto)
            Point[] detected = BoardDetector.findBoardCorners(frame, frame.clone());

            // 3. User Confirmation / Manual Adjustment (Blocking Call)
            // This opens the JavaFX window defined in BoardDetector
            Point[] finalCorners = BoardDetector.pickCornersManually(frame, detected);

            if (finalCorners == null) {
                Platform.runLater(() -> {
                    log("Calibration cancelled by user.");
                    statusLabel.setText("Status: IDLE");
                });
                return;
            }

            // 4. Success - Store State
            this.boardCorners = finalCorners;
            this.prevWarpedImage = ChessMoveLogic.warpBoardStandardized(frame, finalCorners);
            
            Platform.runLater(() -> {
                log("Board Configured. Game Loop Starting...");
                startGameLoop();
            });

        }).start();
    }

    /**
     * PHASE 2: THE GAME LOOP
     * Checks every 1 second for changes.
     */
    private void startGameLoop() {
        isTracking = true;
        statusLabel.setText("Status: TRACKING GAME [Active]");
        
        gameLoopExecutor = Executors.newSingleThreadScheduledExecutor();
        
        // Execute logic every 1 second
        gameLoopExecutor.scheduleWithFixedDelay(() -> {
            try {
                if (!isTracking) return;

                // 1. Capture
                Mat currentFrame = cameraViewer.captureCurrentFrame();
                if (currentFrame == null || currentFrame.empty()) return;

                // 1.5 Check for hand presence (skip processing if hand detected)
                try {
                    boolean hand = handDetector != null && handDetector.isHandPresent(currentFrame, 0.5);
                    if (hand) {
                        Platform.runLater(() -> log("Hand detected in frame — skipping processing."));
                        // Do not update prevWarpedImage; wait until user removes hand
                        return;
                    }
                } catch (Exception e) {
                    // If detection fails, continue normal processing (fail open)
                    System.err.println("Hand detection failed: " + e.getMessage());
                }
                // 2. Warp (Using fixed corners)
                Mat currentWarped = ChessMoveLogic.warpBoardStandardized(currentFrame, boardCorners);

                // 3. Detect Changes
                List<String> changedSquares = ChessMoveLogic.detectSquareChanges(prevWarpedImage, currentWarped);
                System.out.println(changedSquares);
                // If visual changes detected, try to process logic
                if (!changedSquares.isEmpty()) {
                    System.out.println(changedSquares);
                    Platform.runLater(() -> log("Visual change detected: " + changedSquares));

                    // 4. Process Move Logic
                    MoveResult result = tracker.processChangedSquares(changedSquares);
                    System.out.println(result.toString());
                    Platform.runLater(() -> {
                    switch (result.type) {
                        case NONE:
                            // --- NOTHING CHANGED ---
                            // The board is static. We do nothing.
                            break;

                        case VALID:
                            // --- VALID MOVE ---
                            log(">>> MOVE PLAYED: " + result.moveNotation);
                            tracker.printBoard(); // Update console
                            chessBoardUI.updateBoard(tracker.getBoardArray());
                            
                            // Visual feedback (Optional, if you have this method)
                            // chessBoardUI.executeMove(result.moveNotation); 

                            // Check for Mate
                            if (result.moveNotation.endsWith("#")) {
                                showInfo("Game Over", "Checkmate detected!");
                                stopGameLoop();
                            }

                            // CRITICAL: Lock in the new board state
                            prevWarpedImage = currentWarped; 
                            break;

                        case ILLEGAL:
                            // --- ILLEGAL MOVE ---
                            // User touched a piece but placed it invalidly
                            log("!!! ILLEGAL MOVE: " + result.details);
                            showIllegalMoveAlert(result.details);
                            
                            // IMPORTANT: We do NOT update prevWarpedImage. 
                            // The system expects the user to undo the bad move on the physical board.
                            break;

                        case NOISE:
                            // --- VISUAL NOISE ---
                            // Hand hovering, shadows, but no clear piece movement detected.
                            // We only log this if there were actual visual changes, to avoid spam.
                            if (!changedSquares.isEmpty()) {
                                log("... (Ignored visual noise/shadows) ...");
                            }
                            // We do NOT update prevWarpedImage.
                            break;
                    }
                });
                }

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> log("Error in Game Loop: " + e.getMessage()));
            }
        }, 4000, 4000, TimeUnit.MILLISECONDS);
    }

    private void stopGameLoop() {
        isTracking = false;
        if (gameLoopExecutor != null && !gameLoopExecutor.isShutdown()) {
            gameLoopExecutor.shutdownNow();
        }
        statusLabel.setText("Status: STOPPED");
        tracker = new ChessGameTracker();
        chessBoardUI.updateBoard(tracker.getBoardArray());
        log("Game tracking stopped.");
    }

    // --- Helpers ---

    private void showIllegalMoveAlert(String details) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Illegal Move Detected");
        alert.setHeaderText("The detected move is invalid.");
        alert.setContentText("Logic could not validate changes on: " + details + "\n\nPlease fix the board state and click OK to resume.");
        
        // This blocks the JavaFX thread, effectively pausing visual updates, 
        // but the background thread might keep running. 
        // We pause tracking flag briefly or just let the user fix it.
        alert.showAndWait();
    }

    private void performUndo() {
        if (tracker == null) return;

        log(">>> Undoing last move...");

        // 1. Revert internal game logic (You need to ensure this method exists in ChessGameTracker)
        tracker.undoLastMove(); 

        // 2. Update UI to match the reverted internal state
        chessBoardUI.updateBoard(tracker.getBoardArray());

        // 3. Reset Visual Tracking Base
        // We assume the user has physically corrected the board before clicking Undo.
        // We capture the CURRENT frame as the new "safe" baseline.
        if (isTracking && cameraViewer != null) {
             Mat currentFrame = cameraViewer.captureCurrentFrame();
             if (currentFrame != null && !currentFrame.empty() && boardCorners != null) {
                 this.prevWarpedImage = ChessMoveLogic.warpBoardStandardized(currentFrame, boardCorners);
                 log("Visual tracker reset to current board state.");
             }
        }
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    private void showInfo(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void log(String msg) {
        logArea.appendText(msg + "\n");
        logArea.setScrollTop(Double.MAX_VALUE); // Auto-scroll
    }

    public static void main(String[] args) {
        launch(args);
    }
}