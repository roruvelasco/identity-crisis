# Round System Agent

> **For LLM agents.** Complete specification for the round-based elimination system
> in Identity Crisis. Covers round types, safe-zone selection from the TMX map,
> chaos events, and elimination logic.
>
> **Reference docs:** `agents/AGENTS.md`, `agents/rules/game-logic.md`,
> `agents/BACKEND_IMPL.md`, `agents/SPRITE_MOVEMENT.md`.

---

## 1. Overview

The game progresses through sequential rounds. Each round has one of two types
and one active chaos event. Players are eliminated until one remains.

| Round # | Type | Safe Zones Spawned | Elimination Rule |
|---------|------|--------------------|------------------|
| 1–2 | **Timer-based** | `P` zones (P = total alive players) | All players NOT in a safe zone when the timer expires **die** |
| 3+ | **Safe-zone-based** | `N − 1` zones (N = alive players) | The **last player** who fails to claim a zone **dies** |

---

## 2. Safe-Zone Spots from the TMX Map

`ArenaMap.tmx` defines **8 safe-zone regions** via dedicated tile layers named
`safezone1` through `safezone8`. Each layer's non-zero tiles form a bounding
rectangle in world-pixel space.

`MapManager.extractSafeZones()` already parses these into a
`List<SafeZoneRect>` (see `MapManager.java:566–601`):

```java
public record SafeZoneRect(int id, double x, double y, double width, double height) {
    public boolean contains(double px, double py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }
}
```

The 8 spots (IDs 1–8) are the **pool** from which active round zones are chosen.

### 2.1 Server Access to Safe-Zone Spots

The server currently uses a `SafeZone(Vector2D position, double radius)` circle
model. To support map-based rectangular zones, the server must know the 8 spots.

**Option A (recommended):** Hard-code the 8 safe-zone rectangles as constants in
`GameConfig` or a new `SafeZoneSpots` utility, derived from the TMX at build time.
This avoids adding XML parsing to the server.

**Option B:** Add a lightweight TMX parser on the server side that extracts only
the safezone layers (reuse `MapManager.extractSafeZones` logic).

Either way, the data is:

```java
/** All 8 candidate safe-zone spots from ArenaMap.tmx. */
public record SafeZoneSpot(int id, double x, double y, double width, double height) {
    /** Centre point of this spot. */
    public Vector2D center() {
        return new Vector2D(x + width / 2.0, y + height / 2.0);
    }
    /** Whether a world-pixel point is inside this rect. */
    public boolean contains(double px, double py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }
}
```

> [!IMPORTANT]
> The spot coordinates are in **world-pixel space** (16 px per tile), matching
> `MapManager`'s coordinate system.  The actual values are printed by
> `MapManager` on load (`[MapManager] Safe zone N at world (X,Y) size W×H`).
> Run the client once to capture them, then hard-code in `SafeZoneSpots`.

---

## 3. Round Lifecycle

Each round follows this state machine (same phases as `game-logic.md §10`):

```
COUNTDOWN (3s, players frozen)
    │
    ▼
ACTIVE (ROUND_DURATION_SECONDS, players move)
    │  chaos events cycle every CHAOS_EVENT_DURATION seconds
    ▼
ROUND_END (instant — evaluate who dies)
    │
    ▼
ELIMINATION (3s — display who died)
    │
    ▼
alive > 1? ──yes──► next round (COUNTDOWN)
    │
    no
    ▼
GAME_OVER
```

### 3.1 Round Start (`startNewRound` in `RoundManager`)

At the start of every round:

1. **Determine round type:**
   ```java
   boolean isTimerRound = (gameState.getRoundNumber() <= GameConfig.WARMUP_ROUNDS);
   ```

2. **Choose active safe zones from the 8 TMX spots:**
   ```java
   int aliveCount = gameState.getAliveCount();
   int zoneCount  = isTimerRound ? aliveCount : (aliveCount - 1);

   // Clamp to available spots
   zoneCount = Math.min(zoneCount, SafeZoneSpots.ALL.size()); // max 8

   // Shuffle and pick
   List<SafeZoneSpot> pool = new ArrayList<>(SafeZoneSpots.ALL);
   Collections.shuffle(pool, rng);
   List<SafeZoneSpot> activeZones = pool.subList(0, zoneCount);
   gameState.setActiveRoundZones(activeZones);
   ```

