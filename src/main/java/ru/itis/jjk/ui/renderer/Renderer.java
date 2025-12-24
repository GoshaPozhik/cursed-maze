package ru.itis.jjk.ui.renderer;

import ru.itis.jjk.core.Constants;
import ru.itis.jjk.net.msg.PlayerStateDTO;
import ru.itis.jjk.net.msg.ProjectileStateDTO;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import ru.itis.jjk.ui.MatchInfo;

import java.util.Map;

public final class Renderer {

    private Renderer() {}

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

        // Задник
        gc.setFill(Color.rgb(235, 235, 235));
        gc.fillRect(0, 0, cw, ch);

        double offX = 0, offY = 0;
        int cell = Constants.CELL;

        if (mazeCells != null) {
            final double SAFE_TOP = 8;
            final double SAFE_BOTTOM = 120;
            final double PAD = 8;

            double worldW = mazeW * (double) cell;
            double worldH = mazeH * (double) cell;

            double availW = cw - PAD * 2;
            double availH = ch - SAFE_TOP - SAFE_BOTTOM - PAD * 2;

            double scale = Math.min(1.0, Math.min(availW / worldW, availH / worldH));

            double drawW = worldW * scale;
            double drawH = worldH * scale;

            offX = (cw - drawW) / 2.0;
            offY = SAFE_TOP + (availH - drawH) / 2.0;

            gc.save();
            gc.translate(offX, offY);
            gc.scale(scale, scale);

            offX = 0;
            offY = 0;

            MazeRenderer.drawMaze(gc, mazeCells, mazeW, mazeH, offX, offY, cell);
        } else {
            gc.setStroke(Color.BLACK);
            gc.strokeText("Waiting for maze...", 20, 20);
        }

        if (wallFx != null && !wallFx.isEmpty() && mazeCells != null) {
            MazeRenderer.drawWallFx(gc, wallFx, mazeW, offX, offY, cell, nowNs);
        }

        if (projectiles != null) {
            TrailRenderer.updateTrails(projectiles, nowNs, offX, offY);
            TrailRenderer.drawTrails(gc, projectiles, nowNs);

            DomainRenderer.drawDomains(gc, cw, ch, players, serverTimeMs);
            ProjectileRenderer.drawProjectiles(gc, projectiles, offX, offY, nowNs);
        }

        if (players != null) {
            PlayerRenderer.drawPlayers(gc, players, yourId, offX, offY);
        }

        if (mazeCells != null) {
            gc.restore();
        }

        gc.setGlobalAlpha(1.0);
        HudRenderer.drawDomainClashHud(gc, cw, players);
        HudRenderer.drawBottomHud(gc, cw, ch, players != null ? players.get(yourId) : null, serverTimeMs, matchInfo);
        HudRenderer.drawMatchOverlay(gc, cw, ch, players, serverTimeMs, matchInfo);
    }
}
