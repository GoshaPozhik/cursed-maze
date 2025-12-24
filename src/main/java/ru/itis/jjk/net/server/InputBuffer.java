package ru.itis.jjk.net.server;

import ru.itis.jjk.net.msg.InputMsg;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class InputBuffer {

    private final Map<Integer, InputMsg> heldInputs = new ConcurrentHashMap<>();
    private final Map<Integer, ConcurrentLinkedQueue<String>> actionQueues = new ConcurrentHashMap<>();

    public void addPlayer(int id) {
        heldInputs.put(id, new InputMsg());
        actionQueues.put(id, new ConcurrentLinkedQueue<>());
    }

    public void removePlayer(int id) {
        heldInputs.remove(id);
        actionQueues.remove(id);
    }

    public InputMsg held(int id) { return heldInputs.get(id); }

    public void applyInput(int id, InputMsg im) {
        InputMsg held = heldInputs.get(id);
        if (held != null) {
            held.up = im.up;
            held.down = im.down;
            held.left = im.left;
            held.right = im.right;
            held.seq = im.seq;
        }
        if (im.actionsPressed != null && !im.actionsPressed.isEmpty()) {
            var q = actionQueues.get(id);
            if (q != null) q.addAll(im.actionsPressed);
        }
    }

    public String pollAction(int id) {
        var q = actionQueues.get(id);
        return q == null ? null : q.poll();
    }

    public void clearActions(int id) {
        var q = actionQueues.get(id);
        if (q != null) q.clear();
    }
}
