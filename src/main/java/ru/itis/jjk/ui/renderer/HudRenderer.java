package ru.itis.jjk.ui.renderer;

import ru.itis.jjk.net.msg.PlayerStateDTO;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import ru.itis.jjk.ui.MatchInfo;

import java.util.Locale;
import java.util.Map;

final class HudRenderer {

    private HudRenderer() {}

    static void drawDomainClashHud(GraphicsContext gc, double cw, Map<Integer, PlayerStateDTO> players) {
        if (players == null || players.isEmpty()) return;

        PlayerStateDTO gojo = null, suk = null;
        for (PlayerStateDTO p : players.values()) {
            if ("GOJO".equalsIgnoreCase(p.character)) gojo = p;
            if ("SUKUNA".equalsIgnoreCase(p.character)) suk = p;
        }
        if (gojo == null || suk == null) return;

        boolean gOn = gojo.activeDomain != null && !"NONE".equalsIgnoreCase(gojo.activeDomain);
        boolean sOn = suk.activeDomain != null && !"NONE".equalsIgnoreCase(suk.activeDomain);
        if (!(gOn && sOn)) return;

        double w = Math.min(520, cw * 0.62);
        double x = (cw - w) / 2.0;
        double y = 12;
        double h = 16;

        int g = Math.max(0, gojo.cursedEnergy);
        int s = Math.max(0, suk.cursedEnergy);
        double ratio = g / (double) Math.max(1, g + s);

        gc.setFill(Color.rgb(250, 250, 250));
        gc.fillRoundRect(x - 10, y - 6, w + 20, h + 26, 14, 14);
        gc.setStroke(Color.rgb(205, 205, 205));
        gc.strokeRoundRect(x - 10, y - 6, w + 20, h + 26, 14, 14);

        gc.setFill(Color.rgb(20, 20, 20));
        gc.setFont(Font.font("System", FontWeight.BOLD, 14));
        gc.fillText("DOMAIN CLASH", x + 6, y + 14);

        gc.setFill(Color.rgb(235, 235, 235));
        gc.fillRoundRect(x, y + 18, w, h, 12, 12);
        gc.setFill(Color.rgb(90, 160, 245));
        gc.fillRoundRect(x, y + 18, w * ratio, h, 12, 12);
        gc.setFill(Color.rgb(245, 95, 115));
        gc.fillRoundRect(x + w * ratio, y + 18, w * (1 - ratio), h, 12, 12);

        gc.setFill(Color.rgb(80, 80, 80));
        gc.setFont(Font.font("System", 12));
        gc.fillText("Gojo CE " + g + " vs Sukuna CE " + s, x + 6, y + h + 34);
    }

    static void drawBottomHud(GraphicsContext gc, double cw, double ch, PlayerStateDTO me, long tMs, MatchInfo matchInfo) {
        if (me == null) return;

        final double panelH = 112;
        final double margin = 10;
        final double x = margin;
        final double y = ch - panelH - margin;
        final double w = cw - margin * 2;

        gc.setFill(Color.rgb(250, 250, 250));
        gc.fillRoundRect(x, y, w, panelH, 16, 16);
        gc.setStroke(Color.rgb(205, 205, 205));
        gc.strokeRoundRect(x, y, w, panelH, 16, 16);

        int round = 1, p1Score = 0, p2Score = 0, winScore = 3;
        if (matchInfo != null) {
            round = Math.max(1, matchInfo.roundNumber());
            winScore = Math.max(1, matchInfo.winScore());
            p1Score = matchInfo.p1Score();
            p2Score = matchInfo.p2Score();
        }

        gc.setFill(Color.rgb(20, 20, 20));
        gc.setFont(Font.font("System", FontWeight.BOLD, 14));
        gc.fillText("R" + round + "  " + p1Score + "–" + p2Score + " (to " + winScore + ")", x + 12, y + 20);

        if ("GOJO".equalsIgnoreCase(me.character)) {
            double bx = x + w - 52, by = y + 8;
            gc.setFill(Color.WHITE);
            gc.fillRoundRect(bx, by, 40, 26, 12, 12);
            gc.setStroke(Color.rgb(205, 205, 205));
            gc.strokeRoundRect(bx, by, 40, 26, 12, 12);

            gc.setFill(me.cursedEnergy > 0 ? Color.rgb(20, 20, 20) : Color.rgb(150, 150, 150));
            gc.setFont(Font.font("System", FontWeight.BOLD, 18));
            gc.fillText("∞", bx + 14, by + 19);
        }

        final double slot = 44;
        final double pad = 8;
        final double abilTotalW = (slot + pad) * 5 - pad;
        final double abilX0 = x + w - 12 - abilTotalW;
        final double abilY0 = y + panelH - slot - 8;

        final double barsX = x + 12;
        final double barsW = Math.max(160, abilX0 - barsX - 12);

        final double barH = 8;
        final double hpY = abilY0 + 6;
        final double ceY = abilY0 + 24;

        gc.setFill(Color.rgb(20, 20, 20));
        gc.setFont(Font.font("System", FontWeight.BOLD, 11));
        gc.fillText("HP " + me.hp + "/" + me.maxHp, barsX, hpY - 3);
        RenderUtil.drawBar(gc, barsX, hpY, barsW, barH, me.hp, me.maxHp, Color.rgb(245, 95, 115));

        gc.fillText("CE " + me.cursedEnergy + "/" + me.maxCursedEnergy, barsX, ceY - 3);
        RenderUtil.drawBar(gc, barsX, ceY, barsW, barH, me.cursedEnergy, me.maxCursedEnergy, Color.rgb(90, 160, 245));

        drawAbilityHudAt(gc, abilX0, abilY0, me, tMs);
    }

