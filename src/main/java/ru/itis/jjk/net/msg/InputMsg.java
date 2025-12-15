package ru.itis.jjk.net.msg;

import java.util.ArrayList;
import java.util.List;

public final class InputMsg implements NetMessage {
    public final MessageType type = MessageType.INPUT;

    public boolean up;
    public boolean down;
    public boolean left;
    public boolean right;

    public List<String> actionsPressed = new ArrayList<>();

    public long seq;

    public InputMsg() {}

    @Override
    public MessageType type() { return type; }
}
