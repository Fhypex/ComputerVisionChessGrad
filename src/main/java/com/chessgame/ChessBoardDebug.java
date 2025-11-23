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

        String nameOfFile = "IMG_9640.jpg";

        String inputImagePath = Paths.get("src", "main", "resources", "tests", nameOfFile).toString();

        String outputImagePath = Paths.get("output", nameOfFile).toString();

        String debugImagePath = Paths.get("output", "debug_all_attempts.jpg").toString();

        String squaresOutputDir = Paths.get("output", "squares").toString();
        java.io.File f = new java.io.File(inputImagePath);
        String fileNameWithExt = f.getName();
        String baseFileName = fileNameWithExt.replaceFirst("[.][^.]+$", "");
        Mat src = Imgcodecs.imread(inputImagePath);
        if (src.empty()) {
            System.err.println("Cannot read image: " + inputImagePath);
            System.err.println("Please check if the file exists at the path.");
            return;
        }

        System.out.println("Image loaded. Resolution: " + src.width() + "x" + src.height());

        // Create output directory for squares
        new java.io.File(squaresOutputDir).mkdirs();

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

        // 5. Extract and save individual square images
        // IMPORTANT: Use OUTER corners for extraction to avoid cutting off pieces on row 8
        System.out.println("\n=== Extracting individual squares ===");
        extractSquareImages(Imgcodecs.imread(inputImagePath), outerCorners, innerCorners, squaresOutputDir , baseFileName);
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
    
    // 1. Grayscale
    Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);
    
    // 2. Enhanced preprocessing with better noise reduction
    Imgproc.GaussianBlur(gray, blurred, new Size(7, 7), 0);
    
    // NEW: Add bilateral filter to reduce noise while preserving edges
    Mat bilateral = new Mat();
    Imgproc.bilateralFilter(blurred, bilateral, 9, 75, 75);
    
    // NEW: Detect strong edges using gradient magnitude
    Mat gradX = new Mat();
    Mat gradY = new Mat();
    Mat gradMag = new Mat();
    Imgproc.Sobel(bilateral, gradX, CvType.CV_64F, 1, 0, 3);
    Imgproc.Sobel(bilateral, gradY, CvType.CV_64F, 0, 1, 3);
    Core.magnitude(gradX, gradY, gradMag);
    Mat gradMag8U = new Mat();
    gradMag.convertTo(gradMag8U, CvType.CV_8U);
    
    // NEW: Threshold gradient magnitude to get strong edges only
    Mat strongEdges = new Mat();
    Imgproc.threshold(gradMag8U, strongEdges, 0, 255, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU);
    
    // 3. Try multiple thresholding approaches
    List<Mat> thresholdedImages = new ArrayList<>();
    
    // Approach 1: Adaptive threshold (existing)
    Mat thresh1 = new Mat();
    Imgproc.adaptiveThreshold(bilateral, thresh1, 255, 
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, 
            Imgproc.THRESH_BINARY_INV, 21, 5);
    thresholdedImages.add(thresh1);
    
    // Approach 2: Otsu's thresholding
    Mat thresh2 = new Mat();
    Imgproc.threshold(bilateral, thresh2, 0, 255, 
            Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU);
    thresholdedImages.add(thresh2);
    
    // Approach 3: Canny edge detection (improved)
    Mat edges = new Mat();
    Imgproc.Canny(bilateral, edges, 50, 150);
    Mat kernelEdge = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));
    Imgproc.dilate(edges, edges, kernelEdge, new Point(-1, -1), 2);
    thresholdedImages.add(edges);
    
    // NEW: Approach 4: Strong gradient edges
    thresholdedImages.add(strongEdges);
    
    // NEW: Approach 5: Morphological gradient (emphasizes boundaries)
    Mat morphGrad = new Mat();
    Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));
    Imgproc.morphologyEx(bilateral, morphGrad, Imgproc.MORPH_GRADIENT, kernel);
    Mat morphThresh = new Mat();
    Imgproc.threshold(morphGrad, morphThresh, 0, 255, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU);
    thresholdedImages.add(morphThresh);

    if (DEBUG_MODE) {
        Imgcodecs.imwrite("debug_threshold1_adaptive.jpg", thresh1);
        Imgcodecs.imwrite("debug_threshold2_otsu.jpg", thresh2);
        Imgcodecs.imwrite("debug_threshold3_edges.jpg", edges);
        Imgcodecs.imwrite("debug_threshold4_gradient.jpg", strongEdges);
        Imgcodecs.imwrite("debug_threshold5_morph.jpg", morphThresh);
    }

    double imageArea = src.width() * src.height();
    
    // NEW: Store candidates with scores for better selection
    List<CandidateQuad> candidates = new ArrayList<>();
    
    // Try each thresholding approach
    for (int threshIdx = 0; threshIdx < thresholdedImages.size(); threshIdx++) {
        Mat currentThresh = thresholdedImages.get(threshIdx);
        
        System.out.println("\n=== Trying threshold approach #" + threshIdx + " ===");
        
        // Enhanced morphological operations
        Mat processed = new Mat();
        Mat kernelDilate = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(5, 5));
        Mat kernelErode = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));
        
        // NEW: Use closing operation to connect broken edges
        Mat kernelClose = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(7, 7));
        Imgproc.morphologyEx(currentThresh, processed, Imgproc.MORPH_CLOSE, kernelClose);
        
        Imgproc.dilate(processed, processed, kernelDilate);
        Imgproc.erode(processed, processed, kernelErode);

        // Find Contours
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(processed, contours, hierarchy, 
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        // Sort by area
        contours.sort((c1, c2) -> Double.compare(Imgproc.contourArea(c2), Imgproc.contourArea(c1)));

        int attemptNumber = threshIdx * 10;
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

        for (int i = 0; i < Math.min(contours.size(), 15); i++) {
            MatOfPoint contour = contours.get(i);
            double area = Imgproc.contourArea(contour);

            System.out.println("\n--- Checking contour #" + i + " (threshold " + threshIdx + ") ---");
            System.out.println("Area: " + area + " (" + (area/imageArea*100) + "% of image)");

            // Adjusted size constraints
            if (area < imageArea * 0.20) {
                System.out.println("Skipped: Too small (< 20% of image)");
                continue;
            }
            
            if (area > imageArea * 0.85) {
                System.out.println("Skipped: Too large (> 85% of image)");
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

            // Try multiple epsilon values
            for (double epsilonFactor = 0.015; epsilonFactor <= 0.10; epsilonFactor += 0.005) {
                double peri = Imgproc.arcLength(hull2f, true);
                MatOfPoint2f approx = new MatOfPoint2f();
                Imgproc.approxPolyDP(hull2f, approx, epsilonFactor * peri, true);

                if (approx.total() == 4) {
                    Point[] foundPoints = approx.toArray();
                    
                    System.out.println("Found 4-sided polygon with epsilon: " + epsilonFactor);
                    
                    // VALIDATION: Check if it forms a proper quadrilateral
                    if (!isValidQuadrilateral(foundPoints)) {
                        System.out.println("Rejected: Invalid quadrilateral");
                        continue;
                    }
                    
                    // Additional validation: Check corner angles
                    Point[] ordered = orderPoints(foundPoints);
                    if (!hasReasonableAngles(ordered)) {
                        System.out.println("Rejected: Corner angles too extreme");
                        continue;
                    }
                    
                    // Check if board is reasonably centered
                    double centerX = (ordered[0].x + ordered[1].x + ordered[2].x + ordered[3].x) / 4.0;
                    double centerY = (ordered[0].y + ordered[1].y + ordered[2].y + ordered[3].y) / 4.0;
                    
                    double imgCenterX = src.width() / 2.0;
                    double imgCenterY = src.height() / 2.0;
                    
                    double centerDistX = Math.abs(centerX - imgCenterX) / src.width();
                    double centerDistY = Math.abs(centerY - imgCenterY) / src.height();
                    
                    System.out.println("Board center offset: X=" + (centerDistX*100) + "%, Y=" + (centerDistY*100) + "%");
                    
                    if (centerDistX > 0.40 || centerDistY > 0.40) {
                        System.out.println("Rejected: Board too off-center");
                        continue;
                    }
                    
                    // Check solidity
                    double solidity = area / Imgproc.contourArea(new MatOfPoint(hullPoints));
                    System.out.println("Solidity: " + solidity);
                    if (solidity < 0.85) {
                        System.out.println("Rejected: Solidity too low");
                        continue;
                    }
                    
                    // NEW: Calculate comprehensive score
                    double score = calculateCandidateScore(ordered, area, solidity, centerDistX, centerDistY, src);
                    
                    // NEW: Check for chess board pattern inside candidate region
                    double patternScore = checkChessBoardPattern(src, ordered);
                    score += patternScore * 0.3; // Weight pattern detection
                    
                    // NEW: Check color consistency
                    double colorConsistency = checkColorConsistency(src, ordered);
                    score += colorConsistency * 0.2;
                    
                    System.out.println("Candidate score: " + score + " (pattern: " + patternScore + ", color: " + colorConsistency + ")");
                    
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
                    
                    // Scale back to original image size
                    Point[] scaledForCandidate = new Point[4];
                    for(int k = 0; k < 4; k++) {
                        scaledForCandidate[k] = new Point(foundPoints[k].x / scale, foundPoints[k].y / scale);
                    }
                    
                    // Store candidate with score
                    candidates.add(new CandidateQuad(orderPoints(scaledForCandidate), score));
                }
            }
        }
    }
    
    // NEW: Select best candidate based on score
    if (!candidates.isEmpty()) {
        candidates.sort((c1, c2) -> Double.compare(c2.score, c1.score));
        CandidateQuad best = candidates.get(0);
        System.out.println("✓✓✓ Found best board corners with score: " + best.score + " ✓✓✓");
        return best.corners;
    }

    return null;
}

