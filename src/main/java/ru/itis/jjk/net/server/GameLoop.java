package ru.itis.jjk.net.server;

import ru.itis.jjk.core.Constants;
import ru.itis.jjk.net.server.systems.*;

public final class GameLoop {

    private final ServerContext ctx;
    private final ConnectionManager net;
    private final MatchController match;

    private final EnergySystem energy;
    private final DomainSystem domains;
    private final PlayerSystem players;
    private final ProjectileSystem projectiles;

    private volatile long lastStateBroadcastNs = 0;

    public GameLoop(ServerContext ctx,
                    ConnectionManager net,
                    MatchController match,
                    EnergySystem energy,
                    DomainSystem domains,
                    PlayerSystem players,
                    ProjectileSystem projectiles) {
        this.ctx = ctx;
        this.net = net;
        this.match = match;
        this.energy = energy;
        this.domains = domains;
        this.players = players;
        this.projectiles = projectiles;
    }

    public void tick() {
        double dt = 1.0 / Constants.SERVER_TICK_HZ;
        long tMs = ctx.nowMs();

        if (!match.roundActive) {
            players.drainOutOfRoundActions(tMs);
        }

        if (!match.roundActive) {
            if (match.matchOver) {
                if (match.p1Id != 0 && match.p2Id != 0 && match.p1RematchReady && match.p2RematchReady) {
                    match.startNewMatch(tMs);
                }
            } else if (match.nextRoundStartMs > 0 && tMs >= match.nextRoundStartMs) {
                match.startNextRound(tMs);
            }
        }

        if (match.roundActive) {
            energy.updateCERegen(dt, tMs);
            domains.updateDomains(dt, tMs);
            players.updatePlayers(dt, tMs);
            projectiles.updateProjectilesAndEnvironment(dt, tMs);
            match.checkRoundWinCondition(tMs);
        }

        long nowNs = System.nanoTime();
        if (nowNs - lastStateBroadcastNs >= 1_000_000_000L / Constants.STATE_BROADCAST_HZ) {
            lastStateBroadcastNs = nowNs;
            net.broadcast(StateFactory.buildState(ctx, match, tMs));
        }
    }
}
