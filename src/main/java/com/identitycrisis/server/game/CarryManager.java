package com.identitycrisis.server.game;

import com.identitycrisis.server.physics.TmxWallsParser.WallCollisionData;
import com.identitycrisis.shared.model.CarryState;
import com.identitycrisis.shared.model.GameConfig;
import com.identitycrisis.shared.model.Player;
import com.identitycrisis.shared.model.PlayerState;
import com.identitycrisis.shared.util.Vector2D;
import java.util.HashMap;
import java.util.Map;

/**
 * Carry/throw mechanics, done by server.
 * - Carry: within CARRY_RANGE, neither in existing carry.
 * - Carrier speed reduced, carried position locked to carrier + offset.
 * - Carrier CANNOT be marked safe while carrying.
 * - Throw: release with velocity in facing direction, brief stun on carried.
 */
public class CarryManager {

    private final GameState gameState;
    private static final int RELEASE_PRESSES_REQUIRED = 7;
    private final Map<Integer, Integer> releasePressCounts = new HashMap<>();
    private final Map<Integer, Boolean> releaseHeld = new HashMap<>();
    private WallCollisionData wallData;

    public CarryManager(GameState gameState) {
        this.gameState = gameState;
    }

    public void setWallCollisionData(WallCollisionData wallData) {
        this.wallData = wallData;
    }

    public boolean tryCarry(int carrierPlayerId) {
        Player carrier = gameState.getPlayerById(carrierPlayerId);
        if (carrier == null || carrier.getState() != PlayerState.ALIVE)
            return false;
        if (carrier.getCarryingPlayerId() != -1)
            return false;

        int targetId = findNearestCarryTarget(carrierPlayerId);
        if (targetId == -1)
            return false;

        Player target = gameState.getPlayerById(targetId);
        if (target == null || target.getState() != PlayerState.ALIVE)
            return false;
        if (target.getCarriedByPlayerId() != -1)
            return false;

        carrier.setState(PlayerState.CARRYING);
        carrier.setCarryingPlayerId(targetId);
        target.setState(PlayerState.CARRIED);
        target.setCarriedByPlayerId(carrierPlayerId);
        releasePressCounts.put(targetId, 0);
        releaseHeld.put(targetId, false);
        gameState.getActiveCarries().add(new CarryState(carrierPlayerId, targetId));
        return true;
    }

    public void throwCarried(int carrierPlayerId) {
        Player carrier = gameState.getPlayerById(carrierPlayerId);
        if (carrier == null)
            return;
        int carriedId = carrier.getCarryingPlayerId();
        if (carriedId == -1)
            return;

        Player carried = gameState.getPlayerById(carriedId);
        if (carried == null)
            return;

        Vector2D throwDir = facingToVector(carrier.getFacingDirection());
        releasePair(carrier, carried, throwDir, true);
    }

    public void handleReleaseInput(int playerId, boolean releaseDown) {
        if (!releaseDown) {
            releaseHeld.put(playerId, false);
            return;
        }
        if (releaseHeld.getOrDefault(playerId, false)) {
            return;
        }
        releaseHeld.put(playerId, true);

        Player carried = gameState.getPlayerById(playerId);
        if (carried == null || carried.getState() != PlayerState.CARRIED) {
            return;
        }

        int count = releasePressCounts.getOrDefault(playerId, 0) + 1;
        releasePressCounts.put(playerId, count);
        if (count < RELEASE_PRESSES_REQUIRED) {
            return;
        }

        Player carrier = gameState.getPlayerById(carried.getCarriedByPlayerId());
        if (carrier == null) {
            releaseCarry(playerId);
            return;
        }
        releasePair(carrier, carried, facingToVector(carrier.getFacingDirection()).multiply(-1), false);
    }

