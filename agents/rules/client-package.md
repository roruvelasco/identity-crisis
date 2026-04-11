## 7. Client Package

> `com.identitycrisis.client` — JavaFX client. Depends on `shared`. Sends input, receives state, renders.

### 7.1 `client/ClientApp.java` — Client Composition Root

> **This is the client-side Composition Root**, mirroring `ServerApp.main()` on the
> server. It is the only place that creates `SceneManager`. Everything else
> (`GameClient`, `LocalGameState`, `InputManager`) is wired inside `SceneManager`
> when the player navigates to the lobby or game scene.

```java
package com.identitycrisis.client;

import com.identitycrisis.client.scene.SceneManager;
import com.identitycrisis.shared.model.GameConfig;
import javafx.application.Application;
import javafx.stage.Stage;

// JavaFX Application entry point and client-side Composition Root.
public class ClientApp extends Application {
    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle(GameConfig.WINDOW_TITLE);
        primaryStage.setWidth(GameConfig.WINDOW_WIDTH);
        primaryStage.setHeight(GameConfig.WINDOW_HEIGHT);
        primaryStage.setResizable(false);
        SceneManager sceneManager = new SceneManager(primaryStage);
        sceneManager.showMenu();
        primaryStage.show();
    }
    public static void main(String[] args) { launch(args); }
}
```

### 7.2 `client/net/GameClient.java`
```java
package com.identitycrisis.client.net;

import com.identitycrisis.shared.net.*;
import java.io.*;
import java.net.Socket;

// TCP connection to server. Reader thread + synchronized send methods.
public class GameClient {
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private MessageEncoder encoder;
    private MessageDecoder decoder;
    private ServerMessageRouter router;
    private Thread readerThread;
    private volatile boolean connected;

    public GameClient(ServerMessageRouter router) { }
    public void connect(String host, int port) throws IOException { }
    public void startListening() { }
    private void readLoop() {
        // while(connected): type = decoder.readNextType();
        //   router.route(type, decoder)
    }

    // Send methods (synchronized — called from JavaFX or game loop thread)
    public synchronized void sendJoinRequest(String displayName) { }
    public synchronized void sendReady() { }
    public synchronized void sendInput(boolean up, boolean down, boolean left,
                                       boolean right, boolean carry,
                                       boolean throwAction) { }
    public synchronized void sendChat(String text) { }
    public boolean isConnected() { }
    public void disconnect() { }
}
```

### 7.3 `client/net/ServerMessageRouter.java`
```java
package com.identitycrisis.client.net;

import com.identitycrisis.shared.net.*;
import com.identitycrisis.client.game.LocalGameState;

// Routes incoming server messages to update LocalGameState.
// Runs on reader thread — marshal UI updates via Platform.runLater().
public class ServerMessageRouter {
    private LocalGameState localGameState;

    // Callback interfaces for UI events
    private Runnable onLobbyStateChanged;
    private Runnable onGameStarted;
    private Runnable onElimination;
    private Runnable onGameOver;
    private Runnable onChatReceived;

    public ServerMessageRouter(LocalGameState localGameState) { }

    public void route(MessageType type, MessageDecoder decoder) {
        // switch (type):
        //   S_LOBBY_STATE     → update lobby, fire onLobbyStateChanged
        //   S_GAME_STATE      → localGameState.updateFromSnapshot(...)
        //   S_ROUND_STATE     → localGameState.updateRoundState(...)
        //   S_SAFE_ZONE       → localGameState.updateSafeZones(...)
        //   S_PLAYER_ELIMINATED → mark eliminated, fire callback
        //   S_CHAOS_EVENT     → localGameState.setChaosEvent(...)
        //   S_CONTROL_SWAP    → localGameState.setControlledPlayerId(...)
        //   S_GAME_OVER       → set game over, fire callback
        //   S_CHAT_BROADCAST  → add chat message, fire callback
    }

    public void setOnLobbyStateChanged(Runnable r) { }
    public void setOnGameStarted(Runnable r) { }
    public void setOnElimination(Runnable r) { }
    public void setOnGameOver(Runnable r) { }
    public void setOnChatReceived(Runnable r) { }
}
```