    private static void drawAbilityHudAt(GraphicsContext gc, double x0, double y0, PlayerStateDTO me, long tMs) {
        boolean gojo = "GOJO".equalsIgnoreCase(me.character);

        String[] keys = {"Q", "E", "R", "SPACE", "SHIFT"};
        String[] names = gojo
                ? new String[]{"BLUE", "RED", "BLUE MAX", "STEP", "DOMAIN"}
                : new String[]{"DISMANTLE", "CLEAVE", "FUGA", "DASH", "DOMAIN"};

        long[] cds = gojo
                ? new long[]{me.cdBlueUntilMs, me.cdRedUntilMs, me.cdBlueUntilMs, me.cdDashUntilMs, me.cdDomainUntilMs}
                : new long[]{me.cdDismantleUntilMs, me.cdCleaveUntilMs, me.cdFugaUntilMs, me.cdDashUntilMs, me.cdDomainUntilMs};

        int[] costs = gojo ? new int[]{12, 14, 10, 6, 60} : new int[]{10, 10, 18, 8, 60};

        final double slot = 44;
        final double pad = 8;

        for (int i = 0; i < 5; i++) {
            double x = x0 + i * (slot + pad);

            gc.setFill(Color.WHITE);
            gc.fillRoundRect(x, y0, slot, slot, 12, 12);
            gc.setStroke(Color.rgb(205, 205, 205));
            gc.strokeRoundRect(x, y0, slot, slot, 12, 12);

            gc.save();
            gc.translate(x + slot / 2.0, y0 + slot / 2.0);
            drawAbilityIcon(gc, names[i]);
            gc.restore();

            gc.setFill(Color.rgb(20, 20, 20));
            gc.setFont(Font.font("System", FontWeight.BOLD, 11));
            gc.fillText(keys[i], x + 6, y0 + 12);

            gc.setFill(Color.rgb(90, 90, 90));
            gc.setFont(Font.font("System", FontWeight.BOLD, 10));
            gc.fillText(Integer.toString(costs[i]), x + 6, y0 + slot - 6);

            long remain = Math.max(0, cds[i] - tMs);
            if (remain > 0) {
                gc.setFill(Color.rgb(235, 235, 235));
                gc.fillRoundRect(x, y0, slot, slot, 12, 12);

                gc.setFill(Color.rgb(20, 20, 20));
                gc.setFont(Font.font("System", FontWeight.BOLD, 12));
                gc.fillText(String.format(Locale.US, "%.1f", remain / 1000.0), x + 8, y0 + 26);
            }
        }
    }

