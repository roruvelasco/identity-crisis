# Sprite Movement & Hitbox Convention

## Coordinate Systems — Critical Distinction

| Space        | Unit               | Used by                              |
|--------------|--------------------|--------------------------------------|
| **World px** | 1 world px = 1/16 tile | `playerX/Y`, `MapManager.isSolid()`, hitbox constants |
| **Screen px** | depends on viewport | `worldToScreenX/Y`, rendering only  |

`MapManager.getScale()` returns **screen-px / world-px** — a viewport-dependent
multiplier.  Hitbox constants live in **world space** and must **never** be
multiplied by `getScale()`.

---

## Sprite Sheet Layout

Each player sprite sheet uses **32×32 px frames** (1 frame = 1 column in the PNG).

| Animation | Key              | Frames |
|-----------|------------------|--------|
| Idle      | `player_N_idle`  | 4      |
| Walk      | `player_N_walk`  | 6      |
| Death     | `player_N_death` | varies |

A tile is **16 world px** wide.  The sprite frame is **32 world px** (2 tiles), but
the actual character art occupies only the centre-bottom portion of that frame.

---

## Tight Hitbox

The body pixels sit in the **center-bottom** of the 32×32 frame:

```
 0 ┌────────────────────────────┐
   │      transparent           │  ← no art here
   │       ┌──────┐             │
   │       │ body │             │  ← character body
31 │       └──────┘             │
   └────────────────────────────┘
```

### Constants (in `GameArena.java`) — all in **world pixels**

| Constant      | Value   | Meaning                                               |
|---------------|---------|-------------------------------------------------------|
| `HIT_HALF_W`  |  3.0 px | Half-width of the hitbox  (body ≈ 6 px wide)         |
| `HIT_HALF_H`  |  5.0 px | Half-height of the hitbox (body ≈ 10 px tall)        |
| `HIT_OFS_X`   |  0.0 px | Horizontal offset from sprite-frame centre            |
| `HIT_OFS_Y`   |  4.0 px | Vertical offset — body is in lower half of frame     |

A 1-tile-wide door is 16 world px. With `HIT_HALF_W = 3`, the player's full width is
6 world px, leaving 5 px of clearance on each side — enough to pass through comfortably.

### How it works

`playerX` / `playerY` are the **world-pixel coordinates of the sprite-frame centre**.
The hitbox is displaced by `(HIT_OFS_X, HIT_OFS_Y)` from that point and has
half-extents `(HIT_HALF_W, HIT_HALF_H)`.  All four corners are sampled via
`MapManager.isSolid(worldX, worldY)` which operates in the same world-pixel space.

> [!CAUTION]
> **Do NOT multiply HIT_* constants by `mapManager.getScale()`.**
> `getScale()` converts world→screen; multiplying it into hitbox offsets inflates
> the box by 1.5–2× at typical resolutions, causing players to collide with walls
> they should walk past and blocking passage through narrow corridors.

---

## Collision Flow

```
update(dt)
  └─ isBlocked(newX, playerY, 0)   // probe X axis
  └─ isBlocked(playerX, newY, 0)   // probe Y axis independently (wall sliding)
       └─ computes four AABB corners in world-pixel space (no scale multiply)
       └─ mapManager.isSolid(corner)  // tile-grid lookup: worldX/16 → col, worldY/16 → row
```

---

## floor_2 Collision System (`MapManager.buildCollisionGrid`)

The `floor_2` TMX layer is **dual-role**: it contains both pure floor tiles (walkable)
and wall-edge / ledge tiles (solid).  The distinction comes from whether the tile's
`objectgroup` collision shape covers the tile centre **(8, 8)**.

### Priority order inside `buildCollisionGrid`

```
1. Void (no tile in floor_ground OR floor_2)              → solid
2. Water tile present                                      → solid
3. walls tile with ANY collision shape in tileset          → solid   (tileHasAnyCollision)
4. floor_2 tile with ANY collision shape in tileset        → solid   (tileHasAnyCollision)
5. objects/objects2 tile with ANY collision shape defined  → solid   (tileHasAnyCollision)
6. Otherwise                                               → walkable
```

**Unified rule for all layers:** `tileHasAnyCollision` — a tile is solid if and only if
its tileset defines at least one collision `<objectgroup>`. Tiles with no objectgroup
(door openings, passages, decorative sprites) are always walkable regardless of layer.

### The key method: `tileHasAnyCollision`

```java
private boolean tileHasAnyCollision(int[][] grid, int row, int col) {
    int gid = grid[row][col];
    return gid != 0 && tileCollisionShapes.containsKey(gid);
}
```

Used for **both** `floor_2` and `objects`/`objects2`. A tile is solid if it has
**any** objectgroup defined in the tileset — regardless of shape size or position.

This is the correct approach because:
- **Walkable floor tiles** in `floor_2` have NO objectgroup → `containsKey` = false → walkable ✓
- **Wall-edge tiles** with full shapes → `containsKey` = true → solid ✓
- **Partial-shape edge tiles** (fire-pit ledges `height=7`, treasure borders) → `containsKey` = true → solid ✓

> [!CAUTION]
> Do NOT use the `checkLayerForCollision` centre-check `(8,8)` for `floor_2` or
> `objects`. Tiles like `walls_floor` local id 180 have `height=7` collision boxes —
> the point `(8,8)` falls at `y=8` which is outside `y+height=7`, so the centre
> check silently misses them, letting the player walk through fire pits and chests.

> [!IMPORTANT]
> `floor_2` tiles are still counted as "has any floor" in step 1 regardless of
> whether they carry a collision shape. The void-check gate must pass before step 4
> can run the `tileHasAnyCollision` evaluation.

> [!WARNING]
> The `walls` layer marks **any** non-zero tile as solid (no objectgroup check).
> If you ever add walkable tiles to `walls`, add an objectgroup check matching
> the pattern used in step 4.
