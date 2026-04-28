package com.identitycrisis.server.game;

import com.identitycrisis.shared.model.GameConfig;
import com.identitycrisis.shared.model.Player;
import com.identitycrisis.shared.model.PlayerState;
import com.identitycrisis.shared.model.SafeZone;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Evaluates eliminations at round end based on zone occupancy and distance tiebreaks. */
public class EliminationManager {

    private final GameState gameState;
    private final CarryManager carryManager;

    public EliminationManager(GameState gameState, CarryManager carryManager) {
        this.gameState    = gameState;
        this.carryManager = carryManager;
    }

    public List<Integer> evaluateEliminations() {
        List<Integer> eliminated = new ArrayList<>();
        List<SafeZone> zones = gameState.getActiveRoundZones();
        if (zones == null || zones.isEmpty()) return eliminated;

        List<Player> alive = gameState.getAlivePlayers();

        if (alive.size() <= 1) {
            return eliminated;
        }

        if (gameState.getRoundNumber() <= GameConfig.WARMUP_ROUNDS) {
            for (Player p : alive) {
                if (p.getState() == PlayerState.CARRYING
                 || p.getState() == PlayerState.CARRIED) continue;
                if (!p.isInSafeZone()) {
                    eliminatePlayer(p.getPlayerId());
                    eliminated.add(p.getPlayerId());
                }
            }
            return eliminated;
        }

        Map<Integer, Integer> claimed = computeZoneOccupants(zones, alive);
        Set<Integer> safeIds = new HashSet<>(claimed.values());

        List<Player> unsafe = alive.stream()
            .filter(p -> !safeIds.contains(p.getPlayerId()))
            .toList();

        if (unsafe.isEmpty()) {
            Player farthest = alive.stream()
                .max(Comparator.comparingDouble(p -> nearestZoneDistance(p, zones)))
                .orElse(null);
            if (farthest != null) {
                eliminatePlayer(farthest.getPlayerId());
                eliminated.add(farthest.getPlayerId());
            }
        } else {
            Player farthest = unsafe.stream()
                .max(Comparator.comparingDouble(p -> nearestZoneDistance(p, zones)))
                .orElse(null);
            if (farthest != null) {
                eliminatePlayer(farthest.getPlayerId());
                eliminated.add(farthest.getPlayerId());
            }
        }
        return eliminated;
    }

    /** First-claimer-wins occupancy snapshot for unit-testable decoupling. */
    private Map<Integer, Integer> computeZoneOccupants(List<SafeZone> zones, List<Player> alive) {
        java.util.LinkedHashMap<Integer, Integer> claimed = new java.util.LinkedHashMap<>();
        Set<Integer> alreadySafe = new HashSet<>();
        for (SafeZone z : zones) {
            for (Player p : alive) {
                if (alreadySafe.contains(p.getPlayerId())) continue;
                if (p.getState() == PlayerState.CARRYING
                 || p.getState() == PlayerState.CARRIED) continue;
                if (z.contains(p.getPosition().x(), p.getPosition().y())) {
                    claimed.put(z.id(), p.getPlayerId());
                    alreadySafe.add(p.getPlayerId());
                    break;
                }
            }
        }
        return claimed;
    }

    private double nearestZoneDistance(Player p, List<SafeZone> zones) {
        double best = Double.MAX_VALUE;
        for (SafeZone z : zones) {
            double d = p.getPosition().distanceTo(z.center());
            if (d < best) best = d;
        }
        return best;
    }

    private void eliminatePlayer(int playerId) {
        Player p = gameState.getPlayerById(playerId);
        if (p != null) {
            carryManager.releaseCarry(playerId);
            p.setState(PlayerState.ELIMINATED);
            p.setInSafeZone(false);
            Map<Integer, Integer> cm = gameState.getControlMap();
            cm.remove(playerId);
            cm.replaceAll((clientId, controlled) ->
                controlled.equals(playerId) ? clientId : controlled);
        }
    }

    public boolean isGameOver() {
        if (gameState.getPlayers().size() == 1) {
            return gameState.getAliveCount() == 0;
        }
        return gameState.getAliveCount() <= 1; 
    }

    public int getWinnerId() {
        return gameState.getAlivePlayers().stream()
            .findFirst()
            .map(Player::getPlayerId)
            .orElse(-1);
    }
}
