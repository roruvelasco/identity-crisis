package com.identitycrisis.shared.util;

/** Countdown timer used by both server (authoritative) and client (display). */
public class GameTimer {

    private double remainingSeconds;
    private boolean running;

    public GameTimer(double durationSeconds) { }

    public void start() { }

    public void stop() { }

    public void reset(double durationSeconds) { }

    /** Subtract delta, clamp to 0. */
    public void tick(double deltaSeconds) { }

    public boolean isExpired() { throw new UnsupportedOperationException("stub"); }

    public double getRemaining() { throw new UnsupportedOperationException("stub"); }

    public boolean isRunning() { throw new UnsupportedOperationException("stub"); }
}
