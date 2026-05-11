# Identity Crisis — Architecture Document

> **Real-time multiplayer top-down arena survival game**
> CMSC 137 · Second Semester AY 2025-2026

This document is the **sole source of truth** for the implementing LLM. It contains the complete architecture, every file path, every class, every method signature, the network protocol, data flow, and implementation notes. **Create every file listed. Implement every stub.**

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Tech Stack](#2-tech-stack)
3. [Architecture Overview](#3-architecture-overview)
4. [Directory & File Tree](#4-directory--file-tree)
5. [Shared Package](#5-shared-package)
6. [Server Package](#6-server-package)
7. [Client Package](#7-client-package)
8. [Network Protocol Specification](#8-network-protocol-specification)
9. [Game Loop Design](#9-game-loop-design)
10. [Round State Machine](#10-round-state-machine)
11. [Chaos Event System](#11-chaos-event-system)
12. [Carry & Throw Mechanic](#12-carry--throw-mechanic)
13. [Safe Zone Logic](#13-safe-zone-logic)
14. [Scene Flow](#14-scene-flow)
15. [Build & Run](#15-build--run)
16. [Implementation Order](#16-implementation-order)
17. [Functional Requirements Traceability](#17-functional-requirements-traceability)

---

## 1. Project Overview

**Identity Crisis** is a top-down 2D arena survival game where 4+ players compete to reach a randomly spawning safe zone each round.

### Core Rules
- **Rounds 1–2 (Warm-up):** All players who reach the safe zone in time survive.
- **Rounds 3+ (Elimination):** Safe zone fits exactly `n-1` players. One eliminated per round.
- **Chaos events** triggered globally by server mid-round: reversed controls, control ownership swaps, fake safe zone decoys.
- **Carry/throw:** Pick up and throw another player. Carrier CANNOT be marked safe until release.
- **Last player standing wins.**
- **(Bonus)** In-game text chat.

---

## 2. Tech Stack

| Concern | Choice | Rationale |
|---|---|---|
| Language | Java 21 | LTS, records, sealed classes, pattern matching |
| Build tool | Maven | Reliable JavaFX integration |
| Graphics | JavaFX 21 (`Canvas` + `GraphicsContext`) | Clean 2D API, `AnimationTimer` as render hook |
| Networking | `java.net.ServerSocket`, `Socket`, `DataInputStream/DataOutputStream` | No external frameworks per spec |
| Serialization | Manual binary protocol | Full control, no ObjectInputStream security issues |
| Game loop | Custom tick loop on server; `AnimationTimer` on client | Spec mandates custom loop |
| Module system | JPMS (`module-info.java`) | Required for JavaFX 21 |
| Dependency injection | **Manual constructor DI (no framework)** | See §2b below |

## 2b. Dependency Injection Architecture

> **Rule:** No DI framework (no Spring, Guice, or Dagger). Manual constructor injection
> from a single Composition Root. This is JPMS-compatible, zero-dependency, and
> sufficient for this project's ~60-class surface area.

### Composition Root
`ServerApp.main()` is the **sole** place where collaborating server objects are
created with `new`. Every other class receives its dependencies via constructor
or setter parameter — never by calling `new` on another class internally.

### Constructor Injection (preferred)
Used everywhere dependencies do **not** form a cycle:
```
SafeZoneManager(GameState)
ChaosEventManager(GameState)
CarryManager(GameState)
EliminationManager(GameState, CarryManager)
RoundManager(GameState, SafeZoneManager, ChaosEventManager, EliminationManager)
ServerGameLoop(GameServer, GameContext, PhysicsEngine, CollisionDetector)
```

### Setter Injection (circular reference only)
`GameServer`, `ClientMessageRouter`, and `LobbyManager` form a three-way circular
reference. Break it with setter injection from the Composition Root:
```java
GameServer server         = new GameServer(port);          // step 1
ClientMessageRouter router = new ClientMessageRouter(server); // step 2
LobbyManager lobbyMgr     = new LobbyManager(server);        // step 3
server.setRouter(router);              // step 4 — resolve cycle
server.setLobbyManager(lobbyMgr);      // step 4
lobbyMgr.setGameState(gameState);      // step 5 — lobby needs GameState to populate players on start
lobbyMgr.setSafeZoneManager(szm);      // step 5 — lobby spawns round-1 zones via spawnRoundZones(n)
```
`GameServer.start()` throws `IllegalStateException` if setters were not called.

### Backend Implementation Guide
See **`agents/BACKEND_IMPL.md`** for a step-by-step implementation guide covering
every stub method in the backend — from shared foundation through a fully working
game loop with player connections. That document is self-contained and ordered so
an LLM can implement each step without cross-referencing other files.

### GameContext Record
Groups all six game-manager collaborators into one value-object so
`ServerGameLoop` has a clean 4-argument constructor instead of 9:
```java
public record GameContext(
    GameState gameState,
    SafeZoneManager safeZoneManager,
    ChaosEventManager chaosEventManager,
    CarryManager carryManager,
    EliminationManager eliminationManager,
    RoundManager roundManager
) {}
```

### Critical Rules for Implementing Agents
1. **Never instantiate a collaborator inside another collaborator.** If class A needs
   class B, add B as a constructor parameter to A and wire it in `ServerApp.main()`.
   **Exception:** `Renderer` creates its five sub-renderers (`ArenaRenderer`,
   `PlayerRenderer`, `SafeZoneRenderer`, `HudRenderer`, `ChatRenderer`) internally
   because the spec fixes the constructor signature to `(Canvas, SpriteManager)` and
   these sub-renderers are 1:1 owned children never shared with any other class.
2. **All socket writes go through `ClientConnection.send(byte[])`** (synchronized).
   `getOutputStream()` **and** `getEncoder()` are both deprecated and throw
   `UnsupportedOperationException` — do not use them.
   **Encoding pattern:** encode to a `ByteArrayOutputStream`, then pass `byte[]` to
   `send()`. Example:
   ```java
   ByteArrayOutputStream baos = new ByteArrayOutputStream();
   MessageEncoder enc = new MessageEncoder(new DataOutputStream(baos));
   enc.encodeGameState(...);
   enc.flush();
   client.send(baos.toByteArray());
   ```
3. **Stop the game loop with `ServerGameLoop.stop()`**, never `Thread.interrupt()` —
   interrupt closes the socket streams abruptly.
4. **Input queue cap:** `enqueueInput()` silently drops if
   `inputQueue.size() >= GameConfig.MAX_QUEUED_INPUTS` (120).
5. **Named threads:** game loop thread = `"server-game-loop"` (non-daemon);
   client reader threads = `"client-conn-<id>"` (daemon).
6. **Shutdown hook:** `ServerApp` registers a JVM shutdown hook that calls
   `server.shutdown()` — this closes sockets and stops the loop on Ctrl+C.
7. **Keep docs in sync.** Whenever a bug is fixed or an architectural decision
   changes (method signatures, wiring order, new files, threading rules, protocol
   changes, etc.), update **every affected `.md` file** in `agents/` and
   `agents/rules/` before finishing the task. The markdown files are the source of
   truth for future agents — stale docs cause the same bugs to reappear.
8. **Server game loop uses fixed `dt = 1.0 / GameConfig.TICK_RATE`** — never
   wall-clock delta. Variable delta defeats determinism and makes round timers
   machine-dependent. See `ServerGameLoop.run()`.
9. **Disconnect cleanup:** `GameServer.removeClient()` calls
   `ServerGameLoop.cleanupClient(clientId)`, which (a) calls
   `CarryManager.releaseCarry(playerId)` to free carry state, and (b) **prunes
   `controlMap`** — removes the disconnected client's entry and redirects any
   living client currently swapped to control that player back to self-control.
   `ClientConnection.run()` calls `server.removeClient(this)` in its `finally`
   block — this is how the chain is triggered.
13. **`LobbyManager.handleReady()` AND `handleJoin()` must both be `synchronized`** —
   both are called from `ClientConnection` reader threads. `handleReady()` writes to
   `readyClientIds`; `handleJoin()` reads it via `broadcastLobbyState()`. Racing on a
   plain `HashSet` causes silent corruption. The `gameStarted` boolean guard inside
   `handleReady()` is the double-startGame idempotency check.
14. **`GameState.players` and `GameState.activeCarries` are both `CopyOnWriteArrayList`**,
   not plain `ArrayList`s. `activeCarries` is iterated by `CarryManager.tick()` on the
   game loop thread while `CarryManager.releaseCarry()` may modify it from a
   `ClientConnection` reader thread via the disconnect chain. `players` is written by
   `LobbyManager.handleReady()` on a reader thread before the loop starts, but
   using `CopyOnWriteArrayList` makes this safe by type rather than only by control flow.
15. **`GameServer` rejects TCP connections after game starts.** `startGame()` sets
   `volatile boolean gameInProgress = true`. The accept loop checks this flag and
   closes the socket immediately (with `continue`) before creating a `ClientConnection`.
   This prevents ghost clients with no `Player` or `controlMap` entry.
16. **`ChaosEventManager.clearActiveEvent()` must be called at `ACTIVE → ROUND_END`.**
   `tick()` early-returns for non-ACTIVE phases and never clears the event on round end.
   Without the explicit clear, `isFakeSafeZonesActive()` stays true through ROUND_END,
   ELIMINATION, and COUNTDOWN, sending decoy zones in inter-round snapshots.
10. **`Player` must be constructed with `new Player(id, name)`** — never with
    `new Player()` (no such constructor). Carry IDs default to `-1` (not `0`).
    `Player.equals()` / `hashCode()` are keyed on `playerId`.
    `Player.spriteIndex` defaults to `1` (sprite set 1). It is assigned by
    `LobbyManager.handleReady()` as `i + 1` (1-based join order, capped at 8 by
    `MAX_PLAYERS`), encoded as the **last** field of `PlayerNetData` in both
    `MessageEncoder` and `MessageDecoder`, and propagated to the client in
    `LocalGameState.updateFromSnapshot()`. `SpriteManager.loadAll()` loads sets
    1–8; `GameArena.drawPlayer()` selects the key `"player_{spriteIndex}_{anim}"`.
11. **`GameState` must be constructed with `new GameState()`** which initializes
    all collections. Never assume fields are non-null without calling the constructor.
12. **Tests:** Add JUnit 5 tests for any pure logic class. Run with `./mvnw test`.
    A `MessageCodecTest` covering all message types round-trip exists at
    `src/test/java/com/identitycrisis/shared/net/MessageCodecTest.java`.
17. **`controlMap` must be pruned on player elimination and client disconnect.**
    `EliminationManager.eliminatePlayer()` removes the eliminated player's
    `controlMap` entry (`cm.remove(playerId)`) and uses `cm.replaceAll(...)` to
    restore any living client that was CONTROL_SWAP'd onto the eliminated player
    back to self-control. `ServerGameLoop.cleanupClient()` does the same for
    disconnected clients. Failing to prune allows dead players to participate in
    future `CONTROL_SWAP` derangement shuffles and can permanently assign a living
    client to control an immovable character.
18. **`ChaosEventManager.applyControlSwap()` must guard against ≤1 entries in
    `controlMap`.** A derangement (no element maps to itself) is mathematically
    impossible for a single-element list — the `do/while` shuffle loop would spin
    forever. Add `if (clientIds.size() <= 1) return;` before the loop. Correct
    pruning (rule 17) means this should rarely trigger, but the guard is required
    for robustness against edge-case race conditions.
19. **`GameState.controlMap` must be a `ConcurrentHashMap`**, not a plain `HashMap`.
    The map is read and written by two distinct threads simultaneously: the game loop
    thread (in `processInputs`, `broadcastState`, and `ChaosEventManager`) AND the
    `ClientConnection` reader thread (via the disconnect chain
    `ClientConnection.run() finally → GameServer.removeClient() → ServerGameLoop.cleanupClient()`).
    A plain `HashMap` under concurrent modification causes undefined behaviour including
    lost entries and infinite loops. `ConcurrentHashMap.replaceAll()` is also safe,
    unlike `HashMap.replaceAll()` under concurrent access.
20. **`REVERSED_CONTROLS` inversion is client-side only.**
    `ClientGameLoop.applyChaosModifications()` swaps `up↔down` and `left↔right` in
    the `InputSnapshot` **before** the input is sent to the server. The server receives
    already-inverted bytes and must process them as-is — `ServerGameLoop.processInputs()`
    always passes `reversedControls=false` to `PhysicsEngine.applyInput()`.
    **Double-inverting (client + server) would cancel the effect and make
    `REVERSED_CONTROLS` a no-op.** The `reversedControls` parameter of `applyInput()`
    is kept in the method signature for potential future use but must not be set to
    `true` from the game loop.
21. **Pixel-perfect wall collision — two-layer system.**
    The collision system is split by runtime context:
    - **Client** (`MapManager`): `TileHitboxCache.build()` reads each tileset PNG via
      JavaFX `PixelReader` at `MapManager.load()` time, producing
      `Map<Integer, boolean[16][16]>` (`tileAlphaMasks`) keyed by global tile ID.
      `isSolidPixel(worldX, worldY)` does broad-phase via `solid[][]`, then narrows
      to the exact pixel via the alpha bitmask.
      `intersectsWallPixels(x, y, w, h)` checks all tiles overlapping an AABB and
      returns `true` on the first solid pixel — safe to call every frame (≤4 tiles).
    - **Server** (`CollisionDetector`): `TmxWallsParser.load()` parses the TMX at
      `ServerApp.main()` time (no image I/O) and returns `WallCollisionData` with
      the walls GID grid and per-tile objectgroup rectangles.
      `CollisionDetector.resolveWallCollision()` does circle-vs-AABB push-out using
      those rectangles; tiles without an objectgroup fall back to a full 16×16 rect.
    - **Wiring**: called in `ServerApp.main()` at step 3 and injected via
      `CollisionDetector(WallCollisionData)`. Legacy no-arg constructor retained.

## 4. Directory & File Tree

```
identity-crisis/
├── pom.xml
├── Makefile
├── dev.sh
├── README.md
├── .mvn/
│   └── wrapper/
│       └── maven-wrapper.properties  (locks Maven to 3.9.14)
├── src/main/
│   ├── java/
│   │   ├── module-info.java
│   │   └── com/identitycrisis/
│   │       ├── shared/
│   │       │   ├── model/
│   │       │   │   ├── Player.java
│   │       │   │   ├── PlayerState.java        (enum)
│   │       │   │   ├── SafeZone.java           (record)
│   │       │   │   ├── Arena.java
│   │       │   │   ├── RoundPhase.java         (enum)
│   │       │   │   ├── ChaosEventType.java     (enum)
│   │       │   │   ├── CarryState.java         (record)
│   │       │   │   └── GameConfig.java         (constants)
│   │       │   ├── net/
│   │       │   │   ├── MessageType.java        (enum w/ byte tags)
│   │       │   │   ├── MessageEncoder.java
│   │       │   │   ├── MessageDecoder.java
│   │       │   │   ├── client/
│   │       │   │   │   ├── PlayerInputMessage.java   (record)
│   │       │   │   │   ├── JoinRequestMessage.java   (record)
│   │       │   │   │   ├── ReadyMessage.java         (record)
│   │       │   │   │   └── ChatSendMessage.java      (record)
│   │       │   │   └── server/
│   │       │   │       ├── GameStateSnapshot.java
│   │       │   │       ├── RoundStateUpdate.java     (record)
│   │       │   │       ├── SafeZoneUpdate.java       (record)
│   │       │   │       ├── PlayerEliminatedMessage.java (record)
│   │       │   │       ├── ChaosEventMessage.java    (record)
│   │       │   │       ├── ControlSwapMessage.java   (record)
│   │       │   │       ├── GameOverMessage.java      (record)
│   │       │   │       ├── LobbyStateMessage.java    (record)
│   │       │   │       └── ChatBroadcastMessage.java (record)
│   │       │   └── util/
│   │       │       ├── Vector2D.java           (record)
│   │       │       ├── GameTimer.java
│   │       │       └── Logger.java
│   │       ├── server/
│   │       │   ├── ServerApp.java              (main)
│   │       │   ├── net/
│   │       │   │   ├── GameServer.java
│   │       │   │   ├── ClientConnection.java   (Runnable)
│   │       │   │   └── ClientMessageRouter.java
│   │       │   ├── game/
│   │       │   │   ├── ServerGameLoop.java     (Runnable)
│   │       │   │   ├── GameContext.java         (record — game manager bundle for DI)
│   │       │   │   ├── GameState.java
│   │       │   │   ├── RoundManager.java
│   │       │   │   ├── SafeZoneManager.java
│   │       │   │   ├── ChaosEventManager.java
│   │       │   │   ├── CarryManager.java
│   │       │   │   ├── EliminationManager.java
│   │       │   │   └── LobbyManager.java
│   │       │   └── physics/
│   │       │       ├── PhysicsEngine.java
│   │       │       └── CollisionDetector.java
│   │       └── client/
│   │           ├── ClientApp.java              (JavaFX Application)
│   │           ├── net/
│   │           │   ├── GameClient.java
│   │           │   └── ServerMessageRouter.java
│   │           ├── input/
│   │           │   ├── InputManager.java
│   │           │   └── InputSnapshot.java      (record)
│   │           ├── game/
│   │           │   ├── ClientGameLoop.java     (extends AnimationTimer)
│   │           │   └── LocalGameState.java
│   │           ├── render/
│   │           │   ├── Renderer.java
│   │           │   ├── ArenaRenderer.java
│   │           │   ├── PlayerRenderer.java
│   │           │   ├── SafeZoneRenderer.java
│   │           │   ├── HudRenderer.java
│   │           │   ├── ChatRenderer.java
│   │           │   └── SpriteManager.java
│   │           ├── scene/
│   │           │   ├── SceneManager.java
│   │           │   ├── MenuScene.java
│   │           │   ├── LobbyScene.java
│   │           │   ├── GameScene.java
│   │           │   ├── ResultScene.java
│   │           │   └── HowToPlayScene.java
│   │           └── audio/
│   │               └── AudioManager.java
│   └── resources/
│       ├── sprites/
│       │   ├── players/
│       │   ├── map/
│       │   ├── obstacles/
│       │   ├── safezone/
│       │   ├── ui/
│       │   └── effects/
│       ├── audio/
│       ├── fonts/
│       └── maps/
```
## 15. Build & Run

### 15.1 `pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.identitycrisis</groupId>
    <artifactId>identity-crisis</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <javafx.version>21.0.5</javafx.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.openjfx</groupId>
            <artifactId>javafx-controls</artifactId>
            <version>${javafx.version}</version>
        </dependency>
        <dependency>
            <groupId>org.openjfx</groupId>
            <artifactId>javafx-graphics</artifactId>
            <version>${javafx.version}</version>
        </dependency>
        <dependency>
            <groupId>org.openjfx</groupId>
            <artifactId>javafx-media</artifactId>
            <version>${javafx.version}</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
                <configuration>
                    <source>21</source>
                    <target>21</target>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.1.0</version>
                <executions>
                    <execution>
                        <id>server</id>
                        <goals><goal>java</goal></goals>
                        <configuration>
                            <mainClass>com.identitycrisis.server.ServerApp</mainClass>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>org.openjfx</groupId>
                <artifactId>javafx-maven-plugin</artifactId>
                <version>0.0.8</version>
                <configuration>
                    <mainClass>com.identitycrisis/com.identitycrisis.client.ClientApp</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### 15.2 `module-info.java`

```java
module com.identitycrisis {
    requires javafx.controls;
    requires javafx.graphics;
    requires javafx.media;

    exports com.identitycrisis.client;
    exports com.identitycrisis.server;
    exports com.identitycrisis.shared.model;
    exports com.identitycrisis.shared.net;
    exports com.identitycrisis.shared.net.client;
    exports com.identitycrisis.shared.net.server;
    exports com.identitycrisis.shared.util;
}
```

### 15.3 Maven Wrapper

The project uses `./mvnw` (Maven Wrapper) to lock the Maven version to **3.9.14**.
All commands below use `./mvnw` — never plain `mvn`.
Commit `.mvn/` to ensure every collaborator uses the same version.

### 15.4 Running

```bash
# Both server + client in one command (recommended)
make dev

# Individual targets
make build    # ./mvnw clean compile
make server   # ./mvnw exec:java@server
make client   # ./mvnw javafx:run
```

`make dev` runs `dev.sh` which: builds → forks server in background → starts client in foreground → kills server on exit (Ctrl+C or client window closed).

Expected health-check output on `make dev`:
```
[INFO][ServerApp] Identity Crisis Server — starting on port 5137
[INFO][GameServer] Server listening on port 5137
[INFO][GameServer] [HEALTH OK] Identity Crisis server is up and waiting for players.
```
Client opens a 1280×720 window with a green `[HEALTH OK]` label until `SceneManager` is wired.
## 14. Scene Flow

```
┌──────────┐     ┌───────────┐     ┌──────────────┐
│  Menu    │────►│  Lobby    │────►│  GameScene   │
│  Scene   │     │  Scene    │     │  (gameplay)  │
└────┬─────┘     └───────────┘     └──────┬───────┘
     │                                     │ game over
     │           ┌───────────┐     ┌───────▼──────┐
     │           │ HowToPlay │     │  Result      │
     └──────────►│  Scene    │     │  Scene       │
     ◄───────────┘           │     └──────┬───────┘
                 └───────────┘            │
                                          │ play again
                                   ┌──────▼──────┐
                                   │   Lobby     │
                                   │   Scene     │
                                   └─────────────┘
```

### Scene Lifecycle

| Scene | On Enter | On Exit |
|---|---|---|
| **MenuScene** | Build UI (buttons, text fields) | Validate inputs, create `GameClient`, connect |
| **LobbyScene** | Display connected players, register lobby callbacks | Unregister callbacks |
| **GameScene** | Create `Canvas`, `Renderer`, `SpriteManager.loadAll()`, attach `InputManager`, start `ClientGameLoop`; show in-place game-over overlay when `S_GAME_OVER` sets `LocalGameState.gameOver` | Stop `ClientGameLoop`, detach `InputManager` |
| **ResultScene** | Display winner name, show buttons | Disconnect or keep connection for replay |
| **HowToPlayScene** | Display static content | Nothing |
