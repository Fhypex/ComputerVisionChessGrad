package com.chessgame;

import nu.pattern.OpenCV;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

public class ChessVision {
    static { OpenCV.loadLocally(); }

    public static void analyzeBoard(String imagePath) {
        Mat img = Imgcodecs.imread(imagePath);
        if (img.empty()) {
            System.out.println("❌ Could not load image: " + imagePath);
            return;
        }
        BoardDetector.detectAndSplitBoard(imagePath, "output_cells");

        // Convert to grayscale and detect edges
        Mat gray = new Mat();
        Imgproc.cvtColor(img, gray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.GaussianBlur(gray, gray, new Size(5, 5), 0);
        Imgproc.Canny(gray, gray, 50, 150);

        System.out.println("✅ Image loaded and processed: " + imagePath);

        // Placeholder: here you’ll detect corners and extract 8x8 grid squares
    }
}