### 7.4 `client/input/InputSnapshot.java`
```java
package com.identitycrisis.client.input;

// Immutable snapshot of one frame's input.
public record InputSnapshot(
    boolean up, boolean down, boolean left, boolean right,
    boolean carry, boolean throwAction, boolean chatToggle
) {}
```

### 7.5 `client/input/InputManager.java`
```java
package com.identitycrisis.client.input;

import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import java.util.Set;

// Captures keyboard input. Produces InputSnapshot each frame.
// Bindings: W/UP=up, A/LEFT=left, S/DOWN=down, D/RIGHT=right,
//           E=carry, Q=throw, ENTER=chatToggle
public class InputManager {
    private final Set<KeyCode> pressedKeys;

    public InputManager() { }
    public void attachToScene(Scene scene) { }
    public void detachFromScene(Scene scene) { }
    public InputSnapshot snapshot() { }
    public boolean isPressed(KeyCode code) { }
}
```

### 7.6 `client/game/LocalGameState.java`
```java
package com.identitycrisis.client.game;

import com.identitycrisis.shared.model.*;
import com.identitycrisis.shared.net.MessageDecoder;
import java.util.List;

// Client's local copy of game state. Thread-safe via volatile reference swap.
// Written by network thread, read by render thread.
public class LocalGameState {
    // Lobby
    private volatile int lobbyConnectedCount;
    private volatile int lobbyRequiredCount;
    private volatile String[] lobbyPlayerNames;
    private volatile boolean[] lobbyReadyFlags;

    // Game (from latest snapshot)
    private volatile int roundNumber;
    private volatile double timerRemaining;
    private volatile RoundPhase phase;
    private volatile ChaosEventType activeChaos;
    private volatile double chaosDurationRemaining;
    private volatile int controlledPlayerId;
    private volatile int myPlayerId;
    private volatile List<Player> players;
    private volatile List<SafeZone> safeZones;

    // UI
    private volatile boolean gameOver;
    private volatile int winnerPlayerId;
    private volatile String winnerName;
    private volatile List<String> chatMessages;
    private volatile String lastEliminatedName;

    // Update methods (network thread)
    public void updateFromSnapshot(MessageDecoder.GameStateData data) { }
    public void updateLobbyState(MessageDecoder.LobbyStateData data) { }
    public void updateRoundState(MessageDecoder.RoundStateData data) { }
    public void updateSafeZones(MessageDecoder.SafeZoneData data) { }
    public void markEliminated(MessageDecoder.EliminationData data) { }
    public void setChaosEvent(MessageDecoder.ChaosEventData data) { }
    public void setControlledPlayerId(int id) { }
    public void setGameOver(MessageDecoder.GameOverData data) { }
    public void addChatMessage(MessageDecoder.ChatData data) { }
    public void setMyPlayerId(int id) { }

    // Read methods (render thread)
    public int getRoundNumber() { }
    public double getTimerRemaining() { }
    public RoundPhase getPhase() { }
    public ChaosEventType getActiveChaos() { }
    public int getControlledPlayerId() { }
    public int getMyPlayerId() { }
    public List<Player> getPlayers() { }
    public List<SafeZone> getSafeZones() { }
    public boolean isGameOver() { }
    public int getWinnerPlayerId() { }
    public String getWinnerName() { }
    public List<String> getChatMessages() { }
    public String getLastEliminatedName() { }
    public int getLobbyConnectedCount() { }
    public int getLobbyRequiredCount() { }
    public String[] getLobbyPlayerNames() { }
    public boolean[] getLobbyReadyFlags() { }
}
```

