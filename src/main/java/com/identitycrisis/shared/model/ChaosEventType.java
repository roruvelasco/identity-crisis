package com.identitycrisis.shared.model;

public enum ChaosEventType {
    NONE,
    REVERSED_CONTROLS,  // F06: WASD inverted client-side
    CONTROL_SWAP,       // F07: you control someone else's character
    FAKE_SAFE_ZONES     // F08: multiple zones shown, only one is real
}
