package com.identitycrisis.server.physics;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.*;

/**
 * Lightweight server-side TMX parser.
 *
 * <p>Extracts exactly what {@link CollisionDetector} needs for tile-accurate
 * wall collision:
 * <ol>
 *   <li>A broad-phase {@code boolean[row][col]} solid grid from the
 *       {@code "walls"} layer — a cell is {@code true} when its GID is
 *       non-zero.</li>
 *   <li>The raw (flip-stripped) GID at each cell of the walls layer.</li>
 *   <li>Per-tile collision rectangles parsed from {@code <objectgroup>}
 *       elements authored in the Tiled editor.</li>
 * </ol>
 *
 * <p><strong>No image loading.</strong>  The server runs headless; all
 * sub-tile precision comes from the Tiled-authored objectgroup shapes, which
 * the map author has already annotated for every wall tile.  For tiles that
 * lack an objectgroup, {@link WallCollisionData#shapesFor} falls back to a
 * full 16×16 rectangle.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // In ServerApp.main() — Composition Root:
 * WallCollisionData wallData =
 *     TmxWallsParser.load("/sprites/map/ArenaMap.tmx");
 * CollisionDetector cd = new CollisionDetector(wallData);
 * }</pre>
 */
public final class TmxWallsParser {

    /** Native pixel size per tile — matches {@code MapManager.TILE_SIZE}. */
    public static final int TILE_SIZE = 16;

    /**
     * Tiled flip-flag mask. The three MSBs of a GID encode H/V/D flips;
     * strip them to get the real tileset index.
     */
    private static final int GID_MASK = 0x1FFFFFFF;

    private TmxWallsParser() {} // static utility

