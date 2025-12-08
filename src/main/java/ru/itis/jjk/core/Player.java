package ru.itis.jjk.core;

public final class Player {
    public int id;
    public String name;
    public String character; // "GOJO" или "SUKUNA"

    public Vec2 pos = new Vec2();
    public Vec2 vel = new Vec2();
    public double facingAngleRad = 0;

    public int hp = 100;
    public int maxHp = 100;

    public int cursedEnergy = 100;
    public int maxCursedEnergy = 100;

    public double ceRegenCarry = 0.0;

    public boolean stunned = false;

    public String activeDomain = "NONE"; // NONE, UV, MS
    public long domainStartMs = 0;
    public double domainCenterX = 0;
    public double domainCenterY = 0;
    public double domainRadius = 0;
    public double domainMaxRadius = 0;
    public long domainNextTickMs = 0;
    public double domainCeDrainCarry = 0.0;

    public long cdBlueUntilMs = 0;
    public long cdRedUntilMs = 0;
    public long cdPurpleUntilMs = 0;

    public long cdDismantleUntilMs = 0;
    public long cdDashUntilMs = 0;
    public long cdCleaveUntilMs = 0;
    public long cdWorldSlashUntilMs = 0;
    public long cdFugaUntilMs = 0;
    public long cdDomainUntilMs = 0;

    // Отслеживание комбо
    public long lastBlueCastMs = 0;

    public long lastDashCastMs = 0;
    public long lastDismantleCastMs = 0;
    public long lastCleaveCastMs = 0;

    public Player(int id, String name, String character) {
        this.id = id;
        this.name = name;
        this.character = character;
    }

    public boolean isGojo() { return "GOJO".equalsIgnoreCase(character); }
    public boolean isSukuna() { return "SUKUNA".equalsIgnoreCase(character); }
}