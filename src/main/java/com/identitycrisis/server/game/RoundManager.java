package com.identitycrisis.server.game;

import com.identitycrisis.shared.model.GameConfig;
import com.identitycrisis.shared.model.Player;
import com.identitycrisis.shared.model.RoundPhase;
import com.identitycrisis.shared.util.Vector2D;
import java.util.List;

/** Drives the round state machine. */
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
                if (isWarmupRound() && gameState.getAliveCount() > 0 &&
                    gameState.getAlivePlayers().stream().allMatch(Player::isInSafeZone)) {
                    gameState.setRoundTimer(0);
                }
                if (gameState.getRoundTimer() <= 0) {
                    chaosEventManager.clearActiveEvent();
                    transitionTo(RoundPhase.ROUND_END);
                }
            }

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

    /** Starts a new round, spawning zones and resetting player positions. */
    private void startNewRound() {
        int aliveCount = gameState.getAliveCount();
        boolean isWarmup = gameState.getRoundNumber() <= GameConfig.WARMUP_ROUNDS;
        int zoneCount = isWarmup
            ? aliveCount
            : Math.max(GameConfig.SAFE_ZONE_MIN_ZONES, aliveCount - 1);
        safeZoneManager.spawnRoundZones(zoneCount);

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