// NEW: Helper class to store candidates with scores
private static class CandidateQuad {
    Point[] corners;
    double score;
    
    CandidateQuad(Point[] corners, double score) {
        this.corners = corners;
        this.score = score;
    }
}

// NEW: Calculate comprehensive candidate score
private static double calculateCandidateScore(Point[] ordered, double area, double solidity, 
                                               double centerDistX, double centerDistY, Mat src) {
    double score = 0.0;
    
    // Area score (prefer medium-large boards)
    double areaRatio = area / (src.width() * src.height());
    if (areaRatio >= 0.25 && areaRatio <= 0.75) {
        score += 1.0; // Optimal size
    } else if (areaRatio >= 0.20 && areaRatio <= 0.85) {
        score += 0.5; // Acceptable size
    }
    
    // Solidity score
    score += solidity * 0.5;
    
    // Centering score
    double centerScore = 1.0 - (centerDistX + centerDistY) / 2.0;
    score += centerScore * 0.3;
    
    // Aspect ratio score (prefer square-ish)
    double topWidth = Math.sqrt(Math.pow(ordered[1].x - ordered[0].x, 2) + Math.pow(ordered[1].y - ordered[0].y, 2));
    double bottomWidth = Math.sqrt(Math.pow(ordered[2].x - ordered[3].x, 2) + Math.pow(ordered[2].y - ordered[3].y, 2));
    double leftHeight = Math.sqrt(Math.pow(ordered[3].x - ordered[0].x, 2) + Math.pow(ordered[3].y - ordered[0].y, 2));
    double rightHeight = Math.sqrt(Math.pow(ordered[2].x - ordered[1].x, 2) + Math.pow(ordered[2].y - ordered[1].y, 2));
    double avgWidth = (topWidth + bottomWidth) / 2.0;
    double avgHeight = (leftHeight + rightHeight) / 2.0;
    double aspectRatio = avgWidth / avgHeight;
    double aspectScore = 1.0 - Math.abs(1.0 - aspectRatio) * 2.0; // Prefer 1:1
    aspectScore = Math.max(0, aspectScore);
    score += aspectScore * 0.2;
    
    return score;
}

