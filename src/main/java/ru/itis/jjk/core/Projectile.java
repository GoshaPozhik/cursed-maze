package ru.itis.jjk.core;

public final class Projectile {
    public int id;
    public int ownerId;
    public String kind; // "RED", "DISMANTLE"

    public Vec2 pos = new Vec2();
    public Vec2 vel = new Vec2();

    public double radius = 6.0;

    public double angleRad = 0.0;
    public double visualLength = 0.0;
    public double visualThickness = 2.0;

    public int damage = 15;
    public double knockback = 240.0;

    public boolean ignoreInfinity = false;
    public boolean sureHit = false;

    public boolean inInfinity = false;

    public double ttlSec = 2.5;

    public int bouncesRemaining = 0;   // рикошет Красного
    public double pauseSec = 0.0;      // Максимальный Синий
    public boolean orbiting = false;
    public boolean orbitInit = false;
    public double orbitRadius = 0.0;
    public double orbitOmega = 0.0;
    public double orbitAngle = 0.0;
    public double orbitStartAngle = 0.0;
    public double orbitTimeLeftSec = 0.0;
    public boolean anchorToOwner = false;
    public double centerX = 0.0;
    public double centerY = 0.0;

    public long hitMask = 0L;

    // ДОТ-эффекты (дамаг каждую секунду)
    public int dotDamage = 0;
    public int dotCeDrain = 0;
    public long nextDotTickMs = 0L;
    public long dotIntervalMs = 0L;

    public Projectile(int id, int ownerId, String kind) {
        this.id = id;
        this.ownerId = ownerId;
        this.kind = kind;
    }
}
