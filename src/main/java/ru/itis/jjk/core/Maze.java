package ru.itis.jjk.core;

import java.util.Random;

public final class Maze { // Не DFS/Прима так как под динамику игры не очень подходит

    public final int w;
    public final int h;
    public final int[][] cells; // [y][x]

    public Maze(int w, int h) {
        this.w = w;
        this.h = h;
        this.cells = new int[h][w];
    }

    public static Maze generate(long seed, int w, int h) {
        Maze m = new Maze(w, h);
        Random rnd = new Random(seed);

        // Внешние стены
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) {
                    m.cells[y][x] = 1;
                } else {
                    boolean structural = (x % 2 == 0 && y % 2 == 0);
                    m.cells[y][x] = structural ? 1 : (rnd.nextDouble() < 0.18 ? 1 : 0);
                }
            }
        }

        carveSpawn(m, 2, 2);
        carveSpawn(m, w - 3, h - 3);

        int x = 2, y = 2;
        int tx = w - 3, ty = h - 3;
        while (x != tx || y != ty) {
            m.cells[y][x] = 0;
            if (x < tx && rnd.nextBoolean()) x++;
            else if (x > tx && rnd.nextBoolean()) x--;
            else if (y < ty) y++;
            else if (y > ty) y--;
        }
        carveSpawn(m, tx, ty);

        return m;
    }

    private static void carveSpawn(Maze m, int cx, int cy) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int x = cx + dx, y = cy + dy;
                if (x > 0 && y > 0 && x < m.w - 1 && y < m.h - 1) {
                    m.cells[y][x] = 0;
                }
            }
        }
    }

    public boolean isWallCell(int cx, int cy) {
        if (cx < 0 || cy < 0 || cx >= w || cy >= h) return true;
        return cells[cy][cx] == 1;
    }
}
