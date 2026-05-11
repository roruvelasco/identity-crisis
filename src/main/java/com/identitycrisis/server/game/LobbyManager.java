package com.identitycrisis.server.game;

import com.identitycrisis.server.net.ClientConnection;
import com.identitycrisis.server.net.GameServer;
import com.identitycrisis.server.physics.TmxWallsParser.WallCollisionData;
import com.identitycrisis.shared.model.GameConfig;
import com.identitycrisis.shared.model.Player;
import com.identitycrisis.shared.model.RoundPhase;
import com.identitycrisis.shared.net.MessageEncoder;
import com.identitycrisis.shared.util.Vector2D;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/* Pre-game lobby. Accepts players, tracks readiness, signals start. */
public class LobbyManager {

    private final GameServer server;
    private final Set<Integer> readyClientIds = new HashSet<>();
    private final Random spawnRng = new Random();
    private GameState gameState;
    private SafeZoneManager safeZoneManager;
    private WallCollisionData wallData;
    private boolean gameStarted = false;

    public LobbyManager(GameServer server) {
        this.server = server;
    }

    // Setter injection — called from ServerApp.main() after GameState is created.
    public void setGameState(GameState gs) {
        this.gameState = gs;
    }

    // Setter injection — called from ServerApp.main() so lobby can spawn round safe
    // zone.
    public void setSafeZoneManager(SafeZoneManager szm) {
        this.safeZoneManager = szm;
    }

    public void setWallCollisionData(WallCollisionData wallData) {
        this.wallData = wallData;
    }

    public synchronized void handleJoin(ClientConnection client, String displayName) {
        if (displayName == null || displayName.isBlank()) {
            displayName = "Player" + client.getClientId();
        }
        client.setDisplayName(displayName);
        broadcastLobbyState();
    }

    public synchronized void handleReady(ClientConnection client) {
        if (gameStarted)
            return;
        readyClientIds.add(client.getClientId());
        broadcastLobbyState();
        if (canStartGame()) {
            gameStarted = true;
            List<ClientConnection> clients = server.getClients();
            int n = clients.size();
            List<Vector2D> spawns = generateSpawnPositions(n);
            for (int i = 0; i < n; i++) {
                ClientConnection c = clients.get(i);
                Player p = new Player(c.getClientId(), c.getDisplayName());
                p.setPosition(spawns.get(i));
                gameState.getPlayers().add(p);
                gameState.getControlMap().put(c.getClientId(), c.getClientId());
            }
            // Round 1 is a warm-up round → unlimited capacity, one zone per player.
            // The N-zones-for-N-players formula matches RoundManager.startNewRound's
            // warm-up branch so subsequent rounds use the identical algorithm.
            safeZoneManager.spawnRoundZones(n);
            gameState.setPhase(RoundPhase.COUNTDOWN);
            gameState.setRoundNumber(1);
            gameState.setRoundTimer(GameConfig.COUNTDOWN_SECONDS);
            server.startGame();
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
        if (wallData.solidGrid()[row][col]) {
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
                if (!wallData.solidGrid()[r][c]) {
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

    public boolean canStartGame() {
        List<ClientConnection> clients = server.getClients();
        return clients.size() >= GameConfig.MIN_PLAYERS
                && readyClientIds.containsAll(
                        clients.stream().map(ClientConnection::getClientId).toList());
    }

    public void broadcastLobbyState() {
        List<ClientConnection> clients = server.getClients();
        int count = clients.size();
        String[] names = new String[count];
        boolean[] ready = new boolean[count];
        for (int i = 0; i < count; i++) {
            ClientConnection c = clients.get(i);
            names[i] = c.getDisplayName() != null
                    ? c.getDisplayName()
                    : "Player " + c.getClientId();
            ready[i] = readyClientIds.contains(c.getClientId());
        }
        for (int selfIndex = 0; selfIndex < count; selfIndex++) {
            ClientConnection receiver = clients.get(selfIndex);
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                MessageEncoder enc = new MessageEncoder(new DataOutputStream(baos));
                enc.encodeLobbyState(count, GameConfig.MIN_PLAYERS, selfIndex, names, ready);
                enc.flush();
                server.sendToClient(receiver, baos.toByteArray());
            } catch (IOException e) {
                // log and continue
            }
        }
    }
}
