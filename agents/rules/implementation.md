## 16. Implementation Order

Implement in this exact order. Each phase builds on the previous. Do not skip ahead.

### Phase 1 — Foundation (Milestone 1 prerequisite)
1. Create `pom.xml` and `module-info.java` exactly as specified.
2. Implement `shared/util/` — `Vector2D`, `GameTimer`, `Logger`.
3. Implement `shared/model/` — all enums, `Player`, `SafeZone`, `Arena`, `GameConfig`.
4. Implement `client/ClientApp.java` — open a JavaFX window.
5. Implement `client/scene/SceneManager.java` and `client/scene/MenuScene.java` — show a menu with Play/Quit.
6. Implement `client/input/InputManager.java` and `InputSnapshot.java`.
7. Implement `client/render/SpriteManager.java` — load placeholder sprites.
8. Implement `client/render/ArenaRenderer.java` — draw a colored rectangle for the arena.
9. Implement `client/render/PlayerRenderer.java` — draw one player as a sprite or colored circle.
10. Implement `client/scene/GameScene.java` — Canvas + render one player moving with WASD.
11. Implement `client/render/Renderer.java` — orchestrate ArenaRenderer + PlayerRenderer.
12. Implement `server/physics/CollisionDetector.java` — wall collision for the player.
13. **Checkpoint:** One player moves on screen, collides with walls. Window opens from menu.

### Phase 2 — Single-Player Game Logic (Milestone 1)
14. Implement `client/render/SafeZoneRenderer.java` — draw the safe zone.
15. Implement `client/render/HudRenderer.java` — draw round number, timer, player count.
16. Implement `server/game/GameState.java` — all fields and getters/setters.
17. Implement `server/game/SafeZoneManager.java` — spawn, occupancy check.
18. Implement `server/game/RoundManager.java` — full state machine.
19. Implement `server/game/EliminationManager.java` — warmup + elimination logic.
20. Implement `server/game/ChaosEventManager.java` — schedule and trigger.
21. Implement `server/game/CarryManager.java` — carry and throw.
22. Implement `server/physics/PhysicsEngine.java` — movement, throw velocity.
23. Wire it all together in a local-only game loop for testing (temporary).
24. Add dummy AI players or keyboard-split for testing multiple players locally.
25. Implement `client/scene/ResultScene.java` — win screen.
26. **Checkpoint:** Full single-player game loop works. Rounds progress, safe zones spawn, elimination happens, winner declared. **This is Milestone 1.**

### Phase 3 — Networking (Milestone 2 prerequisite)
27. Implement `shared/net/MessageType.java`.
28. Implement `shared/net/MessageEncoder.java` — all encode methods.
29. Implement `shared/net/MessageDecoder.java` — all decode methods.
30. Implement `shared/net/client/` and `shared/net/server/` message records.
31. Implement `server/net/ClientConnection.java`.
32. Implement `server/net/ClientMessageRouter.java`.
33. Implement `server/net/GameServer.java` — accept connections, manage clients.
34. Implement `server/game/LobbyManager.java`.
35. Implement `client/net/GameClient.java` — connect, reader thread, send methods.
36. Implement `client/net/ServerMessageRouter.java`.
37. Implement `client/game/LocalGameState.java`.
38. Implement `client/scene/LobbyScene.java`.
39. Implement `server/game/ServerGameLoop.java` — the real authoritative loop.
40. Implement `client/game/ClientGameLoop.java` — input → send → render from LocalGameState.
41. **Checkpoint:** 2+ clients connect, see each other move, positions synced.

### Phase 4 — Networked Game Logic (Milestone 2)
42. Wire `RoundManager`, `SafeZoneManager`, `EliminationManager` into `ServerGameLoop`.
43. Wire `ChaosEventManager` into `ServerGameLoop` — all three chaos events working.
44. Wire `CarryManager` into `ServerGameLoop` — carry/throw synced.
45. Per-client snapshot building in `broadcastState()` — personalized safe zones.
46. Control swap working end-to-end.
47. Elimination and game over messages flowing to clients.
48. **Checkpoint:** Full networked game. All F01–F12 requirements met. **This is Milestone 2.**

### Phase 5 — Bonus & Polish
49. Implement `client/render/ChatRenderer.java`.
50. Wire chat send/receive through protocol.
51. Implement `client/audio/AudioManager.java`.
52. Implement `client/scene/HowToPlayScene.java`.
53. Replace placeholder art with real sprites.
54. Add animation frames to `PlayerRenderer`.
55. Add visual polish — pulsing safe zone, chaos event toasts, elimination animations.