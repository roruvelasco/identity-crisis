# Backend Implementation Guide

> **For LLM agents.** This document contains every detail needed to implement the
> full server-side backend of Identity Crisis — from stub getters through a working
> multiplayer game loop. Follow the steps in order. Each step lists the exact file,
> method, logic, and tests required. **Do not skip steps or reorder them.**
>
> **Reference docs:** `agents/AGENTS.md` (master spec), `agents/rules/server-package.md`,
> `agents/rules/shared-package.md`, `agents/rules/protocol.md`, `agents/rules/game-logic.md`.

---

## Pre-Conditions

The skeleton already compiles (`./mvnw clean compile` exits 0). Every method listed
below exists as a stub (empty body or `throw new UnsupportedOperationException("stub")`).
Your job is to **replace the stub bodies with working implementations**.

**Critical rules (from `AGENTS.md` §2b):**
- Never call `new` on a collaborator inside another class — all wiring is in `ServerApp.main()`.
- All socket writes go through `ClientConnection.send(byte[])` (synchronized).
  Use the `ByteArrayOutputStream` encoding pattern (see Step 5 below).
- `getEncoder()` and `getOutputStream()` both throw — never call them.
- Server game loop uses fixed `dt = 1.0 / GameConfig.TICK_RATE` — never wall-clock delta.
- `Player` is always constructed with `new Player(id, name)`. Carry IDs default to `-1`.
- `GameState` is always constructed with `new GameState()` which initializes all collections.
- Run `./mvnw test` after each step to verify nothing is broken.

---

## Step 1 — Shared Foundation

### 1A. `Vector2D` math methods
**File:** `src/main/java/com/identitycrisis/shared/util/Vector2D.java`

Already a `record(double x, double y)`. Implement:

```java
public Vector2D add(Vector2D o)       { return new Vector2D(x + o.x, y + o.y); }
public Vector2D subtract(Vector2D o)  { return new Vector2D(x - o.x, y - o.y); }
public Vector2D multiply(double s)    { return new Vector2D(x * s, y * s); }
public double magnitude()             { return Math.sqrt(x * x + y * y); }
public double distanceTo(Vector2D o)  { return subtract(o).magnitude(); }
public Vector2D normalize() {
    double m = magnitude();
    return m == 0 ? zero() : new Vector2D(x / m, y / m);
}
```

**Test:** `src/test/java/.../shared/util/Vector2DTest.java` (already exists — run it).

### 1B. `GameTimer`
**File:** `src/main/java/com/identitycrisis/shared/util/GameTimer.java`

```java
public GameTimer(double durationSeconds) { this.remainingSeconds = durationSeconds; }
public void start()                       { running = true; }
public void stop()                        { running = false; }
public void reset(double durationSeconds) { this.remainingSeconds = durationSeconds; running = false; }
public void tick(double delta)            { if (running) remainingSeconds = Math.max(0, remainingSeconds - delta); }
public boolean isExpired()                { return remainingSeconds <= 0; }
public double getRemaining()              { return remainingSeconds; }
public boolean isRunning()                { return running; }
```

### 1C. `Arena.loadDefault()`
**File:** `src/main/java/com/identitycrisis/shared/model/Arena.java`

Return a placeholder arena with no obstacles. Obstacle support is added later when the
map design is finalized. This does **not** block any other feature.

```java
public double getWidth()  { return width; }
public double getHeight() { return height; }

public boolean isWall(double x, double y) {
    return x < 0 || y < 0 || x >= width || y >= height;
}

public static Arena loadDefault() {
    Arena a = new Arena();
    a.width  = GameConfig.ARENA_WIDTH;   // 1200.0
    a.height = GameConfig.ARENA_HEIGHT;  // 800.0
    return a;
}
```

### 1D. `Player` getters/setters
**File:** `src/main/java/com/identitycrisis/shared/model/Player.java`

Constructor and `equals`/`hashCode` are already implemented. Replace every stub
getter with `return field;` and every setter with `this.field = arg;`. Example:

```java
public int getPlayerId()                { return playerId; }
public void setPlayerId(int id)         { this.playerId = id; }
public String getDisplayName()          { return displayName; }
public void setDisplayName(String name) { this.displayName = name; }
public Vector2D getPosition()           { return position; }
public void setPosition(Vector2D pos)   { this.position = pos; }
public Vector2D getVelocity()           { return velocity; }
public void setVelocity(Vector2D vel)   { this.velocity = vel; }
public PlayerState getState()           { return state; }
public void setState(PlayerState s)     { this.state = s; }
public int getFacingDirection()         { return facingDirection; }
public void setFacingDirection(int d)   { this.facingDirection = d; }
public boolean isInSafeZone()           { return inSafeZone; }
public void setInSafeZone(boolean v)    { this.inSafeZone = v; }
public int getCarriedByPlayerId()       { return carriedByPlayerId; }
public void setCarriedByPlayerId(int i) { this.carriedByPlayerId = i; }
public int getCarryingPlayerId()        { return carryingPlayerId; }
public void setCarryingPlayerId(int i)  { this.carryingPlayerId = i; }
```

**Test:** `src/test/java/.../shared/model/PlayerTest.java` (already exists — run it).

### 1E. `GameState` getters/setters
**File:** `src/main/java/com/identitycrisis/server/game/GameState.java`

Constructor is already implemented. Replace stubs with simple field access, except
these two which have logic:

```java
public List<Player> getAlivePlayers() {
    return players.stream()
        .filter(p -> p.getState() == PlayerState.ALIVE
                  || p.getState() == PlayerState.CARRYING
                  || p.getState() == PlayerState.CARRIED)
        .toList();
}

public Player getPlayerById(int id) {
    return players.stream()
        .filter(p -> p.getPlayerId() == id)
        .findFirst()
        .orElse(null);
}

public int getAliveCount() { return getAlivePlayers().size(); }
```

