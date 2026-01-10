package com.chessgame;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StockfishClient {

    private static final String API_URL = "https://stockfish.online/api/s/v2.php";
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public interface StockfishCallback {
        void onMoveReceived(String bestMove, String evaluation);
        void onError(String error);
    }

    public static void getBestMove(String fen, int depth, StockfishCallback callback) {
        CompletableFuture.runAsync(() -> {
            try {
                // Encode FEN to be URL-safe
                String encodedFen = URLEncoder.encode(fen, StandardCharsets.UTF_8);
                String url = API_URL + "?fen=" + encodedFen + "&depth=" + depth;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    parseResponse(response.body(), callback);
                } else {
                    callback.onError("HTTP Error: " + response.statusCode());
                }
            } catch (Exception e) {
                callback.onError("Exception: " + e.getMessage());
            }
        });
    }

    private static void parseResponse(String json, StockfishCallback callback) {
        // Parse JSON using Regex to avoid external dependencies like Gson/Jackson
        // Example: "bestmove":"bestmove f6e4 ponder d2e4"
        
        // 1. Extract Move
        Pattern movePattern = Pattern.compile("bestmove\\s([a-h][1-8][a-h][1-8][qrbn]?)");
        Matcher moveMatcher = movePattern.matcher(json);
        
        // 2. Extract Evaluation
        Pattern evalPattern = Pattern.compile("\"evaluation\":([\\-0-9\\.]+)");
        Matcher evalMatcher = evalPattern.matcher(json);

        if (moveMatcher.find()) {
            String move = moveMatcher.group(1);
            String eval = evalMatcher.find() ? evalMatcher.group(1) : "?";
            callback.onMoveReceived(move, eval);
        } else {
            callback.onError("Could not parse move from JSON");
        }
    }
}