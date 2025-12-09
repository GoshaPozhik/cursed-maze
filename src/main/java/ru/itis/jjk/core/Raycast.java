package ru.itis.jjk.core;

public final class Raycast {
    private Raycast() {}

    public static boolean hasWallBetween(Maze maze, Vec2 a, Vec2 b) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double dist = Math.sqrt(dx*dx + dy*dy);
        if (dist < 1e-6) return false;

        double step = Constants.CELL / 6.0;
        int steps = (int)Math.ceil(dist / step);

        double sx = dx / steps;
        double sy = dy / steps;

        double x = a.x;
        double y = a.y;

        for (int i = 0; i <= steps; i++) {
            int cx = (int)Math.floor(x / Constants.CELL);
            int cy = (int)Math.floor(y / Constants.CELL);
            if (maze.isWallCell(cx, cy)) return true;
            x += sx;
            y += sy;
        }
        return false;
    }
}