All other getters → `return field;`, all setters → `this.field = value;`.

**Important — `controlMap` type:** Use `ConcurrentHashMap`, not `HashMap`. The map is
accessed by both the game loop thread and `ClientConnection` reader threads (via the
disconnect chain). See AGENTS.md rule 19.

**Extra fields for deferred event broadcasting:** `GameState` also holds two fields that
`RoundManager` writes and `ServerGameLoop.broadcastState()` reads to send one-shot
messages without coupling the round state machine to the network layer:

```java
// Accumulates elimination IDs for the current tick; drained by broadcastState().
private final List<Integer> pendingEliminationIds = new ArrayList<>();
private int pendingGameOverWinnerId = -1;

public List<Integer> getPendingEliminationIds() { return pendingEliminationIds; }
public void clearPendingEliminationIds()        { pendingEliminationIds.clear(); }
public int  getPendingGameOverWinnerId()         { return pendingGameOverWinnerId; }
public void setPendingGameOverWinnerId(int id)   { this.pendingGameOverWinnerId = id; }
```

`pendingEliminationIds` uses a plain `ArrayList` (not COW) because it is only ever
written and read by the single game loop thread.

### 1F. `MessageType.fromTag(byte)`
**File:** `src/main/java/com/identitycrisis/shared/net/MessageType.java`

```java
public static MessageType fromTag(byte tag) {
    for (MessageType t : values()) {
        if (t.tag == tag) return t;
    }
    throw new IllegalArgumentException("Unknown message tag: 0x" + String.format("%02X", tag));
}
```

**Verify:** `./mvnw clean compile && ./mvnw test`

---

## Step 2 — Network Protocol (MessageEncoder + MessageDecoder)

This is the most critical step. The wire format is:

```
┌───────────┬─────────────────┬────────────────────────────┐
│ Type Tag  │ Payload Length   │ Payload                    │
│ 1 byte    │ 2 bytes (uint16)│ variable (0–65535 bytes)   │
└───────────┴─────────────────┴────────────────────────────┘
```

### 2A. `MessageEncoder`
**File:** `src/main/java/com/identitycrisis/shared/net/MessageEncoder.java`

**Key internal helper — `writeHeader`:**

```java
private void writeHeader(MessageType type, int payloadLength) throws IOException {
    out.writeByte(type.getTag());
    out.writeShort(payloadLength);  // unsigned 16-bit big-endian
}

public void flush() throws IOException { out.flush(); }
```

**Encoding pattern for every method:** First compute payload length, write header, write
payload. To compute length, use a temporary `ByteArrayOutputStream`:

```java
public void encodeJoinRequest(String displayName) throws IOException {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    DataOutputStream tmp = new DataOutputStream(buf);
    tmp.writeUTF(displayName);
    tmp.flush();
    byte[] payload = buf.toByteArray();
    writeHeader(MessageType.C_JOIN_REQUEST, payload.length);
    out.write(payload);
}

public void encodeReady() throws IOException {
    writeHeader(MessageType.C_READY, 0);
}

public void encodePlayerInput(boolean up, boolean down, boolean left,
                              boolean right, boolean carry,
                              boolean throwAction) throws IOException {
    int bits = 0;
    if (up)    bits |= 0x01;
    if (down)  bits |= 0x02;
    if (left)  bits |= 0x04;
    if (right) bits |= 0x08;
    if (carry) bits |= 0x10;
    if (throwAction) bits |= 0x20;
    writeHeader(MessageType.C_PLAYER_INPUT, 1);
    out.writeByte(bits);
}

public void encodeChatSend(String text) throws IOException {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    DataOutputStream tmp = new DataOutputStream(buf);
    tmp.writeUTF(text);
    tmp.flush();
    byte[] payload = buf.toByteArray();
    writeHeader(MessageType.C_CHAT_SEND, payload.length);
    out.write(payload);
}
```

**Server → Client encoders** follow the same pattern. Key payload layouts:

**`encodeLobbyState`:**
```
int connectedCount, int requiredCount, then N × (writeUTF(name) + writeByte(readyFlag ? 1 : 0))
```

**`encodeGameState`:**
```
int roundNumber
double timerRemaining
byte phaseOrdinal
byte chaosOrdinal
double chaosDuration
int controlledPlayerId
int playerCount
  N × PlayerBlock:
    int playerId, writeUTF(name), double x, double y, double vx, double vy,
    byte stateOrdinal, int facingDirection, byte inSafeZone (0/1),
    int carriedByPlayerId, int carryingPlayerId
int zoneCount
  M × ZoneBlock:
    double x, double y, double radius
```

**`encodeRoundState`:** `int roundNumber, byte phaseOrdinal, double timerRemaining`

**`encodeSafeZoneUpdate`:**
```
int zoneCount (= xs.length)
  N × (double x, double y, double radius)
```

**`encodePlayerEliminated`:** `int playerId, writeUTF(playerName)`

**`encodeChaosEvent`:** `byte chaosOrdinal, double duration`

**`encodeControlSwap`:** `int newControlledPlayerId`

**`encodeGameOver`:** `int winnerPlayerId, writeUTF(winnerName)`

**`encodeChatBroadcast`:** `writeUTF(senderName), writeUTF(text)`

**Important:** Every server→client method uses the same pattern: buffer payload via
`ByteArrayOutputStream`, compute length, write header + payload.

Add `import java.io.ByteArrayOutputStream;` at the top of `MessageEncoder.java`.

