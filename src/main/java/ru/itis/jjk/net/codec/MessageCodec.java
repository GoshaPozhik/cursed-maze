package ru.itis.jjk.net.codec;

import ru.itis.jjk.net.msg.*;
import com.google.gson.*;

public final class MessageCodec {

    private final Gson gson;

    public MessageCodec() {
        this.gson = new GsonBuilder().create();
    }

    public byte[] encode(NetMessage msg) {
        String json = gson.toJson(msg);
        return json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public NetMessage decode(byte[] bytes) {
        String json = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

        if (!obj.has("type")) {
            throw new IllegalArgumentException("Message has no 'type': " + json);
        }

        MessageType type = MessageType.valueOf(obj.get("type").getAsString());

        return switch (type) {
            case HELLO -> gson.fromJson(obj, HelloMsg.class);
            case WELCOME -> gson.fromJson(obj, WelcomeMsg.class);
            case INPUT -> gson.fromJson(obj, InputMsg.class);
            case STATE -> gson.fromJson(obj, StateMsg.class);
            case EVENT -> gson.fromJson(obj, EventMsg.class);
            case PING -> gson.fromJson(obj, PingMsg.class);
            case PONG -> gson.fromJson(obj, PongMsg.class);
            case CHAT -> gson.fromJson(obj, ChatMsg.class);
            case MAZE_DELTA -> gson.fromJson(obj, MazeDeltaMsg.class);
            case MAZE_FULL -> gson.fromJson(obj, MazeFullMsg.class);
        };
    }
}
