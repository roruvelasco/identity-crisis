package com.identitycrisis.client.render;

import javafx.scene.image.Image;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads, caches, provides sprite images from resources/sprites/.
 * Keys: "player_0_walk_down_0", "safezone", "obstacle_rock", etc.
 */
public class SpriteManager {

    private Map<String, Image> spriteCache = new HashMap<>();

    public SpriteManager() { }

    public void loadAll() { }

    public Image get(String key) { throw new UnsupportedOperationException("stub"); }

    private Image loadImage(String resourcePath) { throw new UnsupportedOperationException("stub"); }
}
