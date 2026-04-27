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

/**
 * Safe-zone selection, occupancy tracking, and decoy generation.
 *
 * <p>Each round, {@link #spawnRoundZones(int)} picks a random subset of the
 * eight TMX-defined rectangles (see {@link SafeZoneSpots#ALL}) and stores them
 * in {@link GameState#getActiveRoundZones()} as the round's pool of valid
 * shelters.  These are the <strong>real</strong> zones the server uses for
 * occupancy and elimination decisions.
 *
 * <p>Clients always receive the real zones.  When the
 * {@code FAKE_SAFE_ZONES} chaos event is active they additionally receive
 * decoys drawn from the <em>unused</em> TMX spots so the visual output is
 * indistinguishable from a real zone.  Decoys are never stored on the server.
 */
public class SafeZoneManager {

    private final GameState gameState;
    private final Random rng = new Random();

    public SafeZoneManager(GameState gameState) { this.gameState = gameState; }

    /**
     * Selects {@code count} distinct safe-zone rectangles at random from
     * {@link SafeZoneSpots#ALL} and writes them to
     * {@link GameState#setActiveRoundZones(List)}.  The count is clamped to
     * {@code [SAFE_ZONE_MIN_ZONES, TOTAL_SAFE_ZONE_SPOTS]} so the caller never
     * has to handle empty pools or out-of-range requests.
     *
     * <p>Called by {@link RoundManager#startNewRound()} and by
     * {@link LobbyManager#handleReady} for round 1.
     */
    public void spawnRoundZones(int count) {
        int clamped = Math.max(GameConfig.SAFE_ZONE_MIN_ZONES,
                       Math.min(count, GameConfig.TOTAL_SAFE_ZONE_SPOTS));
        List<SafeZone> pool = new ArrayList<>(SafeZoneSpots.ALL);
        Collections.shuffle(pool, rng);
        gameState.setActiveRoundZones(pool.subList(0, clamped));
    }

    /**
     * Stamps {@link Player#setInSafeZone(boolean)} on every alive player based
     * on whether their position lies inside any of the round's active
     * rectangles.  Players being carried (or carrying) are forced to
     * {@code false} per game rules.
     *
     * <p>Capacity is <em>not</em> enforced here — it is applied at round-end
     * by {@link EliminationManager} via {@link #getZoneOccupants()}.  This
     * separation keeps the per-tick occupancy pass cheap and lets warm-up
     * rounds (unlimited capacity) reuse the same logic.
     */
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

    /**
     * Returns a {@code zoneId → playerId} map identifying the first player
     * found inside each active zone (insertion-ordered by zone iteration).
     *
     * <p>Used in elimination rounds (round 3+) to enforce the capacity-of-one
     * rule: once a zone is "claimed", subsequent players entering it do
     * <em>not</em> count as safe.  Every active zone appears as a key; zones
     * with no occupant map to {@code null}-equivalent (absent from the map).
     *
     * <p>Carriers and carried players are skipped — they cannot claim a zone.
     */
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

    /**
     * Per-client zone list sent in every state snapshot.  Always includes the
     * round's real zones; under {@code FAKE_SAFE_ZONES} chaos, also includes
     * up to {@link GameConfig#FAKE_SAFE_ZONE_COUNT} decoy rectangles drawn
     * from unused TMX spots.  Each client's decoys are deterministic but
     * differ between clients (seeded by {@code clientId + roundNumber}) so
     * coordinated cheating across clients is impossible.
     */
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

    /**
     * Number of alive players currently inside any active zone (occupancy is
     * unbounded — this is the warm-up count, not the elimination claim count).
     */
    public int getOccupantCount() {
        return (int) gameState.getAlivePlayers().stream().filter(Player::isInSafeZone).count();
    }

    /** Player IDs of every alive player currently inside any active zone. */
    public List<Integer> getOccupantPlayerIds() {
        return gameState.getAlivePlayers().stream()
            .filter(Player::isInSafeZone)
            .map(Player::getPlayerId)
            .toList();
    }
}
