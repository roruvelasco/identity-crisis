package com.identitycrisis.shared.util;

/** Immutable 2D vector. Used everywhere for positions, velocities, directions. */
public record Vector2D(double x, double y) {

    public Vector2D add(Vector2D other) { return new Vector2D(x + other.x, y + other.y); }

    public Vector2D subtract(Vector2D other) { return new Vector2D(x - other.x, y - other.y); }

    public Vector2D multiply(double scalar) { return new Vector2D(x * scalar, y * scalar); }

    public Vector2D normalize() {
        double m = magnitude();
        return m == 0 ? zero() : new Vector2D(x / m, y / m);
    }

    public double magnitude() { return Math.sqrt(x * x + y * y); }

    public double distanceTo(Vector2D other) { return subtract(other).magnitude(); }

    public static Vector2D zero() { return new Vector2D(0, 0); }
}
