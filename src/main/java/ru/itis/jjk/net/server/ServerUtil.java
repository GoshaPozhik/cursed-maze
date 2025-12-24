package ru.itis.jjk.net.server;

import ru.itis.jjk.core.*;

import java.util.Arrays;

public final class ServerUtil {
    private ServerUtil() {}

    public static int[][] deepCopy(int[][] src) {
        if (src == null) return null;
        int[][] out = new int[src.length][];
        for (int y = 0; y < src.length; y++) out[y] = src[y] == null ? null : Arrays.copyOf(src[y], src[y].length);
        return out;
    }

    public static void copyIntoMaze(int[][] dst, int[][] src) {
        if (dst == null || src == null) return;
        for (int y = 0; y < Math.min(dst.length, src.length); y++) {
            if (dst[y] != null && src[y] != null) {
                System.arraycopy(src[y], 0, dst[y], 0, Math.min(dst[y].length, src[y].length));
            }
        }
    }

    public static void respawn(GameState gs, Player player) {
        double off = Constants.CELL * 2.5;
        if (gs.players.isEmpty() || player.id % 2 == 1) { player.pos.x = off; player.pos.y = off; }
        else { player.pos.x = (Constants.MAZE_W * Constants.CELL) - off; player.pos.y = (Constants.MAZE_H * Constants.CELL) - off; }
        player.vel.x = 0; player.vel.y = 0;
        player.hp = player.maxHp;
        player.cursedEnergy = player.maxCursedEnergy;
        player.ceRegenCarry = 0.0;

        player.cdBlueUntilMs = 0; player.cdRedUntilMs = 0; player.cdPurpleUntilMs = 0;
        player.cdDismantleUntilMs = 0; player.cdDashUntilMs = 0; player.cdCleaveUntilMs = 0;
        player.cdWorldSlashUntilMs = 0; player.cdFugaUntilMs = 0; player.cdDomainUntilMs = 0;

        player.lastBlueCastMs = 0; player.lastDashCastMs = 0; player.lastDismantleCastMs = 0; player.lastCleaveCastMs = 0;

        player.activeDomain = "NONE"; player.domainStartMs = 0; player.domainRadius = 0;
        player.stunned = false;
    }

    public static double normalizeAngle(double a) {
        while (a < 0) a += Math.PI * 2;
        while (a >= Math.PI * 2) a -= Math.PI * 2;
        return a;
    }

    public static boolean angleInArc(double a, double start, double end) {
        return (start <= end) ? (a >= start && a <= end) : (a >= start || a <= end);
    }

    public static boolean segmentIntersectsCircle(Vec2 a, Vec2 b, Vec2 c, double r) {
        double abx = b.x - a.x, aby = b.y - a.y;
        double len2 = abx * abx + aby * aby;
        if (len2 < 1e-9) return ((c.x - a.x) * (c.x - a.x) + (c.y - a.y) * (c.y - a.y)) <= r * r;
        double t = Math.max(0, Math.min(1, ((c.x - a.x) * abx + (c.y - a.y) * aby) / len2));
        double dx = c.x - (a.x + abx * t), dy = c.y - (a.y + aby * t);
        return dx * dx + dy * dy <= r * r;
    }
}