    public void tick(double dt) {
        for (CarryState cs : gameState.getActiveCarries()) {
            Player carrier = gameState.getPlayerById(cs.carrierPlayerId());
            Player carried = gameState.getPlayerById(cs.carriedPlayerId());
            if (carrier != null && carried != null) {
                carried.setPosition(carrier.getPosition().add(
                        new Vector2D(0, -GameConfig.PLAYER_RADIUS)));
                carried.setVelocity(Vector2D.zero());
            }
        }
    }

    public void releaseAllCarries() {
        for (Player p : gameState.getAlivePlayers()) {
            if (p.getState() == PlayerState.CARRYING || p.getState() == PlayerState.CARRIED) {
                p.setState(PlayerState.ALIVE);
            }
            p.setCarryingPlayerId(-1);
            p.setCarriedByPlayerId(-1);
            p.setVelocity(Vector2D.zero());
            p.setStunTimer(0);
        }
        gameState.getActiveCarries().clear();
        releasePressCounts.clear();
        releaseHeld.clear();
    }

    /**
     * Releases any carry relationship that involves {@code playerId} — either as
     * carrier or as the carried player. Called when a client disconnects mid-carry
     * to prevent the other player from being permanently stuck.
     */
    public void releaseCarry(int playerId) {
        Player p = gameState.getPlayerById(playerId);
        if (p == null)
            return;

        if (p.getCarryingPlayerId() != -1) {
            Player carried = gameState.getPlayerById(p.getCarryingPlayerId());
            if (carried != null) {
                carried.setState(PlayerState.ALIVE);
                carried.setCarriedByPlayerId(-1);
                carried.setPosition(findLandingPosition(p.getPosition(), facingToVector(p.getFacingDirection())));
                carried.setVelocity(Vector2D.zero());
                releasePressCounts.remove(carried.getPlayerId());
                releaseHeld.remove(carried.getPlayerId());
            }
            p.setState(PlayerState.ALIVE);
            p.setCarryingPlayerId(-1);
        }
        if (p.getCarriedByPlayerId() != -1) {
            Player carrier = gameState.getPlayerById(p.getCarriedByPlayerId());
            if (carrier != null) {
                carrier.setState(PlayerState.ALIVE);
                carrier.setCarryingPlayerId(-1);
            }
            p.setState(PlayerState.ALIVE);
            p.setCarriedByPlayerId(-1);
            Vector2D origin = carrier != null ? carrier.getPosition() : p.getPosition();
            Vector2D direction = carrier != null ? facingToVector(carrier.getFacingDirection()).multiply(-1) : new Vector2D(0, 1);
            p.setPosition(findLandingPosition(origin, direction));
            p.setVelocity(Vector2D.zero());
            releasePressCounts.remove(p.getPlayerId());
            releaseHeld.remove(p.getPlayerId());
        }
        gameState.getActiveCarries().removeIf(
                cs -> cs.carrierPlayerId() == playerId || cs.carriedPlayerId() == playerId);
    }

    private void releasePair(Player carrier, Player carried, Vector2D direction, boolean thrown) {
        carried.setPosition(findLandingPosition(carrier.getPosition(), direction));
        carried.setVelocity(Vector2D.zero());
        carrier.setState(PlayerState.ALIVE);
        carrier.setCarryingPlayerId(-1);
        carried.setState(PlayerState.ALIVE);
        carried.setCarriedByPlayerId(-1);
        carried.setStunTimer(thrown ? GameConfig.THROW_STUN_SECONDS : 0);
        releasePressCounts.remove(carried.getPlayerId());
        releaseHeld.remove(carried.getPlayerId());
        gameState.getActiveCarries().removeIf(cs -> cs.carrierPlayerId() == carrier.getPlayerId());
    }