### 7.7 `client/game/ClientGameLoop.java`
```java
package com.identitycrisis.client.game;

import com.identitycrisis.client.input.*;
import com.identitycrisis.client.net.GameClient;
import com.identitycrisis.client.render.Renderer;
import javafx.animation.AnimationTimer;

// Client-side loop via AnimationTimer (~60fps).
// Each frame: poll input → apply chaos mods → send to server → render.
// No client-side prediction. Pure server-authoritative.
public class ClientGameLoop extends AnimationTimer {
    private final InputManager inputManager;
    private final GameClient gameClient;
    private final LocalGameState localGameState;
    private final Renderer renderer;
    private long lastFrameTime;

    public ClientGameLoop(InputManager inputManager, GameClient gameClient,
                          LocalGameState localGameState, Renderer renderer) { }

    @Override
    public void handle(long now) {
        // double dt = (now - lastFrameTime) / 1_000_000_000.0
        // InputSnapshot input = inputManager.snapshot()
        // InputSnapshot modified = applyChaosModifications(input)
        // gameClient.sendInput(...)
        // renderer.render(localGameState, dt)
        // lastFrameTime = now
    }

    // If REVERSED_CONTROLS active, invert movement keys.
    private InputSnapshot applyChaosModifications(InputSnapshot raw) { }
}
```

### 7.8 `client/render/Renderer.java`

> **DI exception (approved):** `Renderer` creates its five sub-renderers
> (`ArenaRenderer`, `PlayerRenderer`, `SafeZoneRenderer`, `HudRenderer`,
> `ChatRenderer`) internally. This is the one deliberate deviation from the
> "never instantiate a collaborator" rule. Rationale: the constructor signature
> is fixed to `(Canvas, SpriteManager)` by the spec, and these sub-renderers are
> 1:1 owned children of `Renderer` that are never shared with any other class.
> All sub-renderer fields are `final`.

```java
package com.identitycrisis.client.render;

import com.identitycrisis.client.game.LocalGameState;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;

// Master renderer. Orchestrates sub-renderers in z-order:
// 1. Clear → 2. Arena → 3. SafeZone → 4. Players → 5. HUD → 6. Chat
public class Renderer {
    private final Canvas canvas;
    private final GraphicsContext gc;
    private final ArenaRenderer arenaRenderer;      // owned child — see DI exception above
    private final PlayerRenderer playerRenderer;    // owned child
    private final SafeZoneRenderer safeZoneRenderer;// owned child
    private final HudRenderer hudRenderer;          // owned child
    private final ChatRenderer chatRenderer;        // owned child
    private final SpriteManager spriteManager;

    public Renderer(Canvas canvas, SpriteManager spriteManager) { }

    public void render(LocalGameState state, double dt) {
        // gc.clearRect(...)
        // arenaRenderer.render(gc, state)
        // safeZoneRenderer.render(gc, state)
        // playerRenderer.render(gc, state, dt)
        // hudRenderer.render(gc, state)
        // chatRenderer.render(gc, state)
    }
}
```

### 7.9 `client/render/ArenaRenderer.java`
```java
package com.identitycrisis.client.render;

import com.identitycrisis.client.game.LocalGameState;
import javafx.scene.canvas.GraphicsContext;

// Renders static map: background tiles, walls, obstacles.
public class ArenaRenderer {
    private SpriteManager spriteManager;
    public ArenaRenderer(SpriteManager spriteManager) { }
    public void render(GraphicsContext gc, LocalGameState state) { }
}
```

### 7.10 `client/render/PlayerRenderer.java`
```java
package com.identitycrisis.client.render;

import com.identitycrisis.client.game.LocalGameState;
import javafx.scene.canvas.GraphicsContext;

// Renders player sprites. Handles: facing direction, walk animation,
// carrying visual, eliminated ghost, controlled-player highlight.
public class PlayerRenderer {
    private SpriteManager spriteManager;
    private double animationTimer;

    public PlayerRenderer(SpriteManager spriteManager) { }
    public void render(GraphicsContext gc, LocalGameState state, double dt) { }
    private void drawPlayer(GraphicsContext gc, /* Player data */) { }
}
```

### 7.11 `client/render/SafeZoneRenderer.java`
```java
package com.identitycrisis.client.render;

import com.identitycrisis.client.game.LocalGameState;
import javafx.scene.canvas.GraphicsContext;

// Renders safe zone(s). During fake chaos, renders ALL identically.
// Client has no idea which is real. Pulsing/glow effect.
public class SafeZoneRenderer {
    private SpriteManager spriteManager;
    public SafeZoneRenderer(SpriteManager spriteManager) { }
    public void render(GraphicsContext gc, LocalGameState state) { }
}
```

