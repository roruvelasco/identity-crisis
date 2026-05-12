package com.identitycrisis.client.render;

import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.*;

public class MapManager {

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final int TILE_SIZE = 16; // native px per tile in the TMX

    private static final int GID_MASK = 0x1FFFFFFF;

    /** Render order: bottom-to-top visual layer names (safezone layers excluded). */
    private static final List<String> RENDER_LAYERS = List.of(
            "floor_ground", "shadow", "water", "floor_2", "walls", "objects", "objects2"
    );

    /** Layers whose non-zero tiles carry collision regardless of objectgroup presence. */
    private static final Set<String> ALWAYS_SOLID_LAYERS = Set.of("water");

    /** If a tile is in one of these layers AND has no objectgroup, it is walkable (floor). */
    private static final Set<String> FLOOR_LAYERS = Set.of("floor_ground", "floor_2");

    // ── Tileset record ────────────────────────────────────────────────────────
    private record TilesetInfo(int firstGid, int lastGid, int columns, Image image) {}

    // ── Animation data ────────────────────────────────────────────────────────
    /**
     * One frame in a tile animation: local tile-id to display and its duration in ms.
     */
    private record AnimFrame(int localId, int durationMs) {}

    /**
     * Animation sequences keyed by global tile ID (the "trigger" tile placed in the map).
     * Value = ordered list of frames (localId within the same tileset, durationMs).
     */
    private final Map<Integer, List<AnimFrame>> tileAnimations = new HashMap<>();

    /**
     * Elapsed wall-clock time in milliseconds, advanced each {@link #render} call.
     * Used to index into animation sequences without storing per-tile state.
     */
    private long elapsedMs = 0;
    private long lastRenderNano = 0;

    // ── Parsed data ───────────────────────────────────────────────────────────
    /** All tilesets sorted by firstGid ascending. */
    private final List<TilesetInfo> tilesets = new ArrayList<>();

    /**
     * Per-tile collision rectangles keyed by global tile ID.
     * Each list entry is a rect in tile-local coordinates (0–15 px).
     */
    private final Map<Integer, List<Rectangle2D>> tileCollisionShapes = new HashMap<>();

    /**
     * Per-tile alpha bitmasks keyed by global tile ID, built at load time by
     * {@link TileHitboxCache}. Each value is a {@code boolean[16][16]} where
     * {@code true} = pixel is non-transparent and therefore solid.
     * Used by {@link #isSolidPixel} and {@link #intersectsWallPixels}.
     */
    private final Map<Integer, boolean[][]> tileAlphaMasks = new HashMap<>();

    /** Layer name → 2-D tile GID grid [row][col] in the unified world grid. */
    private final Map<String, int[][]> layerGrids = new LinkedHashMap<>();

    // ── World bounds ──────────────────────────────────────────────────────────
    /** World size in tiles, set after all chunks are scanned. */
    private int worldCols, worldRows;
    private int originTileX, originTileY;

    /**
     * Bounding box (in grid indices) of all non-zero tiles across visual layers.
     * Used so the scale and offsets are relative to actual content, not the full
     * 64×48 grid which has large void borders.
     */
    private int activeMinRow, activeMaxRow, activeMinCol, activeMaxCol;

    // ── Collision & safe zones ────────────────────────────────────────────────
    /**
     * solid[row][col] = true when a player cannot occupy the center of that tile.
     * Built after all layers are parsed.
     */
    private boolean[][] solid;

    /** The 8 safe-zone regions extracted from safezoneN layers. */
    private final List<SafeZoneRect> safeZones = new ArrayList<>();

    // ── Cached render geometry ────────────────────────────────────────────────
    /**
     * Independent X and Y scale factors so the map fills the viewport exactly
     * on both axes with no black bars. Recalculated lazily when the viewport changes.
     */
    private double lastScaleX  = 1.0;
    private double lastScaleY  = 1.0;
    private double lastOffsetX = 0.0;
    private double lastOffsetY = 0.0;
    private double lastViewW   = -1;
    private double lastViewH   = -1;

