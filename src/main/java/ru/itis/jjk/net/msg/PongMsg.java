package ru.itis.jjk.net.msg;

public final class PongMsg implements NetMessage {
    public final MessageType type = MessageType.PONG;
    public long clientTimeMs;
    public long serverTimeMs;

    public PongMsg() {}

    public PongMsg(long clientTimeMs, long serverTimeMs) {
        this.clientTimeMs = clientTimeMs;
        this.serverTimeMs = serverTimeMs;
    }

    @Override
    public MessageType type() { return type; }
}