### 7.12 `client/render/HudRenderer.java`
```java
package com.identitycrisis.client.render;

import com.identitycrisis.client.game.LocalGameState;
import javafx.scene.canvas.GraphicsContext;

// HUD: round number, countdown timer, players remaining,
// chaos event toast, elimination toast, YOU WIN / YOU WERE ELIMINATED overlay.
public class HudRenderer {
    public HudRenderer() { }
    public void render(GraphicsContext gc, LocalGameState state) { }
    private void drawToast(GraphicsContext gc, String message,
                           double x, double y) { }
}
```

### 7.13 `client/render/ChatRenderer.java`
```java
package com.identitycrisis.client.render;

import com.identitycrisis.client.game.LocalGameState;
import javafx.scene.canvas.GraphicsContext;

// (Bonus) Chat overlay. Last N messages in semi-transparent box.
// Shows text input field when chat is active.
public class ChatRenderer {
    public ChatRenderer() { }
    public void render(GraphicsContext gc, LocalGameState state) { }
}
```

### 7.14 `client/render/SpriteManager.java`
```java
package com.identitycrisis.client.render;

import javafx.scene.image.Image;
import java.util.Map;

// Loads, caches, provides sprite images from resources/sprites/.
// Keys: "player_0_walk_down_0", "safezone", "obstacle_rock", etc.
public class SpriteManager {
    private Map<String, Image> spriteCache;

    public SpriteManager() { }
    public void loadAll() { }
    public Image get(String key) { }
    private Image loadImage(String resourcePath) { }
}
```

### 7.15 `client/scene/SceneManager.java`

> **Wiring note:** `gameClient`, `localGameState`, and `inputManager` are **not** constructed
> in `SceneManager`'s constructor. They are injected lazily via setters (see §3 Key Principle 6 —
> setter injection for collaborators that cannot all be constructed simultaneously).
> Scene controllers (`MenuScene`, `LobbyScene`, etc.) are eagerly allocated in the constructor
> because they hold only UI state, not network state.
>
> `AboutScene` is an approved addition (static info scene, mirrors `HowToPlayScene`).

```java
package com.identitycrisis.client.scene;

import javafx.stage.Stage;
import com.identitycrisis.client.net.GameClient;
import com.identitycrisis.client.game.LocalGameState;
import com.identitycrisis.client.input.InputManager;

// Manages transitions between scenes by swapping Stage's Scene.
public class SceneManager {
    private Stage primaryStage;
    private GameClient gameClient;        // injected lazily via setter before showLobby()
    private LocalGameState localGameState;// injected lazily via setter before showLobby()
    private InputManager inputManager;    // injected lazily via setter before showGame()

    // Scene controllers — eagerly allocated (UI state only, no network deps)
    private MenuScene menuScene;
    private HowToPlayScene howToPlayScene;
    private LobbyScene lobbyScene;
    private GameScene gameScene;
    private LoadingScene loadingScene;      // transition screen: Menu → Lobby
    private ResultScene resultScene;
    private AboutScene aboutScene;        // approved extra scene

    public SceneManager(Stage primaryStage) { }

    public void showMenu() { }
    public void showLoading() { }         // Play clicked → animated loading bar → showLobby()
    public void showLobby() { }
    public void showGame() { }
    public void showResult() { }
    public void showHowToPlay() { }
    public void showAbout() { }           // approved extra

    // Getters
    public Stage getStage() { }
    public GameClient getGameClient() { }
    public LocalGameState getLocalGameState() { }
    public InputManager getInputManager() { }

    // Setters — called by MenuScene.onPlayClicked() before navigating to lobby/game
    public void setGameClient(GameClient gameClient) { }
    public void setLocalGameState(LocalGameState localGameState) { }
    public void setInputManager(InputManager inputManager) { }
}
```

### 7.16 `client/scene/MenuScene.java`
```java
package com.identitycrisis.client.scene;

import javafx.scene.Scene;

// Main menu: Play, How to Play, Quit.
// Text fields: player name, server IP, port.
// Play → validate → connect → showLoading() → (loading done) → showLobby()
public class MenuScene {
    private Scene scene;
    private SceneManager sceneManager;

    public MenuScene(SceneManager sceneManager) { }
    public Scene getScene() { }
    private void onPlayClicked() { }
    private void onHowToPlayClicked() { }
    private void onQuitClicked() { }
}
```

