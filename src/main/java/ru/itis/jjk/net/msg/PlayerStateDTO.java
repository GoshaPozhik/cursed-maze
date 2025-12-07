package ru.itis.jjk.net.msg;

public final class PlayerStateDTO {
    public int id;
    public String name;
    public String character;

    public double x;
    public double y;
    public double facing;
    // Хп
    public int hp;
    public int maxHp;
    // Энергия
    public int cursedEnergy;
    public int maxCursedEnergy;
    // Domain Expansion "MS" и "UV"
    public String activeDomain;
    public long domainStartMs;
    public double domainCenterX;
    public double domainCenterY;
    public double domainRadius;
    public double domainMaxRadius;
    // Кулдаун способок
    public long cdBlueUntilMs;
    public long cdRedUntilMs;
    public long cdPurpleUntilMs;
    public long cdDismantleUntilMs;
    public long cdDashUntilMs;
    public long cdCleaveUntilMs;
    public long cdWorldSlashUntilMs;
    public long cdFugaUntilMs;
    public long cdDomainUntilMs;

    public boolean stunned;
}