### 2B. `MessageDecoder`
**File:** `src/main/java/com/identitycrisis/shared/net/MessageDecoder.java`

**`readNextType`** — reads header, buffers payload, returns type:

```java
public MessageType readNextType() throws IOException {
    byte tag = in.readByte();
    int length = in.readUnsignedShort();
    payloadBuffer = new byte[length];
    in.readFully(payloadBuffer);
    return MessageType.fromTag(tag);
}
```

After `readNextType()` returns, `payloadBuffer` holds the complete payload. Each
`decode*()` method wraps `payloadBuffer` in a `DataInputStream` and reads it:

```java
private DataInputStream payloadStream() {
    return new DataInputStream(new java.io.ByteArrayInputStream(payloadBuffer));
}

public String decodeJoinRequest() {
    try { return payloadStream().readUTF(); }
    catch (IOException e) { throw new RuntimeException(e); }
}

public void decodeReady() { /* empty payload — nothing to read */ }

public boolean[] decodePlayerInput() {
    try {
        int bits = payloadStream().readByte() & 0xFF;
        return new boolean[] {
            (bits & 0x01) != 0, // up
            (bits & 0x02) != 0, // down
            (bits & 0x04) != 0, // left
            (bits & 0x08) != 0, // right
            (bits & 0x10) != 0, // carry
            (bits & 0x20) != 0  // throw
        };
    } catch (IOException e) { throw new RuntimeException(e); }
}

public String decodeChatSend() {
    try { return payloadStream().readUTF(); }
    catch (IOException e) { throw new RuntimeException(e); }
}
```

Server→Client decoders follow the same pattern — wrap `payloadBuffer`, read fields
in the **exact same order** as the encoder wrote them, return a record.

**`decodeGameState`** (the largest one):
```java
public GameStateData decodeGameState() {
    try {
        DataInputStream p = payloadStream();
        int roundNumber = p.readInt();
        double timer = p.readDouble();
        byte phase = p.readByte();
        byte chaos = p.readByte();
        double chaosDur = p.readDouble();
        int controlledId = p.readInt();
        int playerCount = p.readInt();
        PlayerNetData[] players = new PlayerNetData[playerCount];
        for (int i = 0; i < playerCount; i++) {
            players[i] = new PlayerNetData(
                p.readInt(), p.readUTF(),
                p.readDouble(), p.readDouble(),
                p.readDouble(), p.readDouble(),
                p.readByte(), p.readInt(),
                p.readByte() != 0,
                p.readInt(), p.readInt()
            );
        }
        int zoneCount = p.readInt();
        SafeZoneNetData[] zones = new SafeZoneNetData[zoneCount];
        for (int i = 0; i < zoneCount; i++) {
            zones[i] = new SafeZoneNetData(p.readDouble(), p.readDouble(), p.readDouble());
        }
        return new GameStateData(roundNumber, timer, phase, chaos, chaosDur,
                                 controlledId, players, zones);
    } catch (IOException e) { throw new RuntimeException(e); }
}
```

Implement every other `decode*()` method similarly, reading fields in the same order
as the corresponding `encode*()` writes them.

**Test:** Create `src/test/java/.../shared/net/MessageCodecTest.java` with round-trip
tests: encode a message to `ByteArrayOutputStream`, wrap in `ByteArrayInputStream`,
decode, assert fields match. Test at minimum: `JoinRequest`, `PlayerInput`, `GameState`,
`LobbyState`, `ChatBroadcast`.

**Verify:** `./mvnw test`

---

## Step 3 — Server Networking (Player Connection)

### 3A. `ClientMessageRouter.route()`
**File:** `src/main/java/com/identitycrisis/server/net/ClientMessageRouter.java`

This runs on the `ClientConnection` reader thread. It must be thread-safe.
The decoder has already buffered the payload — just call the appropriate `decode*()`
and delegate:

```java
public void route(ClientConnection sender, MessageType type,
                  MessageDecoder decoder) {
    switch (type) {
        case C_JOIN_REQUEST -> {
            String name = decoder.decodeJoinRequest();
            server.getLobbyManager().handleJoin(sender, name);
        }
        case C_READY -> {
            decoder.decodeReady();
            server.getLobbyManager().handleReady(sender);
        }
        case C_PLAYER_INPUT -> {
            boolean[] flags = decoder.decodePlayerInput();
            ServerGameLoop loop = server.getGameLoop();
            if (loop != null) {
                loop.enqueueInput(sender.getClientId(), flags);
            }
        }
        case C_CHAT_SEND -> {
            String text = decoder.decodeChatSend();
            // Encode chat broadcast and send to all clients
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                MessageEncoder enc = new MessageEncoder(new DataOutputStream(baos));
                enc.encodeChatBroadcast(sender.getDisplayName(), text);
                enc.flush();
                server.broadcastToAll(baos.toByteArray());
            } catch (IOException e) {
                // Log and continue
            }
        }
        default -> { /* unknown message type — ignore */ }
    }
}
```

Add necessary imports: `java.io.ByteArrayOutputStream`, `java.io.DataOutputStream`,
`java.io.IOException`, `com.identitycrisis.server.game.ServerGameLoop`,
`com.identitycrisis.shared.net.MessageEncoder`.

### 3B. `LobbyManager`
**File:** `src/main/java/com/identitycrisis/server/game/LobbyManager.java`

Tracks connected players and their ready state. When all are ready and
`>= MIN_PLAYERS`, starts the game.

