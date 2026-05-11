package com.identitycrisis.client.input;

import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import java.util.Set;
import java.util.HashSet;

/**
 * Captures keyboard input. Produces InputSnapshot each frame.
 *
 * Uses addEventHandler (not setOnKeyPressed) so it coexists with
 * SceneManager's F11 fullscreen handler.
 *
 * Bindings: W/UP=up, A/LEFT=left, S/DOWN=down, D/RIGHT=right,
 *           E=carry, Q=throw, ENTER=chatToggle
 */
public class InputManager {

    private static final boolean CHAOS_DEBUG_KEYS_ENABLED = Boolean.getBoolean("identitycrisis.chaosDebugKeys");
    private final Set<KeyCode> pressedKeys = new HashSet<>();

    private EventHandler<KeyEvent> keyPressedHandler;
    private EventHandler<KeyEvent> keyReleasedHandler;
    
    private boolean testingReversed   = false;
    private boolean uWasPressed        = false;

    private boolean testingFakeZones   = false;
    private boolean oWasPressed        = false;

    public InputManager() { }

    /** Registers key handlers on the scene. Idempotent — detaches first if already attached. */
    public void attachToScene(Scene scene) {
        keyPressedHandler  = e -> {
            pressedKeys.add(e.getCode());
            if (CHAOS_DEBUG_KEYS_ENABLED && e.getCode() == KeyCode.U && !uWasPressed) {
                testingReversed = !testingReversed;
                uWasPressed = true;
                System.out.println("[DEBUG] Reversed bindings debug toggle toggled via U: " + testingReversed);
            }
            if (CHAOS_DEBUG_KEYS_ENABLED && e.getCode() == KeyCode.O && !oWasPressed) {
                testingFakeZones = !testingFakeZones;
                oWasPressed = true;
                System.out.println("[DEBUG] Fake safe-zones chaos toggle via O: " + testingFakeZones);
            }
        };
        keyReleasedHandler = e -> {
            pressedKeys.remove(e.getCode());
            if (e.getCode() == KeyCode.U) uWasPressed = false;
            if (e.getCode() == KeyCode.O) oWasPressed = false;
        };
        scene.addEventHandler(KeyEvent.KEY_PRESSED,  keyPressedHandler);
        scene.addEventHandler(KeyEvent.KEY_RELEASED, keyReleasedHandler);
    }

    /** Removes key handlers from the scene and clears all pressed-key state. */
    public void detachFromScene(Scene scene) {
        if (keyPressedHandler != null) {
            scene.removeEventHandler(KeyEvent.KEY_PRESSED,  keyPressedHandler);
            scene.removeEventHandler(KeyEvent.KEY_RELEASED, keyReleasedHandler);
            keyPressedHandler  = null;
            keyReleasedHandler = null;
        }
        pressedKeys.clear();
    }

    /** Returns an immutable snapshot of the current frame's input. */
    public InputSnapshot snapshot() {
        return new InputSnapshot(
            pressedKeys.contains(KeyCode.W) || pressedKeys.contains(KeyCode.UP),
            pressedKeys.contains(KeyCode.S) || pressedKeys.contains(KeyCode.DOWN),
            pressedKeys.contains(KeyCode.A) || pressedKeys.contains(KeyCode.LEFT),
            pressedKeys.contains(KeyCode.D) || pressedKeys.contains(KeyCode.RIGHT),
            pressedKeys.contains(KeyCode.E),
            pressedKeys.contains(KeyCode.Q),
            pressedKeys.contains(KeyCode.ENTER)
        );
    }

    public boolean isPressed(KeyCode code) {
        return pressedKeys.contains(code);
    }

    public boolean isTestingReversed()   { return CHAOS_DEBUG_KEYS_ENABLED && testingReversed;  }
    public boolean isTestingFakeZones()   { return CHAOS_DEBUG_KEYS_ENABLED && testingFakeZones; }
}
