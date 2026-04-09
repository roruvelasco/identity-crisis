## 3. Architecture Overview

```
┌──────────────────────────────────────────────────────────┐
│                       SERVER (headless)                   │
│  ServerGameLoop (60 tps)                                 │
│    ├─ reads PlayerInputMessage from each ClientConnection│
│    ├─ updates authoritative GameState                    │
│    │    ├─ PhysicsEngine.step()                          │
│    │    ├─ CollisionDetector.resolve()                   │
│    │    ├─ RoundManager.tick()                           │
│    │    ├─ SafeZoneManager.tick()                        │
│    │    ├─ CarryManager.tick()                           │
│    │    ├─ ChaosEventManager.tick()                      │
│    │    └─ EliminationManager.evaluate()                 │
│    └─ broadcasts per-client GameStateSnapshot            │
│  GameServer (TCP)                                        │
│    ├─ LobbyManager  ── accepts connections until ready   │
│    └─ ClientConnection[] ── one thread per client        │
└──────────────┬───────────────────────────┬───────────────┘
               │  TCP (binary protocol)    │
┌──────────────▼───────────┐ ┌─────────────▼──────────────┐
│       CLIENT A           │ │        CLIENT B            │
│  InputManager→send input │ │  (identical structure)     │
│  GameClient→recv snapshot│ │                            │
│  ClientGameLoop→render   │ │                            │
│  Renderer (sub-renderers)│ │                            │
│  SceneManager (UI flow)  │ │                            │
└──────────────────────────┘ └────────────────────────────┘
```

### Key Principles
1. **Server = single source of truth.** Clients send input; render what server tells them.
2. **Per-client payloads.** Each client gets personalized snapshot (different fake safe zones, different controlled player ID after swap).
3. **Tick-based server** (60 tps): read inputs → update state → broadcast.
4. **Frame-based client** (~60 fps via AnimationTimer): poll input → send → render latest snapshot.
5. **Separation:** `shared` has zero dependency on `client`/`server`. Both depend only on `shared`.
6. **Manual constructor DI / Composition Root.** `ServerApp.main()` is the only place
   `new` is called on collaborating objects. Every other class receives dependencies via
   its constructor or (for the circular trio: `GameServer`/`ClientMessageRouter`/`LobbyManager`)
   via setter injection. No DI framework — incompatible with JPMS + spec constraints.
   See §2b in `AGENTS.md` for the full wiring order and critical rules.

## 8. Network Protocol Specification

### 8.1 Wire Format

Every TCP message is framed as:

```
┌───────────┬─────────────────┬────────────────────────────┐
│ Type Tag  │ Payload Length   │ Payload                    │
│ 1 byte    │ 2 bytes (uint16)│ variable (0–65535 bytes)   │
└───────────┴─────────────────┴────────────────────────────┘
```

- **Type tag:** `MessageType.getTag()`. `0x01–0x3F` = client→server; `0x40–0x7F` = server→client.
- **Payload length:** unsigned 16-bit big-endian via `DataOutputStream.writeShort()`.
- **Payload:** defined per message type below.
- **String encoding:** UTF-8 via `DataOutputStream.writeUTF()` / `DataInputStream.readUTF()`.

### 8.2 Client → Server Messages

| Type | Tag | Payload |
|---|---|---|
| `C_JOIN_REQUEST` | `0x01` | UTF string (displayName) |
| `C_READY` | `0x02` | (empty — 0 bytes) |
| `C_PLAYER_INPUT` | `0x03` | 1 byte bitfield: bit0=up, bit1=down, bit2=left, bit3=right, bit4=carry, bit5=throw |
| `C_CHAT_SEND` | `0x04` | UTF string (text, max 200 chars) |

### 8.3 Server → Client Messages

