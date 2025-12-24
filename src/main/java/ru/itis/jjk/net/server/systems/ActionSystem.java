package ru.itis.jjk.net.server.systems;

import ru.itis.jjk.core.*;
import ru.itis.jjk.net.msg.EventMsg;
import ru.itis.jjk.net.server.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static ru.itis.jjk.net.server.ServerBalance.*;

public final class ActionSystem {

    private final ServerContext ctx;
    private final ConnectionManager net;
    private final CooldownTracker cds;
    private final MatchController match;
    private final DomainSystem domains;
    private final ProjectileSystem projectiles;

    public ActionSystem(ServerContext ctx,
                        ConnectionManager net,
                        CooldownTracker cds,
                        MatchController match,
                        DomainSystem domains,
                        ProjectileSystem projectiles) {
        this.ctx = ctx;
        this.net = net;
        this.cds = cds;
        this.match = match;
        this.domains = domains;
        this.projectiles = projectiles;
    }

    private void broadcastEvents(List<EventMsg> evs) { for (EventMsg ev : evs) net.broadcast(ev); }

    private boolean checkCost(Player p, int cost, String name, long tMs) {
        if (p.cursedEnergy < cost) {
            net.broadcast(EventMsg.simple("NO_CE", p.id, "Need " + cost + " CE for " + name, tMs));
            return true;
        }
        return false;
    }

