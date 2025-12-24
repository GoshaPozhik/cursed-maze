package ru.itis.jjk.ui.renderer;

import ru.itis.jjk.net.msg.ProjectileStateDTO;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;

import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

final class ProjectileRenderer {

    private ProjectileRenderer() {}

    static void drawProjectiles(GraphicsContext gc,
                                Map<Integer, ProjectileStateDTO> projectiles,
                                double offX, double offY,
                                long nowNs) {

        for (ProjectileStateDTO pr : projectiles.values()) {
            double sx = offX + pr.x;
            double sy = offY + pr.y;
            String kind = pr.kind == null ? "" : pr.kind.toUpperCase(Locale.ROOT);

            switch (kind) {
                case "BLUE" -> drawBlueSingularity(gc, sx, sy, pr.radius, nowNs);
                case "BLUE_MAX" -> drawBlueMaximum(gc, sx, sy, pr.radius, nowNs);
                case "RED" -> drawRedOrb(gc, sx, sy, pr.radius, nowNs);
                case "DISMANTLE" -> drawDismantleSlash(gc, sx, sy, pr, nowNs);
                case "PURPLE" -> drawPurple(gc, sx, sy, pr.radius, nowNs);
                case "FUGA" -> drawFuga(gc, sx, sy, pr.radius, pr.angleRad, nowNs);
                case "FIRE" -> drawFireZone(gc, sx, sy, pr.radius, nowNs);
                case "EXPLOSION" -> drawExplosion(gc, sx, sy, pr.radius, nowNs);
                case "WCS" -> drawWorldCuttingSlash(gc, sx, sy, pr, nowNs);
                case "CLEAVE_FX" -> drawCleaveFx(gc, sx, sy, pr, nowNs);
                case "DASH_FX" -> drawDashFx(gc, sx, sy, pr);
                default -> {
                    if (!kind.endsWith("_FX")) {
                        gc.setStroke(Color.rgb(30, 30, 30));
                        gc.setLineWidth(1.2);
                        gc.strokeOval(sx - pr.radius, sy - pr.radius, pr.radius * 2, pr.radius * 2);
                    }
                }
            }
        }
    }

    private static void drawBlueSingularity(GraphicsContext gc, double x, double y, double r, long nowNs) {
        RadialGradient g = new RadialGradient(0, 0, x, y, r, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(255, 255, 255, 0.9)),
                new Stop(1, Color.rgb(60, 120, 255, 0.85)));
        gc.setFill(g);
        gc.fillOval(x - r, y - r, r * 2, r * 2);

