## 6. Server Package

> `com.identitycrisis.server` — Headless authoritative game server. No JavaFX dependency. Runs from `main()`.

### 6.1 `server/ServerApp.java` — Composition Root

> **This is the ONLY place that calls `new` on collaborating objects.**
> All other classes receive their dependencies via constructor or setter injection.
> Do NOT add `new PhysicsEngine()` or similar inside game/net classes — wire them here.

```java
package com.identitycrisis.server;

// Composition Root — wiring order:
// 1. GameState
// 2. Game managers (injected with GameState): szm, cem, cm, em, rm
// 3. Physics utilities: pe, cd
// 4. GameContext(gameState, szm, cem, cm, em, rm)
// 5. GameServer(port)           — port only; router/lobby/loop set via setters
// 6. ClientMessageRouter(server)
// 7. LobbyManager(server)
// 8. server.setRouter(router); server.setLobbyManager(lobbyMgr);
//    lobbyMgr.setGameState(gameState); lobbyMgr.setSafeZoneManager(szm);
// 9. ServerGameLoop(server, ctx, pe, cd)
// 10. server.setGameLoop(loop)
// 11. Runtime.getRuntime().addShutdownHook(...)  → calls server.shutdown()
// 12. server.start()  — blocks on accept loop
public class ServerApp {
    private static final Logger LOG = new Logger("ServerApp");
    public static void main(String[] args) { /* see implementation */ }
    private static int parsePort(String[] args) { /* parse args[0], fallback to GameConfig.SERVER_PORT */ }
}
```

### 6.2 `server/net/GameServer.java`

> **Setter injection** is used for `router`, `lobbyManager`, and `gameLoop` because
> these three form a circular reference that cannot be resolved by constructor injection
> alone. The Composition Root (§6.1) calls all three setters before `start()`.
> `start()` throws `IllegalStateException` if setters were skipped.

```java
package com.identitycrisis.server.net;

import com.identitycrisis.server.game.*;
import com.identitycrisis.shared.util.Logger;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

// TCP server. Manages connections, lobby, game start.
public class GameServer {
    private static final Logger LOG = new Logger("GameServer");
    private final int port;
    private final List<ClientConnection> clients = new CopyOnWriteArrayList<>();
    private final AtomicInteger nextClientId = new AtomicInteger(1);
    private ServerSocket serverSocket;
    private volatile boolean gameInProgress = false; // set true in startGame(); rejects late connections

    // Setter-injected (circular ref trio — set by Composition Root before start())
    private ClientMessageRouter router;
    private LobbyManager        lobbyManager;
    private ServerGameLoop      gameLoop;

    public GameServer(int port) { this.port = port; }

    // Setter injection — called from ServerApp.main() before start()
    public void setRouter(ClientMessageRouter router) { this.router = router; }
    public void setLobbyManager(LobbyManager lm)     { this.lobbyManager = lm; }
    public void setGameLoop(ServerGameLoop loop)      { this.gameLoop = loop; }

    // Opens ServerSocket, logs health check, then blocks on accept loop.
    // Each accepted socket → new ClientConnection on a named daemon thread.
    // Rejects late connections after game starts (gameInProgress == true).
    // throws IllegalStateException if router/lobbyManager not injected.
    public void start() {
        if (router == null || lobbyManager == null)
            throw new IllegalStateException("router/lobbyManager not injected");
        try {
            serverSocket = new ServerSocket(port);
            LOG.info("Server listening on port " + port);
            LOG.info("[HEALTH OK] Identity Crisis server is up and waiting for players.");
            while (!serverSocket.isClosed()) {
                Socket socket = serverSocket.accept();
                int id = nextClientId.getAndIncrement();
                if (gameInProgress) {
                    LOG.warn("Rejecting late connection " + id + " — game already in progress.");
                    try { socket.close(); } catch (IOException ignored) { }
                    continue;
                }
                try {
                    ClientConnection conn = new ClientConnection(id, socket, router, this);
                    clients.add(conn);
                    Thread t = new Thread(conn, "client-conn-" + id);
                    t.setDaemon(true);
                    t.start();
                    LOG.info("Client " + id + " connected from " + socket.getInetAddress());
                } catch (IOException e) { LOG.error("Failed client " + id, e); }
            }
        } catch (IOException e) {
            if (serverSocket != null && serverSocket.isClosed()) LOG.info("Server socket closed.");
            else LOG.error("Server error", e);
        }
    }

    // Starts ServerGameLoop on named non-daemon thread "server-game-loop".
    public void startGame() {
        Thread t = new Thread(gameLoop, "server-game-loop");
        t.setDaemon(false);
        t.start();
    }

    // Removes client AND releases any carry state involving that client's player.
    public void removeClient(ClientConnection client) {
        clients.remove(client);
        if (gameLoop != null) {
            gameLoop.cleanupClient(client.getClientId());
        }
    }

    // All writes go through ClientConnection.send() (synchronized)
    public void broadcastToAll(byte[] data) { for (ClientConnection c : clients) c.send(data); }
    public void sendToClient(ClientConnection client, byte[] data) { client.send(data); }

    // Stops game loop, disconnects all clients, closes ServerSocket
    public void shutdown() {
        if (gameLoop != null) gameLoop.stop();
        for (ClientConnection c : clients) c.disconnect();
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
    }

    public List<ClientConnection> getClients()     { return clients; }
    public LobbyManager          getLobbyManager() { return lobbyManager; }
    public ServerGameLoop        getGameLoop()      { return gameLoop; }
    public int                   getPort()          { return port; }
}
```

