package ru.itis.jjk.net.server;

import ru.itis.jjk.core.*;
import ru.itis.jjk.net.codec.FramedTcp;
import ru.itis.jjk.net.msg.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class GameServer {

    private static final double INFINITY_RADIUS = 18.0;
    private static final int CE_REGEN_PER_SEC = 10;
    private static final int CE_LOSS_ON_HIT = 10;
    private static final int CE_LOSS_ON_CLEAVE_BLOCK = 8;

    private static final int CE_COST_BLUE = 12;
    private static final int CE_COST_BLUE_MAX_EXTRA = 10;
    private static final int BLUE_MAX_WINDOW_MS = 900;
    private static final int CE_COST_RED = 14;
    private static final int CE_COST_PURPLE = 24;
    private static final int CE_COST_DISMANTLE = 10;

    private static final int CD_BLUE_MS = 900;
    private static final int CD_RED_MS = 1100;
    private static final int CD_PURPLE_MS = 2600;
    private static final int CD_DISMANTLE_MS = 800;

    private static final int CE_COST_DASH = 8;
    private static final int CE_COST_DASH_GOJO = 6;
    private static final int CE_COST_CLEAVE = 10;
    private static final int CE_COST_WORLD_SLASH = 32;
    private static final int CE_COST_FUGA = 18;
    private static final int CE_COST_DOMAIN = 60;

    private static final int CD_DASH_MS = 900;
    private static final int CD_DASH_GOJO_MS = 850;
    private static final int CD_CLEAVE_MS = 900;
    private static final int CD_WORLD_SLASH_MS = 5200;
    private static final int CD_FUGA_MS = 2200;
    private static final int CD_DOMAIN_MS = 12000;

    private static final int WORLD_SLASH_COMBO_WINDOW_MS = 900;
    private static final double DASH_DISTANCE = 110.0;
    private static final double DASH_DISTANCE_GOJO = 90.0;
    private static final double DASH_STEP = 6.0;

    private static final double CLEAVE_RANGE = 44.0;
    private static final double CLEAVE_FOV_DEG = 70.0;

    private static final int PURPLE_COMBO_WINDOW_MS = 300;

    private static final double FUGA_SPEED = 230.0;
    private static final double FUGA_RADIUS = 7.0;
    private static final double FUGA_TTL_SEC = 3.0;

    private static final double FUGA_EXPLOSION_RADIUS = 86.0;
    private static final int FUGA_EXPLOSION_DAMAGE = 22;

    private static final double FIRE_ZONE_RADIUS = 96.0;
    private static final double FIRE_ZONE_TTL_SEC = 2.8;
    private static final long FIRE_TICK_MS = 500;
    private static final int FIRE_DOT_DAMAGE = 6;
    private static final int FIRE_DOT_CE_DRAIN = 5;

    private static final double DOMAIN_UV_MAX_RADIUS = 220.0;
    private static final double DOMAIN_UV_EXPAND_MS = 650.0;
    private static final double DOMAIN_MS_RADIUS = 420.0;

    private static final double DOMAIN_CE_DRAIN_PER_SEC = 9.0;
    private static final double DOMAIN_CLASH_EXTRA_DRAIN_PER_SEC = 10.0;
    private static final long MS_SURE_HIT_TICK_MS = 500;
    private static final int MS_SURE_HIT_DAMAGE = 7;

    private static final int WIN_SCORE = 3;
    private static final long ROUND_INTERMISSION_MS = 2500;

    private static final double BLUE_EFFECT_RADIUS = 140.0;
    private static final double BLUE_PULL_STRENGTH = 480.0;

    private final int port;
    private volatile boolean running = false;

    private ServerSocket serverSocket;
    private final ExecutorService acceptPool = Executors.newSingleThreadExecutor(r -> new Thread(r, "server-accept"));
    private final ScheduledExecutorService tickExec = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "server-tick"));

    private final List<ClientPeer> peers = new CopyOnWriteArrayList<>();
    private final AtomicInteger nextId = new AtomicInteger(1);

    private final FramedTcp framed = new FramedTcp();

    private final long seed = new Random().nextLong();
    private final GameState gs = new GameState(seed, Maze.generate(seed, Constants.MAZE_W, Constants.MAZE_H));
    private final int[][] baseMazeCells = deepCopy(gs.maze.cells);
    private final AbilitySystem abilities = new AbilitySystem();
    private final AtomicInteger projectileIdGen = new AtomicInteger(1000);

    private volatile int p1Id = 0;
    private volatile int p2Id = 0;
    private volatile int p1Score = 0;
    private volatile int p2Score = 0;
    private volatile int roundNumber = 0;
    private volatile boolean roundActive = false;
    private volatile boolean matchOver = false;
    private volatile int lastRoundWinnerId = 0;
    private volatile int matchWinnerId = 0;
    private volatile long nextRoundStartMs = 0;
    private volatile boolean p1RematchReady = false;
    private volatile boolean p2RematchReady = false;

    private final Map<Integer, InputMsg> heldInputs = new ConcurrentHashMap<>();
    private final Map<Integer, ConcurrentLinkedQueue<String>> actionQueues = new ConcurrentHashMap<>();

    private volatile long lastStateBroadcastNs = 0;

    private final Map<Integer, Long> lastCastBlueMs = new ConcurrentHashMap<>();
    private final Map<Integer, Long> lastCastRedMs = new ConcurrentHashMap<>();
    private final Map<Integer, Long> lastCastPurpleMs = new ConcurrentHashMap<>();
    private final Map<Integer, Long> lastCastDismantleMs = new ConcurrentHashMap<>();
    private final Map<Integer, Long> lastCastDashMs = new ConcurrentHashMap<>();
    private final Map<Integer, Long> lastCastCleaveMs = new ConcurrentHashMap<>();
    private final Map<Integer, Long> lastCastWorldSlashMs = new ConcurrentHashMap<>();
    private final Map<Integer, Long> lastCastFugaMs = new ConcurrentHashMap<>();
    private final Map<Integer, Long> lastCastDomainMs = new ConcurrentHashMap<>();

    public GameServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        if (running) return;
        running = true;
        serverSocket = new ServerSocket(port);
        acceptPool.submit(this::acceptLoop);
        long tickPeriodMs = 1000L / Constants.SERVER_TICK_HZ;
        tickExec.scheduleAtFixedRate(this::tick, 0, tickPeriodMs, TimeUnit.MILLISECONDS);
        System.out.println("[Server] Started on port " + port + " seed=" + seed);
    }

    public void shutdown() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        for (ClientPeer p : peers) p.close();
        acceptPool.shutdownNow();
        tickExec.shutdownNow();
        System.out.println("[Server] Shutdown");
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);

                if (peers.size() >= Constants.MAX_PLAYERS) {
                    socket.close();
                    continue;
                }

                int id = nextId.getAndIncrement();
                ClientPeer peer = new ClientPeer(id, socket, framed, this::onMessageFromPeer, this::onPeerClosed);
                peers.add(peer);

                if (p1Id == 0) p1Id = id;
                else if (p2Id == 0) p2Id = id;

                Player player = new Player(id, "Player" + id, "UNKNOWN");
                respawn(player);
                gs.players.add(player);

                heldInputs.put(id, new InputMsg());
                actionQueues.put(id, new ConcurrentLinkedQueue<>());
                initCooldowns(id);

                peer.start();
                System.out.println("[Server] Client connected id=" + id + " from " + socket.getRemoteSocketAddress());
            } catch (IOException e) {
                if (running) e.printStackTrace();
            }
        }
    }

    private void onPeerClosed(ClientPeer peer) {
        peers.remove(peer);
        gs.players.removeIf(p -> p.id == peer.id);
        heldInputs.remove(peer.id);
        actionQueues.remove(peer.id);
        removeCooldowns(peer.id);

        if (peer.id == p1Id || peer.id == p2Id) {
            p1Id = 0;
            p2Id = 0;
            for (Player p : gs.players) {
                if (p1Id == 0) p1Id = p.id;
                else if (p2Id == 0) p2Id = p.id;
            }
            resetMatchState();
            gs.projectiles.clear();
            copyIntoMaze(gs.maze.cells, baseMazeCells);
            broadcastMazeAndSystemMsg("Opponent disconnected. Match reset.");
        }
        System.out.println("[Server] Client disconnected id=" + peer.id);
    }

    private void initCooldowns(int id) {
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

    private void removeCooldowns(int id) {
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

    private void resetMatchState() {
        p1Score = 0;
        p2Score = 0;
        roundNumber = 0;
        roundActive = false;
        matchOver = false;
        matchWinnerId = 0;
        lastRoundWinnerId = 0;
        nextRoundStartMs = 0L;
        p1RematchReady = false;
        p2RematchReady = false;
    }

    private void broadcastMazeAndSystemMsg(String msg) {
        MazeFullMsg mf = new MazeFullMsg();
        mf.serverTimeMs = nowMs();
        mf.mazeW = gs.maze.w;
        mf.mazeH = gs.maze.h;
        mf.mazeCells = gs.maze.cells;
        broadcast(mf);
        broadcast(EventMsg.simple("SYSTEM", 0, msg, nowMs()));
    }

    private void onMessageFromPeer(ClientPeer peer, NetMessage msg) {
        try {
            switch (msg.type()) {
                case HELLO -> {
                    HelloMsg hm = (HelloMsg) msg;
                    Player p = gs.getPlayer(peer.id);
                    if (p != null) {
                        p.name = safeName(hm.name);
                        p.character = safeCharacter(hm.character);
                        p.cursedEnergy = p.maxCursedEnergy;
                        p.ceRegenCarry = 0.0;
                        p.lastDashCastMs = 0;
                        p.lastDismantleCastMs = 0;
                    }
                    WelcomeMsg wm = new WelcomeMsg();
                    wm.yourPlayerId = peer.id;
                    wm.seed = seed;
                    wm.mazeW = gs.maze.w;
                    wm.mazeH = gs.maze.h;
                    wm.mazeCells = gs.maze.cells;
                    peer.send(wm);
                    assert p != null;
                    broadcast(EventMsg.simple("SYSTEM", peer.id, p.name + " joined as " + p.character, nowMs()));
                    maybeStartFirstRound(nowMs());
                }
                case INPUT -> {
                    InputMsg im = (InputMsg) msg;
                    InputMsg held = heldInputs.get(peer.id);
                    if (held != null) {
                        held.up = im.up;
                        held.down = im.down;
                        held.left = im.left;
                        held.right = im.right;
                        held.seq = im.seq;
                    }
                    if (im.actionsPressed != null && !im.actionsPressed.isEmpty()) {
                        var q = actionQueues.get(peer.id);
                        if (q != null) q.addAll(im.actionsPressed);
                    }
                }
                case PING -> peer.send(new PongMsg(((PingMsg) msg).clientTimeMs, nowMs()));
                case CHAT -> {
                    ((ChatMsg) msg).fromId = peer.id;
                    broadcast(msg);
                }
                default -> {}
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void tick() {
        if (!running) return;
        double dt = 1.0 / Constants.SERVER_TICK_HZ;
        long tMs = nowMs();

        if (!roundActive) {
            drainOutOfRoundActions(tMs);
        }

        if (!roundActive) {
            if (matchOver) {
                if (p1Id != 0 && p2Id != 0 && p1RematchReady && p2RematchReady) startNewMatch(tMs);
            } else if (nextRoundStartMs > 0 && tMs >= nextRoundStartMs) {
                startNextRound(tMs);
            }
        }

        if (roundActive) {
            updateCERegen(dt, tMs);
            updateDomains(dt, tMs);
            updatePlayers(dt, tMs);
            updateProjectilesAndEnvironment(dt, tMs);
            checkRoundWinCondition(tMs);
        }

        long nowNs = System.nanoTime();
        if (nowNs - lastStateBroadcastNs >= 1_000_000_000L / Constants.STATE_BROADCAST_HZ) {
            lastStateBroadcastNs = nowNs;
            broadcast(buildState(tMs));
        }
    }

    private void drainOutOfRoundActions(long tMs) {
        for (Player p : gs.players) {
            var q = actionQueues.get(p.id);
            if (q == null) continue;
            String a;
            while ((a = q.poll()) != null) {
                String u = a.trim().toUpperCase(Locale.ROOT);
                if ("REMATCH_READY".equals(u)) {
                    handleActionWithCooldownAndCost(p, u, tMs);
                }
            }
        }
    }

    private void updateCERegen(double dt, long tMs) {
        for (Player p : gs.players) {
            if (p.cursedEnergy < p.maxCursedEnergy) {
                p.ceRegenCarry += CE_REGEN_PER_SEC * dt;
                int add = (int)Math.floor(p.ceRegenCarry);
                if (add > 0) {
                    int before = p.cursedEnergy;
                    p.cursedEnergy = Math.min(p.maxCursedEnergy, p.cursedEnergy + add);
                    p.ceRegenCarry -= add;
                    if (p.isGojo() && before == 0 && p.cursedEnergy > 0) {
                        broadcast(EventMsg.simple("INFINITY_UP", p.id, "Infinity restored", tMs));
                    }
                }
            } else {
                p.ceRegenCarry = 0.0;
            }
        }
    }

    private void updatePlayers(double dt, long tMs) {
        for (Player p : gs.players) {
            InputMsg in = heldInputs.get(p.id);
            if (in == null) continue;

            if (!p.stunned) {
                double dx = (in.right ? 1 : 0) - (in.left ? 1 : 0);
                double dy = (in.down ? 1 : 0) - (in.up ? 1 : 0);
                Vec2 dir = new Vec2(dx, dy);
                if (dir.len() > 0.001) {
                    dir.norm();
                    p.vel.x = dir.x * Constants.PLAYER_SPEED;
                    p.vel.y = dir.y * Constants.PLAYER_SPEED;
                    p.facingAngleRad = Math.atan2(dir.y, dir.x);
                } else {
                    p.vel.x = 0; p.vel.y = 0;
                }
            } else {
                p.vel.x = 0; p.vel.y = 0;
            }

            applyBluePullToPlayerVel(p, dt);
            Physics.movePlayer(gs, p, dt);

            var q = actionQueues.get(p.id);
            if (q != null) {
                String a;
                while ((a = q.poll()) != null) handleActionWithCooldownAndCost(p, a, tMs);
            }
        }
    }

    private void updateProjectilesAndEnvironment(double dt, long tMs) {
        IntList destroyed = new IntList(128);
        updateProjectiles(dt, tMs, destroyed);
        if (destroyed.size > 0) {
            MazeDeltaMsg md = new MazeDeltaMsg();
            md.serverTimeMs = tMs;
            md.indices = destroyed.toArray();
            broadcast(md);
            broadcast(EventMsg.simple("MAZE", 0, "Destroyed walls: " + destroyed.size, tMs));
        }
    }

    private void handleActionWithCooldownAndCost(Player actor, String actionRaw, long tMs) {
        if (actionRaw == null) return;
        String a = actionRaw.trim().toUpperCase(Locale.ROOT);
        if (a.isEmpty()) return;
        if (actor.stunned && !a.startsWith("DOMAIN")) return;
        if (!roundActive && !"REMATCH_READY".equals(a)) return;

        switch (a) {
            case "REMATCH_READY" -> {
                if (!matchOver) return;
                if (actor.id == p1Id) p1RematchReady = true;
                if (actor.id == p2Id) p2RematchReady = true;
                broadcast(EventMsg.simple("REMATCH", actor.id, actor.name + " is ready", tMs));
            }
            case "BLUE" -> {
                if (!actor.isGojo()) return;
                Projectile ownedBlue = findOwnedBlue(actor.id);
                if (ownedBlue != null && "BLUE".equalsIgnoreCase(ownedBlue.kind)
                        && actor.lastBlueCastMs > 0 && (tMs - actor.lastBlueCastMs) <= BLUE_MAX_WINDOW_MS) {
                    if (actor.cursedEnergy < CE_COST_BLUE_MAX_EXTRA) {
                        broadcast(EventMsg.simple("NO_CE", actor.id, "Need CE for BLUE_MAX", tMs));
                        return;
                    }
                    actor.cursedEnergy -= CE_COST_BLUE_MAX_EXTRA;
                    ownedBlue.kind = "BLUE_MAX";
                    ownedBlue.pauseSec = 0.18;
                    ownedBlue.orbiting = true;
                    ownedBlue.orbitInit = false;
                    ownedBlue.orbitRadius = Math.max(38.0, Vec2.dist(ownedBlue.pos, actor.pos));
                    ownedBlue.orbitOmega = 7.0;
                    ownedBlue.orbitStartAngle = Math.atan2(ownedBlue.pos.y - actor.pos.y, ownedBlue.pos.x - actor.pos.x);
                    ownedBlue.orbitTimeLeftSec = (2 * Math.PI) / ownedBlue.orbitOmega;
                    ownedBlue.anchorToOwner = false;
                    ownedBlue.orbitAngle = 0.0;
                    ownedBlue.radius = 16.0;
                    ownedBlue.visualThickness = 0.0;
                    ownedBlue.visualLength = 0.0;
                    ownedBlue.angleRad = actor.facingAngleRad;
                    ownedBlue.vel.x = 0; ownedBlue.vel.y = 0;
                    ownedBlue.ttlSec = Math.max(ownedBlue.ttlSec, 1.55);
                    actor.cdBlueUntilMs = Math.max(actor.cdBlueUntilMs, tMs + 650);
                    lastCastBlueMs.put(actor.id, tMs);
                    broadcast(EventMsg.simple("COMBO", actor.id, "MAXIMUM OUTPUT: BLUE!", tMs));
                    return;
                }
                if (checkCooldown(actor.id, tMs, lastCastBlueMs, actor.cdBlueUntilMs, CD_BLUE_MS, "BLUE")) return;
                if (checkCost(actor, CE_COST_BLUE, "BLUE", tMs)) return;

                actor.cursedEnergy -= CE_COST_BLUE;
                actor.cdBlueUntilMs = tMs + CD_BLUE_MS;
                actor.lastBlueCastMs = tMs;
                lastCastBlueMs.put(actor.id, tMs);
                broadcastEvents(abilities.handleActions(gs, actor, List.of("BLUE"), tMs, projectileIdGen));
            }
            case "RED" -> {
                if (!actor.isGojo()) return;
                if (actor.lastBlueCastMs > 0 && (tMs - actor.lastBlueCastMs) <= PURPLE_COMBO_WINDOW_MS) {
                    if (tryCastPurple(actor, tMs)) return;
                }
                if (checkCooldown(actor.id, tMs, lastCastRedMs, actor.cdRedUntilMs, CD_RED_MS, "RED")) return;
                if (checkCost(actor, CE_COST_RED, "RED", tMs)) return;

                actor.cursedEnergy -= CE_COST_RED;
                actor.cdRedUntilMs = tMs + CD_RED_MS;
                lastCastRedMs.put(actor.id, tMs);
                broadcastEvents(abilities.handleActions(gs, actor, List.of("RED"), tMs, projectileIdGen));
            }
            case "DISMANTLE" -> {
                if (!actor.isSukuna()) return;
                if (checkCooldown(actor.id, tMs, lastCastDismantleMs, actor.cdDismantleUntilMs, CD_DISMANTLE_MS, "DISMANTLE")) return;
                if (checkCost(actor, CE_COST_DISMANTLE, "DISMANTLE", tMs)) return;

                actor.cursedEnergy -= CE_COST_DISMANTLE;
                actor.cdDismantleUntilMs = tMs + CD_DISMANTLE_MS;
                lastCastDismantleMs.put(actor.id, tMs);
                actor.lastDismantleCastMs = tMs;
                broadcastEvents(abilities.handleActions(gs, actor, List.of("DISMANTLE"), tMs, projectileIdGen));
            }
            case "DASH" -> {
                int dashCd = actor.isGojo() ? CD_DASH_GOJO_MS : CD_DASH_MS;
                if (checkCooldown(actor.id, tMs, lastCastDashMs, actor.cdDashUntilMs, dashCd, "DASH")) return;
                int dashCost = actor.isGojo() ? CE_COST_DASH_GOJO : CE_COST_DASH;
                if (checkCost(actor, dashCost, "DASH", tMs)) return;

                actor.cursedEnergy -= dashCost;
                actor.cdDashUntilMs = tMs + dashCd;
                lastCastDashMs.put(actor.id, tMs);
                actor.lastDashCastMs = tMs;

                Vec2 from = actor.pos.copy();
                Vec2 to = dashTeleport(actor, actor.isGojo() ? DASH_DISTANCE_GOJO : DASH_DISTANCE);
                actor.pos.x = to.x; actor.pos.y = to.y;
                actor.vel.x = 0; actor.vel.y = 0;

                Projectile fx = new Projectile(projectileIdGen.getAndIncrement(), actor.id, "DASH_FX");
                fx.pos.x = from.x; fx.pos.y = from.y;
                fx.ttlSec = 0.12; fx.radius = 1.0; fx.ignoreInfinity = true;
                fx.angleRad = actor.facingAngleRad;
                fx.visualLength = Math.max(10.0, Vec2.dist(from, to));
                fx.visualThickness = 7.0;
                gs.projectiles.add(fx);
                broadcast(EventMsg.simple("DASH", actor.id, "dash!", tMs));
            }
            case "CLEAVE" -> {
                if (actor.isSukuna() && actor.lastDashCastMs > 0 && actor.lastDismantleCastMs > 0 &&
                        actor.lastDismantleCastMs > actor.lastDashCastMs &&
                        (tMs - actor.lastDashCastMs) <= WORLD_SLASH_COMBO_WINDOW_MS &&
                        (tMs - actor.lastDismantleCastMs) <= WORLD_SLASH_COMBO_WINDOW_MS) {
                    if (tryCastWorldSlash(actor, tMs)) return;
                }
                if (!actor.isSukuna()) return;
                if (checkCooldown(actor.id, tMs, lastCastCleaveMs, actor.cdCleaveUntilMs, CD_CLEAVE_MS, "CLEAVE")) return;
                if (checkCost(actor, CE_COST_CLEAVE, "CLEAVE", tMs)) return;

                actor.cursedEnergy -= CE_COST_CLEAVE;
                actor.cdCleaveUntilMs = tMs + CD_CLEAVE_MS;
                lastCastCleaveMs.put(actor.id, tMs);

                int hits = doCleaveMelee(actor, tMs);
                Projectile fx = new Projectile(projectileIdGen.getAndIncrement(), actor.id, "CLEAVE_FX");
                fx.pos.x = actor.pos.x + Math.cos(actor.facingAngleRad) * 18.0;
                fx.pos.y = actor.pos.y + Math.sin(actor.facingAngleRad) * 18.0;
                fx.ttlSec = 0.16; fx.radius = 1.0; fx.ignoreInfinity = true;
                fx.angleRad = actor.facingAngleRad;
                fx.visualLength = 72.0; fx.visualThickness = 4.0;
                gs.projectiles.add(fx);
                broadcast(EventMsg.simple("CLEAVE", actor.id, hits > 0 ? ("hit " + hits) : "miss", tMs));
            }
            case "FUGA" -> {
                if (!actor.isSukuna()) return;
                if (checkCooldown(actor.id, tMs, lastCastFugaMs, actor.cdFugaUntilMs, CD_FUGA_MS, "FUGA")) return;
                if (checkCost(actor, CE_COST_FUGA, "FUGA", tMs)) return;

                actor.cursedEnergy -= CE_COST_FUGA;
                actor.cdFugaUntilMs = tMs + CD_FUGA_MS;
                lastCastFugaMs.put(actor.id, tMs);
                Projectile pr = spawnFuga(actor, projectileIdGen.getAndIncrement());
                gs.projectiles.add(pr);
                broadcast(EventMsg.simple("SPAWN_PROJECTILE", actor.id, "FUGA#" + pr.id, tMs));
            }
            case "DOMAIN_GOJO" -> { if (actor.isGojo()) tryStartDomain(actor, "UV", tMs); }
            case "DOMAIN_SUKUNA" -> { if (actor.isSukuna()) tryStartDomain(actor, "MS", tMs); }
            default -> broadcast(EventMsg.simple("ACTION", actor.id, a, tMs));
        }
    }

    private boolean checkCooldown(int id, long tMs, Map<Integer, Long> lastCastMap, long cdUntil, int cdMs, String name) {
        long last = lastCastMap.getOrDefault(id, 0L);
        if (tMs - last < cdMs || tMs < cdUntil) {
            long remain = Math.max(cdMs - (tMs - last), cdUntil - tMs);
            broadcast(EventMsg.simple("COOLDOWN", id, name + " ready in " + Math.max(0, remain) + "ms", tMs));
            return true;
        }
        return false;
    }

    private boolean checkCost(Player p, int cost, String name, long tMs) {
        if (p.cursedEnergy < cost) {
            broadcast(EventMsg.simple("NO_CE", p.id, "Need " + cost + " CE for " + name, tMs));
            return true;
        }
        return false;
    }

    private void broadcastEvents(List<EventMsg> evs) {
        for (EventMsg ev : evs) broadcast(ev);
    }

    private void tryStartDomain(Player actor, String kind, long tMs) {
        if (!"NONE".equalsIgnoreCase(actor.activeDomain) || tMs < actor.cdDomainUntilMs) return;
        if (actor.cursedEnergy < CE_COST_DOMAIN) {
            broadcast(EventMsg.simple("NO_CE", actor.id, "Need " + CE_COST_DOMAIN + " CE for DOMAIN", tMs));
            return;
        }

        actor.cursedEnergy -= CE_COST_DOMAIN;
        actor.cdDomainUntilMs = tMs + CD_DOMAIN_MS;
        actor.activeDomain = kind;
        actor.domainStartMs = tMs;
        actor.domainCenterX = actor.pos.x;
        actor.domainCenterY = actor.pos.y;
        actor.domainRadius = 0;
        actor.domainCeDrainCarry = 0.0;

        if ("UV".equalsIgnoreCase(kind)) {
            actor.domainMaxRadius = DOMAIN_UV_MAX_RADIUS;
            actor.domainNextTickMs = 0;
        } else if ("MS".equalsIgnoreCase(kind)) {
            actor.domainMaxRadius = DOMAIN_MS_RADIUS;
            actor.domainNextTickMs = tMs + MS_SURE_HIT_TICK_MS;
        } else {
            actor.domainMaxRadius = 0;
            actor.domainNextTickMs = 0;
        }
        broadcast(EventMsg.simple("DOMAIN_START", actor.id, kind, tMs));
    }

    private void endDomain(Player actor, long tMs) {
        if ("NONE".equalsIgnoreCase(actor.activeDomain)) return;
        broadcast(EventMsg.simple("DOMAIN_END", actor.id, actor.activeDomain + " (" + "CE=0" + ")", tMs));
        actor.activeDomain = "NONE";
        actor.domainStartMs = 0;
        actor.domainRadius = 0;
        actor.domainMaxRadius = 0;
        actor.domainNextTickMs = 0;
        actor.domainCeDrainCarry = 0.0;
    }

    private boolean isInsideDomain(Player point, Player owner) {
        if (point == null || owner == null || "NONE".equalsIgnoreCase(owner.activeDomain)) return false;
        double dx = point.pos.x - owner.domainCenterX;
        double dy = point.pos.y - owner.domainCenterY;
        return (dx * dx + dy * dy) <= (owner.domainRadius * owner.domainRadius);
    }

    private boolean isInDomainOverlap(Player point, Player gojo, Player sukuna) {
        return isInsideDomain(point, gojo) && isInsideDomain(point, sukuna);
    }

    private boolean isInfinityActiveNow(Player gojo, Player sukuna) {
        if (gojo == null || gojo.cursedEnergy <= 0) return false;
        if (sukuna != null && "MS".equalsIgnoreCase(sukuna.activeDomain) && isInsideDomain(gojo, sukuna)) {
            return "UV".equalsIgnoreCase(gojo.activeDomain) && isInDomainOverlap(gojo, gojo, sukuna);
        }
        return true;
    }

    private void updateDomains(double dt, long tMs) {
        Player gojo = findCharacter("GOJO");
        Player sukuna = findCharacter("SUKUNA");
        for (Player p : gs.players) p.stunned = false;

        if (gojo != null && "UV".equalsIgnoreCase(gojo.activeDomain)) {
            double age = Math.max(0.0, (double) (tMs - gojo.domainStartMs));
            gojo.domainRadius = gojo.domainMaxRadius * Math.min(1.0, age / DOMAIN_UV_EXPAND_MS);
        }
        if (sukuna != null && "MS".equalsIgnoreCase(sukuna.activeDomain)) {
            double age = Math.max(0.0, (double) (tMs - sukuna.domainStartMs));
            sukuna.domainRadius = sukuna.domainMaxRadius * Math.min(1.0, age / 260.0);
        }

        boolean clash = (gojo != null && sukuna != null
                && "UV".equalsIgnoreCase(gojo.activeDomain)
                && "MS".equalsIgnoreCase(sukuna.activeDomain));

        for (Player owner : gs.players) {
            if ("NONE".equalsIgnoreCase(owner.activeDomain)) continue;
            double drainPerSec = DOMAIN_CE_DRAIN_PER_SEC + (clash ? DOMAIN_CLASH_EXTRA_DRAIN_PER_SEC : 0.0);
            owner.domainCeDrainCarry += drainPerSec * dt;
            int drain = (int) Math.floor(owner.domainCeDrainCarry);
            if (drain > 0) {
                owner.domainCeDrainCarry -= drain;
                owner.cursedEnergy = Math.max(0, owner.cursedEnergy - drain);
                if (owner.cursedEnergy == 0) endDomain(owner, tMs);
            }
        }

        if (gojo != null && sukuna != null && "UV".equalsIgnoreCase(gojo.activeDomain)) {
            if (isInsideDomain(sukuna, gojo) && !(clash && isInDomainOverlap(sukuna, gojo, sukuna))) {
                sukuna.stunned = true;
            }
        }

        if (sukuna != null && gojo != null && "MS".equalsIgnoreCase(sukuna.activeDomain)) {
            if (tMs >= sukuna.domainNextTickMs) {
                while (tMs >= sukuna.domainNextTickMs) sukuna.domainNextTickMs += MS_SURE_HIT_TICK_MS;
                if (isInsideDomain(gojo, sukuna) && !(clash && isInDomainOverlap(gojo, gojo, sukuna))) {
                    List<EventMsg> evs = new ArrayList<>();
                    AbilitySystem.applyDamage(sukuna, gojo, MS_SURE_HIT_DAMAGE, tMs, evs);
                    broadcastEvents(evs);
                }
            }
        }
    }

    private Projectile spawnFuga(Player actor, int projId) {
        Projectile pr = new Projectile(projId, actor.id, "FUGA");
        pr.radius = FUGA_RADIUS; pr.damage = 0; pr.knockback = 120.0; pr.ttlSec = FUGA_TTL_SEC;
        pr.angleRad = actor.facingAngleRad;
        double offset = Constants.PLAYER_RADIUS + pr.radius + 3;
        pr.pos.x = actor.pos.x + Math.cos(actor.facingAngleRad) * offset;
        pr.pos.y = actor.pos.y + Math.sin(actor.facingAngleRad) * offset;
        pr.vel.x = Math.cos(actor.facingAngleRad) * FUGA_SPEED;
        pr.vel.y = Math.sin(actor.facingAngleRad) * FUGA_SPEED;
        return pr;
    }

    private Projectile spawnExplosionFx(int ownerId, Vec2 at) {
        Projectile fx = new Projectile(projectileIdGen.getAndIncrement(), ownerId, "EXPLOSION");
        fx.radius = GameServer.FUGA_EXPLOSION_RADIUS; fx.ttlSec = 0.28;
        fx.pos.x = at.x; fx.pos.y = at.y; fx.ignoreInfinity = true;
        return fx;
    }

    private Projectile spawnFireZone(int ownerId, Vec2 at) {
        Projectile fire = new Projectile(projectileIdGen.getAndIncrement(), ownerId, "FIRE");
        fire.radius = FIRE_ZONE_RADIUS; fire.ttlSec = FIRE_ZONE_TTL_SEC;
        fire.pos.x = at.x; fire.pos.y = at.y; fire.ignoreInfinity = true;
        fire.dotDamage = FIRE_DOT_DAMAGE; fire.dotCeDrain = FIRE_DOT_CE_DRAIN;
        fire.dotIntervalMs = FIRE_TICK_MS; fire.nextDotTickMs = nowMs() + FIRE_TICK_MS;
        return fire;
    }

    private void consumeGojoBlueForPurple(int gojoId) {
        for (Projectile p : gs.projectiles) {
            if (p.ownerId != gojoId) continue;
            String k = p.kind == null ? "" : p.kind.toUpperCase(Locale.ROOT);
            if ("BLUE".equals(k) || "BLUE_MAX".equals(k)) p.ttlSec = 0;
        }
    }

    private boolean tryCastPurple(Player actor, long tMs) {
        if (checkCooldown(actor.id, tMs, lastCastPurpleMs, actor.cdPurpleUntilMs, CD_PURPLE_MS, "PURPLE")) return false;
        if (checkCost(actor, CE_COST_PURPLE, "PURPLE", tMs)) return false;

        actor.cursedEnergy -= CE_COST_PURPLE;
        consumeGojoBlueForPurple(actor.id);
        actor.cdPurpleUntilMs = tMs + CD_PURPLE_MS;
        lastCastPurpleMs.put(actor.id, tMs);
        actor.cdRedUntilMs = Math.max(actor.cdRedUntilMs, tMs + CD_RED_MS);
        lastCastRedMs.put(actor.id, tMs);
        actor.lastBlueCastMs = 0;

        broadcast(EventMsg.simple("COMBO", actor.id, "HOLLOW PURPLE!", tMs));
        broadcastEvents(abilities.handleActions(gs, actor, List.of("PURPLE"), tMs, projectileIdGen));
        return true;
    }

    private boolean tryCastWorldSlash(Player actor, long tMs) {
        if (checkCooldown(actor.id, tMs, lastCastWorldSlashMs, actor.cdWorldSlashUntilMs, CD_WORLD_SLASH_MS, "WORLD SLASH")) return false;
        if (checkCost(actor, CE_COST_WORLD_SLASH, "WORLD SLASH", tMs)) return false;

        actor.cursedEnergy -= CE_COST_WORLD_SLASH;
        actor.cdWorldSlashUntilMs = tMs + CD_WORLD_SLASH_MS;
        lastCastWorldSlashMs.put(actor.id, tMs);
        castWorldSlash(actor, tMs);
        return true;
    }

    private void castWorldSlash(Player actor, long tMs) {
        double speed = 560.0;
        Projectile pr = new Projectile(projectileIdGen.getAndIncrement(), actor.id, "WCS");
        pr.radius = 120.0;
        pr.visualThickness = 70.0;
        pr.visualLength = Math.toRadians(170.0);
        pr.damage = 36;
        pr.knockback = 560.0;
        pr.ignoreInfinity = true;
        pr.sureHit = true;
        pr.ttlSec = 0.85;
        pr.angleRad = actor.facingAngleRad;
        double off = Constants.PLAYER_RADIUS + 10.0;
        pr.pos.x = actor.pos.x + Math.cos(actor.facingAngleRad) * off;
        pr.pos.y = actor.pos.y + Math.sin(actor.facingAngleRad) * off;
        pr.vel.x = Math.cos(actor.facingAngleRad) * speed;
        pr.vel.y = Math.sin(actor.facingAngleRad) * speed;
        pr.hitMask = 0L;
        gs.projectiles.add(pr);
        broadcast(EventMsg.simple("COMBO", actor.id, "WORLD CUTTING SLASH!", tMs));
    }

    private int doCleaveMelee(Player actor, long tMs) {
        int hits = 0;
        double cosHalfFov = Math.cos(Math.toRadians(CLEAVE_FOV_DEG * 0.5));
        double fx = Math.cos(actor.facingAngleRad);
        double fy = Math.sin(actor.facingAngleRad);

        for (Player target : gs.players) {
            if (target.id == actor.id) continue;
            double dx = target.pos.x - actor.pos.x;
            double dy = target.pos.y - actor.pos.y;
            double dist = Math.hypot(dx, dy);
            if (dist > CLEAVE_RANGE) continue;
            double nx = dx / Math.max(1e-6, dist);
            double ny = dy / Math.max(1e-6, dist);
            if ((fx * nx + fy * ny) < cosHalfFov) continue;
            if (Raycast.hasWallBetween(gs.maze, actor.pos, target.pos)) continue;

            if (target.isGojo() && target.cursedEnergy > 0) {
                target.cursedEnergy = Math.max(0, target.cursedEnergy - CE_LOSS_ON_CLEAVE_BLOCK);
                broadcast(EventMsg.simple("INFINITY_BLOCK", target.id, "Infinity blocked CLEAVE", tMs));
                if (target.cursedEnergy == 0) broadcast(EventMsg.simple("INFINITY_DOWN", target.id, "Infinity disabled", tMs));
                hits++;
                continue;
            }

            int dmg = Math.min(Math.max(8, (int) Math.round(target.hp * 0.18)), 28);
            List<EventMsg> evs = new ArrayList<>();
            AbilitySystem.applyDamage(actor, target, dmg, tMs, evs);
            broadcastEvents(evs);
            target.vel.x += nx * 240.0;
            target.vel.y += ny * 240.0;
            hits++;
        }
        return hits;
    }

    private void updateProjectiles(double dt, long tMs, IntList destroyedWallsOut) {
        Player gojo = findCharacter("GOJO");
        List<Projectile> toRemove = new ArrayList<>();
        List<Projectile> toAdd = new ArrayList<>();

        for (Projectile pr : gs.projectiles) {
            if (("BLUE".equalsIgnoreCase(pr.kind) || "BLUE_MAX".equalsIgnoreCase(pr.kind))) {
                for (Projectile other : gs.projectiles) {
                    if (other.id != pr.id && other.ownerId != pr.ownerId) applyBluePull(pr, other.pos, other.vel, dt);
                }
            }
        }

        for (Projectile pr : gs.projectiles) {
            pr.ttlSec -= dt;
            if (pr.ttlSec <= 0) { toRemove.add(pr); continue; }

            String k = pr.kind == null ? "" : pr.kind.toUpperCase(Locale.ROOT);
            if ("FIRE".equals(k)) { handleFireZoneDot(pr, tMs); pr.vel.x = 0; pr.vel.y = 0; continue; }
            if ("EXPLOSION".equals(k)) { pr.vel.x = 0; pr.vel.y = 0; continue; }
            if (k.endsWith("_FX")) continue;

            if (gojo != null && isInfinityActiveNow(gojo, gs.getOtherPlayer(gojo.id)) && pr.ownerId != gojo.id && !pr.ignoreInfinity && !pr.sureHit) {
                if (Vec2.dist(pr.pos, gojo.pos) < INFINITY_RADIUS) {
                    pr.vel.mul(0.15);
                    if (!pr.inInfinity) {
                        pr.inInfinity = true;
                        broadcast(EventMsg.simple("INFINITY", gojo.id, "slowing " + pr.kind + "#" + pr.id, tMs));
                    }
                    if (pr.vel.len() < 20.0) { pr.vel.x = 0; pr.vel.y = 0; }
                } else {
                    pr.inInfinity = false;
                }
            } else {
                pr.inInfinity = false;
            }

            if ("BLUE".equals(k) || "BLUE_MAX".equals(k)) {
                if ("BLUE_MAX".equals(k)) {
                    updateBlueMax(pr, gs.getPlayer(pr.ownerId), dt, destroyedWallsOut);
                    double maxDim = gs.maze.w * Constants.CELL;
                    if (pr.pos.x < 0 || pr.pos.y < 0 || pr.pos.x > maxDim || pr.pos.y > maxDim) toRemove.add(pr);
                } else {
                    Vec2 next = new Vec2(pr.pos.x + pr.vel.x * dt, pr.pos.y + pr.vel.y * dt);
                    if (Raycast.hasWallBetween(gs.maze, pr.pos, next) || Physics.collidesWithMaze(gs, next, pr.radius)) {
                        pr.vel.x = 0; pr.vel.y = 0;
                    } else {
                        pr.pos.x = next.x; pr.pos.y = next.y;
                        if (pr.vel.len() > 1e-6) pr.angleRad = Math.atan2(pr.vel.y, pr.vel.x);
                    }
                    double maxDim = gs.maze.w * Constants.CELL;
                    if (pr.pos.x < 0 || pr.pos.y < 0 || pr.pos.x > maxDim || pr.pos.y > maxDim) toRemove.add(pr);
                }
                continue;
            }

            if ("WCS".equals(k)) {
                updateWCS(pr, dt, tMs, destroyedWallsOut, toRemove);
                continue;
            }

            Vec2 oldPos = pr.pos.copy();
            Vec2 next = new Vec2(pr.pos.x + pr.vel.x * dt, pr.pos.y + pr.vel.y * dt);

            if ("PURPLE".equalsIgnoreCase(pr.kind)) {
                destroyWallsAround(next, pr.radius + 14.0, destroyedWallsOut);
            } else {
                double colR = "DISMANTLE".equals(k) ? 6.0 : pr.radius;
                if (Raycast.hasWallBetween(gs.maze, oldPos, next) || Physics.collidesWithMaze(gs, next, colR)) {
                    if ("RED".equalsIgnoreCase(pr.kind) && pr.bouncesRemaining > 0) {
                        if (Physics.collidesWithMaze(gs, new Vec2(next.x, oldPos.y), pr.radius)) pr.vel.x *= -1;
                        if (Physics.collidesWithMaze(gs, new Vec2(oldPos.x, next.y), pr.radius)) pr.vel.y *= -1;
                        else { pr.vel.x *= -1; pr.vel.y *= -1; }
                        pr.bouncesRemaining--;
                        pr.pos.x = oldPos.x; pr.pos.y = oldPos.y;
                        broadcast(EventMsg.simple("RED_BOUNCE", pr.ownerId, "Bounce", tMs));
                        continue;
                    }
                    toRemove.add(pr);
                    broadcast(EventMsg.simple("PROJECTILE_HIT_WALL", pr.ownerId, pr.kind + "#" + pr.id, tMs));
                    continue;
                }
            }

            double maxDim = gs.maze.w * Constants.CELL;
            if (next.x < 0 || next.y < 0 || next.x > maxDim || next.y > maxDim) { toRemove.add(pr); continue; }
            pr.pos.x = next.x; pr.pos.y = next.y;
            if (pr.vel.len() > 1e-6) pr.angleRad = Math.atan2(pr.vel.y, pr.vel.x);

            handleProjectilePlayerHits(pr, k, oldPos, next, tMs, toRemove, toAdd);
        }

        gs.projectiles.removeAll(toRemove);
        gs.projectiles.addAll(toAdd);
        for (Player p : gs.players) p.vel.mul(0.86);
    }

    private void updateBlueMax(Projectile pr, Player owner, double dt, IntList destroyedWallsOut) {
        if (pr.pauseSec > 0) {
            pr.pauseSec = Math.max(0, pr.pauseSec - dt);
            pr.vel.x = 0; pr.vel.y = 0;
            return;
        }
        if (!pr.orbitInit) {
            pr.orbitInit = true;
            if (owner != null) {
                pr.centerX = owner.pos.x; pr.centerY = owner.pos.y;
                pr.orbitRadius = Math.max(38.0, Vec2.dist(pr.pos, owner.pos));
                pr.orbitStartAngle = Math.atan2(pr.pos.y - owner.pos.y, pr.pos.x - owner.pos.x);
            } else {
                pr.centerX = pr.pos.x; pr.centerY = pr.pos.y;
                pr.orbitRadius = 64.0; pr.orbitStartAngle = 0.0;
            }
            pr.orbitAngle = pr.orbitStartAngle;
            pr.orbitOmega = 7.0;
            pr.orbitTimeLeftSec = (2 * Math.PI) / pr.orbitOmega;
            pr.orbiting = true; pr.anchorToOwner = false;
        }

        double cx = (owner != null) ? owner.pos.x : pr.centerX;
        double cy = (owner != null) ? owner.pos.y : pr.centerY;

        if (pr.orbiting && !pr.anchorToOwner) {
            pr.orbitAngle += pr.orbitOmega * dt;
            pr.orbitTimeLeftSec -= dt;
            if (pr.orbitTimeLeftSec <= 0) {
                pr.orbiting = false; pr.anchorToOwner = true; pr.orbitAngle = pr.orbitStartAngle;
            }
        } else if (pr.anchorToOwner) {
            pr.orbitAngle = pr.orbitStartAngle;
        }

        pr.pos.x = cx + Math.cos(pr.orbitAngle) * pr.orbitRadius;
        pr.pos.y = cy + Math.sin(pr.orbitAngle) * pr.orbitRadius;
        destroyWallsAround(pr.pos, pr.radius + 18.0, destroyedWallsOut);
    }

    private void updateWCS(Projectile pr, double dt, long tMs, IntList destroyedWallsOut, List<Projectile> toRemove) {
        Vec2 next = new Vec2(pr.pos.x + pr.vel.x * dt, pr.pos.y + pr.vel.y * dt);
        pr.pos.x = next.x; pr.pos.y = next.y;
        if (pr.vel.len() > 1e-6) pr.angleRad = Math.atan2(pr.vel.y, pr.vel.x);

        double maxDim = gs.maze.w * Constants.CELL;
        if (next.x < 0 || next.y < 0 || next.x > maxDim || next.y > maxDim) { toRemove.add(pr); return; }

        double outerR = pr.radius;
        double innerR = Math.max(0.0, pr.visualThickness);
        double sweep = pr.visualLength > 0 ? pr.visualLength : Math.toRadians(170.0);

        destroyWallsInArcBand(pr.pos, innerR, outerR, pr.angleRad, sweep, destroyedWallsOut);

        for (Projectile other : gs.projectiles) {
            if (other.id == pr.id || other.kind == null || other.kind.endsWith("_FX") || other.kind.startsWith("BLUE")) continue;
            if (arcBandContainsPoint(pr.pos, 0.0, outerR + 8.0, pr.angleRad, sweep, other.pos, other.radius + 6.0)) toRemove.add(other);
        }

        for (Player target : gs.players) {
            if (target.id == pr.ownerId || ((pr.hitMask >> (target.id & 63)) & 1) != 0) continue;
            if (arcBandContainsPoint(pr.pos, 0.0, outerR, pr.angleRad, sweep, target.pos, Constants.PLAYER_RADIUS + 10.0)) {
                Player attacker = gs.getPlayer(pr.ownerId);
                if (attacker == null) attacker = target;
                List<EventMsg> evs = new ArrayList<>();
                AbilitySystem.applyDamage(attacker, target, pr.damage, tMs, evs);
                broadcastEvents(evs);
                Vec2 kb = new Vec2(target.pos.x - pr.pos.x, target.pos.y - pr.pos.y);
                if (kb.len() > 1e-6) kb.norm();
                target.vel.x += kb.x * pr.knockback;
                target.vel.y += kb.y * pr.knockback;
                pr.hitMask |= (1L << (target.id & 63));
                broadcast(EventMsg.simple("HIT", attacker.id, "WCS hit #" + target.id, tMs));
            }
        }
    }

    private void handleProjectilePlayerHits(Projectile pr, String k, Vec2 oldPos, Vec2 next, long tMs, List<Projectile> toRemove, List<Projectile> toAdd) {
        for (Player target : gs.players) {
            if (target.id == pr.ownerId) continue;

            if ("DISMANTLE".equals(k)) {
                double outerR = pr.radius;
                double innerR = Math.max(0.0, pr.visualThickness);
                double sweep = pr.visualLength > 0 ? pr.visualLength : Math.toRadians(120.0);
                boolean hit = arcBandContainsPoint(pr.pos, innerR, outerR, pr.angleRad, sweep, target.pos, Constants.PLAYER_RADIUS + 2.0) ||
                        arcBandContainsPoint(oldPos, innerR, outerR, pr.angleRad, sweep, target.pos, Constants.PLAYER_RADIUS + 2.0);
                if (hit) {
                    if (target.isGojo() && isInfinityActiveNow(target, gs.getOtherPlayer(target.id)) && !pr.ignoreInfinity && !pr.sureHit) {
                        drainGojoCE(target, CE_LOSS_ON_HIT, "DISMANTLE blocked", tMs, pr.ownerId);
                        toRemove.add(pr);
                        break;
                    }
                    applyHit(pr, target, tMs, 0, toRemove);
                    break;
                }
                continue;
            }

            if (segmentIntersectsCircle(oldPos, next, target.pos, Constants.PLAYER_RADIUS + pr.radius)) {
                if ("FUGA".equalsIgnoreCase(k)) {
                    applyFugaExplosionDamage(next.copy(), pr.ownerId, tMs);
                    toAdd.add(spawnExplosionFx(pr.ownerId, next.copy()));
                    toAdd.add(spawnFireZone(pr.ownerId, next.copy()));
                    toRemove.add(pr);
                    broadcast(EventMsg.simple("PROJECTILE_HIT_PLAYER", pr.ownerId, k + " exploded", tMs));
                    break;
                }

                Player attacker = gs.getPlayer(pr.ownerId);
                if (attacker == null) attacker = target;
                boolean isSukunaAttack = attacker.isSukuna() || "DISMANTLE".equalsIgnoreCase(k);
                if (target.isGojo() && isSukunaAttack) drainGojoCE(target, CE_LOSS_ON_HIT, "Hit by " + k, tMs, attacker.id);

                int dmg = pr.damage;
                if (target.isGojo() && "DISMANTLE".equalsIgnoreCase(k)) dmg = 0;
                applyHit(pr, target, tMs, dmg, toRemove);
                break;
            }
        }
    }

    private void applyHit(Projectile pr, Player target, long tMs, int dmg, List<Projectile> toRemove) {
        Player attacker = gs.getPlayer(pr.ownerId);
        if (attacker == null) attacker = target;
        List<EventMsg> evs = new ArrayList<>();
        AbilitySystem.applyDamage(attacker, target, dmg, tMs, evs);
        broadcastEvents(evs);
        Vec2 kb = new Vec2(target.pos.x - pr.pos.x, target.pos.y - pr.pos.y);
        if (kb.len() > 1e-6) kb.norm();
        target.vel.x += kb.x * pr.knockback;
        target.vel.y += kb.y * pr.knockback;
        toRemove.add(pr);
        broadcast(EventMsg.simple("PROJECTILE_HIT_PLAYER", pr.ownerId, pr.kind + " hit", tMs));
    }

    private void drainGojoCE(Player gojo, int amount, String reason, long tMs, int sourceId) {
        int before = gojo.cursedEnergy;
        gojo.cursedEnergy = Math.max(0, gojo.cursedEnergy - amount);
        broadcast(EventMsg.simple("CE_DRAIN", sourceId, "Gojo CE: " + before + " -> " + gojo.cursedEnergy + " (" + reason + ")", tMs));
        if (before > 0 && gojo.cursedEnergy == 0) broadcast(EventMsg.simple("INFINITY_DOWN", gojo.id, "Infinity disabled", tMs));
    }

    private void applyBluePullToPlayerVel(Player player, double dt) {
        for (Projectile pr : gs.projectiles) {
            if (("BLUE".equalsIgnoreCase(pr.kind) || "BLUE_MAX".equalsIgnoreCase(pr.kind)) && pr.ownerId != player.id) {
                applyBluePull(pr, player.pos, player.vel, dt);
            }
        }
    }

    private void applyBluePull(Projectile blue, Vec2 pos, Vec2 vel, double dt) {
        boolean isMax = "BLUE_MAX".equalsIgnoreCase(blue.kind);
        double effectR = isMax ? BLUE_EFFECT_RADIUS * 1.35 : BLUE_EFFECT_RADIUS;
        double strength = isMax ? BLUE_PULL_STRENGTH * 1.9 : BLUE_PULL_STRENGTH;

        double dx = blue.pos.x - pos.x;
        double dy = blue.pos.y - pos.y;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 1e-6 || dist > effectR) return;

        double accel = strength * (1.0 - (dist / effectR));
        vel.x += (dx / dist) * accel * dt;
        vel.y += (dy / dist) * accel * dt;
    }

    private void destroyWallsAround(Vec2 center, double radius, IntList destroyedOut) {
        int cell = Constants.CELL;
        int minX = Math.max(0, (int) Math.floor((center.x - radius) / cell));
        int maxX = Math.min(gs.maze.w - 1, (int) Math.floor((center.x + radius) / cell));
        int minY = Math.max(0, (int) Math.floor((center.y - radius) / cell));
        int maxY = Math.min(gs.maze.h - 1, (int) Math.floor((center.y + radius) / cell));
        double r2 = radius * radius;

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (gs.maze.cells[y][x] != 1) continue;
                double cx = x * cell + cell / 2.0;
                double cy = y * cell + cell / 2.0;
                if (((cx - center.x) * (cx - center.x) + (cy - center.y) * (cy - center.y)) <= r2 + (cell * cell * 0.18)) {
                    gs.maze.cells[y][x] = 0;
                    destroyedOut.add(y * gs.maze.w + x);
                }
            }
        }
    }

    private Vec2 dashTeleport(Player actor, double distance) {
        double dx = Math.cos(actor.facingAngleRad);
        double dy = Math.sin(actor.facingAngleRad);
        Vec2 lastOk = actor.pos.copy();
        int steps = (int) Math.ceil(distance / DASH_STEP);
        for (int i = 1; i <= steps; i++) {
            double d = Math.min(distance, i * DASH_STEP);
            Vec2 cand = new Vec2(actor.pos.x + dx * d, actor.pos.y + dy * d);
            if (cand.x < 0 || cand.y < 0 || cand.x > gs.maze.w * Constants.CELL || cand.y > gs.maze.h * Constants.CELL) break;
            if (!Physics.collidesWithMaze(gs, cand, Constants.PLAYER_RADIUS)) lastOk = cand;
            else break;
        }
        return lastOk;
    }

    private boolean arcBandContainsPoint(Vec2 center, double innerR, double outerR, double angCenter, double sweepRad, Vec2 p, double extraTol) {
        double dx = p.x - center.x;
        double dy = p.y - center.y;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < Math.max(0, innerR - extraTol) || dist > outerR + extraTol) return false;
        double angTol = (extraTol > 0.0 && dist > 1e-6) ? Math.asin(Math.min(1.0, extraTol / dist)) : 0.0;
        if (sweepRad + 2.0 * angTol >= Math.PI * 2.0) return true;
        double a = Math.atan2(dy, dx);
        double start = normalizeAngle(angCenter - sweepRad * 0.5 - angTol);
        double end = normalizeAngle(angCenter + sweepRad * 0.5 + angTol);
        return angleInArc(a, start, end);
    }

    private void destroyWallsInArcBand(Vec2 center, double innerR, double outerR, double angCenter, double sweepRad, IntList destroyedOut) {
        int cell = Constants.CELL;
        int minX = Math.max(0, (int) Math.floor((center.x - outerR - cell) / cell));
        int maxX = Math.min(gs.maze.w - 1, (int) Math.floor((center.x + outerR + cell) / cell));
        int minY = Math.max(0, (int) Math.floor((center.y - outerR - cell) / cell));
        int maxY = Math.min(gs.maze.h - 1, (int) Math.floor((center.y + outerR + cell) / cell));

        double start = normalizeAngle(angCenter - sweepRad * 0.5);
        double end = normalizeAngle(angCenter + sweepRad * 0.5);
        double inner2 = innerR * innerR;
        double outer2 = outerR * outerR;
        boolean[] mark = new boolean[gs.maze.w * gs.maze.h];

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (gs.maze.cells[y][x] != 1) continue;
                double cx = x * cell + cell / 2.0;
                double cy = y * cell + cell / 2.0;
                double d2 = (cx - center.x) * (cx - center.x) + (cy - center.y) * (cy - center.y);
                if (d2 < inner2 || d2 > outer2) continue;
                if (!angleInArc(Math.atan2(cy - center.y, cx - center.x), start, end)) continue;

                gs.maze.cells[y][x] = 0;
                int idx = y * gs.maze.w + x;
                if (!mark[idx]) { mark[idx] = true; destroyedOut.add(idx); }
            }
        }
    }

    private boolean angleInArc(double a, double start, double end) {
        return (start <= end) ? (a >= start && a <= end) : (a >= start || a <= end);
    }

    private double normalizeAngle(double a) {
        while (a < 0) a += Math.PI * 2;
        while (a >= Math.PI * 2) a -= Math.PI * 2;
        return a;
    }

    private void handleFireZoneDot(Projectile fire, long tMs) {
        if (fire.dotIntervalMs <= 0 || tMs < fire.nextDotTickMs) return;
        fire.nextDotTickMs = tMs + fire.dotIntervalMs;

        for (Player target : gs.players) {
            if (target.id == fire.ownerId || Vec2.dist(fire.pos, target.pos) > fire.radius) continue;
            if (target.isGojo() && target.cursedEnergy > 0) {
                drainGojoCE(target, fire.dotCeDrain, "FIRE tick", tMs, fire.ownerId);
                continue;
            }
            Player attacker = gs.getPlayer(fire.ownerId);
            if (attacker == null) attacker = target;
            List<EventMsg> evs = new ArrayList<>();
            AbilitySystem.applyDamage(attacker, target, fire.dotDamage, tMs, evs);
            broadcastEvents(evs);
        }
    }

    private void applyFugaExplosionDamage(Vec2 center, int ownerId, long tMs) {
        Player attacker = gs.getPlayer(ownerId);
        if (attacker == null && !gs.players.isEmpty()) attacker = gs.players.get(0);
        for (Player target : gs.players) {
            if (target.id == ownerId || Vec2.dist(center, target.pos) > FUGA_EXPLOSION_RADIUS) continue;
            if (target.isGojo() && target.cursedEnergy > 0) {
                drainGojoCE(target, Math.max(10, FIRE_DOT_CE_DRAIN * 2), "FUGA explosion", tMs, ownerId);
                continue;
            }
            if (attacker == null) attacker = target;
            List<EventMsg> evs = new ArrayList<>();
            AbilitySystem.applyDamage(attacker, target, FUGA_EXPLOSION_DAMAGE, tMs, evs);
            broadcastEvents(evs);
        }
    }

    private boolean segmentIntersectsCircle(Vec2 a, Vec2 b, Vec2 c, double r) {
        double abx = b.x - a.x, aby = b.y - a.y;
        double len2 = abx * abx + aby * aby;
        if (len2 < 1e-9) return ((c.x - a.x) * (c.x - a.x) + (c.y - a.y) * (c.y - a.y)) <= r * r;
        double t = Math.max(0, Math.min(1, ((c.x - a.x) * abx + (c.y - a.y) * aby) / len2));
        double dx = c.x - (a.x + abx * t), dy = c.y - (a.y + aby * t);
        return dx * dx + dy * dy <= r * r;
    }

    private Player findCharacter(String character) {
        for (Player p : gs.players) if (character.equalsIgnoreCase(p.character)) return p;
        return null;
    }

    private StateMsg buildState(long tMs) {
        StateMsg sm = new StateMsg();
        sm.serverTimeMs = tMs;
        sm.roundNumber = Math.max(1, roundNumber == 0 ? 1 : roundNumber);
        sm.winScore = WIN_SCORE;
        sm.p1Id = p1Id; sm.p2Id = p2Id;
        sm.p1Score = p1Score; sm.p2Score = p2Score;
        sm.roundActive = roundActive;
        sm.lastRoundWinnerId = lastRoundWinnerId;
        sm.nextRoundStartMs = nextRoundStartMs;
        sm.matchOver = matchOver;
        sm.matchWinnerId = matchWinnerId;
        sm.p1RematchReady = p1RematchReady; sm.p2RematchReady = p2RematchReady;

        for (Player p : gs.players) {
            PlayerStateDTO dto = new PlayerStateDTO();
            dto.id = p.id; dto.name = p.name; dto.character = p.character;
            dto.x = p.pos.x; dto.y = p.pos.y; dto.facing = p.facingAngleRad;
            dto.hp = p.hp; dto.maxHp = p.maxHp;
            dto.cursedEnergy = p.cursedEnergy; dto.maxCursedEnergy = p.maxCursedEnergy;
            dto.stunned = p.stunned;
            dto.activeDomain = p.activeDomain; dto.domainStartMs = p.domainStartMs;
            dto.domainCenterX = p.domainCenterX; dto.domainCenterY = p.domainCenterY;
            dto.domainRadius = p.domainRadius; dto.domainMaxRadius = p.domainMaxRadius;
            dto.cdBlueUntilMs = p.cdBlueUntilMs; dto.cdRedUntilMs = p.cdRedUntilMs;
            dto.cdPurpleUntilMs = p.cdPurpleUntilMs; dto.cdDismantleUntilMs = p.cdDismantleUntilMs;
            dto.cdDashUntilMs = p.cdDashUntilMs; dto.cdCleaveUntilMs = p.cdCleaveUntilMs;
            dto.cdWorldSlashUntilMs = p.cdWorldSlashUntilMs; dto.cdFugaUntilMs = p.cdFugaUntilMs;
            dto.cdDomainUntilMs = p.cdDomainUntilMs;
            sm.players.add(dto);
        }
        for (Projectile pr : gs.projectiles) {
            ProjectileStateDTO dto = new ProjectileStateDTO();
            dto.id = pr.id; dto.ownerId = pr.ownerId; dto.kind = pr.kind;
            dto.x = pr.pos.x; dto.y = pr.pos.y; dto.radius = pr.radius;
            dto.angleRad = pr.angleRad; dto.visualLength = pr.visualLength; dto.visualThickness = pr.visualThickness;
            sm.projectiles.add(dto);
        }
        return sm;
    }

    private void broadcast(NetMessage msg) {
        for (ClientPeer peer : peers) peer.send(msg);
    }

    private void maybeStartFirstRound(long tMs) {
        if (roundNumber != 0 || roundActive || matchOver || p1Id == 0 || p2Id == 0 || gs.players.size() < 2) return;
        Player p1 = gs.getPlayer(p1Id), p2 = gs.getPlayer(p2Id);
        if (p1 == null || p2 == null || "UNKNOWN".equalsIgnoreCase(p1.character) || "UNKNOWN".equalsIgnoreCase(p2.character)) return;
        resetMatchState();
        startNextRound(tMs);
    }

    private void startNewMatch(long tMs) {
        resetMatchState();
        broadcast(EventMsg.simple("SYSTEM", 0, "Rematch started!", tMs));
        startNextRound(tMs);
    }

    private void startNextRound(long tMs) {
        if (gs.players.size() < 2 || p1Id == 0 || p2Id == 0 || matchOver) return;
        roundNumber = Math.max(0, roundNumber) + 1;
        roundActive = true;
        lastRoundWinnerId = 0;
        nextRoundStartMs = 0L;
        copyIntoMaze(gs.maze.cells, baseMazeCells);
        broadcastMazeAndSystemMsg("Round " + roundNumber + "!");
        gs.projectiles.clear();
        for (Player p : gs.players) {
            respawn(p);
            var q = actionQueues.get(p.id);
            if (q != null) q.clear();
        }
    }

    private void checkRoundWinCondition(long tMs) {
        if (!roundActive || gs.players.size() < 2) return;
        Player p1 = gs.getPlayer(p1Id), p2 = gs.getPlayer(p2Id);
        if (p1 == null || p2 == null) return;
        boolean p1Dead = p1.hp <= 0, p2Dead = p2.hp <= 0;
        if (p1Dead || p2Dead) endRound((p1Dead && p2Dead) ? 0 : (p1Dead ? p2.id : p1.id), tMs);
    }

    private void endRound(int winnerId, long tMs) {
        if (!roundActive) return;
        roundActive = false;
        lastRoundWinnerId = winnerId;
        gs.projectiles.clear();
        for (Player p : gs.players) { p.activeDomain = "NONE"; p.domainRadius = 0; p.stunned = false; }
        if (winnerId == p1Id) p1Score++;
        if (winnerId == p2Id) p2Score++;

        if (winnerId != 0 && (p1Score >= WIN_SCORE || p2Score >= WIN_SCORE)) {
            matchOver = true;
            matchWinnerId = winnerId;
            nextRoundStartMs = 0L;
            broadcast(EventMsg.simple("MATCH_OVER", winnerId, "Match over! Winner: #" + winnerId, tMs));
        } else {
            nextRoundStartMs = tMs + ROUND_INTERMISSION_MS;
            broadcast(EventMsg.simple("ROUND_OVER", winnerId, "Round over! Winner: " + (winnerId == 0 ? "Draw" : "#" + winnerId), tMs));
        }
    }

    private long nowMs() { return System.currentTimeMillis(); }

    private String safeName(String s) {
        if (s == null || s.trim().isEmpty()) return "Player";
        return s.trim().length() > 16 ? s.trim().substring(0, 16).replaceAll("[^a-zA-Z0-9_\\- ]", "") : s.trim().replaceAll("[^a-zA-Z0-9_\\- ]", "");
    }

    private String safeCharacter(String c) {
        if (c == null) return "GOJO";
        String u = c.trim().toUpperCase(Locale.ROOT);
        return (u.equals("GOJO") || u.equals("SUKUNA")) ? u : "GOJO";
    }

    private Projectile findOwnedBlue(int ownerId) {
        for (Projectile pr : gs.projectiles) {
            if (pr.ownerId == ownerId && pr.kind != null && ("BLUE".equals(pr.kind.toUpperCase(Locale.ROOT)) || "BLUE_MAX".equals(pr.kind.toUpperCase(Locale.ROOT)))) return pr;
        }
        return null;
    }

    private void respawn(Player player) {
        double off = Constants.CELL * 2.5;
        if (gs.players.isEmpty() || player.id % 2 == 1) { player.pos.x = off; player.pos.y = off; }
        else { player.pos.x = (Constants.MAZE_W * Constants.CELL) - off; player.pos.y = (Constants.MAZE_H * Constants.CELL) - off; }
        player.vel.x = 0; player.vel.y = 0;
        player.hp = player.maxHp;
        player.cursedEnergy = player.maxCursedEnergy;
        player.ceRegenCarry = 0.0;
        player.cdBlueUntilMs = 0; player.cdRedUntilMs = 0; player.cdPurpleUntilMs = 0;
        player.cdDismantleUntilMs = 0; player.cdDashUntilMs = 0; player.cdCleaveUntilMs = 0;
        player.cdWorldSlashUntilMs = 0; player.cdFugaUntilMs = 0; player.cdDomainUntilMs = 0;
        player.lastBlueCastMs = 0; player.lastDashCastMs = 0; player.lastDismantleCastMs = 0; player.lastCleaveCastMs = 0;
        player.activeDomain = "NONE"; player.domainStartMs = 0; player.domainRadius = 0;
        player.stunned = false;
    }

    private static int[][] deepCopy(int[][] src) {
        if (src == null) return null;
        int[][] out = new int[src.length][];
        for (int y = 0; y < src.length; y++) out[y] = src[y] == null ? null : Arrays.copyOf(src[y], src[y].length);
        return out;
    }

    private static void copyIntoMaze(int[][] dst, int[][] src) {
        if (dst == null || src == null) return;
        for (int y = 0; y < Math.min(dst.length, src.length); y++) {
            if (dst[y] != null && src[y] != null) System.arraycopy(src[y], 0, dst[y], 0, Math.min(dst[y].length, src[y].length));
        }
    }

    private static final class IntList {
        int[] a; int size = 0;
        IntList(int cap) { a = new int[Math.max(8, cap)]; }
        void add(int v) {
            if (size >= a.length) a = Arrays.copyOf(a, a.length * 2);
            a[size++] = v;
        }
        int[] toArray() { return Arrays.copyOf(a, size); }
    }
}