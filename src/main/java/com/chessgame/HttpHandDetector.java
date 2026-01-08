package com.chessgame;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.UUID;

public class HttpHandDetector {

    private final HttpClient client;
    private final String serverUrl; // e.g. http://127.0.0.1:8000/detect

    public HttpHandDetector(String serverUrl) {
        this.serverUrl = serverUrl.endsWith("/detect") ? serverUrl : serverUrl + "/detect";
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    /**
     * Sends the Mat image as JPEG to the server and returns true if a hand is detected.
     */
    public boolean isHandPresent(Mat frame, double confThreshold) {
        try {
            MatOfByte mob = new MatOfByte();
            Imgcodecs.imencode(".jpg", frame, mob);
            byte[] imageBytes = mob.toArray();

            String boundary = "----Boundary" + UUID.randomUUID().toString();
            byte[] prefix = (
                    "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"image\"; filename=\"img.jpg\"\r\n" +
                    "Content-Type: application/octet-stream\r\n\r\n"
            ).getBytes();
            byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes();

            var body = HttpRequest.BodyPublishers.ofByteArrays(java.util.List.of(prefix, imageBytes, suffix));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(body)
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            String respBody = resp.body();
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                // Simple parsing: check for "hand":true or numeric confidence
                if (respBody.contains("\"hand\":true") || respBody.contains("\"hand\": 1")) {
                    return true;
                }
                // fallback: look for confidence value
                try {
                    int idx = respBody.indexOf("confidence");
                    if (idx >= 0) {
                        String sub = respBody.substring(idx);
                        String digits = sub.replaceAll("[^0-9.]+", " ").trim();
                        if (!digits.isEmpty()) {
                            double c = Double.parseDouble(digits.split(" ")[0]);
                            return c >= confThreshold;
                        }
                    }
                } catch (Exception ex) {
                    // ignore and treat as no-hand
                }
            }
        } catch (Exception e) {
            System.err.println("HandDetector error: " + e.getMessage());
        }
        return false;
    }
}
