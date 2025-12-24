package ru.itis.jjk.ui.renderer;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

final class RenderUtil {

    private RenderUtil() {}

    static double clamp(double v, double max) {
        return Math.max(0, Math.min(max, v));
    }

    static void drawBar(GraphicsContext gc, double x, double y, double w, double h, int val, int max, Color c) {
        gc.setStroke(Color.rgb(20, 20, 20));
        gc.strokeRect(x, y, w, h);
        gc.setFill(c);
        gc.setGlobalAlpha(0.75);
        gc.fillRect(x, y, w * clamp(max > 0 ? (double) val / max : 0, 1), h);
        gc.setGlobalAlpha(1.0);
    }
}
