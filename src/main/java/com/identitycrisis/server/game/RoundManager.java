package com.identitycrisis.server.game;

import com.identitycrisis.server.physics.TmxWallsParser.WallCollisionData;
import com.identitycrisis.shared.model.GameConfig;
import com.identitycrisis.shared.model.Player;
import com.identitycrisis.shared.model.RoundPhase;
import com.identitycrisis.shared.util.Vector2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Drives the round state machine.
 * LOBBY -> COUNTDOWN -> ACTIVE -> ROUND_END -> ELIMINATION -> COUNTDOWN -> ...
 * When 1 player left -> GAME_OVER
 */
public class RoundManager {

    private final GameState gameState;
    private final SafeZoneManager safeZoneManager;
    private final ChaosEventManager chaosEventManager;
    private final EliminationManager eliminationManager;
    private final Random spawnRng = new Random();
    private WallCollisionData wallData;

    public RoundManager(GameState gs, SafeZoneManager szm,
            ChaosEventManager cem, EliminationManager em) {
        this.gameState = gs;
        this.safeZoneManager = szm;
        this.chaosEventManager = cem;
        this.eliminationManager = em;
    }

    public void setWallCollisionData(WallCollisionData wallData) {
        this.wallData = wallData;
    }

    public void tick(double dt) {
        switch (gameState.getPhase()) {
            case LOBBY -> {
                /* LobbyManager handles this externally */ }

            case COUNTDOWN -> {
                gameState.setRoundTimer(gameState.getRoundTimer() - dt);
                if (gameState.getRoundTimer() <= 0) {
                    transitionTo(RoundPhase.ACTIVE);
                    gameState.setRoundTimer(GameConfig.ROUND_DURATION_SECONDS);
                    chaosEventManager.resetForNewRound();
                }
            }

            case ACTIVE -> {
                gameState.setRoundTimer(gameState.getRoundTimer() - dt);

                if (shouldCompleteRoundImmediately()) {
                    finishRound(true);
                } else if (gameState.getRoundTimer() <= 0) {
                    finishRound(false);
                }
            }

            // ROUND_END is a one-tick transient state: evaluate eliminations then
            // immediately advance to ELIMINATION. This block executes exactly once
            // per round-end transition because transitionTo() changes the phase on
            // the same tick. Do NOT add timers or blocking logic here without also
            // adding an idempotency guard — re-entry would call evaluateEliminations()
            // again and eliminate extra players.
            case ROUND_END -> {
                finishRound(false);
            }

            case ELIMINATION -> {
                gameState.setRoundTimer(gameState.getRoundTimer() - dt);
                if (gameState.getRoundTimer() <= 0) {
                    advanceAfterElimination();
                }
            }

            case GAME_OVER -> {
                /* Game is done */ }
        }
    }

    private void transitionTo(RoundPhase phase) {
        gameState.setPhase(phase);
    }

    private boolean shouldCompleteRoundImmediately() {
        List<Player> alive = gameState.getAlivePlayers();
        if (alive.isEmpty()) {
            return false;
        }
        if (isWarmupRound()) {
            return alive.stream().allMatch(Player::isInSafeZone);
        }
        if (alive.size() <= 1) {
            return false;
        }
        int requiredClaims = Math.max(GameConfig.SAFE_ZONE_MIN_ZONES, alive.size() - 1);
        Map<Integer, Integer> claimed = safeZoneManager.getZoneOccupants();
        return claimed.size() >= requiredClaims;
    }

    private void finishRound(boolean immediateAdvance) {
        chaosEventManager.clearActiveEvent();
        List<Integer> eliminated = eliminationManager.evaluateEliminations();
        gameState.getPendingEliminationIds().addAll(eliminated);
        if (immediateAdvance) {
            advanceAfterElimination();
            return;
        }
        transitionTo(RoundPhase.ELIMINATION);
        gameState.setRoundTimer(GameConfig.ELIMINATION_DISPLAY_SECONDS);
    }

    private void advanceAfterElimination() {
        if (eliminationManager.isGameOver()) {
            gameState.setPendingGameOverWinnerId(eliminationManager.getWinnerId());
            transitionTo(RoundPhase.GAME_OVER);
        } else {
            gameState.setRoundNumber(gameState.getRoundNumber() + 1);
            startNewRound();
            transitionTo(RoundPhase.COUNTDOWN);
            gameState.setRoundTimer(GameConfig.COUNTDOWN_SECONDS);
        }
    }

    /**
     * Begins the next round by selecting that round's safe zones and resetting
     * player positions / per-round flags.
     *
     * Zone-count formula:
     * Warm-up rounds (1–2):
     * {@code zoneCount = aliveCount} —
     * everyone can fit, occupancy is unlimited. All players outside
     * any zone at round end are eliminated.
     * 
     * Elimination rounds (3+):
     * {@code zoneCount = max(SAFE_ZONE_MIN_ZONES, aliveCount - 1)} with
     * capacity 1 per zone. The {@code max(1, …)} clamp is the
     * single-player placeholder: with one alive player, the formula
     * {@code aliveCount - 1} would give zero zones and instant loss;
     * clamping to one keeps the lone player playable for milestone
     * testing (combined with
     * {@link EliminationManager#evaluateEliminations()}'s sole-survivor
     * guard that prevents the last alive player from being eliminated).
     */
    private void startNewRound() {
        chaosEventManager.clearActiveEvent();

        List<Player> alive = gameState.getAlivePlayers();
        int zoneCount = isWarmupRound()
                ? 1
                : Math.max(GameConfig.SAFE_ZONE_MIN_ZONES, alive.size() - 1);
        safeZoneManager.spawnRoundZones(zoneCount);
        List<Vector2D> spawns = generateSpawnPositions(alive.size());
        for (int i = 0; i < alive.size(); i++) {
            alive.get(i).setPosition(spawns.get(i));
            alive.get(i).setVelocity(Vector2D.zero());
            alive.get(i).setInSafeZone(false);
        }
    }

