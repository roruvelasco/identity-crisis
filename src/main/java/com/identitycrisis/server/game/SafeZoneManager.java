package com.identitycrisis.server.game;

import com.identitycrisis.shared.model.GameConfig;
import com.identitycrisis.shared.model.Player;
import com.identitycrisis.shared.model.PlayerState;
import com.identitycrisis.shared.model.SafeZone;
import com.identitycrisis.shared.util.Vector2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Safe zone spawning, occupancy, decoy generation.
 * True position is SERVER-ONLY. Clients get true + decoys mixed.
 */
public class SafeZoneManager {

    private final GameState gameState;
    private final Random rng = new Random();

    public SafeZoneManager(GameState gameState) { this.gameState = gameState; }

    public void spawnSafeZone() {
        Vector2D pos = randomSafePosition();
        gameState.setTrueSafeZone(new SafeZone(pos, GameConfig.SAFE_ZONE_RADIUS));
    }

    private Vector2D randomSafePosition() {
        return randomSafePosition(this.rng);
    }

    private Vector2D randomSafePosition(Random r) {
        double margin = GameConfig.SAFE_ZONE_MIN_MARGIN;
        double w = gameState.getArena().getWidth();
        double h = gameState.getArena().getHeight();
        int maxRetries = 10;
        for (int i = 0; i < maxRetries; i++) {
            double x = margin + r.nextDouble() * (w - 2 * margin);
            double y = margin + r.nextDouble() * (h - 2 * margin);
            return new Vector2D(x, y); // no obstacles defined yet; retry logic ready
        }
        return new Vector2D(w / 2, h / 2);
    }

    public void updateOccupancy() {
        SafeZone zone = gameState.getTrueSafeZone();
        if (zone == null) return;
        for (Player p : gameState.getAlivePlayers()) {
            double dist = p.getPosition().distanceTo(zone.position());
            boolean inRange = dist <= zone.radius();
            if (inRange && p.getState() != PlayerState.CARRYING
                        && p.getState() != PlayerState.CARRIED) {
                p.setInSafeZone(true);
            } else {
                p.setInSafeZone(false);
            }
        }
    }

    /**
     * Per-client zone list.
     * During FAKE_SAFE_ZONES: 1 true + N decoys shuffled.
     */
    public List<SafeZone> generateClientSafeZones(int clientId,
                                                   boolean fakeChaosActive) {
        List<SafeZone> result = new ArrayList<>();
        SafeZone trueSafeZone = gameState.getTrueSafeZone();
        if (trueSafeZone != null) result.add(trueSafeZone);
        if (fakeChaosActive) {
            Random clientRng = new Random(Objects.hash(clientId, gameState.getRoundNumber()));
            for (int i = 0; i < GameConfig.FAKE_SAFE_ZONE_COUNT; i++) {
                result.add(new SafeZone(randomSafePosition(clientRng), GameConfig.SAFE_ZONE_RADIUS));
            }
            Collections.shuffle(result, clientRng);
        }
        return result;
    }

    public int getOccupantCount() {
        return (int) gameState.getAlivePlayers().stream().filter(Player::isInSafeZone).count();
    }

    public List<Integer> getOccupantPlayerIds() {
        return gameState.getAlivePlayers().stream()
            .filter(Player::isInSafeZone)
            .map(Player::getPlayerId)
            .toList();
    }
}
