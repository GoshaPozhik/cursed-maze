package ru.itis.jjk.net.msg;

public final class HelloMsg implements NetMessage {
    public final MessageType type = MessageType.HELLO;

    public String name;
    public String character; // "GOJO" или "SUKUNA"

    public HelloMsg() {}

    public HelloMsg(String name, String character) {
        this.name = name;
        this.character = character;
    }

    @Override
    public MessageType type() { return type; }
}
