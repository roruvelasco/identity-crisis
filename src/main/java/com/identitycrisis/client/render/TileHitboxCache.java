package com.identitycrisis.client.render;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;

import java.util.HashMap;
import java.util.Map;

/**
 * Pre-computes per-tile alpha bitmasks from a tileset {@link Image} at map
 * load time.
 *
 * <p>Each bitmask is a {@code boolean[TILE_SIZE][TILE_SIZE]} where {@code true}
 * means the pixel has an alpha component above {@link #ALPHA_THRESHOLD} and is
 * therefore considered a solid, collidable pixel.
 *
 * <p>All bitmasks are keyed by their <em>global</em> tile ID
 * ({@code firstGid + localId}) so they integrate directly with the GID-keyed
 * maps already used by {@link MapManager}.
 *
 * <h2>Performance</h2>
 * {@link #build} runs exactly <strong>once per tileset at load time</strong> —
 * never per frame. The resulting {@code Map} is read-only during gameplay.
 * Memory cost: 256 booleans × unique tile count ≈ 64 KB for a full 256-tile
 * sheet.
 *
 * <h2>Alpha threshold</h2>
 * {@link #ALPHA_THRESHOLD} defaults to {@code 0} (any non-transparent pixel).
 * Raise to {@code 128} if anti-aliasing fringe pixels on tile edges cause
 * false positives.
 */
public final class TileHitboxCache {

    /** Native pixel size per tile — must match {@code MapManager.TILE_SIZE}. */
    public static final int TILE_SIZE = 16;

    /**
     * Minimum alpha value (0–255) for a pixel to be treated as solid.
     * {@code 0} = any non-transparent pixel is solid.
     */
    public static final int ALPHA_THRESHOLD = 0;

    private TileHitboxCache() {} // static utility — no instances

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds alpha bitmasks for every tile in one tileset.
     *
     * @param img       loaded tileset image (JavaFX {@link Image})
     * @param columns   number of tile columns in the tileset sheet
     * @param firstGid  global tile ID of local tile 0 in this tileset
     * @param tileCount total number of tiles declared in the tileset
     * @return immutable-safe map: global tile ID → {@code boolean[16][16]} mask;
     *         only tiles that have at least one solid pixel are stored
     */
    public static Map<Integer, boolean[][]> build(
            Image img, int columns, int firstGid, int tileCount) {

        Map<Integer, boolean[][]> result = new HashMap<>();
        if (img == null || img.isError()) return result;

        PixelReader pr = img.getPixelReader();
        if (pr == null) return result;

        int imgW = (int) img.getWidth();
        int imgH = (int) img.getHeight();

        for (int localId = 0; localId < tileCount; localId++) {
            int tileCol  = localId % columns;
            int tileRow  = localId / columns;
            int originX  = tileCol * TILE_SIZE;
            int originY  = tileRow * TILE_SIZE;

            // Skip tiles whose origin falls outside the image
            if (originX + TILE_SIZE > imgW || originY + TILE_SIZE > imgH) continue;

            boolean[][] mask   = new boolean[TILE_SIZE][TILE_SIZE];
            boolean     anySet = false;

            for (int py = 0; py < TILE_SIZE; py++) {
                for (int px = 0; px < TILE_SIZE; px++) {
                    // getArgb returns 0xAARRGGBB; shift right 24 to isolate alpha
                    int argb  = pr.getArgb(originX + px, originY + py);
                    int alpha = (argb >>> 24) & 0xFF;
                    if (alpha > ALPHA_THRESHOLD) {
                        mask[py][px] = true;
                        anySet = true;
                    }
                }
            }

            // Only store masks with at least one solid pixel to save memory
            if (anySet) {
                result.put(firstGid + localId, mask);
            }
        }

        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helper utilities used by MapManager
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if at least one pixel in {@code mask} is solid.
     * Used by {@link MapManager} to determine broad-phase tile solidity after
     * bitmask generation.
     *
     * @param mask a bitmask produced by {@link #build}, or {@code null}
     */
    public static boolean hasAnySolid(boolean[][] mask) {
        if (mask == null) return false;
        for (boolean[] row : mask) {
            for (boolean px : row) {
                if (px) return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if the pixel at tile-local coordinates
     * ({@code subX}, {@code subY}) is solid in {@code mask}.
     * Coordinates are clamped to [0, TILE_SIZE-1].
     *
     * @param mask a bitmask produced by {@link #build}, or {@code null}
     * @param subX pixel column within the tile (0–15)
     * @param subY pixel row    within the tile (0–15)
     */
    public static boolean pixelAt(boolean[][] mask, int subX, int subY) {
        if (mask == null) return false;
        subX = Math.max(0, Math.min(TILE_SIZE - 1, subX));
        subY = Math.max(0, Math.min(TILE_SIZE - 1, subY));
        return mask[subY][subX];
    }
}
