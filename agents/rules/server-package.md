## 6. Server Package

> `com.identitycrisis.server` — Headless authoritative game server. No JavaFX dependency. Runs from `main()`.

### 6.1 `server/ServerApp.java`
```java
package com.identitycrisis.server;

// Entry point. Usage: java com.identitycrisis.server.ServerApp [port]
public class ServerApp {
    public static void main(String[] args) {
        // 1. Parse optional port (default: GameConfig.SERVER_PORT)
        // 2. Create GameServer
        // 3. Start listening (blocking on main thread or spawn listener)
        // 4. When lobby fills + all ready → create and start ServerGameLoop
    }
}
```

### 6.2 `server/net/GameServer.java`
```java
package com.identitycrisis.server.net;

import com.identitycrisis.server.game.*;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

// TCP server. Manages connections, lobby, game start.
public class GameServer {
    private ServerSocket serverSocket;
    private final List<ClientConnection> clients = new CopyOnWriteArrayList<>();
    private LobbyManager lobbyManager;
    private ServerGameLoop gameLoop;
    private ClientMessageRouter router;

    public GameServer(int port) { }

    // Accept connections. Blocks or runs on own thread.
    public void start() { }

    // Called when lobby signals all ready.
    public void startGame() { }

    // Remove disconnected client.
    public void removeClient(ClientConnection client) { }

    // Broadcast raw bytes to ALL clients.
    public void broadcastToAll(byte[] data) { }

    // Send to specific client.
    public void sendToClient(ClientConnection client, byte[] data) { }

    public List<ClientConnection> getClients() { }
    public LobbyManager getLobbyManager() { }
    public ServerGameLoop getGameLoop() { }
    public void shutdown() { }
}
```

### 6.3 `server/net/ClientConnection.java`
```java
package com.identitycrisis.server.net;

import com.identitycrisis.shared.net.*;
import java.io.*;
import java.net.Socket;

// One connected client. Reader thread reads messages and routes them.
public class ClientConnection implements Runnable {
    private final int clientId;
    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final MessageEncoder encoder;
    private final MessageDecoder decoder;
    private final ClientMessageRouter router;
    private volatile boolean connected;
    private String displayName;

    public ClientConnection(int clientId, Socket socket,
                            ClientMessageRouter router) { }

    @Override
    public void run() {
        // while (connected): type = decoder.readNextType();
        //   router.route(this, type, decoder)
    }

    public int getClientId() { }
    public String getDisplayName() { }
    public void setDisplayName(String name) { }
    public MessageEncoder getEncoder() { }
    public DataOutputStream getOutputStream() { }
    public boolean isConnected() { }
    public void disconnect() { }
}
```

### 6.4 `server/net/ClientMessageRouter.java`
```java
package com.identitycrisis.server.net;

import com.identitycrisis.shared.net.MessageDecoder;
import com.identitycrisis.shared.net.MessageType;

// Routes decoded client messages to server-side handlers.
// Runs on ClientConnection's reader thread — must be thread-safe.
public class ClientMessageRouter {
    private GameServer server;

    public ClientMessageRouter(GameServer server) { }

    public void route(ClientConnection sender, MessageType type,
                      MessageDecoder decoder) {
        // switch (type):
        //   C_JOIN_REQUEST → lobbyManager.handleJoin(sender, ...)
        //   C_READY        → lobbyManager.handleReady(sender)
        //   C_PLAYER_INPUT → enqueue into ServerGameLoop
        //   C_CHAT_SEND    → broadcast chat to all
    }
}
```

