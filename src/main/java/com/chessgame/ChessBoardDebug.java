package com.chessgame;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import nu.pattern.OpenCV;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ChessBoardDebug {

    // Physical board dimensions
    private static final double OUTER_BOARD_SIZE_CM = 44.0;  // Total board size
    private static final double INNER_BOARD_SIZE_CM = 40.0;  // Playable 8x8 area
    private static final double BORDER_WIDTH_CM = (OUTER_BOARD_SIZE_CM - INNER_BOARD_SIZE_CM) / 2.0; // 2cm per side
    
    private static final int VIRTUAL_RESOLUTION = 800;
    
    // Set to true to save intermediate images for debugging
    private static final boolean DEBUG_MODE = true;

    static { 
        OpenCV.loadLocally(); 
    }

    public static void main(String[] args) {
        String inputImagePath = Paths.get("src", "main", "resources", "tests", "test4.jpg").toString();
        String outputImagePath = Paths.get("output", "corners4.jpg").toString();
        String debugImagePath = Paths.get("output", "debug_all_attempts.jpg").toString();

        Mat src = Imgcodecs.imread(inputImagePath);
        if (src.empty()) {
            System.err.println("Cannot read image: " + inputImagePath);
            System.err.println("Please check if the file exists at the path.");
            return;
        }

        System.out.println("Image loaded. Resolution: " + src.width() + "x" + src.height());

        // Create a debug image to show all attempts
        Mat debugImg = src.clone();

        // 1. Detect the 4 corners of the OUTER board
        Point[] outerCorners = findBoardCorners(src, debugImg);

        // Save debug image showing all attempts
        Imgcodecs.imwrite(debugImagePath, debugImg);
        System.out.println("Debug image saved to " + debugImagePath);

        if (outerCorners == null) {
            System.err.println("FAILED: Could not detect a chessboard.");
            System.err.println("Tips: Ensure good lighting, contrast between board and table, and the board is fully visible.");
            System.err.println("Check " + debugImagePath + " to see all detection attempts.");
            return;
        }

        System.out.println("Success! Outer Board Detected.");
        
        // Draw outer corners in green
        drawCorners(src, outerCorners, new Scalar(0, 255, 0), "Outer");

        // 2. Calculate inner board corners (accounting for 2cm border on each side)
        Point[] innerCorners = calculateInnerCorners(outerCorners);
        
        // Draw inner corners in blue
        drawCorners(src, innerCorners, new Scalar(255, 0, 0), "Inner");

        // 3. Calculate the Grid Coordinates using INNER corners
        List<Point> squareCenters = calculateGridCenters(innerCorners);

        // 4. Draw the centers
        for (int i = 0; i < squareCenters.size(); i++) {
            Point p = squareCenters.get(i);
            Imgproc.circle(src, p, 5, new Scalar(0, 0, 255), -1);
            
            // Optional: Label each square (A1-H8)
            int row = i / 8;
            int col = i % 8;
            String label = (char)('A' + col) + "" + (8 - row);
            Imgproc.putText(src, label, new Point(p.x - 10, p.y + 5), 
                           Imgproc.FONT_HERSHEY_SIMPLEX, 0.4, new Scalar(255, 255, 0), 1);
        }

        Imgcodecs.imwrite(outputImagePath, src);
        System.out.println("Processing complete. Output saved to " + outputImagePath);
    }

    private static Point[] findBoardCorners(Mat originalSrc, Mat debugImg) {
        // Downscale for better detection
        double processingWidth = 600.0;
        double scale = 1.0;
        Mat src = new Mat();

        if (originalSrc.width() > processingWidth) {
            scale = processingWidth / originalSrc.width();
            Imgproc.resize(originalSrc, src, new Size(), scale, scale, Imgproc.INTER_AREA);
            System.out.println("Downscaled image for processing (Scale factor: " + scale + ")");
        } else {
            originalSrc.copyTo(src);
        }

        Mat gray = new Mat();
        Mat blurred = new Mat();
        Mat thresh = new Mat();

        // 1. Grayscale
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);

        // 2. Preprocessing
        Imgproc.GaussianBlur(gray, blurred, new Size(5, 5), 0);
        Imgproc.medianBlur(blurred, blurred, 3);

        // 3. Adaptive Thresholding
        Imgproc.adaptiveThreshold(blurred, thresh, 255, 
                Imgproc.ADAPTIVE_THRESH_MEAN_C, 
                Imgproc.THRESH_BINARY_INV, 15, 3);

        // 4. Morphological Operations
        Mat kernelDilate = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(5, 5));
        Mat kernelErode = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));
        
        Imgproc.dilate(thresh, thresh, kernelDilate);
        Imgproc.erode(thresh, thresh, kernelErode);
        
        if (DEBUG_MODE) {
            Imgcodecs.imwrite("debug_threshold.jpg", thresh);
        }

        // 5. Find Contours
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(thresh, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        // Sort by area
        contours.sort((c1, c2) -> Double.compare(Imgproc.contourArea(c2), Imgproc.contourArea(c1)));

        double imageArea = src.width() * src.height();
        
        int attemptNumber = 0;
        Scalar[] attemptColors = new Scalar[]{
            new Scalar(255, 0, 0),    // Blue
            new Scalar(0, 255, 255),  // Yellow
            new Scalar(255, 0, 255),  // Magenta
            new Scalar(0, 165, 255),  // Orange
            new Scalar(255, 255, 0),  // Cyan
            new Scalar(128, 0, 128),  // Purple
            new Scalar(0, 255, 0),    // Green
            new Scalar(255, 128, 0),  // Sky Blue
            new Scalar(128, 128, 128),// Gray
            new Scalar(200, 200, 200) // Light Gray
        };

        for (int i = 0; i < Math.min(contours.size(), 10); i++) {
            MatOfPoint contour = contours.get(i);
            double area = Imgproc.contourArea(contour);

            System.out.println("\n--- Checking contour #" + i + " ---");
            System.out.println("Area: " + area + " (" + (area/imageArea*100) + "% of image)");

            // Skip if too small or too large
            if (area < imageArea * 0.1) {
                System.out.println("Skipped: Too small (< 10% of image)");
                continue;
            }
            
            if (area > imageArea * 0.95) {
                System.out.println("Skipped: Too large (> 95% of image, probably the whole image border)");
                continue;
            }

            // Convex hull and polygon approximation
            MatOfInt hullIdx = new MatOfInt();
            Imgproc.convexHull(contour, hullIdx);
            
            Point[] contourArray = contour.toArray();
            Point[] hullPoints = new Point[hullIdx.rows()];
            for(int j=0; j < hullIdx.rows(); j++) {
                hullPoints[j] = contourArray[hullIdx.toArray()[j]];
            }
            MatOfPoint2f hull2f = new MatOfPoint2f(hullPoints);

            // Try different epsilon values
            for (double epsilonFactor = 0.02; epsilonFactor <= 0.08; epsilonFactor += 0.01) {
                double peri = Imgproc.arcLength(hull2f, true);
                MatOfPoint2f approx = new MatOfPoint2f();
                Imgproc.approxPolyDP(hull2f, approx, epsilonFactor * peri, true);

                if (approx.total() == 4) {
                    Point[] foundPoints = approx.toArray();
                    
                    System.out.println("Found 4-sided polygon with epsilon: " + epsilonFactor);
                    
                    // Scale to original image for visualization
                    Point[] scaledPoints = new Point[4];
                    for(int k = 0; k < 4; k++) {
                        scaledPoints[k] = new Point(foundPoints[k].x / scale, foundPoints[k].y / scale);
                    }
                    
                    // Draw this attempt on debug image
                    Scalar color = attemptColors[attemptNumber % attemptColors.length];
                    Point[] orderedScaled = orderPoints(scaledPoints);
                    for (int k = 0; k < 4; k++) {
                        Imgproc.line(debugImg, orderedScaled[k], orderedScaled[(k + 1) % 4], color, 2);
                        Imgproc.circle(debugImg, orderedScaled[k], 8, color, -1);
                        Imgproc.putText(debugImg, "A" + attemptNumber + "C" + k, 
                                       new Point(orderedScaled[k].x + 10, orderedScaled[k].y - 10),
                                       Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, color, 2);
                    }
                    attemptNumber++;
                    
                    // VALIDATION: Check if it forms a proper quadrilateral
                    if (!isValidQuadrilateral(foundPoints)) {
                        System.out.println("Rejected: Invalid quadrilateral");
                        continue;
                    }
                    
                    // Additional check: Board should be reasonably centered in the image
                    Point[] ordered = orderPoints(foundPoints);
                    double centerX = (ordered[0].x + ordered[1].x + ordered[2].x + ordered[3].x) / 4.0;
                    double centerY = (ordered[0].y + ordered[1].y + ordered[2].y + ordered[3].y) / 4.0;
                    
                    double imgCenterX = src.width() / 2.0;
                    double imgCenterY = src.height() / 2.0;
                    
                    double centerDistX = Math.abs(centerX - imgCenterX) / src.width();
                    double centerDistY = Math.abs(centerY - imgCenterY) / src.height();
                    
                    System.out.println("Board center offset: X=" + (centerDistX*100) + "%, Y=" + (centerDistY*100) + "%");
                    
                    // Board should be relatively centered (within 30% of center)
                    if (centerDistX > 0.3 || centerDistY > 0.3) {
                        System.out.println("Rejected: Board too off-center (might be a piece or edge)");
                        continue;
                    }
                    
                    // Scale back to original image size
                    for(Point p : foundPoints) {
                        p.x = p.x / scale;
                        p.y = p.y / scale;
                    }
                    
                    System.out.println("✓✓✓ Found valid board corners! ✓✓✓");
                    return orderPoints(foundPoints);
                }
            }
        }

        return null;
    }

    /**
     * Validates that 4 points form a proper quadrilateral.
     * Very lenient check - mainly ensures it's roughly quadrilateral shaped
     * and filters out obvious non-board shapes.
     */
    private static boolean isValidQuadrilateral(Point[] pts) {
        Point[] ordered = orderPoints(pts);
        // ordered: [TL, TR, BR, BL]
        
        Point tl = ordered[0];
        Point tr = ordered[1];
        Point br = ordered[2];
        Point bl = ordered[3];
        
        // Calculate dimensions
        double topWidth = tr.x - tl.x;
        double bottomWidth = br.x - bl.x;
        double leftHeight = bl.y - tl.y;
        double rightHeight = br.y - tr.y;
        
        double avgWidth = (topWidth + bottomWidth) / 2.0;
        double avgHeight = (leftHeight + rightHeight) / 2.0;
        
        System.out.println("Quadrilateral check:");
        System.out.println("  Top width: " + topWidth + ", Bottom width: " + bottomWidth);
        System.out.println("  Left height: " + leftHeight + ", Right height: " + rightHeight);
        System.out.println("  TL: (" + tl.x + ", " + tl.y + ")");
        System.out.println("  TR: (" + tr.x + ", " + tr.y + ")");
        System.out.println("  BR: (" + br.x + ", " + br.y + ")");
        System.out.println("  BL: (" + bl.x + ", " + bl.y + ")");
        
        // Check 1: Top corners should be roughly horizontally aligned (similar Y)
        double topYDiff = Math.abs(tr.y - tl.y);
        double maxTopYDiff = avgHeight * 0.15; // Top edge can vary max 15% of height
        if (topYDiff > maxTopYDiff) {
            System.out.println("Failed: Top corners not horizontally aligned. Y diff: " + topYDiff + " (max: " + maxTopYDiff + ")");
            return false;
        }
        
        // Check 2: Bottom corners should be roughly horizontally aligned (similar Y)
        double bottomYDiff = Math.abs(br.y - bl.y);
        double maxBottomYDiff = avgHeight * 0.15; // Bottom edge can vary max 15% of height
        if (bottomYDiff > maxBottomYDiff) {
            System.out.println("Failed: Bottom corners not horizontally aligned. Y diff: " + bottomYDiff + " (max: " + maxBottomYDiff + ")");
            return false;
        }
        
        // Check 3: Width ratio shouldn't be too extreme (handles severe distortion)
        double widthRatio = Math.min(topWidth, bottomWidth) / Math.max(topWidth, bottomWidth);
        if (widthRatio < 0.3) { 
            System.out.println("Failed: Width ratio too extreme: " + widthRatio);
            return false;
        }
        
        // Check 4: Height ratio shouldn't be too extreme
        double heightRatio = Math.min(leftHeight, rightHeight) / Math.max(leftHeight, rightHeight);
        if (heightRatio < 0.3) {
            System.out.println("Failed: Height ratio too extreme: " + heightRatio);
            return false;
        }
        
        // Check 5: Aspect ratio should be somewhat square-ish (not a thin rectangle)
        double aspectRatio = avgWidth / avgHeight;
        if (aspectRatio < 0.3 || aspectRatio > 3.0) {
            System.out.println("Failed: Aspect ratio too extreme: " + aspectRatio);
            return false;
        }
        
        // Check 6: Vertical edges - X alignment (lenient 50% tolerance)
        double leftXDiff = Math.abs(bl.x - tl.x);
        double rightXDiff = Math.abs(br.x - tr.x);
        double maxAllowedXDiff = avgWidth * 0.5;
        
        if (leftXDiff > maxAllowedXDiff) {
            System.out.println("Failed: Left edge X difference too large: " + leftXDiff + " (tolerance: " + maxAllowedXDiff + ")");
            return false;
        }
        
        if (rightXDiff > maxAllowedXDiff) {
            System.out.println("Failed: Right edge X difference too large: " + rightXDiff + " (tolerance: " + maxAllowedXDiff + ")");
            return false;
        }
        
        System.out.println("✓ Validation passed - proper quadrilateral detected");
        System.out.println("  Top Y diff: " + topYDiff + ", Bottom Y diff: " + bottomYDiff);
        return true;
    }

    private static Point[] orderPoints(Point[] pts) {
        Point[] result = new Point[4];
        List<Point> points = Arrays.asList(pts);
        
        // Sort by Y coordinate
        points.sort((p1, p2) -> Double.compare(p1.y, p2.y));
        
        // Top 2 points
        List<Point> top = new ArrayList<>(points.subList(0, 2));
        top.sort((p1, p2) -> Double.compare(p1.x, p2.x));
        result[0] = top.get(0); // TL
        result[1] = top.get(1); // TR
        
        // Bottom 2 points
        List<Point> bottom = new ArrayList<>(points.subList(2, 4));
        bottom.sort((p1, p2) -> Double.compare(p1.x, p2.x));
        result[3] = bottom.get(0); // BL
        result[2] = bottom.get(1); // BR

        return result;
    }

    /**
     * Calculate inner board corners by shrinking the outer board.
     * The border is 2cm on each side (44cm outer -> 40cm inner).
     */
    private static Point[] calculateInnerCorners(Point[] outerCorners) {
        // outerCorners: [TL, TR, BR, BL]
        Point tl = outerCorners[0];
        Point tr = outerCorners[1];
        Point br = outerCorners[2];
        Point bl = outerCorners[3];
        
        // Calculate the shrink ratio: inner/outer = 40/44
        double shrinkRatio = INNER_BOARD_SIZE_CM / OUTER_BOARD_SIZE_CM;
        
        // The border offset as a ratio from each edge
        double offsetRatio = (1.0 - shrinkRatio) / 2.0; // = (1 - 40/44) / 2 = 0.0454...
        
        // Calculate inner corners using linear interpolation
        Point[] innerCorners = new Point[4];
        
        // Top-left inner
        innerCorners[0] = new Point(
            tl.x + offsetRatio * (tr.x - tl.x) + offsetRatio * (bl.x - tl.x),
            tl.y + offsetRatio * (tr.y - tl.y) + offsetRatio * (bl.y - tl.y)
        );
        
        // Top-right inner
        innerCorners[1] = new Point(
            tr.x - offsetRatio * (tr.x - tl.x) + offsetRatio * (br.x - tr.x),
            tr.y - offsetRatio * (tr.y - tl.y) + offsetRatio * (br.y - tr.y)
        );
        
        // Bottom-right inner
        innerCorners[2] = new Point(
            br.x - offsetRatio * (br.x - bl.x) - offsetRatio * (br.x - tr.x),
            br.y - offsetRatio * (br.y - bl.y) - offsetRatio * (br.y - tr.y)
        );
        
        // Bottom-left inner
        innerCorners[3] = new Point(
            bl.x + offsetRatio * (br.x - bl.x) - offsetRatio * (bl.x - tl.x),
            bl.y + offsetRatio * (br.y - bl.y) - offsetRatio * (bl.y - tl.y)
        );
        
        System.out.println("Calculated inner corners with " + BORDER_WIDTH_CM + "cm border offset");
        return innerCorners;
    }

    private static List<Point> calculateGridCenters(Point[] innerCorners) {
        List<Point> centers = new ArrayList<>();

        // Set up perspective transform to map inner board to virtual square
        Point[] dstPoints = new Point[]{
                new Point(0, 0),
                new Point(VIRTUAL_RESOLUTION, 0),
                new Point(VIRTUAL_RESOLUTION, VIRTUAL_RESOLUTION),
                new Point(0, VIRTUAL_RESOLUTION)
        };

        Mat srcMat = new MatOfPoint2f(innerCorners);
        Mat dstMat = new MatOfPoint2f(dstPoints);
        Mat perspectiveMatrix = Imgproc.getPerspectiveTransform(srcMat, dstMat);
        Mat invertedMatrix = new Mat();
        Core.invert(perspectiveMatrix, invertedMatrix);

        // Now the virtual space directly represents the 40cm x 40cm playable area
        // Divide into 8x8 grid
        double squareSize = VIRTUAL_RESOLUTION / 8.0;

        List<Point> virtualPoints = new ArrayList<>();
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                double x = (col * squareSize) + (squareSize / 2.0);
                double y = (row * squareSize) + (squareSize / 2.0);
                virtualPoints.add(new Point(x, y));
            }
        }

        // Transform back to original image coordinates
        MatOfPoint2f virtualMat = new MatOfPoint2f();
        virtualMat.fromList(virtualPoints);
        MatOfPoint2f originalMat = new MatOfPoint2f();
        Core.perspectiveTransform(virtualMat, originalMat, invertedMatrix);

        return originalMat.toList();
    }

    private static void drawCorners(Mat img, Point[] corners, Scalar color, String label) {
        for (int i = 0; i < corners.length; i++) {
            Imgproc.line(img, corners[i], corners[(i + 1) % 4], color, 3);
            Imgproc.circle(img, corners[i], 8, color, -1);
            Imgproc.putText(img, label + i, 
                           new Point(corners[i].x + 10, corners[i].y - 10), 
                           Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, color, 2);
        }
    }
}