```java
import java.io.*;
import java.util.*;
import com.identitycrisis.shared.model.GameConfig;
import com.identitycrisis.shared.net.MessageEncoder;

public class LobbyManager {
    private final GameServer server;
    private final Set<Integer> readyClientIds = new HashSet<>();

    public LobbyManager(GameServer server) { this.server = server; }

    public void handleJoin(ClientConnection client, String displayName) {
        client.setDisplayName(displayName);
        broadcastLobbyState();
    }

    public void handleReady(ClientConnection client) {
        readyClientIds.add(client.getClientId());
        broadcastLobbyState();
        if (canStartGame()) {
            server.startGame();
        }
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
            names[i] = clients.get(i).getDisplayName() != null
                     ? clients.get(i).getDisplayName() : "Player " + clients.get(i).getClientId();
            ready[i] = readyClientIds.contains(clients.get(i).getClientId());
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MessageEncoder enc = new MessageEncoder(new DataOutputStream(baos));
            enc.encodeLobbyState(count, GameConfig.MIN_PLAYERS, names, ready);
            enc.flush();
            server.broadcastToAll(baos.toByteArray());
        } catch (IOException e) { /* log */ }
    }
}
```

**At this point, player connection is fully functional:**
1. Client connects via TCP to `SERVER_PORT` (5137).
2. `GameServer.start()` accepts the socket, creates `ClientConnection`, starts reader thread.
3. Client sends `C_JOIN_REQUEST` → `ClientMessageRouter.route()` → `LobbyManager.handleJoin()`.
4. Client sends `C_READY` → `LobbyManager.handleReady()` → broadcasts lobby state.
5. When ≥4 players are connected and all ready → `server.startGame()` launches the game loop thread.

**Verify:** Run the server with `./mvnw compile exec:java@server`. Connect with a
raw TCP test (e.g., a tiny Java main that opens a socket and sends a `C_JOIN_REQUEST`).
The server should log the connection and not crash.

---

## Step 4 — Physics Engine

### 4A. `PhysicsEngine`
**File:** `src/main/java/com/identitycrisis/server/physics/PhysicsEngine.java`

```java
import com.identitycrisis.shared.model.*;
import com.identitycrisis.shared.util.Vector2D;

public class PhysicsEngine {
    public PhysicsEngine() { }

    /**
     * Euler integration: position += velocity * dt for all alive players.
     * Also decays throw velocity and ticks stun timers.
     */
    public void step(GameState state, double dt) {
        for (Player p : state.getAlivePlayers()) {
            if (p.getState() == PlayerState.CARRIED) continue; // position locked by CarryManager
            Vector2D pos = p.getPosition().add(p.getVelocity().multiply(dt));
            p.setPosition(pos);
            // Decay velocity for thrown players (friction)
            if (p.getVelocity().magnitude() > 0.1) {
                p.setVelocity(p.getVelocity().multiply(0.95)); // simple drag
            } else {
                p.setVelocity(Vector2D.zero());
            }
        }
    }

    /**
     * Applies a player's input to velocity. Called once per queued input.
     * If reversedControls is true, up↔down and left↔right are swapped
     * (though for REVERSED_CONTROLS the client inverts input before sending,
     * the server-side flag is used for CONTROL_SWAP scenarios where the
     * server needs to know the real mapping).
     */
    public void applyInput(GameState state, int playerId,
                           boolean up, boolean down, boolean left, boolean right,
                           boolean reversedControls) {
        Player p = state.getPlayerById(playerId);
        if (p == null || p.getState() == PlayerState.ELIMINATED
                      || p.getState() == PlayerState.SPECTATING
                      || p.getState() == PlayerState.CARRIED) return;

        double speed = GameConfig.PLAYER_SPEED;
        // Carrier moves slower
        if (p.getState() == PlayerState.CARRYING) speed *= 0.6;

        double vx = 0, vy = 0;
        if (up)    vy -= 1;
        if (down)  vy += 1;
        if (left)  vx -= 1;
        if (right) vx += 1;

        // Normalize diagonal movement
        double mag = Math.sqrt(vx * vx + vy * vy);
        if (mag > 0) {
            vx = (vx / mag) * speed;
            vy = (vy / mag) * speed;
        }

        p.setVelocity(new Vector2D(vx, vy));

        // Update facing direction based on last movement
        if (vy < 0) p.setFacingDirection(0);      // up
        else if (vx > 0) p.setFacingDirection(1);  // right
        else if (vy > 0) p.setFacingDirection(2);  // down
        else if (vx < 0) p.setFacingDirection(3);  // left
    }
}
```

### 4B. `CollisionDetector`
**File:** `src/main/java/com/identitycrisis/server/physics/CollisionDetector.java`

```java
import com.identitycrisis.shared.model.*;
import com.identitycrisis.shared.util.Vector2D;
import java.util.List;

public class CollisionDetector {
    public CollisionDetector() { }

    public void resolve(GameState state) {
        List<Player> alive = state.getAlivePlayers();
        Arena arena = state.getArena();
        // Wall collisions
        for (Player p : alive) {
            resolveWallCollision(p, arena);
        }
        // Player-player collisions (soft push)
        for (int i = 0; i < alive.size(); i++) {
            for (int j = i + 1; j < alive.size(); j++) {
                resolvePlayerCollision(alive.get(i), alive.get(j));
            }
        }
    }

    private void resolveWallCollision(Player p, Arena arena) {
        double r = GameConfig.PLAYER_RADIUS;
        double x = Math.max(r, Math.min(p.getPosition().x(), arena.getWidth() - r));
        double y = Math.max(r, Math.min(p.getPosition().y(), arena.getHeight() - r));
        p.setPosition(new Vector2D(x, y));
    }

    private void resolvePlayerCollision(Player a, Player b) {
        double minDist = GameConfig.PLAYER_RADIUS * 2;
        double dist = a.getPosition().distanceTo(b.getPosition());
        if (dist < minDist && dist > 0.001) {
            Vector2D dir = b.getPosition().subtract(a.getPosition()).normalize();
            double overlap = (minDist - dist) / 2.0;
            a.setPosition(a.getPosition().subtract(dir.multiply(overlap)));
            b.setPosition(b.getPosition().add(dir.multiply(overlap)));
        }
    }
}
```

