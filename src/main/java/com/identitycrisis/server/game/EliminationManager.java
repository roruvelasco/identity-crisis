package com.identitycrisis.server.game;

import com.identitycrisis.shared.model.GameConfig;
import com.identitycrisis.shared.model.Player;
import com.identitycrisis.shared.model.PlayerState;
import com.identitycrisis.shared.model.SafeZone;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Evaluates eliminations at round end.
 * Warmup (1–2): all outside safe zone eliminated (usually nobody).
 * Elimination (3+): zone fits n-1. One eliminated per round guaranteed.
 *   Tiebreak: farthest from zone center among those outside.
 */
public class EliminationManager {

    private final GameState gameState;

    public EliminationManager(GameState gameState) { this.gameState = gameState; }

    public List<Integer> evaluateEliminations() {
        List<Integer> eliminated = new ArrayList<>();
        SafeZone zone = gameState.getTrueSafeZone();
        if (zone == null) return eliminated;

        List<Player> alive   = gameState.getAlivePlayers();
        List<Player> outside = alive.stream().filter(p -> !p.isInSafeZone()).toList();

        if (gameState.getRoundNumber() <= GameConfig.WARMUP_ROUNDS) {
            for (Player p : outside) {
                eliminatePlayer(p.getPlayerId());
                eliminated.add(p.getPlayerId());
            }
        } else {
            if (outside.isEmpty()) {
                Player farthest = alive.stream()
                    .max(Comparator.comparingDouble(p -> p.getPosition().distanceTo(zone.position())))
                    .orElse(null);
                if (farthest != null) {
                    eliminatePlayer(farthest.getPlayerId());
                    eliminated.add(farthest.getPlayerId());
                }
            } else {
                Player farthest = outside.stream()
                    .max(Comparator.comparingDouble(p -> p.getPosition().distanceTo(zone.position())))
                    .orElse(null);
                if (farthest != null) {
                    eliminatePlayer(farthest.getPlayerId());
                    eliminated.add(farthest.getPlayerId());
                }
            }
        }
        return eliminated;
    }

    private void eliminatePlayer(int playerId) {
        Player p = gameState.getPlayerById(playerId);
        if (p != null) {
            p.setState(PlayerState.ELIMINATED);
            p.setInSafeZone(false);
        }
    }

    public boolean isGameOver() { return gameState.getAliveCount() <= 1; }

    public int getWinnerId() {
        return gameState.getAlivePlayers().stream()
            .findFirst()
            .map(Player::getPlayerId)
            .orElse(-1);
    }
}