    private List<Vector2D> generateSpawnPositions(int count) {
        List<Vector2D> candidates = largestWalkableComponent();
        if (candidates.isEmpty()) {
            return fallbackSpawnPositions(count);
        }

        Collections.shuffle(candidates, spawnRng);
        List<Vector2D> chosen = new ArrayList<>(count);
        double minDist = GameConfig.PLAYER_RADIUS * 4.0;
        for (Vector2D candidate : candidates) {
            boolean farEnough = true;
            for (Vector2D existing : chosen) {
                if (candidate.distanceTo(existing) < minDist) {
                    farEnough = false;
                    break;
                }
            }
            if (farEnough) {
                chosen.add(candidate);
                if (chosen.size() == count) {
                    return chosen;
                }
            }
        }

        for (Vector2D candidate : candidates) {
            if (chosen.size() == count) {
                break;
            }
            chosen.add(candidate);
        }
        return chosen;
    }

    private List<Vector2D> largestWalkableComponent() {
        if (wallData == null || wallData.isEmpty()) {
            return List.of();
        }

        int rows = wallData.worldRows();
        int cols = wallData.worldCols();
        boolean[][] visited = new boolean[rows][cols];
        List<int[]> best = new ArrayList<>();

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                if (visited[row][col] || !isSpawnTileWalkable(row, col)) {
                    continue;
                }
                List<int[]> component = floodFillWalkable(row, col, visited);
                if (component.size() > best.size()) {
                    best = component;
                }
            }
        }

        int ts = wallData.tileSize();
        List<Vector2D> positions = new ArrayList<>(best.size());
        for (int[] cell : best) {
            positions.add(new Vector2D((cell[1] + 0.5) * ts, (cell[0] + 0.5) * ts));
        }
        return positions;
    }

    private List<int[]> floodFillWalkable(int startRow, int startCol, boolean[][] visited) {
        List<int[]> component = new ArrayList<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        visited[startRow][startCol] = true;
        queue.add(new int[] { startRow, startCol });

        int[] dr = { -1, 1, 0, 0 };
        int[] dc = { 0, 0, -1, 1 };
        while (!queue.isEmpty()) {
            int[] cell = queue.removeFirst();
            component.add(cell);
            for (int i = 0; i < 4; i++) {
                int nr = cell[0] + dr[i];
                int nc = cell[1] + dc[i];
                if (nr < 0 || nc < 0 || nr >= wallData.worldRows() || nc >= wallData.worldCols()) {
                    continue;
                }
                if (visited[nr][nc] || !isSpawnTileWalkable(nr, nc)) {
                    continue;
                }
                visited[nr][nc] = true;
                queue.addLast(new int[] { nr, nc });
            }
        }
        return component;
    }

    private boolean isSpawnTileWalkable(int row, int col) {
        if (wallData == null || wallData.isEmpty()) {
            return false;
        }
        if (!wallData.spawnableGrid()[row][col]) {
            return false;
        }

        int ts = wallData.tileSize();
        double x = (col + 0.5) * ts;
        double y = (row + 0.5) * ts;
        double hitL = x - 3.0;
        double hitT = y - 1.0;
        double hitR = x + 3.0;
        double hitB = y + 9.0;

        int minRow = Math.max(0, row - 1);
        int maxRow = Math.min(wallData.worldRows() - 1, row + 1);
        int minCol = Math.max(0, col - 1);
        int maxCol = Math.min(wallData.worldCols() - 1, col + 1);
        for (int r = minRow; r <= maxRow; r++) {
            for (int c = minCol; c <= maxCol; c++) {
                if (wallData.spawnableGrid()[r][c]) {
                    continue;
                }
                double tileL = c * ts;
                double tileT = r * ts;
                double tileR = tileL + ts;
                double tileB = tileT + ts;
                if (hitL < tileR && hitR > tileL && hitT < tileB && hitB > tileT) {
                    return false;
                }
            }
        }
        return true;
    }

    private List<Vector2D> fallbackSpawnPositions(int count) {
        double cx = gameState.getArena().getWidth() / 2.0;
        double cy = gameState.getArena().getHeight() / 2.0;
        List<Vector2D> spawns = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            spawns.add(new Vector2D(
                    cx + GameConfig.SPAWN_RADIUS * Math.cos(angle),
                    cy + GameConfig.SPAWN_RADIUS * Math.sin(angle)));
        }
        return spawns;
    }

    public boolean isWarmupRound() {
        return gameState.getRoundNumber() <= GameConfig.WARMUP_ROUNDS;
    }
}
