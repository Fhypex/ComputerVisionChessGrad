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

    // Game State Variables
    private Point[] boardCorners;
    private Mat prevWarpedImage;
    private ScheduledExecutorService gameLoopExecutor;
    private boolean isTracking = false;

    @Override
    public void start(Stage stage) {
        // Initialize Core Logic
        tracker = new ChessGameTracker();
        chessBoardUI = new ChessBoard();

        // Initialize UI Components
        cameraViewer = new CameraViewer();
        cameraViewer.startCamera();

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

        Button btnFlip = new Button("Flip View");
        btnFlip.setStyle("-fx-background-color: #555; -fx-text-fill: white;");
        btnFlip.setOnAction(e -> {
            boolean current = chessBoardUI.isWhitePerspective();
            chessBoardUI.setPerspective(!current);
            // Force an immediate redraw using the tracker's current internal board
            chessBoardUI.updateBoard(tracker.getBoardArray()); 
        });

    // Add btnFlip to your controlBox HBox
        HBox controlBox = new HBox(15, btnStartGame, btnStopGame, btnFlip, statusLabel);
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

                // 2. Warp (Using fixed corners)
                Mat currentWarped = ChessMoveLogic.warpBoardStandardized(currentFrame, boardCorners);

                // 3. Detect Changes
                List<String> changedSquares = ChessMoveLogic.detectSquareChanges(prevWarpedImage, currentWarped);

                // If visual changes detected, try to process logic
                if (!changedSquares.isEmpty()) {
                    Platform.runLater(() -> log("Visual change detected: " + changedSquares));

                    // 4. Process Move Logic
                    String move = tracker.processChangedSquares(changedSquares);

                    Platform.runLater(() -> {
                        if (move != null) {
                            // --- VALID MOVE ---
                            log(">>> MOVE PLAYED: " + move);
                            
                            // Update UI Board (Requires your ChessBoard class to have a method like movePiece)
                            // chessBoardUI.executeMove(move); 
                            
                            // Update FEN/Console
                            tracker.printBoard();
                            
                            // Check for Mate
                            if (move.endsWith("#")) {
                                showInfo("Game Over", "Checkmate detected!");
                                stopGameLoop();
                            }

                            // Update Reference Image for next turn
                            prevWarpedImage = currentWarped; 
                        } else {
                            // --- ILLEGAL MOVE / NOISE ---
                            // Tracker returns null if logic fails but input was present
                            log("!!! ILLEGAL MOVE or NOISE DETECTED !!!");
                            showIllegalMoveAlert(changedSquares.toString());
                            
                            // IMPORTANT: We do NOT update prevWarpedImage. 
                            // The system expects the user to undo the bad move on the physical board.
                        }
                    });
                }

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> log("Error in Game Loop: " + e.getMessage()));
            }
        }, 1000, 1000, TimeUnit.MILLISECONDS);
    }

    private void stopGameLoop() {
        isTracking = false;
        if (gameLoopExecutor != null && !gameLoopExecutor.isShutdown()) {
            gameLoopExecutor.shutdownNow();
        }
        statusLabel.setText("Status: STOPPED");
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