package ru.itis.jjk.net.server;

import ru.itis.jjk.core.Player;
import ru.itis.jjk.net.msg.EventMsg;
import ru.itis.jjk.net.msg.MazeFullMsg;

import static ru.itis.jjk.net.server.ServerBalance.*;

public final class MatchController {

    private final ServerContext ctx;
    private final ConnectionManager net;
    private final InputBuffer inputs;

    public volatile int p1Id = 0;
    public volatile int p2Id = 0;

    public volatile int p1Score = 0;
    public volatile int p2Score = 0;
    public volatile int roundNumber = 0;

    public volatile boolean roundActive = false;
    public volatile boolean matchOver = false;

    public volatile int lastRoundWinnerId = 0;
    public volatile int matchWinnerId = 0;
    public volatile long nextRoundStartMs = 0;

    public volatile boolean p1RematchReady = false;
    public volatile boolean p2RematchReady = false;

    public MatchController(ServerContext ctx, ConnectionManager net, InputBuffer inputs) {
        this.ctx = ctx;
        this.net = net;
        this.inputs = inputs;
    }

    public void resetMatchState() {
        p1Score = 0; p2Score = 0;
        roundNumber = 0;
        roundActive = false;
        matchOver = false;
        matchWinnerId = 0;
        lastRoundWinnerId = 0;
        nextRoundStartMs = 0L;
        p1RematchReady = false;
        p2RematchReady = false;
    }

    public void broadcastMazeAndSystemMsg(String msg) {
        MazeFullMsg mf = new MazeFullMsg();
        mf.serverTimeMs = ctx.nowMs();
        mf.mazeW = ctx.gs.maze.w;
        mf.mazeH = ctx.gs.maze.h;
        mf.mazeCells = ctx.gs.maze.cells;
        net.broadcast(mf);
        net.broadcast(EventMsg.simple("SYSTEM", 0, msg, ctx.nowMs()));
    }

    public void maybeStartFirstRound(long tMs) {
        if (roundNumber != 0 || roundActive || matchOver || p1Id == 0 || p2Id == 0 || ctx.gs.players.size() < 2) return;
        Player p1 = ctx.gs.getPlayer(p1Id), p2 = ctx.gs.getPlayer(p2Id);
        if (p1 == null || p2 == null) return;
        if ("UNKNOWN".equalsIgnoreCase(p1.character) || "UNKNOWN".equalsIgnoreCase(p2.character)) return;

        resetMatchState();
        startNextRound(tMs);
    }

    public void startNewMatch(long tMs) {
        resetMatchState();
        net.broadcast(EventMsg.simple("SYSTEM", 0, "Rematch started!", tMs));
        startNextRound(tMs);
    }

    public void startNextRound(long tMs) {
        if (ctx.gs.players.size() < 2 || p1Id == 0 || p2Id == 0 || matchOver) return;
        roundNumber = Math.max(0, roundNumber) + 1;
        roundActive = true;
        lastRoundWinnerId = 0;
        nextRoundStartMs = 0L;

        ServerUtil.copyIntoMaze(ctx.gs.maze.cells, ctx.baseMazeCells);
        broadcastMazeAndSystemMsg("Round " + roundNumber + "!");
        ctx.gs.projectiles.clear();

        for (Player p : ctx.gs.players) {
            ServerUtil.respawn(ctx.gs, p);
            inputs.clearActions(p.id);
        }
    }

    public void checkRoundWinCondition(long tMs) {
        if (!roundActive || ctx.gs.players.size() < 2) return;
        Player p1 = ctx.gs.getPlayer(p1Id), p2 = ctx.gs.getPlayer(p2Id);
        if (p1 == null || p2 == null) return;

        boolean p1Dead = p1.hp <= 0, p2Dead = p2.hp <= 0;
        if (p1Dead || p2Dead) endRound((p1Dead && p2Dead) ? 0 : (p1Dead ? p2.id : p1.id), tMs);
    }

    public void endRound(int winnerId, long tMs) {
        if (!roundActive) return;
        roundActive = false;
        lastRoundWinnerId = winnerId;

        ctx.gs.projectiles.clear();
        for (Player p : ctx.gs.players) {
            p.activeDomain = "NONE";
            p.domainRadius = 0;
            p.stunned = false;
        }

        if (winnerId == p1Id) p1Score++;
        if (winnerId == p2Id) p2Score++;

        if (winnerId != 0 && (p1Score >= WIN_SCORE || p2Score >= WIN_SCORE)) {
            matchOver = true;
            matchWinnerId = winnerId;
            nextRoundStartMs = 0L;
            net.broadcast(EventMsg.simple("MATCH_OVER", winnerId, "Match over! Winner: #" + winnerId, tMs));
        } else {
            nextRoundStartMs = tMs + ROUND_INTERMISSION_MS;
            net.broadcast(EventMsg.simple("ROUND_OVER", winnerId,
                    "Round over! Winner: " + (winnerId == 0 ? "Draw" : "#" + winnerId), tMs));
        }
    }
}
