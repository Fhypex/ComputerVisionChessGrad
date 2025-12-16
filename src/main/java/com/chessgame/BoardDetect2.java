package com.chessgame;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import nu.pattern.OpenCV;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BoardDetect2 {

    // Physical board dimensions (Unchanged)
    public static final double OUTER_BOARD_SIZE_CM = 44.0;
    public static final double INNER_BOARD_SIZE_CM = 40.0;
    public static final double BORDER_WIDTH_CM = (OUTER_BOARD_SIZE_CM - INNER_BOARD_SIZE_CM) / 2.0;

    public static final int VIRTUAL_RESOLUTION = 800;
    public static final boolean DEBUG_MODE = true;

    static {
        OpenCV.loadLocally();
    }

    public static void main(String[] args) {
        String nameOfFile = "before.jpg"; // Ensure this matches your file
        String inputImagePath = Paths.get("src", "main", "resources", "tests", nameOfFile).toString();
        String outputImagePath = Paths.get("output", nameOfFile).toString();
        String debugImagePath = Paths.get("output", "debug_detection.jpg").toString();
        String squaresOutputDir = Paths.get("output", "squares").toString();

        java.io.File f = new java.io.File(inputImagePath);
        String fileNameWithExt = f.getName();
        String baseFileName = fileNameWithExt.replaceFirst("[.][^.]+$", "");

        Mat src = Imgcodecs.imread(inputImagePath);
        if (src.empty()) {
            System.err.println("Cannot read image: " + inputImagePath);
            return;
        }

        System.out.println("Image loaded. Resolution: " + src.width() + "x" + src.height());
        new java.io.File(squaresOutputDir).mkdirs();

        Mat debugImg = src.clone();

        // 1. Detect Outer Corners using Lines (Ignores Rooks)
        Point[] outerCorners = findBoardCorners(src, debugImg);

        Imgcodecs.imwrite(debugImagePath, debugImg);
        System.out.println("Debug image saved to " + debugImagePath);

        if (outerCorners == null) {
            System.err.println("FAILED: Could not detect board lines.");
            return;
        }

        System.out.println("Success! Outer Board Detected.");
        drawCorners(src, outerCorners, new Scalar(0, 255, 0), "Outer");

        // 2. Calculate Inner Corners (Standard Logic)
        Point[] innerCorners = calculateInnerCorners(outerCorners);
        drawCorners(src, innerCorners, new Scalar(255, 0, 0), "Inner");

        // 3. Grid Centers
        List<Point> squareCenters = calculateGridCenters(innerCorners);

        // 4. Draw Centers
        for (int i = 0; i < squareCenters.size(); i++) {
            Imgproc.circle(src, squareCenters.get(i), 5, new Scalar(0, 0, 255), -1);
        }

        Imgcodecs.imwrite(outputImagePath, src);

        // 5. Extract Squares (Logic Preserved)
        System.out.println("\n=== Extracting individual squares ===");
        extractSquareImages(Imgcodecs.imread(inputImagePath), outerCorners, innerCorners, squaresOutputDir, baseFileName);
    }

    /**
     * UPDATED: Uses HoughLinesP to find the board edges.
     * This ignores "bumpy" shapes (pieces) and focuses on long straight lines.
     */
    public static Point[] findBoardCorners(Mat originalSrc, Mat debugImg) {
        // 1. Scale down for reliable detection
        double processingWidth = 800.0;
        double scale = 1.0;
        Mat src = new Mat();

        if (originalSrc.width() > processingWidth) {
            scale = processingWidth / originalSrc.width();
            Imgproc.resize(originalSrc, src, new Size(), scale, scale, Imgproc.INTER_AREA);
        } else {
            originalSrc.copyTo(src);
        }

        // 2. Pre-processing
        Mat gray = new Mat();
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);
        
        // Use Canny Edge Detection
        Mat edges = new Mat();
        // Blur slightly to remove wood grain noise
        Imgproc.GaussianBlur(gray, gray, new Size(5, 5), 0);
        // Canny thresholds: 50/150 is standard, adjusted for board contrast
        Imgproc.Canny(gray, edges, 30, 120);

        // 3. Hough Lines Probabilistic
        Mat lines = new Mat();
        // threshold: 80 votes needed
        // minLineLength: 20% of image width. KEY FACTOR: Rooks are smaller than this, so they are ignored.
        // maxLineGap: 10% of width. Bridges gaps if a piece blocks the line.
        int minLength = (int)(src.width() * 0.20);
        int maxGap = (int)(src.width() * 0.10);
        
        Imgproc.HoughLinesP(edges, lines, 1, Math.PI / 180, 50, minLength, maxGap);

        System.out.println("Detected " + lines.rows() + " strong lines.");

        // 4. Filter Lines into Horizontal and Vertical
        List<double[]> horizontals = new ArrayList<>();
        List<double[]> verticals = new ArrayList<>();

        for (int i = 0; i < lines.rows(); i++) {
            double[] l = lines.get(i, 0);
            double x1 = l[0], y1 = l[1], x2 = l[2], y2 = l[3];
            double angle = Math.atan2(y2 - y1, x2 - x1) * 180.0 / Math.PI;
            
            // Draw all candidates faintly in gray
            if (DEBUG_MODE) Imgproc.line(debugImg, new Point(x1/scale, y1/scale), new Point(x2/scale, y2/scale), new Scalar(200, 200, 200), 1);

            if (Math.abs(angle) < 20 || Math.abs(angle) > 160) {
                horizontals.add(l);
            } else if (Math.abs(angle) > 70 && Math.abs(angle) < 110) {
                verticals.add(l);
            }
        }

        if (horizontals.isEmpty() || verticals.isEmpty()) return null;

        // 5. Find the Extremes (Top, Bottom, Left, Right)
        // We look for the lines closest to the center that are still "outer"
        // Or simply the outermost lines that aren't the image edge.
        
        double[] top = null, bottom = null, left = null, right = null;
        double minY = Double.MAX_VALUE;
        double maxY = Double.MIN_VALUE;
        double minX = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;

        int margin = 10; // Ignore lines touching image border

        for (double[] h : horizontals) {
            double avgY = (h[1] + h[3]) / 2.0;
            if (avgY < margin || avgY > src.height() - margin) continue;

            if (avgY < minY) { minY = avgY; top = h; }
            if (avgY > maxY) { maxY = avgY; bottom = h; }
        }

        for (double[] v : verticals) {
            double avgX = (v[0] + v[2]) / 2.0;
            if (avgX < margin || avgX > src.width() - margin) continue;

            if (avgX < minX) { minX = avgX; left = v; }
            if (avgX > maxX) { maxX = avgX; right = v; }
        }

        if (top == null || bottom == null || left == null || right == null) {
            System.err.println("Could not find 4 distinct sides.");
            return null;
        }

        // Draw winning lines on debug
        if (DEBUG_MODE) {
            drawLine(debugImg, top, new Scalar(0, 255, 0), scale);
            drawLine(debugImg, bottom, new Scalar(0, 255, 0), scale);
            drawLine(debugImg, left, new Scalar(0, 0, 255), scale);
            drawLine(debugImg, right, new Scalar(0, 0, 255), scale);
        }

        // 6. Compute Intersections
        Point pTL = computeIntersection(top, left);
        Point pTR = computeIntersection(top, right);
        Point pBR = computeIntersection(bottom, right);
        Point pBL = computeIntersection(bottom, left);

        // Scale back up
        Point[] corners = new Point[]{
            new Point(pTL.x / scale, pTL.y / scale),
            new Point(pTR.x / scale, pTR.y / scale),
            new Point(pBR.x / scale, pBR.y / scale),
            new Point(pBL.x / scale, pBL.y / scale)
        };

        return corners;
    }

    // Helper to find where two lines intersect
    private static Point computeIntersection(double[] l1, double[] l2) {
        double x1 = l1[0], y1 = l1[1], x2 = l1[2], y2 = l1[3];
        double x3 = l2[0], y3 = l2[1], x4 = l2[2], y4 = l2[3];

        double d = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);
        if (d == 0) return new Point(0,0); // Parallel

        double x = ((x1 * y2 - y1 * x2) * (x3 - x4) - (x1 - x2) * (x3 * y4 - y3 * x4)) / d;
        double y = ((x1 * y2 - y1 * x2) * (y3 - y4) - (y1 - y2) * (x3 * y4 - y3 * x4)) / d;

        return new Point(x, y);
    }

    private static void drawLine(Mat img, double[] l, Scalar color, double scale) {
        Imgproc.line(img, 
            new Point(l[0]/scale, l[1]/scale), 
            new Point(l[2]/scale, l[3]/scale), color, 3);
    }
    
    // --- EXISTING LOGIC BELOW (Unchanged functionality) ---

    public static Point[] calculateInnerCorners(Point[] outerCorners) {
        Point tl = outerCorners[0];
        Point tr = outerCorners[1];
        Point br = outerCorners[2];
        Point bl = outerCorners[3];

        double shrinkRatio = INNER_BOARD_SIZE_CM / OUTER_BOARD_SIZE_CM;
        double baseOffsetRatio = (1.0 - shrinkRatio) / 2.0;

        double topRatio = baseOffsetRatio * 0.84;
        double bottomRatio = baseOffsetRatio * 1.16;
        double sideRatio = baseOffsetRatio;

        Point[] innerCorners = new Point[4];

        innerCorners[0] = new Point(
            tl.x + sideRatio * (tr.x - tl.x) + topRatio * (bl.x - tl.x),
            tl.y + sideRatio * (tr.y - tl.y) + topRatio * (bl.y - tl.y)
        );
        innerCorners[1] = new Point(
            tr.x - sideRatio * (tr.x - tl.x) + topRatio * (br.x - tr.x),
            tr.y - sideRatio * (tr.y - tl.y) + topRatio * (br.y - tr.y)
        );
        innerCorners[2] = new Point(
            br.x - sideRatio * (br.x - bl.x) - bottomRatio * (br.x - tr.x),
            br.y - sideRatio * (br.y - bl.y) - bottomRatio * (br.y - tr.y)
        );
        innerCorners[3] = new Point(
            bl.x + sideRatio * (br.x - bl.x) - bottomRatio * (bl.x - tl.x),
            bl.y + sideRatio * (br.y - bl.y) - bottomRatio * (bl.y - tl.y)
        );
        return innerCorners;
    }

    public static List<Point> calculateGridCenters(Point[] innerCorners) {
        List<Point> centers = new ArrayList<>();
        Point[] dstPoints = new Point[]{
            new Point(0, 0), new Point(VIRTUAL_RESOLUTION, 0),
            new Point(VIRTUAL_RESOLUTION, VIRTUAL_RESOLUTION), new Point(0, VIRTUAL_RESOLUTION)
        };
        Mat srcMat = new MatOfPoint2f(innerCorners);
        Mat dstMat = new MatOfPoint2f(dstPoints);
        Mat perspectiveMatrix = Imgproc.getPerspectiveTransform(srcMat, dstMat);
        Mat invertedMatrix = new Mat();
        Core.invert(perspectiveMatrix, invertedMatrix);
        double squareSize = VIRTUAL_RESOLUTION / 8.0;
        List<Point> virtualPoints = new ArrayList<>();
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                double x = (col * squareSize) + (squareSize / 2.0);
                double y = (row * squareSize) + (squareSize / 2.0);
                virtualPoints.add(new Point(x, y));
            }
        }
        MatOfPoint2f virtualMat = new MatOfPoint2f();
        virtualMat.fromList(virtualPoints);
        MatOfPoint2f originalMat = new MatOfPoint2f();
        Core.perspectiveTransform(virtualMat, originalMat, invertedMatrix);
        return originalMat.toList();
    }

    public static void drawCorners(Mat img, Point[] corners, Scalar color, String label) {
        for (int i = 0; i < corners.length; i++) {
            Imgproc.line(img, corners[i], corners[(i + 1) % 4], color, 3);
            Imgproc.circle(img, corners[i], 8, color, -1);
            Imgproc.putText(img, label + i, 
                new Point(corners[i].x + 10, corners[i].y - 10), 
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, color, 2);
        }
    }

    public static void extractSquareImages(Mat src, Point[] outerCorners, Point[] innerCorners, String outputDir , String baseFileName) {
        int warpedWidth = VIRTUAL_RESOLUTION;
        int skyBuffer = (int)(warpedWidth * 0.5); 
        int warpedHeight = warpedWidth + skyBuffer; 
        
        Point[] dstPoints = new Point[]{
            new Point(0, skyBuffer), new Point(warpedWidth, skyBuffer),
            new Point(warpedWidth, warpedWidth + skyBuffer), new Point(0, warpedWidth + skyBuffer)
        };

        Mat srcMat = new MatOfPoint2f(outerCorners);
        Mat dstMat = new MatOfPoint2f(dstPoints);
        Mat perspectiveMatrix = Imgproc.getPerspectiveTransform(srcMat, dstMat);
        Mat warpedBoard = new Mat();
        Imgproc.warpPerspective(src, warpedBoard, perspectiveMatrix, new Size(warpedWidth, warpedHeight));

        double outerSize = OUTER_BOARD_SIZE_CM;
        double innerSize = INNER_BOARD_SIZE_CM;
        double borderRatio = BORDER_WIDTH_CM / outerSize; 
        double innerStartPixel = warpedWidth * borderRatio;
        double innerSizePixel = warpedWidth * (innerSize / outerSize);
        double squareSize = innerSizePixel / 8.0;
        
        double baseExtraHeightRatio = 0.85;  
        double extraWidthRatio = 0.3;       
        int extraWidthPerSide = (int)(squareSize * extraWidthRatio / 2.0);
        int squareNumber = 1;
        
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                double baseX = innerStartPixel + (col * squareSize);
                double baseY = skyBuffer + innerStartPixel + (row * squareSize); 
                double rowAdjustment;
                if (row == 0) rowAdjustment = baseExtraHeightRatio + 0.3; 
                else if (row < 2) rowAdjustment = baseExtraHeightRatio + 0.2;
                else if(row < 4) rowAdjustment = baseExtraHeightRatio;
                else if(row < 6) rowAdjustment = baseExtraHeightRatio - 0.2;
                else rowAdjustment = baseExtraHeightRatio -0.3;
                
                int extraHeight = (int)(squareSize * rowAdjustment);
                int extendedX = (int)Math.max(0, baseX - extraWidthPerSide);
                int extendedY = (int)Math.max(0, baseY - extraHeight);
                int extendedWidth = (int)squareSize + (2 * extraWidthPerSide);
                int extendedHeight = (int)squareSize + extraHeight;
                
                if (extendedX + extendedWidth > warpedBoard.width()) extendedWidth = warpedBoard.width() - extendedX;
                if (extendedY + extendedHeight > warpedBoard.height()) extendedHeight = warpedBoard.height() - extendedY;

                Rect squareRect = new Rect(extendedX, extendedY, extendedWidth, extendedHeight);
                Mat squareImg = new Mat(warpedBoard, squareRect);
                String chessNotation = (char)('A' + col) + "" + (8 - row);
                String filename = String.format(baseFileName + "square%02d_%s.jpg", squareNumber, chessNotation);
                String filepath = Paths.get(outputDir, filename).toString();
                Imgcodecs.imwrite(filepath, squareImg);
                squareNumber++;
            }
        }
    }
}