package ru.itis.jjk.net.server;

import ru.itis.jjk.core.*;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public final class ServerContext {
    public final long seed;
    public final GameState gs;
    public final int[][] baseMazeCells;

    public final AbilitySystem abilities = new AbilitySystem();
    public final AtomicInteger projectileIdGen = new AtomicInteger(1000);

    public ServerContext() {
        this.seed = new Random().nextLong();
        this.gs = new GameState(seed, Maze.generate(seed, Constants.MAZE_W, Constants.MAZE_H));
        this.baseMazeCells = ServerUtil.deepCopy(gs.maze.cells);
    }

    public long nowMs() { return System.currentTimeMillis(); }
}
