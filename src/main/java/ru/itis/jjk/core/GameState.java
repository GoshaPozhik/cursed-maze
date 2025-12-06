package ru.itis.jjk.core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class GameState {
    public long seed;
    public Maze maze;

    public final List<Player> players = new CopyOnWriteArrayList<>();
    public final List<Projectile> projectiles = new CopyOnWriteArrayList<>();

    public GameState(long seed, Maze maze) {
        this.seed = seed;
        this.maze = maze;
    }

    public Player getPlayer(int id) {
        for (Player p : players) if (p.id == id) return p;
        return null;
    }

    public Player getOtherPlayer(int id) {
        for (Player p : players) if (p.id != id) return p;
        return null;
    }
}
