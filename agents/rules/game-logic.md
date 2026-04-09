## 9. Game Loop Design

### 9.1 Server Game Loop (Fixed Timestep)

```
TARGET: 60 ticks per second (16.67ms per tick)

while (running):
    long tickStart = System.nanoTime()
    
    // 1. PROCESS INPUTS
    while (!inputQueue.isEmpty()):
        QueuedInput qi = inputQueue.poll()
        int controlledPlayer = gameState.getControlMap().get(qi.clientId)
        boolean reversed = (gameState.getActiveChaosEvent() == REVERSED_CONTROLS)
        physics.applyInput(gameState, controlledPlayer, qi.flags, reversed)
    
    // 2. UPDATE
    double dt = 1.0 / TICK_RATE   // fixed dt, not wall-clock delta
    physics.step(gameState, dt)
    collisions.resolve(gameState)
    carryManager.tick(dt)
    safeZoneManager.updateOccupancy()
    chaosEventManager.tick(dt)
    roundManager.tick(dt)
    // roundManager internally calls eliminationManager.evaluate() at phase transitions
    
    // 3. BROADCAST
    for each ClientConnection client:
        GameStateSnapshot snapshot = buildSnapshotForClient(client)
        // snapshot has per-client safe zones and controlled player ID
        encode snapshot and send
    
    // 4. SLEEP
    long elapsed = System.nanoTime() - tickStart
    long sleepNs = TICK_DURATION_NS - elapsed
    if (sleepNs > 0):
        Thread.sleep(sleepNs / 1_000_000, (int)(sleepNs % 1_000_000))
```

### 9.2 Client Game Loop (AnimationTimer)

```
AnimationTimer.handle(long now):
    if (lastFrameTime == 0): lastFrameTime = now; return
    double dt = (now - lastFrameTime) / 1_000_000_000.0
    lastFrameTime = now
    
    // 1. INPUT
    InputSnapshot raw = inputManager.snapshot()
    InputSnapshot modified = applyChaosModifications(raw)
    // applyChaosModifications: if localGameState.getActiveChaos() == REVERSED_CONTROLS,
    //   swap up↔down and left↔right
    
    // 2. SEND
    gameClient.sendInput(modified.up(), modified.down(), modified.left(),
                         modified.right(), modified.carry(), modified.throwAction())
    
    // 3. RENDER
    renderer.render(localGameState, dt)
    // localGameState was already updated by the network reader thread
```

---

## 10. Round State Machine

```
                    ┌─────────────┐
                    │    LOBBY    │  (waiting for MIN_PLAYERS + all ready)
                    └──────┬──────┘
                           │ all ready
                    ┌──────▼──────┐
             ┌─────►│  COUNTDOWN  │  (3 seconds, players frozen)
             │      └──────┬──────┘
             │             │ timer expires
             │      ┌──────▼──────┐
             │      │   ACTIVE    │  (ROUND_DURATION_SECONDS, players move)
             │      │             │  chaos events may trigger here
             │      └──────┬──────┘
             │             │ timer expires
             │      ┌──────▼──────┐
             │      │  ROUND_END  │  (instant — evaluate safe zone occupancy)
             │      └──────┬──────┘
             │             │ evaluate eliminations
             │      ┌──────▼──────┐
             │      │ ELIMINATION │  (ELIMINATION_DISPLAY_SECONDS — show who died)
             │      └──────┬──────┘
             │             │
             │      ┌──────▼──────┐
             │      │ alive > 1?  │
             │      └──┬───────┬──┘
             │    yes  │       │ no
             └─────────┘  ┌────▼─────┐
                          │ GAME_OVER│
                          └──────────┘
```

### Transition Logic in `RoundManager.tick(dt)`:

