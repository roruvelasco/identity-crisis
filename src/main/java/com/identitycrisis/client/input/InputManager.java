package com.identitycrisis.client.input;

import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import java.util.Set;
import java.util.HashSet;

/** Captures keyboard input and produces an InputSnapshot each frame. */
public class InputManager {

    private final Set<KeyCode> pressedKeys = new HashSet<>();

    private EventHandler<KeyEvent> keyPressedHandler;
    private EventHandler<KeyEvent> keyReleasedHandler;
    
    private boolean testingReversed   = false;
    private boolean uWasPressed        = false;

    private boolean testingFakeZones   = false;
    private boolean oWasPressed        = false;

    public InputManager() { }

    /** Registers key handlers on the scene. */
    public void attachToScene(Scene scene) {
        keyPressedHandler  = e -> {
            pressedKeys.add(e.getCode());
            if (e.getCode() == KeyCode.U && !uWasPressed) {
                testingReversed = !testingReversed;
                uWasPressed = true;
                System.out.println("Reversed bindings toggle: " + testingReversed);
            }
            if (e.getCode() == KeyCode.O && !oWasPressed) {
                testingFakeZones = !testingFakeZones;
                oWasPressed = true;
                System.out.println("Fake safe-zones toggle: " + testingFakeZones);
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

    /** Removes key handlers and clears pressed-key state. */
    public void detachFromScene(Scene scene) {
        if (keyPressedHandler != null) {
            scene.removeEventHandler(KeyEvent.KEY_PRESSED,  keyPressedHandler);
            scene.removeEventHandler(KeyEvent.KEY_RELEASED, keyReleasedHandler);
            keyPressedHandler  = null;
            keyReleasedHandler = null;
        }
        pressedKeys.clear();
    }

    /** Returns an immutable snapshot of current input. */
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

    public boolean isTestingReversed()   { return testingReversed;  }
    public boolean isTestingFakeZones()   { return testingFakeZones; }
}
