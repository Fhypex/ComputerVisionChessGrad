package com.chessgame;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PieceClassifier {

    public static class Template {
        public String label;
        public Mat image;

        public Template(String label, String path) {
            this.label = label;
            this.image = Imgcodecs.imread(path, Imgcodecs.IMREAD_GRAYSCALE);
            if (this.image.empty()) {
                System.out.println("⚠️ Could not load template: " + path);
            }
        }
    }

    private final List<Template> templates = new ArrayList<>();

    public PieceClassifier(String templateDir) {
        loadTemplates(templateDir);
    }

    private void loadTemplates(String templateDir) {
        File dir = new File(templateDir);
        if (!dir.exists()) {
            System.out.println("Template directory not found: " + templateDir);
            return;
        }

        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".png") || name.toLowerCase().endsWith(".jpg"));
        if (files == null) return;

        for (File f : files) {
            String label = f.getName().replace(".jpg", "").replace(".png", "");
            templates.add(new Template(label, f.getAbsolutePath()));
        }

        System.out.println("Loaded " + templates.size() + " templates from " + templateDir);
    }

    /**
     * Classifies a single board cell using template matching.
     * Returns the best matching piece label or "empty".
     */
    public String classifyCell(Mat cell) {
        if (templates.isEmpty()) {
            System.out.println("No templates loaded!");
            return "empty";
        }

        Mat grayCell = new Mat();
        Imgproc.cvtColor(cell, grayCell, Imgproc.COLOR_BGR2GRAY);

        double bestScore = 0;
        String bestLabel = "empty";

        for (Template t : templates) {
            if (t.image.empty()) continue;

            Mat result = new Mat();
            Imgproc.matchTemplate(grayCell, t.image, result, Imgproc.TM_CCOEFF_NORMED);
            Core.MinMaxLocResult mmr = Core.minMaxLoc(result);

            if (mmr.maxVal > bestScore && mmr.maxVal > 0.7) { // threshold
                bestScore = mmr.maxVal;
                bestLabel = t.label;
            }
        }

        return bestLabel;
    }

    /**
     * Classifies all 64 board cells and prints a 2D board view.
     */
    public void classifyBoard(List<Mat> cells) {
        if (cells == null || cells.isEmpty()) {
            System.out.println("No board cells found!");
            return;
        }

        System.out.println("Classifying 64 board squares...");

        for (int row = 0; row < 8; row++) {
            StringBuilder line = new StringBuilder();
            for (int col = 0; col < 8; col++) {
                int idx = row * 8 + col;
                String label = classifyCell(cells.get(idx));

                // Represent empty as "."
                if (label.equals("empty")) label = ".";

                // Shorten long names (e.g. "white_pawn" → "P")
                label = simplifyLabel(label);
                line.append(label + " ");
            }
            System.out.println(line);
        }
    }

    private String simplifyLabel(String label) {
        // Convert full labels to single characters
        switch (label.toLowerCase()) {
            case "white_pawn": return "P";
            case "white_rook": return "R";
            case "white_knight": return "N";
            case "white_bishop": return "B";
            case "white_queen": return "Q";
            case "white_king": return "K";
            case "black_pawn": return "p";
            case "black_rook": return "r";
            case "black_knight": return "n";
            case "black_bishop": return "b";
            case "black_queen": return "q";
            case "black_king": return "k";
            default: return ".";
        }
    }
}
