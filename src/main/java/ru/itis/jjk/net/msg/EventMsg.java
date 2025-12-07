package ru.itis.jjk.net.msg;

public final class EventMsg implements NetMessage {
    public final MessageType type = MessageType.EVENT;

    public String eventType;   // "ACTION", "DAMAGE", "SPAWN_PROJECTILE", "DOMAIN_START"
    public int actorId;
    public String payload;
    public long serverTimeMs;

    public EventMsg() {}

    public static EventMsg simple(String eventType, int actorId, String payload, long serverTimeMs) {
        EventMsg e = new EventMsg();
        e.eventType = eventType;
        e.actorId = actorId;
        e.payload = payload;
        e.serverTimeMs = serverTimeMs;
        return e;
    }

    @Override
    public MessageType type() { return type; }
}