    private Vector2D findLandingPosition(Vector2D origin, Vector2D direction) {
        Vector2D dir = direction.magnitude() == 0 ? new Vector2D(0, 1) : direction.normalize();
        Vector2D perpendicular = new Vector2D(-dir.y(), dir.x());
        double spacing = Math.max(GameConfig.PLAYER_RADIUS * 2.0, 32.0);
        for (int forward = 1; forward <= 6; forward++) {
            for (int lateral = 0; lateral <= forward; lateral++) {
                for (int sign : new int[] { 0, -1, 1 }) {
                    if (sign == 0 && lateral != 0) {
                        continue;
                    }
                    Vector2D candidate = origin
                            .add(dir.multiply(spacing * forward))
                            .add(perpendicular.multiply(spacing * lateral * sign));
                    if (isWalkablePosition(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return nearestWalkablePosition(origin);
    }

    private Vector2D nearestWalkablePosition(Vector2D origin) {
        if (wallData == null || wallData.isEmpty()) {
            return clampToWorld(origin);
        }
        int ts = wallData.tileSize();
        int startCol = clamp((int) Math.floor(origin.x() / ts), 0, wallData.worldCols() - 1);
        int startRow = clamp((int) Math.floor(origin.y() / ts), 0, wallData.worldRows() - 1);
        for (int radius = 0; radius < Math.max(wallData.worldCols(), wallData.worldRows()); radius++) {
            for (int row = startRow - radius; row <= startRow + radius; row++) {
                for (int col = startCol - radius; col <= startCol + radius; col++) {
                    if (row < 0 || col < 0 || row >= wallData.worldRows() || col >= wallData.worldCols()) {
                        continue;
                    }
                    if (wallData.spawnableGrid()[row][col] && !wallData.solidGrid()[row][col]) {
                        return new Vector2D(col * ts + ts / 2.0, row * ts + ts / 2.0);
                    }
                }
            }
        }
        return clampToWorld(origin);
    }

    private boolean isWalkablePosition(Vector2D position) {
        if (wallData == null || wallData.isEmpty()) {
            Vector2D clamped = clampToWorld(position);
            return clamped.distanceTo(position) < 0.001;
        }
        int ts = wallData.tileSize();
        int col = (int) Math.floor(position.x() / ts);
        int row = (int) Math.floor(position.y() / ts);
        return row >= 0 && col >= 0
                && row < wallData.worldRows()
                && col < wallData.worldCols()
                && wallData.spawnableGrid()[row][col]
                && !wallData.solidGrid()[row][col];
    }

    private Vector2D clampToWorld(Vector2D position) {
        double worldW = wallData != null && !wallData.isEmpty()
                ? wallData.worldCols() * wallData.tileSize()
                : gameState.getArena().getWidth();
        double worldH = wallData != null && !wallData.isEmpty()
                ? wallData.worldRows() * wallData.tileSize()
                : gameState.getArena().getHeight();
        double margin = GameConfig.PLAYER_RADIUS;
        return new Vector2D(
                Math.max(margin, Math.min(position.x(), worldW - margin)),
                Math.max(margin, Math.min(position.y(), worldH - margin)));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private int findNearestCarryTarget(int carrierPlayerId) {
        Player carrier = gameState.getPlayerById(carrierPlayerId);
        double minDist = Double.MAX_VALUE;
        int targetId = -1;
        for (Player p : gameState.getAlivePlayers()) {
            if (p.getPlayerId() == carrierPlayerId)
                continue;
            if (p.getCarriedByPlayerId() != -1)
                continue;
            double dist = carrier.getPosition().distanceTo(p.getPosition());
            if (dist <= GameConfig.CARRY_RANGE && dist < minDist) {
                minDist = dist;
                targetId = p.getPlayerId();
            }
        }
        return targetId;
    }

    private Vector2D facingToVector(int facing) {
        return switch (facing) {
            case 0 -> new Vector2D(0, -1);
            case 1 -> new Vector2D(1, 0);
            case 2 -> new Vector2D(0, 1);
            case 3 -> new Vector2D(-1, 0);
            default -> new Vector2D(0, 1);
        };
    }
}