### 7.17 `client/scene/LobbyScene.java`
```java
package com.identitycrisis.client.scene;

import javafx.scene.Scene;

// Waiting room displayed after LoadingScene completes.
// UI: "Waiting for Players…" subtitle, dynamic donut ring (N gold slices for N players,
//      max 8 = GameConfig.MAX_PLAYERS), "X / 8 PLAYERS" counter, "▶ LOBBY FULL ◀"
//      indicator (shown at capacity), mock [−]/[+] buttons to simulate player count
//      for testing the donut fill, "▶ Start Game" button (Cinzel gold variant) wired
//      directly to sceneManager.showGame(), rotating tips section.
// Note: game start is triggered manually by the Start Game button, not by a server signal.
//       The real server signal path (S_LOBBY_STATE → onLobbyStateChanged callback) should
//       call setPlayerCount(int) to drive the donut from live data instead of mock buttons.
public class LobbyScene {
    private Scene scene;
    private SceneManager sceneManager;

    public LobbyScene(SceneManager sceneManager) { }
    public Scene createScene() { }
    public Scene getScene() { }

    // Lifecycle — called by SceneManager on enter/exit
    public void onEnter() { /* reset playerCount=1, redraw donut, start tipRotation */ }
    public void onExit()  { /* stop tipRotation */ }

    // Called by the real network layer (ServerMessageRouter) with live lobby count.
    // Updates the donut ring, counter label, and LOBBY FULL indicator.
    public void setPlayerCount(int count) { }

    private void onReadyClicked() { }
    public void refreshLobbyDisplay() { }
}
```

### 7.18 `client/scene/GameScene.java`
```java
package com.identitycrisis.client.scene;

import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;

// Core gameplay view. Canvas + InputManager + ClientGameLoop.
public class GameScene {
    private Scene scene;
    private Canvas canvas;
    private SceneManager sceneManager;

    public GameScene(SceneManager sceneManager) { }
    public Scene getScene() { }
    public void onEnter() { /* attach input, start ClientGameLoop */ }
    public void onExit() { /* stop loop, detach input */ }
}
```

### 7.19 `client/scene/ResultScene.java`
```java
package com.identitycrisis.client.scene;

import javafx.scene.Scene;

// Winner display. Buttons: Play Again, Main Menu, Quit.
public class ResultScene {
    private Scene scene;
    private SceneManager sceneManager;

    public ResultScene(SceneManager sceneManager) { }
    public Scene getScene() { }
}
```

### 7.20 `client/scene/HowToPlayScene.java`
```java
package com.identitycrisis.client.scene;

import javafx.scene.Scene;

// Static info: key bindings, rules, chaos events. Back button → menu.
public class HowToPlayScene {
    private Scene scene;
    private SceneManager sceneManager;

    public HowToPlayScene(SceneManager sceneManager) { }
    public Scene getScene() { }
}
```

### 7.21 `client/audio/AudioManager.java`
```java
package com.identitycrisis.client.audio;

// BGM and SFX via JavaFX MediaPlayer/AudioClip.
// SFX: elimination, chaos trigger, round start, throw, pickup, win.
public class AudioManager {
    public AudioManager() { }
    public void loadAll() { }
    public void playBGM(String trackName) { }
    public void stopBGM() { }
    public void playSFX(String sfxName) { }
    public void setVolume(double volume) { }
}
```

### 7.22 `client/scene/LoadingScene.java`
```java
package com.identitycrisis.client.scene;

import javafx.scene.Scene;

// Animated transition screen between Menu and Lobby.
// Spinning crest, progress bar, status messages, rotating tips.
// onEnter() starts the animation; transitions to showLobby() on completion.
// onExit() stops all timelines.
public class LoadingScene {
    private Scene scene;
    private SceneManager sceneManager;

    public LoadingScene(SceneManager sceneManager) { }
    public Scene getScene() { }
    public void onEnter() { /* start loadingAnimation + tipRotation; fires showLobby() when done */ }
    public void onExit() { /* stop loadingAnimation + tipRotation */ }
}
```

---