    // ─────────────────────────────────────────────────────────────────────────
    //  Public data record
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Immutable wall-collision snapshot produced by {@link #load}.
     *
     * @param solidGrid    broad-phase grid: {@code true} where a walls-layer
     *                     tile has a non-zero GID.
     * @param wallsGidGrid flip-stripped GID at each {@code [row][col]}; 0 = empty.
     * @param tileShapes   per-GID list of collision rects from objectgroup
     *                     (tile-local px, origin at top-left of tile).
     *                     Each rect is {@code double[]{x, y, w, h}}.
     *                     Absent GIDs fall back to a full-tile rect via
     *                     {@link #shapesFor}.
     * @param tileSize     native tile size in pixels (always 16 for ArenaMap).
     * @param worldCols    grid width in tiles.
     * @param worldRows    grid height in tiles.
     */
    public record WallCollisionData(
            boolean[][]                  solidGrid,
            boolean[][]                  spawnableGrid,
            int[][]                      wallsGidGrid,
            Map<Integer, List<double[]>> tileShapes,
            int                          tileSize,
            int                          worldCols,
            int                          worldRows
    ) {
        /**
         * Returns the objectgroup collision rects for {@code gid}, or a
         * single full-tile rect {@code [0, 0, tileSize, tileSize]} when
         * no objectgroup was authored for that tile.
         */
        public List<double[]> shapesFor(int gid) {
            List<double[]> shapes = tileShapes.get(gid);
            if (shapes != null && !shapes.isEmpty()) return shapes;
            return List.of(new double[]{0, 0, tileSize, tileSize});
        }

        /** {@code true} if no wall data was loaded (parse failure / missing file). */
        public boolean isEmpty() { return worldCols == 0 || worldRows == 0; }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Loader
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Parses the TMX file and returns {@link WallCollisionData}.
     * Returns an empty stub if the resource is not found or the parse fails.
     *
     * @param tmxResourcePath classpath path, e.g. {@code "/sprites/map/ArenaMap.tmx"}
     */
    public static WallCollisionData load(String tmxResourcePath) {
        try (InputStream is =
                     TmxWallsParser.class.getResourceAsStream(tmxResourcePath)) {

            if (is == null) {
                System.err.println("[TmxWallsParser] TMX not found: " + tmxResourcePath);
                return emptyData();
            }

            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().parse(is);
            doc.getDocumentElement().normalize();
            Element mapEl = doc.getDocumentElement();

            // 1. Parse per-tile objectgroup shapes (no image loading)
            Map<Integer, List<double[]>> tileShapes = parseTileShapes(mapEl);

            // 2. Determine world bounds (handles infinite chunk format)
            int fallbackW = parseInt(mapEl.getAttribute("width"),  0);
            int fallbackH = parseInt(mapEl.getAttribute("height"), 0);
            int[] bounds  = scanBounds(mapEl, fallbackW, fallbackH);
            int originX   = bounds[0];
            int originY   = bounds[1];
            int worldCols = bounds[2];
            int worldRows = bounds[3];

            if (worldCols == 0 || worldRows == 0) {
                System.err.println("[TmxWallsParser] Could not determine world bounds.");
                return emptyData();
            }

            // 3. Parse walls layer into a GID grid
            int[][] wallsGid = new int[worldRows][worldCols];
            parseLayer(mapEl, "walls", wallsGid, originX, originY, worldCols, worldRows);
            int[][] floorGroundGid = new int[worldRows][worldCols];
            int[][] floor2Gid = new int[worldRows][worldCols];
            int[][] waterGid = new int[worldRows][worldCols];
            int[][] objectsGid = new int[worldRows][worldCols];
            int[][] objects2Gid = new int[worldRows][worldCols];
            parseLayer(mapEl, "floor_ground", floorGroundGid, originX, originY, worldCols, worldRows);
            parseLayer(mapEl, "floor_2", floor2Gid, originX, originY, worldCols, worldRows);
            parseLayer(mapEl, "water", waterGid, originX, originY, worldCols, worldRows);
            parseLayer(mapEl, "objects", objectsGid, originX, originY, worldCols, worldRows);
            parseLayer(mapEl, "objects2", objects2Gid, originX, originY, worldCols, worldRows);

            // 4. Build broad-phase solid grid: non-zero GID → solid
            boolean[][] solid = new boolean[worldRows][worldCols];
            boolean[][] spawnable = new boolean[worldRows][worldCols];
            for (int r = 0; r < worldRows; r++)
                for (int c = 0; c < worldCols; c++) {
                    boolean hasFloor = floorGroundGid[r][c] != 0 || floor2Gid[r][c] != 0;
                    boolean blocked = wallsGid[r][c] != 0
                            || waterGid[r][c] != 0
                            || tileShapes.containsKey(floor2Gid[r][c])
                            || tileShapes.containsKey(objectsGid[r][c])
                            || tileShapes.containsKey(objects2Gid[r][c]);
                    solid[r][c] = !hasFloor || blocked;
                    spawnable[r][c] = hasFloor && !blocked;
                }

            System.out.printf(
                    "[TmxWallsParser] Loaded %d×%d wall grid, %d tiles with custom shapes%n",
                    worldCols, worldRows, tileShapes.size());

            return new WallCollisionData(
                    solid, spawnable, wallsGid, Collections.unmodifiableMap(tileShapes),
                    TILE_SIZE, worldCols, worldRows);

        } catch (Exception e) {
            System.err.println("[TmxWallsParser] Parse error: " + e.getMessage());
            e.printStackTrace();
            return emptyData();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Scans all {@code <tileset>} children of {@code <map>} and collects
     * the objectgroup collision rectangles for every tile that has them.
     */
    private static Map<Integer, List<double[]>> parseTileShapes(Element mapEl) {
        Map<Integer, List<double[]>> result = new HashMap<>();
        NodeList tilesets = mapEl.getElementsByTagName("tileset");

        for (int i = 0; i < tilesets.getLength(); i++) {
            Element ts = (Element) tilesets.item(i);
            // Only direct <tileset> children of <map>
            if (!ts.getParentNode().equals(mapEl)) continue;

            int firstGid = parseInt(ts.getAttribute("firstgid"), 1);
            NodeList tiles = ts.getElementsByTagName("tile");

            for (int t = 0; t < tiles.getLength(); t++) {
                Element tileEl = (Element) tiles.item(t);
                if (!tileEl.getParentNode().equals(ts)) continue;

                int localId  = parseInt(tileEl.getAttribute("id"), 0);
                int globalId = firstGid + localId;

                NodeList ogList = tileEl.getElementsByTagName("objectgroup");
                if (ogList.getLength() == 0) continue;

                Element    og      = (Element) ogList.item(0);
                NodeList   objects = og.getElementsByTagName("object");
                List<double[]> shapes = new ArrayList<>();

                for (int o = 0; o < objects.getLength(); o++) {
                    Element obj   = (Element) objects.item(o);
                    String  wAttr = obj.getAttribute("width");
                    String  hAttr = obj.getAttribute("height");
                    // Only rect objects have width + height attributes
                    if (wAttr.isEmpty() || hAttr.isEmpty()) continue;

                    double ox = parseDouble(obj.getAttribute("x"), 0);
                    double oy = parseDouble(obj.getAttribute("y"), 0);
                    double ow = parseDouble(wAttr, TILE_SIZE);
                    double oh = parseDouble(hAttr, TILE_SIZE);
                    shapes.add(new double[]{ox, oy, ow, oh});
                }

                if (!shapes.isEmpty()) result.put(globalId, shapes);
            }
        }

        return result;
    }

    /**
     * Scans chunk bounds to determine world grid dimensions for infinite maps.
     * Returns {@code {originX, originY, worldCols, worldRows}}.
     */
    private static int[] scanBounds(Element mapEl, int fallbackW, int fallbackH) {
        NodeList chunks = mapEl.getElementsByTagName("chunk");
        if (chunks.getLength() == 0) {
            return new int[]{0, 0, fallbackW, fallbackH};
        }

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;

        for (int i = 0; i < chunks.getLength(); i++) {
            Element ch  = (Element) chunks.item(i);
            int     cx  = parseInt(ch.getAttribute("x"),      0);
            int     cy  = parseInt(ch.getAttribute("y"),      0);
            int     cw  = parseInt(ch.getAttribute("width"),  0);
            int     ch2 = parseInt(ch.getAttribute("height"), 0);
            minX = Math.min(minX, cx);       minY = Math.min(minY, cy);
            maxX = Math.max(maxX, cx + cw);  maxY = Math.max(maxY, cy + ch2);
        }

        return new int[]{minX, minY, maxX - minX, maxY - minY};
    }

    /**
     * Finds the {@code "walls"} layer and fills {@code grid[row][col]} with
     * the flip-stripped GID at each cell.  Supports both infinite-chunk and
     * fixed-size (non-chunk) layer formats.
     */
    private static void parseLayer(Element mapEl, String layerName, int[][] grid,
                                   int originX, int originY,
                                   int worldCols, int worldRows) {
        NodeList layers = mapEl.getElementsByTagName("layer");

        for (int i = 0; i < layers.getLength(); i++) {
            Element layerEl = (Element) layers.item(i);
            if (!layerEl.getParentNode().equals(mapEl)) continue;
            if (!layerName.equals(layerEl.getAttribute("name"))) continue;

            NodeList chunks = layerEl.getElementsByTagName("chunk");

            if (chunks.getLength() > 0) {
                // Infinite map: stitch chunks
                for (int c = 0; c < chunks.getLength(); c++) {
                    Element ch  = (Element) chunks.item(c);
                    int chX = parseInt(ch.getAttribute("x"), 0) - originX;
                    int chY = parseInt(ch.getAttribute("y"), 0) - originY;
                    int chW = parseInt(ch.getAttribute("width"),  0);
                    int chH = parseInt(ch.getAttribute("height"), 0);

                    int[] gids = parseCSV(ch.getTextContent().trim());
                    int   idx  = 0;

                    for (int r = 0; r < chH; r++) {
                        for (int col = 0; col < chW; col++, idx++) {
                            int gr = chY + r;
                            int gc = chX + col;
                            if (gr >= 0 && gr < worldRows && gc >= 0 && gc < worldCols) {
                                grid[gr][gc] = idx < gids.length
                                        ? (gids[idx] & GID_MASK) : 0;
                            }
                        }
                    }
                }
            } else {
                // Fixed-size map: single <data> block
                NodeList data = layerEl.getElementsByTagName("data");
                if (data.getLength() > 0) {
                    int[] gids = parseCSV(data.item(0).getTextContent().trim());
                    int   idx  = 0;
                    for (int r = 0; r < worldRows && idx < gids.length; r++)
                        for (int col = 0; col < worldCols && idx < gids.length; col++)
                            grid[r][col] = gids[idx++] & GID_MASK;
                }
            }

            return; // walls layer found — stop scanning
        }

        System.err.println("[TmxWallsParser] '" + layerName + "' layer not found in TMX.");
    }

    // ── CSV / attribute helpers ───────────────────────────────────────────────

    private static int[] parseCSV(String csv) {
        String[] parts = csv.split("[,\n\r]+");
        int[]    buf   = new int[parts.length];
        int      cnt   = 0;
        for (String p : parts) {
            p = p.trim();
            if (!p.isEmpty()) {
                try { buf[cnt++] = Integer.parseInt(p); }
                catch (NumberFormatException ignored) {}
            }
        }
        return cnt == buf.length ? buf : Arrays.copyOf(buf, cnt);
    }

    private static int    parseInt(String v, int def) {
        try { return (v != null && !v.isEmpty()) ? Integer.parseInt(v) : def; }
        catch (NumberFormatException e) { return def; }
    }

    private static double parseDouble(String v, double def) {
        try { return (v != null && !v.isEmpty()) ? Double.parseDouble(v) : def; }
        catch (NumberFormatException e) { return def; }
    }

    private static WallCollisionData emptyData() {
        return new WallCollisionData(
                new boolean[0][0], new boolean[0][0], new int[0][0],
                Map.of(), TILE_SIZE, 0, 0);
    }
}