### 6.2b `server/game/GameContext.java` — Manager bundle record

> Groups all six game managers into one value-object passed to `ServerGameLoop`.
> This avoids a 9-parameter constructor and keeps the type system explicit about
> what each class needs.

```java
package com.identitycrisis.server.game;

// Value-object used as single constructor arg to ServerGameLoop.
// All fields are set once by the Composition Root; never mutated after that.
public record GameContext(
        GameState          gameState,
        SafeZoneManager    safeZoneManager,
        ChaosEventManager  chaosEventManager,
        CarryManager       carryManager,
        EliminationManager eliminationManager,
        RoundManager       roundManager
) { }
```

### 6.3 `server/net/ClientConnection.java`

> **IMPORTANT:** All writes to this client's socket **must** go through
> `send(byte[])`, which is `synchronized`. Do NOT expose or call `getOutputStream()`
> directly — it will cause interleaved/corrupted frames under concurrent writes
> (game loop thread vs. lobby/main thread). `getOutputStream()` is deprecated and
> throws `UnsupportedOperationException` to enforce this contract.

```java
package com.identitycrisis.server.net;

import com.identitycrisis.shared.net.*;
import java.io.*;
import java.net.Socket;

// One connected client. Reader thread reads messages and routes them.
public class ClientConnection implements Runnable {
    private static final Logger LOG = new Logger("ClientConnection");
    private final int clientId;
    private final Socket socket;
    private final DataInputStream  in;
    private final DataOutputStream out;   // never exposed directly — use send(byte[])
    private final MessageDecoder   decoder;
    private final ClientMessageRouter router;
    private final GameServer server;      // for removeClient() on disconnect
    private volatile boolean connected;
    private String displayName;

    public ClientConnection(int clientId, Socket socket,
                            ClientMessageRouter router,
                            GameServer server) throws IOException { }

    @Override
    public void run() {
        // try { while (connected): type = decoder.readNextType(); router.route(this, type, decoder) }
        // catch (IOException) { ... }
        // finally { disconnect(); server.removeClient(this); }
    }

    // ── Synchronized write — ONLY way to send data to this client ────────────
    public synchronized void send(byte[] data) { /* out.write(data); out.flush(); */ }

    public int     getClientId()    { return clientId; }
    public String  getDisplayName() { return displayName; }
    public void    setDisplayName(String name) { }
    public boolean isConnected()    { return connected; }
    public void    disconnect()     { /* connected=false; socket.close(); */ }

    /**
     * @deprecated Use send(byte[]) instead. Throws UnsupportedOperationException.
     * Direct stream access bypasses the synchronization lock.
     */
    @Deprecated
    public DataOutputStream getOutputStream() { throw new UnsupportedOperationException(); }

    /**
     * @deprecated Calling encoder methods directly bypasses the send() lock.
     * Encode to a ByteArrayOutputStream and pass byte[] to send() instead:
     *   ByteArrayOutputStream baos = new ByteArrayOutputStream();
     *   MessageEncoder enc = new MessageEncoder(new DataOutputStream(baos));
     *   enc.encodeXxx(...); enc.flush();
     *   client.send(baos.toByteArray());
     */
    @Deprecated
    public MessageEncoder getEncoder() { throw new UnsupportedOperationException(); }
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

> **Full constructor injection.** Receives `GameServer`, `GameContext`, `PhysicsEngine`,
> and `CollisionDetector` — never creates them internally.
> `enqueueInput()` silently drops if the queue exceeds `GameConfig.MAX_QUEUED_INPUTS`
> to prevent memory DoS from misbehaving clients.
> Call `stop()` (not `Thread.interrupt()`) to halt the loop cleanly.

```java
package com.identitycrisis.server.game;

