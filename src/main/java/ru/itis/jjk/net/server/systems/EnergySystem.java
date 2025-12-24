package ru.itis.jjk.net.server.systems;

import ru.itis.jjk.core.Player;
import ru.itis.jjk.net.msg.EventMsg;
import ru.itis.jjk.net.server.ConnectionManager;
import ru.itis.jjk.net.server.ServerContext;

import static ru.itis.jjk.net.server.ServerBalance.*;

public final class EnergySystem {
    private final ServerContext ctx;
    private final ConnectionManager net;

    public EnergySystem(ServerContext ctx, ConnectionManager net) {
        this.ctx = ctx;
        this.net = net;
    }

    public void updateCERegen(double dt, long tMs) {
        for (Player p : ctx.gs.players) {
            if (p.cursedEnergy < p.maxCursedEnergy) {
                p.ceRegenCarry += CE_REGEN_PER_SEC * dt;
                int add = (int)Math.floor(p.ceRegenCarry);
                if (add > 0) {
                    int before = p.cursedEnergy;
                    p.cursedEnergy = Math.min(p.maxCursedEnergy, p.cursedEnergy + add);
                    p.ceRegenCarry -= add;
                    if (p.isGojo() && before == 0 && p.cursedEnergy > 0) {
                        net.broadcast(EventMsg.simple("INFINITY_UP", p.id, "Infinity restored", tMs));
                    }
                }
            } else {
                p.ceRegenCarry = 0.0;
            }
        }
    }

    public void drainGojoCE(Player gojo, int amount, String reason, long tMs, int sourceId) {
        int before = gojo.cursedEnergy;
        gojo.cursedEnergy = Math.max(0, gojo.cursedEnergy - amount);
        net.broadcast(EventMsg.simple("CE_DRAIN", sourceId,
                "Gojo CE: " + before + " -> " + gojo.cursedEnergy + " (" + reason + ")", tMs));
        if (before > 0 && gojo.cursedEnergy == 0) {
            net.broadcast(EventMsg.simple("INFINITY_DOWN", gojo.id, "Infinity disabled", tMs));
        }
    }
}
