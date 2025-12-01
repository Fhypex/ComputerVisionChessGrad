package com.chessgame;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import nu.pattern.OpenCV;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ChessMoveDetector {

    private static final double OUTER_BOARD_SIZE_CM = 44.0;
    private static final double INNER_BOARD_SIZE_CM = 40.0;
    private static final double BORDER_WIDTH_CM = (OUTER_BOARD_SIZE_CM - INNER_BOARD_SIZE_CM) / 2.0;
    private static final int VIRTUAL_RESOLUTION = 800;
    
    // Threshold for detecting significant changes between squares
    private static final double CHANGE_THRESHOLD = 15.0; // Adjust based on testing

    static {
        OpenCV.loadLocally();
    }

    public static void main(String[] args) {
        // Example usage
        String image1Path = Paths.get("src", "main", "resources", "tests", "board_before.jpg").toString();
        String image2Path = Paths.get("src", "main", "resources", "tests", "board_after.jpg").toString();
        
        detectMoves(image1Path, image2Path);
    }

    /**
     * Detects moves between two chessboard images
     * @param beforeImagePath Path to the first image
     * @param afterImagePath Path to the second image
     */
    public static void detectMoves(String beforeImagePath, String afterImagePath) {
        System.out.println("=== Chess Move Detection ===");
        System.out.println("Before: " + beforeImagePath);
        System.out.println("After: " + afterImagePath);
        
        // Load both images
        Mat beforeImg = Imgcodecs.imread(beforeImagePath);
        Mat afterImg = Imgcodecs.imread(afterImagePath);
        
        if (beforeImg.empty() || afterImg.empty()) {
            System.err.println("ERROR: Could not load one or both images");
            return;
        }
        
        System.out.println("\n--- Processing BEFORE image ---");
        BoardData beforeBoard = extractBoardData(beforeImg);
        
        if (beforeBoard == null) {
            System.err.println("ERROR: Could not detect board in BEFORE image");
            return;
        }
        
        System.out.println("\n--- Processing AFTER image ---");
        BoardData afterBoard = extractBoardData(afterImg);
        
        if (afterBoard == null) {
            System.err.println("ERROR: Could not detect board in AFTER image");
            return;
        }
        
        // Compare the two boards
        System.out.println("\n=== Analyzing Changes ===");
        List<String> changes = compareBoards(beforeBoard, afterBoard);
        
        if (changes.isEmpty()) {
            System.out.println("No significant changes detected.");
        } else {
            System.out.println("\nDetected changes:");
            for (String change : changes) {
                System.out.println("  " + change);
            }
            
            // Try to infer the move
            inferMove(changes);
        }
    }

    /**
     * Extracts board data including warped image and square images
     */
    private static BoardData extractBoardData(Mat src) {
        // Detect board corners (reusing logic from ChessBoardDebug)
        Point[] outerCorners = findBoardCorners(src);
        
        if (outerCorners == null) {
            return null;
        }
        
        System.out.println("✓ Board detected successfully");
        
        // Calculate inner corners
        Point[] innerCorners = calculateInnerCorners(outerCorners);
        
        // Warp the board to standard perspective
        Mat warpedBoard = warpBoard(src, outerCorners);
        
        // Extract all 64 squares
        Mat[] squares = extractAllSquares(warpedBoard);
        
        return new BoardData(warpedBoard, squares, outerCorners, innerCorners);
    }

    /**
     * Warps the board to a standard top-down view
     */
    private static Mat warpBoard(Mat src, Point[] outerCorners) {
        int warpedWidth = VIRTUAL_RESOLUTION;
        int skyBuffer = (int)(warpedWidth * 0.5);
        int warpedHeight = warpedWidth + skyBuffer;
        
        Point[] dstPoints = new Point[]{
            new Point(0, skyBuffer),
            new Point(warpedWidth, skyBuffer),
            new Point(warpedWidth, warpedWidth + skyBuffer),
            new Point(0, warpedWidth + skyBuffer)
        };
        
        Mat srcMat = new MatOfPoint2f(outerCorners);
        Mat dstMat = new MatOfPoint2f(dstPoints);
        Mat perspectiveMatrix = Imgproc.getPerspectiveTransform(srcMat, dstMat);
        
        Mat warped = new Mat();
        Imgproc.warpPerspective(src, warped, perspectiveMatrix, new Size(warpedWidth, warpedHeight));
        
        return warped;
    }

    /**
     * Extracts all 64 squares from a warped board
     * Returns array indexed 0-63 (A8, B8, ..., G1, H1)
     */
    private static Mat[] extractAllSquares(Mat warpedBoard) {
        Mat[] squares = new Mat[64];
        
        int warpedWidth = VIRTUAL_RESOLUTION;
        int skyBuffer = (int)(warpedWidth * 0.5);
        
        double outerSize = OUTER_BOARD_SIZE_CM;
        double innerSize = INNER_BOARD_SIZE_CM;
        double borderRatio = BORDER_WIDTH_CM / outerSize;
        
        double innerStartPixel = warpedWidth * borderRatio;
        double innerSizePixel = warpedWidth * (innerSize / outerSize);
        double squareSize = innerSizePixel / 8.0;
        
        int index = 0;
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                double baseX = innerStartPixel + (col * squareSize);
                double baseY = skyBuffer + innerStartPixel + (row * squareSize);
                
                int x = (int)baseX;
                int y = (int)baseY;
                int width = (int)squareSize;
                int height = (int)squareSize;
                
                // Boundary check
                if (x + width > warpedBoard.width()) width = warpedBoard.width() - x;
                if (y + height > warpedBoard.height()) height = warpedBoard.height() - y;
                
                Rect squareRect = new Rect(x, y, width, height);
                squares[index] = new Mat(warpedBoard, squareRect).clone();
                index++;
            }
        }
        
        return squares;
    }

    /**
     * Compares two boards and returns list of changed squares
     */
    private static List<String> compareBoards(BoardData before, BoardData after) {
        List<String> changes = new ArrayList<>();
        
        for (int i = 0; i < 64; i++) {
            double difference = calculateSquareDifference(before.squares[i], after.squares[i]);
            
            int row = i / 8;
            int col = i % 8;
            String squareName = "" + (char)('a' + col) + (8 - row);
            
            if (difference > CHANGE_THRESHOLD) {
                changes.add(squareName + " (diff: " + String.format("%.2f", difference) + ")");
                System.out.println("Change detected at " + squareName + ": " + String.format("%.2f", difference));
            }
        }
        
        return changes;
    }

    /**
     * Calculates the difference between two square images
     * Returns a value representing how different they are
     */
    private static double calculateSquareDifference(Mat square1, Mat square2) {
        // Convert to grayscale
        Mat gray1 = new Mat();
        Mat gray2 = new Mat();
        Imgproc.cvtColor(square1, gray1, Imgproc.COLOR_BGR2GRAY);
        Imgproc.cvtColor(square2, gray2, Imgproc.COLOR_BGR2GRAY);
        
        // Calculate absolute difference
        Mat diff = new Mat();
        Core.absdiff(gray1, gray2, diff);
        
        // Calculate mean difference
        Scalar meanDiff = Core.mean(diff);
        
        return meanDiff.val[0];
    }

    /**
     * Tries to infer the chess move from the list of changes
     */
    private static void inferMove(List<String> changes) {
        System.out.println("\n=== Move Inference ===");
        
        if (changes.size() == 2) {
            // Most common case: piece moved from one square to another
            String from = changes.get(0).split(" ")[0];
            String to = changes.get(1).split(" ")[0];
            System.out.println("Likely move: " + from + " → " + to);
        } else if (changes.size() == 4) {
            // Possible castling (king and rook both move)
            System.out.println("Possible castling detected (4 squares changed)");
            System.out.println("Changed squares: " + changes);
        } else if (changes.size() == 3) {
            // Possible en passant (pawn captures, captured pawn removed)
            System.out.println("Possible en passant detected (3 squares changed)");
            System.out.println("Changed squares: " + changes);
        } else {
            System.out.println("Complex change detected (" + changes.size() + " squares)");
            System.out.println("Changed squares: " + changes);
        }
    }

    // ==================== Helper Methods from ChessBoardDebug ====================
    
    private static Point[] findBoardCorners(Mat src) {
        // Simplified version - you can copy the full implementation from ChessBoardDebug
        // For now, returning a basic implementation
        
        Mat gray = new Mat();
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);
        
        Mat blurred = new Mat();
        Imgproc.GaussianBlur(gray, blurred, new Size(5, 5), 0);
        
        Mat edges = new Mat();
        Imgproc.Canny(blurred, edges, 50, 150);
        
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        
        double imageArea = src.width() * src.height();
        
        // Find largest quadrilateral
        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            
            if (area < imageArea * 0.20 || area > imageArea * 0.85) {
                continue;
            }
            
            MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
            double peri = Imgproc.arcLength(contour2f, true);
            MatOfPoint2f approx = new MatOfPoint2f();
            Imgproc.approxPolyDP(contour2f, approx, 0.02 * peri, true);
            
            if (approx.total() == 4) {
                Point[] pts = approx.toArray();
                return orderPoints(pts);
            }
        }
        
        return null;
    }

    private static Point[] orderPoints(Point[] pts) {
        Point[] result = new Point[4];
        List<Point> points = new ArrayList<>();
        for (Point p : pts) points.add(p);
        
        points.sort((p1, p2) -> Double.compare(p1.y, p2.y));
        
        List<Point> top = new ArrayList<>(points.subList(0, 2));
        top.sort((p1, p2) -> Double.compare(p1.x, p2.x));
        result[0] = top.get(0); // TL
        result[1] = top.get(1); // TR
        
        List<Point> bottom = new ArrayList<>(points.subList(2, 4));
        bottom.sort((p1, p2) -> Double.compare(p1.x, p2.x));
        result[3] = bottom.get(0); // BL
        result[2] = bottom.get(1); // BR
        
        return result;
    }

    private static Point[] calculateInnerCorners(Point[] outerCorners) {
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

    // ==================== Data Classes ====================
    
    /**
     * Stores board data for comparison
     */
    private static class BoardData {
        Mat warpedBoard;
        Mat[] squares; // 64 squares indexed 0-63
        Point[] outerCorners;
        Point[] innerCorners;
        
        BoardData(Mat warpedBoard, Mat[] squares, Point[] outerCorners, Point[] innerCorners) {
            this.warpedBoard = warpedBoard;
            this.squares = squares;
            this.outerCorners = outerCorners;
            this.innerCorners = innerCorners;
        }
    }
}