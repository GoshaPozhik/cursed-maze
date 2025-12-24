package ru.itis.jjk.ui.renderer;

import ru.itis.jjk.net.msg.PlayerStateDTO;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

final class DomainRenderer {

    private DomainRenderer() {}

    private static final class Star {
        double x, y, r, a, layer;
        Star(double x, double y, double r, double a, double layer) {
            this.x = x; this.y = y; this.r = r; this.a = a; this.layer = layer;
        }
    }

    static void drawDomains(GraphicsContext gc, double cw, double ch, Map<Integer, PlayerStateDTO> players, long serverTimeMs) {
        if (players == null || players.isEmpty()) return;

        PlayerStateDTO gojo = null, sukuna = null;
        for (PlayerStateDTO p : players.values()) {
            if ("GOJO".equalsIgnoreCase(p.character)) gojo = p;
            if ("SUKUNA".equalsIgnoreCase(p.character)) sukuna = p;
        }
        boolean uv = gojo != null && "UV".equalsIgnoreCase(gojo.activeDomain) && gojo.domainRadius > 1;
        boolean ms = sukuna != null && "MS".equalsIgnoreCase(sukuna.activeDomain) && sukuna.domainRadius > 1;

        double shakeX = 0, shakeY = 0;
        if (uv) {
            long age = serverTimeMs - gojo.domainStartMs;
            if (age >= 0 && age < 260) {
                double t = 1.0 - age / 260.0;
                shakeX += Math.cos((gojo.domainStartMs % 6283) / 1000.0) * 6.0 * t;
                shakeY += Math.sin((gojo.domainStartMs % 6283) / 1000.0) * 6.0 * t;
            }
        }
        if (ms) {
            long age = serverTimeMs - sukuna.domainStartMs;
            if (age >= 0 && age < 260) {
                double t = 1.0 - age / 260.0;
                shakeX += Math.cos((sukuna.domainStartMs % 6283) / 1000.0 + 1.7) * 7.0 * t;
                shakeY += Math.sin((sukuna.domainStartMs % 6283) / 1000.0 + 1.7) * 7.0 * t;
            }
        }

        gc.save();
        gc.translate(shakeX, shakeY);

        if (uv) {
            double cx = gojo.domainCenterX, cy = gojo.domainCenterY, r = gojo.domainRadius;
            long age = serverTimeMs - gojo.domainStartMs;
            drawActivationFlash(gc, cx, cy, r, age);
            gc.save();
            gc.beginPath();
            gc.arc(cx, cy, r, r, 0, 360);
            gc.closePath();
            gc.clip();

            gc.setGlobalAlpha(0.38);
            gc.setFill(Color.rgb(10, 12, 18));
            gc.fillRect(0, 0, cw, ch);

            double drift = (age % 5000) / 5000.0;
            for (Star st : makeStarfield(gojo.domainStartMs ^ 0x9E3779B97F4A7C15L)) {
                double px = (st.x * cw + drift * 40.0 * st.layer) % cw;
                double py = (st.y * ch + drift * 26.0 * st.layer) % ch;
                if (px < 0) px += cw;
                if (py < 0) py += ch;
                gc.setGlobalAlpha(st.a);
                gc.setFill(Color.WHITE);
                gc.fillOval(px, py, st.r, st.r);
            }

            gc.setGlobalAlpha(0.20);
            gc.setStroke(Color.rgb(220, 240, 255));
            for (int i = 0; i < 70; i++) {
                double ang = (i * 11.0 + ((age % 2000) / 2000.0) * 360.0) * Math.PI / 180.0;
                double len = r * (0.25 + 0.75 * ((i % 7) / 6.0));
                gc.setLineWidth(1.0 + (i % 3) * 0.6);
                gc.strokeLine(cx + Math.cos(ang) * (r * 0.15), cy + Math.sin(ang) * (r * 0.15),
                        cx + Math.cos(ang) * len, cy + Math.sin(ang) * len);
            }
            gc.restore();

            gc.setGlobalAlpha(0.85);
            gc.setStroke(Color.rgb(120, 190, 255));
            gc.setLineWidth(3.0 + 3.0 * (0.5 + 0.5 * Math.sin(age / 120.0)));
            gc.strokeOval(cx - r, cy - r, r * 2, r * 2);
        }

        if (ms) {
            double cx = sukuna.domainCenterX, cy = sukuna.domainCenterY, r = sukuna.domainRadius;
            long age = serverTimeMs - sukuna.domainStartMs;
            drawActivationFlash(gc, cx, cy, r, age);
            gc.save();
            gc.beginPath();
            gc.arc(cx, cy, r, r, 0, 360);
            gc.closePath();
            gc.clip();

            gc.setGlobalAlpha(0.24);
            gc.setFill(Color.rgb(25, 0, 0));
            gc.fillRect(0, 0, cw, ch);

            Random rnd = new Random((sukuna.domainStartMs + serverTimeMs / 50) * 31);
            for (int i = 0; i < 26; i++) {
                double rr = 40 + rnd.nextDouble() * 120;
                gc.setGlobalAlpha(0.06 + rnd.nextDouble() * 0.10);
                gc.setFill(Color.rgb(160, 0, 0));
                gc.fillOval(cx + (rnd.nextDouble() * 2 - 1) * r * 0.75 - rr * 0.5,
                        cy + (rnd.nextDouble() * 2 - 1) * r * 0.75 - rr * 0.5,
                        rr, rr);
            }

            gc.setStroke(Color.rgb(240, 240, 240));
            for (int i = 0; i < 110; i++) {
                double x = cx + (rnd.nextDouble() * 2 - 1) * r;
                double y = cy + (rnd.nextDouble() * 2 - 1) * r;
                double ang = rnd.nextDouble() * Math.PI * 2;
                double len = 10 + rnd.nextDouble() * 26;
                gc.setGlobalAlpha(0.08 + rnd.nextDouble() * 0.10);
                gc.setLineWidth(1.0);
                gc.strokeLine(x - Math.cos(ang) * len, y - Math.sin(ang) * len,
                        x + Math.cos(ang) * len, y + Math.sin(ang) * len);
            }
            gc.restore();

            gc.setGlobalAlpha(0.80);
            gc.setStroke(Color.rgb(200, 20, 20));
            gc.setLineWidth(3.2);
            gc.strokeOval(cx - r, cy - r, r * 2, r * 2);
        }

        if (uv && ms) {
            gc.setGlobalAlpha(0.05 + 0.03 * (((double) serverTimeMs / 80) % 2));
            gc.setFill(Color.rgb(250, 250, 255));
            gc.fillRect(0, 0, cw, ch);
        }

        gc.restore();
    }

    private static void drawActivationFlash(GraphicsContext gc, double cx, double cy, double baseR, long ageMs) {
        if (ageMs < 0 || ageMs > 320) return;
        double t = ageMs / 320.0;
        double r = baseR * (0.35 + 1.25 * t);
        gc.save();
        gc.setGlobalAlpha(0.55 * (1.0 - t));
        gc.setLineWidth(4.0 + 10.0 * (1.0 - t));
        gc.setStroke(Color.WHITE);
        gc.strokeOval(cx - r, cy - r, r * 2, r * 2);
        gc.restore();
    }

    private static List<Star> makeStarfield(long seed) {
        Random rnd = new Random(seed);
        ArrayList<Star> stars = new ArrayList<>(240);
        for (int i = 0; i < 240; i++) {
            stars.add(new Star(
                    rnd.nextDouble(),
                    rnd.nextDouble(),
                    0.6 + rnd.nextDouble() * 1.8,
                    0.15 + rnd.nextDouble() * 0.55,
                    0.3 + rnd.nextDouble() * 0.7
            ));
        }
        return stars;
    }
}