3. **Start the chaos event cycle:**
   ```java
   chaosEventManager.resetForNewRound();
   ```
   `ChaosEventManager` immediately activates one random enabled event, cycles to
   another every `CHAOS_EVENT_DURATION` seconds, and clears when the round ends.

4. **Reset player positions** (scatter around arena centre).

5. **Reset all `inSafeZone` flags** to `false`.

---

## 4. Safe-Zone Occupancy

### 4.1 Checking Occupancy

`SafeZoneManager.updateOccupancy()` runs every tick during ACTIVE phase.
Instead of a single circular zone, check against the list of active rectangular
zones:

```java
public void updateOccupancy() {
    List<SafeZoneSpot> zones = gameState.getActiveRoundZones();
    if (zones == null || zones.isEmpty()) return;

    for (Player p : gameState.getAlivePlayers()) {
        if (p.getState() == PlayerState.CARRYING
         || p.getState() == PlayerState.CARRIED) {
            p.setInSafeZone(false);
            continue;
        }
        boolean inAny = false;
        for (SafeZoneSpot zone : zones) {
            if (zone.contains(p.getPosition().x(), p.getPosition().y())) {
                inAny = true;
                break;
            }
        }
        p.setInSafeZone(inAny);
    }
}
```

### 4.2 Zone Capacity (Safe-Zone-Based Rounds Only)

For rounds 3+, each rectangular zone has a **capacity of 1 player**. Once a
player is inside a zone, that zone is "claimed" — subsequent players entering
the same zone are **not** considered safe.

Track occupancy per zone:

```java
/** Returns a map of zoneId → first occupant playerId, or empty if unclaimed. */
public Map<Integer, Integer> getZoneOccupants() {
    Map<Integer, Integer> claimed = new LinkedHashMap<>();
    List<SafeZoneSpot> zones = gameState.getActiveRoundZones();

    for (SafeZoneSpot zone : zones) {
        for (Player p : gameState.getAlivePlayers()) {
            if (p.getState() == PlayerState.CARRYING
             || p.getState() == PlayerState.CARRIED) continue;
            if (zone.contains(p.getPosition().x(), p.getPosition().y())) {
                claimed.putIfAbsent(zone.id(), p.getPlayerId());
            }
        }
    }
    return claimed;
}
```

For **timer-based rounds** (1–2), zone capacity is unlimited — any player inside
any active zone is safe.

---

## 5. Elimination Logic

### 5.1 Timer-Based Rounds (1–2)

At `ROUND_END`:

```
for each alive player:
    if NOT inSafeZone:
        eliminate(player)
```

All players outside any active zone die simultaneously. If everyone is inside,
nobody dies (warm-up is forgiving).

### 5.2 Safe-Zone-Based Rounds (3+)

At `ROUND_END`:

There are `N − 1` zones for `N` alive players. Each zone fits exactly 1 player.
The player who **fails to claim a zone** is eliminated.

