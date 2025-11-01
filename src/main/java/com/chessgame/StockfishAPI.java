package com.chessgame;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class StockfishAPI {

    public static String getBestMove(String fen, int depth) {
        try {
            String encodedFEN = URLEncoder.encode(fen, "UTF-8");
            String apiUrl = "https://stockfish.online/api/s/v2.php?fen=" + encodedFEN + "&depth=" + depth;
            URL url = java.net.URI.create(apiUrl).toURL();

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            return response.toString();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
