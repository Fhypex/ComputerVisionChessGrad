package com.chessgame;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import nu.pattern.OpenCV;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TestBoardCorners {

    // Configuration for the physical board
    private static final double BOARD_PHYSICAL_SIZE_CM = 40.0;
    private static final double BORDER_OFFSET_CM = 1.0;
    private static final int VIRTUAL_RESOLUTION = 800;

    static { OpenCV.loadLocally(); }
    public static void main(String[] args) {
        // Load the OpenCV Native Library
        

        String inputImagePath = Paths.get("src", "main", "resources", "tests", "test2.jpg").toString();
        String outputImagePath = Paths.get("output", "corners3.jpg").toString();

        Mat src = Imgcodecs.imread(inputImagePath);
        if (src.empty()) {
            System.err.println("Cannot read image: " + inputImagePath);
            return;
        }

        // 1. Detect the 4 corners of the physical board
        Point[] boardCorners = findBoardCorners(src);

        if (boardCorners == null) {
            System.err.println("Could not detect a chessboard (large enough) in the image.");
            return;
        }

        System.out.println("Board Detected.");

        // 2. Visualize the detected corners
        drawCorners(src, boardCorners, new Scalar(0, 255, 0));

        // 3. Calculate the Grid Coordinates
        List<Point> squareCenters = calculateGridCenters(src, boardCorners);

        // 4. Draw the centers
        for (Point p : squareCenters) {
            Imgproc.circle(src, p, 5, new Scalar(0, 0, 255), -1);
        }

        Imgcodecs.imwrite(outputImagePath, src);
        System.out.println("Processing complete. Output saved to " + outputImagePath);
    }

    /**
     * Improved Detection Logic:
     * Uses Adaptive Threshold + Dilation to merge the grid into one big object
     * to prevent detecting single squares.
     */
    private static Point[] findBoardCorners(Mat src) {
        Mat gray = new Mat();
        Mat thresh = new Mat();

        // 1. Grayscale
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);

        // 2. Gaussian Blur (Stronger blur to remove texture details)
        Imgproc.GaussianBlur(gray, gray, new Size(9, 9), 0);

        // 3. Adaptive Thresholding
        // This works better than Canny for grids because it compares pixels to their neighbors
        // rather than global thresholds.
        Imgproc.adaptiveThreshold(gray, thresh, 255, 
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, 
                Imgproc.THRESH_BINARY_INV, 11, 2);

        // 4. Dilate (The "Blob" Trick)
        // This thickens the white lines/areas. It connects the squares together 
        // so the algorithm sees one giant block instead of 64 small squares.
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));
        Imgproc.dilate(thresh, thresh, kernel);

        // 5. Find Contours (Use RETR_EXTERNAL to only look for outer shells, ignoring insides)
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(thresh, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        double imageArea = src.width() * src.height();
        double maxArea = 0;
        MatOfPoint2f bestApprox = null;

        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);

            // RULE 1: Size Filter
            // If the contour is smaller than 5% of the image, it's definitely not the board.
            // This stops it from picking up a single square or noise.
            if (area < imageArea * 0.05) continue;

            MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
            double peri = Imgproc.arcLength(contour2f, true);
            MatOfPoint2f approx = new MatOfPoint2f();

            // Approximate to polygon
            Imgproc.approxPolyDP(contour2f, approx, 0.02 * peri, true);

            // RULE 2: Must have 4 corners
            if (approx.total() == 4) {
                
                // RULE 3: Convexity Check
                // A chessboard is a simple convex shape.
                if (!Imgproc.isContourConvex(new MatOfPoint(approx.toArray()))) continue;

                // RULE 4: Find the largest candidate
                if (area > maxArea) {
                    maxArea = area;
                    bestApprox = approx;
                }
            }
        }

        if (bestApprox != null) {
            return orderPoints(bestApprox.toArray());
        }

        return null;
    }

    private static Point[] orderPoints(Point[] pts) {
        Point[] result = new Point[4];
        List<Point> points = Arrays.asList(pts);
        points.sort((p1, p2) -> Double.compare(p1.y, p2.y));
        List<Point> top = new ArrayList<>(points.subList(0, 2));
        top.sort((p1, p2) -> Double.compare(p1.x, p2.x));
        result[0] = top.get(0); 
        result[1] = top.get(1); 
        List<Point> bottom = new ArrayList<>(points.subList(2, 4));
        bottom.sort((p1, p2) -> Double.compare(p1.x, p2.x));
        result[3] = bottom.get(0); 
        result[2] = bottom.get(1); 
        return result;
    }

    private static List<Point> calculateGridCenters(Mat src, Point[] corners) {
        List<Point> centers = new ArrayList<>();

        Point[] dstPoints = new Point[]{
                new Point(0, 0),
                new Point(VIRTUAL_RESOLUTION, 0),
                new Point(VIRTUAL_RESOLUTION, VIRTUAL_RESOLUTION),
                new Point(0, VIRTUAL_RESOLUTION)
        };

        Mat srcMat = new MatOfPoint2f(corners);
        Mat dstMat = new MatOfPoint2f(dstPoints);
        Mat perspectiveMatrix = Imgproc.getPerspectiveTransform(srcMat, dstMat);
        Mat invertedMatrix = new Mat();
        Core.invert(perspectiveMatrix, invertedMatrix);

        double pixelsPerCm = VIRTUAL_RESOLUTION / BOARD_PHYSICAL_SIZE_CM;
        double offsetPixels = BORDER_OFFSET_CM * pixelsPerCm;
        double playableWidth = VIRTUAL_RESOLUTION - (2 * offsetPixels);
        double squareSize = playableWidth / 8.0;

        List<Point> virtualPoints = new ArrayList<>();
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                double x = offsetPixels + (col * squareSize) + (squareSize / 2.0);
                double y = offsetPixels + (row * squareSize) + (squareSize / 2.0);
                virtualPoints.add(new Point(x, y));
            }
        }

        MatOfPoint2f virtualMat = new MatOfPoint2f();
        virtualMat.fromList(virtualPoints);
        MatOfPoint2f originalMat = new MatOfPoint2f();
        Core.perspectiveTransform(virtualMat, originalMat, invertedMatrix);

        return originalMat.toList();
    }

    private static void drawCorners(Mat img, Point[] corners, Scalar color) {
        for (int i = 0; i < corners.length; i++) {
            Imgproc.line(img, corners[i], corners[(i + 1) % 4], color, 4);
        }
    }
}