package com.chessgame;

import ai.onnxruntime.*;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import nu.pattern.OpenCV;

import java.nio.FloatBuffer;
import java.nio.file.Paths;
import java.util.Collections;

public class TestYoloMain {
    static { OpenCV.loadLocally(); }

    public static void main(String[] args) throws Exception {

        String modelPath = Paths.get("models", "best.onnx").toString();
        String imagePath = Paths.get("src", "main", "resources", "tests", "test.jpg").toString();

        // Load YOLO ONNX model
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        OrtSession session = env.createSession(modelPath, opts);
        System.out.println("Model loaded successfully!");

        // Read and preprocess image
        Mat img = Imgcodecs.imread(imagePath);
        if (img.empty()) {
            System.err.println("Could not read input image: " + imagePath);
            return;
        }

        int origWidth = img.width();
        int origHeight = img.height();

        Mat resized = new Mat();
        Imgproc.resize(img, resized, new Size(640, 640));
        Imgproc.cvtColor(resized, resized, Imgproc.COLOR_BGR2RGB);

        float[] inputData = new float[3 * 640 * 640];
        int idx = 0;
        for (int y = 0; y < 640; y++) {
            for (int x = 0; x < 640; x++) {
                double[] pixel = resized.get(y, x);
                inputData[idx++] = (float) (pixel[0] / 255.0);
                inputData[idx++] = (float) (pixel[1] / 255.0);
                inputData[idx++] = (float) (pixel[2] / 255.0);
            }
        }

        OnnxTensor inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputData), new long[]{1, 3, 640, 640});

        // Run inference
        OrtSession.Result result = session.run(Collections.singletonMap("images", inputTensor));
        float[][][] output = (float[][][]) result.get(0).getValue();
        float[][] detections = output[0];

        System.out.println("Inference completed! Drawing boxes...");

        // Draw detections
        for (float[] det : detections) {
            float conf = det[4];
            if (conf > 0.5) { // threshold
                float cx = det[0];
                float cy = det[1];
                float w = det[2];
                float h = det[3];

                // Convert from YOLO center x,y,w,h to top-left corner format
                float x1 = (cx - w / 2);
                float y1 = (cy - h / 2);
                float x2 = (cx + w / 2);
                float y2 = (cy + h / 2);

                // Scale back to original image size
                int x1i = (int) (x1 * origWidth / 640);
                int y1i = (int) (y1 * origHeight / 640);
                int x2i = (int) (x2 * origWidth / 640);
                int y2i = (int) (y2 * origHeight / 640);

                // Draw rectangle and label
                Imgproc.rectangle(img, new Point(x1i, y1i), new Point(x2i, y2i), new Scalar(0, 255, 0), 2);
                Imgproc.putText(img,
                        String.format("Conf: %.2f", conf),
                        new Point(x1i, y1i - 5),
                        Imgproc.FONT_HERSHEY_SIMPLEX,
                        0.6,
                        new Scalar(255, 0, 0),
                        2
                );
            }
        }

        // Save output image
        String outputPath = Paths.get("output", "detections.jpg").toString();
        new java.io.File("output").mkdirs();
        Imgcodecs.imwrite(outputPath, img);
        System.out.println("Result saved to: " + outputPath);

        // OPTIONAL: Display image in a window
        // (Only works if you have a desktop environment)
        showImage("YOLO Detections", img);

        System.out.println("Test completed!");
    }

    private static void showImage(String title, Mat img) {
        // Convert BGR to RGB for display
        Mat bgr = new Mat();
        Imgproc.cvtColor(img, bgr, Imgproc.COLOR_BGR2RGB);

        // Convert Mat to BufferedImage
        int width = bgr.width();
        int height = bgr.height();
        int channels = bgr.channels();
        byte[] sourcePixels = new byte[width * height * channels];
        bgr.get(0, 0, sourcePixels);
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_3BYTE_BGR);
        image.getRaster().setDataElements(0, 0, width, height, sourcePixels);

        javax.swing.ImageIcon icon = new javax.swing.ImageIcon(image);
        javax.swing.JFrame frame = new javax.swing.JFrame(title);
        frame.setLayout(new java.awt.FlowLayout());
        frame.setSize(image.getWidth() + 50, image.getHeight() + 50);
        javax.swing.JLabel lbl = new javax.swing.JLabel();
        lbl.setIcon(icon);
        frame.add(lbl);
        frame.setVisible(true);
        frame.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
    }
}
