package com.identitycrisis.shared.model;

/** ALL magic numbers. Single source of truth for tuning. */
public final class GameConfig {

    private GameConfig() { }

    // Networking
    public static final int SERVER_PORT = 5137;
    public static final int TICK_RATE = 60;
    public static final long TICK_DURATION_NS = 1_000_000_000L / TICK_RATE;

    // Arena
    public static final double ARENA_WIDTH = 1200.0;
    public static final double ARENA_HEIGHT = 800.0;

    // Players
    public static final int MIN_PLAYERS = 4;
    public static final int MAX_PLAYERS = 8;
    public static final double PLAYER_SPEED = 200.0;    // px/sec
    public static final double PLAYER_RADIUS = 16.0;

    // Rounds
    public static final int WARMUP_ROUNDS = 2;
    public static final double ROUND_DURATION_SECONDS = 15.0;
    public static final double COUNTDOWN_SECONDS = 3.0;
    public static final double ELIMINATION_DISPLAY_SECONDS = 3.0;

    // Safe Zone
    public static final double SAFE_ZONE_RADIUS = 64.0;
    public static final double SAFE_ZONE_MIN_MARGIN = 100.0;

    // Carry/Throw
    public static final double CARRY_RANGE = 32.0;
    public static final double THROW_SPEED = 400.0;
    public static final double THROW_STUN_SECONDS = 0.5;

    // Physics
    public static final double VELOCITY_DAMPING = 0.95;
    public static final double VELOCITY_STOP_THRESHOLD = 0.1;
    public static final double SPAWN_RADIUS = 200.0;
    public static final double CARRYING_SPEED_MULTIPLIER = 0.6;

    // Chaos Events
    public static final double CHAOS_EVENT_MIN_DELAY = 3.0;
    public static final double CHAOS_EVENT_MAX_DELAY = 8.0;
    public static final double CHAOS_EVENT_DURATION = 5.0;
    public static final int FAKE_SAFE_ZONE_COUNT = 3;

    // Window
    public static final int WINDOW_WIDTH = 1280;
    public static final int WINDOW_HEIGHT = 720;
    public static final String WINDOW_TITLE = "Identity Crisis";


    // Server robustness
    /** Max pending inputs per client connection before silently dropping. */
    public static final int MAX_QUEUED_INPUTS = 120; // 2 seconds at 60 tps
}