---

## Step 5 — Game Managers

Implement in this order because later managers depend on earlier ones.

### 5A. `SafeZoneManager`
**File:** `src/main/java/com/identitycrisis/server/game/SafeZoneManager.java`

Key behaviors:
- `spawnSafeZone()`: random position within arena margins, not overlapping obstacles.
- `updateOccupancy()`: mark players in/out of safe zone. **Carrying/carried players CANNOT be safe.**
- `generateClientSafeZones(clientId, fakeChaosActive)`: if fake chaos active, return
  1 true + `FAKE_SAFE_ZONE_COUNT` decoys shuffled per-client.

```java
import com.identitycrisis.shared.model.*;
import com.identitycrisis.shared.util.Vector2D;
import java.util.*;

public class SafeZoneManager {
    private final GameState gameState;
    private final Random rng = new Random();

    public SafeZoneManager(GameState gameState) { this.gameState = gameState; }

    public void spawnSafeZone() {
        Vector2D pos = randomSafePosition();
        gameState.setTrueSafeZone(new SafeZone(pos, GameConfig.SAFE_ZONE_RADIUS));
    }

    private Vector2D randomSafePosition() {
        double margin = GameConfig.SAFE_ZONE_MIN_MARGIN;
        double x = margin + rng.nextDouble() * (gameState.getArena().getWidth() - 2 * margin);
        double y = margin + rng.nextDouble() * (gameState.getArena().getHeight() - 2 * margin);
        return new Vector2D(x, y);
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

    public List<SafeZone> generateClientSafeZones(int clientId, boolean fakeChaosActive) {
        List<SafeZone> result = new ArrayList<>();
        result.add(gameState.getTrueSafeZone());
        if (fakeChaosActive) {
            Random clientRng = new Random(Objects.hash(clientId, gameState.getRoundNumber()));
            for (int i = 0; i < GameConfig.FAKE_SAFE_ZONE_COUNT; i++) {
                double margin = GameConfig.SAFE_ZONE_MIN_MARGIN;
                double x = margin + clientRng.nextDouble() * (gameState.getArena().getWidth() - 2 * margin);
                double y = margin + clientRng.nextDouble() * (gameState.getArena().getHeight() - 2 * margin);
                result.add(new SafeZone(new Vector2D(x, y), GameConfig.SAFE_ZONE_RADIUS));
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
```

### 5B. `EliminationManager`
**File:** `src/main/java/com/identitycrisis/server/game/EliminationManager.java`

`CarryManager` is injected so `eliminatePlayer()` can release carry state on the partner
before setting `ELIMINATED` — prevents the partner from being permanently stuck.
`eliminatePlayer()` also **prunes `controlMap`** so dead players are never included in a
future `CONTROL_SWAP` derangement and no living client remains swapped onto an eliminated player.

```java
import com.identitycrisis.shared.model.*;
import java.util.*;

public class EliminationManager {
    private final GameState gameState;
    private final CarryManager carryManager;

    public EliminationManager(GameState gameState, CarryManager carryManager) {
        this.gameState    = gameState;
        this.carryManager = carryManager;
    }

    public List<Integer> evaluateEliminations() {
        List<Integer> eliminated = new ArrayList<>();
        SafeZone zone = gameState.getTrueSafeZone();
        if (zone == null) return eliminated;

        List<Player> alive = gameState.getAlivePlayers();
        List<Player> outside = alive.stream()
            .filter(p -> !p.isInSafeZone())
            .toList();

        if (gameState.getRoundNumber() <= GameConfig.WARMUP_ROUNDS) {
            // Warmup: all outside eliminated (usually nobody)
            for (Player p : outside) {
                eliminatePlayer(p.getPlayerId());
                eliminated.add(p.getPlayerId());
            }
        } else {
            // Elimination round: exactly one must die
            if (outside.isEmpty()) {
                // Edge case: all inside — eliminate the one farthest from center
                Player farthest = alive.stream()
                    .max(Comparator.comparingDouble(p -> p.getPosition().distanceTo(zone.position())))
                    .orElse(null);
                if (farthest != null) {
                    eliminatePlayer(farthest.getPlayerId());
                    eliminated.add(farthest.getPlayerId());
                }
            } else {
                // Eliminate the one farthest from zone center among those outside
                Player farthest = outside.stream()
                    .max(Comparator.comparingDouble(p -> p.getPosition().distanceTo(zone.position())))
                    .orElse(null);
                if (farthest != null) {
                    eliminatePlayer(farthest.getPlayerId());
                    eliminated.add(farthest.getPlayerId());
                }
            }
        }
        return eliminated;
    }

    private void eliminatePlayer(int playerId) {
        Player p = gameState.getPlayerById(playerId);
        if (p != null) {
            carryManager.releaseCarry(playerId); // frees partner; may temporarily set p to ALIVE
            p.setState(PlayerState.ELIMINATED);  // override — eliminated wins
            p.setInSafeZone(false);
            // Prune controlMap: remove their entry and restore any client that was
            // CONTROL_SWAP'd onto this player back to self-control.
            Map<Integer, Integer> cm = gameState.getControlMap();
            cm.remove(playerId);
            cm.replaceAll((clientId, controlled) ->
                controlled.equals(playerId) ? clientId : controlled);
        }
    }

    public boolean isGameOver() { return gameState.getAliveCount() <= 1; }

    public int getWinnerId() {
        return gameState.getAlivePlayers().stream()
            .findFirst()
            .map(Player::getPlayerId)
            .orElse(-1);
    }
}
```

