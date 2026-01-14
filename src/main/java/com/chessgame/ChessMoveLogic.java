package com.chessgame;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ChessMoveLogic {

    private static float lightLevel;

    /**
     * NEW: Standardized warping to ensure Before/After match pixel-for-pixel.
     * Uses the exact same geometry logic as extractSquareImages (Sky Buffer).
     */
    public static Mat warpBoardStandardized(Mat src, Point[] outerCorners) {
        int warpedWidth = BoardDetector.VIRTUAL_RESOLUTION;
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
     * Detects changes between two already-warped board images.
     */
    public static List<String> detectSquareChanges(Mat warpedBefore, Mat warpedAfter) {
    List<String> changes = new ArrayList<>();
    // NEW: Map to store change scores for parallax filtering
    Map<String, Double> scoreMap = new HashMap<>();

    // Geometry calculation
    double outerSize = BoardDetector.OUTER_BOARD_SIZE_CM;
    double innerSize = BoardDetector.INNER_BOARD_SIZE_CM;
    double borderRatio = BoardDetector.BORDER_WIDTH_CM / outerSize;

    double innerStartPixel = BoardDetector.VIRTUAL_RESOLUTION * borderRatio;
    double innerSizePixel = BoardDetector.VIRTUAL_RESOLUTION * (innerSize / outerSize);
    double squareSize = innerSizePixel / 8.0;

    int skyBuffer = (int)(BoardDetector.VIRTUAL_RESOLUTION * 0.5);

    Mat diffViz = warpedAfter.clone();

    for (int row = 0; row < 8; row++) {
        for (int col = 0; col < 8; col++) {

            double baseX = innerStartPixel + (col * squareSize);
            double baseY = skyBuffer + innerStartPixel + (row * squareSize);

            int inset = 5;
            Rect strictRect = new Rect(
                    (int)baseX + inset,
                    (int)baseY + inset,
                    (int)squareSize - (2 * inset),
                    (int)squareSize - (2 * inset)
            );

            if (strictRect.x < 0 || strictRect.y < 0 ||
                    strictRect.x + strictRect.width > warpedBefore.width() ||
                    strictRect.y + strictRect.height > warpedBefore.height()) continue;

            Mat roiBefore = new Mat(warpedBefore, strictRect);
            Mat roiAfter = new Mat(warpedAfter, strictRect);

            // --- STEP 1: Intensity ---
            Mat grayBefore = new Mat();
            Mat grayAfter = new Mat();
            Imgproc.cvtColor(roiBefore, grayBefore, Imgproc.COLOR_BGR2GRAY);
            Imgproc.cvtColor(roiAfter, grayAfter, Imgproc.COLOR_BGR2GRAY);

            Imgproc.GaussianBlur(grayBefore, grayBefore, new Size(5, 5), 0);
            Imgproc.GaussianBlur(grayAfter, grayAfter, new Size(5, 5), 0);

            Mat diffIntensity = new Mat();
            Core.absdiff(grayBefore, grayAfter, diffIntensity);
            double intensityScore = Core.mean(diffIntensity).val[0];

            // --- STEP 2: Edges ---
            Mat edgesBefore = new Mat();
            Mat edgesAfter = new Mat();

            Imgproc.Canny(grayBefore, edgesBefore, 40, 100);
            Imgproc.Canny(grayAfter, edgesAfter, 40, 100);

            Mat diffEdges = new Mat();
            Core.absdiff(edgesBefore, edgesAfter, diffEdges);
            double edgeScore = Core.mean(diffEdges).val[0];

            // --- STEP 3: Logic ---
            double INTENSITY_THRESH;
            double EDGE_THRESH;
            double dynamicBlack = 2.0f;
            double dynamicWhite = 1.0f;
             if((row + col) % 2 != 0) {                
                    INTENSITY_THRESH = 21.0 + (dynamicBlack * lightLevel);
                    EDGE_THRESH = 5.0;
                } else {
                    if((row == 6 && col == 4) || (row == 4 && col == 4)){
                        System.out.println("DEBUG: intensity: " + intensityScore + " - edge: " + edgeScore);
                    }
                    INTENSITY_THRESH = 12.0 + (dynamicWhite * lightLevel);
                    EDGE_THRESH = 4.0;
            }                   

            boolean isChanged = false;

            if (intensityScore > INTENSITY_THRESH) {
                if (edgeScore > EDGE_THRESH) {
                    isChanged = true;
                } else {
                    Imgproc.rectangle(diffViz, strictRect, new Scalar(255, 0, 0), 1);
                }
            }

            if (isChanged) {
                String chessNotation = (char)('A' + col) + "" + (8 - row);
                changes.add(chessNotation);
                
                // NEW: Store the combined score to distinguish real moves from parallax shadows
                // We weight edgeScore higher as it is the primary differentiator for solid pieces
                scoreMap.put(chessNotation, intensityScore + (edgeScore * 2.0));

                Imgproc.rectangle(diffViz, strictRect, new Scalar(0, 0, 255), 2);
            }

            roiBefore.release(); roiAfter.release();
            grayBefore.release(); grayAfter.release();
            diffIntensity.release();
            edgesBefore.release(); edgesAfter.release(); diffEdges.release();
        }
    }

    if (BoardDetector.DEBUG_MODE) {
        Imgcodecs.imwrite("output/debug_change_heatmap.jpg", diffViz);
    }

    // NEW: Helper call to filter parallax
    return resolveParallaxOrEnPassant(changes, scoreMap);
}

// --- NEW HELPER FUNCTIONS ---

private static List<String> resolveParallaxOrEnPassant(List<String> changes, Map<String, Double> scores) {
    // Only apply logic if we detected exactly 3 changes
    if (changes.size() != 3) {
        return changes;
    }

    // Check if this is a valid En Passant trajectory
    if (isEnPassantPattern(changes)) {
        return changes; // It's a valid 3-square move, return as is
    }

    // If not En Passant, it's likely Parallax (Ghost square).
    // Sort by score descending (Highest change confidence first)
    changes.sort((a, b) -> Double.compare(scores.get(b), scores.get(a)));

    // Return the top 2 squares (The real From/To squares)
    return new ArrayList<>(changes.subList(0, 2));
}

private static boolean isEnPassantPattern(List<String> squares) {
    Set<Integer> files = new HashSet<>();
    Set<Integer> ranks = new HashSet<>();

    for (String s : squares) {
        files.add(s.charAt(0) - 'A');
        ranks.add(Integer.parseInt(s.substring(1)));
    }

    // A valid En Passant involves exactly 2 Files and 2 Ranks.
    // Example: White Pawn E5 captures Black Pawn D5 -> lands on D6.
    // Squares: E5, D5, D6.
    // Files: E, D (Size 2). Ranks: 5, 6 (Size 2).
    // Parallax usually occurs on 1 File (e.g., Queen D1->D8 parallaxing D7) or spans 3 ranks.
    return files.size() == 2 && ranks.size() == 2;
}

    public static Mat getSquareForModel(Mat warpedBoard, int logicalRank, int logicalFile) {
        // 1. Convert Logical Rank (0=White side) to Visual Row (0=Top)
        int row = 7 - logicalRank; 
        int col = logicalFile;

        // 2. Geometry Constants (Must match your training extraction exactly)
        int warpedWidth = warpedBoard.width(); 
        int skyBuffer = (int)(warpedWidth * 0.5); 
        
        double outerSize = BoardDetector.OUTER_BOARD_SIZE_CM;
        double innerSize = BoardDetector.INNER_BOARD_SIZE_CM;
        double borderRatio = BoardDetector.BORDER_WIDTH_CM / outerSize;
        
        double innerStartPixel = warpedWidth * borderRatio;
        double innerSizePixel = warpedWidth * (innerSize / outerSize);
        double squareSize = innerSizePixel / 8.0;

        // 3. Sky Buffer Adjustments
        double baseX = innerStartPixel + (col * squareSize);
        double baseY = skyBuffer + innerStartPixel + (row * squareSize); 

        double baseExtraHeightRatio = 0.85;  
        double extraWidthRatio = 0.3;       
        int extraWidthPerSide = (int)(squareSize * extraWidthRatio / 2.0);

        double rowAdjustment;
        if (row == 0) rowAdjustment = baseExtraHeightRatio + 0.3; 
        else if (row < 2) rowAdjustment = baseExtraHeightRatio + 0.2;
        else if(row < 4) rowAdjustment = baseExtraHeightRatio;
        else if(row < 6) rowAdjustment = baseExtraHeightRatio - 0.2;
        else rowAdjustment = baseExtraHeightRatio - 0.3;

        int extraHeight = (int)(squareSize * rowAdjustment);

        // 4. Calculate Crop
        int extendedX = (int)Math.max(0, baseX - extraWidthPerSide);
        int extendedY = (int)Math.max(0, baseY - extraHeight);
        int extendedWidth = (int)squareSize + (2 * extraWidthPerSide);
        int extendedHeight = (int)squareSize + extraHeight;
        
        if (extendedX + extendedWidth > warpedBoard.width()) extendedWidth = warpedBoard.width() - extendedX;
        if (extendedY + extendedHeight > warpedBoard.height()) extendedHeight = warpedBoard.height() - extendedY;

        return new Mat(warpedBoard, new Rect(extendedX, extendedY, extendedWidth, extendedHeight)).clone();
    }

    /**
     * Prepares an OpenCV Matrix for the DeepLearning4J model.
     */
    public static float[] preprocessImageForModel(Mat src) {
        // Resize to model input (Assuming 224x224 based on standard transfer learning)
        Mat resized = new Mat();
        Imgproc.resize(src, resized, new Size(224, 224));

        int channels = 3;
        float[] data = new float[224 * 224 * channels];
        byte[] byteData = new byte[(int) (resized.total() * resized.channels())];
        resized.get(0, 0, byteData);

        for (int i = 0; i < byteData.length; i++) {
            data[i] = (byteData[i] & 0xFF) / 255.0f; // Normalize 0-1
        }
        return data;
    }


    private static String getNotation(int row, int col) {
        char file = (char) ('a' + col);
        int rank = 8 - row;
        return "" + file + rank;
    }

    public static void setLightLevel(String currentLightMode) {
        if(currentLightMode == "low") {
            lightLevel = 3.0f;
        }
        if(currentLightMode == "mid") {
            lightLevel = 2.0f;
        }
        if(currentLightMode == "high") {
            lightLevel = 1.0f;
        }
    }
}