import com.identitycrisis.server.net.GameServer;
import com.identitycrisis.server.physics.*;
import com.identitycrisis.shared.model.GameConfig;
import java.util.concurrent.ConcurrentLinkedQueue;

// Authoritative game loop. Runs on its own thread at fixed tick rate.
// Each tick: processInputs → update(dt) → broadcastState → sleepUntilNextTick
public class ServerGameLoop implements Runnable {

    // ── Injected (never created here) ─────────────────────────────────────
    private final GameServer         server;
    private final GameContext        ctx;        // all six game managers
    private final PhysicsEngine      physics;
    private final CollisionDetector  collisions;

    // ── Owned internals ───────────────────────────────────────────────────
    private final ConcurrentLinkedQueue<QueuedInput> inputQueue;
    private volatile boolean running;

    /** All deps injected by Composition Root (ServerApp.main). Never call new here. */
    public ServerGameLoop(GameServer server, GameContext ctx,
                          PhysicsEngine physics, CollisionDetector collisions) { }

    @Override
    public void run() {
        running = true;
        // Fixed timestep — dt is always 1/TICK_RATE. Never use wall-clock delta here.
        final double dt = 1.0 / GameConfig.TICK_RATE;
        while (running) {
            long tickStart = System.nanoTime();
            processInputs();
            update(dt);
            broadcastState();
            sleepUntilNextTick(tickStart);
        }
    }

    private void processInputs() { }

    private void update(double dt) {
        // physics.step(ctx.gameState(), dt);
        // collisions.resolve(ctx.gameState());
        // ctx.roundManager().tick(dt);
        // ctx.safeZoneManager().updateOccupancy();
        // ctx.carryManager().tick(dt);
        // ctx.chaosEventManager().tick(dt);
    }

    private void broadcastState() {
        // For EACH client: build personalized GameStateSnapshot
        // (different safeZones list via SafeZoneManager.generateClientSafeZones,
        //  different controlledPlayerId after CONTROL_SWAP chaos event)
        // Encode and send via server.sendToClient(client, encoded)
    }

    /**
     * Thread-safe — called from ClientConnection reader threads.
     * Drops silently if queue.size() >= GameConfig.MAX_QUEUED_INPUTS.
     */
    public void enqueueInput(int clientId, boolean[] inputFlags) { }

    /** Signal the loop to exit cleanly. Do NOT use Thread.interrupt(). */
    public void stop() { running = false; }

    /**
     * Releases carry state for the given clientId on disconnect and prunes
     * controlMap so no living client remains swapped onto the disconnected
     * player's character.
     * Called by GameServer.removeClient() — do not call Thread.interrupt().
     */
    public void cleanupClient(int clientId) {
        ctx.carryManager().releaseCarry(clientId);
        Map<Integer, Integer> cm = ctx.gameState().getControlMap();
        cm.remove(clientId);
        cm.replaceAll((cid, controlled) -> controlled.equals(clientId) ? cid : controlled);
    }

    public record QueuedInput(int clientId, boolean[] flags) { }
}
```

### 6.6 `server/game/GameState.java`
```java
package com.identitycrisis.server.game;

