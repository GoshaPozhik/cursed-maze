package ru.itis.jjk.ui.renderer;

import ru.itis.jjk.net.msg.ProjectileStateDTO;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

final class TrailRenderer {

    private TrailRenderer() {}

    private static final Map<Integer, Deque<TrailPoint>> TRAILS = new HashMap<>();
    private record TrailPoint(double x, double y, long tNs) {}

    static void updateTrails(Map<Integer, ProjectileStateDTO> projectiles, long nowNs, double offX, double offY) {
        long keepNs = (long) (0.24 * 1e9);
        for (ProjectileStateDTO pr : projectiles.values()) {
            String kind = pr.kind == null ? "" : pr.kind.toUpperCase();
            if (kind.endsWith("_FX")) continue;
            Deque<TrailPoint> dq = TRAILS.computeIfAbsent(pr.id, k -> new ArrayDeque<>());
            dq.addLast(new TrailPoint(offX + pr.x, offY + pr.y, nowNs));
            while (dq.size() > 28) dq.removeFirst();
            while (!dq.isEmpty() && nowNs - dq.peekFirst().tNs > keepNs) dq.removeFirst();
        }
        TRAILS.keySet().removeIf(id -> !projectiles.containsKey(id));
    }

    static void drawTrails(GraphicsContext gc, Map<Integer, ProjectileStateDTO> projectiles, long nowNs) {
        for (ProjectileStateDTO pr : projectiles.values()) {
            Deque<TrailPoint> dq = TRAILS.get(pr.id);
            if (dq == null || dq.size() < 2) continue;

            switch (pr.kind == null ? "" : pr.kind.toUpperCase()) {
                case "BLUE", "BLUE_MAX" -> { gc.setStroke(Color.rgb(60, 120, 255)); gc.setLineWidth(2.6); }
                case "RED" -> { gc.setStroke(Color.rgb(255, 80, 80)); gc.setLineWidth(3.0); }
                case "DISMANTLE" -> { gc.setStroke(Color.rgb(0, 0, 0)); gc.setLineWidth(2.0); }
                case "PURPLE" -> { gc.setStroke(Color.rgb(165, 70, 255)); gc.setLineWidth(4.0); }
                case "WCS" -> { gc.setStroke(Color.rgb(0, 0, 0)); gc.setLineWidth(3.2); }
                case "FUGA" -> { gc.setStroke(Color.rgb(255, 110, 50)); gc.setLineWidth(4.0); }
                default -> { gc.setStroke(Color.rgb(40, 40, 40)); gc.setLineWidth(2.0); }
            }

            TrailPoint prev = null;
            for (TrailPoint tp : dq) {
                if (prev != null) {
                    gc.setGlobalAlpha(RenderUtil.clamp(0.60 - (nowNs - tp.tNs) / 1e9 * 2.3, 0.60));
                    gc.strokeLine(prev.x, prev.y, tp.x, tp.y);
                }
                prev = tp;
            }
            gc.setGlobalAlpha(1.0);
        }
    }
}