    public void handleAction(Player actor, String actionRaw, long tMs) {
        if (actionRaw == null) return;
        String a = actionRaw.trim().toUpperCase(Locale.ROOT);
        if (a.isEmpty()) return;
        if (actor.stunned && !a.startsWith("DOMAIN")) return;
        if (!match.roundActive && !"REMATCH_READY".equals(a)) return;

        switch (a) {
            case "REMATCH_READY" -> {
                if (!match.matchOver) return;
                if (actor.id == match.p1Id) match.p1RematchReady = true;
                if (actor.id == match.p2Id) match.p2RematchReady = true;
                net.broadcast(EventMsg.simple("REMATCH", actor.id, actor.name + " is ready", tMs));
            }
            case "BLUE" -> {
                if (!actor.isGojo()) return;
                Projectile ownedBlue = findOwnedBlue(actor.id);
                if (ownedBlue != null && "BLUE".equalsIgnoreCase(ownedBlue.kind)
                        && actor.lastBlueCastMs > 0 && (tMs - actor.lastBlueCastMs) <= BLUE_MAX_WINDOW_MS) {
                    if (actor.cursedEnergy < CE_COST_BLUE_MAX_EXTRA) {
                        net.broadcast(EventMsg.simple("NO_CE", actor.id, "Need CE for BLUE_MAX", tMs));
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
                    cds.blue().put(actor.id, tMs);
                    net.broadcast(EventMsg.simple("COMBO", actor.id, "MAXIMUM OUTPUT: BLUE!", tMs));
                    return;
                }

                if (cds.checkCooldown(actor.id, tMs, cds.blue(), actor.cdBlueUntilMs, CD_BLUE_MS, "BLUE", net)) return;
                if (checkCost(actor, CE_COST_BLUE, "BLUE", tMs)) return;

                actor.cursedEnergy -= CE_COST_BLUE;
                actor.cdBlueUntilMs = tMs + CD_BLUE_MS;
                actor.lastBlueCastMs = tMs;
                cds.blue().put(actor.id, tMs);
                broadcastEvents(ctx.abilities.handleActions(ctx.gs, actor, List.of("BLUE"), tMs, ctx.projectileIdGen));
            }
            case "RED" -> {
                if (!actor.isGojo()) return;
                if (actor.lastBlueCastMs > 0 && (tMs - actor.lastBlueCastMs) <= PURPLE_COMBO_WINDOW_MS) {
                    if (tryCastPurple(actor, tMs)) return;
                }
                if (cds.checkCooldown(actor.id, tMs, cds.red(), actor.cdRedUntilMs, CD_RED_MS, "RED", net)) return;
                if (checkCost(actor, CE_COST_RED, "RED", tMs)) return;

                actor.cursedEnergy -= CE_COST_RED;
                actor.cdRedUntilMs = tMs + CD_RED_MS;
                cds.red().put(actor.id, tMs);
                broadcastEvents(ctx.abilities.handleActions(ctx.gs, actor, List.of("RED"), tMs, ctx.projectileIdGen));
            }
            case "DISMANTLE" -> {
                if (!actor.isSukuna()) return;
                if (cds.checkCooldown(actor.id, tMs, cds.dismantle(), actor.cdDismantleUntilMs, CD_DISMANTLE_MS, "DISMANTLE", net)) return;
                if (checkCost(actor, CE_COST_DISMANTLE, "DISMANTLE", tMs)) return;

                actor.cursedEnergy -= CE_COST_DISMANTLE;
                actor.cdDismantleUntilMs = tMs + CD_DISMANTLE_MS;
                cds.dismantle().put(actor.id, tMs);
                actor.lastDismantleCastMs = tMs;
                broadcastEvents(ctx.abilities.handleActions(ctx.gs, actor, List.of("DISMANTLE"), tMs, ctx.projectileIdGen));
            }
            case "DASH" -> {
                int dashCd = actor.isGojo() ? CD_DASH_GOJO_MS : CD_DASH_MS;
                if (cds.checkCooldown(actor.id, tMs, cds.dash(), actor.cdDashUntilMs, dashCd, "DASH", net)) return;
                int dashCost = actor.isGojo() ? CE_COST_DASH_GOJO : CE_COST_DASH;
                if (checkCost(actor, dashCost, "DASH", tMs)) return;

                actor.cursedEnergy -= dashCost;
                actor.cdDashUntilMs = tMs + dashCd;
                cds.dash().put(actor.id, tMs);
                actor.lastDashCastMs = tMs;

                Vec2 from = actor.pos.copy();
                Vec2 to = dashTeleport(actor, actor.isGojo() ? DASH_DISTANCE_GOJO : DASH_DISTANCE);
                actor.pos.x = to.x; actor.pos.y = to.y;
                actor.vel.x = 0; actor.vel.y = 0;

                Projectile fx = new Projectile(ctx.projectileIdGen.getAndIncrement(), actor.id, "DASH_FX");
                fx.pos.x = from.x; fx.pos.y = from.y;
                fx.ttlSec = 0.12; fx.radius = 1.0; fx.ignoreInfinity = true;
                fx.angleRad = actor.facingAngleRad;
                fx.visualLength = Math.max(10.0, Vec2.dist(from, to));
                fx.visualThickness = 7.0;
                ctx.gs.projectiles.add(fx);
                net.broadcast(EventMsg.simple("DASH", actor.id, "dash!", tMs));
            }
            case "CLEAVE" -> {
                if (actor.isSukuna() && actor.lastDashCastMs > 0 && actor.lastDismantleCastMs > 0 &&
                        actor.lastDismantleCastMs > actor.lastDashCastMs &&
                        (tMs - actor.lastDashCastMs) <= WORLD_SLASH_COMBO_WINDOW_MS &&
                        (tMs - actor.lastDismantleCastMs) <= WORLD_SLASH_COMBO_WINDOW_MS) {
                    if (tryCastWorldSlash(actor, tMs)) return;
                }
                if (!actor.isSukuna()) return;
                if (cds.checkCooldown(actor.id, tMs, cds.cleave(), actor.cdCleaveUntilMs, CD_CLEAVE_MS, "CLEAVE", net)) return;
                if (checkCost(actor, CE_COST_CLEAVE, "CLEAVE", tMs)) return;

                actor.cursedEnergy -= CE_COST_CLEAVE;
                actor.cdCleaveUntilMs = tMs + CD_CLEAVE_MS;
                cds.cleave().put(actor.id, tMs);

                int hits = doCleaveMelee(actor, tMs);
                Projectile fx = new Projectile(ctx.projectileIdGen.getAndIncrement(), actor.id, "CLEAVE_FX");
                fx.pos.x = actor.pos.x + Math.cos(actor.facingAngleRad) * 18.0;
                fx.pos.y = actor.pos.y + Math.sin(actor.facingAngleRad) * 18.0;
                fx.ttlSec = 0.16; fx.radius = 1.0; fx.ignoreInfinity = true;
                fx.angleRad = actor.facingAngleRad;
                fx.visualLength = 72.0; fx.visualThickness = 4.0;
                ctx.gs.projectiles.add(fx);
                net.broadcast(EventMsg.simple("CLEAVE", actor.id, hits > 0 ? ("hit " + hits) : "miss", tMs));
            }
            case "FUGA" -> {
                if (!actor.isSukuna()) return;
                if (cds.checkCooldown(actor.id, tMs, cds.fuga(), actor.cdFugaUntilMs, CD_FUGA_MS, "FUGA", net)) return;
                if (checkCost(actor, CE_COST_FUGA, "FUGA", tMs)) return;

                actor.cursedEnergy -= CE_COST_FUGA;
                actor.cdFugaUntilMs = tMs + CD_FUGA_MS;
                cds.fuga().put(actor.id, tMs);
                Projectile pr = projectiles.spawnFuga(actor, ctx.projectileIdGen.getAndIncrement());
                ctx.gs.projectiles.add(pr);
                net.broadcast(EventMsg.simple("SPAWN_PROJECTILE", actor.id, "FUGA#" + pr.id, tMs));
            }
            case "DOMAIN_GOJO" -> { if (actor.isGojo()) domains.tryStartDomain(actor, "UV", tMs); }
            case "DOMAIN_SUKUNA" -> { if (actor.isSukuna()) domains.tryStartDomain(actor, "MS", tMs); }
            default -> net.broadcast(EventMsg.simple("ACTION", actor.id, a, tMs));
        }
    }

    private void consumeGojoBlueForPurple(int gojoId) {
        for (Projectile p : ctx.gs.projectiles) {
            if (p.ownerId != gojoId) continue;
            String k = p.kind == null ? "" : p.kind.toUpperCase(Locale.ROOT);
            if ("BLUE".equals(k) || "BLUE_MAX".equals(k)) p.ttlSec = 0;
        }
    }

    private boolean tryCastPurple(Player actor, long tMs) {
        if (cds.checkCooldown(actor.id, tMs, cds.purple(), actor.cdPurpleUntilMs, CD_PURPLE_MS, "PURPLE", net)) return true;
        if (checkCost(actor, CE_COST_PURPLE, "PURPLE", tMs)) return true;

        actor.cursedEnergy -= CE_COST_PURPLE;
        consumeGojoBlueForPurple(actor.id);
        actor.cdPurpleUntilMs = tMs + CD_PURPLE_MS;
        cds.purple().put(actor.id, tMs);

        actor.cdRedUntilMs = Math.max(actor.cdRedUntilMs, tMs + CD_RED_MS);
        cds.red().put(actor.id, tMs);
        actor.lastBlueCastMs = 0;

        net.broadcast(EventMsg.simple("COMBO", actor.id, "HOLLOW PURPLE!", tMs));
        broadcastEvents(ctx.abilities.handleActions(ctx.gs, actor, List.of("PURPLE"), tMs, ctx.projectileIdGen));
        return true;
    }

    private boolean tryCastWorldSlash(Player actor, long tMs) {
        if (cds.checkCooldown(actor.id, tMs, cds.worldSlash(), actor.cdWorldSlashUntilMs, CD_WORLD_SLASH_MS, "WORLD SLASH", net)) return true;
        if (checkCost(actor, CE_COST_WORLD_SLASH, "WORLD SLASH", tMs)) return true;

        actor.cursedEnergy -= CE_COST_WORLD_SLASH;
        actor.cdWorldSlashUntilMs = tMs + CD_WORLD_SLASH_MS;
        cds.worldSlash().put(actor.id, tMs);
        castWorldSlash(actor, tMs);
        return true;
    }

    private void castWorldSlash(Player actor, long tMs) {
        double speed = 560.0;
        Projectile pr = new Projectile(ctx.projectileIdGen.getAndIncrement(), actor.id, "WCS");
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
        ctx.gs.projectiles.add(pr);
        net.broadcast(EventMsg.simple("COMBO", actor.id, "WORLD CUTTING SLASH!", tMs));
    }

    private int doCleaveMelee(Player actor, long tMs) {
        int hits = 0;
        double cosHalfFov = Math.cos(Math.toRadians(CLEAVE_FOV_DEG * 0.5));
        double fx = Math.cos(actor.facingAngleRad);
        double fy = Math.sin(actor.facingAngleRad);

        for (Player target : ctx.gs.players) {
            if (target.id == actor.id) continue;
            double dx = target.pos.x - actor.pos.x;
            double dy = target.pos.y - actor.pos.y;
            double dist = Math.hypot(dx, dy);
            if (dist > CLEAVE_RANGE) continue;
            double nx = dx / Math.max(1e-6, dist);
            double ny = dy / Math.max(1e-6, dist);
            if ((fx * nx + fy * ny) < cosHalfFov) continue;
            if (Raycast.hasWallBetween(ctx.gs.maze, actor.pos, target.pos)) continue;

            if (target.isGojo() && target.cursedEnergy > 0) {
                target.cursedEnergy = Math.max(0, target.cursedEnergy - CE_LOSS_ON_CLEAVE_BLOCK);
                net.broadcast(EventMsg.simple("INFINITY_BLOCK", target.id, "Infinity blocked CLEAVE", tMs));
                if (target.cursedEnergy == 0) net.broadcast(EventMsg.simple("INFINITY_DOWN", target.id, "Infinity disabled", tMs));
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

    private Vec2 dashTeleport(Player actor, double distance) {
        double dx = Math.cos(actor.facingAngleRad);
        double dy = Math.sin(actor.facingAngleRad);
        Vec2 lastOk = actor.pos.copy();
        int steps = (int) Math.ceil(distance / DASH_STEP);
        for (int i = 1; i <= steps; i++) {
            double d = Math.min(distance, i * DASH_STEP);
            Vec2 cand = new Vec2(actor.pos.x + dx * d, actor.pos.y + dy * d);
            if (cand.x < 0 || cand.y < 0 || cand.x > ctx.gs.maze.w * Constants.CELL || cand.y > ctx.gs.maze.h * Constants.CELL) break;
            if (!Physics.collidesWithMaze(ctx.gs, cand, Constants.PLAYER_RADIUS)) lastOk = cand;
            else break;
        }
        return lastOk;
    }

    private Projectile findOwnedBlue(int ownerId) {
        for (Projectile pr : ctx.gs.projectiles) {
            if (pr.ownerId == ownerId && pr.kind != null) {
                String k = pr.kind.toUpperCase(Locale.ROOT);
                if ("BLUE".equals(k) || "BLUE_MAX".equals(k)) return pr;
            }
        }
        return null;
    }
}
