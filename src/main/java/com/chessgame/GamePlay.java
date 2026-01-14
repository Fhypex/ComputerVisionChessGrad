package com.chessgame;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Point;
import org.opencv.imgcodecs.Imgcodecs;

import com.chessgame.ChessGameTracker.MoveResult;

import java.io.ByteArrayInputStream;
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
    private Label aiSuggestionLabel; // NEW: UI for Stockfish moves
    private HttpHandDetector handDetector;

    // --- Debug Views for Warp Logic ---
    private ImageView prevWarpedView;
    private ImageView currentWarpedView;
    // ----------------------------------

    // returns of model
    private final String[] MODEL_CLASSES = {
        "black_bishop",       // 0
        "black_king",         // 1
        "black_knight",       // 2
        "black_pawn",         // 3
        "black_queen",        // 4
        "black_rook",         // 5
        "empty",              // 6
        "half_empty_square",  // 7
        "white_bishop",       // 8
        "white_king",         // 9
        "white_knight",       // 10
        "white_pawn",         // 11
        "white_queen",        // 12
        "white_rook"          // 13
    };

    // Game State Variables
    private Point[] boardCorners;
    private Mat prevWarpedImage;
    private ScheduledExecutorService gameLoopExecutor;
    private boolean isTracking = false;
    private boolean computerIsBlack = false;
    private String modelPath = "models/detection_model.h5";
    private ChessModelLoader loader = null;
    
    // NEW: Prevents spamming the API during the same turn
    private boolean isThinking = false; 
    
    // NEW: Light Level Logic
    private String currentLightMode = "mid"; // Default start value
    private ComboBox<Integer> intervalSelector; // Selector for loop speed

    @Override
    public void start(Stage stage) {
        // Initialize Core Logic
        tracker = new ChessGameTracker(computerIsBlack);
        chessBoardUI = new ChessBoard();
        loader = new ChessModelLoader();
        try {
            loader.loadModel(modelPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
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

        // --- NEW: AI Advice Label ---
        aiSuggestionLabel = new Label("AI Advice: Waiting...");
        aiSuggestionLabel.setStyle("-fx-text-fill: #00BFFF; -fx-font-weight: bold; -fx-font-size: 14px; -fx-border-color: #00BFFF; -fx-padding: 5;");
        // ----------------------------

        // --- Initialize Debug Image Views ---
        prevWarpedView = new ImageView();
        prevWarpedView.setFitWidth(200); 
        prevWarpedView.setPreserveRatio(true);

        currentWarpedView = new ImageView();
        currentWarpedView.setFitWidth(200); 
        currentWarpedView.setPreserveRatio(true);
        // ------------------------------------

        // --- Buttons & Controls ---
        Button btnStartGame = new Button("START REALTIME GAME");
        btnStartGame.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
        btnStartGame.setPrefHeight(40); // Height increased
        btnStartGame.setOnAction(e -> startCalibrationSequence());

        Button btnStopGame = new Button("STOP / RESET");
        btnStopGame.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
        btnStopGame.setPrefHeight(40); // Height increased
        btnStopGame.setOnAction(e -> stopGameLoop());

        Button btnUndo = new Button("Undo Last Move");
        btnUndo.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; -fx-font-weight: bold;");
        btnUndo.setPrefHeight(40); // Height increased
        btnUndo.setOnAction(e -> performUndo());

        Button btnFlip = new Button("Flip View");
        btnFlip.setStyle("-fx-background-color: #555; -fx-text-fill: white;");
        btnFlip.setPrefHeight(40); // Height increased
        btnFlip.setOnAction(e -> {
            boolean current = chessBoardUI.isWhitePerspective();
            chessBoardUI.setPerspective(!current);
            // Force an immediate redraw using the tracker's current internal board
            chessBoardUI.updateBoard(tracker.getBoardArray());            
            computerIsBlack = !computerIsBlack;
            tracker = new ChessGameTracker(computerIsBlack);
        });
        
        // --- NEW: Light Level Button ---
        Button btnLightLevel = new Button("Light: MID");
        btnLightLevel.setStyle("-fx-background-color: #FFC107; -fx-text-fill: black; -fx-font-weight: bold;");
        btnLightLevel.setPrefHeight(40); // Height increased
        btnLightLevel.setOnAction(e -> {
            // Cycle: mid -> high -> low -> mid
            switch (currentLightMode) {
                case "mid":
                    currentLightMode = "high";
                    break;
                case "high":
                    currentLightMode = "low";
                    break;
                case "low":
                    currentLightMode = "mid";
                    break;
            }
            btnLightLevel.setText("Light: " + currentLightMode.toUpperCase());
            // Call the static function on ChessMoveLogic
            try {
                ChessMoveLogic.setLightLevel(currentLightMode);
            } catch (Exception ex) {
                System.err.println("Method setLightLevel not found in ChessMoveLogic: " + ex.getMessage());
            }
        });

        // --- NEW: Time Interval Selector ---
        Label lblInterval = new Label("Loop(s):");
        lblInterval.setStyle("-fx-text-fill: white;");
        
        intervalSelector = new ComboBox<>();
        intervalSelector.getItems().addAll(1, 2, 3, 4, 5);
        intervalSelector.setValue(2); // Default
        intervalSelector.setPrefHeight(40); // Height increased

        // Add aiSuggestionLabel to your controlBox HBox
        HBox controlBox = new HBox(15, 
            btnStartGame, 
            btnStopGame, 
            btnFlip, 
            btnUndo, 
            btnLightLevel, // Added here
            lblInterval,   // Added label
            intervalSelector, // Added selector
            statusLabel, 
            aiSuggestionLabel
        );
        // Layouts
        controlBox.setPadding(new Insets(10));
        controlBox.setStyle("-fx-background-color: #333; -fx-alignment: center-left;");

        // --- UPDATED LAYOUT: Side-by-Side Debug Views ---
        // Create small containers for label + image to keep them organized
        VBox prevBox = new VBox(5, new Label("Debug: Prev State"), prevWarpedView);
        VBox currBox = new VBox(5, new Label("Debug: Current State"), currentWarpedView);
        
        // Put them side by side
        HBox debugRow = new HBox(15, prevBox, currBox);
        debugRow.setAlignment(Pos.CENTER_LEFT);

        VBox rightPane = new VBox(10);
        rightPane.getChildren().addAll(
            new Label("Camera Feed"), 
            cameraViewer, 
            debugRow,   // Side-by-side images
            logArea     // Chat/Log below them
        );
        rightPane.setPadding(new Insets(10));
        // ----------------------------------------------------

        BorderPane root = new BorderPane();
        root.setCenter(chessBoardUI.getBoardUI()); // Assuming getBoardUI returns the JavaFX Grid/Pane
        root.setRight(rightPane);
        root.setBottom(controlBox);

        Scene scene = new Scene(root, 1350, 950); // Increased width slightly for new buttons
        stage.setScene(scene);
        stage.setTitle("Realtime Chess Simulation");
        stage.setOnCloseRequest(e -> {
            stopGameLoop();
            cameraViewer.stopCamera();
            Platform.exit();
            System.exit(0);
        });
        stage.show();
        
        // Set initial light level logic just in case
        try {
            ChessMoveLogic.setLightLevel(currentLightMode);
        } catch (Exception ex) {
            // Ignore if not yet implemented
        }
    }

    /**
     * PHASE 1: CALIBRATION
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
                // Set initial image for debugging
                prevWarpedView.setImage(matToImage(this.prevWarpedImage));
                startGameLoop();
            });

        }).start();
    }

    /**
     * PHASE 2: THE GAME LOOP
     */
    private void startGameLoop() {
        isTracking = true;
        statusLabel.setText("Status: TRACKING GAME [Active]");
        
        gameLoopExecutor = Executors.newSingleThreadScheduledExecutor();
        
        // Get user selected interval
        long intervalSeconds = intervalSelector.getValue();
        
        // Execute logic based on selected interval
        gameLoopExecutor.scheduleWithFixedDelay(() -> {
            try {
                if (!isTracking) return;

                // 1. Capture
                Mat currentFrame = cameraViewer.captureCurrentFrame();
                if (currentFrame == null || currentFrame.empty()) return;

                // 1.5 Check for hand presence
                try {
                    boolean hand = handDetector != null && handDetector.isHandPresent(currentFrame, 0.5);
                    if (hand) {
                        Platform.runLater(() -> log("Hand detected — skipping."));
                        return;
                    }
                } catch (Exception e) {
                    System.err.println("Hand detection failed: " + e.getMessage());
                }
                
                // 2. Warp 
                Mat currentWarped = ChessMoveLogic.warpBoardStandardized(currentFrame, boardCorners);

                // --- Update UI Debug Images ---
                // We must convert Mat to Image. Since we are on a background thread, 
                // we wrap the update in Platform.runLater
                Image curImg = matToImage(currentWarped);
                Image prevImg = matToImage(prevWarpedImage);
                Platform.runLater(() -> {
                    if (curImg != null) currentWarpedView.setImage(curImg);
                    if (prevImg != null) prevWarpedView.setImage(prevImg);
                });
                // ------------------------------

                // 3. Detect Changes
                List<String> changedSquares = ChessMoveLogic.detectSquareChanges(prevWarpedImage, currentWarped);
                
                // If visual changes detected, process logic
                if (!changedSquares.isEmpty()) {
                    System.out.println(changedSquares);
                    Platform.runLater(() -> log("Visual change: " + changedSquares));

                    // 4. Process Move Logic
                    MoveResult result = tracker.processChangedSquares(changedSquares);
                    System.out.println(result.moveNotation);
                    
                    Platform.runLater(() -> {
                    switch (result.type) {
                        case NONE:
                            break;

                        case VALID:
                            log(">>> MOVE PLAYED: " + result.moveNotation);

                            // [START] AI PROMOTION CHECK
                            if (result.moveNotation.contains("Q") || result.moveNotation.endsWith("Q")) {
                                String destStr = result.moveNotation.substring(2, 4); 
                                int destCol = destStr.charAt(0) - 'a';
                                int destRow = destStr.charAt(1) - '1';
                                Mat pieceImg = ChessMoveLogic.getSquareForModel(currentWarped, destRow, destCol);
                                System.out.println("Goingto classifier");
                                String detected = classifyPiece(pieceImg); 

                                if (!detected.equals("Q")) {
                                    log("AI CORRECTION: Promotion was " + detected);
                                    tracker.overridePromotion(destRow, destCol, detected);
                                    result.moveNotation = result.moveNotation.replace("Q", detected);
                                }
                            }
                            // [END] AI PROMOTION CHECK
                            
                            tracker.printBoard(); 
                            chessBoardUI.updateBoard(tracker.getBoardArray());
                            
                            // Check for Mate
                            if (result.moveNotation.endsWith("#")) {
                                showInfo("Game Over", "Checkmate detected!");
                                stopGameLoop();
                            }

                            // Lock in the new board state
                            prevWarpedImage = currentWarped; 
                            
                            // Update the debug view for "Previous" now that we have locked it in
                            prevWarpedView.setImage(matToImage(prevWarpedImage));

                            // --- Trigger Stockfish API ---
                            checkAndTriggerStockfish();
                            // -----------------------------
                            break;

                        case ILLEGAL:
                            log("!!! ILLEGAL MOVE: " + result.details);
                            showIllegalMoveAlert(result.details);
                            break;

                        case NOISE:
                            if (!changedSquares.isEmpty()) log("... (Ignored noise) ...");
                            break;
                    }
                });
                } else {
                    // Even if no visual change, we might want to check if it's our turn 
                    // and we haven't asked AI yet (e.g. after game load)
                    Platform.runLater(this::checkAndTriggerStockfish);
                }

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> log("Error in Game Loop: " + e.getMessage()));
            }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    /**
     * Logic to check whose turn it is and ask Stockfish
     */
    private void checkAndTriggerStockfish() {
        if (tracker == null || !isTracking) return;

        // Parse FEN to determine turn: " ... w ..." means White
        boolean isWhiteTurn = tracker.getFEN().contains(" w ");
        
        // Computer moves if: 
        // 1. Computer is Black AND it is NOT White's turn
        // 2. Computer is White AND it IS White's turn
        boolean isComputerTurn = (computerIsBlack && !isWhiteTurn) || (!computerIsBlack && isWhiteTurn);

        if (isComputerTurn && !isThinking) {
            isThinking = true;
            aiSuggestionLabel.setText("AI Advice: Thinking...");
            
            // Default depth 12
            StockfishClient.getBestMove(tracker.getFEN(), 12, new StockfishClient.StockfishCallback() {
                @Override
                public void onMoveReceived(String bestMove, String evaluation) {
                    isThinking = false;
                    
                    Platform.runLater(() -> {
                        String friendlyMove = formatMoveForSpeech(bestMove);
                        
                        aiSuggestionLabel.setText("AI Recommends: " + friendlyMove + " (Eval: " + evaluation + ")");
                        log(">>> STOCKFISH: " + bestMove + " | Eval: " + evaluation);
                        
                        // Speak the move
                        speakMove(friendlyMove);
                    });
                }

                @Override
                public void onError(String error) {
                    isThinking = false;
                    Platform.runLater(() -> log("Stockfish Error: " + error));
                }
            });
        }
    }

    /**
     * Voice Command Implementation
     * Uses native OS TTS tools (PowerShell for Windows, 'say' for Mac)
     */
    private void speakMove(String text) {
        String os = System.getProperty("os.name").toLowerCase();
        
        // Add spaces for clearer pronunciation (e.g., "e 2 to e 4")
        String spokenText = text.replaceAll("([a-h])([1-8])", "$1 $2");

        new Thread(() -> {
            try {
                if (os.contains("win")) {
                    String cmd = "Add-Type -AssemblyName System.Speech; (New-Object System.Speech.Synthesis.SpeechSynthesizer).Speak('" + spokenText + "');";
                    new ProcessBuilder("powershell.exe", "-Command", cmd).start();
                } else if (os.contains("mac")) {
                    new ProcessBuilder("say", spokenText).start();
                } else if (os.contains("nix") || os.contains("nux")) {
                    new ProcessBuilder("espeak", spokenText).start();
                }
            } catch (Exception e) {
                System.err.println("TTS Failed: " + e.getMessage());
            }
        }).start();
    }

    private String formatMoveForSpeech(String move) {
        if (move == null || move.length() < 4) return move;
        // e2e4 -> e2 to e4
        return move.substring(0, 2) + " to " + move.substring(2, 4);
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
    
    // Helper method to convert OpenCV Mat to JavaFX Image
    private Image matToImage(Mat mat) {
        if (mat == null || mat.empty()) return null;
        try {
            MatOfByte buffer = new MatOfByte();
            Imgcodecs.imencode(".png", mat, buffer);
            return new Image(new ByteArrayInputStream(buffer.toArray()));
        } catch (Exception e) {
            System.err.println("Failed to convert Mat to Image: " + e.getMessage());
            return null;
        }
    }

    private void showIllegalMoveAlert(String details) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Illegal Move Detected");
        alert.setHeaderText("The detected move is invalid.");
        alert.setContentText("Logic could not validate changes on: " + details + "\n\nPlease fix the board state and click OK to resume.");
        alert.showAndWait();
    }

    private void performUndo() {
        if (tracker == null) return;

        log(">>> Undoing last move...");

        // 1. Revert internal game logic 
        tracker.undoLastMove(); 
        
        // Reset thinking state in case AI was thinking during undo
        isThinking = false; 

        // 2. Update UI to match the reverted internal state
        chessBoardUI.updateBoard(tracker.getBoardArray());

        // 3. Reset Visual Tracking Base
        if (isTracking && cameraViewer != null) {
              Mat currentFrame = cameraViewer.captureCurrentFrame();
              if (currentFrame != null && !currentFrame.empty() && boardCorners != null) {
                  this.prevWarpedImage = ChessMoveLogic.warpBoardStandardized(currentFrame, boardCorners);
                  
                  // Update Debug UI
                  prevWarpedView.setImage(matToImage(this.prevWarpedImage));
                  
                  log("Visual tracker reset to current board state.");
              }
        }
        
        // Check if we need to trigger AI (if undo made it computer's turn)
        checkAndTriggerStockfish();
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

    private String classifyPiece(Mat squareImage) {
        if (loader == null) return "Q"; 

        try {
            // 1. Preprocess using the logic (224x224, /255.0)
            float[] inputData = ChessMoveLogic.preprocessImageForModel(squareImage);
            System.out.println("preprocessing happened.");
            // 2. Predict
            int index = loader.predict(inputData, 224, 224, 3);
            
            // 3. Map the index to the Class Name
            if (index < 0 || index >= MODEL_CLASSES.length) {
                System.err.println("Model predicted invalid index: " + index);
                return "Q";
            }            

            String fullLabel = MODEL_CLASSES[index];
            System.out.println("AI sees: " + fullLabel + " (Index " + index + ")");

            return mapLabelToNotation(fullLabel);

        } catch (Exception e) {
            e.printStackTrace();
            return "Q"; 
        }
    }

    private String mapLabelToNotation(String label) {
        if (label.contains("queen"))  return "Q";
        if (label.contains("rook"))   return "R";
        if (label.contains("bishop")) return "B";
        if (label.contains("knight")) return "N";
        return "Q"; 
    }

    private void log(String msg) {
        logArea.appendText(msg + "\n");
        logArea.setScrollTop(Double.MAX_VALUE); // Auto-scroll
    }

    public static void main(String[] args) {
        launch(args);
    }
}