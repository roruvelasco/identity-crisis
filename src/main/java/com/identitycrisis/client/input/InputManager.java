package com.identitycrisis.client.input;

import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import java.util.Set;
import java.util.HashSet;

/**
 * Captures keyboard input. Produces InputSnapshot each frame.
 * Bindings: W/UP=up, A/LEFT=left, S/DOWN=down, D/RIGHT=right,
 *           E=carry, Q=throw, ENTER=chatToggle
 */
public class InputManager {

    private final Set<KeyCode> pressedKeys = new HashSet<>();

    public InputManager() { }

    public void attachToScene(Scene scene) { }

    public void detachFromScene(Scene scene) { }

    public InputSnapshot snapshot() { throw new UnsupportedOperationException("stub"); }

    public boolean isPressed(KeyCode code) { throw new UnsupportedOperationException("stub"); }
}
