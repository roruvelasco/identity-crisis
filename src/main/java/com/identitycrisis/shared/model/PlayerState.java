package com.identitycrisis.shared.model;

public enum PlayerState {
    ALIVE,
    ELIMINATED,
    SPECTATING,
    CARRYING,   // this player is carrying another
    CARRIED     // this player is being carried by another
}
