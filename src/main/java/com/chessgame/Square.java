package com.chessgame;

import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

public class Square {

    private int row, col;
    private Color color;
    private StackPane ui;

    public Square(int row, int col, Color color) {
        this.row = row;
        this.col = col;
        this.color = color;
        createUI();
    }

    private void createUI() {
        ui = new StackPane();
        Rectangle rect = new Rectangle(70, 70);
        rect.setFill(color);
        rect.setStroke(Color.BLACK);

        ui.getChildren().add(rect);
    }

    public StackPane getUI() {
        return ui;
    }
}
