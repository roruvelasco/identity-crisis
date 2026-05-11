# Networked Multiplayer — Implementation Plan

> **Goal:** Make all connected players visible in the GameArena with real-time sprite movement sync — using the existing TCP binary protocol, no new dependencies.

---

## Current State Summary

| What exists | Status |
|---|---|
| **8 sprite sets** (`/sprites/players/1–8/`, each with `Idle.png`, `Walk.png`, `Death.png`) | ✅ Assets ready |
| **SpriteManager** loads players 1–4 (`player_{N}_idle/walk/death`) | ✅ Partial (only 1–4) |
| **GameArena** renders **one** local player using `player_1_*` hardcoded | ⚠️ Single-player only |
| **Server: LobbyManager** creates `Player` objects, assigns `clientId`, spawns positions | ✅ Working |
| **Server: ServerGameLoop** broadcasts `S_GAME_STATE` with **all players' positions** every tick | ✅ Working |
| **Client: LocalGameState.updateFromSnapshot()** reads `PlayerNetData[]` from snapshot | ⚠️ Stores nothing — `players` list never updated |
| **LobbyScene** shows a donut ring with player count — no per-player sprites | ⚠️ No sprite preview |
| **Lobby → GameArena transition** goes through `LoadingScene` → `GameArena` | ✅ Flow exists |
| **Protocol** already sends per-player: `id, name, x, y, vx, vy, stateOrdinal, facing, inSafeZone, carriedBy, carrying` | ✅ Wire-complete |

> [!IMPORTANT]
> The server **already broadcasts all players' positions and states** in every `S_GAME_STATE` snapshot. The client **already decodes** them. The main work is making the client **store** and **render** this data, and assigning sprite indices.

---

## Architecture Decisions

1. **Sprite assignment** = `(clientId - 1) % 8 + 1` → sprite folder index 1–8. No new protocol field needed — the player's `id` deterministically maps to a sprite.
2. **No new message types.** The existing `S_GAME_STATE` already carries everything needed. `S_LOBBY_STATE` already carries player names — we extend it to include sprite indices.
3. **`LocalGameState.players`** will be populated from the snapshot's `PlayerNetData[]` — it's currently declared but never written to.
4. **Remote players** are rendered by `GameArena` using the same `drawPlayer()` logic, parameterised by sprite index, position, facing direction, and animation state.
5. **The local player** continues to be moved client-side for responsiveness; server position is used for all remote players.

---

## Implementation Steps

### Phase 1: Wire `LocalGameState.players` from Snapshot

