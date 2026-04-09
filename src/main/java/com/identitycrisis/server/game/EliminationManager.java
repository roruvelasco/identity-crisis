package com.identitycrisis.server.game;

import java.util.List;

/**
 * Evaluates eliminations at round end.
 * Warmup (1–2): all outside safe zone eliminated (usually nobody).
 * Elimination (3+): zone fits n-1. One eliminated per round guaranteed.
 *   Tiebreak: farthest from zone center among those outside.
 */
public class EliminationManager {

    private GameState gameState;

    public EliminationManager(GameState gameState) { this.gameState = gameState; }

    public List<Integer> evaluateEliminations() { throw new UnsupportedOperationException("stub"); }

    private void eliminatePlayer(int playerId) { }

    public boolean isGameOver() { throw new UnsupportedOperationException("stub"); }

    public int getWinnerId() { throw new UnsupportedOperationException("stub"); }
}
