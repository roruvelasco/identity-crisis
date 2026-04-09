package com.identitycrisis.server.game;

/**
 * Drives the round state machine.
 * LOBBY → COUNTDOWN → ACTIVE → ROUND_END → ELIMINATION → COUNTDOWN → ...
 * When 1 player left → GAME_OVER
 */
public class RoundManager {

    private GameState gameState;
    private SafeZoneManager safeZoneManager;
    private ChaosEventManager chaosEventManager;
    private EliminationManager eliminationManager;

    public RoundManager(GameState gs, SafeZoneManager szm,
                        ChaosEventManager cem, EliminationManager em) {
        this.gameState          = gs;
        this.safeZoneManager    = szm;
        this.chaosEventManager  = cem;
        this.eliminationManager = em;
    }

    public void tick(double dt) { }

    private void transitionTo(/* RoundPhase */) { }

    private void startNewRound() { }

    public boolean isWarmupRound() { throw new UnsupportedOperationException("stub"); }

    private boolean shouldEndGame() { throw new UnsupportedOperationException("stub"); }
}
