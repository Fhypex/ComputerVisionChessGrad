package com.chessgame;

import nu.pattern.OpenCV;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import javafx.application.Platform;

import java.nio.file.Paths;
import java.util.List;

public class ChessMoveDetectorTest {

    // --- CONFIGURATION ---
    public static final String GAME_NAME = "dr"; 
    public static final String TEST_DIR = "dr"; 
    public static final String FILE_EXT = ".jpg";
    // ---------------------

    static {
        OpenCV.loadLocally();
    }

    public static void main(String[] args) {
        // 1. Initialize JavaFX Environment
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException e) {
            // Platform already started
        }

        ChessGameTracker tracker = new ChessGameTracker();

        String startFileName = GAME_NAME + "start" + FILE_EXT; 
        if (!isFileExists(startFileName)) {
            startFileName = GAME_NAME + "hamle1" + FILE_EXT; 
            System.out.println("Info: 'start' image not found, attempting to use " + startFileName + " as base.");
        }
        
        String startPath = getPath(startFileName);
        Mat prevImage = Imgcodecs.imread(startPath);

        if (prevImage.empty()) {
            System.err.println("CRITICAL ERROR: Could not load starting image: " + startPath);
            Platform.exit();
            return;
        }

        // --- OPTIONAL: Verify Board on Start Image ---
        System.out.println("Verifying Start Image Board...");
        Point[] startCorners = BoardDetector.findBoardCorners(prevImage, prevImage.clone());
        if (startCorners == null) {
            System.out.println("Could not auto-detect board on START image. Opening Manual Picker...");
            startCorners = BoardDetector.pickCornersManually(prevImage, null);
            if (startCorners == null) {
                System.out.println("Terminated by user.");
                Platform.exit();
                return;
            }
        }
        // ---------------------------------------------

        System.out.println("=== CHESS GAME TRACKING STARTED (AUTOMATED) ===");
        System.out.println("Game: " + GAME_NAME);
        tracker.printBoard();
        System.out.println("FEN: " + tracker.getFEN());
        System.out.println("-----------------------------------");

        int moveCount = 1;
        
        // Loop indefinitely until error or end of files
        while (true) {
            String nextFileName;
            if (startFileName.contains("start")) {
                 nextFileName = GAME_NAME + "hamle" + moveCount + FILE_EXT;
            } else {
                 nextFileName = GAME_NAME + "hamle" + (moveCount + 1) + FILE_EXT;
            }

            System.out.println("\n--- Processing Move #" + moveCount + " ---");
            System.out.println("Target File: " + nextFileName);

            String nextPath = getPath(nextFileName);
            Mat currImage = Imgcodecs.imread(nextPath);

            if (currImage.empty()) {
                System.out.println("Result: End of files reached (or file missing).");
                break;
            }

            // --- RETRY LOOP FOR CURRENT MOVE ---
            // If detection fails OR move logic fails, we open GUI and try again with new corners
            boolean moveResolved = false;
            Point[] outerCorners = null;
            
            // Initial attempt
            outerCorners = BoardDetector.findBoardCorners(currImage, currImage.clone());
            
            while (!moveResolved) {
                // If auto-detection failed, force manual pick
                if (outerCorners == null) {
                    System.err.println(">> Auto-detection failed. Opening Manual Interface...");
                    outerCorners = BoardDetector.pickCornersManually(currImage, outerCorners);
                    
                    if (outerCorners == null) {
                        System.out.println(">> TERMINATE signal received.");
                        Platform.exit();
                        return;
                    }
                }

                // 3. Warp Images using corners (user verified or auto-detected)
                // Note: Logic implies we use CURRENT corners for warping both? 
                // Or standardized corners. Assuming standard warp:
                Mat warpedPrev = ChessMoveLogic.warpBoardStandardized(prevImage, outerCorners);
                Mat warpedCurr = ChessMoveLogic.warpBoardStandardized(currImage, outerCorners);

                // 4. Detect Visual Changes
                List<String> changedSquares = ChessMoveLogic.detectSquareChanges(warpedPrev, warpedCurr);
                System.out.println("Visual Changes: " + changedSquares);

                // 5. Identify and Validate Move
                //String move = tracker.processChangedSquares(changedSquares);
                String move = null;
                /* 
                if (move != null && ) {
                    System.out.println(">>> CONFIRMED MOVE: " + move + " <<<");
                    tracker.printBoard();
                    System.out.println("FEN: " + tracker.getFEN());
                    
                    // Save debug image
                    String debugOut = "output/" + GAME_NAME + "_move" + moveCount + "_detected.jpg";
                    BoardDetector.drawCorners(currImage, outerCorners, new Scalar(0, 255, 0), "Board");
                    Imgcodecs.imwrite(debugOut, currImage);
                    
                    if (move.endsWith("#")) {
                        System.out.println(">>> CHECKMATE DETECTED. Stopping tracking. <<<");
                        Platform.exit();
                        return;
                    }

                    // Prepare for next iteration
                    prevImage = currImage; 
                    moveCount++;
                    moveResolved = true; // Exit retry loop
                } else {
                    System.err.println("FAILURE: Logic could not identify a valid chess move.");
                    System.err.println("Squares changed: " + changedSquares);
                    System.err.println(">> Opening Manual Interface to adjust corners and retry...");
                    
                    // Open GUI with current corners so user can adjust
                    outerCorners = BoardDetector.pickCornersManually(currImage, outerCorners);
                    
                    if (outerCorners == null) {
                         System.out.println(">> TERMINATE signal received.");
                         Platform.exit();
                         return;
                    }
                    // Loop continues...
                }
                */
            }
        }
        
        System.out.println("=== GAME TRACKING ENDED ===");
        Platform.exit();
    }

    private static String getPath(String filename) {
        return Paths.get("src", "main", "resources", "tests", TEST_DIR, filename).toString();
    }
    
    private static boolean isFileExists(String filename) {
        java.io.File f = new java.io.File(getPath(filename));
        return f.exists();
    }
}