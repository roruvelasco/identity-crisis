package com.identitycrisis.server.game;


public record GameContext(
        GameState          gameState,
        SafeZoneManager    safeZoneManager,
        ChaosEventManager  chaosEventManager,
        CarryManager       carryManager,
        EliminationManager eliminationManager,
        RoundManager       roundManager
) { }
