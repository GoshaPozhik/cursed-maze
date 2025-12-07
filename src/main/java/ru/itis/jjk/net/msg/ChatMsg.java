package ru.itis.jjk.net.msg;

public final class ChatMsg implements NetMessage {
    public final MessageType type = MessageType.CHAT;

    public int fromId;
    public String text;

    public ChatMsg() {}

    public ChatMsg(int fromId, String text) {
        this.fromId = fromId;
        this.text = text;
    }

    @Override
    public MessageType type() { return type; }
}
