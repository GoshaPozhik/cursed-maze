package ru.itis.jjk.ui.renderer;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.Map;

final class MazeRenderer {

    private MazeRenderer() {}

    static void drawMaze(GraphicsContext gc, int[][] maze, int w, int h, double offX, double offY, int cell) {
        int t = 6;
        gc.setFill(Color.rgb(70, 70, 70));

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (maze[y][x] != 1) continue;
                double ox = offX + x * cell;
                double oy = offY + y * cell;
                if (y == 0 || maze[y - 1][x] != 1) gc.fillRect(ox, oy, cell, t);
                if (y == h - 1 || maze[y + 1][x] != 1) gc.fillRect(ox, oy + cell - t, cell, t);
                if (x == 0 || maze[y][x - 1] != 1) gc.fillRect(ox, oy, t, cell);
                if (x == w - 1 || maze[y][x + 1] != 1) gc.fillRect(ox + cell - t, oy, t, cell);
            }
        }
        gc.setFill(Color.rgb(90, 90, 90));
        double mw = w * (double) cell;
        double mh = h * (double) cell;
        gc.fillRect(offX, offY, mw, t);
        gc.fillRect(offX, offY + mh - t, mw, t);
        gc.fillRect(offX, offY, t, mh);
        gc.fillRect(offX + mw - t, offY, t, mh);
    }

    static void drawWallFx(GraphicsContext gc, Map<Integer, Long> wallFx, int w, double offX, double offY, int cell, long nowNs) {
        gc.setStroke(Color.rgb(165, 70, 255));
        gc.setLineWidth(1.2);
        for (var e : wallFx.entrySet()) {
            double age = (nowNs - e.getValue()) / 1e9;
            if (age < 0 || age > 0.7) continue;
            gc.setGlobalAlpha(RenderUtil.clamp(0.70 - age * 1.1, 0.70));
            int idx = e.getKey();
            double px = offX + (idx % w) * cell + cell / 2.0;
            double py = offY + ((double) idx / w) * cell + cell / 2.0;
            int seed = (int) ((e.getValue() >> 18) ^ (idx * 1337));
            for (int i = 0; i < 6; i++) {
                seed = seed * 1664525 + 1013904223;
                double ang = ((seed >>> 8) % 628) / 100.0;
                seed = seed * 1664525 + 1013904223;
                double len = 6 + ((seed >>> 8) % 10);
                double sx = px + Math.cos(ang) * (4 + i);
                double sy = py + Math.sin(ang) * (4 + i);
                gc.strokeLine(sx, sy, sx + Math.cos(ang) * len, sy + Math.sin(ang) * len);
            }
        }
        gc.setGlobalAlpha(1.0);
    }
}
