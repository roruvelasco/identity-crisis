package com.identitycrisis.shared.model;

/** Static arena configuration: bounds, walls, obstacles. */
public class Arena {

    private double width;
    private double height;
    // Obstacle rectangles loaded from map definition

    public double getWidth() { return width; }

    public double getHeight() { return height; }

    /** Check bounds + obstacles. */
    public boolean isWall(double x, double y) {
        return x < 0 || y < 0 || x >= width || y >= height;
    }

    /** Hardcoded or file-loaded arena. */
    public static Arena loadDefault() {
        Arena a = new Arena();
        a.width  = GameConfig.ARENA_WIDTH;
        a.height = GameConfig.ARENA_HEIGHT;
        return a;
    }
}
