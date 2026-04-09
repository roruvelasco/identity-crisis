## 5. Shared Package

> `com.identitycrisis.shared` — Zero dependencies on client or server.

### 5.1 `shared/util/Vector2D.java`
```java
package com.identitycrisis.shared.util;

// Immutable 2D vector. Used everywhere for positions, velocities, directions.
public record Vector2D(double x, double y) {
    public Vector2D add(Vector2D other) { }
    public Vector2D subtract(Vector2D other) { }
    public Vector2D multiply(double scalar) { }
    public Vector2D normalize() { }
    public double magnitude() { }
    public double distanceTo(Vector2D other) { }
    public static Vector2D zero() { return new Vector2D(0, 0); }
}
```

### 5.2 `shared/util/GameTimer.java`
```java
package com.identitycrisis.shared.util;

// Countdown timer used by both server (authoritative) and client (display).
public class GameTimer {
    private double remainingSeconds;
    private boolean running;

    public GameTimer(double durationSeconds) { }
    public void start() { }
    public void stop() { }
    public void reset(double durationSeconds) { }
    public void tick(double deltaSeconds) { /* subtract delta, clamp to 0 */ }
    public boolean isExpired() { }
    public double getRemaining() { }
    public boolean isRunning() { }
}
```

### 5.3 `shared/util/Logger.java`

> **Usage convention:** `Logger` is **instance-based** (not static). Every class
> that needs logging declares one `private static final` field:
> ```java
> private static final Logger LOG = new Logger("ClassName");
> ```
> Never call `Logger.info(...)` directly — there are no static methods.
> This was the root cause of a compile error caught during initial build.

```java
package com.identitycrisis.shared.util;

// Minimal tagged logger. Wraps System.out/err with [TAG] prefixes.
// Output format: [LEVEL][Tag] message
public class Logger {
    private static final boolean DEBUG = false;
    private final String tag;
    public Logger(String tag) { this.tag = tag; }
    public void info(String msg)  { System.out.println("[INFO][" + tag + "] " + msg); }
    public void warn(String msg)  { System.out.println("[WARN][" + tag + "] " + msg); }
    public void error(String msg) { System.err.println("[ERROR][" + tag + "] " + msg); }
    public void error(String msg, Throwable t) {
        System.err.println("[ERROR][" + tag + "] " + msg);
        t.printStackTrace(System.err);
    }
    public void debug(String msg) { if (DEBUG) System.out.println("[DEBUG][" + tag + "] " + msg); }
}
```

### 5.4 `shared/model/PlayerState.java`
```java
package com.identitycrisis.shared.model;

public enum PlayerState {
    ALIVE,
    ELIMINATED,
    SPECTATING,
    CARRYING,    // this player is carrying another
    CARRIED      // this player is being carried by another
}
```

### 5.5 `shared/model/RoundPhase.java`
```java
package com.identitycrisis.shared.model;

public enum RoundPhase {
    LOBBY,          // waiting for players
    COUNTDOWN,      // 3-2-1 before round starts
    ACTIVE,         // round in progress
    ROUND_END,      // freeze, evaluate safe zone occupancy
    ELIMINATION,    // display who was eliminated
    GAME_OVER       // winner declared
}
```

### 5.6 `shared/model/ChaosEventType.java`
```java
package com.identitycrisis.shared.model;

public enum ChaosEventType {
    NONE,
    REVERSED_CONTROLS,   // F06: WASD inverted client-side
    CONTROL_SWAP,        // F07: you control someone else's character
    FAKE_SAFE_ZONES      // F08: multiple zones shown, only one is real
}
```

### 5.7 `shared/model/CarryState.java`
```java
package com.identitycrisis.shared.model;

// Represents a carry relationship. Only server creates/destroys these.
public record CarryState(int carrierPlayerId, int carriedPlayerId) { }
```

### 5.8 `shared/model/Player.java`

> **Always construct with `new Player(id, name)`** — there is no no-arg constructor.
> Carry IDs default to `-1`, **not `0`** (0 is a valid player ID).
> `equals()` / `hashCode()` are keyed on `playerId` so server-side and
> client-side copies of the same player compare equal.