| Type | Tag | Payload |
|---|---|---|
| `S_LOBBY_STATE` | `0x40` | int connectedCount, int requiredCount, then N × (UTF name + byte readyFlag) |
| `S_GAME_STATE` | `0x41` | int roundNumber, double timerRemaining, byte phaseOrdinal, byte chaosOrdinal, double chaosDuration, int controlledPlayerId, int playerCount, then N × PlayerBlock, int zoneCount, then M × ZoneBlock |
| `S_ROUND_STATE` | `0x42` | int roundNumber, byte phaseOrdinal, double timerRemaining |
| `S_SAFE_ZONE` | `0x43` | int zoneCount, then N × (double x, double y, double radius) |
| `S_PLAYER_ELIMINATED` | `0x44` | int playerId, UTF playerName |
| `S_CHAOS_EVENT` | `0x45` | byte chaosTypeOrdinal, double durationSeconds |
| `S_CONTROL_SWAP` | `0x46` | int newControlledPlayerId |
| `S_GAME_OVER` | `0x47` | int winnerPlayerId, UTF winnerName |
| `S_CHAT_BROADCAST` | `0x48` | UTF senderName, UTF text |

### 8.4 PlayerBlock (within `S_GAME_STATE`)

```
int   playerId
UTF   displayName
double x
double y
double vx
double vy
byte  stateOrdinal    (PlayerState.ordinal())
int   facingDirection
byte  inSafeZone      (0 or 1)
int   carriedByPlayerId  (-1 if none)
int   carryingPlayerId   (-1 if none)
```

### 8.5 ZoneBlock (within `S_GAME_STATE`)

```
double x
double y
double radius
```

### 8.6 Threading Model

```
SERVER:
  Main thread ──────── accepts connections via ServerSocket.accept()
  ClientConnection-N ─ reader thread per client, reads messages, routes to handlers
  ServerGameLoop ───── single game thread, fixed 60 tps tick loop
  
  Flow: ClientConnection reader → ConcurrentLinkedQueue<QueuedInput> → ServerGameLoop consumes
        ServerGameLoop → per-client encode → ClientConnection.getOutputStream() (synchronized write)

CLIENT:
  JavaFX Application thread ── UI, scene management
  AnimationTimer (on FX thread) ── ClientGameLoop.handle(), poll input, send, render
  GameClient reader thread ──── reads server messages, updates LocalGameState (volatile swap)
  
  Flow: Reader thread → LocalGameState (volatile fields) → AnimationTimer reads on next frame
```

### 8.7 Critical Concurrency Notes

1. **Server input queue:** Use `ConcurrentLinkedQueue` — network threads enqueue, game loop thread dequeues. No locks needed.
2. **Server output:** `ClientConnection.getOutputStream()` must be `synchronized` because the game loop thread writes snapshots while the main thread might write lobby state.
3. **Client LocalGameState:** Use volatile fields for primitive/reference swaps. For `List<Player>`, create a new list each update and swap the reference atomically (volatile reference assignment is atomic in Java).
4. **Never touch JavaFX scene graph from non-FX threads.** Use `Platform.runLater()` for UI callbacks from the network reader thread

## Appendix A: Key Design Decisions

1. **Binary protocol over ObjectStreams** — Smaller payloads, no serialization UID headaches, easier to debug with hex dumps. Worth the upfront effort for the encoder/decoder.

2. **Per-client snapshots** — More bandwidth than a single broadcast, but necessary for F08 (fake safe zones) and F07 (different `controlledPlayerId`). With 4–8 players, the bandwidth is trivial on LAN.

3. **No client-side prediction** — Simplifies the architecture enormously. On a LAN or local machine, the latency is sub-millisecond and prediction adds no value. If movement feels laggy on real networks, add **interpolation** (render between the last two snapshots) rather than prediction.

4. **Fixed timestep server, variable timestep client** — Server uses fixed `dt = 1/60` for determinism. Client uses wall-clock `dt` for smooth rendering. Since the client is just a renderer, there's no simulation divergence risk.

5. **Single game loop thread on server** — No need for separate physics/logic threads. At 60 tps with 4–8 players, the update + broadcast fits within 16ms easily. Complexity of multi-threaded game state would far outweigh any performance benefit.

6. **CopyOnWriteArrayList for client list** — The client list is rarely modified (only on connect/disconnect) but frequently iterated (every tick for broadcast). COW is perfect for this access pattern.