### 5C. `ChaosEventManager`
**File:** `src/main/java/com/identitycrisis/server/game/ChaosEventManager.java`

```java
import com.identitycrisis.shared.model.*;
import java.util.*;

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

        // If an event is active, tick its duration down
        if (gameState.getActiveChaosEvent() != ChaosEventType.NONE) {
            double remaining = gameState.getChaosEventTimer() - dt;
            if (remaining <= 0) {
                // Event expired
                if (gameState.getActiveChaosEvent() == ChaosEventType.CONTROL_SWAP) {
                    revertControlSwap();
                }
                gameState.setActiveChaosEvent(ChaosEventType.NONE);
                gameState.setChaosEventTimer(0);
            } else {
                gameState.setChaosEventTimer(remaining);
            }
            return; // only one event at a time
        }

        // No active event — check if it's time to trigger one
        elapsedInRound += dt;
        if (elapsedInRound >= scheduledTriggerTime) {
            ChaosEventType event = pickRandomEvent();
            gameState.setActiveChaosEvent(event);
            gameState.setChaosEventTimer(GameConfig.CHAOS_EVENT_DURATION);
            if (event == ChaosEventType.CONTROL_SWAP) {
                applyControlSwap();
            }
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

    /**
     * Shuffles controlMap so no client controls their own player (derangement).
     */
    private void applyControlSwap() {
        Map<Integer, Integer> map = gameState.getControlMap();
        List<Integer> clientIds = new ArrayList<>(map.keySet());
        List<Integer> playerIds = new ArrayList<>(map.values());
        // Fisher-Yates derangement: shuffle playerIds until no fixed point
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
        // Restore identity mapping: each client controls their own player
        Map<Integer, Integer> map = gameState.getControlMap();
        for (Integer clientId : new ArrayList<>(map.keySet())) {
            map.put(clientId, clientId);
        }
    }

    public boolean isFakeSafeZonesActive() {
        return gameState.getActiveChaosEvent() == ChaosEventType.FAKE_SAFE_ZONES;
    }
}
```

### 5D. `CarryManager`
**File:** `src/main/java/com/identitycrisis/server/game/CarryManager.java`

```java
import com.identitycrisis.shared.model.*;
import com.identitycrisis.shared.util.Vector2D;
import java.util.*;

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

        // Throw velocity based on carrier facing direction
        Vector2D throwDir = facingToVector(carrier.getFacingDirection());
        carried.setVelocity(throwDir.multiply(GameConfig.THROW_SPEED));

        // Release carry
        carrier.setState(PlayerState.ALIVE);
        carrier.setCarryingPlayerId(-1);
        carried.setState(PlayerState.ALIVE);
        carried.setCarriedByPlayerId(-1);
        // Stun the thrown player so throw velocity is preserved for THROW_STUN_SECONDS
        // before they can override it with directional input. PhysicsEngine.applyInput()
        // skips the player while stunTimer > 0; step() decays the velocity via damping.
        carried.setStunTimer(GameConfig.THROW_STUN_SECONDS);

        gameState.getActiveCarries().removeIf(
            cs -> cs.carrierPlayerId() == carrierPlayerId);
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

    public void releaseCarry(int playerId) {
        Player p = gameState.getPlayerById(playerId);
        if (p == null) return;

        // If this player was carrying someone
        if (p.getCarryingPlayerId() != -1) {
            Player carried = gameState.getPlayerById(p.getCarryingPlayerId());
            if (carried != null) {
                carried.setState(PlayerState.ALIVE);
                carried.setCarriedByPlayerId(-1);
            }
            p.setState(PlayerState.ALIVE);
            p.setCarryingPlayerId(-1);
        }
        // If this player was being carried
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
            case 0 -> new Vector2D(0, -1);  // up
            case 1 -> new Vector2D(1, 0);   // right
            case 2 -> new Vector2D(0, 1);   // down
            case 3 -> new Vector2D(-1, 0);  // left
            default -> new Vector2D(0, 1);
        };
    }
}
```

### 5E. `RoundManager`
**File:** `src/main/java/com/identitycrisis/server/game/RoundManager.java`

Implements the full state machine from `game-logic.md` §10.

