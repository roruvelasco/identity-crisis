package com.identitycrisis.shared.model;

/** Static arena configuration: bounds, walls, obstacles. */
public class Arena {

    private double width;
    private double height;
    // Obstacle rectangles loaded from map definition

    public double getWidth() { throw new UnsupportedOperationException("stub"); }

    public double getHeight() { throw new UnsupportedOperationException("stub"); }

    /** Check bounds + obstacles. */
    public boolean isWall(double x, double y) { throw new UnsupportedOperationException("stub"); }

    /** Hardcoded or file-loaded arena. */
    public static Arena loadDefault() { throw new UnsupportedOperationException("stub"); }
}