### 6.5 `server/game/ServerGameLoop.java`
```java
package com.identitycrisis.server.game;

import com.identitycrisis.server.net.GameServer;
import com.identitycrisis.server.physics.*;
import java.util.concurrent.ConcurrentLinkedQueue;

// Authoritative game loop. Runs on its own thread at fixed tick rate.
// Each tick: collect inputs → update state → broadcast per-client snapshots.
public class ServerGameLoop implements Runnable {
    private final GameServer server;
    private final GameState gameState;
    private final PhysicsEngine physics;
    private final CollisionDetector collisions;
    private final RoundManager roundManager;
    private final SafeZoneManager safeZoneManager;
    private final ChaosEventManager chaosEventManager;
    private final CarryManager carryManager;
    private final EliminationManager eliminationManager;
    private final ConcurrentLinkedQueue<QueuedInput> inputQueue;
    private volatile boolean running;

    public ServerGameLoop(GameServer server, GameState initialState) { }

    @Override
    public void run() {
        // Fixed timestep loop:
        // while (running):
        //   processInputs()
        //   update(dt)
        //   broadcastState()
        //   sleepUntilNextTick()
    }

    private void processInputs() { }
    private void update(double dt) { }
    private void broadcastState() {
        // For EACH client: build personalized GameStateSnapshot
        // (different safeZones list, different controlledPlayerId)
        // Encode and send via GameServer.sendToClient()
    }

    // Thread-safe: called from network threads.
    public void enqueueInput(int clientId, boolean[] inputFlags) { }
    public void stop() { }

    // Inner class for queued input
    public record QueuedInput(int clientId, boolean[] flags) { }
}
```

### 6.6 `server/game/GameState.java`
```java
package com.identitycrisis.server.game;

import com.identitycrisis.shared.model.*;
import java.util.List;
import java.util.Map;

// Single authoritative game state. Modified only by ServerGameLoop.
public class GameState {
    private List<Player> players;
    private Arena arena;
    private SafeZone trueSafeZone;
    private int roundNumber;
    private RoundPhase phase;
    private double roundTimer;
    private ChaosEventType activeChaosEvent;
    private double chaosEventTimer;
    private Map<Integer, Integer> controlMap;  // clientId → controlledPlayerId
    private List<CarryState> activeCarries;

    public List<Player> getPlayers() { }
    public List<Player> getAlivePlayers() { }
    public Player getPlayerById(int id) { }
    public Arena getArena() { }
    public SafeZone getTrueSafeZone() { }
    public void setTrueSafeZone(SafeZone zone) { }
    public int getRoundNumber() { }
    public void setRoundNumber(int n) { }
    public RoundPhase getPhase() { }
    public void setPhase(RoundPhase phase) { }
    public double getRoundTimer() { }
    public void setRoundTimer(double t) { }
    public ChaosEventType getActiveChaosEvent() { }
    public void setActiveChaosEvent(ChaosEventType type) { }
    public double getChaosEventTimer() { }
    public void setChaosEventTimer(double t) { }
    public Map<Integer, Integer> getControlMap() { }
    public List<CarryState> getActiveCarries() { }
    public int getAliveCount() { }
}
```

### 6.7 `server/game/RoundManager.java`
```java
package com.identitycrisis.server.game;

// Drives the round state machine.
// LOBBY → COUNTDOWN → ACTIVE → ROUND_END → ELIMINATION → COUNTDOWN → ...
// When 1 player left → GAME_OVER
public class RoundManager {
    private GameState gameState;
    private SafeZoneManager safeZoneManager;
    private ChaosEventManager chaosEventManager;
    private EliminationManager eliminationManager;

    public RoundManager(GameState gs, SafeZoneManager szm,
                        ChaosEventManager cem, EliminationManager em) { }

    public void tick(double dt) { }
    private void transitionTo(/* RoundPhase */) { }
    private void startNewRound() { }
    public boolean isWarmupRound() { }
    private boolean shouldEndGame() { }
}
```

### 6.8 `server/game/SafeZoneManager.java`
```java
package com.identitycrisis.server.game;

import com.identitycrisis.shared.model.SafeZone;
import com.identitycrisis.shared.util.Vector2D;
import java.util.List;

// Safe zone spawning, occupancy, decoy generation.
// True position is SERVER-ONLY. Clients get true + decoys mixed.
public class SafeZoneManager {
    private GameState gameState;

    public SafeZoneManager(GameState gameState) { }
    public void spawnSafeZone() { }
    private Vector2D randomSafePosition() { }
    public void updateOccupancy() { }

    // Per-client zone list. During FAKE_SAFE_ZONES: 1 true + N decoys shuffled.
    public List<SafeZone> generateClientSafeZones(int clientId,
                                                   boolean fakeChaosActive) { }
    public int getOccupantCount() { }
    public List<Integer> getOccupantPlayerIds() { }
}
```

