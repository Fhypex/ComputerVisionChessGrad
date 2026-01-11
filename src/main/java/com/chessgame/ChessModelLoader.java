package com.chessgame;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;

public class ChessModelLoader {

    private final String serverUrl;
    private final Gson gson;
    private boolean serverHealthy = false;

    /**
     * Constructor - default to localhost:5000
     */
    public ChessModelLoader() {
        this("http://127.0.0.1:5000");
    }

    /**
     * Constructor with custom server URL
     */
    public ChessModelLoader(String serverUrl) {
        this.serverUrl = serverUrl;
        this.gson = new Gson();
    }

    /**
     * Check if Python server is running and model is loaded
     */
    public void loadModel(String modelPath) throws Exception {
        System.out.println("Checking Python model server at: " + serverUrl);

        try {
            URL url = new URL(serverUrl + "/health");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int responseCode = conn.getResponseCode();
            
            if (responseCode == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String response = in.readLine();
                in.close();

                JsonObject health = gson.fromJson(response, JsonObject.class);
                serverHealthy = health.get("model_loaded").getAsBoolean();

                if (serverHealthy) {
                    System.out.println("✓ Python server is healthy!");
                    System.out.println("  Status: " + health.get("status").getAsString());
                    System.out.println("  Classes: " + health.get("num_classes").getAsInt());
                } else {
                    throw new Exception("Server is running but model is not loaded");
                }
            } else {
                throw new Exception("Server returned status code: " + responseCode);
            }

        } catch (Exception e) {
            System.err.println("✗ CRITICAL ERROR: Cannot connect to Python server!");
            System.err.println("  Make sure to run: python model_server.py");
            System.err.println("  Error: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Make prediction by sending image data to Python server
     */
    public int predict(float[] imageData, int height, int width, int channels) {
        if (!serverHealthy) {
            System.err.println("Server is not healthy!");
            return -1;
        }

        try {
            // Create JSON payload
            JsonObject payload = new JsonObject();
            
            // Convert float array to JSON array (Gson handles this)
            payload.add("image", gson.toJsonTree(imageData));

            // Send POST request
            URL url = new URL(serverUrl + "/predict");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            // Write payload
            OutputStream os = conn.getOutputStream();
            os.write(payload.toString().getBytes("UTF-8"));
            os.flush();
            os.close();

            // Read response
            int responseCode = conn.getResponseCode();
            
            if (responseCode == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                in.close();

                // Parse response
                JsonObject result = gson.fromJson(response.toString(), JsonObject.class);
                
                if (result.get("success").getAsBoolean()) {
                    int classIndex = result.get("class_index").getAsInt();
                    String className = result.get("class_name").getAsString();
                    double confidence = result.get("confidence").getAsDouble();

                    System.out.println("Prediction: " + className + 
                                     " (Class " + classIndex + 
                                     ", Confidence: " + String.format("%.2f%%", confidence * 100) + ")");

                    return classIndex;
                } else {
                    System.err.println("Server error: " + result.get("error").getAsString());
                    return -1;
                }
            } else {
                System.err.println("Server returned error code: " + responseCode);
                return -1;
            }

        } catch (Exception e) {
            System.err.println("Error during prediction: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Get prediction probabilities for all classes
     */
    public float[] predictProbabilities(float[] imageData, int height, int width, int channels) {
        if (!serverHealthy) {
            System.err.println("Server is not healthy!");
            return null;
        }

        try {
            JsonObject payload = new JsonObject();
            payload.add("image", gson.toJsonTree(imageData));

            URL url = new URL(serverUrl + "/predict");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            OutputStream os = conn.getOutputStream();
            os.write(payload.toString().getBytes("UTF-8"));
            os.flush();
            os.close();

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            in.close();

            JsonObject result = gson.fromJson(response.toString(), JsonObject.class);
            
            if (result.get("success").getAsBoolean()) {
                // Get probabilities array
                double[] probs = gson.fromJson(result.get("probabilities"), double[].class);
                
                // Convert to float array
                float[] floatProbs = new float[probs.length];
                for (int i = 0; i < probs.length; i++) {
                    floatProbs[i] = (float) probs[i];
                }
                
                return floatProbs;
            }

        } catch (Exception e) {
            System.err.println("Error getting probabilities: " + e.getMessage());
            e.printStackTrace();
        }
        
        return null;
    }

    public boolean isModelLoaded() {
        return serverHealthy;
    }

    public static String getClassName(int classIndex) {
        String[] classNames = {
            "black_bishop",       // 0
            "black_king",         // 1
            "black_knight",       // 2
            "black_pawn",         // 3
            "black_queen",        // 4
            "black_rook",         // 5
            "empty",              // 6
            "half_empty_square",  // 7
            "white_bishop",       // 8
            "white_king",         // 9
            "white_knight",       // 10
            "white_pawn",         // 11
            "white_queen",        // 12
            "white_rook"          // 13
        };
        
        if (classIndex >= 0 && classIndex < classNames.length) {
            return classNames[classIndex];
        }
        return "unknown";
    }
}