**Files:** [LocalGameState.java](file:///c:/Users/James/Desktop/codez/cods3rdyr/cmsc137/identity-crisis/src/main/java/com/identitycrisis/client/game/LocalGameState.java)

**What:** `updateFromSnapshot()` currently reads `PlayerNetData[]` but **never populates `this.players`**. Fix it.

- [ ] In `updateFromSnapshot()`, build a `List<Player>` from `data.players()`:
  ```java
  List<Player> updated = new java.util.ArrayList<>();
  for (MessageDecoder.PlayerNetData pd : data.players()) {
      Player p = new Player(pd.id(), pd.name());
      p.setPosition(new Vector2D(pd.x(), pd.y()));
      p.setVelocity(new Vector2D(pd.vx(), pd.vy()));
      p.setState(PlayerState.values()[pd.stateOrdinal()]);
      p.setFacingDirection(pd.facing());
      p.setInSafeZone(pd.inSafeZone());
      p.setCarriedByPlayerId(pd.carriedBy());
      p.setCarryingPlayerId(pd.carrying());
      updated.add(p);
  }
  this.players = updated;
  ```

**Outcome:** After this, `LocalGameState.getPlayers()` returns all players every frame.

---

### Phase 2: Expand `SpriteManager` to Load All 8 Sprites

**File:** [SpriteManager.java](file:///c:/Users/James/Desktop/codez/cods3rdyr/cmsc137/identity-crisis/src/main/java/com/identitycrisis/client/render/SpriteManager.java)

**What:** Currently loads players 1–4. Expand to 1–8.

- [ ] Change `loadAll()` loop from `i <= 4` to `i <= 8`:
  ```java
  for (int i = 1; i <= 8; i++) { ... }
  ```

**Outcome:** All 8 player sprite sets are available in cache as `player_{1..8}_{idle,walk,death}`.

---

### Phase 3: Send `myPlayerId` from Server to Client

**What:** The client needs to know which player ID it controls so it can distinguish "me" from "remote players." This is already sent in `S_GAME_STATE` as `controlledPlayerId` and stored as `LocalGameState.controlledPlayerId`. We also need `myPlayerId` set once at connection time.

**Files:**
- [ServerMessageRouter.java](file:///c:/Users/James/Desktop/codez/cods3rdyr/cmsc137/identity-crisis/src/main/java/com/identitycrisis/client/net/ServerMessageRouter.java)
- [LocalGameState.java](file:///c:/Users/James/Desktop/codez/cods3rdyr/cmsc137/identity-crisis/src/main/java/com/identitycrisis/client/game/LocalGameState.java)

- [ ] In `ServerMessageRouter.route()` → `S_GAME_STATE` branch, after `updateFromSnapshot()`, set `myPlayerId` from `controlledPlayerId` **if not already set**:
  ```java
  case S_GAME_STATE -> {
      MessageDecoder.GameStateData data = decoder.decodeGameState();
      localGameState.updateFromSnapshot(data);
      if (localGameState.getMyPlayerId() == 0) {
          localGameState.setMyPlayerId(data.controlledPlayerId());
      }
  }
  ```

**Outcome:** `LocalGameState.getMyPlayerId()` returns this client's player ID.

---

### Phase 4: Render All Remote Players in `GameArena`

**File:** [GameArena.java](file:///c:/Users/James/Desktop/codez/cods3rdyr/cmsc137/identity-crisis/src/main/java/com/identitycrisis/client/scene/GameArena.java)

This is the core change. Currently `drawPlayer()` draws one hardcoded sprite. We need to:

#### 4A. Store remote player animation state

- [ ] Add a map to track per-player animation state:
  ```java
  /** Per-remote-player animation state (keyed by playerId). */
  private final java.util.Map<Integer, RemotePlayerAnim> remoteAnims = new java.util.HashMap<>();

  private static class RemotePlayerAnim {
      int animFrame = 0;
      double animTimer = 0;
      boolean facingLeft = false;
      boolean isMoving = false;
      double lastX, lastY;
  }
  ```

#### 4B. Sync local player position from server

- [ ] In `update(dt)`, when a server snapshot is available, update `playerX`/`playerY` from the server's authoritative position for **my player**, and update remote player animation states:
  ```java
  // After existing syncRoundStateFromServer() call:
  if (sceneManager != null && sceneManager.getLocalGameState() != null
          && sceneManager.getLocalGameState().hasReceivedSnapshot()) {
      LocalGameState lgs = sceneManager.getLocalGameState();
      int myId = lgs.getMyPlayerId();
      for (Player p : lgs.getPlayers()) {
          if (p.getPlayerId() == myId) continue; // skip self — local controls
          RemotePlayerAnim ra = remoteAnims.computeIfAbsent(
              p.getPlayerId(), k -> new RemotePlayerAnim());
          double dx = p.getPosition().x() - ra.lastX;
          double dy = p.getPosition().y() - ra.lastY;
          ra.isMoving = (Math.abs(dx) > 0.1 || Math.abs(dy) > 0.1);
          if (dx < -0.1) ra.facingLeft = true;
          if (dx > 0.1)  ra.facingLeft = false;
          ra.lastX = p.getPosition().x();
          ra.lastY = p.getPosition().y();
          ra.animTimer += dt;
          if (ra.animTimer >= FRAME_DURATION) {
              ra.animTimer -= FRAME_DURATION;
              int frames = ra.isMoving ? WALK_FRAMES : IDLE_FRAMES;
              ra.animFrame = (ra.animFrame + 1) % frames;
          }
      }
  }
  ```

#### 4C. Create a generalised `drawPlayerSprite()` method

- [ ] Refactor `drawPlayer()` into `drawPlayerSprite(gc, viewW, viewH, worldX, worldY, spriteIdx, animFrame, isMoving, facingLeft)`:
  ```java
  private void drawPlayerSprite(GraphicsContext gc, double viewW, double viewH,
                                double worldX, double worldY,
                                int spriteIdx, int frame, boolean moving, boolean left) {
      double screenX, screenY, displaySize;
      if (mapManager != null && mapManager.getWorldWidth() > 0) {
          double scale = mapManager.getScale(viewW, viewH);
          screenX = mapManager.worldToScreenX(worldX, viewW, viewH);
          screenY = mapManager.worldToScreenY(worldY, viewW, viewH);
          displaySize = SPRITE_NATIVE * scale;
      } else {
          screenX = worldX; screenY = worldY;
          displaySize = SPRITE_NATIVE * 3.0;
      }
      String key = (moving ? "player_" + spriteIdx + "_walk"
                           : "player_" + spriteIdx + "_idle");
      Image sheet = spriteManager.get(key);
      int maxFrames = moving ? WALK_FRAMES : IDLE_FRAMES;
      int f = Math.min(frame, maxFrames - 1);
      double srcX = f * SPRITE_NATIVE;
      double drawX = screenX - displaySize / 2.0;
      double drawY = screenY - displaySize / 2.0;

      if (sheet != null) {
          gc.save();
          gc.setImageSmoothing(false);
          if (left) {
              gc.translate(drawX + displaySize, drawY);
              gc.scale(-1, 1);
              gc.drawImage(sheet, srcX, 0, SPRITE_NATIVE, SPRITE_NATIVE,
                           0, 0, displaySize, displaySize);
          } else {
              gc.drawImage(sheet, srcX, 0, SPRITE_NATIVE, SPRITE_NATIVE,
                           drawX, drawY, displaySize, displaySize);
          }
          gc.restore();
      }
  }
  ```

#### 4D. Update `render()` to draw all players

- [ ] Replace the single `drawPlayer(gc, w, h)` call in `render()` with a loop:
  ```java
  // 3. Player sprites (local + remote)
  drawAllPlayers(gc, w, h);
  ```
- [ ] Implement `drawAllPlayers()`:
  ```java
  private void drawAllPlayers(GraphicsContext gc, double viewW, double viewH) {
      LocalGameState lgs = (sceneManager != null) ? sceneManager.getLocalGameState() : null;
      if (lgs != null && lgs.hasReceivedSnapshot() && lgs.getPlayers() != null) {
          int myId = lgs.getMyPlayerId();
          for (Player p : lgs.getPlayers()) {
              if (p.getState() == PlayerState.ELIMINATED) continue;
              int spriteIdx = ((p.getPlayerId() - 1) % 8) + 1;
              if (p.getPlayerId() == myId) {
                  // Local player — use client-side position for responsiveness
                  drawPlayerSprite(gc, viewW, viewH,
                      playerX, playerY, spriteIdx,
                      animFrame, isMoving, facingLeft);
              } else {
                  // Remote player — use server position
                  RemotePlayerAnim ra = remoteAnims.get(p.getPlayerId());
                  if (ra == null) ra = new RemotePlayerAnim();
                  drawPlayerSprite(gc, viewW, viewH,
                      p.getPosition().x(), p.getPosition().y(),
                      spriteIdx, ra.animFrame, ra.isMoving, ra.facingLeft);
              }
          }
      } else {
          // Offline / no snapshot — draw local player with sprite 1 (legacy)
          drawPlayerSprite(gc, viewW, viewH,
              playerX, playerY, 1, animFrame, isMoving, facingLeft);
      }
  }
  ```

#### 4E. Draw player name tags

- [ ] Add a name tag above each player sprite (small `Press Start 2P` label):
  ```java
  // Inside drawPlayerSprite(), after drawing the image:
  // (pass displayName as parameter)
  gc.setFont(loadFont("Press Start 2P", 5));
  gc.setTextAlign(TextAlignment.CENTER);
  gc.setFill(Color.web("#e8dfc4"));
  gc.fillText(displayName, screenX, drawY - 4);
  ```

**Outcome:** All connected players visible with unique sprites, animated, and name-tagged.

---

### Phase 5: Lobby UI — Show Sprite Previews Per Player

**File:** [LobbyScene.java](file:///c:/Users/James/Desktop/codez/cods3rdyr/cmsc137/identity-crisis/src/main/java/com/identitycrisis/client/scene/LobbyScene.java)

**What:** Replace the abstract donut ring with a per-player list showing their assigned sprite idle frame.

- [ ] Add a `SpriteManager` field and load it in `createScene()`.
- [ ] Replace the donut section with a horizontal `HBox` of player cards (up to 8).
- [ ] Each card shows:
  - The player's `Idle.png` frame 0, rendered at 2× or 3× native.
  - The player name (from `LobbyStateData.names[i]`).
  - A `"YOU"` badge on the local player's card.
  - Ready state indicator (checkmark or "READY" label).
- [ ] Update `setPlayerCount()` → `setLobbyPlayers(String[] names, boolean[] ready, int myIndex)`.
- [ ] Wire the lobby state callback in `CreateOrJoinScene.onCreateClicked()` and `JoinRoomScene.onJoinClicked()` to call the new method.

**Outcome:** Each player in the lobby sees all connected players with their actual sprite preview.

---

### Phase 6: Lobby → GameArena Transition (Host "Start Game")

**Files:**
- [LobbyScene.java](file:///c:/Users/James/Desktop/codez/cods3rdyr/cmsc137/identity-crisis/src/main/java/com/identitycrisis/client/scene/LobbyScene.java)
- [ServerMessageRouter.java](file:///c:/Users/James/Desktop/codez/cods3rdyr/cmsc137/identity-crisis/src/main/java/com/identitycrisis/client/net/ServerMessageRouter.java)

**What:** When the host clicks "Start Game", the server's `LobbyManager.handleReady()` starts the game loop automatically (already implemented for all-ready). We need:

- [ ] **Host "Start Game" button** sends `C_READY` via `GameClient.sendReady()` — the server already treats "all ready" as the start trigger.
- [ ] **All clients** need a callback for `S_GAME_STATE` (first snapshot received = game started). Add `onGameStarted` callback in `ServerMessageRouter`:
  ```java
  case S_GAME_STATE -> {
      MessageDecoder.GameStateData data = decoder.decodeGameState();
      localGameState.updateFromSnapshot(data);
      if (localGameState.getMyPlayerId() == 0) {
          localGameState.setMyPlayerId(data.controlledPlayerId());
      }
      // Fire game-started callback once (first snapshot = game begin)
      if (onGameStarted != null) {
          Platform.runLater(onGameStarted);
          onGameStarted = null; // fire only once
      }
  }
  ```
- [ ] **Wire the callback** in `CreateOrJoinScene` and `JoinRoomScene` to navigate to `LoadingScene` → `GameArena`:
  ```java
  router.setOnGameStarted(() -> sceneManager.showLoading());
  ```

**Outcome:** Host clicks Start → server starts game loop → first snapshot triggers all clients to transition to GameArena.

---

### Phase 7: Send Client Input to Server from GameArena

**File:** [GameArena.java](file:///c:/Users/James/Desktop/codez/cods3rdyr/cmsc137/identity-crisis/src/main/java/com/identitycrisis/client/scene/GameArena.java)

**What:** Currently GameArena moves the local player purely client-side. We need to **also** send input to the server so the server can broadcast position to other clients.

- [ ] In `update(dt)`, after computing `input`, send it to the server:
  ```java
  GameClient gc = (sceneManager != null) ? sceneManager.getGameClient() : null;
  if (gc != null && gc.isConnected()) {
      gc.sendInput(input.up(), input.down(), input.left(), input.right(),
                   input.carry(), input.throwAction());
  }
  ```

**Outcome:** Server receives this client's input → applies via `PhysicsEngine` → broadcasts updated position to all other clients.

---

## File Change Summary

| File | Change |
|---|---|
| [LocalGameState.java](file:///c:/Users/James/Desktop/codez/cods3rdyr/cmsc137/identity-crisis/src/main/java/com/identitycrisis/client/game/LocalGameState.java) | Populate `players` list from snapshot |
| [SpriteManager.java](file:///c:/Users/James/Desktop/codez/cods3rdyr/cmsc137/identity-crisis/src/main/java/com/identitycrisis/client/render/SpriteManager.java) | Load sprites 1–8 |
| [ServerMessageRouter.java](file:///c:/Users/James/Desktop/codez/cods3rdyr/cmsc137/identity-crisis/src/main/java/com/identitycrisis/client/net/ServerMessageRouter.java) | Set `myPlayerId`, fire `onGameStarted` on first snapshot |
| [GameArena.java](file:///c:/Users/James/Desktop/codez/cods3rdyr/cmsc137/identity-crisis/src/main/java/com/identitycrisis/client/scene/GameArena.java) | Render all players, send input to server, remote anim state |
| [LobbyScene.java](file:///c:/Users/James/Desktop/codez/cods3rdyr/cmsc137/identity-crisis/src/main/java/com/identitycrisis/client/scene/LobbyScene.java) | Replace donut with per-player sprite cards |
| [CreateOrJoinScene.java](file:///c:/Users/James/Desktop/codez/cods3rdyr/cmsc137/identity-crisis/src/main/java/com/identitycrisis/client/scene/CreateOrJoinScene.java) | Wire `onGameStarted` callback, send `C_READY` |
| [JoinRoomScene.java](file:///c:/Users/James/Desktop/codez/cods3rdyr/cmsc137/identity-crisis/src/main/java/com/identitycrisis/client/scene/JoinRoomScene.java) | Wire `onGameStarted` callback, send `C_READY` |

---

## What We Are NOT Changing (No Scope Creep)

- ❌ Server game logic (RoundManager, SafeZoneManager, etc.) — already working
- ❌ Network protocol — existing `S_GAME_STATE` carries all needed data
- ❌ Player collision / physics — server-side, already implemented
- ❌ Carry/throw mechanics — out of scope for this task
- ❌ Chat system — out of scope
- ❌ Any server-side files (except if needed for sprite index in lobby)

---

## End-to-End Flow (After Implementation)

```
1. Player opens game → Main Menu
2. Host clicks "Create Game"
   → EmbeddedServer starts
   → GameClient connects to localhost
   → sendJoinRequest("Host")
   → Server broadcasts S_LOBBY_STATE
   → LobbyScene shows host's sprite card (sprite 1)

3. Remote player clicks "Join Game" → enters room code
   → GameClient connects to host IP:port
   → sendJoinRequest("PlayerName")
   → Server broadcasts S_LOBBY_STATE (2 players)
   → All clients' LobbyScene updates with both sprite cards

4. (Repeat for players 3, 4)

5. Host clicks "Start Game"
   → GameClient.sendReady()
   → Server: all clients marked ready → startGame()
   → Server starts ServerGameLoop → first S_GAME_STATE broadcast
   → onGameStarted callback fires on all clients
   → All clients transition: LoadingScene → GameArena

6. In GameArena:
   → Each client sends C_PLAYER_INPUT every frame
   → Server broadcasts S_GAME_STATE at 60 tps with all positions
   → Each client renders all players with correct sprites
   → Local player uses client-side position (responsive)
   → Remote players use server-authoritative position (synced)
```

---

## Implementation Order

> [!TIP]
> Start with Phase 1–3 (data plumbing), then Phase 4 (rendering), then Phase 7 (input sending), then Phase 5–6 (lobby polish). Each phase is independently testable.

| Order | Phase | Risk | Effort |
|---|---|---|---|
| 1st | Phase 1: Wire `LocalGameState.players` | Low | Small |
| 2nd | Phase 2: Expand SpriteManager to 8 | Low | Tiny |
| 3rd | Phase 3: Set `myPlayerId` | Low | Small |
| 4th | Phase 4: Render all players in GameArena | Medium | **Large** |
| 5th | Phase 7: Send input to server | Low | Small |
| 6th | Phase 6: Game-start transition | Medium | Medium |
| 7th | Phase 5: Lobby sprite previews | Low | Medium |
