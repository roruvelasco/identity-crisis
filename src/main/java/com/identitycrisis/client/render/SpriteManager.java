package com.identitycrisis.client.render;

import javafx.scene.image.Image;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads, caches, and provides sprite sheet images from resources/sprites/.
 *
 * Key convention:
 *   "player_1_idle"  → /sprites/players/1/Idle.png  (128×32, 4 frames of 32×32)
 *   "player_1_walk"  → /sprites/players/1/Walk.png  (192×32, 6 frames of 32×32)
 */
public class SpriteManager {

    private final Map<String, Image> spriteCache = new HashMap<>();

    public SpriteManager() { }

    /** Loads all known sprites into cache. Call once during scene init. */
    public void loadAll() {
        for (int i = 1; i <= 4; i++) {
            loadSprite("player_" + i + "_idle", "/sprites/players/" + i + "/Idle.png");
            loadSprite("player_" + i + "_walk", "/sprites/players/" + i + "/Walk.png");
            loadSprite("player_" + i + "_death", "/sprites/players/" + i + "/Death.png");
        }
    }

    /** Returns the cached Image for key, or null if not loaded. */
    public Image get(String key) {
        return spriteCache.get(key);
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private void loadSprite(String key, String path) {
        Image img = loadImage(path);
        if (img != null) {
            spriteCache.put(key, img);
        }
    }

    private Image loadImage(String resourcePath) {
        try (var stream = getClass().getResourceAsStream(resourcePath)) {
            if (stream != null) {
                return new Image(stream);
            }
        } catch (Exception e) {
            System.err.println("[SpriteManager] Failed to load: " + resourcePath + " — " + e.getMessage());
        }
        return null;
    }
}
