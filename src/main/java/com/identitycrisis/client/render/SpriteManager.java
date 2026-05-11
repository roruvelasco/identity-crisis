package com.identitycrisis.client.render;

import javafx.scene.image.Image;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads sprites from resources/sprites/.
 *
 * Sprites (players 1–8):
 * "player_N_idle" → /sprites/players/N/Idle.png (128×32, 4 frames of 32×32)
 * "player_N_walk" → /sprites/players/N/Walk.png (192×32, 6 frames of 32×32)
 * "player_N_death" → /sprites/players/N/Death.png
 */
public class SpriteManager {

    private final Map<String, Image> spriteCache = new HashMap<>();

    public SpriteManager() {
    }

    /** Loads all known sprites into cache. Call once during scene init. */
    public void loadAll() {
<<<<<<< HEAD
        // Load all 8 player sprite sets for multiplayer support
=======
>>>>>>> d287cc4497895f069ecdbc5de3e7a403eafd722f
        for (int i = 1; i <= 8; i++) {
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
