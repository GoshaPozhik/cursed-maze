package ru.itis.jjk.net.server;

public final class ServerBalance {
    private ServerBalance() {}

    public static final double INFINITY_RADIUS = 18.0;
    public static final int CE_REGEN_PER_SEC = 10;
    public static final int CE_LOSS_ON_HIT = 10;
    public static final int CE_LOSS_ON_CLEAVE_BLOCK = 8;

    public static final int CE_COST_BLUE = 12;
    public static final int CE_COST_BLUE_MAX_EXTRA = 10;
    public static final int BLUE_MAX_WINDOW_MS = 900;
    public static final int CE_COST_RED = 14;
    public static final int CE_COST_PURPLE = 24;
    public static final int CE_COST_DISMANTLE = 10;

    public static final int CD_BLUE_MS = 900;
    public static final int CD_RED_MS = 1100;
    public static final int CD_PURPLE_MS = 2600;
    public static final int CD_DISMANTLE_MS = 800;

    public static final int CE_COST_DASH = 8;
    public static final int CE_COST_DASH_GOJO = 6;
    public static final int CE_COST_CLEAVE = 10;
    public static final int CE_COST_WORLD_SLASH = 32;
    public static final int CE_COST_FUGA = 18;
    public static final int CE_COST_DOMAIN = 60;

    public static final int CD_DASH_MS = 900;
    public static final int CD_DASH_GOJO_MS = 850;
    public static final int CD_CLEAVE_MS = 900;
    public static final int CD_WORLD_SLASH_MS = 5200;
    public static final int CD_FUGA_MS = 2200;
    public static final int CD_DOMAIN_MS = 12000;

    public static final int WORLD_SLASH_COMBO_WINDOW_MS = 900;
    public static final double DASH_DISTANCE = 110.0;
    public static final double DASH_DISTANCE_GOJO = 90.0;
    public static final double DASH_STEP = 6.0;

    public static final double CLEAVE_RANGE = 44.0;
    public static final double CLEAVE_FOV_DEG = 70.0;

    public static final int PURPLE_COMBO_WINDOW_MS = 300;

    public static final double FUGA_SPEED = 230.0;
    public static final double FUGA_RADIUS = 7.0;
    public static final double FUGA_TTL_SEC = 3.0;

    public static final double FUGA_EXPLOSION_RADIUS = 86.0;
    public static final int FUGA_EXPLOSION_DAMAGE = 22;

    public static final double FIRE_ZONE_RADIUS = 96.0;
    public static final double FIRE_ZONE_TTL_SEC = 2.8;
    public static final long FIRE_TICK_MS = 500;
    public static final int FIRE_DOT_DAMAGE = 6;
    public static final int FIRE_DOT_CE_DRAIN = 5;

    public static final double DOMAIN_UV_MAX_RADIUS = 220.0;
    public static final double DOMAIN_UV_EXPAND_MS = 650.0;
    public static final double DOMAIN_MS_RADIUS = 420.0;

    public static final double DOMAIN_CE_DRAIN_PER_SEC = 9.0;
    public static final double DOMAIN_CLASH_EXTRA_DRAIN_PER_SEC = 10.0;
    public static final long MS_SURE_HIT_TICK_MS = 500;
    public static final int MS_SURE_HIT_DAMAGE = 7;

    public static final int WIN_SCORE = 3;
    public static final long ROUND_INTERMISSION_MS = 2500;

    public static final double BLUE_EFFECT_RADIUS = 140.0;
    public static final double BLUE_PULL_STRENGTH = 480.0;
}
