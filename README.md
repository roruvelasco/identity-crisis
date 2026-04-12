# Identity Crisis

A multiplayer arena battle game built with JavaFX. Players compete in rounds to reach safe zones while surviving chaos events and avoiding elimination.

## Tech Stack

- **Java**: 21 (LTS)
- **JavaFX**: 21.0.5 (controls, graphics, media)
- **Build Tool**: Maven 3.x
- **Fonts**: 
  - Cinzel Decorative (dominant titles)
  - Cinzel (buttons, labels)
  - Crimson Pro (body text, descriptions)
  - Press Start 2P (room codes, HUD elements)

## Project Structure

```
identity-crisis/
├── src/main/java/com/identitycrisis/
│   ├── client/
│   │   ├── ClientApp.java              # Entry point for client
│   │   ├── audio/AudioManager.java     # Sound effects and music
│   │   ├── game/
│   │   │   ├── ClientGameLoop.java     # Main client game loop
│   │   │   └── LocalGameState.java     # Local game state cache
│   │   ├── input/
│   │   │   ├── InputManager.java       # Keyboard input handling
│   │   │   └── InputSnapshot.java      # Input state snapshot
│   │   ├── net/
│   │   │   ├── GameClient.java         # Network client
│   │   │   └── ServerMessageRouter.java # Message routing
│   │   ├── render/
│   │   │   ├── ArenaRenderer.java      # Arena visual rendering
│   │   │   ├── ChatRenderer.java       # Chat UI rendering
│   │   │   ├── HudRenderer.java        # HUD elements
│   │   │   ├── PlayerRenderer.java     # Player sprite rendering
│   │   │   ├── Renderer.java           # Base renderer
│   │   │   ├── SafeZoneRenderer.java   # Safe zone rendering
│   │   │   └── SpriteManager.java      # Sprite asset management
│   │   └── scene/
│   │       ├── AboutScene.java         # About/credits screen
│   │       ├── CreateOrJoinScene.java  # Create or join game
│   │       ├── GameArena.java          # Active gameplay arena
│   │       ├── HowToPlayScene.java     # Instructions screen
│   │       ├── JoinRoomScene.java      # Join game with code
│   │       ├── LoadingScene.java       # Loading screen
│   │       ├── LobbyScene.java         # Waiting lobby
│   │       ├── MenuScene.java          # Main menu (Home)
│   │       ├── ResultScene.java        # Game over results
│   │       └── SceneManager.java       # Scene transition manager
│   ├── server/
│   │   ├── ServerApp.java              # Entry point for server
│   │   ├── EmbeddedServer.java         # Embedded server mode
│   │   └── game/
│   │       ├── CarryManager.java       # Player carry/throw logic
│   │       ├── ChaosEventManager.java  # Random chaos events
│   │       ├── EliminationManager.java # Player elimination
│   │       └── GameState.java          # Server-side game state
│   └── shared/
│       └── model/
│           └── GameConfig.java         # All game constants
├── src/main/resources/
│   ├── fonts/                          # Custom font files
│   ├── styles/global.css               # Global CSS styles
│   ├── bg_img.jpg                      # Home screen background
│   └── dungeon_bg.jpg                  # CreateOrJoin background
├── html/                               # Reference HTML designs
└── pom.xml                             # Maven configuration
```

## Screen Flow

```
Home (MenuScene)
    ├── Play → CreateOrJoinScene
    │       ├── Create Game → LobbyScene → LoadingScene → GameArena
    │       └── Join Game → JoinRoomScene → LobbyScene → LoadingScene → GameArena
    ├── How to Play → HowToPlayScene
    ├── About → AboutScene
    └── Quit
```

### Screen Descriptions

| Screen | Description |
|--------|-------------|
| **Home** | Main menu with animated background, title, and navigation buttons |
| **CreateOrJoin** | Choose between creating a new game or joining with a code |
| **Lobby** | Waiting room displaying room code, player count, and tips |
| **JoinRoom** | Input field for entering room codes |
| **Loading** | Progress bar with status messages before arena |
| **GameArena** | Active gameplay screen (currently placeholder) |

## How to Run

### Prerequisites
- Java 21 or higher
- Maven 3.8+

### Build and Run Client
```bash
# Compile
./mvnw clean compile

# Run client
./mvnw javafx:run

# Or on Windows
.\mvnw.cmd javafx:run
```

### Run Server (Standalone)
```bash
./mvnw exec:java -Dexec.mainClass="com.identitycrisis.server.ServerApp"
```

## Typography System

| Font | Usage |
|------|-------|
| **Cinzel Decorative** | Main titles ("Identity Crisis", "PLAY") |
| **Cinzel** | Button labels, section headers, back buttons |
| **Crimson Pro** | Body text, descriptions, tips |
| **Press Start 2P** | Room codes, player counts, HUD counters |

## Key Assets

| Asset | Usage |
|-------|-------|
| `bg_img.jpg` | Home screen background image |
| `dungeon_bg.jpg` | CreateOrJoin scene background |
| `sprites.svg` | Player and game sprites |
| `identity_crisis_logo.png` | Game logo |

## Known Placeholders

- **GameArena**: Currently shows placeholder label; actual game canvas rendering to be implemented
- **Room Code Generation**: Client-side random generation only; no server validation
- **Network Gameplay**: UI flow complete but backend multiplayer logic is stubbed
- **Chaos Events**: UI described in HowToPlay but not yet implemented in gameplay
- **Audio**: AudioManager class exists but sound assets not yet integrated
