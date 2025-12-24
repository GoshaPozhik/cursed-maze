package ru.itis.jjk.net.server.systems;

import ru.itis.jjk.core.*;
import ru.itis.jjk.net.msg.EventMsg;
import ru.itis.jjk.net.msg.MazeDeltaMsg;
import ru.itis.jjk.net.server.*;

import java.util.*;

import static ru.itis.jjk.net.server.ServerBalance.*;

public final class ProjectileSystem {

    private final ServerContext ctx;
    private final ConnectionManager net;
    private final DomainSystem domains;
    private final EnergySystem energy;

    public ProjectileSystem(ServerContext ctx,
                            ConnectionManager net,
                            DomainSystem domains,
                            EnergySystem energy) {
        this.ctx = ctx;
        this.net = net;
        this.domains = domains;
        this.energy = energy;
    }

    // small int list helper
    private static final class IntList {
        int[] a; int size = 0;
        IntList(int cap) { a = new int[Math.max(8, cap)]; }
        void add(int v) { if (size >= a.length) a = Arrays.copyOf(a, a.length * 2); a[size++] = v; }
        int[] toArray() { return Arrays.copyOf(a, size); }
    }

    public Projectile spawnFuga(Player actor, int projId) {
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
        Projectile fx = new Projectile(ctx.projectileIdGen.getAndIncrement(), ownerId, "EXPLOSION");
        fx.radius = FUGA_EXPLOSION_RADIUS; fx.ttlSec = 0.28;
        fx.pos.x = at.x; fx.pos.y = at.y; fx.ignoreInfinity = true;
        return fx;
    }

    private Projectile spawnFireZone(int ownerId, Vec2 at) {
        Projectile fire = new Projectile(ctx.projectileIdGen.getAndIncrement(), ownerId, "FIRE");
        fire.radius = FIRE_ZONE_RADIUS; fire.ttlSec = FIRE_ZONE_TTL_SEC;
        fire.pos.x = at.x; fire.pos.y = at.y; fire.ignoreInfinity = true;
        fire.dotDamage = FIRE_DOT_DAMAGE; fire.dotCeDrain = FIRE_DOT_CE_DRAIN;
        fire.dotIntervalMs = FIRE_TICK_MS; fire.nextDotTickMs = ctx.nowMs() + FIRE_TICK_MS;
        return fire;
    }

    public void updateProjectilesAndEnvironment(double dt, long tMs) {
        IntList destroyed = new IntList(128);
        updateProjectiles(dt, tMs, destroyed);
        if (destroyed.size > 0) {
            MazeDeltaMsg md = new MazeDeltaMsg();
            md.serverTimeMs = tMs;
            md.indices = destroyed.toArray();
            net.broadcast(md);
            net.broadcast(EventMsg.simple("MAZE", 0, "Destroyed walls: " + destroyed.size, tMs));
        }
    }

