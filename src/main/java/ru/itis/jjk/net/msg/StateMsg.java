package ru.itis.jjk.net.msg;

import java.util.ArrayList;
import java.util.List;

public final class StateMsg implements NetMessage {
    public final MessageType type = MessageType.STATE;

    public long serverTimeMs;

    public int roundNumber = 1;
    public int winScore = 3;

    public int p1Id = 0;
    public int p2Id = 0;
    public int p1Score = 0;
    public int p2Score = 0;

    public boolean roundActive = true;
    public int lastRoundWinnerId = 0; // 0 = draw / none
    public long nextRoundStartMs = 0;

    public boolean matchOver = false;
    public int matchWinnerId = 0;
    public boolean p1RematchReady = false;
    public boolean p2RematchReady = false;

    public List<PlayerStateDTO> players = new ArrayList<>();
    public List<ProjectileStateDTO> projectiles = new ArrayList<>();

    public StateMsg() {}

    @Override
    public MessageType type() { return type; }
}
