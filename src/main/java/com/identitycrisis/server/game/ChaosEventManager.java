package com.identitycrisis.server.game;

import com.identitycrisis.shared.model.ChaosEventType;
import com.identitycrisis.shared.model.GameConfig;
import com.identitycrisis.shared.model.RoundPhase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Triggers and manages chaos events during ACTIVE phase.
 * REVERSED_CONTROLS: flag sent to client, client inverts input.
 * CONTROL_SWAP: remap controlMap so each client controls different player.
 * FAKE_SAFE_ZONES: SafeZoneManager generates decoys per client.
 */
public class ChaosEventManager {

    private final GameState gameState;
    private double scheduledTriggerTime;
    private double elapsedInRound;
    private final Random rng = new Random();

    public ChaosEventManager(GameState gameState) { this.gameState = gameState; }

    public void resetForNewRound() {
        elapsedInRound = 0;
        gameState.setActiveChaosEvent(ChaosEventType.NONE);
        gameState.setChaosEventTimer(0);
        scheduledTriggerTime = GameConfig.CHAOS_EVENT_MIN_DELAY
            + rng.nextDouble() * (GameConfig.CHAOS_EVENT_MAX_DELAY - GameConfig.CHAOS_EVENT_MIN_DELAY);
    }

    public void tick(double dt) {
        if (gameState.getPhase() != RoundPhase.ACTIVE) return;

        if (gameState.getActiveChaosEvent() != ChaosEventType.NONE) {
            double remaining = gameState.getChaosEventTimer() - dt;
            if (remaining <= 0) {
                if (gameState.getActiveChaosEvent() == ChaosEventType.CONTROL_SWAP) {
                    revertControlSwap();
                }
                gameState.setActiveChaosEvent(ChaosEventType.NONE);
                gameState.setChaosEventTimer(0);
            } else {
                gameState.setChaosEventTimer(remaining);
            }
            return;
        }

        elapsedInRound += dt;
        if (elapsedInRound >= scheduledTriggerTime) {
            ChaosEventType event = pickRandomEvent();
            gameState.setActiveChaosEvent(event);
            gameState.setChaosEventTimer(GameConfig.CHAOS_EVENT_DURATION);
            if (event == ChaosEventType.CONTROL_SWAP) {
                applyControlSwap();
            }
            // Prevent re-triggering within the same round. elapsedInRound continues
            // to grow but can never exceed MAX_VALUE again until resetForNewRound()
            // picks a fresh scheduledTriggerTime for the next round.
            scheduledTriggerTime = Double.MAX_VALUE;
        }
    }

    private ChaosEventType pickRandomEvent() {
        ChaosEventType[] options = {
            ChaosEventType.REVERSED_CONTROLS,
            ChaosEventType.CONTROL_SWAP,
            ChaosEventType.FAKE_SAFE_ZONES
        };
        return options[rng.nextInt(options.length)];
    }

    private void applyControlSwap() {
        Map<Integer, Integer> map = gameState.getControlMap();
        List<Integer> clientIds  = new ArrayList<>(map.keySet());
        List<Integer> playerIds  = new ArrayList<>(map.values());
        // A derangement (no fixed point) is impossible with ≤1 element — the loop
        // would spin forever. Guard here; in practice this means only 1 player is
        // left (game should already be GAME_OVER), or controlMap wasn't pruned.
        if (clientIds.size() <= 1) return;
        do {
            Collections.shuffle(playerIds, rng);
        } while (hasFixedPoint(clientIds, playerIds));
        for (int i = 0; i < clientIds.size(); i++) {
            map.put(clientIds.get(i), playerIds.get(i));
        }
    }

    private boolean hasFixedPoint(List<Integer> keys, List<Integer> vals) {
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i).equals(vals.get(i))) return true;
        }
        return false;
    }

    private void revertControlSwap() {
        Map<Integer, Integer> map = gameState.getControlMap();
        for (Integer clientId : new ArrayList<>(map.keySet())) {
            map.put(clientId, clientId);
        }
    }

    public boolean isFakeSafeZonesActive() {
        return gameState.getActiveChaosEvent() == ChaosEventType.FAKE_SAFE_ZONES;
    }

    /** Clears any active chaos event immediately (called at ACTIVE → ROUND_END). */
    public void clearActiveEvent() {
        if (gameState.getActiveChaosEvent() == ChaosEventType.CONTROL_SWAP) {
            revertControlSwap();
        }
        gameState.setActiveChaosEvent(ChaosEventType.NONE);
        gameState.setChaosEventTimer(0);
    }
}
