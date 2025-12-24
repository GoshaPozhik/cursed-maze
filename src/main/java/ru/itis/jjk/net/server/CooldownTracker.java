package ru.itis.jjk.net.server;

import ru.itis.jjk.net.msg.EventMsg;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CooldownTracker {

    private final Map<Integer, Long> lastCastBlueMs = new ConcurrentHashMap<>();
    private final Map<Integer, Long> lastCastRedMs = new ConcurrentHashMap<>();
    private final Map<Integer, Long> lastCastPurpleMs = new ConcurrentHashMap<>();
    private final Map<Integer, Long> lastCastDismantleMs = new ConcurrentHashMap<>();
    private final Map<Integer, Long> lastCastDashMs = new ConcurrentHashMap<>();
    private final Map<Integer, Long> lastCastCleaveMs = new ConcurrentHashMap<>();
    private final Map<Integer, Long> lastCastWorldSlashMs = new ConcurrentHashMap<>();
    private final Map<Integer, Long> lastCastFugaMs = new ConcurrentHashMap<>();
    private final Map<Integer, Long> lastCastDomainMs = new ConcurrentHashMap<>();

    public void initPlayer(int id) {
        lastCastBlueMs.put(id, 0L);
        lastCastRedMs.put(id, 0L);
        lastCastPurpleMs.put(id, 0L);
        lastCastDismantleMs.put(id, 0L);
        lastCastDashMs.put(id, 0L);
        lastCastCleaveMs.put(id, 0L);
        lastCastWorldSlashMs.put(id, 0L);
        lastCastFugaMs.put(id, 0L);
        lastCastDomainMs.put(id, 0L);
    }

    public void removePlayer(int id) {
        lastCastBlueMs.remove(id);
        lastCastRedMs.remove(id);
        lastCastPurpleMs.remove(id);
        lastCastDismantleMs.remove(id);
        lastCastDashMs.remove(id);
        lastCastCleaveMs.remove(id);
        lastCastWorldSlashMs.remove(id);
        lastCastFugaMs.remove(id);
        lastCastDomainMs.remove(id);
    }

    public Map<Integer, Long> blue() { return lastCastBlueMs; }
    public Map<Integer, Long> red() { return lastCastRedMs; }
    public Map<Integer, Long> purple() { return lastCastPurpleMs; }
    public Map<Integer, Long> dismantle() { return lastCastDismantleMs; }
    public Map<Integer, Long> dash() { return lastCastDashMs; }
    public Map<Integer, Long> cleave() { return lastCastCleaveMs; }
    public Map<Integer, Long> worldSlash() { return lastCastWorldSlashMs; }
    public Map<Integer, Long> fuga() { return lastCastFugaMs; }
    public Map<Integer, Long> domain() { return lastCastDomainMs; }

    public boolean checkCooldown(
            int id,
            long tMs,
            Map<Integer, Long> lastCastMap,
            long cdUntil,
            int cdMs,
            String name,
            ConnectionManager net
    ) {
        long last = lastCastMap.getOrDefault(id, 0L);
        if (tMs - last < cdMs || tMs < cdUntil) {
            long remain = Math.max(cdMs - (tMs - last), cdUntil - tMs);
            net.broadcast(EventMsg.simple("COOLDOWN", id, name + " ready in " + Math.max(0, remain) + "ms", tMs));
            return true;
        }
        return false;
    }
}