    private static void drawAbilityIcon(GraphicsContext gc, String name) {
        gc.setLineWidth(2.2);
        if (name.contains("BLUE")) {
            double base = name.contains("MAX") ? 16 : 12;
            gc.setFill(Color.rgb(90, 160, 245));
            gc.fillOval(-base, -base, base * 2, base * 2);
            gc.setStroke(Color.rgb(40, 120, 235));
            gc.strokeOval(-(base + 5), -(base + 5) * 0.72, (base + 5) * 2, (base + 5) * 1.44);
        } else if ("RED".equals(name)) {
            gc.setStroke(Color.rgb(245, 95, 115));
            gc.strokeOval(-13, -13, 26, 26);
            gc.strokeLine(0, -13, 0, -23);
            gc.strokeLine(13, 0, 23, 0);
            gc.strokeLine(0, 13, 0, 23);
            gc.strokeLine(-13, 0, -23, 0);
        } else if ("DISMANTLE".equals(name)) {
            gc.setStroke(Color.rgb(30, 30, 30));
            gc.setLineWidth(2.6);
            gc.strokeArc(-18, -18, 36, 36, 215, 110, ArcType.OPEN);
        } else if ("CLEAVE".equals(name)) {
            gc.setStroke(Color.rgb(30, 30, 30));
            gc.setLineWidth(2.6);
            gc.strokeLine(-16, -10, 14, 10);
            gc.strokeLine(-14, 12, 16, -12);
        } else if ("FUGA".equals(name)) {
            gc.setStroke(Color.rgb(255, 140, 60));
            gc.setFill(Color.rgb(255, 200, 140));
            gc.strokeLine(-22, 0, 6, 0);
            gc.fillPolygon(new double[]{-4, 12, -4}, new double[]{-7, 0, 7}, 3);
            gc.setStroke(Color.rgb(220, 90, 30));
            gc.strokePolygon(new double[]{-4, 12, -4}, new double[]{-7, 0, 7}, 3);
        } else if ("DOMAIN".equals(name)) {
            gc.setStroke(Color.rgb(80, 110, 190));
            gc.strokeOval(-16, -16, 32, 32);
            gc.strokeLine(-16, 0, 16, 0);
            gc.strokeLine(0, -16, 0, 16);
        } else {
            gc.setStroke(Color.rgb(30, 30, 30));
            gc.strokeLine(-18, -6, 18, -6);
            gc.strokeLine(-18, 0, 18, 0);
            gc.strokeLine(-18, 6, 18, 6);
        }
    }

    static void drawMatchOverlay(GraphicsContext gc, double cw, double ch, Map<Integer, PlayerStateDTO> players, long tMs, MatchInfo matchInfo) {
        if (matchInfo == null || matchInfo.roundActive()) return;

        gc.setFill(Color.rgb(255, 255, 255, 0.75));
        gc.fillRect(0, 0, cw, ch);

        double w = Math.min(560, cw * 0.78);
        double h = 240;
        double x = (cw - w) / 2.0;
        double y = (ch - h) / 2.0;

        gc.setFill(Color.WHITE);
        gc.fillRoundRect(x, y, w, h, 18, 18);
        gc.setStroke(Color.rgb(205, 205, 205));
        gc.strokeRoundRect(x, y, w, h, 18, 18);

        String winTxt = "Draw";
        if (matchInfo.lastRoundWinnerId() != 0) {
            PlayerStateDTO p = players != null ? players.get(matchInfo.lastRoundWinnerId()) : null;
            winTxt = (p != null ? p.name : "#" + matchInfo.lastRoundWinnerId()) + " wins";
        }

        gc.setFill(Color.rgb(20, 20, 20));
        gc.setFont(Font.font("System", FontWeight.BOLD, 28));
        gc.fillText(matchInfo.matchOver() ? "MATCH OVER" : "ROUND OVER", x + 22, y + 48);
        gc.setFont(Font.font("System", FontWeight.BOLD, 20));
        gc.fillText(winTxt, x + 22, y + 84);

        gc.setFill(Color.rgb(80, 80, 80));
        gc.setFont(Font.font("System", 14));
        if (matchInfo.matchOver()) {
            String winnerName = "Draw";
            if (matchInfo.matchWinnerId() != 0 && players != null) {
                PlayerStateDTO wp = players.get(matchInfo.matchWinnerId());
                if (wp != null) winnerName = wp.name;
                else winnerName = "#" + matchInfo.matchWinnerId();
            }
            gc.fillText("Winner: " + winnerName, x + 22, y + 118);
            gc.fillText("Rematch: press ENTER or click Rematch", x + 22, y + 142);
            gc.fillText("P1 " + (matchInfo.p1RematchReady() ? "READY" : "...") + " | P2 " + (matchInfo.p2RematchReady() ? "READY" : "..."), x + 22, y + 170);
        } else {
            gc.fillText(String.format(Locale.US, "Next round in %.1fs",
                    Math.max(0.0, (matchInfo.nextRoundStartMs() - tMs) / 1000.0)), x + 22, y + 126);
        }
    }
}
