package com.chessgame;

import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;
import org.opencv.core.MatOfByte;

import java.io.ByteArrayInputStream;


public class CameraViewer extends StackPane {
    private final ImageView imageView = new ImageView();
    private VideoCapture capture;
    private volatile boolean running = false;

    static {
        nu.pattern.OpenCV.loadLocally();
    }

    public CameraViewer() {
        imageView.setFitWidth(400); // width of camera pane
        imageView.setPreserveRatio(true);
        this.getChildren().add(imageView);
    }

    public void startCamera() {
        capture = new VideoCapture(1); // 0 = default camera; try 1 or 2 for Camo
        
        if (!capture.isOpened()) {
            System.out.println("❌ Cannot open camera.");
            return;
        }

        running = true;
        Thread thread = new Thread(() -> {
            Mat frame = new Mat();
            while (running) {
                if (capture.read(frame)) {
                    Image image = mat2Image(frame);
                    Platform.runLater(() -> imageView.setImage(image));
                }
            }
            frame.release();
        });
        thread.setDaemon(true);
        thread.start();
    }

    public void stopCamera() {
        running = false;
        if (capture != null) {
            capture.release();
        }
    }

    public Mat captureCurrentFrame() {
        if (capture != null && capture.isOpened()) {
            Mat frame = new Mat();
            capture.read(frame);
            return frame;
        }
        return null;
    }

    private Image mat2Image(Mat frame) {
        MatOfByte buffer = new MatOfByte();
        Imgcodecs.imencode(".png", frame, buffer);
        return new Image(new ByteArrayInputStream(buffer.toArray()));
    }
}