### 6.9 `server/game/ChaosEventManager.java`
```java
package com.identitycrisis.server.game;

import com.identitycrisis.shared.model.ChaosEventType;

// Triggers and manages chaos events during ACTIVE phase.
// REVERSED_CONTROLS: flag sent to client, client inverts input.
// CONTROL_SWAP: remap controlMap so each client controls different player.
// FAKE_SAFE_ZONES: SafeZoneManager generates decoys per client.
public class ChaosEventManager {
    private GameState gameState;
    private double scheduledTriggerTime;
    private double elapsedInRound;

    public ChaosEventManager(GameState gameState) { }
    public void resetForNewRound() { }
    public void tick(double dt) { }
    private ChaosEventType pickRandomEvent() { }
    private void applyControlSwap() { }
    private void revertControlSwap() { }
    public boolean isFakeSafeZonesActive() { }
}
```

### 6.10 `server/game/CarryManager.java`
```java
package com.identitycrisis.server.game;

// Carry/throw mechanics, server-authoritative.
// - Carry: within CARRY_RANGE, neither in existing carry.
// - Carrier speed reduced, carried position locked to carrier + offset.
// - Carrier CANNOT be marked safe while carrying.
// - Throw: release with velocity in facing direction, brief stun on carried.
public class CarryManager {
    private GameState gameState;

    public CarryManager(GameState gameState) { }
    public boolean tryCarry(int carrierPlayerId) { }
    public void throwCarried(int carrierPlayerId) { }
    public void tick(double dt) { }
    private int findNearestCarryTarget(int carrierPlayerId) { }
}
```

### 6.11 `server/game/EliminationManager.java`
```java
package com.identitycrisis.server.game;

import java.util.List;

// Evaluates eliminations at round end.
// Warmup (1–2): all outside safe zone eliminated (usually nobody).
// Elimination (3+): zone fits n-1. One eliminated per round guaranteed.
//   Tiebreak: farthest from zone center among those outside.
public class EliminationManager {
    private GameState gameState;

    public EliminationManager(GameState gameState) { }
    public List<Integer> evaluateEliminations() { }
    private void eliminatePlayer(int playerId) { }
    public boolean isGameOver() { }
    public int getWinnerId() { }
}
```

### 6.12 `server/game/LobbyManager.java`
```java
package com.identitycrisis.server.game;

import com.identitycrisis.server.net.ClientConnection;
import com.identitycrisis.server.net.GameServer;

// Pre-game lobby. Accepts players, tracks readiness, signals start.
public class LobbyManager {
    private GameServer server;

    public LobbyManager(GameServer server) { }
    public void handleJoin(ClientConnection client, String displayName) { }
    public void handleReady(ClientConnection client) { }
    public boolean canStartGame() { }
    public void broadcastLobbyState() { }
}
```

### 6.13 `server/physics/PhysicsEngine.java`
```java
package com.identitycrisis.server.physics;

import com.identitycrisis.server.game.GameState;

// Movement: Euler integration. position += velocity * dt.
// Applies throw velocity decay and stun timers.
public class PhysicsEngine {
    public PhysicsEngine() { }
    public void step(GameState state, double dt) { }
    public void applyInput(GameState state, int playerId,
                           boolean up, boolean down, boolean left, boolean right,
                           boolean reversedControls) { }
}
```

### 6.14 `server/physics/CollisionDetector.java`
```java
package com.identitycrisis.server.physics;

import com.identitycrisis.server.game.GameState;

// Resolves: player vs walls (clamp), player vs obstacles (push out),
// player vs player (soft push, no overlap).
public class CollisionDetector {
    public CollisionDetector() { }
    public void resolve(GameState state) { }
    private void resolveWallCollision(/* player, arena */) { }
    private void resolvePlayerCollision(/* playerA, playerB */) { }
}
```