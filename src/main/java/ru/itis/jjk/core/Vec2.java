package ru.itis.jjk.core;

public final class Vec2 {
    public double x;
    public double y;

    public Vec2() { this(0,0); }
    public Vec2(double x, double y) { this.x = x; this.y = y; }

    public Vec2 copy() { return new Vec2(x, y); }

    public Vec2 add(Vec2 o) { this.x += o.x; this.y += o.y; return this; }
    public Vec2 sub(Vec2 o) { this.x -= o.x; this.y -= o.y; return this; }
    public Vec2 mul(double k) { this.x *= k; this.y *= k; return this; }

    public double len() { return Math.sqrt(x*x + y*y); }

    public Vec2 norm() {
        double l = len();
        if (l > 1e-9) { x /= l; y /= l; }
        return this;
    }

    public static Vec2 of(double x, double y) { return new Vec2(x,y); }
    public static double dist(Vec2 a, Vec2 b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        return Math.sqrt(dx*dx + dy*dy);
    }
}
