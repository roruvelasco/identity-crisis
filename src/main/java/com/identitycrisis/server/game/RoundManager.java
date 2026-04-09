package com.identitycrisis.server.game;

import com.identitycrisis.shared.model.GameConfig;
import com.identitycrisis.shared.model.Player;
import com.identitycrisis.shared.model.RoundPhase;

/**
 * Drives the round state machine.
 * LOBBY → COUNTDOWN → ACTIVE → ROUND_END → ELIMINATION → COUNTDOWN → ...
 * When 1 player left → GAME_OVER
 */
public class RoundManager {

    private final GameState gameState;
    private final SafeZoneManager safeZoneManager;
    private final ChaosEventManager chaosEventManager;
    private final EliminationManager eliminationManager;

    public RoundManager(GameState gs, SafeZoneManager szm,
                        ChaosEventManager cem, EliminationManager em) {
        this.gameState          = gs;
        this.safeZoneManager    = szm;
        this.chaosEventManager  = cem;
        this.eliminationManager = em;
    }

    public void tick(double dt) {
        switch (gameState.getPhase()) {
            case LOBBY -> { /* LobbyManager handles this externally */ }

            case COUNTDOWN -> {
                gameState.setRoundTimer(gameState.getRoundTimer() - dt);
                if (gameState.getRoundTimer() <= 0) {
                    safeZoneManager.spawnSafeZone();
                    transitionTo(RoundPhase.ACTIVE);
                    gameState.setRoundTimer(GameConfig.ROUND_DURATION_SECONDS);
                    chaosEventManager.resetForNewRound();
                }
            }

            case ACTIVE -> {
                gameState.setRoundTimer(gameState.getRoundTimer() - dt);
                if (gameState.getRoundTimer() <= 0) {
                    transitionTo(RoundPhase.ROUND_END);
                }
            }

            case ROUND_END -> {
                eliminationManager.evaluateEliminations();
                transitionTo(RoundPhase.ELIMINATION);
                gameState.setRoundTimer(GameConfig.ELIMINATION_DISPLAY_SECONDS);
            }

            case ELIMINATION -> {
                gameState.setRoundTimer(gameState.getRoundTimer() - dt);
                if (gameState.getRoundTimer() <= 0) {
                    if (eliminationManager.isGameOver()) {
                        transitionTo(RoundPhase.GAME_OVER);
                    } else {
                        gameState.setRoundNumber(gameState.getRoundNumber() + 1);
                        startNewRound();
                        transitionTo(RoundPhase.COUNTDOWN);
                        gameState.setRoundTimer(GameConfig.COUNTDOWN_SECONDS);
                    }
                }
            }

            case GAME_OVER -> { /* Game is done */ }
        }
    }

    private void transitionTo(RoundPhase phase) { gameState.setPhase(phase); }

    private void startNewRound() {
        for (Player p : gameState.getAlivePlayers()) {
            p.setInSafeZone(false);
        }
    }

    public boolean isWarmupRound() {
        return gameState.getRoundNumber() <= GameConfig.WARMUP_ROUNDS;
    }

    private boolean shouldEndGame() {
        return eliminationManager.isGameOver();
    }
}
