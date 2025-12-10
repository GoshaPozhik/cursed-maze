package ru.itis.jjk.net.msg;

public final class MazeFullMsg implements NetMessage {
    public final MessageType type = MessageType.MAZE_FULL;

    public long serverTimeMs;
    public int mazeW;
    public int mazeH;
    public int[][] mazeCells;

    public MazeFullMsg() {}

    @Override
    public MessageType type() { return type; }
}
