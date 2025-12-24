package ru.itis.jjk.net.server;

import ru.itis.jjk.core.*;
import ru.itis.jjk.net.msg.PlayerStateDTO;
import ru.itis.jjk.net.msg.ProjectileStateDTO;
import ru.itis.jjk.net.msg.StateMsg;

public final class StateFactory {
    private StateFactory() {}

    public static StateMsg buildState(ServerContext ctx, MatchController match, long tMs) {
        StateMsg sm = new StateMsg();
        sm.serverTimeMs = tMs;

        sm.roundNumber = Math.max(1, match.roundNumber == 0 ? 1 : match.roundNumber);
        sm.winScore = ServerBalance.WIN_SCORE;

        sm.p1Id = match.p1Id; sm.p2Id = match.p2Id;
        sm.p1Score = match.p1Score; sm.p2Score = match.p2Score;

        sm.roundActive = match.roundActive;
        sm.lastRoundWinnerId = match.lastRoundWinnerId;
        sm.nextRoundStartMs = match.nextRoundStartMs;

        sm.matchOver = match.matchOver;
        sm.matchWinnerId = match.matchWinnerId;

        sm.p1RematchReady = match.p1RematchReady;
        sm.p2RematchReady = match.p2RematchReady;

        for (Player p : ctx.gs.players) {
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

        for (Projectile pr : ctx.gs.projectiles) {
            ProjectileStateDTO dto = new ProjectileStateDTO();
            dto.id = pr.id; dto.ownerId = pr.ownerId; dto.kind = pr.kind;
            dto.x = pr.pos.x; dto.y = pr.pos.y; dto.radius = pr.radius;
            dto.angleRad = pr.angleRad;
            dto.visualLength = pr.visualLength;
            dto.visualThickness = pr.visualThickness;
            sm.projectiles.add(dto);
        }

        return sm;
    }
}
