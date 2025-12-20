package com.chessgame;

import nu.pattern.OpenCV;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;

import java.nio.file.Paths;
import java.util.List;

public class ChessMoveDetectorTest {

    // --- CONFIGURATION ---
    public static final String GAME_NAME = "coban"; // e.g., "coban"
    public static final String TEST_DIR = "cobanmati"; // Subfolder in resources/tests/
    public static final String FILE_EXT = ".jpg";
    // ---------------------

    static {
        OpenCV.loadLocally();
    }

    public static void main(String[] args) {
        ChessGameTracker tracker = new ChessGameTracker();

        // 1. Load Start Image
        String startFileName = GAME_NAME + "start" + FILE_EXT; // e.g., cobanstart.jpg
        
        // Fallback: Check if start exists, if not try hamle1 as base
        if (!isFileExists(startFileName)) {
            startFileName = GAME_NAME + "hamle1" + FILE_EXT; 
            System.out.println("Info: 'start' image not found, attempting to use " + startFileName + " as base.");
        }
        
        String startPath = getPath(startFileName);
        Mat prevImage = Imgcodecs.imread(startPath);

        if (prevImage.empty()) {
            System.err.println("CRITICAL ERROR: Could not load starting image: " + startPath);
            return;
        }

        System.out.println("=== CHESS GAME TRACKING STARTED (AUTOMATED) ===");
        System.out.println("Game: " + GAME_NAME);
        tracker.printBoard();
        System.out.println("FEN: " + tracker.getFEN());
        System.out.println("-----------------------------------");

        int moveCount = 1;
        
        // Loop indefinitely until error or end of files
        while (true) {
            // Logic: Start image is state 0. "hamle2" is the result of Move 1? 
            // Naming convention usually: start -> hamle1 (Move 1) -> hamle2 (Move 2)
            // Adjust index based on your specific filenames. 
            // Assuming: start vs hamle2 is comparing Move 1? 
            // Or start vs hamle1 is Move 1?
            // Usually: 
            // Compare "start" vs "hamle1" -> Detects Move 1
            // Compare "hamle1" vs "hamle2" -> Detects Move 2
            
            // If we started with 'start', next is 'hamle1' for Move 1.
            // If we started with 'hamle1', next is 'hamle2' for Move 1 (relative to file load).
            
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

            // 1. Check if file exists
            if (currImage.empty()) {
                System.out.println("Result: End of files reached (or file missing).");
                System.out.println("Tracking stopped successfully.");
                break;
            }

            // 2. Detect Board (Source of Truth)
            Mat debugImg = currImage.clone();
            Point[] outerCorners = BoardDetector.findBoardCorners(currImage, debugImg);
            
            if (outerCorners == null) {
                System.err.println("FAILURE: Could not detect board in " + nextFileName);
                System.err.println("Stopping execution.");
                break;
            }

            // 3. Warp Images
            Mat warpedPrev = ChessMoveLogic.warpBoardStandardized(prevImage, outerCorners);
            Mat warpedCurr = ChessMoveLogic.warpBoardStandardized(currImage, outerCorners);

            // 4. Detect Visual Changes
            List<String> changedSquares = ChessMoveLogic.detectSquareChanges(warpedPrev, warpedCurr);
            System.out.println("Visual Changes: " + changedSquares);

            // 5. Identify and Validate Move
            String move = tracker.processChangedSquares(changedSquares);

            if (move != null) {
                System.out.println(">>> CONFIRMED MOVE: " + move + " <<<");
                tracker.printBoard();
                System.out.println("FEN: " + tracker.getFEN());
                
                // Save debug image
                String debugOut = "output/" + GAME_NAME + "_move" + moveCount + "_detected.jpg";
                BoardDetector.drawCorners(currImage, outerCorners, new Scalar(0, 255, 0), "Board");
                Imgcodecs.imwrite(debugOut, currImage);
                
                // Prepare for next iteration
                prevImage = currImage; 
                moveCount++;
            } else {
                System.err.println("FAILURE: Logic could not identify a valid chess move.");
                System.err.println("Squares changed: " + changedSquares);
                System.err.println("Stopping execution due to tracking loss.");
                break;
            }
        }
        
        System.out.println("=== GAME TRACKING ENDED ===");
    }

    private static String getPath(String filename) {
        return Paths.get("src", "main", "resources", "tests", TEST_DIR, filename).toString();
    }
    
    private static boolean isFileExists(String filename) {
        java.io.File f = new java.io.File(getPath(filename));
        return f.exists();
    }
}