```
switch (gameState.getPhase()):
    case LOBBY:
        // Do nothing — LobbyManager handles this externally
        // When lobby signals start → transitionTo(COUNTDOWN), setRoundNumber(1)
    
    case COUNTDOWN:
        gameState.setRoundTimer(gameState.getRoundTimer() - dt)
        if timer <= 0:
            transitionTo(ACTIVE)
            gameState.setRoundTimer(ROUND_DURATION_SECONDS)
            chaosEventManager.resetForNewRound()
    
    case ACTIVE:
        gameState.setRoundTimer(gameState.getRoundTimer() - dt)
        if timer <= 0:
            transitionTo(ROUND_END)
    
    case ROUND_END:
        // Immediate evaluation
        List<Integer> eliminated = eliminationManager.evaluateEliminations()
        // Send PlayerEliminated messages
        transitionTo(ELIMINATION)
        gameState.setRoundTimer(ELIMINATION_DISPLAY_SECONDS)
    
    case ELIMINATION:
        gameState.setRoundTimer(gameState.getRoundTimer() - dt)
        if timer <= 0:
            if eliminationManager.isGameOver():
                transitionTo(GAME_OVER)
                // Send GameOver message
            else:
                // Next round
                gameState.setRoundNumber(gameState.getRoundNumber() + 1)
                startNewRound()  // spawn new safe zone, reset positions
                transitionTo(COUNTDOWN)
                gameState.setRoundTimer(COUNTDOWN_SECONDS)
    
    case GAME_OVER:
        // Do nothing. Game is done. Server can accept restart commands later.
```

---

## 11. Chaos Event System

### 11.1 Scheduling

Each round during ACTIVE phase, **one** chaos event is scheduled:

```
scheduledTriggerTime = random(CHAOS_EVENT_MIN_DELAY, CHAOS_EVENT_MAX_DELAY)
elapsedInRound = 0
```

When `elapsedInRound >= scheduledTriggerTime`, the event fires.

### 11.2 Event Types and Their Implementation

#### F06: `REVERSED_CONTROLS`
- **Server:** Sets `gameState.setActiveChaosEvent(REVERSED_CONTROLS)`. Broadcasts in snapshot.
- **Client:** `ClientGameLoop.applyChaosModifications()` swaps `up↔down` and `left↔right` in the `InputSnapshot` before sending. The server processes input as-is — the inversion already happened client-side.
- **Duration:** `CHAOS_EVENT_DURATION` seconds.

#### F07: `CONTROL_SWAP`
- **Server:** `ChaosEventManager.applyControlSwap()` shuffles `gameState.getControlMap()`. Each client now maps to a different player ID. The shuffle must ensure **no client controls its own player** (derangement).
- **Client:** Receives `controlledPlayerId` in `S_GAME_STATE` or `S_CONTROL_SWAP`. Client input is still sent normally, but the server applies it to the swapped player. Client renderer should highlight which player it now controls.
- **Duration:** `CHAOS_EVENT_DURATION` seconds. On expiry, `revertControlSwap()` restores identity mapping.

#### F08: `FAKE_SAFE_ZONES`
- **Server:** Sets chaos flag. In `broadcastState()`, calls `safeZoneManager.generateClientSafeZones(clientId, true)` which returns `1 true + FAKE_SAFE_ZONE_COUNT decoys` in random order.
- **Client:** Receives a list of zones and renders ALL of them identically. Has NO way to distinguish real from fake.
- **Duration:** `CHAOS_EVENT_DURATION` seconds. After expiry, clients receive only the true zone again.
- **IMPORTANT:** Decoy positions must be generated per-client (different clients see different decoys) to prevent trivial elimination of fakes by comparing screens.

### 11.3 Only One Chaos Event Per Round

Keep it simple. One event per round. No overlapping events. Chaos events only fire during `ACTIVE` phase and auto-expire after duration or when the round ends (whichever comes first).

---

## 12. Carry & Throw Mechanic

### 12.1 State Diagram

```
IDLE ──(press E near target)──► CARRYING
  ▲                                │
  │                           (press Q)
  │                                │
  └──────── THROWN ◄───────────────┘
            (stun wears off → IDLE)
```

### 12.2 Server-Side Logic (`CarryManager`)

**Initiate Carry (E pressed):**
```
tryCarry(carrierPlayerId):
    carrier = gameState.getPlayerById(carrierPlayerId)
    if carrier.state != ALIVE: return false
    if carrier is already carrying someone: return false
    
    targetId = findNearestCarryTarget(carrierPlayerId)
    if targetId == -1: return false  // nobody in range
    
    target = gameState.getPlayerById(targetId)
    if target.state != ALIVE: return false
    if target is being carried: return false
    
    // Initiate carry
    carrier.setState(CARRYING)
    carrier.setCarryingPlayerId(targetId)
    target.setState(CARRIED)
    target.setCarriedByPlayerId(carrierPlayerId)
    gameState.getActiveCarries().add(new CarryState(carrierPlayerId, targetId))
    return true
```

