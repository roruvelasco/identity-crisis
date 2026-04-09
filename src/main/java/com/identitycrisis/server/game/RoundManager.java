package com.identitycrisis.server.game;

import com.identitycrisis.shared.model.GameConfig;
import com.identitycrisis.shared.model.Player;
import com.identitycrisis.shared.model.RoundPhase;
import com.identitycrisis.shared.util.Vector2D;
import java.util.List;

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
                    transitionTo(RoundPhase.ACTIVE);
                    gameState.setRoundTimer(GameConfig.ROUND_DURATION_SECONDS);
                    chaosEventManager.resetForNewRound();
                }
            }

            case ACTIVE -> {
                gameState.setRoundTimer(gameState.getRoundTimer() - dt);
                if (gameState.getRoundTimer() <= 0) {
                    chaosEventManager.clearActiveEvent();
                    transitionTo(RoundPhase.ROUND_END);
                }
            }

            // ROUND_END is a one-tick transient state: evaluate eliminations then
            // immediately advance to ELIMINATION. This block executes exactly once
            // per round-end transition because transitionTo() changes the phase on
            // the same tick. Do NOT add timers or blocking logic here without also
            // adding an idempotency guard — re-entry would call evaluateEliminations()
            // again and eliminate extra players.
            case ROUND_END -> {
                List<Integer> eliminated = eliminationManager.evaluateEliminations();
                gameState.getPendingEliminationIds().addAll(eliminated);
                transitionTo(RoundPhase.ELIMINATION);
                gameState.setRoundTimer(GameConfig.ELIMINATION_DISPLAY_SECONDS);
            }

            case ELIMINATION -> {
                gameState.setRoundTimer(gameState.getRoundTimer() - dt);
                if (gameState.getRoundTimer() <= 0) {
                    if (eliminationManager.isGameOver()) {
                        gameState.setPendingGameOverWinnerId(eliminationManager.getWinnerId());
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
        safeZoneManager.spawnSafeZone();
        List<Player> alive = gameState.getAlivePlayers();
        double cx = gameState.getArena().getWidth()  / 2.0;
        double cy = gameState.getArena().getHeight() / 2.0;
        for (int i = 0; i < alive.size(); i++) {
            double angle = 2 * Math.PI * i / alive.size();
            double x = cx + GameConfig.SPAWN_RADIUS * Math.cos(angle);
            double y = cy + GameConfig.SPAWN_RADIUS * Math.sin(angle);
            alive.get(i).setPosition(new Vector2D(x, y));
            alive.get(i).setVelocity(Vector2D.zero());
            alive.get(i).setInSafeZone(false);
        }
    }

    public boolean isWarmupRound() {
        return gameState.getRoundNumber() <= GameConfig.WARMUP_ROUNDS;
    }
}
