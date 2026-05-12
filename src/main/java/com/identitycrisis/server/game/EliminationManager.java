package com.identitycrisis.server.game;

import com.identitycrisis.shared.model.GameConfig;
import com.identitycrisis.shared.model.CarryState;
import com.identitycrisis.shared.model.Player;
import com.identitycrisis.shared.model.PlayerState;
import com.identitycrisis.shared.model.SafeZone;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Evaluates eliminations at round end.
 *
 * Warm-up rounds (1–2): every alive player outside any active
 * zone is eliminated. Capacity is unlimited so it is normal for nobody
 * to die here.
 * Elimination rounds (3+): capacity-1 per zone, drawn from
 * {@link SafeZoneManager#getZoneOccupants()}. Players who failed to
 * claim a zone are eliminated. In the typical {@code n-1} scenario
 * exactly one player is unclaimed; if the count differs (edge cases
 * around carry/throw or reconnection), the player farthest
 * from the nearest zone centre is eliminated as tiebreak.
 *
 * Single-player guard:
 * when only one player is alive the method
 * returns without eliminating anyone, so the milestone-A solo flow remains
 * playable indefinitely.
 */
public class EliminationManager {

    private final GameState gameState;
    private final CarryManager carryManager;

    public EliminationManager(GameState gameState, CarryManager carryManager) {
        this.gameState = gameState;
        this.carryManager = carryManager;
    }

    public List<Integer> evaluateEliminations() {
        List<Integer> eliminated = new ArrayList<>();
        List<SafeZone> zones = gameState.getActiveRoundZones();
        if (zones == null || zones.isEmpty())
            return eliminated;

        List<Player> alive = gameState.getAlivePlayers();

        // Support single-player testing: don't eliminate the sole player
        if (alive.size() <= 1) {
            return eliminated;
        }

        Set<Integer> activeCarryIds = activeCarryPlayerIds();
        if (!activeCarryIds.isEmpty()) {
            for (Integer playerId : activeCarryIds) {
                eliminatePlayer(playerId);
                eliminated.add(playerId);
            }
            if (gameState.getAliveCount() <= 1) {
                return eliminated;
            }
            alive = gameState.getAlivePlayers();
        }

        if (gameState.getRoundNumber() <= GameConfig.WARMUP_ROUNDS) {
            // Warm-up: every player outside any active zone is eliminated.
            for (Player p : alive) {
                if (!p.isInSafeZone()) {
                    eliminatePlayer(p.getPlayerId());
                    eliminated.add(p.getPlayerId());
                }
            }
            return eliminated;
        }

        // Elimination round (3+): exactly one zone per N-1 players, capacity 1.
        Map<Integer, Integer> claimed = computeZoneOccupants(zones, alive);
        Set<Integer> safeIds = new HashSet<>(claimed.values());

        List<Player> unsafe = alive.stream()
                .filter(p -> !safeIds.contains(p.getPlayerId()))
                .toList();

        if (unsafe.isEmpty()) {
            // Edge case: every alive player claimed a zone (shouldn't happen
            // with N-1 zones and capacity 1). Eliminate the player farthest
            // from their claimed zone's centre as tiebreak.
            Player farthest = alive.stream()
                    .max(Comparator.comparingDouble(p -> nearestZoneDistance(p, zones)))
                    .orElse(null);
            if (farthest != null) {
                eliminatePlayer(farthest.getPlayerId());
                eliminated.add(farthest.getPlayerId());
            }
        } else {
            // Eliminate the unsafe player farthest from the nearest zone
            // centre. In the normal n-1 case there is exactly one unsafe
            // player; the tiebreak only matters under edge-case desync.
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

    /**
     * First-claimer-wins occupancy snapshot. Mirrors
     * {@link SafeZoneManager#getZoneOccupants()} but operates on local
     * arguments so this class stays loosely coupled to the manager and is
     * independently unit-testable.
     */
    private Map<Integer, Integer> computeZoneOccupants(List<SafeZone> zones, List<Player> alive) {
        java.util.LinkedHashMap<Integer, Integer> claimed = new java.util.LinkedHashMap<>();
        Set<Integer> alreadySafe = new HashSet<>();
        for (SafeZone z : zones) {
            for (Player p : alive) {
                if (alreadySafe.contains(p.getPlayerId()))
                    continue;
                if (p.getState() == PlayerState.CARRYING
                        || p.getState() == PlayerState.CARRIED)
                    continue;
                if (z.contains(p.getPosition().x(), p.getPosition().y())) {
                    claimed.put(z.id(), p.getPlayerId());
                    alreadySafe.add(p.getPlayerId());
                    break;
                }
            }
        }
        return claimed;
    }

    private Set<Integer> activeCarryPlayerIds() {
        Set<Integer> ids = new HashSet<>();
        for (CarryState cs : gameState.getActiveCarries()) {
            ids.add(cs.carrierPlayerId());
            ids.add(cs.carriedPlayerId());
        }
        return ids;
    }

    private double nearestZoneDistance(Player p, List<SafeZone> zones) {
        double best = Double.MAX_VALUE;
        for (SafeZone z : zones) {
            double d = p.getPosition().distanceTo(z.center());
            if (d < best)
                best = d;
        }
        return best;
    }

    private void eliminatePlayer(int playerId) {
        Player p = gameState.getPlayerById(playerId);
        if (p != null) {
            carryManager.releaseCarry(playerId); // frees partner; may temporarily set p to ALIVE
            p.setState(PlayerState.ELIMINATED); // override — eliminated wins
            p.setInSafeZone(false);
            // Prune controlMap so the eliminated player can no longer control anyone,
            // and any living client currently CONTROL_SWAP'd onto this player is restored
            // to self-control. Without this, applyControlSwap() would include dead
            // players in the derangement shuffle, potentially assigning a living client
            // to control an eliminated (immovable) player.
            Map<Integer, Integer> cm = gameState.getControlMap();
            cm.remove(playerId);
            cm.replaceAll((clientId, controlled) -> controlled.equals(playerId) ? clientId : controlled);
        }
    }

    public boolean isGameOver() {
        // Support single-player testing: if the game started with 1 player,
        // don't end the game immediately, wait until they actually die.
        if (gameState.getPlayers().size() == 1) {
            return gameState.getAliveCount() == 0;
        }
        return gameState.getAliveCount() <= 1;
    }

    public int getWinnerId() {
        return gameState.getAlivePlayers().stream()
                .findFirst()
                .map(Player::getPlayerId)
                .orElse(0);
    }
}