```java
public List<Integer> evaluateEliminations() {
    List<Integer> eliminated = new ArrayList<>();
    boolean isTimerRound = gameState.getRoundNumber() <= GameConfig.WARMUP_ROUNDS;

    if (isTimerRound) {
        // Timer round: everyone outside dies
        for (Player p : gameState.getAlivePlayers()) {
            if (!p.isInSafeZone()) {
                eliminatePlayer(p.getPlayerId());
                eliminated.add(p.getPlayerId());
            }
        }
    } else {
        // Safe-zone round: exactly one dies — the unclaimed player
        Map<Integer, Integer> claimed = getZoneOccupants();
        Set<Integer> safePlayerIds = new HashSet<>(claimed.values());

        List<Player> unsafe = gameState.getAlivePlayers().stream()
            .filter(p -> !safePlayerIds.contains(p.getPlayerId()))
            .toList();

        if (unsafe.isEmpty()) {
            // Edge case: more players than zones but all somehow in zones
            // (shouldn't happen with N-1 zones and capacity 1)
            // Fallback: eliminate player farthest from nearest zone center
            Player farthest = gameState.getAlivePlayers().stream()
                .max(Comparator.comparingDouble(p -> nearestZoneDistance(p)))
                .orElse(null);
            if (farthest != null) {
                eliminatePlayer(farthest.getPlayerId());
                eliminated.add(farthest.getPlayerId());
            }
        } else if (unsafe.size() == 1) {
            // Normal case: exactly one player without a zone
            eliminatePlayer(unsafe.get(0).getPlayerId());
            eliminated.add(unsafe.get(0).getPlayerId());
        } else {
            // Multiple players without zones: eliminate the one farthest
            // from the nearest zone center (tiebreak)
            Player farthest = unsafe.stream()
                .max(Comparator.comparingDouble(p -> nearestZoneDistance(p)))
                .orElse(null);
            if (farthest != null) {
                eliminatePlayer(farthest.getPlayerId());
                eliminated.add(farthest.getPlayerId());
            }
        }
    }
    return eliminated;
}

private double nearestZoneDistance(Player p) {
    return gameState.getActiveRoundZones().stream()
        .mapToDouble(z -> p.getPosition().distanceTo(z.center()))
        .min().orElse(Double.MAX_VALUE);
}
```

---

## 6. Chaos Events

Chaos events are server-driven during `ACTIVE` phase. One enabled event is active
at a time for `CHAOS_EVENT_DURATION` seconds, then `ChaosEventManager` deactivates
it and immediately activates another random enabled event. Events do not overlap.

### 6.1 Event Pool

| Event | ID | Effect |
|-------|----|--------|
| **Reverse Controls** | `REVERSED_CONTROLS` | Client swaps up↔down and left↔right in `InputSnapshot` before sending. Server processes as-is. (See AGENTS.md rule 20) |
| **Fake Safe Zones** | `FAKE_SAFE_ZONES` | Clients receive decoy zones mixed with real ones. Each client gets different decoy positions (seeded by `clientId + roundNumber`). Server uses only real zones for occupancy checks. |

### 6.2 Activation / Deactivation

```java
// In RoundManager, on transition COUNTDOWN → ACTIVE:
chaosEventManager.resetForNewRound();

// In RoundManager, on transition ACTIVE → ROUND_END:
chaosEventManager.clearActiveEvent();
```

### 6.3 Fake Safe Zones Detail

When `FAKE_SAFE_ZONES` is active:

- **Server** still uses only the real `activeRoundZones` for occupancy.
- **Client broadcast** (`broadcastState`): for each client, generate decoy
  zones in addition to real ones. Use `SafeZoneManager.generateClientSafeZones()`.
- Decoys should look identical to real zones (same visual, same size).
- The number of decoys per client: `GameConfig.FAKE_SAFE_ZONE_COUNT` (default 3).

---

## 7. Data Model Changes

### 7.1 `GameState` — New Fields

```java
/** The safe-zone spots active this round (subset of the 8 TMX spots). */
private List<SafeZoneSpot> activeRoundZones = new ArrayList<>();

// Getter/setter
public List<SafeZoneSpot> getActiveRoundZones() { return activeRoundZones; }
public void setActiveRoundZones(List<SafeZoneSpot> zones) { this.activeRoundZones = zones; }
```

### 7.2 `GameConfig` — New/Modified Constants

```java
/** Total safe-zone spots available in the TMX map. */
public static final int TOTAL_SAFE_ZONE_SPOTS = 8;
```

`WARMUP_ROUNDS` already exists as `2`.

### 7.3 `SafeZoneSpots` — New Utility Class

```java
package com.identitycrisis.shared.model;

import com.identitycrisis.shared.util.Vector2D;
import java.util.List;

/**
 * The 8 safe-zone rectangles from ArenaMap.tmx, hard-coded.
 * Run the client once to read [MapManager] log output, then paste here.
 */
public final class SafeZoneSpots {
    private SafeZoneSpots() {}

    public record SafeZoneSpot(int id, double x, double y, double w, double h) {
        public Vector2D center() { return new Vector2D(x + w / 2, y + h / 2); }
        public boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    // TODO: Fill in from [MapManager] log output after running client once.
    // Example: Safe zone 1 at world (48,64) size 48×32
    public static final List<SafeZoneSpot> ALL = List.of(
        // new SafeZoneSpot(1, 48, 64, 48, 32),
        // new SafeZoneSpot(2, ...),
        // ... up to 8
    );
}
```