// NEW: Check for chess board pattern inside candidate region
private static double checkChessBoardPattern(Mat src, Point[] corners) {
    try {
        // Warp the candidate region to a square
        Point[] ordered = orderPoints(corners);
        int size = 400;
        Point[] dstPoints = new Point[]{
            new Point(0, 0),
            new Point(size, 0),
            new Point(size, size),
            new Point(0, size)
        };
        
        Mat srcMat = new MatOfPoint2f(ordered);
        Mat dstMat = new MatOfPoint2f(dstPoints);
        Mat perspectiveMatrix = Imgproc.getPerspectiveTransform(srcMat, dstMat);
        
        Mat warped = new Mat();
        Imgproc.warpPerspective(src, warped, perspectiveMatrix, new Size(size, size));
        
        // Convert to grayscale
        Mat grayWarped = new Mat();
        Imgproc.cvtColor(warped, grayWarped, Imgproc.COLOR_BGR2GRAY);
        
        // Divide into 8x8 grid and check for alternating pattern
        int squareSize = size / 8;
        int alternatingMatches = 0;
        int totalSquares = 0;
        
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Rect squareRect = new Rect(col * squareSize, row * squareSize, squareSize, squareSize);
                Mat square = new Mat(grayWarped, squareRect);
                
                Scalar mean = Core.mean(square);
                double brightness = mean.val[0];
                
                // Check if brightness matches expected pattern (alternating)
                boolean expectedDark = (row + col) % 2 == 1;
                boolean isDark = brightness < 128;
                
                if (expectedDark == isDark) {
                    alternatingMatches++;
                }
                totalSquares++;
            }
        }
        
        double patternScore = (double)alternatingMatches / totalSquares;
        System.out.println("Pattern match: " + (patternScore * 100) + "%");
        return patternScore;
    } catch (Exception e) {
        System.out.println("Pattern check failed: " + e.getMessage());
        return 0.0;
    }
}

