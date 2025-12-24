package ru.itis.jjk.net.server;

import ru.itis.jjk.core.Player;
import ru.itis.jjk.net.msg.*;

public final class ProtocolHandler {

    private final ServerContext ctx;
    private final ConnectionManager net;
    private final SessionManager sessions;
    private final InputBuffer inputs;
    private final MatchController match;

    public ProtocolHandler(ServerContext ctx, ConnectionManager net, SessionManager sessions, InputBuffer inputs, MatchController match) {
        this.ctx = ctx;
        this.net = net;
        this.sessions = sessions;
        this.inputs = inputs;
        this.match = match;
    }

    public void onMessage(ClientPeer peer, NetMessage msg) {
        try {
            switch (msg.type()) {
                case HELLO -> {
                    HelloMsg hm = (HelloMsg) msg;
                    Player p = ctx.gs.getPlayer(peer.id);
                    if (p != null) {
                        p.name = sessions.safeName(hm.name);
                        p.character = sessions.safeCharacter(hm.character);
                        p.cursedEnergy = p.maxCursedEnergy;
                        p.ceRegenCarry = 0.0;
                        p.lastDashCastMs = 0;
                        p.lastDismantleCastMs = 0;
                    }

                    WelcomeMsg wm = new WelcomeMsg();
                    wm.yourPlayerId = peer.id;
                    wm.seed = ctx.seed;
                    wm.mazeW = ctx.gs.maze.w;
                    wm.mazeH = ctx.gs.maze.h;
                    wm.mazeCells = ctx.gs.maze.cells;
                    peer.send(wm);

                    if (p != null) {
                        net.broadcast(EventMsg.simple("SYSTEM", peer.id, p.name + " joined as " + p.character, ctx.nowMs()));
                    }
                    match.maybeStartFirstRound(ctx.nowMs());
                }
                case INPUT -> inputs.applyInput(peer.id, (InputMsg) msg);
                case PING -> peer.send(new PongMsg(((PingMsg) msg).clientTimeMs, ctx.nowMs()));
                case CHAT -> {
                    ((ChatMsg) msg).fromId = peer.id;
                    net.broadcast(msg);
                }
                default -> {}
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
