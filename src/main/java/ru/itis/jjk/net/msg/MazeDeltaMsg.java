package ru.itis.jjk.net.msg;

public final class MazeDeltaMsg implements NetMessage {
    public final MessageType type = MessageType.MAZE_DELTA;

    public long serverTimeMs;

    public int[] indices;

    public MazeDeltaMsg() {}

    @Override
    public MessageType type() { return type; }
}
