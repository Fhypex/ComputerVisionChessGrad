package com.chessgame;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.nio.file.Paths;

public class ChessSquareExtractor {

    /**
     * Extracts individual square images from the chessboard.
     * Uses OUTER corners to warp, then crops based on INNER grid to avoid losing pieces.
     * Each square is cropped with extra vertical height to capture tall pieces.
     * Adjusts for perspective: bottom rows get more offset, top rows get less.
     * * @param src Original source image
     * @param outerCorners The 4 corners of the outer board (44cm)
     * @param innerCorners The 4 corners of the playable 8x8 board (40cm)
     * @param outputDir Directory to save square images
     */
    public static void extractSquareImages(Mat src, Point[] outerCorners, Point[] innerCorners, String outputDir , String baseFileName) {
        int warpedWidth = BoardDetector.VIRTUAL_RESOLUTION;

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

        if (BoardDetector.DEBUG_MODE) {
            Imgcodecs.imwrite("debug_warped_board_with_buffer.jpg", warpedBoard);
        }

        // Calculate geometry
        double outerSize = BoardDetector.OUTER_BOARD_SIZE_CM;
        double innerSize = BoardDetector.INNER_BOARD_SIZE_CM;
        double borderRatio = BoardDetector.BORDER_WIDTH_CM / outerSize;

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
}