package com.chessgame;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import nu.pattern.OpenCV;

// JavaFX Imports
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.io.ByteArrayInputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class ChessBoardDebug {

    // Physical board dimensions
    public static final double OUTER_BOARD_SIZE_CM = 44.0;
    public static final double INNER_BOARD_SIZE_CM = 40.0;
    public static final double BORDER_WIDTH_CM = (OUTER_BOARD_SIZE_CM - INNER_BOARD_SIZE_CM) / 2.0;
    
    public static final int VIRTUAL_RESOLUTION = 800;
    public static final boolean DEBUG_MODE = true;
    public static final boolean ALL_ATTEMPTS = true;

    static { 
        OpenCV.loadLocally(); 
    }

    public static void main(String[] args) {

        // --- JAVA FX INITIALIZATION ---
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException e) {
            // Platform already started, ignore
        }

        String nameOfFile = "drhamle16.jpg";

        String inputImagePath = Paths.get("src", "main", "resources", "tests","dr", nameOfFile).toString();
        String outputImagePath = Paths.get("output", nameOfFile).toString();
        String debugImagePath = Paths.get("output", "debug_all_attempts.jpg").toString();
        String squaresOutputDir = Paths.get("output", "squares").toString();
        String specialDebugDir = Paths.get("output", "special_debug").toString();
        
        java.io.File f = new java.io.File(inputImagePath);
        String fileNameWithExt = f.getName();
        String baseFileName = fileNameWithExt.replaceFirst("[.][^.]+$", "");
        
        Mat src = Imgcodecs.imread(inputImagePath);
        if (src.empty()) {
            System.err.println("Cannot read image: " + inputImagePath);
            Platform.exit();
            return;
        }

        System.out.println("Image loaded. Resolution: " + src.width() + "x" + src.height());

        new java.io.File(squaresOutputDir).mkdirs();
        if (ALL_ATTEMPTS) {
            new java.io.File(specialDebugDir).mkdirs();
        }

        Mat debugImg = src.clone();

        // 1. Detect the 4 corners using YOUR ORIGINAL LOGIC
        // Note: Removed 'specialDebugDir' argument to match your logic
        Point[] outerCorners = findBoardCorners(src, debugImg);

        Imgcodecs.imwrite(debugImagePath, debugImg);
        System.out.println("Debug image saved to " + debugImagePath);

        // =========================================================================
        // NEW: GUI MANUAL INTERVENTION / CONFIRMATION
        // =========================================================================
        
        System.out.println("Launching GUI for verification...");
        Image fxImage = mat2Image(src);
        
        // Open the Picker Window
        Point[] confirmedCorners = ManualBoardPicker.showInterface(fxImage, outerCorners);
        
        if (confirmedCorners != null) {
            System.out.println("GUI: Using confirmed corners.");
            outerCorners = confirmedCorners;
        } else {
            if (outerCorners == null) {
                System.err.println("FAILED: Detection failed and Manual selection was cancelled.");
                Platform.exit();
                return;
            }
            System.out.println("GUI: Cancelled. Proceeding with auto-detected corners.");
        }
        // =========================================================================        

        System.out.println("Success! Outer Board Detected.");
        
        drawCorners(src, outerCorners, new Scalar(0, 255, 0), "Outer");

        Point[] innerCorners = calculateInnerCorners(outerCorners);
        drawCorners(src, innerCorners, new Scalar(255, 0, 0), "Inner");

        List<Point> squareCenters = calculateGridCenters(innerCorners);

        for (int i = 0; i < squareCenters.size(); i++) {
            Point p = squareCenters.get(i);
            Imgproc.circle(src, p, 5, new Scalar(0, 0, 255), -1);
            int row = i / 8;
            int col = i % 8;
            String label = (char)('A' + col) + "" + (8 - row);
            Imgproc.putText(src, label, new Point(p.x - 10, p.y + 5), 
                           Imgproc.FONT_HERSHEY_SIMPLEX, 0.4, new Scalar(255, 255, 0), 1);
        }

        Imgcodecs.imwrite(outputImagePath, src);
        System.out.println("Processing complete. Output saved to " + outputImagePath);

        System.out.println("\n=== Extracting individual squares ===");
        Mat cleanSrc = Imgcodecs.imread(inputImagePath);
        extractSquareImages(cleanSrc, outerCorners, innerCorners, squaresOutputDir , baseFileName);
        
        Platform.exit();
    }
    
    public static Image mat2Image(Mat frame) {
        MatOfByte buffer = new MatOfByte();
        Imgcodecs.imencode(".png", frame, buffer);
        return new Image(new ByteArrayInputStream(buffer.toArray()));
    }
    
    // =========================================================================
    // GUI CLASS
    // =========================================================================
    public static class ManualBoardPicker {
        
        private static Point[] resultCorners = null;
        private static final List<Point> clickedPoints = new ArrayList<>();
        
        public static Point[] showInterface(Image image, Point[] detectedPoints) {
            CountDownLatch latch = new CountDownLatch(1);
            resultCorners = detectedPoints;

            Platform.runLater(() -> {
                Stage stage = new Stage();
                stage.setTitle("Chess Board Selector");

                BorderPane root = new BorderPane();
                ScrollPane scrollPane = new ScrollPane();
                scrollPane.setPannable(true);
                
                Pane contentContainer = new Pane();
                ImageView imageView = new ImageView(image);
                imageView.setPreserveRatio(true);
                
                Pane overlayPane = new Pane();
                overlayPane.setPrefSize(image.getWidth(), image.getHeight());
                overlayPane.setMouseTransparent(true); 

                contentContainer.getChildren().addAll(imageView, overlayPane);
                scrollPane.setContent(contentContainer);
                
                root.setCenter(scrollPane);

                HBox controls = new HBox(15);
                controls.setStyle("-fx-padding: 15; -fx-background-color: #222; -fx-alignment: center-left;");
                
                Text statusText = new Text();
                statusText.setFill(Color.WHITE);
                statusText.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

                Button btnConfirm = new Button("Confirm Selection");
                Button btnReset = new Button("Reset / Manual Pick");
                Button btnCancel = new Button("Cancel");
                
                btnConfirm.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-size: 14px;");
                btnReset.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-size: 14px;");
                btnCancel.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-font-size: 14px;");

                Runnable redraw = () -> {
                    overlayPane.getChildren().clear();
                    if (resultCorners != null) {
                        Point[] ordered = orderPoints(resultCorners);
                        
                        for (int i = 0; i < 4; i++) {
                            Line l = new Line(ordered[i].x, ordered[i].y, ordered[(i+1)%4].x, ordered[(i+1)%4].y);
                            l.setStroke(Color.LIME);
                            l.setStrokeWidth(5);
                            overlayPane.getChildren().add(l);
                        }
                        
                        for (int i = 0; i < 4; i++) {
                            Circle c = new Circle(ordered[i].x, ordered[i].y, 10, Color.LIME);
                            c.setStroke(Color.BLACK);
                            c.setStrokeWidth(2);
                            Text t = new Text(ordered[i].x + 15, ordered[i].y - 15, String.valueOf(i));
                            t.setFill(Color.YELLOW);
                            t.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-stroke: black; -fx-stroke-width: 1px;");
                            overlayPane.getChildren().addAll(c, t);
                        }
                        statusText.setText("Status: Corners Detected. Confirm or Reset.");
                        statusText.setFill(Color.LIGHTGREEN);
                        btnConfirm.setDisable(false);
                    } else {
                        for (int i = 0; i < clickedPoints.size(); i++) {
                            Point p = clickedPoints.get(i);
                            Circle c = new Circle(p.x, p.y, 8, Color.CYAN);
                            c.setStroke(Color.WHITE);
                            Text t = new Text(p.x + 10, p.y, String.valueOf(i + 1));
                            t.setFill(Color.CYAN);
                            t.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-stroke: black;");
                            overlayPane.getChildren().addAll(c, t);
                        }
                        statusText.setText("Manual Mode: Click " + (clickedPoints.size()) + "/4 corners.");
                        statusText.setFill(Color.CYAN);
                        btnConfirm.setDisable(true);
                    }
                };

                contentContainer.setOnMouseClicked(e -> {
                    if (resultCorners == null) {
                        if (e.getButton() == MouseButton.PRIMARY && clickedPoints.size() < 4) {
                            clickedPoints.add(new Point(e.getX(), e.getY()));
                            if (clickedPoints.size() == 4) {
                                Point[] pts = new Point[4];
                                for(int i=0; i<4; i++) pts[i] = clickedPoints.get(i);
                                resultCorners = orderPoints(pts);
                            }
                            redraw.run();
                        }
                    }
                });

                btnReset.setOnAction(e -> {
                    resultCorners = null;
                    clickedPoints.clear();
                    redraw.run();
                });

                btnConfirm.setOnAction(e -> {
                    stage.close();
                    latch.countDown();
                });

                btnCancel.setOnAction(e -> {
                    resultCorners = null;
                    stage.close();
                    latch.countDown();
                });

                controls.getChildren().addAll(statusText, btnReset, btnConfirm, btnCancel);
                root.setBottom(controls);

                Scene scene = new Scene(root, 1200, 800);
                stage.setScene(scene);
                stage.setMaximized(true);
                
                if (resultCorners == null) {
                    statusText.setText("Auto-Detection Failed. Please select 4 corners manually.");
                    btnReset.fire(); 
                } else {
                    redraw.run();
                }
                
                stage.show();
            });

            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return resultCorners;
        }
    }

    // =========================================================================
    // VISION LOGIC (RESTORED TO YOUR ORIGINAL CODE)
    // =========================================================================

    public static Point[] findBoardCorners(Mat originalSrc, Mat debugImg) {
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

        // Convert to grayscale
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);

        // Strong denoising to reduce spurious edges
        Imgproc.GaussianBlur(gray, blurred, new Size(9, 9), 2);
        
        // Additional bilateral filter for edge-preserving smoothing
        Mat bilateral = new Mat();
        Imgproc.bilateralFilter(blurred, bilateral, 11, 80, 80);

        List<Mat> thresholdedImages = new ArrayList<>();

        // Approach 1: Adaptive threshold with larger block size
        Mat thresh1 = new Mat();
        Imgproc.adaptiveThreshold(bilateral, thresh1, 255,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV, 31, 10); // Increased from 21, 5
        thresholdedImages.add(thresh1);

        // Approach 2: Otsu's thresholding
        Mat thresh2 = new Mat();
        Imgproc.threshold(bilateral, thresh2, 0, 255,
                Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU);
        thresholdedImages.add(thresh2);

        // Approach 3: Improved Canny with aggressive morphological closing
        Mat edges = new Mat();
        Imgproc.Canny(bilateral, edges, 30, 90); // Lower thresholds
        
        // Aggressive closing to connect broken edges
        Mat kernelClose = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(9, 9));
        Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, kernelClose);
        
        Mat kernelDilate = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(5, 5));
        Imgproc.dilate(edges, edges, kernelDilate, new Point(-1, -1), 2);
        thresholdedImages.add(edges);

        // Approach 4: Morphological gradient with strong closing
        Mat morphGrad = new Mat();
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(5, 5));
        Imgproc.morphologyEx(bilateral, morphGrad, Imgproc.MORPH_GRADIENT, kernel);
        
        Mat morphThresh = new Mat();
        Imgproc.threshold(morphGrad, morphThresh, 0, 255, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU);
        
        // Strong closing to connect edges
        Mat kernelClose2 = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(11, 11));
        Imgproc.morphologyEx(morphThresh, morphThresh, Imgproc.MORPH_CLOSE, kernelClose2);
        thresholdedImages.add(morphThresh);

        if (DEBUG_MODE) {
            Imgcodecs.imwrite("debug_threshold1_adaptive.jpg", thresh1);
            Imgcodecs.imwrite("debug_threshold2_otsu.jpg", thresh2);
            Imgcodecs.imwrite("debug_threshold3_edges.jpg", edges);
            Imgcodecs.imwrite("debug_threshold4_morph.jpg", morphThresh);
        }

        double imageArea = src.width() * src.height();
        List<CandidateQuad> candidates = new ArrayList<>();

        for (int threshIdx = 0; threshIdx < thresholdedImages.size(); threshIdx++) {
            Mat currentThresh = thresholdedImages.get(threshIdx);

            System.out.println("\n=== Trying threshold approach #" + threshIdx + " ===");

            // Additional morphological operations to ensure closed contours
            Mat processed = new Mat();
            Mat kernelCloseOp = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(11, 11));
            Imgproc.morphologyEx(currentThresh, processed, Imgproc.MORPH_CLOSE, kernelCloseOp);

            // Find Contours
            List<MatOfPoint> contours = new ArrayList<>();
            Mat hierarchy = new Mat();
            Imgproc.findContours(processed, contours, hierarchy,
                    Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

            contours.sort((c1, c2) -> Double.compare(Imgproc.contourArea(c2), Imgproc.contourArea(c1)));

            int attemptNumber = threshIdx * 10;
            Scalar[] attemptColors = new Scalar[]{
                    new Scalar(255, 0, 0), new Scalar(0, 255, 255),
                    new Scalar(255, 0, 255), new Scalar(0, 165, 255),
                    new Scalar(255, 255, 0), new Scalar(128, 0, 128),
                    new Scalar(0, 255, 0), new Scalar(255, 128, 0),
                    new Scalar(128, 128, 128), new Scalar(200, 200, 200)
            };

            for (int i = 0; i < Math.min(contours.size(), 20); i++) { // Check more contours
                MatOfPoint contour = contours.get(i);
                double area = Imgproc.contourArea(contour);

                System.out.println("\n--- Checking contour #" + i + " (threshold " + threshIdx + ") ---");
                System.out.println("Area: " + area + " (" + (area/imageArea*100) + "% of image)");

                // More relaxed size constraints
                if (area < imageArea * 0.10) {
                    System.out.println("Skipped: Too small (< 10% of image)");
                    continue;
                }

                if (area > imageArea * 0.95) {
                    System.out.println("Skipped: Too large (> 95% of image)");
                    continue;
                }

                // Convex hull
                MatOfInt hullIdx = new MatOfInt();
                Imgproc.convexHull(contour, hullIdx);

                Point[] contourArray = contour.toArray();
                Point[] hullPoints = new Point[hullIdx.rows()];
                for(int j=0; j < hullIdx.rows(); j++) {
                    hullPoints[j] = contourArray[hullIdx.toArray()[j]];
                }
                MatOfPoint2f hull2f = new MatOfPoint2f(hullPoints);

                // Try multiple epsilon values with wider range
                for (double epsilonFactor = 0.01; epsilonFactor <= 0.12; epsilonFactor += 0.005) {
                    double peri = Imgproc.arcLength(hull2f, true);
                    MatOfPoint2f approx = new MatOfPoint2f();
                    Imgproc.approxPolyDP(hull2f, approx, epsilonFactor * peri, true);

                    if (approx.total() == 4) {
                        Point[] foundPoints = approx.toArray();
                        System.out.println("Found 4-sided polygon with epsilon: " + epsilonFactor);

                        // Basic validation
                        if (!isValidQuadrilateral(foundPoints)) {
                            System.out.println("Rejected: Invalid quadrilateral");
                            continue;
                        }

                        Point[] ordered = orderPoints(foundPoints);
                        
                        // Check corner angles
                        if (!hasReasonableAngles(ordered)) {
                            System.out.println("Rejected: Corner angles too extreme");
                            continue;
                        }

                        // Check centering (more relaxed)
                        double centerX = (ordered[0].x + ordered[1].x + ordered[2].x + ordered[3].x) / 4.0;
                        double centerY = (ordered[0].y + ordered[1].y + ordered[2].y + ordered[3].y) / 4.0;
                        double imgCenterX = src.width() / 2.0;
                        double imgCenterY = src.height() / 2.0;
                        double centerDistX = Math.abs(centerX - imgCenterX) / src.width();
                        double centerDistY = Math.abs(centerY - imgCenterY) / src.height();

                        System.out.println("Board center offset: X=" + (centerDistX*100) + "%, Y=" + (centerDistY*100) + "%");

                        if (centerDistX > 0.48 || centerDistY > 0.48) {
                            System.out.println("Rejected: Board too off-center");
                            continue;
                        }

                        // Check solidity
                        double solidity = area / Imgproc.contourArea(new MatOfPoint(hullPoints));
                        System.out.println("Solidity: " + solidity);
                        if (solidity < 0.81) {
                            System.out.println("Rejected: Solidity too low");
                            continue;
                        }

                        // Calculate comprehensive score
                        double score = calculateCandidateScore(ordered, area, solidity, centerDistX, centerDistY, src);

                        // Pattern and color checks
                        double patternScore = checkChessBoardPattern(src, ordered);
                        score += patternScore * 0.5; // Increased weight

                        double colorConsistency = checkColorConsistency(src, ordered);
                        score += colorConsistency * 0.3; // Increased weight

                        System.out.println("Candidate score: " + score + " (pattern: " + patternScore + ", color: " + colorConsistency + ")");

                        // Draw on debug image
                        Point[] scaledPoints = new Point[4];
                        for(int k = 0; k < 4; k++) {
                            scaledPoints[k] = new Point(foundPoints[k].x / scale, foundPoints[k].y / scale);
                        }

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

                        Point[] scaledForCandidate = new Point[4];
                        for(int k = 0; k < 4; k++) {
                            scaledForCandidate[k] = new Point(foundPoints[k].x / scale, foundPoints[k].y / scale);
                        }

                        candidates.add(new CandidateQuad(orderPoints(scaledForCandidate), score));
                    }
                }
            }
        }

        // Select best candidate
        if (!candidates.isEmpty()) {
            candidates.sort((c1, c2) -> Double.compare(c2.score, c1.score));
            CandidateQuad best = candidates.get(0);
            System.out.println("✓✓✓ Found best board corners with score: " + best.score + " ✓✓✓");
            return best.corners;
        }

        System.out.println("❌ No valid board detected");
        return null;
    }

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

    public static class CandidateQuad {
        Point[] corners;
        double score;

        CandidateQuad(Point[] corners, double score) {
            this.corners = corners;
            this.score = score;
        }
    }

    public static double calculateCandidateScore(Point[] ordered, double area, double solidity,
                                                 double centerDistX, double centerDistY, Mat src) {
        double score = 0.0;

        // Area score (prefer medium-large boards)
        double areaRatio = area / (src.width() * src.height());
        if (areaRatio >= 0.25 && areaRatio <= 0.75) {
            score += 2.0; // Increased weight
        } else if (areaRatio >= 0.15 && areaRatio <= 0.85) {
            score += 1.0;
        }

        // Solidity score (increased weight)
        score += solidity * 1.0;

        // Centering score
        double centerScore = 1.0 - (centerDistX + centerDistY) / 2.0;
        score += centerScore * 0.5;

        // Aspect ratio score (prefer square)
        double topWidth = Math.sqrt(Math.pow(ordered[1].x - ordered[0].x, 2) + Math.pow(ordered[1].y - ordered[0].y, 2));
        double bottomWidth = Math.sqrt(Math.pow(ordered[2].x - ordered[3].x, 2) + Math.pow(ordered[2].y - ordered[3].y, 2));
        double leftHeight = Math.sqrt(Math.pow(ordered[3].x - ordered[0].x, 2) + Math.pow(ordered[3].y - ordered[0].y, 2));
        double rightHeight = Math.sqrt(Math.pow(ordered[2].x - ordered[1].x, 2) + Math.pow(ordered[2].y - ordered[1].y, 2));
        double avgWidth = (topWidth + bottomWidth) / 2.0;
        double avgHeight = (leftHeight + rightHeight) / 2.0;
        double aspectRatio = avgWidth / avgHeight;
        double aspectScore = 1.0 - Math.abs(1.0 - aspectRatio) * 2.0;
        aspectScore = Math.max(0, aspectScore);
        score += aspectScore * 0.5;

        return score;
    }

    public static double checkChessBoardPattern(Mat src, Point[] corners) {
        try {
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

            Mat grayWarped = new Mat();
            Imgproc.cvtColor(warped, grayWarped, Imgproc.COLOR_BGR2GRAY);

            int squareSize = size / 8;
            
            // Calculate variance in brightness across squares
            List<Double> brightnesses = new ArrayList<>();
            for (int row = 0; row < 8; row++) {
                for (int col = 0; col < 8; col++) {
                    Rect squareRect = new Rect(col * squareSize + 5, row * squareSize + 5, 
                                               squareSize - 10, squareSize - 10);
                    Mat square = new Mat(grayWarped, squareRect);
                    Scalar mean = Core.mean(square);
                    brightnesses.add(mean.val[0]);
                }
            }

            // Check if there's good variance (alternating pattern)
            double sumBrightness = 0;
            for (double b : brightnesses) sumBrightness += b;
            double meanBrightness = sumBrightness / brightnesses.size();

            double variance = 0;
            for (double b : brightnesses) {
                variance += Math.pow(b - meanBrightness, 2);
            }
            variance /= brightnesses.size();

            // Higher variance = better pattern
            double patternScore = Math.min(1.0, variance / 1000.0);
            System.out.println("Pattern variance: " + variance + " (score: " + patternScore + ")");
            return patternScore;
        } catch (Exception e) {
            System.out.println("Pattern check failed: " + e.getMessage());
            return 0.0;
        }
    }

    public static double checkColorConsistency(Mat src, Point[] corners) {
        try {
            Point[] ordered = orderPoints(corners);
            List<Scalar> samples = new ArrayList<>();

            double centerX = (ordered[0].x + ordered[1].x + ordered[2].x + ordered[3].x) / 4.0;
            double centerY = (ordered[0].y + ordered[1].y + ordered[2].y + ordered[3].y) / 4.0;

            for (int i = 0; i < 4; i++) {
                Point p1 = ordered[i];
                Point p2 = ordered[(i + 1) % 4];
                double midX = (p1.x + p2.x) / 2.0;
                double midY = (p1.y + p2.y) / 2.0;

                double sampleX = (centerX + midX) / 2.0;
                double sampleY = (centerY + midY) / 2.0;

                if (sampleX >= 10 && sampleX < src.width() - 10 && sampleY >= 10 && sampleY < src.height() - 10) {
                    Mat sampleMat = new Mat(src, new Rect((int)sampleX - 5, (int)sampleY - 5, 10, 10));
                    Scalar mean = Core.mean(sampleMat);
                    samples.add(mean);
                }
            }

            if (samples.size() < 3) return 0.5;

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

            double consistencyScore = 1.0 / (1.0 + variance / 100.0);
            return consistencyScore;
        } catch (Exception e) {
            System.out.println("Color consistency check failed: " + e.getMessage());
            return 0.5;
        }
    }

    public static boolean isValidQuadrilateral(Point[] pts) {
        Point[] ordered = orderPoints(pts);
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

        // VERY RELAXED width ratio (allow strong perspective)
        double widthRatio = Math.min(topWidth, bottomWidth) / Math.max(topWidth, bottomWidth);
        if (widthRatio < 0.20) { 
            System.out.println("Failed: Width ratio too extreme: " + widthRatio);
            return false;
        }

        // VERY RELAXED height ratio
        double heightRatio = Math.min(leftHeight, rightHeight) / Math.max(leftHeight, rightHeight);
        if (heightRatio < 0.20) { 
            System.out.println("Failed: Height ratio too extreme: " + heightRatio);
            return false;
        }

        // VERY RELAXED aspect ratio
        double aspectRatio = avgWidth / avgHeight;
        if (aspectRatio < 0.3 || aspectRatio > 3.0) { 
            System.out.println("Failed: Aspect ratio not square enough: " + aspectRatio);
            return false;
        }

        // RELAXED diagonal check
        double diag1 = Math.sqrt(Math.pow(br.x - tl.x, 2) + Math.pow(br.y - tl.y, 2));
        double diag2 = Math.sqrt(Math.pow(bl.x - tr.x, 2) + Math.pow(bl.y - tr.y, 2));
        double diagRatio = Math.min(diag1, diag2) / Math.max(diag1, diag2);

        System.out.println("Diagonal ratio: " + diagRatio);
        if (diagRatio < 0.30) { 
            System.out.println("Failed: Diagonals too unequal: " + diagRatio);
            return false;
        }

        System.out.println("✓ Validation passed - proper quadrilateral detected");
        return true;
    }

    public static Point[] orderPoints(Point[] pts) {
        Point[] result = new Point[4];
        List<Point> points = Arrays.asList(pts);

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

    public static boolean hasReasonableAngles(Point[] ordered) {
        double[] angles = new double[4];

        for (int i = 0; i < 4; i++) {
            Point prev = ordered[(i + 3) % 4];
            Point curr = ordered[i];
            Point next = ordered[(i + 1) % 4];

            double v1x = prev.x - curr.x;
            double v1y = prev.y - curr.y;
            double v2x = next.x - curr.x;
            double v2y = next.y - curr.y;

            double dot = v1x * v2x + v1y * v2y;
            double mag1 = Math.sqrt(v1x * v1x + v1y * v1y);
            double mag2 = Math.sqrt(v2x * v2x + v2y * v2y);

            double cosAngle = dot / (mag1 * mag2);
            cosAngle = Math.max(-1.0, Math.min(1.0, cosAngle));
            angles[i] = Math.toDegrees(Math.acos(cosAngle));
        }

        System.out.println("Corner angles: " + Arrays.toString(angles));

        // VERY RELAXED angle constraints (20-160 degrees)
        for (double angle : angles) {
            if (angle < 20 || angle > 160) {
                System.out.println("Failed: Angle " + angle + " is too extreme");
                return false;
            }
        }

        return true;
    }

    /**
     * Extracts individual square images from the chessboard.
     */
    public static void extractSquareImages(Mat src, Point[] outerCorners, Point[] innerCorners, String outputDir , String baseFileName) {
        int warpedWidth = VIRTUAL_RESOLUTION;
        
        // Define a "Sky Buffer"
        int skyBuffer = (int)(warpedWidth * 0.5); 
        int warpedHeight = warpedWidth + skyBuffer; // Total height of the new image
        
        Point[] dstPoints = new Point[]{
                new Point(0, skyBuffer),                  // TL
                new Point(warpedWidth, skyBuffer),        // TR
                new Point(warpedWidth, warpedWidth + skyBuffer), // BR
                new Point(0, warpedWidth + skyBuffer)     // BL
        };

        Mat srcMat = new MatOfPoint2f(outerCorners);
        Mat dstMat = new MatOfPoint2f(dstPoints);
        Mat perspectiveMatrix = Imgproc.getPerspectiveTransform(srcMat, dstMat);

        Mat warpedBoard = new Mat();
        Imgproc.warpPerspective(src, warpedBoard, perspectiveMatrix, 
                                new Size(warpedWidth, warpedHeight));

        if (DEBUG_MODE) {
            Imgcodecs.imwrite("debug_warped_board_with_buffer.jpg", warpedBoard);
        }

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

                if (row == 0) {
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

                int extendedX = (int)Math.max(0, baseX - extraWidthPerSide);
                int extendedY = (int)Math.max(0, baseY - extraHeight);
                
                int extendedWidth = (int)squareSize + (2 * extraWidthPerSide);
                int extendedHeight = (int)squareSize + extraHeight;
                
                if (extendedX + extendedWidth > warpedBoard.width()) {
                    extendedWidth = warpedBoard.width() - extendedX;
                }
                if (extendedY + extendedHeight > warpedBoard.height()) {
                    extendedHeight = warpedBoard.height() - extendedY;
                }

                Rect squareRect = new Rect(extendedX, extendedY, extendedWidth, extendedHeight);
                Mat squareImg = new Mat(warpedBoard, squareRect);

                String chessNotation = (char)('A' + col) + "" + (8 - row);
                String filename = String.format(baseFileName + "square%02d_%s.jpg", squareNumber, chessNotation);
                String filepath = Paths.get(outputDir, filename).toString();

                Imgcodecs.imwrite(filepath, squareImg);
                squareNumber++;
            }
        }
        System.out.println("✓ Extracted with Sky Buffer.");
    }
}