    public void applyBluePullToPlayerVel(Player player, double dt) {
        for (Projectile pr : ctx.gs.projectiles) {
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

    private Player findCharacter(String character) {
        for (Player p : ctx.gs.players) if (character.equalsIgnoreCase(p.character)) return p;
        return null;
    }

    private void updateProjectiles(double dt, long tMs, IntList destroyedWallsOut) {
        Player gojo = findCharacter("GOJO");
        List<Projectile> toRemove = new ArrayList<>();
        List<Projectile> toAdd = new ArrayList<>();

        // blue pull on other projectiles
        for (Projectile pr : ctx.gs.projectiles) {
            if (("BLUE".equalsIgnoreCase(pr.kind) || "BLUE_MAX".equalsIgnoreCase(pr.kind))) {
                for (Projectile other : ctx.gs.projectiles) {
                    if (other.id != pr.id && other.ownerId != pr.ownerId) applyBluePull(pr, other.pos, other.vel, dt);
                }
            }
        }

        for (Projectile pr : ctx.gs.projectiles) {
            pr.ttlSec -= dt;
            if (pr.ttlSec <= 0) { toRemove.add(pr); continue; }

            String k = pr.kind == null ? "" : pr.kind.toUpperCase(Locale.ROOT);
            if ("FIRE".equals(k)) { handleFireZoneDot(pr, tMs); pr.vel.x = 0; pr.vel.y = 0; continue; }
            if ("EXPLOSION".equals(k)) { pr.vel.x = 0; pr.vel.y = 0; continue; }
            if (k.endsWith("_FX")) continue;

            // infinity slow
            if (gojo != null) {
                Player other = ctx.gs.getOtherPlayer(gojo.id);
                if (domains.isInfinityActiveNow(gojo, other) && pr.ownerId != gojo.id && !pr.ignoreInfinity && !pr.sureHit) {
                    if (Vec2.dist(pr.pos, gojo.pos) < INFINITY_RADIUS) {
                        pr.vel.mul(0.15);
                        if (!pr.inInfinity) {
                            pr.inInfinity = true;
                            net.broadcast(EventMsg.simple("INFINITY", gojo.id, "slowing " + pr.kind + "#" + pr.id, tMs));
                        }
                        if (pr.vel.len() < 20.0) { pr.vel.x = 0; pr.vel.y = 0; }
                    } else {
                        pr.inInfinity = false;
                    }
                } else {
                    pr.inInfinity = false;
                }
            }

            // BLUE / BLUE_MAX special
            if ("BLUE".equals(k) || "BLUE_MAX".equals(k)) {
                if ("BLUE_MAX".equals(k)) {
                    updateBlueMax(pr, ctx.gs.getPlayer(pr.ownerId), dt, destroyedWallsOut);
                    double maxDim = ctx.gs.maze.w * Constants.CELL;
                    if (pr.pos.x < 0 || pr.pos.y < 0 || pr.pos.x > maxDim || pr.pos.y > maxDim) toRemove.add(pr);
                } else {
                    Vec2 next = new Vec2(pr.pos.x + pr.vel.x * dt, pr.pos.y + pr.vel.y * dt);
                    if (Raycast.hasWallBetween(ctx.gs.maze, pr.pos, next) || Physics.collidesWithMaze(ctx.gs, next, pr.radius)) {
                        pr.vel.x = 0; pr.vel.y = 0;
                    } else {
                        pr.pos.x = next.x; pr.pos.y = next.y;
                        if (pr.vel.len() > 1e-6) pr.angleRad = Math.atan2(pr.vel.y, pr.vel.x);
                    }
                    double maxDim = ctx.gs.maze.w * Constants.CELL;
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
                if (Raycast.hasWallBetween(ctx.gs.maze, oldPos, next) || Physics.collidesWithMaze(ctx.gs, next, colR)) {
                    if ("RED".equalsIgnoreCase(pr.kind) && pr.bouncesRemaining > 0) {
                        if (Physics.collidesWithMaze(ctx.gs, new Vec2(next.x, oldPos.y), pr.radius)) pr.vel.x *= -1;
                        if (Physics.collidesWithMaze(ctx.gs, new Vec2(oldPos.x, next.y), pr.radius)) pr.vel.y *= -1;
                        else { pr.vel.x *= -1; pr.vel.y *= -1; }
                        pr.bouncesRemaining--;
                        pr.pos.x = oldPos.x; pr.pos.y = oldPos.y;
                        net.broadcast(EventMsg.simple("RED_BOUNCE", pr.ownerId, "Bounce", tMs));
                        continue;
                    }
                    toRemove.add(pr);
                    net.broadcast(EventMsg.simple("PROJECTILE_HIT_WALL", pr.ownerId, pr.kind + "#" + pr.id, tMs));
                    continue;
                }
            }

            double maxDim = ctx.gs.maze.w * Constants.CELL;
            if (next.x < 0 || next.y < 0 || next.x > maxDim || next.y > maxDim) { toRemove.add(pr); continue; }
            pr.pos.x = next.x; pr.pos.y = next.y;
            if (pr.vel.len() > 1e-6) pr.angleRad = Math.atan2(pr.vel.y, pr.vel.x);

            handleProjectilePlayerHits(pr, k, oldPos, next, tMs, toRemove, toAdd);
        }

        ctx.gs.projectiles.removeAll(toRemove);
        ctx.gs.projectiles.addAll(toAdd);
        for (Player p : ctx.gs.players) p.vel.mul(0.86);
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

        double maxDim = ctx.gs.maze.w * Constants.CELL;
        if (next.x < 0 || next.y < 0 || next.x > maxDim || next.y > maxDim) { toRemove.add(pr); return; }

        double outerR = pr.radius;
        double innerR = Math.max(0.0, pr.visualThickness);
        double sweep = pr.visualLength > 0 ? pr.visualLength : Math.toRadians(170.0);

        destroyWallsInArcBand(pr.pos, innerR, outerR, pr.angleRad, sweep, destroyedWallsOut);

        for (Projectile other : ctx.gs.projectiles) {
            if (other.id == pr.id || other.kind == null || other.kind.endsWith("_FX") || other.kind.startsWith("BLUE")) continue;
            if (arcBandContainsPoint(pr.pos, 0.0, outerR + 8.0, pr.angleRad, sweep, other.pos, other.radius + 6.0)) toRemove.add(other);
        }

        for (Player target : ctx.gs.players) {
            if (target.id == pr.ownerId || ((pr.hitMask >> (target.id & 63)) & 1) != 0) continue;
            if (arcBandContainsPoint(pr.pos, 0.0, outerR, pr.angleRad, sweep, target.pos, Constants.PLAYER_RADIUS + 10.0)) {
                Player attacker = ctx.gs.getPlayer(pr.ownerId);
                if (attacker == null) attacker = target;
                List<EventMsg> evs = new ArrayList<>();
                AbilitySystem.applyDamage(attacker, target, pr.damage, tMs, evs);
                for (EventMsg ev : evs) net.broadcast(ev);
                Vec2 kb = new Vec2(target.pos.x - pr.pos.x, target.pos.y - pr.pos.y);
                if (kb.len() > 1e-6) kb.norm();
                target.vel.x += kb.x * pr.knockback;
                target.vel.y += kb.y * pr.knockback;
                pr.hitMask |= (1L << (target.id & 63));
                net.broadcast(EventMsg.simple("HIT", attacker.id, "WCS hit #" + target.id, tMs));
            }
        }
    }

    private void handleProjectilePlayerHits(Projectile pr, String k, Vec2 oldPos, Vec2 next, long tMs, List<Projectile> toRemove, List<Projectile> toAdd) {
        for (Player target : ctx.gs.players) {
            if (target.id == pr.ownerId) continue;

            if ("DISMANTLE".equals(k)) {
                double outerR = pr.radius;
                double innerR = Math.max(0.0, pr.visualThickness);
                double sweep = pr.visualLength > 0 ? pr.visualLength : Math.toRadians(120.0);
                boolean hit = arcBandContainsPoint(pr.pos, innerR, outerR, pr.angleRad, sweep, target.pos, Constants.PLAYER_RADIUS + 2.0) ||
                        arcBandContainsPoint(oldPos, innerR, outerR, pr.angleRad, sweep, target.pos, Constants.PLAYER_RADIUS + 2.0);
                if (hit) {
                    if (target.isGojo()) {
                        Player suk = ctx.gs.getOtherPlayer(target.id);
                        if (domains.isInfinityActiveNow(target, suk) && !pr.ignoreInfinity && !pr.sureHit) {
                            energy.drainGojoCE(target, CE_LOSS_ON_HIT, "DISMANTLE blocked", tMs, pr.ownerId);
                            toRemove.add(pr);
                            break;
                        }
                    }
                    applyHit(pr, target, tMs, 0, toRemove);
                    break;
                }
                continue;
            }

            if (ServerUtil.segmentIntersectsCircle(oldPos, next, target.pos, Constants.PLAYER_RADIUS + pr.radius)) {
                if ("FUGA".equalsIgnoreCase(k)) {
                    applyFugaExplosionDamage(next.copy(), pr.ownerId, tMs);
                    toAdd.add(spawnExplosionFx(pr.ownerId, next.copy()));
                    toAdd.add(spawnFireZone(pr.ownerId, next.copy()));
                    toRemove.add(pr);
                    net.broadcast(EventMsg.simple("PROJECTILE_HIT_PLAYER", pr.ownerId, k + " exploded", tMs));
                    break;
                }

                Player attacker = ctx.gs.getPlayer(pr.ownerId);
                if (attacker == null) attacker = target;
                boolean isSukunaAttack = attacker.isSukuna() || "DISMANTLE".equalsIgnoreCase(k);
                if (target.isGojo() && isSukunaAttack) energy.drainGojoCE(target, CE_LOSS_ON_HIT, "Hit by " + k, tMs, attacker.id);

                int dmg = pr.damage;
                if (target.isGojo() && "DISMANTLE".equalsIgnoreCase(k)) dmg = 0;
                applyHit(pr, target, tMs, dmg, toRemove);
                break;
            }
        }
    }

    private void applyHit(Projectile pr, Player target, long tMs, int dmg, List<Projectile> toRemove) {
        Player attacker = ctx.gs.getPlayer(pr.ownerId);
        if (attacker == null) attacker = target;
        List<EventMsg> evs = new ArrayList<>();
        AbilitySystem.applyDamage(attacker, target, dmg, tMs, evs);
        for (EventMsg ev : evs) net.broadcast(ev);
        Vec2 kb = new Vec2(target.pos.x - pr.pos.x, target.pos.y - pr.pos.y);
        if (kb.len() > 1e-6) kb.norm();
        target.vel.x += kb.x * pr.knockback;
        target.vel.y += kb.y * pr.knockback;
        toRemove.add(pr);
        net.broadcast(EventMsg.simple("PROJECTILE_HIT_PLAYER", pr.ownerId, pr.kind + " hit", tMs));
    }

    private void handleFireZoneDot(Projectile fire, long tMs) {
        if (fire.dotIntervalMs <= 0 || tMs < fire.nextDotTickMs) return;
        fire.nextDotTickMs = tMs + fire.dotIntervalMs;

        for (Player target : ctx.gs.players) {
            if (target.id == fire.ownerId || Vec2.dist(fire.pos, target.pos) > fire.radius) continue;
            if (target.isGojo() && target.cursedEnergy > 0) {
                energy.drainGojoCE(target, fire.dotCeDrain, "FIRE tick", tMs, fire.ownerId);
                continue;
            }
            Player attacker = ctx.gs.getPlayer(fire.ownerId);
            if (attacker == null) attacker = target;
            List<EventMsg> evs = new ArrayList<>();
            AbilitySystem.applyDamage(attacker, target, fire.dotDamage, tMs, evs);
            for (EventMsg ev : evs) net.broadcast(ev);
        }
    }

    private void applyFugaExplosionDamage(Vec2 center, int ownerId, long tMs) {
        Player attacker = ctx.gs.getPlayer(ownerId);
        if (attacker == null && !ctx.gs.players.isEmpty()) attacker = ctx.gs.players.get(0);
        for (Player target : ctx.gs.players) {
            if (target.id == ownerId || Vec2.dist(center, target.pos) > FUGA_EXPLOSION_RADIUS) continue;
            if (target.isGojo() && target.cursedEnergy > 0) {
                energy.drainGojoCE(target, Math.max(10, FIRE_DOT_CE_DRAIN * 2), "FUGA explosion", tMs, ownerId);
                continue;
            }
            if (attacker == null) attacker = target;
            List<EventMsg> evs = new ArrayList<>();
            AbilitySystem.applyDamage(attacker, target, FUGA_EXPLOSION_DAMAGE, tMs, evs);
            for (EventMsg ev : evs) net.broadcast(ev);
        }
    }

    private boolean arcBandContainsPoint(Vec2 center, double innerR, double outerR, double angCenter, double sweepRad, Vec2 p, double extraTol) {
        double dx = p.x - center.x;
        double dy = p.y - center.y;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < Math.max(0, innerR - extraTol) || dist > outerR + extraTol) return false;
        double angTol = (extraTol > 0.0 && dist > 1e-6) ? Math.asin(Math.min(1.0, extraTol / dist)) : 0.0;
        if (sweepRad + 2.0 * angTol >= Math.PI * 2.0) return true;
        double a = Math.atan2(dy, dx);
        double start = ServerUtil.normalizeAngle(angCenter - sweepRad * 0.5 - angTol);
        double end = ServerUtil.normalizeAngle(angCenter + sweepRad * 0.5 + angTol);
        return ServerUtil.angleInArc(a, start, end);
    }

    private void destroyWallsAround(Vec2 center, double radius, IntList destroyedOut) {
        int cell = Constants.CELL;
        int minX = Math.max(0, (int) Math.floor((center.x - radius) / cell));
        int maxX = Math.min(ctx.gs.maze.w - 1, (int) Math.floor((center.x + radius) / cell));
        int minY = Math.max(0, (int) Math.floor((center.y - radius) / cell));
        int maxY = Math.min(ctx.gs.maze.h - 1, (int) Math.floor((center.y + radius) / cell));
        double r2 = radius * radius;

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (ctx.gs.maze.cells[y][x] != 1) continue;
                double cx = x * cell + cell / 2.0;
                double cy = y * cell + cell / 2.0;
                if (((cx - center.x) * (cx - center.x) + (cy - center.y) * (cy - center.y)) <= r2 + (cell * cell * 0.18)) {
                    ctx.gs.maze.cells[y][x] = 0;
                    destroyedOut.add(y * ctx.gs.maze.w + x);
                }
            }
        }
    }

