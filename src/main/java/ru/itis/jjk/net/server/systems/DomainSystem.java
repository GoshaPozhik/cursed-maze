package ru.itis.jjk.net.server.systems;

import ru.itis.jjk.core.AbilitySystem;
import ru.itis.jjk.core.Player;
import ru.itis.jjk.net.msg.EventMsg;
import ru.itis.jjk.net.server.ConnectionManager;
import ru.itis.jjk.net.server.ServerContext;

import java.util.ArrayList;
import java.util.List;

import static ru.itis.jjk.net.server.ServerBalance.*;

public final class DomainSystem {

    private final ServerContext ctx;
    private final ConnectionManager net;

    public DomainSystem(ServerContext ctx, ConnectionManager net) {
        this.ctx = ctx;
        this.net = net;
    }

    public void tryStartDomain(Player actor, String kind, long tMs) {
        if (!"NONE".equalsIgnoreCase(actor.activeDomain) || tMs < actor.cdDomainUntilMs) return;
        if (actor.cursedEnergy < CE_COST_DOMAIN) {
            net.broadcast(EventMsg.simple("NO_CE", actor.id, "Need " + CE_COST_DOMAIN + " CE for DOMAIN", tMs));
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
        net.broadcast(EventMsg.simple("DOMAIN_START", actor.id, kind, tMs));
    }

    public void endDomain(Player actor, long tMs) {
        if ("NONE".equalsIgnoreCase(actor.activeDomain)) return;
        net.broadcast(EventMsg.simple("DOMAIN_END", actor.id, actor.activeDomain + " (CE=0)", tMs));
        actor.activeDomain = "NONE";
        actor.domainStartMs = 0;
        actor.domainRadius = 0;
        actor.domainMaxRadius = 0;
        actor.domainNextTickMs = 0;
        actor.domainCeDrainCarry = 0.0;
    }

    public boolean isInsideDomain(Player point, Player owner) {
        if (point == null || owner == null || "NONE".equalsIgnoreCase(owner.activeDomain)) return false;
        double dx = point.pos.x - owner.domainCenterX;
        double dy = point.pos.y - owner.domainCenterY;
        return (dx * dx + dy * dy) <= (owner.domainRadius * owner.domainRadius);
    }

    public boolean isInDomainOverlap(Player point, Player gojo, Player sukuna) {
        return isInsideDomain(point, gojo) && isInsideDomain(point, sukuna);
    }

    public boolean isInfinityActiveNow(Player gojo, Player sukuna) {
        if (gojo == null || gojo.cursedEnergy <= 0) return false;
        if (sukuna != null && "MS".equalsIgnoreCase(sukuna.activeDomain) && isInsideDomain(gojo, sukuna)) {
            return "UV".equalsIgnoreCase(gojo.activeDomain) && isInDomainOverlap(gojo, gojo, sukuna);
        }
        return true;
    }

    private Player findCharacter(String character) {
        for (Player p : ctx.gs.players) if (character.equalsIgnoreCase(p.character)) return p;
        return null;
    }

    private void broadcastEvents(List<EventMsg> evs) { for (EventMsg ev : evs) net.broadcast(ev); }

    public void updateDomains(double dt, long tMs) {
        Player gojo = findCharacter("GOJO");
        Player sukuna = findCharacter("SUKUNA");
        for (Player p : ctx.gs.players) p.stunned = false;

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

        for (Player owner : ctx.gs.players) {
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
}