```java
package com.identitycrisis.shared.model;

import com.identitycrisis.shared.util.Vector2D;
import java.util.Objects;

// Shared player data transferred over the network.
// Server holds authoritative copy; client holds rendered copy.
public class Player {
    private int playerId;
    private String displayName;
    private Vector2D position;
    private Vector2D velocity;
    private PlayerState state;
    private int facingDirection;       // 0=up,1=right,2=down,3=left
    private boolean inSafeZone;
    private int carriedByPlayerId;     // -1 if not carried
    private int carryingPlayerId;      // -1 if not carrying
    private double stunTimer;          // > 0 while throw-stunned; decremented by PhysicsEngine.step()

    // Primary constructor — sets carry IDs to -1 (NOT 0) and all refs to safe defaults.
    public Player(int playerId, String displayName) {
        this.playerId          = playerId;
        this.displayName       = displayName;
        this.position          = Vector2D.zero();
        this.velocity          = Vector2D.zero();
        this.state             = PlayerState.ALIVE;
        this.facingDirection   = 2; // 2 = down
        this.inSafeZone        = false;
        this.carriedByPlayerId = -1;
        this.carryingPlayerId  = -1;
    }

    // Full getters and setters for every field
    public int getPlayerId() { }
    public void setPlayerId(int id) { }
    public String getDisplayName() { }
    public void setDisplayName(String name) { }
    public Vector2D getPosition() { }
    public void setPosition(Vector2D pos) { }
    public Vector2D getVelocity() { }
    public void setVelocity(Vector2D vel) { }
    public PlayerState getState() { }
    public void setState(PlayerState state) { }
    public int getFacingDirection() { }
    public void setFacingDirection(int dir) { }
    public boolean isInSafeZone() { }
    public void setInSafeZone(boolean val) { }
    public int getCarriedByPlayerId() { }
    public void setCarriedByPlayerId(int id) { }
    public int getCarryingPlayerId() { }
    public void setCarryingPlayerId(int id) { }
    public double getStunTimer() { }
    public void setStunTimer(double t) { }

    // equals/hashCode keyed on playerId — server copy equals client copy of same player.
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Player other)) return false;
        return playerId == other.playerId;
    }
    @Override public int hashCode() { return Objects.hash(playerId); }
}
```

### 5.9 `shared/model/SafeZone.java`
```java
package com.identitycrisis.shared.model;

import com.identitycrisis.shared.util.Vector2D;

// Safe zone circle. Server knows which is true; clients just get a list.
public record SafeZone(Vector2D position, double radius) { }
```

### 5.10 `shared/model/Arena.java`
```java
package com.identitycrisis.shared.model;

// Static arena configuration. Bounds, walls, obstacles.
public class Arena {
    private double width;
    private double height;
    // Obstacle rectangles loaded from map definition

    public double getWidth() { }
    public double getHeight() { }
    public boolean isWall(double x, double y) { /* check bounds + obstacles */ }
    public static Arena loadDefault() { /* hardcoded or file-loaded arena */ }
}
```

### 5.11 `shared/model/GameConfig.java`
```java
package com.identitycrisis.shared.model;

// ALL magic numbers. Single source of truth for tuning.
public final class GameConfig {
    private GameConfig() { }

    // Networking
    public static final int SERVER_PORT = 5137;
    public static final int TICK_RATE = 60;
    public static final long TICK_DURATION_NS = 1_000_000_000L / TICK_RATE;

    // Arena
    public static final double ARENA_WIDTH = 1200.0;
    public static final double ARENA_HEIGHT = 800.0;

    // Players
    public static final int MIN_PLAYERS = 4;
    public static final int MAX_PLAYERS = 8;
    public static final double PLAYER_SPEED = 200.0;        // px/sec
    public static final double PLAYER_RADIUS = 16.0;

    // Rounds
    public static final int WARMUP_ROUNDS = 2;
    public static final double ROUND_DURATION_SECONDS = 15.0;
    public static final double COUNTDOWN_SECONDS = 3.0;
    public static final double ELIMINATION_DISPLAY_SECONDS = 3.0;

    // Safe Zone
    public static final double SAFE_ZONE_RADIUS = 64.0;
    public static final double SAFE_ZONE_MIN_MARGIN = 100.0;

    // Carry/Throw
    public static final double CARRY_RANGE = 32.0;
    public static final double THROW_SPEED = 400.0;
    public static final double THROW_STUN_SECONDS = 0.5;

    // Physics (used by PhysicsEngine — named constants, not magic numbers)
    public static final double VELOCITY_DAMPING = 0.95;        // per-tick damping factor
    public static final double VELOCITY_STOP_THRESHOLD = 0.1;  // below this → zero velocity
    public static final double SPAWN_RADIUS = 200.0;           // radial spawn spread (px)

    // Input Queue
    public static final int MAX_QUEUED_INPUTS = 120;

    // Chaos Events
    public static final double CHAOS_EVENT_MIN_DELAY = 3.0;
    public static final double CHAOS_EVENT_MAX_DELAY = 8.0;
    public static final double CHAOS_EVENT_DURATION = 5.0;
    public static final int FAKE_SAFE_ZONE_COUNT = 3;

    // Window
    public static final int WINDOW_WIDTH = 1280;
    public static final int WINDOW_HEIGHT = 720;
    public static final String WINDOW_TITLE = "Identity Crisis";
}
```

