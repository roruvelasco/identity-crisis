package com.identitycrisis.shared.util;

/** Immutable 2D vector. Used everywhere for positions, velocities, directions. */
public record Vector2D(double x, double y) {

    public Vector2D add(Vector2D other) { throw new UnsupportedOperationException("stub"); }

    public Vector2D subtract(Vector2D other) { throw new UnsupportedOperationException("stub"); }

    public Vector2D multiply(double scalar) { throw new UnsupportedOperationException("stub"); }

    public Vector2D normalize() { throw new UnsupportedOperationException("stub"); }

    public double magnitude() { throw new UnsupportedOperationException("stub"); }

    public double distanceTo(Vector2D other) { throw new UnsupportedOperationException("stub"); }

    public static Vector2D zero() { return new Vector2D(0, 0); }
}