> [!IMPORTANT]
> You **must** run the client once to get the actual coordinates from the
> `[MapManager] Safe zone N at world (X,Y) size W×H` log lines, then populate
> `SafeZoneSpots.ALL`.

---

## 8. Network Protocol Impact

### 8.1 `S_GAME_STATE` Snapshot Changes

The zone data in the game state snapshot changes from a single circle to a list
of rectangles:

```
int zoneCount
  N × ZoneBlock:
    int zoneId
    double x, double y, double width, double height
```

Update `MessageEncoder.encodeGameState` and `MessageDecoder.decodeGameState`
accordingly. The client renderer should highlight these rectangular regions
instead of circular zones.

### 8.2 `S_SAFE_ZONE_UPDATE`

Same change — send rectangular zones instead of circles.

---

## 9. Client Rendering

### 9.1 Active Zone Visualization

`SafeZoneRenderer` should draw the active zones as highlighted rectangular
regions on the map. Since the zones correspond to TMX `safezoneN` layers,
the renderer can:

- Draw a translucent colored overlay on each active zone rect.
- Pulse/animate the overlay to draw player attention.
- During `FAKE_SAFE_ZONES`, render all received zones identically (client
  cannot distinguish real from fake).

### 9.2 HUD Indicators

`HudRenderer` should display:

- Current round number and type ("Timer Round" / "Elimination Round").
- Active chaos event name and icon.
- Number of safe zones remaining (for safe-zone rounds).
- Round timer countdown.

---

## 10. Implementation Checklist

```
1. [ ] Create SafeZoneSpots utility with hard-coded TMX zone rects
2. [ ] Add activeRoundZones field to GameState
3. [ ] Update SafeZoneManager:
       - spawnRoundZones(int count) picks random subset from SafeZoneSpots.ALL
       - updateOccupancy() checks rectangular zones
       - getZoneOccupants() for capacity-1 tracking (rounds 3+)
4. [ ] Update EliminationManager.evaluateEliminations():
       - Timer rounds: all outside die
       - Safezone rounds: exactly 1 unclaimed player dies
5. [ ] Update RoundManager.startNewRound():
       - Compute zone count based on round type
       - Pick random chaos event for entire ACTIVE phase
       - Activate chaos at ACTIVE start, clear at ROUND_END
6. [ ] Update ChaosEventManager:
       - Chaos lasts entire ACTIVE phase (no random delay/duration)
       - clearActiveEvent() at ACTIVE → ROUND_END transition
7. [ ] Update MessageEncoder/Decoder for rectangular zone format
8. [ ] Update SafeZoneRenderer for rectangular zones
9. [ ] Update HudRenderer with round type / chaos event display
```

---

## 11. Example Round Walkthrough

### Round 1 (Timer, 4 players alive)

1. **Start:** 4 zones randomly chosen from 8 spots. Chaos: `REVERSED_CONTROLS`.
2. **ACTIVE (15s):** Players move (controls inverted). All 4 zones visible.
3. **ROUND_END:** Player C is not in any zone → Player C eliminated.
4. **Result:** 3 players remain.

### Round 3 (Safe-zone, 3 players alive)

1. **Start:** 2 zones (3−1) randomly chosen. Chaos: `FAKE_SAFE_ZONES`.
2. **ACTIVE (15s):** Players see 2 real + 3 fake zones each (different fakes per client).
3. **ROUND_END:** Players A and B each claimed a real zone. Player D has no zone → eliminated.
4. **Result:** 2 players remain.

### Round 4 (Safe-zone, 2 players alive)

1. **Start:** 1 zone (2−1). Chaos: `CONTROL_SWAP`.
2. **ACTIVE (15s):** Each player controls the other's character.
3. **ROUND_END:** Player A is in the zone. Player B is not → eliminated.
4. **GAME_OVER:** Player A wins.
