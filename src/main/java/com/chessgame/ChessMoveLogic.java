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

                double INTENSITY_THRESH = 25.0; // Was 15.0
                double EDGE_THRESH = 3.0;       // Requires ~3% of pixels to be different edges

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
}