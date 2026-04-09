package com.identitycrisis.shared.model;

public enum RoundPhase {
    LOBBY,          // waiting for players
    COUNTDOWN,      // 3-2-1 before round starts
    ACTIVE,         // round in progress
    ROUND_END,      // freeze, evaluate safe zone occupancy
    ELIMINATION,    // display who was eliminated
    GAME_OVER       // winner declared
}