```java
import com.identitycrisis.shared.model.*;

public class RoundManager {
    private final GameState gameState;
    private final SafeZoneManager safeZoneManager;
    private final ChaosEventManager chaosEventManager;
    private final EliminationManager eliminationManager;

    public RoundManager(GameState gs, SafeZoneManager szm,
                        ChaosEventManager cem, EliminationManager em) {
        this.gameState          = gs;
        this.safeZoneManager    = szm;
        this.chaosEventManager  = cem;
        this.eliminationManager = em;
    }

    public void tick(double dt) {
        switch (gameState.getPhase()) {
            case LOBBY -> { /* LobbyManager handles this externally */ }

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
                if (gameState.getRoundTimer() <= 0) {
                    // Clear chaos event BEFORE transitioning — ChaosEventManager.tick()
                    // early-returns for non-ACTIVE phases and will never clear it otherwise.
                    // Without this, isFakeSafeZonesActive() stays true through ROUND_END,
                    // ELIMINATION, and COUNTDOWN, sending decoy zones in inter-round snapshots.
                    chaosEventManager.clearActiveEvent();
                    transitionTo(RoundPhase.ROUND_END);
                }
            }

            // ROUND_END is a one-tick transient state that executes exactly once per
            // round-end transition. Collect eliminated IDs into GameState.pendingEliminationIds
            // so ServerGameLoop.broadcastState() can send S_PLAYER_ELIMINATED messages for
            // this tick without the round state machine touching the network layer.
            case ROUND_END -> {
                List<Integer> eliminated = eliminationManager.evaluateEliminations();
                gameState.getPendingEliminationIds().addAll(eliminated);
                transitionTo(RoundPhase.ELIMINATION);
                gameState.setRoundTimer(GameConfig.ELIMINATION_DISPLAY_SECONDS);
            }

            case ELIMINATION -> {
                gameState.setRoundTimer(gameState.getRoundTimer() - dt);
                if (gameState.getRoundTimer() <= 0) {
                    if (eliminationManager.isGameOver()) {
                        transitionTo(RoundPhase.GAME_OVER);
                    } else {
                        gameState.setRoundNumber(gameState.getRoundNumber() + 1);
                        startNewRound();
                        transitionTo(RoundPhase.COUNTDOWN);
                        gameState.setRoundTimer(GameConfig.COUNTDOWN_SECONDS);
                    }
                }
            }

            case GAME_OVER -> { /* Game is done. */ }
        }
    }

    private void transitionTo(RoundPhase phase) { gameState.setPhase(phase); }

    private void startNewRound() {
        safeZoneManager.spawnSafeZone();
        // Reset all alive players' safe zone flags and positions to random spawns
        for (Player p : gameState.getAlivePlayers()) {
            p.setInSafeZone(false);
        }
    }

    public boolean isWarmupRound() {
        return gameState.getRoundNumber() <= GameConfig.WARMUP_ROUNDS;
    }

    private boolean shouldEndGame() {
        return eliminationManager.isGameOver();
    }
}
```

**Verify:** `./mvnw clean compile && ./mvnw test`

---

## Step 6 — Server Game Loop Integration

### 6A. `ServerGameLoop.processInputs()`
**File:** `src/main/java/com/identitycrisis/server/game/ServerGameLoop.java`

```java
private void processInputs() {
    QueuedInput qi;
    while ((qi = inputQueue.poll()) != null) {
        Map<Integer, Integer> controlMap = ctx.gameState().getControlMap();
        int controlledPlayer = controlMap.getOrDefault(qi.clientId(), qi.clientId());
        // REVERSED_CONTROLS inversion is handled client-side (ClientGameLoop.applyChaosModifications
        // swaps up↔down and left↔right in the InputSnapshot before sending). The server receives
        // already-inverted bytes and must NOT invert again — double-inverting cancels the effect.
        // Always pass false; the reversedControls parameter of applyInput() is kept for the
        // method signature but must never be set true from the game loop. (AGENTS.md rule 20)
        boolean[] f = qi.flags();
        // flags: [0]=up, [1]=down, [2]=left, [3]=right, [4]=carry, [5]=throw
        physics.applyInput(ctx.gameState(), controlledPlayer,
                           f[0], f[1], f[2], f[3], false);
        if (f[4]) ctx.carryManager().tryCarry(controlledPlayer);
        if (f[5]) ctx.carryManager().throwCarried(controlledPlayer);
    }
}
```

Add import: `import java.util.Map;`
(`ChaosEventType` is NOT needed here — the reversal check was removed, see above.)

### 6B. `ServerGameLoop.broadcastState()`

Builds a **per-client** snapshot and encodes it using the `ByteArrayOutputStream` pattern:

```java
private void broadcastState() {
    GameState gs = ctx.gameState();
    List<Player> allPlayers = gs.getPlayers();
    boolean fakeSafeZones = ctx.chaosEventManager().isFakeSafeZonesActive();

    for (ClientConnection client : server.getClients()) {
        int clientId = client.getClientId();
        int controlledPlayerId = gs.getControlMap().getOrDefault(clientId, clientId);

        // Build per-client safe zone list
        List<SafeZone> zones = ctx.safeZoneManager().generateClientSafeZones(clientId, fakeSafeZones);

        // Convert to encoder data carriers
        MessageEncoder.PlayerNetData[] playerData = new MessageEncoder.PlayerNetData[allPlayers.size()];
        for (int i = 0; i < allPlayers.size(); i++) {
            Player p = allPlayers.get(i);
            playerData[i] = new MessageEncoder.PlayerNetData(
                p.getPlayerId(), p.getDisplayName(),
                p.getPosition().x(), p.getPosition().y(),
                p.getVelocity().x(), p.getVelocity().y(),
                (byte) p.getState().ordinal(), p.getFacingDirection(),
                p.isInSafeZone(), p.getCarriedByPlayerId(), p.getCarryingPlayerId()
            );
        }

        MessageEncoder.SafeZoneNetData[] zoneData = new MessageEncoder.SafeZoneNetData[zones.size()];
        for (int i = 0; i < zones.size(); i++) {
            SafeZone z = zones.get(i);
            zoneData[i] = new MessageEncoder.SafeZoneNetData(
                z.position().x(), z.position().y(), z.radius());
        }

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MessageEncoder enc = new MessageEncoder(new DataOutputStream(baos));
            enc.encodeGameState(
                gs.getRoundNumber(), gs.getRoundTimer(),
                (byte) gs.getPhase().ordinal(),
                (byte) gs.getActiveChaosEvent().ordinal(),
                gs.getChaosEventTimer(), controlledPlayerId,
                playerData, zoneData
            );
            enc.flush();
            server.sendToClient(client, baos.toByteArray());
        } catch (IOException e) {
            // Client may have disconnected — send() handles this gracefully
        }
    }
}
```

Add imports:
```java
import com.identitycrisis.shared.model.*;
import com.identitycrisis.shared.net.MessageEncoder;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
```

