package ru.itis.jjk.core;

public final class Physics {
    private Physics() {}

    public static void movePlayer(GameState gs, Player p, double dt) {
        double nx = p.pos.x + p.vel.x * dt;
        double ny = p.pos.y + p.vel.y * dt;

        Vec2 candidate = new Vec2(nx, p.pos.y);
        if (!collidesWithMaze(gs, candidate, Constants.PLAYER_RADIUS)) {
            p.pos.x = candidate.x;
        } else {
            p.vel.x = 0;
        }

        candidate = new Vec2(p.pos.x, ny);
        if (!collidesWithMaze(gs, candidate, Constants.PLAYER_RADIUS)) {
            p.pos.y = candidate.y;
        } else {
            p.vel.y = 0;
        }

        // Внутри границ
        double wPx = gs.maze.w * Constants.CELL;
        double hPx = gs.maze.h * Constants.CELL;
        p.pos.x = clamp(p.pos.x, Constants.PLAYER_RADIUS, wPx - Constants.PLAYER_RADIUS);
        p.pos.y = clamp(p.pos.y, Constants.PLAYER_RADIUS, hPx - Constants.PLAYER_RADIUS);
    }

    public static boolean collidesWithMaze(GameState gs, Vec2 circleCenter, double r) {
        int cell = Constants.CELL;
        int minCx = (int)Math.floor((circleCenter.x - r) / cell);
        int maxCx = (int)Math.floor((circleCenter.x + r) / cell);
        int minCy = (int)Math.floor((circleCenter.y - r) / cell);
        int maxCy = (int)Math.floor((circleCenter.y + r) / cell);

        for (int cy = minCy; cy <= maxCy; cy++) {
            for (int cx = minCx; cx <= maxCx; cx++) {
                if (gs.maze.isWallCell(cx, cy)) {
                    double rx = cx * cell;
                    double ry = cy * cell;
                    if (circleIntersectsRect(circleCenter.x, circleCenter.y, r, rx, ry, cell, cell)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean circleIntersectsRect(double cx, double cy, double cr, double rx, double ry, double rw, double rh) {
        double closestX = clamp(cx, rx, rx + rw);
        double closestY = clamp(cy, ry, ry + rh);
        double dx = cx - closestX;
        double dy = cy - closestY;
        return (dx*dx + dy*dy) < (cr*cr);
    }

    private static double clamp(double v, double a, double b) {
        return Math.max(a, Math.min(b, v));
    }
}
