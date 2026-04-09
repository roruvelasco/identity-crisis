package com.identitycrisis.server.game;

import com.identitycrisis.shared.model.CarryState;
import com.identitycrisis.shared.model.GameConfig;
import com.identitycrisis.shared.model.Player;
import com.identitycrisis.shared.model.PlayerState;
import com.identitycrisis.shared.util.Vector2D;

/**
 * Carry/throw mechanics, server-authoritative.
 * - Carry: within CARRY_RANGE, neither in existing carry.
 * - Carrier speed reduced, carried position locked to carrier + offset.
 * - Carrier CANNOT be marked safe while carrying.
 * - Throw: release with velocity in facing direction, brief stun on carried.
 */
public class CarryManager {

    private final GameState gameState;

    public CarryManager(GameState gameState) { this.gameState = gameState; }

    public boolean tryCarry(int carrierPlayerId) {
        Player carrier = gameState.getPlayerById(carrierPlayerId);
        if (carrier == null || carrier.getState() != PlayerState.ALIVE) return false;
        if (carrier.getCarryingPlayerId() != -1) return false;

        int targetId = findNearestCarryTarget(carrierPlayerId);
        if (targetId == -1) return false;

        Player target = gameState.getPlayerById(targetId);
        if (target == null || target.getState() != PlayerState.ALIVE) return false;
        if (target.getCarriedByPlayerId() != -1) return false;

        carrier.setState(PlayerState.CARRYING);
        carrier.setCarryingPlayerId(targetId);
        target.setState(PlayerState.CARRIED);
        target.setCarriedByPlayerId(carrierPlayerId);
        gameState.getActiveCarries().add(new CarryState(carrierPlayerId, targetId));
        return true;
    }

    public void throwCarried(int carrierPlayerId) {
        Player carrier = gameState.getPlayerById(carrierPlayerId);
        if (carrier == null) return;
        int carriedId = carrier.getCarryingPlayerId();
        if (carriedId == -1) return;

        Player carried = gameState.getPlayerById(carriedId);
        if (carried == null) return;

        Vector2D throwDir = facingToVector(carrier.getFacingDirection());
        carried.setVelocity(throwDir.multiply(GameConfig.THROW_SPEED));

        carrier.setState(PlayerState.ALIVE);
        carrier.setCarryingPlayerId(-1);
        carried.setState(PlayerState.ALIVE);
        carried.setCarriedByPlayerId(-1);

        gameState.getActiveCarries().removeIf(cs -> cs.carrierPlayerId() == carrierPlayerId);
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

    /**
     * Releases any carry relationship that involves {@code playerId} — either as
     * carrier or as the carried player. Called when a client disconnects mid-carry
     * to prevent the other player from being permanently stuck.
     */
    public void releaseCarry(int playerId) {
        Player p = gameState.getPlayerById(playerId);
        if (p == null) return;

        if (p.getCarryingPlayerId() != -1) {
            Player carried = gameState.getPlayerById(p.getCarryingPlayerId());
            if (carried != null) {
                carried.setState(PlayerState.ALIVE);
                carried.setCarriedByPlayerId(-1);
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
        }
        gameState.getActiveCarries().removeIf(
            cs -> cs.carrierPlayerId() == playerId || cs.carriedPlayerId() == playerId);
    }

    private int findNearestCarryTarget(int carrierPlayerId) {
        Player carrier = gameState.getPlayerById(carrierPlayerId);
        double minDist = Double.MAX_VALUE;
        int targetId = -1;
        for (Player p : gameState.getAlivePlayers()) {
            if (p.getPlayerId() == carrierPlayerId) continue;
            if (p.getCarriedByPlayerId() != -1) continue;
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
            case 1 -> new Vector2D(1,  0);
            case 2 -> new Vector2D(0,  1);
            case 3 -> new Vector2D(-1, 0);
            default -> new Vector2D(0,  1);
        };
    }
}