// NEW: Check color consistency (board should have relatively uniform color)
private static double checkColorConsistency(Mat src, Point[] corners) {
    try {
        // Sample points inside the quadrilateral
        Point[] ordered = orderPoints(corners);
        List<Scalar> samples = new ArrayList<>();
        
        // Sample center and midpoints of edges
        double centerX = (ordered[0].x + ordered[1].x + ordered[2].x + ordered[3].x) / 4.0;
        double centerY = (ordered[0].y + ordered[1].y + ordered[2].y + ordered[3].y) / 4.0;
        
        // Sample multiple points
        for (int i = 0; i < 4; i++) {
            Point p1 = ordered[i];
            Point p2 = ordered[(i + 1) % 4];
            double midX = (p1.x + p2.x) / 2.0;
            double midY = (p1.y + p2.y) / 2.0;
            
            // Sample point between center and midpoint
            double sampleX = (centerX + midX) / 2.0;
            double sampleY = (centerY + midY) / 2.0;
            
            if (sampleX >= 0 && sampleX < src.width() && sampleY >= 0 && sampleY < src.height()) {
                Mat sampleMat = new Mat(src, new Rect((int)sampleX, (int)sampleY, 10, 10));
                Scalar mean = Core.mean(sampleMat);
                samples.add(mean);
            }
        }
        
        if (samples.size() < 3) return 0.0;
        
        // Calculate variance in color
        double meanB = 0, meanG = 0, meanR = 0;
        for (Scalar s : samples) {
            meanB += s.val[0];
            meanG += s.val[1];
            meanR += s.val[2];
        }
        meanB /= samples.size();
        meanG /= samples.size();
        meanR /= samples.size();
        
        double variance = 0;
        for (Scalar s : samples) {
            variance += Math.pow(s.val[0] - meanB, 2);
            variance += Math.pow(s.val[1] - meanG, 2);
            variance += Math.pow(s.val[2] - meanR, 2);
        }
        variance /= (samples.size() * 3);
        
        // Lower variance = more consistent = higher score
        double consistencyScore = 1.0 / (1.0 + variance / 100.0);
        return consistencyScore;
    } catch (Exception e) {
        System.out.println("Color consistency check failed: " + e.getMessage());
        return 0.0;
    }
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
    double topWidth = Math.sqrt(Math.pow(tr.x - tl.x, 2) + Math.pow(tr.y - tl.y, 2));
    double bottomWidth = Math.sqrt(Math.pow(br.x - bl.x, 2) + Math.pow(br.y - bl.y, 2));
    double leftHeight = Math.sqrt(Math.pow(bl.x - tl.x, 2) + Math.pow(bl.y - tl.y, 2));
    double rightHeight = Math.sqrt(Math.pow(br.x - tr.x, 2) + Math.pow(br.y - tr.y, 2));
    
    double avgWidth = (topWidth + bottomWidth) / 2.0;
    double avgHeight = (leftHeight + rightHeight) / 2.0;
    
    System.out.println("Quadrilateral check:");
    System.out.println("  Top width: " + topWidth + ", Bottom width: " + bottomWidth);
    System.out.println("  Left height: " + leftHeight + ", Right height: " + rightHeight);
    
    // Check 1: Width ratio shouldn't be too extreme
    double widthRatio = Math.min(topWidth, bottomWidth) / Math.max(topWidth, bottomWidth);
    if (widthRatio < 0.5) { // More strict than 0.3
        System.out.println("Failed: Width ratio too extreme: " + widthRatio);
        return false;
    }
    
    // Check 2: Height ratio shouldn't be too extreme
    double heightRatio = Math.min(leftHeight, rightHeight) / Math.max(leftHeight, rightHeight);
    if (heightRatio < 0.5) { // More strict than 0.3
        System.out.println("Failed: Height ratio too extreme: " + heightRatio);
        return false;
    }
    
    // Check 3: Aspect ratio should be somewhat square-ish
    double aspectRatio = avgWidth / avgHeight;
    if (aspectRatio < 0.6 || aspectRatio > 1.7) { // More strict range
        System.out.println("Failed: Aspect ratio not square enough: " + aspectRatio);
        return false;
    }
    
    // Check 4: Diagonals should be roughly equal (square property)
    double diag1 = Math.sqrt(Math.pow(br.x - tl.x, 2) + Math.pow(br.y - tl.y, 2));
    double diag2 = Math.sqrt(Math.pow(bl.x - tr.x, 2) + Math.pow(bl.y - tr.y, 2));
    double diagRatio = Math.min(diag1, diag2) / Math.max(diag1, diag2);
    
    System.out.println("Diagonal ratio: " + diagRatio);
    if (diagRatio < 0.8) {
        System.out.println("Failed: Diagonals too unequal: " + diagRatio);
        return false;
    }
    
    System.out.println("✓ Validation passed - proper quadrilateral detected");
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

    // 1. Calculate the BASE offset ratio (approx 0.045)
    double baseOffsetRatio = (1.0 - shrinkRatio) / 2.0;

    // 2. APPLY YOUR ADJUSTMENTS HERE
    // Top border is 10% thinner (0.9), Bottom border is 10% thicker (1.1)
    double topRatio = baseOffsetRatio * 0.84;
    double bottomRatio = baseOffsetRatio * 1.16;
    double sideRatio = baseOffsetRatio; // Sides stay standard

    Point[] innerCorners = new Point[4];

    // Top-left inner
    // Moves Right by sideRatio, Moves DOWN by topRatio
    innerCorners[0] = new Point(
        tl.x + sideRatio * (tr.x - tl.x) + topRatio * (bl.x - tl.x),
        tl.y + sideRatio * (tr.y - tl.y) + topRatio * (bl.y - tl.y)
    );

    // Top-right inner
    // Moves Left by sideRatio, Moves DOWN by topRatio
    innerCorners[1] = new Point(
        tr.x - sideRatio * (tr.x - tl.x) + topRatio * (br.x - tr.x),
        tr.y - sideRatio * (tr.y - tl.y) + topRatio * (br.y - tr.y)
    );

    // Bottom-right inner
    // Moves Left by sideRatio, Moves UP by bottomRatio
    innerCorners[2] = new Point(
        br.x - sideRatio * (br.x - bl.x) - bottomRatio * (br.x - tr.x),
        br.y - sideRatio * (br.y - bl.y) - bottomRatio * (br.y - tr.y)
    );

    // Bottom-left inner
    // Moves Right by sideRatio, Moves UP by bottomRatio
    innerCorners[3] = new Point(
        bl.x + sideRatio * (br.x - bl.x) - bottomRatio * (bl.x - tl.x),
        bl.y + sideRatio * (br.y - bl.y) - bottomRatio * (bl.y - tl.y)
    );

    System.out.println("Calculated inner corners with adjusted offsets:");
    System.out.println("Top: " + (topRatio*100) + "%, Bottom: " + (bottomRatio*100) + "%");
    
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

    /**
     * Extracts individual square images from the chessboard.
     * Uses OUTER corners to warp, then crops based on INNER grid to avoid losing pieces.
     * Each square is cropped with extra vertical height to capture tall pieces.
     * Adjusts for perspective: bottom rows get more offset, top rows get less.
     * 
     * @param src Original source image
     * @param outerCorners The 4 corners of the outer board (44cm)
     * @param innerCorners The 4 corners of the playable 8x8 board (40cm)
     * @param outputDir Directory to save square images
     */
    private static void extractSquareImages(Mat src, Point[] outerCorners, Point[] innerCorners, String outputDir , String baseFileName) {
        int warpedWidth = VIRTUAL_RESOLUTION;
        
        // --- FIX START: Define a "Sky Buffer" ---
        // We need extra space ABOVE the board in the warped image for tall pieces on the back rank.
        // Let's add 50% of the board height as a buffer above.
        int skyBuffer = (int)(warpedWidth * 0.5); 
        int warpedHeight = warpedWidth + skyBuffer; // Total height of the new image
        
        // Set up perspective transform using OUTER corners
        // Crucial Change: We shift the Y coordinates down by 'skyBuffer'
        Point[] dstPoints = new Point[]{
                new Point(0, skyBuffer),                  // TL maps to (0, skyBuffer)
                new Point(warpedWidth, skyBuffer),        // TR
                new Point(warpedWidth, warpedWidth + skyBuffer), // BR
                new Point(0, warpedWidth + skyBuffer)     // BL
        };
        // --- FIX END ---

        Mat srcMat = new MatOfPoint2f(outerCorners);
        Mat dstMat = new MatOfPoint2f(dstPoints);
        Mat perspectiveMatrix = Imgproc.getPerspectiveTransform(srcMat, dstMat);

        // Warp the image with the new height
        Mat warpedBoard = new Mat();
        Imgproc.warpPerspective(src, warpedBoard, perspectiveMatrix, 
                                new Size(warpedWidth, warpedHeight));

        if (DEBUG_MODE) {
            Imgcodecs.imwrite("debug_warped_board_with_buffer.jpg", warpedBoard);
        }

        // Calculate geometry
        double outerSize = OUTER_BOARD_SIZE_CM;
        double innerSize = INNER_BOARD_SIZE_CM;
        double borderRatio = BORDER_WIDTH_CM / outerSize; 
        
        // Inner board pixel calculation
        double innerStartPixel = warpedWidth * borderRatio;
        double innerSizePixel = warpedWidth * (innerSize / outerSize);
        double squareSize = innerSizePixel / 8.0;
        
        // Base extra padding for pieces
        double baseExtraHeightRatio = 0.85;  
        double extraWidthRatio = 0.3;       
        int extraWidthPerSide = (int)(squareSize * extraWidthRatio / 2.0);

        int squareNumber = 1;
        
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                
                // --- FIX: Adjust Base Y to account for the Sky Buffer ---
                // The board doesn't start at Y=0 anymore; it starts at Y=skyBuffer
                double baseX = innerStartPixel + (col * squareSize);
                double baseY = skyBuffer + innerStartPixel + (row * squareSize); 
                
                // Logic for row adjustments (unchanged, but now safe to use)
                double rowAdjustment;

                if (row == 0) {
                    // Rank 8: Can now safely grab huge chunks upwards
                    rowAdjustment = baseExtraHeightRatio + 0.3; 
                } else if (row < 2) {
                    rowAdjustment = baseExtraHeightRatio + 0.2;
                } else if(row < 4) {
                    rowAdjustment = baseExtraHeightRatio;
                }  else if(row < 6) {
                    rowAdjustment = baseExtraHeightRatio - 0.2;
                } else {
                    rowAdjustment = baseExtraHeightRatio -0.3;
                }
                
                int extraHeight = (int)(squareSize * rowAdjustment);

                // Calculate Crop Coordinates
                // Since we added skyBuffer, (baseY - extraHeight) will no longer be negative!
                int extendedX = (int)Math.max(0, baseX - extraWidthPerSide);
                int extendedY = (int)Math.max(0, baseY - extraHeight);
                
                int extendedWidth = (int)squareSize + (2 * extraWidthPerSide);
                int extendedHeight = (int)squareSize + extraHeight;
                
                // Boundary Checks (Prevent crashing if we go off the bottom/right)
                if (extendedX + extendedWidth > warpedBoard.width()) {
                    extendedWidth = warpedBoard.width() - extendedX;
                }
                if (extendedY + extendedHeight > warpedBoard.height()) {
                    extendedHeight = warpedBoard.height() - extendedY;
                }

                // Extract
                Rect squareRect = new Rect(extendedX, extendedY, extendedWidth, extendedHeight);
                Mat squareImg = new Mat(warpedBoard, squareRect);

                // Save
                String chessNotation = (char)('A' + col) + "" + (8 - row);
                String filename = String.format(baseFileName + "square%02d_%s.jpg", squareNumber, chessNotation);
                String filepath = Paths.get(outputDir, filename).toString();

                Imgcodecs.imwrite(filepath, squareImg);
                squareNumber++;
            }
        }
        System.out.println("✓ Extracted with Sky Buffer. Check debug_warped_board_with_buffer.jpg to see the extra space.");
    }

    private static boolean hasReasonableAngles(Point[] ordered) {
    // ordered: [TL, TR, BR, BL]
    double[] angles = new double[4];
    
    for (int i = 0; i < 4; i++) {
        Point prev = ordered[(i + 3) % 4];
        Point curr = ordered[i];
        Point next = ordered[(i + 1) % 4];
        
        // Calculate angle at curr using dot product
        double v1x = prev.x - curr.x;
        double v1y = prev.y - curr.y;
        double v2x = next.x - curr.x;
        double v2y = next.y - curr.y;
        
        double dot = v1x * v2x + v1y * v2y;
        double mag1 = Math.sqrt(v1x * v1x + v1y * v1y);
        double mag2 = Math.sqrt(v2x * v2x + v2y * v2y);
        
        double cosAngle = dot / (mag1 * mag2);
        // Clamp to [-1, 1] to handle floating point errors
        cosAngle = Math.max(-1.0, Math.min(1.0, cosAngle));
        angles[i] = Math.toDegrees(Math.acos(cosAngle));
    }
    
    System.out.println("Corner angles: " + Arrays.toString(angles));
    
    // All angles should be roughly between 45 and 135 degrees
    // (accounting for perspective distortion)
    for (double angle : angles) {
        if (angle < 40 || angle > 140) {
            System.out.println("Failed: Angle " + angle + " is too extreme");
            return false;
        }
    }
    
    return true;
    }
}