import com.identitycrisis.shared.model.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;   // NOT HashMap — see AGENTS.md rule 19
import java.util.concurrent.CopyOnWriteArrayList;

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
    private Map<Integer, Integer> controlMap;  // clientId → controlledPlayerId (ConcurrentHashMap)
    private List<CarryState> activeCarries;
    // Deferred one-shot event fields — written by RoundManager, drained by broadcastState().
    private final List<Integer> pendingEliminationIds = new ArrayList<>();
    private int pendingGameOverWinnerId = -1;

    // Constructor initialises all collections and sets safe defaults so no field
    // is ever null when the game managers first access it.
    public GameState() {
        this.players          = new CopyOnWriteArrayList<>(); // cross-thread safe
        this.arena            = Arena.loadDefault();
        this.phase            = RoundPhase.LOBBY;
        this.roundNumber      = 0;
        this.roundTimer       = 0.0;
        this.activeChaosEvent = ChaosEventType.NONE;
        this.chaosEventTimer  = 0.0;
        this.controlMap       = new ConcurrentHashMap<>(); // cross-thread safe (rule 19)
        this.activeCarries    = new CopyOnWriteArrayList<>(); // cross-thread safe
    }

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
    // Pending-event accessors (game loop thread only)
    public List<Integer> getPendingEliminationIds() { }
    public void clearPendingEliminationIds() { }
    public int  getPendingGameOverWinnerId() { }
    public void setPendingGameOverWinnerId(int id) { }
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
    private void transitionTo(RoundPhase phase) { }
    private void startNewRound() { }
    public boolean isWarmupRound() { }
    // Note: shouldEndGame() is intentionally removed — use EliminationManager.isGameOver() directly.
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
    // Called by ServerGameLoop.cleanupClient() on disconnect to un-stick players.
    public void releaseCarry(int playerId) { }
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
    private CarryManager carryManager;

    // CarryManager injected so eliminatePlayer() can release carry state on the partner
    // before setting ELIMINATED — prevents partner from being permanently stuck.
    public EliminationManager(GameState gameState, CarryManager carryManager) { }
    public List<Integer> evaluateEliminations() { }
    // eliminatePlayer: releaseCarry → setState(ELIMINATED) → setInSafeZone(false)
    //   → cm.remove(playerId)  [remove their own entry]
    //   → cm.replaceAll(...)   [restore any client CONTROL_SWAP'd onto them to self-control]
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
    private GameState gameState;           // setter-injected
    private SafeZoneManager safeZoneManager; // setter-injected
    private boolean gameStarted = false;

    public LobbyManager(GameServer server) { }

    // Setter injection — called from ServerApp.main() after GameState is created.
    public void setGameState(GameState gs) { this.gameState = gs; }

    // Setter injection — so lobby can spawn the round-1 safe zone.
    public void setSafeZoneManager(SafeZoneManager szm) { this.safeZoneManager = szm; }

    // synchronized — guards readyClientIds read/write across concurrent reader threads
    public synchronized void handleJoin(ClientConnection client, String displayName) {
        // null/empty name falls back to "Player<id>"
    }
    // synchronized — prevents double startGame() if two C_READY messages race
    public synchronized void handleReady(ClientConnection client) { }
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
    private void resolveWallCollision(Player p, Arena arena) { }
    private void resolvePlayerCollision(Player a, Player b) { }
}
```

---

### 6.15 `server/EmbeddedServer.java` — In-Process Host Server

> Runs a full game server **inside** the client JVM on a background daemon thread.
> Used by the host player's "Create Room" flow — one client acts as both host
> and player without needing a separate server process.
>
> **Wiring:** mirrors `ServerApp.main()` exactly (same composition-root order).
> The only difference is that `GameServer.start()` runs on a named daemon thread
> so the JavaFX application thread is never blocked.
>
> **Lifecycle:** call `start(port)` once, call `stop()` to shut down. Do NOT
> call `start()` more than once per instance.

```java
package com.identitycrisis.server;

// Runs the full server inside the client process for the "Create Room" flow.
public class EmbeddedServer {

    public void start(int port) {
        // Wire all collaborators identically to ServerApp.main().
        // Run server.start() on a daemon thread named "embedded-server-accept".
    }

    public void stop() { /* server.shutdown() */ }

    public int  getPort()     { /* port this server is bound to, or -1 */ }
    public boolean isRunning() { /* true while server != null */ }
}
```

**Create Room usage pattern (in `CreateJoinScene`):**
```java
int port = NetworkUtils.findFreePort();
EmbeddedServer embedded = new EmbeddedServer();
embedded.start(port);

String code = RoomCodec.encode(NetworkUtils.getLanIp(), port);
// show code to user ...

gameClient.connect("localhost", port);
gameClient.sendJoinRequest(displayName);
sceneManager.showLobby();
```

**Join Room usage pattern:**
```java
RoomCodec.HostPort hp = RoomCodec.decode(enteredCode);
gameClient.connect(hp.ip(), hp.port());
gameClient.sendJoinRequest(displayName);
sceneManager.showLobby();
```