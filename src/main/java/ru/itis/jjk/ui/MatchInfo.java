package ru.itis.jjk.ui;

public record MatchInfo(
        int roundNumber,
        int winScore,
        int p1Id,
        int p2Id,
        int p1Score,
        int p2Score,
        boolean roundActive,
        int lastRoundWinnerId,
        long nextRoundStartMs,
        boolean matchOver,
        int matchWinnerId,
        boolean p1RematchReady,
        boolean p2RematchReady
) {
}