### 5.12 `shared/net/MessageType.java`
```java
package com.identitycrisis.shared.net;

// Every wire message starts with a 1-byte type tag.
// 0x01–0x3F = client→server. 0x40–0x7F = server→client.
public enum MessageType {
    C_JOIN_REQUEST    ((byte) 0x01),
    C_READY           ((byte) 0x02),
    C_PLAYER_INPUT    ((byte) 0x03),
    C_CHAT_SEND       ((byte) 0x04),

    S_LOBBY_STATE     ((byte) 0x40),
    S_GAME_STATE      ((byte) 0x41),
    S_ROUND_STATE     ((byte) 0x42),
    S_SAFE_ZONE       ((byte) 0x43),
    S_PLAYER_ELIMINATED ((byte) 0x44),
    S_CHAOS_EVENT     ((byte) 0x45),
    S_CONTROL_SWAP    ((byte) 0x46),
    S_GAME_OVER       ((byte) 0x47),
    S_CHAT_BROADCAST  ((byte) 0x48);

    private final byte tag;
    MessageType(byte tag) { this.tag = tag; }
    public byte getTag() { return tag; }
    public static MessageType fromTag(byte tag) { /* loop or map lookup */ }
}
```

### 5.13 `shared/net/MessageEncoder.java`
```java
package com.identitycrisis.shared.net;

import java.io.DataOutputStream;
import java.io.IOException;

// Writes typed messages to DataOutputStream.
// Wire format: [1B type][2B payload length][payload bytes]
public class MessageEncoder {
    private final DataOutputStream out;

    public MessageEncoder(DataOutputStream out) { }

    // Client → Server
    public void encodeJoinRequest(String displayName) throws IOException { }
    public void encodeReady() throws IOException { }
    public void encodePlayerInput(boolean up, boolean down, boolean left,
                                  boolean right, boolean carry,
                                  boolean throwAction) throws IOException { }
    public void encodeChatSend(String text) throws IOException { }

    // Server → Client
    public void encodeLobbyState(int connectedCount, int requiredCount,
                                 String[] playerNames,
                                 boolean[] readyFlags) throws IOException { }
    public void encodeGameState(int roundNumber, double timerRemaining,
                                byte phaseOrdinal, byte chaosOrdinal,
                                double chaosDuration, int controlledPlayerId,
                                PlayerNetData[] players,
                                SafeZoneNetData[] zones) throws IOException { }
    public void encodeRoundState(int roundNumber, byte phaseOrdinal,
                                 double timerRemaining) throws IOException { }
    public void encodeSafeZoneUpdate(double[] xs, double[] ys,
                                     double[] radii) throws IOException { }
    public void encodePlayerEliminated(int playerId,
                                       String playerName) throws IOException { }
    public void encodeChaosEvent(byte chaosOrdinal,
                                 double duration) throws IOException { }
    public void encodeControlSwap(int newControlledPlayerId) throws IOException { }
    public void encodeGameOver(int winnerPlayerId,
                               String winnerName) throws IOException { }
    public void encodeChatBroadcast(String senderName,
                                    String text) throws IOException { }

    // Internal
    private void writeHeader(MessageType type,
                             int payloadLength) throws IOException { }
    public void flush() throws IOException { }

    // Inner data carriers for encoding
    public record PlayerNetData(int id, String name, double x, double y,
                                double vx, double vy, byte stateOrdinal,
                                int facing, boolean inSafeZone,
                                int carriedBy, int carrying) { }
    public record SafeZoneNetData(double x, double y, double radius) { }
}
```

