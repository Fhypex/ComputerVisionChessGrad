package com.chessgame;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

public class ChessMoveLogic {

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
     * NEW: Detects changes between two already-warped board images.
     */
    public static List<String> detectSquareChanges(Mat warpedBefore, Mat warpedAfter) {
        List<String> changes = new ArrayList<>();

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

                // Inset slightly to focus on the center of the square
                int inset = 5;
                Rect strictRect = new Rect(
                        (int)baseX + inset,
                        (int)baseY + inset,
                        (int)squareSize - (2 * inset),
                        (int)squareSize - (2 * inset)
                );

                // Safety check
                if (strictRect.x < 0 || strictRect.y < 0 ||
                        strictRect.x + strictRect.width > warpedBefore.width() ||
                        strictRect.y + strictRect.height > warpedBefore.height()) continue;

                Mat roiBefore = new Mat(warpedBefore, strictRect);
                Mat roiAfter = new Mat(warpedAfter, strictRect);

                // --- STEP 1: Intensity Difference (The original check) ---
                Mat grayBefore = new Mat();
                Mat grayAfter = new Mat();
                Imgproc.cvtColor(roiBefore, grayBefore, Imgproc.COLOR_BGR2GRAY);
                Imgproc.cvtColor(roiAfter, grayAfter, Imgproc.COLOR_BGR2GRAY);

                // Blur to remove camera noise (crucial for Canny stability)
                Imgproc.GaussianBlur(grayBefore, grayBefore, new Size(5, 5), 0);
                Imgproc.GaussianBlur(grayAfter, grayAfter, new Size(5, 5), 0);

                Mat diffIntensity = new Mat();
                Core.absdiff(grayBefore, grayAfter, diffIntensity);
                double intensityScore = Core.mean(diffIntensity).val[0];

                // --- STEP 2: Structural Edge Difference (The Shadow Killer) ---
                // Shadows do not create hard edges. Pieces do.
                Mat edgesBefore = new Mat();
                Mat edgesAfter = new Mat();

                // Thresholds 30/100 are standard for detecting piece outlines without picking up wood grain too much
                Imgproc.Canny(grayBefore, edgesBefore, 30, 100);
                Imgproc.Canny(grayAfter, edgesAfter, 30, 100);

                Mat diffEdges = new Mat();
                Core.absdiff(edgesBefore, edgesAfter, diffEdges);
                double edgeScore = Core.mean(diffEdges).val[0];

                // --- STEP 3: Combined Decision Logic ---

                // LOGIC EXPLAINED:
                // 1. Intensity must change (Base requirement).
                // 2. BUT, Structure (Edges) must ALSO change.
                // A shadow will cause high Intensity Score (~30-50) but very low Edge Score (~0-1).
                // A moved piece will cause high Intensity Score AND high Edge Score.

                double INTENSITY_THRESH = 15.0; // Was 15.0
                double EDGE_THRESH = 2.0;       // Requires ~3% of pixels to be different edges

                boolean isChanged = false;

                if (intensityScore > INTENSITY_THRESH) {
                    // Potential change detected, verify if it's just a shadow
                    if (edgeScore > EDGE_THRESH) {
                        isChanged = true;
                    } else {
                        // This is likely a shadow (Color changed, but structure didn't)
                        // Draw Blue debug rect for "Shadow Detected / Ignored"
                        Imgproc.rectangle(diffViz, strictRect, new Scalar(255, 0, 0), 1);
                    }
                }

                if (isChanged) {
                    String chessNotation = (char)('A' + col) + "" + (8 - row);
                    changes.add(chessNotation);
                    // Red for confirmed change
                    Imgproc.rectangle(diffViz, strictRect, new Scalar(0, 0, 255), 2);
                } else {
                    // Green for no change
                    // Imgproc.rectangle(diffViz, strictRect, new Scalar(0, 255, 0, 50), 1);
                }

                // Clean up Mats to prevent memory leaks in loop
                roiBefore.release(); roiAfter.release();
                grayBefore.release(); grayAfter.release();
                diffIntensity.release();
                edgesBefore.release(); edgesAfter.release(); diffEdges.release();
            }
        }

        if (BoardDetector.DEBUG_MODE) {
            Imgcodecs.imwrite("output/debug_change_heatmap.jpg", diffViz);
        }

        return changes;
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
}