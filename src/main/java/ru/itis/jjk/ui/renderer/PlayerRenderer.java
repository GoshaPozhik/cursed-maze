package ru.itis.jjk.ui.renderer;

import ru.itis.jjk.net.msg.PlayerStateDTO;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.Map;

final class PlayerRenderer {

    private PlayerRenderer() {}

    static void drawPlayers(GraphicsContext gc,
                            Map<Integer, PlayerStateDTO> players,
                            int yourId,
                            double offX, double offY) {
        if (players == null) return;
        for (PlayerStateDTO p : players.values()) {
            drawBlockAvatar(gc, offX + p.x, offY + p.y, p, p.id == yourId);
        }
    }

    private static void drawBlockAvatar(GraphicsContext gc, double x, double y, PlayerStateDTO p, boolean highlight) {
        boolean isGojo = "GOJO".equalsIgnoreCase(p.character);
        Color bodyColor = isGojo
                ? Color.rgb(70, 120, 255)
                : ("SUKUNA".equalsIgnoreCase(p.character) ? Color.rgb(235, 70, 70) : Color.rgb(90, 90, 90));

        if (isGojo && p.cursedEnergy > 0) {
            gc.setStroke(Color.rgb(70, 120, 255));
            gc.setLineWidth(2.0);
            gc.setLineDashes(6, 6);
            gc.setGlobalAlpha(0.60);
            gc.strokeRect(x - 18, y - 18, 36, 36);
            gc.setGlobalAlpha(1.0);
            gc.setLineDashes();
        }

        if (highlight) {
            gc.setStroke(Color.BLACK);
            gc.setLineWidth(1.5);
            gc.strokeRect(x - 22, y - 34, 44, 56);
        }

        gc.setFill(bodyColor);
        gc.setStroke(Color.rgb(15, 15, 15));
        gc.setLineWidth(1.3);

        double bx = x - 7, by = y - 12;
        gc.fillRect(x - 6, y - 26, 12, 12);
        gc.strokeRect(x - 6, y - 26, 12, 12);
        gc.fillRect(bx, by, 14, 16);
        gc.strokeRect(bx, by, 14, 16);
        gc.fillRect(bx - 5, by, 5, 14);
        gc.strokeRect(bx - 5, by, 5, 14);
        gc.fillRect(bx + 14, by, 5, 14);
        gc.strokeRect(bx + 14, by, 5, 14);
        gc.fillRect(x - 7, y + 4, 6, 14);
        gc.strokeRect(x - 7, y + 4, 6, 14);
        gc.fillRect(x + 1, y + 4, 6, 14);
        gc.strokeRect(x + 1, y + 4, 6, 14);

        gc.setStroke(Color.rgb(20, 20, 20));
        gc.setLineWidth(1.0);
        gc.strokeText("#" + p.id + " " + p.name, x + 16, y - 28);

        RenderUtil.drawBar(gc, x - 19, y - 38, 38, 4, p.hp, p.maxHp, Color.rgb(60, 200, 90));
        RenderUtil.drawBar(gc, x - 19, y - 32, 38, 4, p.cursedEnergy, p.maxCursedEnergy,
                isGojo ? Color.rgb(70, 120, 255) : Color.rgb(235, 70, 70));

        if (p.stunned) gc.strokeText("STUN", x - 14, y + 30);
    }
}
