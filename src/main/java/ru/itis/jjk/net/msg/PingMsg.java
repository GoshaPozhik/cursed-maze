package ru.itis.jjk.net.msg;

public final class PingMsg implements NetMessage {
    public final MessageType type = MessageType.PING;
    public long clientTimeMs;

    public PingMsg() {}

    public PingMsg(long clientTimeMs) { this.clientTimeMs = clientTimeMs; }

    @Override
    public MessageType type() { return type; }
}
