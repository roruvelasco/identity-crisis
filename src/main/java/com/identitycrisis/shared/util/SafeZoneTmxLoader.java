package com.identitycrisis.shared.util;

import com.identitycrisis.shared.model.SafeZone;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


public final class SafeZoneTmxLoader {

    /** Native tile size in world pixels (must match {@code MapManager.TILE_SIZE}). */
    private static final int TILE_SIZE = 16;

    /**
     * Tiled stores horizontal/vertical/diagonal flip flags in the top three GID
     * bits.  We mask these off so flipped tiles are still recognised as
     * non-empty when scanning the safe-zone layers.
     */
    private static final int GID_MASK = 0x1FFFFFFF;

    private SafeZoneTmxLoader() {}

    /**
     * Parses the TMX at the given classpath resource path and returns the
     * eight safe-zone rectangles, sorted ascending by zone id.
     *
     * @throws IllegalStateException if the TMX cannot be read or contains
     *                               fewer than one safezone layer
     */
    public static List<SafeZone> load(String tmxResourcePath) {
        try (InputStream is = SafeZoneTmxLoader.class.getResourceAsStream(tmxResourcePath)) {
            if (is == null) {
                throw new IllegalStateException(
                        "TMX resource not found on classpath: " + tmxResourcePath);
            }
            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().parse(is);
            doc.getDocumentElement().normalize();
            Element mapEl = doc.getDocumentElement();

            int[] origin = scanChunkBounds(mapEl); // [originTileX, originTileY]
            int originX  = origin[0];
            int originY  = origin[1];

            List<SafeZone> zones = new ArrayList<>();
            for (int id = 1; id <= 8; id++) {
                SafeZone zone = extractZoneRect(mapEl, "safezone" + id, id, originX, originY);
                if (zone != null) zones.add(zone);
            }
            if (zones.isEmpty()) {
                throw new IllegalStateException(
                        "TMX " + tmxResourcePath + " contains no safezoneN layers");
            }
            zones.sort(Comparator.comparingInt(SafeZone::id));
            return List.copyOf(zones);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to parse safe zones from " + tmxResourcePath, e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Origin scan
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns {@code [originTileX, originTileY]} — the smallest chunk
     * {@code x} and {@code y} found anywhere in the document.  All other
     * chunk coordinates are made non-negative by subtracting this origin
     * before being placed into the unified world grid.
     */
    private static int[] scanChunkBounds(Element mapEl) {
        NodeList chunks = mapEl.getElementsByTagName("chunk");
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        for (int i = 0; i < chunks.getLength(); i++) {
            Element ch = (Element) chunks.item(i);
            minX = Math.min(minX, Integer.parseInt(ch.getAttribute("x")));
            minY = Math.min(minY, Integer.parseInt(ch.getAttribute("y")));
        }
        if (minX == Integer.MAX_VALUE) {
            // Non-infinite map — fall back to (0, 0).
            minX = 0;
            minY = 0;
        }
        return new int[] { minX, minY };
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Per-layer rect extraction
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Finds the bounding rectangle of every non-zero tile in the named layer
     * and returns it as a {@link SafeZone}, or {@code null} if the layer is
     * absent or completely empty.
     */
    private static SafeZone extractZoneRect(Element mapEl, String layerName,
                                            int zoneId, int originX, int originY) {
        Element layerEl = findLayerByName(mapEl, layerName);
        if (layerEl == null) return null;

        int minR = Integer.MAX_VALUE, maxR = Integer.MIN_VALUE;
        int minC = Integer.MAX_VALUE, maxC = Integer.MIN_VALUE;
        boolean found = false;

        NodeList chunks = layerEl.getElementsByTagName("chunk");
        if (chunks.getLength() > 0) {
            for (int c = 0; c < chunks.getLength(); c++) {
                Element ch = (Element) chunks.item(c);
                int chX = Integer.parseInt(ch.getAttribute("x")) - originX;
                int chY = Integer.parseInt(ch.getAttribute("y")) - originY;
                int chW = Integer.parseInt(ch.getAttribute("width"));
                int chH = Integer.parseInt(ch.getAttribute("height"));

                int[] gids = parseCsv(ch.getTextContent());
                int idx = 0;
                for (int r = 0; r < chH; r++) {
                    for (int col = 0; col < chW; col++) {
                        int gid = idx < gids.length ? (gids[idx] & GID_MASK) : 0;
                        idx++;
                        if (gid == 0) continue;
                        int gr = chY + r;
                        int gc = chX + col;
                        minR = Math.min(minR, gr); maxR = Math.max(maxR, gr);
                        minC = Math.min(minC, gc); maxC = Math.max(maxC, gc);
                        found = true;
                    }
                }
            }
        } else {
            // Non-infinite layer fallback: single <data> block.
            NodeList dataNodes = layerEl.getElementsByTagName("data");
            if (dataNodes.getLength() == 0) return null;
            int width  = Integer.parseInt(layerEl.getAttribute("width"));
            int[] gids = parseCsv(dataNodes.item(0).getTextContent());
            for (int i = 0; i < gids.length; i++) {
                int gid = gids[i] & GID_MASK;
                if (gid == 0) continue;
                int gr = i / width;
                int gc = i % width;
                minR = Math.min(minR, gr); maxR = Math.max(maxR, gr);
                minC = Math.min(minC, gc); maxC = Math.max(maxC, gc);
                found = true;
            }
        }

        if (!found) return null;
        double x = minC * (double) TILE_SIZE;
        double y = minR * (double) TILE_SIZE;
        double w = (maxC - minC + 1) * (double) TILE_SIZE;
        double h = (maxR - minR + 1) * (double) TILE_SIZE;
        return new SafeZone(zoneId, x, y, w, h);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static Element findLayerByName(Element mapEl, String layerName) {
        NodeList layers = mapEl.getElementsByTagName("layer");
        for (int i = 0; i < layers.getLength(); i++) {
            Element layerEl = (Element) layers.item(i);
            // Only direct <layer> children of <map>
            if (!layerEl.getParentNode().equals(mapEl)) continue;
            if (layerName.equals(layerEl.getAttribute("name"))) return layerEl;
        }
        return null;
    }

    private static int[] parseCsv(String csv) {
        String[] parts = csv.trim().split("[,\\s]+");
        int[] out = new int[parts.length];
        int n = 0;
        for (String p : parts) {
            if (p.isEmpty()) continue;
            try {
                out[n++] = Integer.parseInt(p);
            } catch (NumberFormatException ignored) {
                out[n++] = 0;
            }
        }
        if (n != out.length) {
            int[] trimmed = new int[n];
            System.arraycopy(out, 0, trimmed, 0, n);
            return trimmed;
        }
        return out;
    }
}
