package com.chessgame;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class BoardDetector {

    /**
     * Detects and extracts the chessboard grid from a chess.com screenshot.
     * Returns a list of 64 Mat images (each square cell).
     */
    public static List<Mat> detectAndSplitBoard(String imagePath, String outputDir) {
        Mat src = Imgcodecs.imread(imagePath);
        if (src.empty()) {
            System.out.println("❌ Could not load image: " + imagePath);
            return null;
        }

        // Convert to grayscale
        Mat gray = new Mat();
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);

        // Detect edges
        Mat edges = new Mat();
        Imgproc.GaussianBlur(gray, gray, new Size(5, 5), 0);
        Imgproc.Canny(gray, edges, 50, 150);

        // Find contours
        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(edges, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        Rect boardRect = null;
        double maxArea = 0;

        for (MatOfPoint c : contours) {
            Rect rect = Imgproc.boundingRect(c);
            double area = rect.area();
            double ratio = (double) rect.width / rect.height;

            // Find the biggest (almost square) contour
            if (area > maxArea && ratio > 0.8 && ratio < 1.2) {
                maxArea = area;
                boardRect = rect;
            }
        }

        if (boardRect == null) {
            System.out.println("❌ No chessboard detected.");
            return null;
        }

        // Crop the board
        Mat board = new Mat(src, boardRect);
        System.out.println("✅ Chessboard detected. Cropping region: " + boardRect);

        // Split board into 8x8
        int cellWidth = board.cols() / 8;
        int cellHeight = board.rows() / 8;

        List<Mat> cells = new ArrayList<>();

        // Create output directory if needed
        if (outputDir != null) {
            new File(outputDir).mkdirs();
        }

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Rect cellRect = new Rect(col * cellWidth, row * cellHeight, cellWidth, cellHeight);
                Mat cell = new Mat(board, cellRect);
                cells.add(cell);

                // Save each cell (optional)
                if (outputDir != null) {
                    String filename = String.format("%s/cell_%d_%d.jpg", outputDir, row, col);
                    Imgcodecs.imwrite(filename, cell);
                }
            }
        }

        System.out.println("✅ Split into 64 cells successfully.");
        return cells;
    }
}
