package ru.itis.jjk.core;

public final class Constants {
    private Constants() {}

    public static final int MAZE_W = 31;   // cells
    public static final int MAZE_H = 21;   // cells
    public static final int CELL = 28;     // pixels per cell

    public static final double PLAYER_RADIUS = 10.0;
    public static final double PLAYER_SPEED = 120.0;

    public static final int MAX_PLAYERS = 2;

    public static final int SERVER_TICK_HZ = 60;
    public static final int STATE_BROADCAST_HZ = 20;
}