### 6C. Wire up `LobbyManager` → `RoundManager` start

When `LobbyManager` detects `canStartGame()`, it currently calls `server.startGame()`.
You also need to initialize the `controlMap` and add players to `GameState` at that point.

**In `LobbyManager.handleReady()`**, before calling `server.startGame()`, add the
player initialization logic. The cleanest approach: add a method to `LobbyManager`
called `initializeGameState(GameState, GameContext)` that `ServerApp.main()` can
wire with a reference, OR have the `ServerGameLoop` check on first tick if players
need initialization.

**Simplest approach:** In `LobbyManager`, when `canStartGame()` is true:

```java
if (canStartGame()) {
    // Initialize players in GameState
    ServerGameLoop loop = server.getGameLoop();
    // Players need to be added to GameState before the game loop starts.
    // This is done here because LobbyManager knows which clients are connected.
    // Access GameState via the loop's context is not ideal, but the alternative
    // is adding GameState as a LobbyManager dependency — which is a valid refactor
    // if needed later.
    server.startGame();
}
```

**Note:** The `GameState.getPlayers()` list must be populated with `Player` objects
matching the connected clients **before** the game loop starts. The cleanest place
is in `ServerApp.main()` or in a startup hook. For the skeleton, add a `getGameState()`
accessor to `ServerGameLoop` or pass `GameState` to `LobbyManager` via setter.

**Recommended approach — add `GameState` as a LobbyManager field:**

In `LobbyManager`, add:
```java
private GameState gameState; // set by Composition Root
public void setGameState(GameState gs) { this.gameState = gs; }
```

In `ServerApp.main()`, after creating `LobbyManager`, call:
```java
lobbyMgr.setGameState(gameState);
```

Then in `handleReady()`, when `canStartGame()`:
```java
if (canStartGame()) {
    // Populate GameState with connected players
    for (ClientConnection c : server.getClients()) {
        Player p = new Player(c.getClientId(), c.getDisplayName());
        gameState.getPlayers().add(p);
        gameState.getControlMap().put(c.getClientId(), c.getClientId());
    }
    gameState.setPhase(RoundPhase.COUNTDOWN);
    gameState.setRoundNumber(1);
    gameState.setRoundTimer(GameConfig.COUNTDOWN_SECONDS);
    // Spawn first safe zone
    // (SafeZoneManager is in GameContext, not accessible here — the first
    //  RoundManager.tick() will handle the first zone spawn via startNewRound())
    server.startGame();
}
```

Update `agents/rules/server-package.md` to document this `setGameState()` setter.

**Verify:** `./mvnw clean compile && ./mvnw test`

---

## Step 7 — End-to-End Verification

At this point the full server backend is operational:

1. **Start server:** `./mvnw compile exec:java@server`
2. **Connect 4 TCP clients** (can be simple test mains or a test harness).
3. Each client sends `C_JOIN_REQUEST("PlayerN")` → server logs connection, broadcasts `S_LOBBY_STATE`.
4. Each client sends `C_READY` → when all 4 ready, game loop starts.
5. Game loop ticks at 60 tps: processes inputs, runs physics, ticks round manager (COUNTDOWN→ACTIVE→ROUND_END→ELIMINATION→...), broadcasts per-client `S_GAME_STATE` every tick.
6. Clients can send `C_PLAYER_INPUT` → players move, collisions resolve.
7. Chaos events trigger mid-round, safe zones spawn each round, eliminations happen.
8. Last player standing → `GAME_OVER` → `S_GAME_OVER` broadcast.

### Test Harness (optional)

Create `src/test/java/.../server/ServerIntegrationTest.java` that:
1. Starts `GameServer` on a random port in a background thread.
2. Connects 4 `Socket` clients.
3. Each sends `C_JOIN_REQUEST` + `C_READY`.
4. Reads `S_LOBBY_STATE` responses.
5. Verifies game starts (receives `S_GAME_STATE` messages).
6. Disconnects gracefully.

---

## Summary of Files Modified

| File | What changed |
|---|---|
| `shared/util/Vector2D.java` | Math method bodies |
| `shared/util/GameTimer.java` | All method bodies |
| `shared/model/Arena.java` | `loadDefault()`, `getWidth()`, `getHeight()`, `isWall()` |
| `shared/model/Player.java` | All getter/setter bodies |
| `shared/net/MessageType.java` | `fromTag()` body |
| `shared/net/MessageEncoder.java` | All encode methods + `writeHeader` + `flush` |
| `shared/net/MessageDecoder.java` | `readNextType()` + all decode methods + `payloadStream()` |
| `server/net/ClientMessageRouter.java` | `route()` body |
| `server/game/LobbyManager.java` | `handleJoin`, `handleReady`, `canStartGame`, `broadcastLobbyState` |
| `server/game/GameState.java` | All getter/setter bodies |
| `server/physics/PhysicsEngine.java` | `step()`, `applyInput()` |
| `server/physics/CollisionDetector.java` | `resolve()`, `resolveWallCollision()`, `resolvePlayerCollision()` |
| `server/game/SafeZoneManager.java` | All method bodies |
| `server/game/EliminationManager.java` | All method bodies |
| `server/game/ChaosEventManager.java` | All method bodies |
| `server/game/CarryManager.java` | All method bodies |
| `server/game/RoundManager.java` | `tick()`, `transitionTo()`, `startNewRound()`, `isWarmupRound()` |
| `server/game/ServerGameLoop.java` | `processInputs()`, `broadcastState()` |
| `server/ServerApp.java` | Add `lobbyMgr.setGameState(gameState)` |

**No client/render/scene files are touched.** The entire frontend is independent.