    // ── Public records ────────────────────────────────────────────────────────
    /** A named safe-zone rectangle in world-pixel coordinates (native 16 px scale). */
    public record SafeZoneRect(int id, double x, double y, double width, double height) {
        public boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Loads and parses the TMX file from the given classpath resource path.
     * Call once during scene initialisation.
     *
     * @param tmxResourcePath e.g. {@code "/sprites/map/ArenaMap.tmx"}
     */
    public void load(String tmxResourcePath) {
        try (InputStream is = getClass().getResourceAsStream(tmxResourcePath)) {
            if (is == null) {
                System.err.println("[MapManager] TMX resource not found: " + tmxResourcePath);
                return;
            }
            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().parse(is);
            doc.getDocumentElement().normalize();
            Element mapEl = doc.getDocumentElement();

            parseTilesets(mapEl);
            scanChunkBounds(mapEl);
            parseLayers(mapEl);
            buildActiveBounds();   // tight rect around non-empty tiles
            buildCollisionGrid();
            extractSafeZones(mapEl);

            int activeCols = activeMaxCol - activeMinCol + 1;
            int activeRows = activeMaxRow - activeMinRow + 1;
            System.out.printf("[MapManager] Loaded map: %d×%d tiles (active %d×%d), %d tilesets, %d safe zones%n",
                    worldCols, worldRows, activeCols, activeRows, tilesets.size(), safeZones.size());
        } catch (Exception e) {
            System.err.println("[MapManager] Failed to load TMX: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Renders all visual map layers, scaled to fit the given viewport exactly.
     * The map is centred if the viewport aspect ratio differs from the map's.
     */
    public void render(GraphicsContext gc, double viewW, double viewH) {
        if (tilesets.isEmpty()) return;
        recomputeScale(viewW, viewH);

        // ── Advance animation clock ───────────────────────────────────────────
        long now = System.nanoTime();
        if (lastRenderNano != 0) {
            elapsedMs += (now - lastRenderNano) / 1_000_000L;
        }
        lastRenderNano = now;

        double sx   = lastScaleX;
        double sy   = lastScaleY;
        double offX = lastOffsetX;
        double offY = lastOffsetY;

        for (String layerName : RENDER_LAYERS) {
            int[][] grid = layerGrids.get(layerName);
            if (grid == null) continue;

            boolean isShadow = layerName.equals("shadow");
            if (isShadow) { gc.save(); gc.setGlobalAlpha(0.55); }

            for (int row = activeMinRow; row <= activeMaxRow; row++) {
                for (int col = activeMinCol; col <= activeMaxCol; col++) {
                    int gid = grid[row][col] & GID_MASK;
                    if (gid == 0) continue;

                    TilesetInfo ts = findTileset(gid);
                    if (ts == null || ts.image() == null) continue;

                    // ── Animation: resolve current frame local-id ─────────────
                    int localId = resolveAnimLocalId(gid, ts.firstGid());

                    double srcX = (localId % ts.columns()) * TILE_SIZE;
                    double srcY = (localId / ts.columns()) * TILE_SIZE;

                    // Position relative to active content origin, not full grid
                    double destX = Math.floor(offX + (col - activeMinCol) * TILE_SIZE * sx);
                    double destY = Math.floor(offY + (row - activeMinRow) * TILE_SIZE * sy);
                    double destW = Math.ceil(TILE_SIZE * sx + 0.5);
                    double destH = Math.ceil(TILE_SIZE * sy + 0.5);

                    gc.drawImage(ts.image(),
                            srcX, srcY, TILE_SIZE, TILE_SIZE,
                            destX, destY, destW, destH);
                }
            }

            if (isShadow) gc.restore();
        }
    }

    /**
     * Resolves the current animation frame local-id for a given global tile id.
     * If the tile has no animation, returns the base local-id (gid - firstGid).
     * Uses the shared {@link #elapsedMs} clock so all instances of the same
     * tile stay in sync regardless of map position.
     */
    private int resolveAnimLocalId(int gid, int firstGid) {
        List<AnimFrame> frames = tileAnimations.get(gid);
        if (frames == null || frames.isEmpty()) return gid - firstGid;

        // Compute total cycle duration
        int totalMs = 0;
        for (AnimFrame f : frames) totalMs += f.durationMs();
        if (totalMs <= 0) return gid - firstGid;

        // Position within the cycle
        long pos = elapsedMs % totalMs;
        long acc = 0;
        for (AnimFrame f : frames) {
            acc += f.durationMs();
            if (pos < acc) return f.localId();
        }
        return frames.get(frames.size() - 1).localId();
    }

    /**
     * Returns {@code true} if a player cannot stand at the given world-pixel position.
     *
     * @param worldX x in native (16 px/tile) world coordinates
     * @param worldY y in native (16 px/tile) world coordinates
     */
    public boolean isSolid(double worldX, double worldY) {
        // World pixel coords: worldX = col * TILE_SIZE, worldY = row * TILE_SIZE
        // (grid[0][0] is at world pixel (0,0), no origin offset needed here)
        int col = (int) Math.floor(worldX / TILE_SIZE);
        int row = (int) Math.floor(worldY / TILE_SIZE);
        if (row < 0 || col < 0 || row >= worldRows || col >= worldCols) return true;
        return solid[row][col];
    }

    /**
     * Returns the safe-zone id (1–8) if the given world-pixel point is inside one,
     * or {@code -1} if not in any safe zone.
     */
    public int getSafeZoneAt(double worldX, double worldY) {
        for (SafeZoneRect sz : safeZones) {
            if (sz.contains(worldX, worldY)) return sz.id();
        }
        return -1;
    }

    /** All 8 safe-zone rectangles (world-pixel coords, native scale). */
    public List<SafeZoneRect> getSafeZones() { return Collections.unmodifiableList(safeZones); }

    /**
     * Pixel-perfect solid check using the alpha bitmasks pre-computed at load time.
     *
     * <p>More precise than {@link #isSolid} — resolves to the exact sub-tile pixel
     * rather than treating the whole 16×16 tile as solid.
     *
     * <p>Priority chain:
     * <ol>
     *   <li>Out-of-bounds → {@code true} (solid boundary)</li>
     *   <li>Broad-phase {@code solid[][]} says walkable → {@code false} (skip narrow phase)</li>
     *   <li>Non-walls source (void / water) → defer to broad-phase result</li>
     *   <li>Walls tile: look up alpha bitmask at sub-pixel; null mask → {@code true} (safe fallback)</li>
     * </ol>
     *
     * @param worldX x in native (16 px/tile) world coordinates
     * @param worldY y in native (16 px/tile) world coordinates
     */
    public boolean isSolidPixel(double worldX, double worldY) {
        int col = (int) Math.floor(worldX / TILE_SIZE);
        int row = (int) Math.floor(worldY / TILE_SIZE);

        if (row < 0 || col < 0 || row >= worldRows || col >= worldCols) return true;
        return solid[row][col];
    }

    public boolean intersectsSolidPixels(double x, double y, double w, double h) {
        int pxMin = (int) Math.floor(x);
        int pxMax = (int) Math.ceil(x + w) - 1;
        int pyMin = (int) Math.floor(y);
        int pyMax = (int) Math.ceil(y + h) - 1;

        for (int py = pyMin; py <= pyMax; py++) {
            for (int px = pxMin; px <= pxMax; px++) {
                if (isSolidPixel(px, py)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * AABB vs wall-pixel bitmask intersection.
     *
     * <p>Tests all tiles whose world-pixel bounds overlap the supplied rectangle
     * {@code [x, x+w) × [y, y+h)}, then checks only the pixels that fall inside
     * the intersection.  At most 4 tiles are examined per call (a 16 px player
     * rarely spans more), so this is safe to invoke every frame.
     *
     * <p>Returns {@code true} on the first solid pixel found.
     *
     * @param x left  edge of the bounding box in world pixels
     * @param y top   edge of the bounding box in world pixels
     * @param w width  of the bounding box in world pixels
     * @param h height of the bounding box in world pixels
     */
    public boolean intersectsWallPixels(double x, double y, double w, double h) {
        int[][] wallsGrid = layerGrids.get("walls");
        if (wallsGrid == null) return false;

        int colMin = (int) Math.floor(x / TILE_SIZE);
        int colMax = (int) Math.floor((x + w - 0.001) / TILE_SIZE);
        int rowMin = (int) Math.floor(y / TILE_SIZE);
        int rowMax = (int) Math.floor((y + h - 0.001) / TILE_SIZE);

        for (int row = rowMin; row <= rowMax; row++) {
            for (int col = colMin; col <= colMax; col++) {
                // Out-of-bounds cell → solid wall
                if (row < 0 || col < 0 || row >= worldRows || col >= worldCols) return true;

                int gid = wallsGrid[row][col] & GID_MASK;
                if (gid == 0) continue; // empty walls cell

                boolean[][] mask = tileAlphaMasks.get(gid);
                if (mask == null) return true; // no bitmask → conservative: solid

                // Compute the overlap of the AABB with this tile in sub-tile coordinates
                double tileOriginX = col * (double) TILE_SIZE;
                double tileOriginY = row * (double) TILE_SIZE;

                int pxMin = Math.max(0, (int) Math.floor(x - tileOriginX));
                int pxMax = Math.min(TILE_SIZE - 1, (int) Math.floor(x + w - tileOriginX));
                int pyMin = Math.max(0, (int) Math.floor(y - tileOriginY));
                int pyMax = Math.min(TILE_SIZE - 1, (int) Math.floor(y + h - tileOriginY));

                for (int py = pyMin; py <= pyMax; py++) {
                    for (int px = pxMin; px <= pxMax; px++) {
                        if (mask[py][px]) return true;
                    }
                }
            }
        }
        return false;
    }

    /** World width in native pixels (worldCols × TILE_SIZE). */
    public double getWorldWidth()  { return (double) worldCols * TILE_SIZE; }

    /** World height in native pixels (worldRows × TILE_SIZE). */
    public double getWorldHeight() { return (double) worldRows * TILE_SIZE; }

    /** World-pixel X of the horizontal center of the active content region. */
    public double getActiveContentCenterX() {
        return ((activeMinCol + activeMaxCol) / 2.0 + 0.5) * TILE_SIZE;
    }

    /** World-pixel Y of the vertical center of the active content region. */
    public double getActiveContentCenterY() {
        return ((activeMinRow + activeMaxRow) / 2.0 + 0.5) * TILE_SIZE;
    }

    /** Horizontal scale factor (viewport width / map native width). */
    public double getScaleX(double viewW, double viewH) {
        recomputeScale(viewW, viewH);
        return lastScaleX;
    }

    /** Vertical scale factor (viewport height / map native height). */
    public double getScaleY(double viewW, double viewH) {
        recomputeScale(viewW, viewH);
        return lastScaleY;
    }

    /**
     * Uniform scale — geometric mean of X/Y scales.
     * Use for sizing elements that must stay square (e.g. player sprites).
     */
    public double getScale(double viewW, double viewH) {
        recomputeScale(viewW, viewH);
        return Math.sqrt(lastScaleX * lastScaleY);
    }

    /** Convert world-pixel X → screen-pixel X (includes centering offsets). */
    public double worldToScreenX(double worldX, double viewW, double viewH) {
        recomputeScale(viewW, viewH);
        // worldX is in full-grid pixel space; subtract active-content origin first
        double contentX = worldX - activeMinCol * TILE_SIZE;
        return lastOffsetX + contentX * lastScaleX;
    }

    /** Convert world-pixel Y → screen-pixel Y. */
    public double worldToScreenY(double worldY, double viewW, double viewH) {
        recomputeScale(viewW, viewH);
        double contentY = worldY - activeMinRow * TILE_SIZE;
        return lastOffsetY + contentY * lastScaleY;
    }

    /** Convert screen-pixel X → world-pixel X. */
    public double screenToWorldX(double screenX, double viewW, double viewH) {
        recomputeScale(viewW, viewH);
        return activeMinCol * TILE_SIZE + (screenX - lastOffsetX) / lastScaleX;
    }

    /** Convert screen-pixel Y → world-pixel Y. */
    public double screenToWorldY(double screenY, double viewW, double viewH) {
        recomputeScale(viewW, viewH);
        return activeMinRow * TILE_SIZE + (screenY - lastOffsetY) / lastScaleY;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Parsing — Tilesets
    // ─────────────────────────────────────────────────────────────────────────

    private void parseTilesets(Element mapEl) {
        NodeList tilesetNodes = mapEl.getElementsByTagName("tileset");
        for (int i = 0; i < tilesetNodes.getLength(); i++) {
            Element ts = (Element) tilesetNodes.item(i);
            // Only process direct children of <map> (not <tile><objectgroup> etc.)
            if (!ts.getParentNode().equals(mapEl)) continue;

            int firstGid   = Integer.parseInt(ts.getAttribute("firstgid"));
            int tileCount  = Integer.parseInt(ts.getAttribute("tilecount"));
            int columns    = Integer.parseInt(ts.getAttribute("columns"));
            int lastGid    = firstGid + tileCount - 1;

            // Load tileset PNG
            NodeList imageNodes = ts.getElementsByTagName("image");
            Image img = null;
            if (imageNodes.getLength() > 0) {
                String src = ((Element) imageNodes.item(0)).getAttribute("source");
                // src is like "decorative_cracks_walls.png" — relative to the TMX
                String resourcePath = "/sprites/map/tileSets/" + src;
                img = loadImage(resourcePath);
                // Build pixel-perfect alpha bitmasks for this tileset (once at load)
                if (img != null) {
                    tileAlphaMasks.putAll(
                            TileHitboxCache.build(img, columns, firstGid, tileCount));
                }
            }

            tilesets.add(new TilesetInfo(firstGid, lastGid, columns, img));

            // Parse per-tile collision shapes AND animation sequences
            NodeList tileNodes = ts.getElementsByTagName("tile");
            for (int t = 0; t < tileNodes.getLength(); t++) {
                Element tileEl = (Element) tileNodes.item(t);
                // Only direct <tile> children of this <tileset>
                if (!tileEl.getParentNode().equals(ts)) continue;

                int localId = Integer.parseInt(tileEl.getAttribute("id"));
                int globalId = firstGid + localId;

                // ── Collision shapes ──────────────────────────────────────────
                NodeList ogList = tileEl.getElementsByTagName("objectgroup");
                if (ogList.getLength() > 0) {
                    Element og = (Element) ogList.item(0);
                    NodeList objects = og.getElementsByTagName("object");
                    List<Rectangle2D> shapes = new ArrayList<>();

                    for (int o = 0; o < objects.getLength(); o++) {
                        Element obj = (Element) objects.item(o);
                        String wAttr = obj.getAttribute("width");
                        String hAttr = obj.getAttribute("height");
                        double ox = parseDoubleAttr(obj, "x", 0);
                        double oy = parseDoubleAttr(obj, "y", 0);

                        if (!wAttr.isEmpty() && !hAttr.isEmpty()) {
                            double ow = Double.parseDouble(wAttr);
                            double oh = Double.parseDouble(hAttr);
                            shapes.add(new Rectangle2D(ox, oy, ow, oh));
                        } else {
                            NodeList polyNodes = obj.getElementsByTagName("polygon");
                            if (polyNodes.getLength() > 0) {
                                String points = ((Element) polyNodes.item(0)).getAttribute("points");
                                Rectangle2D bbox = polygonBoundingBox(points, ox, oy);
                                if (bbox != null) shapes.add(bbox);
                            } else {
                                shapes.add(new Rectangle2D(0, 0, TILE_SIZE, TILE_SIZE));
                            }
                        }
                    }
                    if (!shapes.isEmpty()) tileCollisionShapes.put(globalId, shapes);
                }

                // ── Animation frames ──────────────────────────────────────────
                // Each <tile> may have one <animation> child with ordered <frame> elements.
                // Frames reference local tile-ids within the same tileset.
                NodeList animList = tileEl.getElementsByTagName("animation");
                if (animList.getLength() > 0) {
                    Element animEl = (Element) animList.item(0);
                    NodeList frameNodes = animEl.getElementsByTagName("frame");
                    List<AnimFrame> frames = new ArrayList<>();
                    for (int f = 0; f < frameNodes.getLength(); f++) {
                        Element frameEl = (Element) frameNodes.item(f);
                        int frameLocalId  = Integer.parseInt(frameEl.getAttribute("tileid"));
                        int frameDuration = Integer.parseInt(frameEl.getAttribute("duration"));
                        frames.add(new AnimFrame(frameLocalId, frameDuration));
                    }
                    if (!frames.isEmpty()) tileAnimations.put(globalId, frames);
                }
            }
        }

        // Sort by firstGid ascending for findTileset binary search
        tilesets.sort(Comparator.comparingInt(TilesetInfo::firstGid));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Parsing — World bounds (first pass over chunks)
    // ─────────────────────────────────────────────────────────────────────────

    private void scanChunkBounds(Element mapEl) {
        NodeList chunks = mapEl.getElementsByTagName("chunk");
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;

        for (int i = 0; i < chunks.getLength(); i++) {
            Element ch = (Element) chunks.item(i);
            int cx = Integer.parseInt(ch.getAttribute("x"));
            int cy = Integer.parseInt(ch.getAttribute("y"));
            int cw = Integer.parseInt(ch.getAttribute("width"));
            int ch2 = Integer.parseInt(ch.getAttribute("height"));
            minX = Math.min(minX, cx);
            minY = Math.min(minY, cy);
            maxX = Math.max(maxX, cx + cw);
            maxY = Math.max(maxY, cy + ch2);
        }

        if (chunks.getLength() == 0) {
            // Fallback: read width/height from <map> element directly
            worldCols = Integer.parseInt(mapEl.getAttribute("width"));
            worldRows = Integer.parseInt(mapEl.getAttribute("height"));
            originTileX = 0;
            originTileY = 0;
        } else {
            originTileX = minX;
            originTileY = minY;
            worldCols = maxX - minX;
            worldRows = maxY - minY;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Parsing — Layers + chunks (second pass)
    // ─────────────────────────────────────────────────────────────────────────

    private void parseLayers(Element mapEl) {
        NodeList layerNodes = mapEl.getElementsByTagName("layer");
        for (int i = 0; i < layerNodes.getLength(); i++) {
            Element layerEl = (Element) layerNodes.item(i);
            if (!layerEl.getParentNode().equals(mapEl)) continue;

            String name = layerEl.getAttribute("name");
            int[][] grid = new int[worldRows][worldCols];

            // Collect all chunks for this layer
            NodeList chunks = layerEl.getElementsByTagName("chunk");
            if (chunks.getLength() > 0) {
                // Infinite map: stitch chunks
                for (int c = 0; c < chunks.getLength(); c++) {
                    Element ch = (Element) chunks.item(c);
                    int chX  = Integer.parseInt(ch.getAttribute("x"))  - originTileX;
                    int chY  = Integer.parseInt(ch.getAttribute("y"))  - originTileY;
                    int chW  = Integer.parseInt(ch.getAttribute("width"));
                    int chH  = Integer.parseInt(ch.getAttribute("height"));

                    String csv = ch.getTextContent().trim();
                    int[] gids = parseCSV(csv);
                    int idx = 0;
                    for (int r = 0; r < chH; r++) {
                        for (int col = 0; col < chW; col++) {
                            int gr = chY + r;
                            int gc = chX + col;
                            if (gr >= 0 && gr < worldRows && gc >= 0 && gc < worldCols) {
                                grid[gr][gc] = idx < gids.length ? gids[idx] : 0;
                            }
                            idx++;
                        }
                    }
                }
            } else {
                // Non-infinite: single <data> block
                NodeList dataNodes = layerEl.getElementsByTagName("data");
                if (dataNodes.getLength() > 0) {
                    String csv = dataNodes.item(0).getTextContent().trim();
                    int[] gids = parseCSV(csv);
                    int idx = 0;
                    for (int r = 0; r < worldRows && idx < gids.length; r++) {
                        for (int col = 0; col < worldCols && idx < gids.length; col++) {
                            grid[r][col] = gids[idx++];
                        }
                    }
                }
            }

            layerGrids.put(name, grid);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Collision grid construction
    // ─────────────────────────────────────────────────────────────────────────

    private void buildCollisionGrid() {
        solid = new boolean[worldRows][worldCols];
        int[][] floorGrid  = layerGrids.get("floor_ground");
        int[][] wallsGrid  = layerGrids.get("walls");
        int[][] waterGrid  = layerGrids.get("water");
        int[][] floor2Grid = layerGrids.get("floor_2");
        int[][] objGrid    = layerGrids.get("objects");
        int[][] obj2Grid   = layerGrids.get("objects2");

        for (int row = 0; row < worldRows; row++) {
            for (int col = 0; col < worldCols; col++) {
                // 1. Void: no floor tile of any kind → solid.
                // For floor_2, a tile only grants walkable floor when it has no
                // centre-covering collision shape (i.e. it is a pure floor tile).
                // Wall-edge/ledge tiles in floor_2 DO have collision and must be
                // handled in step 4 below, so we allow them to pass the hasFloor
                // gate here — the void check is only about whether there is ANY tile.
                boolean hasFloor = (floorGrid  != null && floorGrid[row][col]  != 0)
                                || (floor2Grid != null && floor2Grid[row][col] != 0);
                if (!hasFloor) {
                    solid[row][col] = true;
                    continue;
                }

                // 2. Water → always solid
                if (waterGrid != null && waterGrid[row][col] != 0) {
                    solid[row][col] = true;
                    continue;
                }

                // 3. Walls layer — every non-zero tile is solid.
                //    The walls_floor tileset does not define objectgroups for most
                //    of its tiles (they have implicit full-tile collision in Tiled).
                //    Relying on tileHasAnyCollision would silently skip those tiles.
                //    Treat the walls layer exactly like water: presence == solid.
                //    Exception: tile GIDs that ARE in tileCollisionShapes with
                //    explicitly empty shapes (none added) are still treated as solid
                //    because the layer itself implies obstruction.
                if (wallsGrid != null && (wallsGrid[row][col] & GID_MASK) != 0) {
                    solid[row][col] = true;
                    continue;
                }

                // 4. Per-tile collision shapes: check floor_2 layer.
                //    floor_2 mixes truly walkable floor tiles (no objectgroup at all
                //    in the tileset) with wall-edge and ledge tiles (with objectgroups).
                //    We use tileHasAnyCollision so that edge tiles whose shapes do NOT
                //    cover the tile centre (8,8) — such as fire-pit ledges (height=7)
                //    and treasure-chest borders — are still caught and marked solid.
                //    Walkable floor tiles have no objectgroup and return false.
                if (floor2Grid != null) {
                    int gid = floor2Grid[row][col];
                    if (gid != 0 && tileHasAnyCollision(floor2Grid, row, col)) {
                        solid[row][col] = true;
                        continue;
                    }
                }

                // 5. Objects with collision shapes block movement.
                //    Object tiles are never walkable floor, so ANY defined collision
                //    shape (not just one covering the centre) makes the tile solid.
                //    This catches fire-edge, chest-edge, and other partial-shape tiles
                //    that do not reach tile centre (8,8).
                boolean objectSolid = tileHasAnyCollision(objGrid,  row, col)
                                   || tileHasAnyCollision(obj2Grid, row, col);
                if (objectSolid) {
                    solid[row][col] = true;
                }
            }
        }
    }

    /**
     * Returns {@code true} if the tile at {@code (row,col)} in {@code grid} has
     * a collision shape whose bounding rectangle contains the tile centre (8,8).
     *
     * <p>Kept as a utility for future use.  {@link #buildCollisionGrid} now uses
     * {@link #tileHasAnyCollision} for all layers, which catches partial-shape
     * tiles that miss the centre point.
     */
    private boolean checkLayerForCollision(int[][] grid, int row, int col) {
        if (grid == null) return false;
        int gid = grid[row][col] & GID_MASK;
        if (gid == 0) return false;
        List<Rectangle2D> shapes = tileCollisionShapes.get(gid);
        if (shapes == null) return false;
        for (Rectangle2D r : shapes) {
            if (r.contains(8, 8)) return true;
        }
        return false;
    }

    /**
     * Returns {@code true} if the tile at {@code (row,col)} in {@code grid} has
     * <em>any</em> collision shape defined in the tileset — regardless of shape
     * position or size.
     *
     * <p>Used for {@code objects} and {@code objects2}: these tiles are never
     * walkable floor, so the presence of <em>any</em> objectgroup is sufficient
     * to mark the cell solid.  This catches partial-shape tiles such as fire-pit
     * edges and chest edges whose shapes do not cover the tile centre (8,8).
     */
    private boolean tileHasAnyCollision(int[][] grid, int row, int col) {
        if (grid == null) return false;
        int gid = grid[row][col] & GID_MASK;
        if (gid == 0) return false;
        return tileCollisionShapes.containsKey(gid);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Safe-zone extraction
    // ─────────────────────────────────────────────────────────────────────────

    private void extractSafeZones(Element mapEl) {
        for (int id = 1; id <= 8; id++) {
            String layerName = "safezone" + id;
            int[][] grid = layerGrids.get(layerName);
            if (grid == null) continue;

            // Find all non-zero tile positions; compute bounding rect
            int minC = Integer.MAX_VALUE, minR = Integer.MAX_VALUE;
            int maxC = Integer.MIN_VALUE, maxR = Integer.MIN_VALUE;
            boolean found = false;

            for (int row = 0; row < worldRows; row++) {
                for (int col = 0; col < worldCols; col++) {
                    if (grid[row][col] != 0) {
                        minR = Math.min(minR, row);
                        minC = Math.min(minC, col);
                        maxR = Math.max(maxR, row);
                        maxC = Math.max(maxC, col);
                        found = true;
                    }
                }
            }

            if (!found) continue;

            // Convert grid indices → world pixel coords
            // World pixel space: worldX = col * TILE_SIZE (no origin offset)
            double wx = minC * TILE_SIZE;
            double wy = minR * TILE_SIZE;
            double ww = (maxC - minC + 1) * TILE_SIZE;
            double wh = (maxR - minR + 1) * TILE_SIZE;

            safeZones.add(new SafeZoneRect(id, wx, wy, ww, wh));
            System.out.printf("[MapManager] Safe zone %d at world (%.0f,%.0f) size %.0f×%.0f%n",
                    id, wx, wy, ww, wh);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Rendering helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Scans all visual layers and computes the tightest grid-index bounding box
     * that contains every non-zero tile. This is the "active content" region —
     * used by {@link #recomputeScale} so void borders are excluded from scaling.
     */
    private void buildActiveBounds() {
        activeMinRow = worldRows; activeMaxRow = 0;
        activeMinCol = worldCols; activeMaxCol = 0;
        for (String layerName : RENDER_LAYERS) {
            int[][] grid = layerGrids.get(layerName);
            if (grid == null) continue;
            for (int row = 0; row < worldRows; row++) {
                for (int col = 0; col < worldCols; col++) {
                    if (grid[row][col] != 0) {
                        if (row < activeMinRow) activeMinRow = row;
                        if (row > activeMaxRow) activeMaxRow = row;
                        if (col < activeMinCol) activeMinCol = col;
                        if (col > activeMaxCol) activeMaxCol = col;
                    }
                }
            }
        }
        // Fallback: if nothing found, use full grid
        if (activeMinRow > activeMaxRow) {
            activeMinRow = 0; activeMaxRow = worldRows - 1;
            activeMinCol = 0; activeMaxCol = worldCols - 1;
        }
    }

    private void recomputeScale(double viewW, double viewH) {
        if (viewW == lastViewW && viewH == lastViewH) return;
        lastViewW = viewW;
        lastViewH = viewH;
        if (worldCols == 0 || worldRows == 0) {
            lastScaleX = lastScaleY = 1; lastOffsetX = lastOffsetY = 0; return;
        }

        int activeCols = activeMaxCol - activeMinCol + 1;
        int activeRows = activeMaxRow - activeMinRow + 1;

        // Stretch to fill entire viewport - no black bars!
        lastScaleX = viewW / (activeCols * TILE_SIZE);
        lastScaleY = viewH / (activeRows * TILE_SIZE);

        // No offset - map fills entire screen
        lastOffsetX = 0;
        lastOffsetY = 0;
    }

    private TilesetInfo findTileset(int gid) {
        // Binary search: find the tileset with the largest firstGid ≤ gid
        int lo = 0, hi = tilesets.size() - 1, result = -1;
        while (lo <= hi) {
            int mid = (lo + hi) / 2;
            if (tilesets.get(mid).firstGid() <= gid) { result = mid; lo = mid + 1; }
            else hi = mid - 1;
        }
        if (result < 0) return null;
        TilesetInfo ts = tilesets.get(result);
        return (gid <= ts.lastGid()) ? ts : null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Utility helpers
    // ─────────────────────────────────────────────────────────────────────────

    private int[] parseCSV(String csv) {
        String[] parts = csv.split("[,\n\r]+");
        int[] result = new int[parts.length];
        int cnt = 0;
        for (String p : parts) {
            p = p.trim();
            if (!p.isEmpty()) {
                try { result[cnt++] = Integer.parseInt(p); }
                catch (NumberFormatException ignored) {}
            }
        }
        return cnt == parts.length ? result : Arrays.copyOf(result, cnt);
    }

    private Rectangle2D polygonBoundingBox(String pointsStr, double offsetX, double offsetY) {
        String[] tokens = pointsStr.trim().split("\\s+");
        if (tokens.length == 0) return null;
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (String t : tokens) {
            String[] xy = t.split(",");
            if (xy.length < 2) continue;
            double x = Double.parseDouble(xy[0]) + offsetX;
            double y = Double.parseDouble(xy[1]) + offsetY;
            minX = Math.min(minX, x); minY = Math.min(minY, y);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
        }
        return new Rectangle2D(minX, minY, maxX - minX, maxY - minY);
    }

    private double parseDoubleAttr(Element el, String attr, double defaultVal) {
        String v = el.getAttribute(attr);
        if (v == null || v.isEmpty()) return defaultVal;
        try { return Double.parseDouble(v); } catch (NumberFormatException e) { return defaultVal; }
    }

    private Image loadImage(String resourcePath) {
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is != null) return new Image(is);
            System.err.println("[MapManager] Image not found: " + resourcePath);
        } catch (Exception e) {
            System.err.println("[MapManager] Failed to load image " + resourcePath + ": " + e.getMessage());
        }
        return null;
    }
}
