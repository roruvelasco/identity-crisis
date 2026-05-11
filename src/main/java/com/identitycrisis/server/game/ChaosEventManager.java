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
    private static final boolean CHAOS_EVENTS_ENABLED = !Boolean.getBoolean("identitycrisis.disableChaosEvents");
    private static final List<ChaosEventType> ENABLED_EVENTS = List.of(
            ChaosEventType.REVERSED_CONTROLS,
            ChaosEventType.CONTROL_SWAP,
            ChaosEventType.FAKE_SAFE_ZONES);

    public ChaosEventManager(GameState gameState) { this.gameState = gameState; }

    public void resetForNewRound() {
        clearActiveEvent();
        gameState.setChaosEventTimer(CHAOS_EVENTS_ENABLED ? 0 : 0);
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
        gameState.setActiveChaosEvent(ChaosEventType.NONE);
        gameState.setChaosEventTimer(0);
    }

    private void applyControlSwap() {
        Map<Integer, Integer> map = gameState.getControlMap();
        List<Integer> clientIds  = new ArrayList<>(map.keySet());
        Collections.sort(clientIds);
        List<Integer> playerIds = new ArrayList<>(clientIds);
        List<Integer> previousPlayerIds = new ArrayList<>();
        for (Integer clientId : clientIds) {
            previousPlayerIds.add(map.getOrDefault(clientId, clientId));
        }
        if (clientIds.size() <= 1) return;
        List<Integer> best = new ArrayList<>(playerIds);
        int bestUnchanged = clientIds.size();
        for (int attempt = 0; attempt < 40; attempt++) {
            Collections.shuffle(playerIds, rng);
            int unchanged = countUnchangedAssignments(previousPlayerIds, playerIds);
            if (unchanged < bestUnchanged) {
                best = new ArrayList<>(playerIds);
                bestUnchanged = unchanged;
            }
            if (unchanged == 0) {
                break;
            }
        }
        for (int i = 0; i < clientIds.size(); i++) {
            map.put(clientIds.get(i), best.get(i));
        }
    }

    private int countUnchangedAssignments(List<Integer> previous, List<Integer> next) {
        int count = 0;
        for (int i = 0; i < previous.size(); i++) {
            if (previous.get(i).equals(next.get(i))) count++;
        }
        return count;
    }

    public boolean isFakeSafeZonesActive() {
        return gameState.getActiveChaosEvent() == ChaosEventType.FAKE_SAFE_ZONES;
    }

    /** Clears any active chaos event immediately (called at ACTIVE → ROUND_END). */
    public void clearActiveEvent() {
        deactivateEvent(gameState.getActiveChaosEvent());
    }
}
