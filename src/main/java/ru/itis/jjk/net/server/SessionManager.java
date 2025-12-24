package ru.itis.jjk.net.server;

import ru.itis.jjk.core.Player;

import java.util.Locale;

public final class SessionManager {

    private final ServerContext ctx;
    private final InputBuffer inputs;
    private final CooldownTracker cds;
    private final MatchController match;

    public SessionManager(ServerContext ctx, InputBuffer inputs, CooldownTracker cds, MatchController match) {
        this.ctx = ctx;
        this.inputs = inputs;
        this.cds = cds;
        this.match = match;
    }

    public void onConnected(ClientPeer peer) {
        if (match.p1Id == 0) match.p1Id = peer.id;
        else if (match.p2Id == 0) match.p2Id = peer.id;

        Player player = new Player(peer.id, "Player" + peer.id, "UNKNOWN");
        ServerUtil.respawn(ctx.gs, player);
        ctx.gs.players.add(player);

        inputs.addPlayer(peer.id);
        cds.initPlayer(peer.id);

        System.out.println("[Server] Client connected id=" + peer.id);
    }

    public void onDisconnected(ClientPeer peer) {
        ctx.gs.players.removeIf(p -> p.id == peer.id);
        inputs.removePlayer(peer.id);
        cds.removePlayer(peer.id);

        if (peer.id == match.p1Id || peer.id == match.p2Id) {
            match.p1Id = 0;
            match.p2Id = 0;

            for (Player p : ctx.gs.players) {
                if (match.p1Id == 0) match.p1Id = p.id;
                else if (match.p2Id == 0) match.p2Id = p.id;
            }

            match.resetMatchState();
            ctx.gs.projectiles.clear();
            ServerUtil.copyIntoMaze(ctx.gs.maze.cells, ctx.baseMazeCells);
            match.broadcastMazeAndSystemMsg("Opponent disconnected. Match reset.");
        }

        System.out.println("[Server] Client disconnected id=" + peer.id);
    }

    public String safeName(String s) {
        if (s == null || s.trim().isEmpty()) return "Player";
        String t = s.trim();
        t = t.length() > 16 ? t.substring(0, 16) : t;
        return t.replaceAll("[^a-zA-Z0-9_\\- ]", "");
    }

    public String safeCharacter(String c) {
        if (c == null) return "GOJO";
        String u = c.trim().toUpperCase(Locale.ROOT);
        return (u.equals("GOJO") || u.equals("SUKUNA")) ? u : "GOJO";
    }
}