### 5.14 `shared/net/MessageDecoder.java`
```java
package com.identitycrisis.shared.net;

import java.io.DataInputStream;
import java.io.IOException;

// Reads framed messages from DataInputStream.
// Usage: type = readNextType(); switch(type) { case X -> decodeX(); }
public class MessageDecoder {
    private final DataInputStream in;
    private byte[] payloadBuffer;

    public MessageDecoder(DataInputStream in) { }

    // Blocks until next message header. Returns type.
    public MessageType readNextType() throws IOException { }

    // Client → Server decoders
    public String decodeJoinRequest() { }
    public void decodeReady() { }
    public boolean[] decodePlayerInput() { /* [up,down,left,right,carry,throw] */ }
    public String decodeChatSend() { }

    // Server → Client decoders
    public LobbyStateData decodeLobbyState() { }
    public GameStateData decodeGameState() { }
    public RoundStateData decodeRoundState() { }
    public SafeZoneData decodeSafeZoneUpdate() { }
    public EliminationData decodePlayerEliminated() { }
    public ChaosEventData decodeChaosEvent() { }
    public int decodeControlSwap() { }
    public GameOverData decodeGameOver() { }
    public ChatData decodeChatBroadcast() { }

    // Decoded payload containers
    public record LobbyStateData(int connectedCount, int requiredCount,
                                 String[] names, boolean[] ready) { }
    public record GameStateData(int roundNumber, double timerRemaining,
                                byte phaseOrdinal, byte chaosOrdinal,
                                double chaosDuration, int controlledPlayerId,
                                PlayerNetData[] players,
                                SafeZoneNetData[] zones) { }
    public record PlayerNetData(int id, String name, double x, double y,
                                double vx, double vy, byte stateOrdinal,
                                int facing, boolean inSafeZone,
                                int carriedBy, int carrying) { }
    public record SafeZoneNetData(double x, double y, double radius) { }
    public record RoundStateData(int roundNumber, byte phaseOrdinal,
                                 double timerRemaining) { }
    public record SafeZoneData(double[] xs, double[] ys, double[] radii) { }
    public record EliminationData(int playerId, String playerName) { }
    public record ChaosEventData(byte chaosOrdinal, double duration) { }
    public record GameOverData(int winnerPlayerId, String winnerName) { }
    public record ChatData(String senderName, String text) { }
}
```

### 5.15 `shared/net/client/` — Message Records
```java
// PlayerInputMessage.java
package com.identitycrisis.shared.net.client;
public record PlayerInputMessage(boolean up, boolean down, boolean left,
                                  boolean right, boolean carry,
                                  boolean throwAction) { }

// JoinRequestMessage.java
package com.identitycrisis.shared.net.client;
public record JoinRequestMessage(String displayName) { }

// ReadyMessage.java
package com.identitycrisis.shared.net.client;
public record ReadyMessage() { }

// ChatSendMessage.java
package com.identitycrisis.shared.net.client;
public record ChatSendMessage(String text) { }
```

### 5.16 `shared/net/server/` — Message Records
```java
// GameStateSnapshot.java
package com.identitycrisis.shared.net.server;
import com.identitycrisis.shared.model.*;
import java.util.List;
// PER-CLIENT snapshot. Each client may receive different data.
public class GameStateSnapshot {
    private int roundNumber;
    private double timerRemaining;
    private RoundPhase phase;
    private ChaosEventType activeChaos;
    private double chaosDurationRemaining;
    private int controlledPlayerId;
    private List<Player> players;
    private List<SafeZone> safeZones; // includes decoys for this client
    // Full getters and setters
}

// RoundStateUpdate.java
package com.identitycrisis.shared.net.server;
public record RoundStateUpdate(int roundNumber, byte phaseOrdinal,
                                double timerRemaining) { }

// SafeZoneUpdate.java
package com.identitycrisis.shared.net.server;
import com.identitycrisis.shared.model.SafeZone;
import java.util.List;
public record SafeZoneUpdate(List<SafeZone> zones) { }

// PlayerEliminatedMessage.java
package com.identitycrisis.shared.net.server;
public record PlayerEliminatedMessage(int playerId, String playerName) { }

// ChaosEventMessage.java
package com.identitycrisis.shared.net.server;
import com.identitycrisis.shared.model.ChaosEventType;
public record ChaosEventMessage(ChaosEventType type, double durationSeconds) { }

// ControlSwapMessage.java
package com.identitycrisis.shared.net.server;
public record ControlSwapMessage(int newControlledPlayerId) { }

// GameOverMessage.java
package com.identitycrisis.shared.net.server;
public record GameOverMessage(int winnerPlayerId, String winnerName) { }

// LobbyStateMessage.java
package com.identitycrisis.shared.net.server;
public record LobbyStateMessage(int connectedCount, int requiredCount,
                                 String[] playerNames,
                                 boolean[] readyFlags) { }

// ChatBroadcastMessage.java
package com.identitycrisis.shared.net.server;
public record ChatBroadcastMessage(String senderName, String text) { }
```