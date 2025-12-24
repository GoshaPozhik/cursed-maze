package ru.itis.jjk.net.server.systems;

import ru.itis.jjk.core.*;
import ru.itis.jjk.net.server.*;
import ru.itis.jjk.net.msg.InputMsg;

import java.util.Locale;

public final class PlayerSystem {

    private final ServerContext ctx;
    private final InputBuffer inputs;
    private final ActionSystem actions;
    private final ProjectileSystem projectiles;

    public PlayerSystem(ServerContext ctx,
                        InputBuffer inputs,
                        ActionSystem actions,
                        ProjectileSystem projectiles) {
        this.ctx = ctx;
        this.inputs = inputs;
        this.actions = actions;
        this.projectiles = projectiles;
    }

    public void drainOutOfRoundActions(long tMs) {
        for (Player p : ctx.gs.players) {
            String a;
            while ((a = inputs.pollAction(p.id)) != null) {
                String u = a.trim().toUpperCase(Locale.ROOT);
                if ("REMATCH_READY".equals(u)) actions.handleAction(p, u, tMs);
            }
        }
    }

    public void updatePlayers(double dt, long tMs) {
        for (Player p : ctx.gs.players) {
            InputMsg in = inputs.held(p.id);
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

            projectiles.applyBluePullToPlayerVel(p, dt);
            Physics.movePlayer(ctx.gs, p, dt);

            String a;
            while ((a = inputs.pollAction(p.id)) != null) {
                actions.handleAction(p, a, tMs);
            }
        }
    }
}
