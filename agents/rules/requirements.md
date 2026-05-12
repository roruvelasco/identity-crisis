## 17. Functional Requirements Traceability

| Req | Description | Server Classes | Client Classes |
|---|---|---|---|
| F01 | 4+ players | `GameConfig.MIN_PLAYERS`, `LobbyManager` | `LobbyScene` |
| F02 | Top-down 2D arena | `Arena`, `GameState` | `ArenaRenderer` |
| F03 | Random safe zone per round | `SafeZoneManager.spawnSafeZone()` | `SafeZoneRenderer` |
| F04 | Warm-up rounds 1–2 | `RoundManager.isWarmupRound()`, `EliminationManager` | `HudRenderer` |
| F05 | Elimination rounds 3+ | `EliminationManager.evaluateEliminations()` | `HudRenderer` |
| F06 | Reversed controls chaos | `ChaosEventManager` (flag only) | `ClientGameLoop.applyChaosModifications()` |
| F07 | Control swap chaos | `ChaosEventManager.applyControlSwap()`, `GameState.controlMap` | `ServerMessageRouter` (reads `controlledPlayerId`) |
| F08 | Fake safe zones chaos | `SafeZoneManager.generateClientSafeZones()` | `SafeZoneRenderer` (renders all zones identically) |
| F09 | Carry player | `CarryManager.tryCarry()` | `PlayerRenderer` (carry visual) |
| F10 | Throw player | `CarryManager.throwCarried()`, `PhysicsEngine` | `PlayerRenderer` (throw animation) |
| F11 | Carrier can't be safe | `SafeZoneManager.updateOccupancy()` (safety block) | N/A (server-enforced) |
| F12 | Last player wins | `EliminationManager.isGameOver()`, `RoundManager` | `ResultScene` |
| F13 | In-game chat (bonus) | `ChatManager`, `ClientMessageRouter`, `ChatBroadcastMessage` | `LobbyScene`, `ServerMessageRouter`, `LocalGameState`, `GameClient` |