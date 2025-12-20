package com.chessgame;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import nu.pattern.OpenCV;

import java.nio.file.Paths;
import java.util.List;

public class ChessMoveDetector {

    static {
        OpenCV.loadLocally();
    }

    public static void main(String[] args) {

        // 1. SETUP: Load Before and After images
        String beforeFile = "cobanhamle1.jpg";
        String afterFile = "cobanhamle2.jpg";

        String beforePath = Paths.get("src", "main", "resources", "tests","cobanmati", beforeFile).toString();
        String inputImagePath = Paths.get("src", "main", "resources", "tests","cobanmati", afterFile).toString(); // Using 'after' as main input
        String outputImagePath = Paths.get("output", "after_detected.jpg").toString();
        String debugImagePath = Paths.get("output", "debug_all_attempts.jpg").toString();
        String squaresOutputDir = Paths.get("output", "squares").toString();

        Mat imgBefore = Imgcodecs.imread(beforePath);
        Mat imgAfter = Imgcodecs.imread(inputImagePath); // 'src' in original code

        if (imgBefore.empty() || imgAfter.empty()) {
            System.err.println("Error: Could not read one or both images.");
            System.err.println("Check: " + beforePath);
            System.err.println("Check: " + inputImagePath);
            return;
        }

        System.out.println("Images loaded. Resolution: " + imgAfter.width() + "x" + imgAfter.height());

        // Create output directory
        new java.io.File(squaresOutputDir).mkdirs();

        // 2. DETECT: Find corners using the 'After' image (Source of Truth)
        Mat debugImg = imgAfter.clone();
        Point[] outerCorners = BoardDetector.findBoardCorners(imgAfter, debugImg);

        // Save debug image
        Imgcodecs.imwrite(debugImagePath, debugImg);

        if (outerCorners == null) {
            System.err.println("FAILED: Could not detect a chessboard on the 'After' image.");
            return;
        }

        System.out.println("Success! Outer Board Detected.");

        // Draw detection on output image
        BoardDetector.drawCorners(imgAfter, outerCorners, new Scalar(0, 255, 0), "Outer");
        Point[] innerCorners = BoardDetector.calculateInnerCorners(outerCorners);
        BoardDetector.drawCorners(imgAfter, innerCorners, new Scalar(255, 0, 0), "Inner");
        Imgcodecs.imwrite(outputImagePath, imgAfter);

        // 3. WARP: Align both images to the exact same perspective
        // We use a helper method to ensure both use the exact same matrix
        Mat warpedBefore = ChessMoveLogic.warpBoardStandardized(imgBefore, outerCorners);
        Mat warpedAfter = ChessMoveLogic.warpBoardStandardized(imgAfter, outerCorners);

        if (BoardDetector.DEBUG_MODE) {
            Imgcodecs.imwrite("output/debug_warped_before.jpg", warpedBefore);
            Imgcodecs.imwrite("output/debug_warped_after.jpg", warpedAfter);
        }

        // 4. DETECT CHANGES: Compare the squares
        System.out.println("\n=== Analyzing Changes ===");
        List<String> changedSquares = ChessMoveLogic.detectSquareChanges(warpedBefore, warpedAfter);

        System.out.println("\n-----------------------------");
        System.out.println("DETECTED MOVEMENTS: " + changedSquares);
        System.out.println("-----------------------------");

        // 5. EXTRACT: Save individual images (Original logic preserved)
        // We pass the original 'imgAfter' here as your extraction logic handles the warping internally
        java.io.File f = new java.io.File(inputImagePath);
        String baseFileName = f.getName().replaceFirst("[.][^.]+$", "");
        System.out.println("\n=== Extracting individual squares from 'After' image ===");
        ChessSquareExtractor.extractSquareImages(Imgcodecs.imread(inputImagePath), outerCorners, innerCorners, squaresOutputDir, baseFileName);
    }
}