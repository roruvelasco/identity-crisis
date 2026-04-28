package com.identitycrisis.server.game;

import com.identitycrisis.shared.model.GameConfig;
import com.identitycrisis.shared.model.Player;
import com.identitycrisis.shared.model.PlayerState;
import com.identitycrisis.shared.model.SafeZone;
import com.identitycrisis.shared.model.SafeZoneSpots;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/** Safe-zone selection, occupancy tracking, and decoy generation. */
public class SafeZoneManager {

    private final GameState gameState;
    private final Random rng = new Random();

    public SafeZoneManager(GameState gameState) { this.gameState = gameState; }

    /** Selects random safe-zone rectangles and writes to game state. */
    public void spawnRoundZones(int count) {
        int clamped = Math.max(GameConfig.SAFE_ZONE_MIN_ZONES,
                       Math.min(count, GameConfig.TOTAL_SAFE_ZONE_SPOTS));
        List<SafeZone> pool = new ArrayList<>(SafeZoneSpots.ALL);
        Collections.shuffle(pool, rng);
        gameState.setActiveRoundZones(pool.subList(0, clamped));
    }

    /** Updates player safe-zone flags based on position. */
    public void updateOccupancy() {
        List<SafeZone> zones = gameState.getActiveRoundZones();
        if (zones == null || zones.isEmpty()) {
            for (Player p : gameState.getAlivePlayers()) p.setInSafeZone(false);
            return;
        }
        for (Player p : gameState.getAlivePlayers()) {
            if (p.getState() == PlayerState.CARRYING
             || p.getState() == PlayerState.CARRIED) {
                p.setInSafeZone(false);
                continue;
            }
            boolean inAny = false;
            for (SafeZone z : zones) {
                if (z.contains(p.getPosition().x(), p.getPosition().y())) {
                    inAny = true;
                    break;
                }
            }
            p.setInSafeZone(inAny);
        }
    }

    /** Returns zoneId→playerId map for first player in each zone. */
    public Map<Integer, Integer> getZoneOccupants() {
        Map<Integer, Integer> claimed = new LinkedHashMap<>();
        List<SafeZone> zones = gameState.getActiveRoundZones();
        if (zones == null) return claimed;

        Set<Integer> alreadySafe = new HashSet<>();
        for (SafeZone z : zones) {
            for (Player p : gameState.getAlivePlayers()) {
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

    /** Generates per-client zone list with decoys under fake chaos. */
    public List<SafeZone> generateClientSafeZones(int clientId,
                                                   boolean fakeChaosActive) {
        List<SafeZone> real   = new ArrayList<>(gameState.getActiveRoundZones());
        if (!fakeChaosActive) return real;

        Set<Integer> realIds = new HashSet<>();
        for (SafeZone z : real) realIds.add(z.id());

        List<SafeZone> unused = new ArrayList<>();
        for (SafeZone spot : SafeZoneSpots.ALL) {
            if (!realIds.contains(spot.id())) unused.add(spot);
        }
        if (unused.isEmpty()) return real;

        Random clientRng = new Random(Objects.hash(clientId, gameState.getRoundNumber()));
        Collections.shuffle(unused, clientRng);
        int decoyCount = Math.min(GameConfig.FAKE_SAFE_ZONE_COUNT, unused.size());

        List<SafeZone> result = new ArrayList<>(real.size() + decoyCount);
        result.addAll(real);
        result.addAll(unused.subList(0, decoyCount));
        Collections.shuffle(result, clientRng);
        return result;
    }

    /** Returns count of alive players inside any active zone. */
    public int getOccupantCount() {
        return (int) gameState.getAlivePlayers().stream().filter(Player::isInSafeZone).count();
    }

    /** Returns player IDs of alive players inside any active zone. */
    public List<Integer> getOccupantPlayerIds() {
        return gameState.getAlivePlayers().stream()
            .filter(Player::isInSafeZone)
            .map(Player::getPlayerId)
            .toList();
    }
}
