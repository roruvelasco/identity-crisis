package com.identitycrisis.shared.model;

/** Static arena configuration: bounds, walls, obstacles. */
public class Arena {

    private double width;
    private double height;

    public double getWidth() { return width; }

    public double getHeight() { return height; }

    /** Returns true if position is outside bounds or in obstacle. */
    public boolean isWall(double x, double y) {
        return x < 0 || y < 0 || x >= width || y >= height;
    }

    /** Returns hardcoded default arena. */
    public static Arena loadDefault() {
        Arena a = new Arena();
        a.width  = GameConfig.ARENA_WIDTH;
        a.height = GameConfig.ARENA_HEIGHT;
        return a;
    }
}
