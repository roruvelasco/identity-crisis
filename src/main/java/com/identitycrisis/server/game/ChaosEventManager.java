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
    private final Random rng = new Random();
    private static final boolean CHAOS_EVENTS_ENABLED = Boolean.getBoolean("identitycrisis.chaosEvents");
    private static final List<ChaosEventType> ENABLED_EVENTS = List.of(
            ChaosEventType.REVERSED_CONTROLS,
            ChaosEventType.FAKE_SAFE_ZONES);

    public ChaosEventManager(GameState gameState) { this.gameState = gameState; }

    public void resetForNewRound() {
        clearActiveEvent();
        gameState.setChaosEventTimer(CHAOS_EVENTS_ENABLED ? GameConfig.CHAOS_EVENT_DURATION : 0);
    }

    public void tick(double dt) {
        if (!CHAOS_EVENTS_ENABLED) {
            clearActiveEvent();
            return;
        }
        if (gameState.getPhase() != RoundPhase.ACTIVE) return;

        double remaining = gameState.getChaosEventTimer() - dt;
        if (gameState.getActiveChaosEvent() == ChaosEventType.NONE) {
            if (remaining <= 0) {
                activateEvent(pickRandomEvent());
            } else {
                gameState.setChaosEventTimer(remaining);
            }
            return;
        }

        if (remaining <= 0) {
            cycleEvent();
        } else {
            gameState.setChaosEventTimer(remaining);
        }
    }

    private ChaosEventType pickRandomEvent() {
        return ENABLED_EVENTS.get(rng.nextInt(ENABLED_EVENTS.size()));
    }

    private ChaosEventType pickRandomEventExcept(ChaosEventType current) {
        if (ENABLED_EVENTS.size() <= 1) {
            return pickRandomEvent();
        }
        ChaosEventType next;
        do {
            next = pickRandomEvent();
        } while (next == current);
        return next;
    }

    private void cycleEvent() {
        ChaosEventType current = gameState.getActiveChaosEvent();
        deactivateEvent(current);
        activateEvent(pickRandomEventExcept(current));
    }

    private void activateEvent(ChaosEventType event) {
        gameState.setActiveChaosEvent(event);
        gameState.setChaosEventTimer(GameConfig.CHAOS_EVENT_DURATION);
        if (event == ChaosEventType.CONTROL_SWAP) {
            applyControlSwap();
        }
    }

    private void deactivateEvent(ChaosEventType event) {
        if (event == ChaosEventType.CONTROL_SWAP) {
            revertControlSwap();
        }
        gameState.setActiveChaosEvent(ChaosEventType.NONE);
        gameState.setChaosEventTimer(0);
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
        deactivateEvent(gameState.getActiveChaosEvent());
    }
}
