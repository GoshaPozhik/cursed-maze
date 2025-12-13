package ru.itis.jjk.ui;

import ru.itis.jjk.core.Constants;
import ru.itis.jjk.net.msg.PlayerStateDTO;
import ru.itis.jjk.net.msg.ProjectileStateDTO;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import java.util.*;
import java.util.function.BiConsumer;

public final class Renderer {

    private Renderer() {}

    private static final class Star {
        double x, y, r, a, layer;
        Star(double x, double y, double r, double a, double layer){this.x=x;this.y=y;this.r=r;this.a=a;this.layer=layer;}
    }

    private static final Map<Integer, Deque<TrailPoint>> TRAILS = new HashMap<>();
    private record TrailPoint(double x, double y, long tNs) {}

    public static void render(GraphicsContext gc,
                              int[][] mazeCells, int mazeW, int mazeH,
                              Map<Integer, PlayerStateDTO> players,
                              Map<Integer, ProjectileStateDTO> projectiles,
                              int yourId,
                              Map<Integer, Long> wallFx,
                              long serverTimeMs,
                              int comboGlowMask,
                              MatchInfo matchInfo) {

        long nowNs = System.nanoTime();
        double cw = gc.getCanvas().getWidth();
        double ch = gc.getCanvas().getHeight();

        gc.setFill(Color.rgb(235, 235, 235));
        gc.fillRect(0, 0, cw, ch);

        double offX = 0, offY = 0;
        int cell = Constants.CELL;

        if (mazeCells != null) {
            double mw = mazeW * cell;
            double mh = mazeH * cell;
            offX = Math.max(8, (cw - mw) / 2.0);
            offY = Math.max(8, (ch - mh) / 2.0);

            drawMaze(gc, mazeCells, mazeW, mazeH, offX, offY, cell);
        } else {
            gc.setStroke(Color.BLACK);
            gc.strokeText("Waiting for maze...", 20, 20);
        }

        if (wallFx != null && !wallFx.isEmpty() && mazeCells != null) {
            drawWallFx(gc, wallFx, mazeW, offX, offY, cell, nowNs);
        }

        updateTrails(projectiles, nowNs, offX, offY);
        drawTrails(gc, projectiles, nowNs);

        drawDomains(gc, cw, ch, players, serverTimeMs, offX, offY);

        for (ProjectileStateDTO pr : projectiles.values()) {
            double sx = offX + pr.x;
            double sy = offY + pr.y;
            String kind = pr.kind == null ? "" : pr.kind.toUpperCase(Locale.ROOT);
            switch (kind) {
                case "BLUE" -> drawBlueSingularity(gc, sx, sy, pr.radius, nowNs);
                case "BLUE_MAX" -> drawBlueMaximum(gc, sx, sy, pr.radius, pr.angleRad, nowNs);
                case "RED" -> drawRedOrb(gc, sx, sy, pr.radius, nowNs);
                case "DISMANTLE" -> drawDismantleSlash(gc, sx, sy, pr, nowNs);
                case "PURPLE" -> drawPurple(gc, sx, sy, pr.radius, nowNs);
                case "FUGA" -> drawFuga(gc, sx, sy, pr.radius, pr.angleRad, nowNs);
                case "FIRE" -> drawFireZone(gc, sx, sy, pr.radius, nowNs);
                case "EXPLOSION" -> drawExplosion(gc, sx, sy, pr.radius, nowNs);
                case "WCS" -> drawWorldCuttingSlash(gc, sx, sy, pr, nowNs);
                case "CLEAVE_FX" -> drawCleaveFx(gc, sx, sy, pr, nowNs);
                case "DASH_FX" -> drawDashFx(gc, sx, sy, pr, nowNs);
                default -> {
                    if (!kind.endsWith("_FX")) {
                        gc.setStroke(Color.rgb(30, 30, 30));
                        gc.setLineWidth(1.2);
                        gc.strokeOval(sx - pr.radius, sy - pr.radius, pr.radius * 2, pr.radius * 2);
                    }
                }
            }
        }

        for (PlayerStateDTO p : players.values()) {
            drawBlockAvatar(gc, offX + p.x, offY + p.y, p, p.id == yourId, nowNs);
        }

        gc.setGlobalAlpha(1.0);
        drawDomainClashHud(gc, cw, ch, players);
        drawMinimalHud(gc, players, players.get(yourId), serverTimeMs, comboGlowMask, matchInfo);
        drawMatchOverlay(gc, cw, ch, players, serverTimeMs, matchInfo);
    }

