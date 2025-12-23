package ru.itis.jjk.core;

import ru.itis.jjk.net.msg.EventMsg;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public final class AbilitySystem {

    public List<EventMsg> handleActions(GameState gs,
                                        Player actor,
                                        List<String> actions,
                                        long serverTimeMs,
                                        AtomicInteger projectileIdGen) {

        List<EventMsg> events = new ArrayList<>();
        if (actions == null) return events;

        for (String raw : actions) {
            String a = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
            if (a.isEmpty()) continue;

            switch (a) {
                case "BLUE" -> {
                    if (!actor.isGojo()) {
                        events.add(EventMsg.simple("WARN", actor.id, "BLUE ignored (not GOJO)", serverTimeMs));
                        break;
                    }
                    Projectile pr = spawnBlue(gs, actor, projectileIdGen.getAndIncrement());
                    gs.projectiles.add(pr);
                    events.add(EventMsg.simple("SPAWN_PROJECTILE", actor.id, "BLUE#" + pr.id, serverTimeMs));
                }

                case "RED" -> {
                    if (!actor.isGojo()) {
                        events.add(EventMsg.simple("WARN", actor.id, "RED ignored (not GOJO)", serverTimeMs));
                        break;
                    }
                    Projectile pr = spawnRed(actor, projectileIdGen.getAndIncrement());
                    gs.projectiles.add(pr);
                    events.add(EventMsg.simple("SPAWN_PROJECTILE", actor.id, "RED#" + pr.id, serverTimeMs));
                }

                case "PURPLE" -> {
                    if (!actor.isGojo()) {
                        events.add(EventMsg.simple("WARN", actor.id, "PURPLE ignored (not GOJO)", serverTimeMs));
                        break;
                    }
                    Projectile pr = spawnPurple(gs, actor, projectileIdGen.getAndIncrement());
                    gs.projectiles.add(pr);
                    events.add(EventMsg.simple("SPAWN_PROJECTILE", actor.id, "PURPLE#" + pr.id, serverTimeMs));
                }

                case "DISMANTLE" -> {
                    if (!actor.isSukuna()) {
                        events.add(EventMsg.simple("WARN", actor.id, "DISMANTLE ignored (not SUKUNA)", serverTimeMs));
                        break;
                    }
                    Projectile pr = spawnDismantle(actor, projectileIdGen.getAndIncrement());
                    gs.projectiles.add(pr);
                    events.add(EventMsg.simple("SPAWN_PROJECTILE", actor.id, "DISMANTLE#" + pr.id, serverTimeMs));
                }

                default -> events.add(EventMsg.simple("ACTION", actor.id, a, serverTimeMs));
            }
        }

        return events;
    }

    private Projectile spawnRed(Player actor, int projId) {
        Projectile pr = new Projectile(projId, actor.id, "RED");
        pr.radius = 7.0;
        pr.damage = 18;
        pr.knockback = 620.0;
        pr.ttlSec = 2.2;
        pr.bouncesRemaining = 2;

        double speed = 270.0;
        pr.angleRad = actor.facingAngleRad;

        pr.pos.x = actor.pos.x + Math.cos(actor.facingAngleRad) * (Constants.PLAYER_RADIUS + pr.radius + 2);
        pr.pos.y = actor.pos.y + Math.sin(actor.facingAngleRad) * (Constants.PLAYER_RADIUS + pr.radius + 2);
        pr.vel.x = Math.cos(actor.facingAngleRad) * speed;
        pr.vel.y = Math.sin(actor.facingAngleRad) * speed;
        return pr;
    }

    private Projectile spawnDismantle(Player actor, int projId) {
    Projectile pr = new Projectile(projId, actor.id, "DISMANTLE");

    double outerR = 54.0;
    double innerR = 32.0;
    double sweep = Math.toRadians(120.0);

    pr.radius = outerR;
    pr.visualThickness = innerR;
    pr.visualLength = sweep;

    pr.damage = 14;
    pr.knockback = 220.0;

    double speed = 720.0;
    pr.ttlSec = 0.55;
    pr.angleRad = actor.facingAngleRad;

    pr.pos.x = actor.pos.x + Math.cos(actor.facingAngleRad) * (Constants.PLAYER_RADIUS + 10.0);
    pr.pos.y = actor.pos.y + Math.sin(actor.facingAngleRad) * (Constants.PLAYER_RADIUS + 10.0);
    pr.vel.x = Math.cos(actor.facingAngleRad) * speed;
    pr.vel.y = Math.sin(actor.facingAngleRad) * speed;
    return pr;
}


    
private Projectile spawnBlue(GameState gs, Player actor, int projId) {
    Projectile pr = new Projectile(projId, actor.id, "BLUE");
    pr.radius = 11.0;
    pr.damage = 0;
    pr.knockback = 0.0;
    pr.ttlSec = 1.35;

    double speed = 250.0;
    pr.angleRad = actor.facingAngleRad;

    Vec2 p = tryPlaceInFront(gs, actor, 34);
    pr.pos.x = p.x;
    pr.pos.y = p.y;

    pr.vel.x = Math.cos(actor.facingAngleRad) * speed;
    pr.vel.y = Math.sin(actor.facingAngleRad) * speed;

    return pr;
}

    private Projectile spawnPurple(GameState gs, Player actor, int projId) {
        Projectile pr = new Projectile(projId, actor.id, "PURPLE");
        pr.radius = 18.0;
        pr.damage = 35;
        pr.knockback = 520.0;
        pr.ttlSec = 2.2;
        pr.bouncesRemaining = 2;

        double speed = 360.0;
        pr.angleRad = actor.facingAngleRad;

        pr.pos.x = actor.pos.x + Math.cos(actor.facingAngleRad) * (Constants.PLAYER_RADIUS + pr.radius + 4);
        pr.pos.y = actor.pos.y + Math.sin(actor.facingAngleRad) * (Constants.PLAYER_RADIUS + pr.radius + 4);
        pr.vel.x = Math.cos(actor.facingAngleRad) * speed;
        pr.vel.y = Math.sin(actor.facingAngleRad) * speed;

        pr.visualLength = 0;
        pr.visualThickness = 0;
        return pr;
    }

    private Vec2 tryPlaceInFront(GameState gs, Player actor, double distance) {
        double ang = actor.facingAngleRad;
        for (int i = 0; i < 6; i++) {
            double d = distance - i * 8;
            Vec2 p = new Vec2(
                    actor.pos.x + Math.cos(ang) * d,
                    actor.pos.y + Math.sin(ang) * d
            );
            if (!Physics.collidesWithMaze(gs, p, 12)) return p;
        }
        return actor.pos.copy();
    }

    public static void applyDamage(GameState gs, Player attacker, Player target, int dmg, long t, List<EventMsg> outEvents) {
        if (dmg <= 0) {
            outEvents.add(EventMsg.simple("DAMAGE", attacker.id, "dealt 0 to #" + target.id + " (hp=" + target.hp + ")", t));
            return;
        }

        target.hp -= dmg;
        if (target.hp < 0) target.hp = 0;
        outEvents.add(EventMsg.simple("DAMAGE", attacker.id, "dealt " + dmg + " to #" + target.id + " (hp=" + target.hp + ")", t));

        if (target.hp == 0) {
            outEvents.add(EventMsg.simple("DOWN", target.id, "downed", t));
        }
    }
}
