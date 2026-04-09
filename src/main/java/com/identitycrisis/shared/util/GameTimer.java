package com.identitycrisis.shared.util;

/** Countdown timer used by both server (authoritative) and client (display). */
public class GameTimer {

    private double remainingSeconds;
    private boolean running;

    public GameTimer(double durationSeconds) { this.remainingSeconds = durationSeconds; }

    public void start() { running = true; }

    public void stop() { running = false; }

    public void reset(double durationSeconds) { this.remainingSeconds = durationSeconds; running = false; }

    /** Subtract delta, clamp to 0. */
    public void tick(double deltaSeconds) { if (running) remainingSeconds = Math.max(0, remainingSeconds - deltaSeconds); }

    public boolean isExpired() { return remainingSeconds <= 0; }

    public double getRemaining() { return remainingSeconds; }

    public boolean isRunning() { return running; }
}
