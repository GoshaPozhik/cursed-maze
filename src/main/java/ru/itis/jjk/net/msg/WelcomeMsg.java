package ru.itis.jjk.net.msg;

public final class WelcomeMsg implements NetMessage {
    public final MessageType type = MessageType.WELCOME;

    public int yourPlayerId;
    public long seed;

    public int mazeW;
    public int mazeH;
    public int[][] mazeCells; // [y][x]

    public WelcomeMsg() {}

    @Override
    public MessageType type() { return type; }
}