    private static void drawMaze(GraphicsContext gc, int[][] maze, int w, int h, double offX, double offY, int cell) {
        int t = 6;
        gc.setFill(Color.rgb(70, 70, 70));

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (maze[y][x] != 1) continue;
                double ox = offX + x * cell;
                double oy = offY + y * cell;
                if (y == 0 || maze[y-1][x] != 1) gc.fillRect(ox, oy, cell, t);
                if (y == h-1 || maze[y+1][x] != 1) gc.fillRect(ox, oy + cell - t, cell, t);
                if (x == 0 || maze[y][x-1] != 1) gc.fillRect(ox, oy, t, cell);
                if (x == w-1 || maze[y][x+1] != 1) gc.fillRect(ox + cell - t, oy, t, cell);
            }
        }
        gc.setFill(Color.rgb(90, 90, 90));
        double mw = w * cell;
        double mh = h * cell;
        gc.fillRect(offX, offY, mw, t);
        gc.fillRect(offX, offY + mh - t, mw, t);
        gc.fillRect(offX, offY, t, mh);
        gc.fillRect(offX + mw - t, offY, t, mh);
    }

    private static void drawWallFx(GraphicsContext gc, Map<Integer, Long> wallFx, int w, double offX, double offY, int cell, long now) {
        gc.setStroke(Color.rgb(165, 70, 255));
        gc.setLineWidth(1.2);
        for (var e : wallFx.entrySet()) {
            double age = (now - e.getValue()) / 1e9;
            if (age < 0 || age > 0.7) continue;
            gc.setGlobalAlpha(clamp(0.70 - age * 1.1, 0, 0.70));
            int idx = e.getKey();
            double px = offX + (idx % w) * cell + cell / 2.0;
            double py = offY + (idx / w) * cell + cell / 2.0;
            int seed = (int)((e.getValue() >> 18) ^ (idx * 1337));
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

    private static void updateTrails(Map<Integer, ProjectileStateDTO> projectiles, long nowNs, double offX, double offY) {
        long keepNs = (long)(0.24 * 1e9);
        for (ProjectileStateDTO pr : projectiles.values()) {
            String kind = pr.kind == null ? "" : pr.kind.toUpperCase(Locale.ROOT);
            if (kind.endsWith("_FX")) continue;
            Deque<TrailPoint> dq = TRAILS.computeIfAbsent(pr.id, k -> new ArrayDeque<>());
            dq.addLast(new TrailPoint(offX + pr.x, offY + pr.y, nowNs));
            while (dq.size() > 28) dq.removeFirst();
            while (!dq.isEmpty() && nowNs - dq.peekFirst().tNs > keepNs) dq.removeFirst();
        }
        TRAILS.keySet().removeIf(id -> !projectiles.containsKey(id));
    }

    private static void drawTrails(GraphicsContext gc, Map<Integer, ProjectileStateDTO> projectiles, long nowNs) {
        for (ProjectileStateDTO pr : projectiles.values()) {
            Deque<TrailPoint> dq = TRAILS.get(pr.id);
            if (dq == null || dq.size() < 2) continue;
            switch (pr.kind == null ? "" : pr.kind.toUpperCase(Locale.ROOT)) {
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
                    gc.setGlobalAlpha(clamp(0.60 - (nowNs - tp.tNs) / 1e9 * 2.3, 0, 0.60));
                    gc.strokeLine(prev.x, prev.y, tp.x, tp.y);
                }
                prev = tp;
            }
            gc.setGlobalAlpha(1.0);
        }
    }

    private static List<Star> makeStarfield(long seed, int n) {
        Random rnd = new Random(seed);
        ArrayList<Star> stars = new ArrayList<>(n);
        for (int i=0;i<n;i++) stars.add(new Star(rnd.nextDouble(), rnd.nextDouble(), 0.6+rnd.nextDouble()*1.8, 0.15+rnd.nextDouble()*0.55, 0.3+rnd.nextDouble()*0.7));
        return stars;
    }

    private static void drawDomains(GraphicsContext gc, double cw, double ch, Map<Integer, PlayerStateDTO> players, long serverTimeMs, double offX, double offY) {
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
            gc.beginPath(); gc.arc(cx, cy, r, r, 0, 360); gc.closePath(); gc.clip();
            gc.setGlobalAlpha(0.38); gc.setFill(Color.rgb(10, 12, 18)); gc.fillRect(0, 0, cw, ch);

            double drift = (age % 5000) / 5000.0;
            for (Star st : makeStarfield(gojo.domainStartMs ^ 0x9E3779B97F4A7C15L, 240)) {
                double px = (st.x * cw + drift * 40.0 * st.layer) % cw;
                double py = (st.y * ch + drift * 26.0 * st.layer) % ch;
                if (px < 0) px += cw; if (py < 0) py += ch;
                gc.setGlobalAlpha(st.a); gc.setFill(Color.WHITE); gc.fillOval(px, py, st.r, st.r);
            }
            gc.setGlobalAlpha(0.20); gc.setStroke(Color.rgb(220, 240, 255));
            for (int i=0;i<70;i++){
                double ang = (i * 11.0 + ((age % 2000) / 2000.0)*360.0) * Math.PI/180.0;
                double len = r * (0.25 + 0.75 * ((i%7)/6.0));
                gc.setLineWidth(1.0 + (i%3)*0.6);
                gc.strokeLine(cx + Math.cos(ang) * (r*0.15), cy + Math.sin(ang) * (r*0.15), cx + Math.cos(ang) * len, cy + Math.sin(ang) * len);
            }
            gc.restore();
            gc.setGlobalAlpha(0.85); gc.setStroke(Color.rgb(120, 190, 255)); gc.setLineWidth(3.0 + 3.0*(0.5 + 0.5*Math.sin(age/120.0)));
            gc.strokeOval(cx - r, cy - r, r*2, r*2);
        }

        if (ms) {
            double cx = sukuna.domainCenterX, cy = sukuna.domainCenterY, r = sukuna.domainRadius;
            long age = serverTimeMs - sukuna.domainStartMs;
            drawActivationFlash(gc, cx, cy, r, age);
            gc.save();
            gc.beginPath(); gc.arc(cx, cy, r, r, 0, 360); gc.closePath(); gc.clip();
            gc.setGlobalAlpha(0.24); gc.setFill(Color.rgb(25, 0, 0)); gc.fillRect(0, 0, cw, ch);
            Random rnd = new Random((sukuna.domainStartMs + serverTimeMs/50) * 31);
            for (int i=0;i<26;i++){
                double rr = 40 + rnd.nextDouble()*120;
                gc.setGlobalAlpha(0.06 + rnd.nextDouble()*0.10); gc.setFill(Color.rgb(160, 0, 0));
                gc.fillOval(cx + (rnd.nextDouble()*2-1)*r*0.75 - rr*0.5, cy + (rnd.nextDouble()*2-1)*r*0.75 - rr*0.5, rr, rr);
            }
            gc.setStroke(Color.rgb(240, 240, 240));
            for (int i=0;i<110;i++){
                double x = cx + (rnd.nextDouble()*2-1)*r, y = cy + (rnd.nextDouble()*2-1)*r;
                double ang = rnd.nextDouble()*Math.PI*2, len = 10 + rnd.nextDouble()*26;
                gc.setGlobalAlpha(0.08 + rnd.nextDouble()*0.10); gc.setLineWidth(1.0);
                gc.strokeLine(x - Math.cos(ang)*len, y - Math.sin(ang)*len, x + Math.cos(ang)*len, y + Math.sin(ang)*len);
            }
            gc.restore();
            gc.setGlobalAlpha(0.80); gc.setStroke(Color.rgb(200, 20, 20)); gc.setLineWidth(3.2); gc.strokeOval(cx - r, cy - r, r*2, r*2);
        }

        if (uv && ms) {
            gc.setGlobalAlpha(0.05 + 0.03 * ((serverTimeMs / 80) % 2));
            gc.setFill(Color.rgb(250, 250, 255));
            gc.fillRect(0, 0, cw, ch);
        }
        gc.restore();
    }

    private static void drawActivationFlash(GraphicsContext gc, double cx, double cy, double baseR, long ageMs) {
        if (ageMs < 0 || ageMs > 320) return;
        double t = ageMs / 320.0, r = baseR * (0.35 + 1.25 * t);
        gc.save();
        gc.setGlobalAlpha(0.55 * (1.0 - t));
        gc.setLineWidth(4.0 + 10.0*(1.0-t));
        gc.setStroke(Color.WHITE);
        gc.strokeOval(cx - r, cy - r, r*2, r*2);
        gc.restore();
    }

    private static void drawBlockAvatar(GraphicsContext gc, double x, double y, PlayerStateDTO p, boolean highlight, long nowNs) {
        boolean isGojo = "GOJO".equalsIgnoreCase(p.character);
        Color bodyColor = isGojo ? Color.rgb(70, 120, 255) : ("SUKUNA".equalsIgnoreCase(p.character) ? Color.rgb(235, 70, 70) : Color.rgb(90, 90, 90));

        if (isGojo && p.cursedEnergy > 0) {
            gc.setStroke(Color.rgb(70, 120, 255)); gc.setLineWidth(2.0); gc.setLineDashes(6, 6); gc.setGlobalAlpha(0.60);
            gc.strokeRect(x - 18, y - 18, 36, 36);
            gc.setGlobalAlpha(1.0); gc.setLineDashes();
        }

        if (highlight) { gc.setStroke(Color.BLACK); gc.setLineWidth(1.5); gc.strokeRect(x - 22, y - 34, 44, 56); }

        gc.setFill(bodyColor); gc.setStroke(Color.rgb(15, 15, 15)); gc.setLineWidth(1.3);
        double bx = x - 7, by = y - 12;
        gc.fillRect(x - 6, y - 26, 12, 12); gc.strokeRect(x - 6, y - 26, 12, 12); // head
        gc.fillRect(bx, by, 14, 16); gc.strokeRect(bx, by, 14, 16); // body
        gc.fillRect(bx - 5, by, 5, 14); gc.strokeRect(bx - 5, by, 5, 14); // l arm
        gc.fillRect(bx + 14, by, 5, 14); gc.strokeRect(bx + 14, by, 5, 14); // r arm
        gc.fillRect(x - 7, y + 4, 6, 14); gc.strokeRect(x - 7, y + 4, 6, 14); // l leg
        gc.fillRect(x + 1, y + 4, 6, 14); gc.strokeRect(x + 1, y + 4, 6, 14); // r leg

        gc.setStroke(Color.rgb(20, 20, 20)); gc.setLineWidth(1.0); gc.strokeText("#" + p.id + " " + p.name, x + 16, y - 28);
        drawBar(gc, x - 19, y - 38, 38, 4, p.hp, p.maxHp, Color.rgb(60, 200, 90));
        drawBar(gc, x - 19, y - 32, 38, 4, p.cursedEnergy, p.maxCursedEnergy, isGojo ? Color.rgb(70, 120, 255) : Color.rgb(235, 70, 70));
        if (p.stunned) gc.strokeText("STUN", x - 14, y + 30);
    }

    private static void drawBar(GraphicsContext gc, double x, double y, double w, double h, int val, int max, Color c) {
        gc.setStroke(Color.rgb(20, 20, 20)); gc.strokeRect(x, y, w, h);
        gc.setFill(c); gc.setGlobalAlpha(0.75);
        gc.fillRect(x, y, w * clamp(max > 0 ? (double)val/max : 0, 0, 1), h);
        gc.setGlobalAlpha(1.0);
    }

    private static void drawBlueSingularity(GraphicsContext gc, double x, double y, double r, long nowNs) {
        RadialGradient g = new RadialGradient(0, 0, x, y, r, false, CycleMethod.NO_CYCLE, new Stop(0, Color.rgb(255,255,255,0.9)), new Stop(1, Color.rgb(60,120,255,0.85)));
        gc.setFill(g); gc.fillOval(x - r, y - r, r*2, r*2);
        int seed = (int)((nowNs >> 17) ^ ((long)(x*19)<<2));
        gc.setGlobalAlpha(0.55); gc.setStroke(Color.rgb(60, 120, 255)); gc.setLineWidth(2.0);
        for(int i=0; i<6; i++) {
            seed = seed * 1664525 + 1013904223;
            double a = ((seed>>>8)%628)/100.0, rr = r*2.2+((seed>>>8)%10);
            gc.strokeLine(x+Math.cos(a)*rr, y+Math.sin(a)*rr, x-Math.cos(a)*rr*0.35, y-Math.sin(a)*rr*0.35);
        }
        gc.setGlobalAlpha(1.0); gc.setLineWidth(1.0);
    }

    private static void drawBlueMaximum(GraphicsContext gc, double x, double y, double r, double ang, long nowNs) {
        gc.setFill(Color.rgb(60, 160, 255)); gc.setGlobalAlpha(0.08); gc.fillOval(x - r*7, y - r*7, r*14, r*14);
        gc.setGlobalAlpha(0.95); gc.fillOval(x - r*1.25, y - r*1.25, r*2.5, r*2.5);
        long t = nowNs / 18_000_000L;
        gc.setLineWidth(2.2); gc.setGlobalAlpha(0.55); gc.setStroke(Color.rgb(60, 160, 255));
        for (int i=0; i<3; i++) {
            double rr = r*(2.2+i*0.9);
            gc.strokeArc(x-rr, y-rr, rr*2, rr*2, Math.toDegrees(((t%628)/100.0 * (1.0+i*0.35)+i*1.7)%(Math.PI*2)), 240, ArcType.OPEN);
        }
        gc.setGlobalAlpha(1.0); gc.setLineWidth(1.0);
    }

    private static void drawRedOrb(GraphicsContext gc, double x, double y, double r, long nowNs) {
        gc.setFill(Color.rgb(255, 80, 80)); gc.setGlobalAlpha(0.95); gc.fillOval(x - r, y - r, r*2, r*2);
        gc.setGlobalAlpha(1.0); gc.setStroke(Color.rgb(255, 80, 80)); gc.setLineWidth(1.1); gc.strokeOval(x - r*1.15, y - r*1.15, r*2.3, r*2.3);
        double phase = ((nowNs / 35_000_000L) % 10) / 10.0;
        gc.setStroke(Color.rgb(255, 110, 110)); gc.setLineWidth(2.0); gc.setGlobalAlpha(0.20 - phase * 0.18);
        double rr = r * (1.6 + phase * 1.2); gc.strokeOval(x - rr, y - rr, rr*2, rr*2);
        gc.setGlobalAlpha(1.0);
    }

    private static void drawPurple(GraphicsContext gc, double x, double y, double r, long nowNs) {
        RadialGradient core = new RadialGradient(0, 0, x, y, r, false, CycleMethod.NO_CYCLE, new Stop(0, Color.rgb(255,255,255,0.95)), new Stop(1, Color.rgb(130,40,220,0.95)));
        gc.setFill(core); gc.fillOval(x - r, y - r, r*2, r*2);
        double rot = ((nowNs / 18_000_000L) % 360);
        gc.setLineWidth(2.6); gc.setGlobalAlpha(0.55);
        gc.setStroke(Color.rgb(90, 175, 255)); gc.strokeArc(x-r*1.2, y-r*1.2, r*2.4, r*2.4, rot, 140, ArcType.OPEN);
        gc.setStroke(Color.rgb(255, 95, 110)); gc.strokeArc(x-r*1.2, y-r*1.2, r*2.4, r*2.4, rot+160, 140, ArcType.OPEN);
        gc.setGlobalAlpha(1.0); gc.setLineWidth(1.0);
    }

    private static void drawWorldCuttingSlash(GraphicsContext gc, double x, double y, ProjectileStateDTO pr, long nowNs) {
        double outerR = pr.radius > 0 ? pr.radius : 420.0;
        double innerR = pr.visualThickness > 0 ? pr.visualThickness : Math.max(outerR - 140.0, 90.0);
        double sweep = pr.visualLength > 0 ? pr.visualLength : Math.toRadians(170.0);
        double start = pr.angleRad - sweep * 0.5;
        double midR = (outerR + innerR) * 0.5;
        double band = Math.max(8.0, outerR - innerR);

        BiConsumer<Double, Double> layer = (w, a) -> {
            gc.setLineWidth(w); gc.setGlobalAlpha(a);
            double px=0, py=0;
            for(int i=0; i<70; i++){
                double ang = start + sweep * (i/69.0);
                double nx=x+Math.cos(ang)*midR, ny=y+Math.sin(ang)*midR;
                if(i>0) gc.strokeLine(px, py, nx, ny);
                px=nx; py=ny;
            }
        };
        gc.setStroke(Color.BLACK); gc.setLineCap(StrokeLineCap.ROUND);
        layer.accept(band+14, 0.2); layer.accept(band+6, 0.38); layer.accept(band, 1.0);

        int seed = (int)((nowNs >> 16) ^ (pr.id * 1337));
        gc.setStroke(Color.WHITE); gc.setLineWidth(2.2); gc.setGlobalAlpha(0.55);
        for(int i=0; i<18; i++){
            seed = seed * 1103515245 + 12345;
            double a = start + sweep * ((seed>>>8)%1000/1000.0);
            double rr = midR + (((seed>>>8)%80-40)/80.0)*(band*0.35);
            double nx = -Math.sin(a), ny = Math.cos(a), L = 18 + ((seed>>>8)%34);
            double px=x+Math.cos(a)*rr, py=y+Math.sin(a)*rr;
            gc.strokeLine(px-nx*L*0.55, py-ny*L*0.55, px+nx*L*0.55, py+ny*L*0.55);
        }
        gc.setGlobalAlpha(1.0); gc.setLineWidth(1.0); gc.setLineCap(StrokeLineCap.BUTT);
    }

    private static void drawDismantleSlash(GraphicsContext gc, double x, double y, ProjectileStateDTO pr, long nowNs) {
        double outerR = pr.radius > 0 ? pr.radius : 54.0;
        double innerR = pr.visualThickness > 0 ? pr.visualThickness : Math.max(outerR - 18.0, 12.0);
        double sweep = pr.visualLength > 0 ? pr.visualLength : Math.toRadians(120.0);
        double start = pr.angleRad - sweep * 0.5;
        double midR = (outerR + innerR) * 0.5;
        double band = Math.max(6.0, outerR - innerR);

        BiConsumer<Double, Double> layer = (w, a) -> {
            gc.setLineWidth(w); gc.setGlobalAlpha(a);
            double px=x+Math.cos(start)*midR, py=y+Math.sin(start)*midR;
            for(int i=1; i<=34; i++){
                double ang = start + sweep * (i/34.0);
                double nx=x+Math.cos(ang)*midR, ny=y+Math.sin(ang)*midR;
                gc.strokeLine(px, py, nx, ny); px=nx; py=ny;
            }
        };
        gc.setStroke(Color.BLACK); gc.setLineCap(StrokeLineCap.ROUND);
        layer.accept(band+10, 0.22); layer.accept(band+4, 0.45); layer.accept(band, 1.0);

        int seed = (int)((nowNs >> 16) ^ (pr.id * 977));
        gc.setStroke(Color.WHITE); gc.setLineWidth(1.8); gc.setGlobalAlpha(0.55);
        for(int i=0; i<10; i++){
            seed = seed * 1103515245 + 12345;
            double a = start + sweep * ((seed>>>8)%1000/1000.0);
            double rr = midR + (band*0.5+4)*0.55;
            double px = x+Math.cos(a)*rr, py = y+Math.sin(a)*rr;
            gc.strokeLine(px, py, px+Math.cos(a)*(10+(seed&7)), py+Math.sin(a)*(10+(seed&7)));
        }
        gc.setGlobalAlpha(1.0); gc.setLineWidth(1.0);
    }

    private static void drawCleaveFx(GraphicsContext gc, double x, double y, ProjectileStateDTO pr, long nowNs) {
        int seed = (int)((nowNs >> 16) ^ (pr.id * 1337));
        gc.setStroke(Color.BLACK); gc.setLineWidth(2.2); gc.setGlobalAlpha(0.95);
        for(int i=0; i<14; i++){
            seed = seed * 1103515245 + 12345;
            double a = pr.angleRad + (((seed>>>8)%900)-450)/900.0*1.6;
            double L = 22 + ((seed>>>8)%48);
            double sx = x + Math.cos(pr.angleRad)*(i%3)*4, sy = y + Math.sin(pr.angleRad)*(i%3)*4;
            gc.strokeLine(sx, sy, sx+Math.cos(a)*L, sy+Math.sin(a)*L);
        }
        gc.setGlobalAlpha(1.0);
    }

    private static void drawDashFx(GraphicsContext gc, double x, double y, ProjectileStateDTO pr, long nowNs) {
        double len = pr.visualLength > 0 ? pr.visualLength : 60.0;
        double x2 = x + Math.cos(pr.angleRad) * len, y2 = y + Math.sin(pr.angleRad) * len;
        gc.setStroke(Color.BLACK);
        gc.setGlobalAlpha(0.18); gc.setLineWidth(12.0); gc.strokeLine(x, y, x2, y2);
        gc.setGlobalAlpha(0.55); gc.setLineWidth(5.0); gc.strokeLine(x, y, x2, y2);
        gc.setGlobalAlpha(1.0); gc.setLineWidth(1.0);
    }

    private static void drawFuga(GraphicsContext gc, double x, double y, double r, double ang, long nowNs) {
        double dx = Math.cos(ang), dy = Math.sin(ang);
        int seed = (int)((nowNs >> 14) ^ ((long)(x*31)<<1));
        gc.setLineCap(StrokeLineCap.ROUND);

        gc.setStroke(Color.BLACK);
        for(int i=0; i<18; i++) {
            seed = seed * 1664525 + 1013904223;
            double t = i/18.0, off = (((seed>>>8)%1000)/1000.0-0.5)*(20+t*34);
            double sx = x-dx*(t*38)-dy*off, sy = y-dy*(t*38)+dx*off;
            gc.setGlobalAlpha(0.06+0.05*(1-t)); gc.setLineWidth(2.6-t*1.5);
            gc.strokeLine(sx, sy, sx-dx*(30+(seed>>>8)%56), sy-dy*(30+(seed>>>8)%56));
        }

        gc.save(); gc.translate(x, y); gc.rotate(Math.toDegrees(ang));
        double hl = 22+r*0.8, hw = 14+r*0.6, sl = 46+r*2.2, sw = 6+r*0.35;
        gc.setGlobalAlpha(0.13); gc.setFill(Color.rgb(255,120,45)); gc.fillOval(-sl*0.75-r*5, -r*5, r*10+sl*0.75, r*10);
        gc.setGlobalAlpha(0.92); gc.setFill(Color.rgb(255,120,45)); gc.fillRoundRect(-sl, -sw/2, sl, sw, sw, sw);
        gc.setGlobalAlpha(0.55); gc.setStroke(Color.rgb(255,230,180)); gc.setLineWidth(1.3); gc.strokeLine(-sl, -sw*0.22, -4, -sw*0.22);
        gc.setGlobalAlpha(0.98); gc.setFill(Color.rgb(255,150,60)); gc.fillPolygon(new double[]{hl,0,0}, new double[]{0,-hw/2,hw/2}, 3);
        gc.setGlobalAlpha(0.78); gc.setFill(Color.rgb(255,245,230)); gc.fillPolygon(new double[]{hl*0.88,hl*0.25,hl*0.25}, new double[]{0,-hw*0.22,hw*0.22}, 3);
        gc.restore();

        gc.setStroke(Color.rgb(255, 90, 20));
        for(int i=0; i<18; i++){
            seed = seed * 1664525 + 1013904223;
            double t = i/18.0, off = (((seed>>>8)%1000)/1000.0-0.5)*(8+t*18);
            double sx = x-dx*(t*34)-dy*off, sy = y-dy*(t*34)+dx*off;
            gc.setGlobalAlpha(clamp(0.6-t*0.6,0,0.6)); gc.setLineWidth(2.4-t*1.6);
            gc.strokeLine(sx, sy, sx-dx*(18+(seed>>>8)%26), sy-dy*(18+(seed>>>8)%26));
        }
        gc.setGlobalAlpha(1.0); gc.setLineWidth(1.0);
    }

    private static void drawExplosion(GraphicsContext gc, double x, double y, double r, long nowNs) {
        gc.setStroke(Color.rgb(255, 150, 60)); gc.setFill(Color.rgb(255, 150, 60));
        gc.setGlobalAlpha(0.12); gc.fillOval(x-r, y-r, r*2, r*2);
        double phase = ((nowNs / 40_000_000L) % 8) / 8.0, rr = r*(0.55+phase*0.55);
        gc.setGlobalAlpha(0.55-phase*0.55); gc.setLineWidth(3.0); gc.strokeOval(x-rr, y-rr, rr*2, rr*2);
        gc.setGlobalAlpha(1.0); gc.setLineWidth(1.0);
    }

    private static void drawFireZone(GraphicsContext gc, double x, double y, double r, long nowNs) {
        gc.setFill(Color.rgb(255, 95, 25)); gc.setStroke(Color.rgb(255, 95, 25));
        gc.setGlobalAlpha(0.10); gc.fillOval(x-r*1.05, y-r*1.05, r*2.1, r*2.1);
        int seed = (int)((nowNs >> 15) ^ ((long)(x*19)<<2));
        for(int i=0; i<48; i++){
            seed = seed * 1664525 + 1013904223;
            double a = ((seed>>>8)%628)/100.0, rr = Math.pow((seed>>>8)%1000/1000.0, 2)*r;
            double px = x+Math.cos(a)*rr, py = y+Math.sin(a)*rr;
            seed = seed * 1664525 + 1013904223;
            double size = 1+(seed>>>8)%4;
            if((seed&1)==0) gc.setFill(Color.rgb(255,120,45)); else gc.setFill(Color.rgb(255,200,120));
            gc.setGlobalAlpha(0.1 + ((seed>>>8)%100)/100.0 * 0.3);
            gc.fillOval(px-size/2, py-size/2, size, size);
        }
        gc.setGlobalAlpha(1.0);
    }

    private static void drawDomainClashHud(GraphicsContext gc, double cw, double ch, Map<Integer, PlayerStateDTO> players) {
        PlayerStateDTO gojo = null, suk = null;
        for (PlayerStateDTO p : players.values()) {
            if ("GOJO".equalsIgnoreCase(p.character)) gojo = p;
            if ("SUKUNA".equalsIgnoreCase(p.character)) suk = p;
        }
        if (gojo == null || suk == null) return;
        boolean gOn = gojo.activeDomain != null && !"NONE".equalsIgnoreCase(gojo.activeDomain);
        boolean sOn = suk.activeDomain != null && !"NONE".equalsIgnoreCase(suk.activeDomain);
        if (!(gOn && sOn)) return;

        double w = Math.min(520, cw * 0.62), x = (cw - w) / 2.0, y = 12, h = 16;
        int g = Math.max(0, gojo.cursedEnergy), s = Math.max(0, suk.cursedEnergy);
        double ratio = g / (double) Math.max(1, g + s);

        gc.setFill(Color.rgb(250, 250, 250)); gc.fillRoundRect(x - 10, y - 6, w + 20, h + 26, 14, 14);
        gc.setStroke(Color.rgb(205, 205, 205)); gc.strokeRoundRect(x - 10, y - 6, w + 20, h + 26, 14, 14);
        gc.setFill(Color.rgb(20, 20, 20)); gc.setFont(Font.font("System", FontWeight.BOLD, 14)); gc.fillText("DOMAIN CLASH", x + 6, y + 14);

        gc.setFill(Color.rgb(235, 235, 235)); gc.fillRoundRect(x, y + 18, w, h, 12, 12);
        gc.setFill(Color.rgb(90, 160, 245)); gc.fillRoundRect(x, y + 18, w * ratio, h, 12, 12);
        gc.setFill(Color.rgb(245, 95, 115)); gc.fillRoundRect(x + w * ratio, y + 18, w * (1 - ratio), h, 12, 12);
        gc.setFill(Color.rgb(80, 80, 80)); gc.setFont(Font.font("System", 12)); gc.fillText("Gojo CE " + g + " vs Sukuna CE " + s, x + 6, y + h + 34);
    }

    private static void drawMinimalHud(GraphicsContext gc, Map<Integer, PlayerStateDTO> players, PlayerStateDTO me, long tMs, int comboGlowMask, MatchInfo matchInfo) {
        if (me == null) return;
        double x = 12, y = 12, w = 310, h = 92;
        String p1Name="P1", p2Name="P2"; int p1Score=0, p2Score=0, round=1, winScore=3;

        if (matchInfo != null) {
            round = Math.max(1, matchInfo.roundNumber()); winScore = Math.max(1, matchInfo.winScore());
            p1Score = matchInfo.p1Score(); p2Score = matchInfo.p2Score();
            PlayerStateDTO p1 = players.get(matchInfo.p1Id()), p2 = players.get(matchInfo.p2Id());
            if(p1!=null) p1Name=p1.name; if(p2!=null) p2Name=p2.name;
        }

        gc.setFill(Color.rgb(250, 250, 250)); gc.fillRoundRect(x, y, w, h, 16, 16);
        gc.setStroke(Color.rgb(205, 205, 205)); gc.strokeRoundRect(x, y, w, h, 16, 16);
        gc.setFill(Color.rgb(20, 20, 20)); gc.setFont(Font.font("System", FontWeight.BOLD, 15)); gc.fillText("Round " + round + "  •  " + p1Score + "–" + p2Score, x + 14, y + 24);
        gc.setFill(Color.rgb(90, 90, 90)); gc.setFont(Font.font("System", 11)); gc.fillText(p1Name + " vs " + p2Name + "  (to " + winScore + ")", x + 14, y + 41);

        if ("GOJO".equalsIgnoreCase(me.character)) {
            double px = x + w - 54, py = y + 14;
            gc.setFill(Color.rgb(240, 240, 240)); gc.fillRoundRect(px, py, 40, 22, 12, 12);
            gc.setStroke(Color.rgb(205, 205, 205)); gc.strokeRoundRect(px, py, 40, 22, 12, 12);
            gc.setFill(me.cursedEnergy > 0 ? Color.rgb(20, 20, 20) : Color.rgb(150, 150, 150)); gc.setFont(Font.font("System", FontWeight.BOLD, 16)); gc.fillText("∞", px + 14, py + 16);
        }

        double barX = x + 14, barW = w - 28;
        drawBar(gc, barX, y + 52, barW, 10, me.hp, me.maxHp, Color.rgb(245, 95, 115));
        drawBar(gc, barX, y + 68, barW, 10, me.cursedEnergy, me.maxCursedEnergy, Color.rgb(90, 160, 245));
        gc.setFill(Color.rgb(20, 20, 20)); gc.setFont(Font.font("System", FontWeight.BOLD, 11));
        gc.fillText("HP " + me.hp + "/" + me.maxHp, barX, y + 50); gc.fillText("CE " + me.cursedEnergy + "/" + me.maxCursedEnergy, barX, y + 66);

        drawAbilityHud(gc, gc.getCanvas().getWidth(), gc.getCanvas().getHeight(), me, tMs);
    }

    private static void drawAbilityHud(GraphicsContext gc, double cw, double ch, PlayerStateDTO me, long tMs) {
        boolean gojo = "GOJO".equalsIgnoreCase(me.character);
        String[] keys = {"Q","E","R","SPACE","SHIFT"};
        String[] names = gojo ? new String[]{"BLUE","RED","BLUE MAX","STEP","DOMAIN"} : new String[]{"DISMANTLE","CLEAVE","FUGA","DASH","DOMAIN"};
        long[] cds = gojo ? new long[]{me.cdBlueUntilMs, me.cdRedUntilMs, me.cdBlueUntilMs, me.cdDashUntilMs, me.cdDomainUntilMs}
                : new long[]{me.cdDismantleUntilMs, me.cdCleaveUntilMs, me.cdFugaUntilMs, me.cdDashUntilMs, me.cdDomainUntilMs};
        int[] costs = gojo ? new int[]{12,14,10,6,60} : new int[]{10,10,18,8,60};

        double slot = 56, pad = 10, totalW = (slot+pad)*5 - pad, x0 = (cw - totalW) * 0.5, y0 = ch - slot - 62;

        gc.setFill(Color.rgb(250, 250, 250)); gc.fillRoundRect(x0 - 10, y0 - 16, totalW + 20, slot + 64, 18, 18);
        gc.setStroke(Color.rgb(205, 205, 205)); gc.strokeRoundRect(x0 - 10, y0 - 16, totalW + 20, slot + 64, 18, 18);

        for (int i = 0; i < 5; i++) {
            double x = x0 + i * (slot + pad);
            gc.setFill(Color.WHITE); gc.fillRoundRect(x, y0, slot, slot, 14, 14);
            gc.setStroke(Color.rgb(205, 205, 205)); gc.strokeRoundRect(x, y0, slot, slot, 14, 14);

            gc.save(); gc.translate(x + slot/2, y0 + slot/2); drawAbilityIcon(gc, names[i]); gc.restore();

            gc.setFill(Color.rgb(20, 20, 20)); gc.setFont(Font.font("System", FontWeight.BOLD, 12)); gc.fillText(keys[i], x + 6, y0 + 14);
            gc.setFill(Color.rgb(90, 90, 90)); gc.setFont(Font.font("System", 11)); gc.fillText(costs[i] + " CE", x + 6, y0 + slot + 14);
            gc.setFill(Color.rgb(70, 70, 70)); gc.fillText(names[i], x + 6, y0 + slot + 30);

            long remain = Math.max(0, cds[i] - tMs);
            if (remain > 0) {
                gc.setFill(Color.rgb(230, 230, 230)); gc.fillRoundRect(x, y0, slot, slot, 14, 14);
                gc.setFill(Color.rgb(20, 20, 20)); gc.setFont(Font.font("System", FontWeight.BOLD, 16));
                gc.fillText(String.format("%.1fs", remain / 1000.0), x + 10, y0 + 34);
            }
        }
        gc.setFill(Color.rgb(90, 90, 90)); gc.setFont(Font.font("System", 12));
        gc.fillText(gojo ? "Combo: Q→E = PURPLE | Q→R = BLUE MAX" : "Combo: SPACE→Q→E = WCS", x0, y0 - 2);
    }

    private static void drawAbilityIcon(GraphicsContext gc, String name) {
        gc.setLineWidth(2.2);
        if (name.contains("BLUE")) {
            double base = name.contains("MAX") ? 16 : 12;
            gc.setFill(Color.rgb(90, 160, 245)); gc.fillOval(-base, -base, base*2, base*2);
            gc.setStroke(Color.rgb(40, 120, 235)); gc.strokeOval(-(base+5), -(base+5)*0.72, (base+5)*2, (base+5)*1.44);
        } else if ("RED".equals(name)) {
            gc.setStroke(Color.rgb(245, 95, 115)); gc.strokeOval(-13, -13, 26, 26);
            gc.strokeLine(0, -13, 0, -23); gc.strokeLine(13, 0, 23, 0); gc.strokeLine(0, 13, 0, 23); gc.strokeLine(-13, 0, -23, 0);
        } else if ("DISMANTLE".equals(name)) {
            gc.setStroke(Color.rgb(30, 30, 30)); gc.setLineWidth(2.6); gc.strokeArc(-18, -18, 36, 36, 215, 110, ArcType.OPEN);
        } else if ("CLEAVE".equals(name)) {
            gc.setStroke(Color.rgb(30, 30, 30)); gc.setLineWidth(2.6); gc.strokeLine(-16, -10, 14, 10); gc.strokeLine(-14, 12, 16, -12);
        } else if ("FUGA".equals(name)) {
            gc.setStroke(Color.rgb(255, 140, 60)); gc.setFill(Color.rgb(255, 200, 140));
            gc.strokeLine(-22, 0, 6, 0); gc.fillPolygon(new double[]{-4,12,-4}, new double[]{-7,0,7}, 3);
            gc.setStroke(Color.rgb(220, 90, 30)); gc.strokePolygon(new double[]{-4,12,-4}, new double[]{-7,0,7}, 3);
        } else if ("DOMAIN".equals(name)) {
            gc.setStroke(Color.rgb(80, 110, 190)); gc.strokeOval(-16, -16, 32, 32); gc.strokeLine(-16, 0, 16, 0); gc.strokeLine(0, -16, 0, 16);
        } else {
            gc.setStroke(Color.rgb(30, 30, 30)); gc.strokeLine(-18, -6, 18, -6); gc.strokeLine(-18, 0, 18, 0); gc.strokeLine(-18, 6, 18, 6);
        }
    }

    private static void drawMatchOverlay(GraphicsContext gc, double cw, double ch, Map<Integer, PlayerStateDTO> players, long tMs, MatchInfo matchInfo) {
        if (matchInfo == null || matchInfo.roundActive()) return;
        gc.setFill(Color.rgb(250, 250, 250)); gc.fillRect(0, 0, cw, ch);
        double w = Math.min(560, cw * 0.78), h = 240, x = (cw - w) / 2.0, y = (ch - h) / 2.0;

        gc.setFill(Color.WHITE); gc.fillRoundRect(x, y, w, h, 18, 18);
        gc.setStroke(Color.rgb(205, 205, 205)); gc.strokeRoundRect(x, y, w, h, 18, 18);

        String winTxt = "Draw";
        if (matchInfo.lastRoundWinnerId() != 0) {
            PlayerStateDTO p = players.get(matchInfo.lastRoundWinnerId());
            winTxt = (p != null ? p.name : "#" + matchInfo.lastRoundWinnerId()) + " wins";
        }

        gc.setFill(Color.rgb(20, 20, 20)); gc.setFont(Font.font("System", FontWeight.BOLD, 28));
        gc.fillText(matchInfo.matchOver() ? "MATCH OVER" : "ROUND OVER", x + 22, y + 48);
        gc.setFont(Font.font("System", FontWeight.BOLD, 20)); gc.fillText(winTxt, x + 22, y + 84);

        gc.setFill(Color.rgb(80, 80, 80)); gc.setFont(Font.font("System", 14));
        if (matchInfo.matchOver()) {
            gc.fillText("Winner: " + (matchInfo.matchWinnerId() == 0 ? "Draw" : players.get(matchInfo.matchWinnerId()).name), x + 22, y + 118);
            gc.fillText("Rematch: press ENTER or click Rematch", x + 22, y + 142);
            gc.fillText("P1 " + (matchInfo.p1RematchReady() ? "READY" : "...") + " | P2 " + (matchInfo.p2RematchReady() ? "READY" : "..."), x + 22, y + 170);
        } else {
            gc.fillText(String.format(Locale.US, "Next round in %.1fs", Math.max(0.0, (matchInfo.nextRoundStartMs() - tMs) / 1000.0)), x + 22, y + 126);
        }
    }

    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
}