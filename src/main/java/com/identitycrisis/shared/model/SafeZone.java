package com.identitycrisis.shared.model;

import com.identitycrisis.shared.util.Vector2D;

/**
 * Rectangular safe zone aligned to the TMX map's {@code safezoneN} regions.
 *
 * <p>All coordinates are in <em>world-pixel</em> space at the native 16 px/tile
 * scale — the same coordinate system used by player positions and by
 * {@code MapManager.worldToScreenX/Y}.  This makes a {@code SafeZone}
 * directly renderable on the client without any further coordinate
 * translation.
 *
 * @param id  Stable zone id (1–8) matching the TMX layer name.  Clients use
 *            this to deduplicate when rendering and to keep visual identity
 *            stable across snapshots.
 * @param x   World-pixel X of the rectangle's top-left corner.
 * @param y   World-pixel Y of the rectangle's top-left corner.
 * @param w   Rectangle width in world pixels.
 * @param h   Rectangle height in world pixels.
 */
public record SafeZone(int id, double x, double y, double w, double h) {

    /** Centre point of this rectangle in world-pixel space. */
    public Vector2D center() {
        return new Vector2D(x + w / 2.0, y + h / 2.0);
    }

    /** Whether the world-pixel point {@code (px, py)} is inside this rectangle. */
    public boolean contains(double px, double py) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }
}
