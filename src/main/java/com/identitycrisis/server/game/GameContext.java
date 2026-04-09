package com.identitycrisis.server.game;

/**
 * Value-object that bundles all server-side game managers together.
 *
 * <p>Used as a single constructor argument to {@link ServerGameLoop} instead of
 * passing six individual managers. All fields are final and set at construction
 * time — this record is effectively immutable after the Composition Root wires
 * everything up in {@code ServerApp.main()}.
 *
 * <p><b>DI note:</b> This is not a DI container. It is a plain, typed grouping
 * of already-constructed collaborators that share the same {@link GameState}.
 * {@link ServerGameLoop} reads from it but never creates or replaces anything.
 */
public record GameContext(
        GameState          gameState,
        SafeZoneManager    safeZoneManager,
        ChaosEventManager  chaosEventManager,
        CarryManager       carryManager,
        EliminationManager eliminationManager,
        RoundManager       roundManager
) { }