        int seed = (int) ((nowNs >> 17) ^ ((long) (x * 19) << 2));
        gc.setGlobalAlpha(0.55);
        gc.setStroke(Color.rgb(60, 120, 255));
        gc.setLineWidth(2.0);
        for (int i = 0; i < 6; i++) {
            seed = seed * 1664525 + 1013904223;
            double a = ((seed >>> 8) % 628) / 100.0;
            double rr = r * 2.2 + ((seed >>> 8) % 10);
            gc.strokeLine(x + Math.cos(a) * rr, y + Math.sin(a) * rr,
                    x - Math.cos(a) * rr * 0.35, y - Math.sin(a) * rr * 0.35);
        }
        gc.setGlobalAlpha(1.0);
        gc.setLineWidth(1.0);
    }

    private static void drawBlueMaximum(GraphicsContext gc, double x, double y, double r, long nowNs) {
        gc.setFill(Color.rgb(60, 160, 255));
        gc.setGlobalAlpha(0.08);
        gc.fillOval(x - r * 7, y - r * 7, r * 14, r * 14);
        gc.setGlobalAlpha(0.95);
        gc.fillOval(x - r * 1.25, y - r * 1.25, r * 2.5, r * 2.5);

        long t = nowNs / 18_000_000L;
        gc.setLineWidth(2.2);
        gc.setGlobalAlpha(0.55);
        gc.setStroke(Color.rgb(60, 160, 255));
        for (int i = 0; i < 3; i++) {
            double rr = r * (2.2 + i * 0.9);
            gc.strokeArc(x - rr, y - rr, rr * 2, rr * 2,
                    Math.toDegrees(((t % 628) / 100.0 * (1.0 + i * 0.35) + i * 1.7) % (Math.PI * 2)),
                    240, ArcType.OPEN);
        }
        gc.setGlobalAlpha(1.0);
        gc.setLineWidth(1.0);
    }

    private static void drawRedOrb(GraphicsContext gc, double x, double y, double r, long nowNs) {
        gc.setFill(Color.rgb(255, 80, 80));
        gc.setGlobalAlpha(0.95);
        gc.fillOval(x - r, y - r, r * 2, r * 2);

        gc.setGlobalAlpha(1.0);
        gc.setStroke(Color.rgb(255, 80, 80));
        gc.setLineWidth(1.1);
        gc.strokeOval(x - r * 1.15, y - r * 1.15, r * 2.3, r * 2.3);

        double phase = (((double) nowNs / 35_000_000L) % 10) / 10.0;
        gc.setStroke(Color.rgb(255, 110, 110));
        gc.setLineWidth(2.0);
        gc.setGlobalAlpha(0.20 - phase * 0.18);
        double rr = r * (1.6 + phase * 1.2);
        gc.strokeOval(x - rr, y - rr, rr * 2, rr * 2);

        gc.setGlobalAlpha(1.0);
    }

    private static void drawPurple(GraphicsContext gc, double x, double y, double r, long nowNs) {
        RadialGradient core = new RadialGradient(0, 0, x, y, r, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(255, 255, 255, 0.95)),
                new Stop(1, Color.rgb(130, 40, 220, 0.95)));
        gc.setFill(core);
        gc.fillOval(x - r, y - r, r * 2, r * 2);

        double rot = (((double) nowNs / 18_000_000L) % 360);
        gc.setLineWidth(2.6);
        gc.setGlobalAlpha(0.55);
        gc.setStroke(Color.rgb(90, 175, 255));
        gc.strokeArc(x - r * 1.2, y - r * 1.2, r * 2.4, r * 2.4, rot, 140, ArcType.OPEN);
        gc.setStroke(Color.rgb(255, 95, 110));
        gc.strokeArc(x - r * 1.2, y - r * 1.2, r * 2.4, r * 2.4, rot + 160, 140, ArcType.OPEN);

        gc.setGlobalAlpha(1.0);
        gc.setLineWidth(1.0);
    }

    private static void drawWorldCuttingSlash(GraphicsContext gc, double x, double y, ProjectileStateDTO pr, long nowNs) {
        double outerR = pr.radius > 0 ? pr.radius : 420.0;
        double innerR = pr.visualThickness > 0 ? pr.visualThickness : Math.max(outerR - 140.0, 90.0);
        double sweep = pr.visualLength > 0 ? pr.visualLength : Math.toRadians(170.0);
        double start = pr.angleRad - sweep * 0.5;
        double midR = (outerR + innerR) * 0.5;
        double band = Math.max(8.0, outerR - innerR);

        BiConsumer<Double, Double> layer = (w, a) -> {
            gc.setLineWidth(w);
            gc.setGlobalAlpha(a);
            double px = 0, py = 0;
            for (int i = 0; i < 70; i++) {
                double ang = start + sweep * (i / 69.0);
                double nx = x + Math.cos(ang) * midR;
                double ny = y + Math.sin(ang) * midR;
                if (i > 0) gc.strokeLine(px, py, nx, ny);
                px = nx;
                py = ny;
            }
        };

        gc.setStroke(Color.BLACK);
        gc.setLineCap(StrokeLineCap.ROUND);
        layer.accept(band + 14, 0.2);
        layer.accept(band + 6, 0.38);
        layer.accept(band, 1.0);

        int seed = (int) ((nowNs >> 16) ^ (pr.id * 1337));
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(2.2);
        gc.setGlobalAlpha(0.55);
        for (int i = 0; i < 18; i++) {
            seed = seed * 1103515245 + 12345;
            double a = start + sweep * ((seed >>> 8) % 1000 / 1000.0);
            double rr = midR + (((seed >>> 8) % 80 - 40) / 80.0) * (band * 0.35);
            double nx = -Math.sin(a);
            double ny = Math.cos(a);
            double L = 18 + ((seed >>> 8) % 34);
            double px = x + Math.cos(a) * rr;
            double py = y + Math.sin(a) * rr;
            gc.strokeLine(px - nx * L * 0.55, py - ny * L * 0.55, px + nx * L * 0.55, py + ny * L * 0.55);
        }

        gc.setGlobalAlpha(1.0);
        gc.setLineWidth(1.0);
        gc.setLineCap(StrokeLineCap.BUTT);
    }

    private static void drawDismantleSlash(GraphicsContext gc, double x, double y, ProjectileStateDTO pr, long nowNs) {
        double outerR = pr.radius > 0 ? pr.radius : 54.0;
        double innerR = pr.visualThickness > 0 ? pr.visualThickness : Math.max(outerR - 18.0, 12.0);
        double sweep = pr.visualLength > 0 ? pr.visualLength : Math.toRadians(120.0);
        double start = pr.angleRad - sweep * 0.5;
        double midR = (outerR + innerR) * 0.5;
        double band = Math.max(6.0, outerR - innerR);

        BiConsumer<Double, Double> layer = (w, a) -> {
            gc.setLineWidth(w);
            gc.setGlobalAlpha(a);
            double px = x + Math.cos(start) * midR;
            double py = y + Math.sin(start) * midR;
            for (int i = 1; i <= 34; i++) {
                double ang = start + sweep * (i / 34.0);
                double nx = x + Math.cos(ang) * midR;
                double ny = y + Math.sin(ang) * midR;
                gc.strokeLine(px, py, nx, ny);
                px = nx;
                py = ny;
            }
        };

        gc.setStroke(Color.BLACK);
        gc.setLineCap(StrokeLineCap.ROUND);
        layer.accept(band + 10, 0.22);
        layer.accept(band + 4, 0.45);
        layer.accept(band, 1.0);

        int seed = (int) ((nowNs >> 16) ^ (pr.id * 977));
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(1.8);
        gc.setGlobalAlpha(0.55);
        for (int i = 0; i < 10; i++) {
            seed = seed * 1103515245 + 12345;
            double a = start + sweep * ((seed >>> 8) % 1000 / 1000.0);
            double rr = midR + (band * 0.5 + 4) * 0.55;
            double px = x + Math.cos(a) * rr;
            double py = y + Math.sin(a) * rr;
            gc.strokeLine(px, py, px + Math.cos(a) * (10 + (seed & 7)), py + Math.sin(a) * (10 + (seed & 7)));
        }

        gc.setGlobalAlpha(1.0);
        gc.setLineWidth(1.0);
    }

    private static void drawCleaveFx(GraphicsContext gc, double x, double y, ProjectileStateDTO pr, long nowNs) {
        int seed = (int) ((nowNs >> 16) ^ (pr.id * 1337));
        gc.setStroke(Color.BLACK);
        gc.setLineWidth(2.2);
        gc.setGlobalAlpha(0.95);
        for (int i = 0; i < 14; i++) {
            seed = seed * 1103515245 + 12345;
            double a = pr.angleRad + (((seed >>> 8) % 900) - 450) / 900.0 * 1.6;
            double L = 22 + ((seed >>> 8) % 48);
            double sx = x + Math.cos(pr.angleRad) * (i % 3) * 4;
            double sy = y + Math.sin(pr.angleRad) * (i % 3) * 4;
            gc.strokeLine(sx, sy, sx + Math.cos(a) * L, sy + Math.sin(a) * L);
        }
        gc.setGlobalAlpha(1.0);
    }

    private static void drawDashFx(GraphicsContext gc, double x, double y, ProjectileStateDTO pr) {
        double len = pr.visualLength > 0 ? pr.visualLength : 60.0;
        double x2 = x + Math.cos(pr.angleRad) * len;
        double y2 = y + Math.sin(pr.angleRad) * len;
        gc.setStroke(Color.BLACK);
        gc.setGlobalAlpha(0.18);
        gc.setLineWidth(12.0);
        gc.strokeLine(x, y, x2, y2);
        gc.setGlobalAlpha(0.55);
        gc.setLineWidth(5.0);
        gc.strokeLine(x, y, x2, y2);
        gc.setGlobalAlpha(1.0);
        gc.setLineWidth(1.0);
    }

    private static void drawFuga(GraphicsContext gc, double x, double y, double r, double ang, long nowNs) {
        double dx = Math.cos(ang);
        double dy = Math.sin(ang);
        int seed = (int) ((nowNs >> 14) ^ ((long) (x * 31) << 1));
        gc.setLineCap(StrokeLineCap.ROUND);

        gc.setStroke(Color.BLACK);
        for (int i = 0; i < 18; i++) {
            seed = seed * 1664525 + 1013904223;
            double t = i / 18.0;
            double off = (((seed >>> 8) % 1000) / 1000.0 - 0.5) * (20 + t * 34);
            double sx = x - dx * (t * 38) - dy * off;
            double sy = y - dy * (t * 38) + dx * off;
            gc.setGlobalAlpha(0.06 + 0.05 * (1 - t));
            gc.setLineWidth(2.6 - t * 1.5);
            gc.strokeLine(sx, sy, sx - dx * (30 + (seed >>> 8) % 56), sy - dy * (30 + (seed >>> 8) % 56));
        }

        gc.save();
        gc.translate(x, y);
        gc.rotate(Math.toDegrees(ang));
        double hl = 22 + r * 0.8, hw = 14 + r * 0.6, sl = 46 + r * 2.2, sw = 6 + r * 0.35;
        gc.setGlobalAlpha(0.13);
        gc.setFill(Color.rgb(255, 120, 45));
        gc.fillOval(-sl * 0.75 - r * 5, -r * 5, r * 10 + sl * 0.75, r * 10);
        gc.setGlobalAlpha(0.92);
        gc.setFill(Color.rgb(255, 120, 45));
        gc.fillRoundRect(-sl, -sw / 2, sl, sw, sw, sw);
        gc.setGlobalAlpha(0.55);
        gc.setStroke(Color.rgb(255, 230, 180));
        gc.setLineWidth(1.3);
        gc.strokeLine(-sl, -sw * 0.22, -4, -sw * 0.22);
        gc.setGlobalAlpha(0.98);
        gc.setFill(Color.rgb(255, 150, 60));
        gc.fillPolygon(new double[]{hl, 0, 0}, new double[]{0, -hw / 2, hw / 2}, 3);
        gc.setGlobalAlpha(0.78);
        gc.setFill(Color.rgb(255, 245, 230));
        gc.fillPolygon(new double[]{hl * 0.88, hl * 0.25, hl * 0.25}, new double[]{0, -hw * 0.22, hw * 0.22}, 3);
        gc.restore();

        gc.setStroke(Color.rgb(255, 90, 20));
        for (int i = 0; i < 18; i++) {
            seed = seed * 1664525 + 1013904223;
            double t = i / 18.0;
            double off = (((seed >>> 8) % 1000) / 1000.0 - 0.5) * (8 + t * 18);
            double sx = x - dx * (t * 34) - dy * off;
            double sy = y - dy * (t * 34) + dx * off;
            gc.setGlobalAlpha(RenderUtil.clamp(0.6 - t * 0.6, 0.6));
            gc.setLineWidth(2.4 - t * 1.6);
            gc.strokeLine(sx, sy, sx - dx * (18 + (seed >>> 8) % 26), sy - dy * (18 + (seed >>> 8) % 26));
        }

        gc.setGlobalAlpha(1.0);
        gc.setLineWidth(1.0);
        gc.setLineCap(StrokeLineCap.BUTT);
    }

    private static void drawExplosion(GraphicsContext gc, double x, double y, double r, long nowNs) {
        gc.setStroke(Color.rgb(255, 150, 60));
        gc.setFill(Color.rgb(255, 150, 60));
        gc.setGlobalAlpha(0.12);
        gc.fillOval(x - r, y - r, r * 2, r * 2);
        double phase = (((double) nowNs / 40_000_000L) % 8) / 8.0;
        double rr = r * (0.55 + phase * 0.55);
        gc.setGlobalAlpha(0.55 - phase * 0.55);
        gc.setLineWidth(3.0);
        gc.strokeOval(x - rr, y - rr, rr * 2, rr * 2);
        gc.setGlobalAlpha(1.0);
        gc.setLineWidth(1.0);
    }

    private static void drawFireZone(GraphicsContext gc, double x, double y, double r, long nowNs) {
        gc.setFill(Color.rgb(255, 95, 25));
        gc.setStroke(Color.rgb(255, 95, 25));
        gc.setGlobalAlpha(0.10);
        gc.fillOval(x - r * 1.05, y - r * 1.05, r * 2.1, r * 2.1);

        int seed = (int) ((nowNs >> 15) ^ ((long) (x * 19) << 2));
        for (int i = 0; i < 48; i++) {
            seed = seed * 1664525 + 1013904223;
            double a = ((seed >>> 8) % 628) / 100.0;
            double rr = Math.pow((seed >>> 8) % 1000 / 1000.0, 2) * r;
            double px = x + Math.cos(a) * rr;
            double py = y + Math.sin(a) * rr;
            seed = seed * 1664525 + 1013904223;
            double size = 1 + (seed >>> 8) % 4;
            if ((seed & 1) == 0) gc.setFill(Color.rgb(255, 120, 45));
            else gc.setFill(Color.rgb(255, 200, 120));
            gc.setGlobalAlpha(0.1 + ((seed >>> 8) % 100) / 100.0 * 0.3);
            gc.fillOval(px - size / 2, py - size / 2, size, size);
        }
        gc.setGlobalAlpha(1.0);
    }
}
