package ru.itis.jjk.net.server;

import ru.itis.jjk.net.codec.FramedTcp;
import ru.itis.jjk.net.server.systems.*;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public final class GameServer {

    private final int port;
    private volatile boolean running = false;

    private final ScheduledExecutorService tickExec =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "server-tick"));

    private ConnectionManager net;

    public GameServer(int port) { this.port = port; }

    public void start() throws IOException {
        if (running) return;
        running = true;

        ServerContext ctx = new ServerContext();
        FramedTcp framed = new FramedTcp();

        InputBuffer inputs = new InputBuffer();
        CooldownTracker cds = new CooldownTracker();

        AtomicReference<ProtocolHandler> protocolRef = new AtomicReference<>();
        AtomicReference<SessionManager> sessionRef = new AtomicReference<>();

        // create net first, handlers will be set right after creation
        net = new ConnectionManager(
                port,
                framed,
                (peer, msg) -> {
                    ProtocolHandler ph = protocolRef.get();
                    if (ph != null) ph.onMessage(peer, msg);
                },
                peer -> {
                    SessionManager sm = sessionRef.get();
                    if (sm != null) sm.onConnected(peer);
                },
                peer -> {
                    SessionManager sm = sessionRef.get();
                    if (sm != null) sm.onDisconnected(peer);
                }
        );

        MatchController match = new MatchController(ctx, net, inputs);
        SessionManager sessions = new SessionManager(ctx, inputs, cds, match);
        ProtocolHandler protocol = new ProtocolHandler(ctx, net, sessions, inputs, match);

        sessionRef.set(sessions);
        protocolRef.set(protocol);

        EnergySystem energy = new EnergySystem(ctx, net);
        DomainSystem domains = new DomainSystem(ctx, net);
        ProjectileSystem projectiles = new ProjectileSystem(ctx, net, domains, energy);
        ActionSystem actions = new ActionSystem(ctx, net, cds, match, domains, projectiles);
        PlayerSystem players = new PlayerSystem(ctx, inputs, actions, projectiles);

        GameLoop loop = new GameLoop(ctx, net, match, energy, domains, players, projectiles);

        net.start();

        long tickPeriodMs = 1000L / ru.itis.jjk.core.Constants.SERVER_TICK_HZ;
        tickExec.scheduleAtFixedRate(loop::tick, 0, tickPeriodMs, TimeUnit.MILLISECONDS);

        System.out.println("[Server] Started on port " + port + " seed=" + ctx.seed);
    }

    public void shutdown() {
        running = false;
        try { if (net != null) net.shutdown(); } catch (Exception ignored) {}
        tickExec.shutdownNow();
        System.out.println("[Server] Shutdown");
    }
}