    private void destroyWallsInArcBand(Vec2 center, double innerR, double outerR, double angCenter, double sweepRad, IntList destroyedOut) {
        int cell = Constants.CELL;
        int minX = Math.max(0, (int) Math.floor((center.x - outerR - cell) / cell));
        int maxX = Math.min(ctx.gs.maze.w - 1, (int) Math.floor((center.x + outerR + cell) / cell));
        int minY = Math.max(0, (int) Math.floor((center.y - outerR - cell) / cell));
        int maxY = Math.min(ctx.gs.maze.h - 1, (int) Math.floor((center.y + outerR + cell) / cell));

        double start = ServerUtil.normalizeAngle(angCenter - sweepRad * 0.5);
        double end = ServerUtil.normalizeAngle(angCenter + sweepRad * 0.5);
        double inner2 = innerR * innerR;
        double outer2 = outerR * outerR;
        boolean[] mark = new boolean[ctx.gs.maze.w * ctx.gs.maze.h];

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (ctx.gs.maze.cells[y][x] != 1) continue;
                double cx = x * cell + cell / 2.0;
                double cy = y * cell + cell / 2.0;
                double d2 = (cx - center.x) * (cx - center.x) + (cy - center.y) * (cy - center.y);
                if (d2 < inner2 || d2 > outer2) continue;
                if (!ServerUtil.angleInArc(Math.atan2(cy - center.y, cx - center.x), start, end)) continue;

                ctx.gs.maze.cells[y][x] = 0;
                int idx = y * ctx.gs.maze.w + x;
                if (!mark[idx]) { mark[idx] = true; destroyedOut.add(idx); }
            }
        }
    }
}