**Throw (Q pressed):**
```
throwCarried(carrierPlayerId):
    carrier = gameState.getPlayerById(carrierPlayerId)
    carriedId = carrier.getCarryingPlayerId()
    if carriedId == -1: return  // not carrying anyone
    
    carried = gameState.getPlayerById(carriedId)
    
    // Calculate throw velocity based on carrier's facing direction
    Vector2D throwDir = facingToVector(carrier.getFacingDirection())
    carried.setVelocity(throwDir.multiply(THROW_SPEED))
    
    // Release carry
    carrier.setState(ALIVE)
    carrier.setCarryingPlayerId(-1)
    carried.setState(ALIVE)  // temporarily — will be overridden if stunned
    carried.setCarriedByPlayerId(-1)
    // carried gets a stun timer (THROW_STUN_SECONDS) — cannot input during stun
    
    // Remove from activeCarries list
```

**Tick (every frame while carrying):**
```
tick(dt):
    for each CarryState cs in gameState.getActiveCarries():
        carrier = getPlayerById(cs.carrierPlayerId)
        carried = getPlayerById(cs.carriedPlayerId)
        // Lock carried player's position to carrier's position + small offset above
        carried.setPosition(carrier.getPosition().add(new Vector2D(0, -PLAYER_RADIUS)))
        carried.setVelocity(Vector2D.zero())
```

### 12.3 Safety Block Rule

In `SafeZoneManager.updateOccupancy()`:
```
for each alive player:
    if player is within trueSafeZone radius:
        if player.getState() == CARRYING:
            player.setInSafeZone(false)  // CANNOT be safe while carrying
        else if player.getState() == CARRIED:
            player.setInSafeZone(false)  // CANNOT be safe while being carried
        else:
            player.setInSafeZone(true)
    else:
        player.setInSafeZone(false)
```

---

## 13. Safe Zone Logic

### 13.1 Spawning

At the start of each round (`startNewRound()` in `RoundManager`):

```
SafeZoneManager.spawnSafeZone():
    position = randomSafePosition()
    // randomSafePosition(): 
    //   x = random(SAFE_ZONE_MIN_MARGIN, ARENA_WIDTH - SAFE_ZONE_MIN_MARGIN)
    //   y = random(SAFE_ZONE_MIN_MARGIN, ARENA_HEIGHT - SAFE_ZONE_MIN_MARGIN)
    //   Ensure position is not overlapping an obstacle.
    //   Retry up to N times if needed.
    gameState.setTrueSafeZone(new SafeZone(position, SAFE_ZONE_RADIUS))
```

### 13.2 Occupancy During Warmup (Rounds 1–2)

- Safe zone has **unlimited capacity**.
- All players inside at `ROUND_END` survive.
- Players outside are eliminated (in theory — if everyone's good, nobody dies).

### 13.3 Occupancy During Elimination (Rounds 3+)

- Safe zone has capacity `n - 1` (alive count minus one).
- At `ROUND_END`, server counts players inside the zone.
- If ≤ `n-1` players are inside, all inside survive. Among those outside, the player **farthest from the safe zone center** is eliminated (tiebreak).
- Exactly **one** elimination per round is guaranteed. If all players somehow end up inside (shouldn't happen with proper radius sizing), eliminate the one who entered last or is closest to the edge.

### 13.4 Safe Zone Radius Scaling (Optional Enhancement)

For elimination rounds, the safe zone radius could shrink based on alive count:

```
effectiveRadius = SAFE_ZONE_RADIUS * (aliveCount - 1) / aliveCount
```

This naturally makes the zone fit fewer people. Implement if time allows; otherwise keep a fixed radius and rely on the server counting occupants.

### 13.5 Decoy Generation for F08

```
SafeZoneManager.generateClientSafeZones(clientId, fakeChaosActive):
    List<SafeZone> result = new ArrayList<>()
    result.add(gameState.getTrueSafeZone())  // always include the real one
    
    if (fakeChaosActive):
        Random rng = new Random(clientId * roundNumber)  // per-client seed
        for i in 0..FAKE_SAFE_ZONE_COUNT-1:
            decoyPos = randomSafePosition()  // random, not overlapping true zone
            result.add(new SafeZone(decoyPos, SAFE_ZONE_RADIUS))
        Collections.shuffle(result, rng)  // shuffle so true zone isn't always first
    
    return result
```