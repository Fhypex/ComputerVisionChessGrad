package com.chessgame;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import nu.pattern.OpenCV;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ChangeDetector {

    // Load the OpenCV native library
    static {
        // Assuming you are using the openpnp/opencv dependency
        OpenCV.loadLocally();
    }

    public static void main(String[] args) {
        // --- SETUP DATA ---
        // Create dummy images if they don't exist, just for testing flow
        // createDummyImages(); 

        String nameOfFile = "IMG_9665_720p.jpg";
        String file1 = Paths.get("src", "main", "resources", "tests", nameOfFile).toString();
        
        String nameOfFile2 = "IMG_9666_720p.jpg";
        String file2 = Paths.get("src", "main", "resources", "tests", nameOfFile2).toString();
        
        String outputFilename = nameOfFile2 + "_detect.jpg";
        String outputImagePath = Paths.get("output", outputFilename).toString();

        // --- RUN DETECTION ---
        System.out.println("Processing...");
        System.out.println("Input 1: " + file1);
        System.out.println("Input 2: " + file2);
        
        detectChanges(file1, file2, outputImagePath);
    }

    public static void detectChanges(String imagePath1, String imagePath2, String outputPath) {
        // 1. Load the two images
        Mat img1 = Imgcodecs.imread(imagePath1);
        Mat img2 = Imgcodecs.imread(imagePath2);

        if (img1.empty() || img2.empty()) {
            System.out.println("Error: Could not load images. Check your file paths.");
            return;
        }

        // Ensure images are the same size for processing
        // We resize img2 to match img1 if they differ
        if (img1.size().width != img2.size().width || img1.size().height != img2.size().height) {
            Imgproc.resize(img2, img2, img1.size());
        }

        // 2. Convert to Grayscale
        Mat gray1 = new Mat();
        Mat gray2 = new Mat();
        Imgproc.cvtColor(img1, gray1, Imgproc.COLOR_BGR2GRAY);
        Imgproc.cvtColor(img2, gray2, Imgproc.COLOR_BGR2GRAY);

        // 3. Blur the images to remove noise
        // This is crucial for chess boards to ignore grain or slight lighting shifts
        Imgproc.GaussianBlur(gray1, gray1, new Size(21, 21), 0);
        Imgproc.GaussianBlur(gray2, gray2, new Size(21, 21), 0);

        // 4. Compute the Absolute Difference
        Mat diffFrame = new Mat();
        Core.absdiff(gray1, gray2, diffFrame);

        // 5. Thresholding (Convert to strictly Black/White)
        Mat thresh = new Mat();
        double threshVal = 30.0;
        Imgproc.threshold(diffFrame, thresh, threshVal, 255, Imgproc.THRESH_BINARY);

        // Dilate to fill holes
        Imgproc.dilate(thresh, thresh, new Mat(), new Point(-1, -1), 2);

        // 6. Find Contours
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(thresh, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        // Create a copy of image 2 to draw on (This will be our "After" image)
        Mat resultImg = img2.clone();

        // 7. Draw Boxes around changes
        for (MatOfPoint contour : contours) {
            // Ignore small movements (adjust this area threshold based on your camera resolution)
            if (Imgproc.contourArea(contour) < 500) {
                continue;
            }

            // Get bounding box
            Rect rect = Imgproc.boundingRect(contour);

            // Draw green rectangle (BGR color: 0, 255, 0)
            Imgproc.rectangle(resultImg, 
                    new Point(rect.x, rect.y), 
                    new Point(rect.x + rect.width, rect.y + rect.height), 
                    new Scalar(0, 255, 0), 2);
        }

        // --- VISUALIZATION BLOCK ---
        
        // We need to ensure img1 is the same type/size as resultImg for stacking
        // (It should be, unless img2 was resized at the start, but let's be safe)
        if (img1.size().width != resultImg.size().width || img1.size().height != resultImg.size().height) {
             Imgproc.resize(img1, img1, resultImg.size());
        }

        // Add text labels to the images
        // Note: We clone img1 here so we don't draw text on the original data if we needed it later
        Mat displayImg1 = img1.clone();
        
        Imgproc.putText(displayImg1, "Before (Input 1)", new Point(50, 50), 
                Imgproc.FONT_HERSHEY_SIMPLEX, 1.0, new Scalar(0, 255, 0), 2);
        
        Imgproc.putText(resultImg, "After (Detected)", new Point(50, 50), 
                Imgproc.FONT_HERSHEY_SIMPLEX, 1.0, new Scalar(0, 255, 0), 2);

        // Stack images horizontally: [ Before ] [ After ]
        Mat combinedOutput = new Mat();
        List<Mat> src = Arrays.asList(displayImg1, resultImg);
        Core.hconcat(src, combinedOutput);

        // Save and finish
        Imgcodecs.imwrite(outputPath, combinedOutput);
        System.out.println("Success! Check " + outputPath + " to see the changes.");
    }

    private static void createDummyImages() {
        // Create black images (400x400)
        Mat imgA = Mat.zeros(400, 400, CvType.CV_8UC3);
        Mat imgB = Mat.zeros(400, 400, CvType.CV_8UC3);

        // Draw white square in different positions
        Imgproc.rectangle(imgA, new Point(50, 50), new Point(100, 100), new Scalar(255, 255, 255), -1);
        Imgproc.rectangle(imgB, new Point(200, 200), new Point(250, 250), new Scalar(255, 255, 255), -1);

        Imgcodecs.imwrite("test_before.jpg", imgA);
        Imgcodecs.imwrite("test_after.jpg", imgB);
    }
}