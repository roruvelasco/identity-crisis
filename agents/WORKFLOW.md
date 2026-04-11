# Identity Crisis - JavaFX UI/UX Implementation Workflow

## Project Overview
Transform stub JavaFX scene files into pixel-perfect implementations matching the HTML reference designs.

**STATUS: ✅ ALL TASKS COMPLETED**

---

## STEP 1: Global Typography & Styles Setup ✅

### 1.1 Create Global Stylesheet ✅
- [x] Create `src/main/resources/styles/global.css` - @ `/src/main/resources/styles/global.css`
- [x] Define CSS color variables matching HTML:
  - `-gold: #c9a84c`
  - `-gold-light: #e8c86a`
  - `-gold-dark: #8a6a1a`
  - `-stone-dark: #0d0d10` / `-stone-mid: #1a1a1e`
  - `-stone-panel: #1c1c26`
  - `-stone-border: #2a2a36`
  - `-torch-orange: #e8743c`
  - `-text-parchment: #e8dfc4`
  - `-text-muted: #7a7060`
  - `-text-body: #b0a890`
  - `-btn-idle: #3b4663`
  - `-btn-border: #5a6a90`
  - `-chaos-purple: #7c5cbf`
  - `-safe-green: #4a8c5c`
  - `-danger-red: #8c3a3a`

### 1.2 Font Loading ✅
- [x] Load Google Fonts via CSS `@import`:
  - Cinzel Decorative (400, 700) - Logo text
  - Cinzel (400, 700, 900) - Button labels, headers
  - Crimson Pro (300, 400, 600, italic) - Descriptions, body text
  - Press Start 2P (400) - Toast messages, HUD, badges

### 1.3 Common UI Components CSS ✅
- [x] `.pixel-button` / `.pixel-button-play` - Pixel-style button with corner cutouts
- [x] `.pixel-panel` - Stone panel styling
- [x] `.section-title-text` - Gold title with gradient line
- [x] `.scanlines-overlay` - Scanline overlay effect
- [x] `.torch-glow-left/right` - Radial gradient torch effects

---

## STEP 2: SceneManager Implementation ✅

### 2.1 Scene Management ✅
- [x] Implement scene caching (Map<String, Scene>) - @ `SceneManager.java:22`
- [x] Implement showMenu(), showLobby(), showGame(), showResult(), showHowToPlay() - @ `SceneManager.java:44-79`
- [x] Add getter/setter methods for GameClient, LocalGameState, InputManager - @ `SceneManager.java:120-146`

### 2.2 Fullscreen Toggle ✅
- [x] Implement toggleFullscreen() method - @ `SceneManager.java:85-88`
- [x] Implement isFullscreen() getter - @ `SceneManager.java:93-95`
- [x] Bind F11 key shortcut via scene key handlers - @ `SceneManager.java:100-118`
- [x] Add fullscreen button to all scenes (top-right corner)
- [x] Use `stage.setFullScreenExitHint("")` to suppress default hint - @ `SceneManager.java:33`
- [x] Use `stage.setFullScreenExitKeyCombination(KeyCombination.NO_MATCH)` - @ `SceneManager.java:34`

---

## STEP 3: MenuScene (Home Screen) Implementation ✅

### 3.1 Background Layer ✅
- [x] Load `bg_img.jpg` as BackgroundImage (cover, center top) - @ `MenuScene.java:82-94`
- [x] Add vignette overlay (radial gradient transparent to rgba(0,0,0,0.7)) - @ `MenuScene.java:96-100`
- [x] Add bottom gradient overlay (linear-gradient to top) - @ `MenuScene.java:102-113`
- [x] Add scanlines overlay (repeating-linear-gradient) - @ `MenuScene.java:442-449`
- [x] Add torch glow effects (left 5%, right 8%) - @ `MenuScene.java:116-154`
- [x] Add animated torch particles (4 particles with flicker animation) - @ `MenuScene.java:156-184`

### 3.2 Logo Area ✅
- [x] Title "Identity Crisis" - Cinzel Decorative, 64px, parchment color - @ `MenuScene.java:233-249`
- [x] Gold text shadow glow effect - @ `MenuScene.java:245-249`
- [x] Gold divider line with diamond center ornament - @ `MenuScene.java:251-263`
- [x] Tagline "Who will survive the arena?" - Crimson Pro italic - @ `MenuScene.java:265-274`

### 3.3 Player Badge ✅
- [x] "⚔ 4+ PLAYERS · ARENA BATTLE" - Press Start 2P, 7px - @ `MenuScene.java:196-209`
- [x] Black background with gold border - @ `MenuScene.java:197-207`

### 3.4 Menu Buttons ✅
- [x] Play button (gold accent variant) with ▶ icon - @ `MenuScene.java:285-287`
- [x] How to Play button with ? icon - @ `MenuScene.java:289-291`
- [x] About button with ✦ icon - @ `MenuScene.java:293-295`
- [x] Quit button with ✕ icon - @ `MenuScene.java:297-299`
- [x] Button dimensions: 240x52px - @ `MenuScene.java:304-308`
- [x] Button font: Cinzel, 14px, letter-spacing 3px - @ `MenuScene.java:315-324`
- [x] Hover effects (color change, glow intensify) - @ `MenuScene.java:326-353`
- [x] Press animation (scale down) - @ `MenuScene.java:355-365`

### 3.5 Footer Elements ✅
- [x] Course tag "CMSC 137 · AY 2025-2026" - Press Start 2P, bottom-left - @ `MenuScene.java:370-381`
- [x] Version "v1.0.0" - Press Start 2P, bottom-right - @ `MenuScene.java:383-393`

### 3.6 Fullscreen Button ✅
- [x] Top-right fullscreen toggle button - @ `MenuScene.java:396-439`

---

## STEP 4: HowToPlayScene Implementation ✅

### 4.1 Layout Structure ✅
- [x] Stone tile background pattern (48px grid) - @ `HowToPlayScene.java:65-76`
- [x] Torch corner glows (top-left, top-right) - @ `HowToPlayScene.java:78-98`
- [x] Scanlines overlay - @ `HowToPlayScene.java:594-602`

### 4.2 Header ✅
- [x] Back button: ◀ Back - Cinzel, 11px - @ `HowToPlayScene.java:145-191`
- [x] Title: "How to Play" - Cinzel, 18px - @ `HowToPlayScene.java:193-201`
- [x] Header divider line - @ `HowToPlayScene.java:203-208`

### 4.3 Objective Banner ✅
- [x] Label "◆ OBJECTIVE ◆" - Press Start 2P, 6px - @ `HowToPlayScene.java:213-229`
- [x] Text "Reach the safe zone. Survive every round. Be the last one standing." - Cinzel, 18px - @ `HowToPlayScene.java:231-240`

### 4.4 Controls Section ✅
- [x] Section title "◆ Controls" with gradient line - @ `HowToPlayScene.java:245-250`
- [x] WASD visual cluster (3 keys in row, W above) - @ `HowToPlayScene.java:278-297`
- [x] Key badges: E, Q, Enter - Press Start 2P - @ `HowToPlayScene.java:300-336`
- [x] Action descriptions - Crimson Pro - @ `HowToPlayScene.java:338-366`

### 4.5 Round Phases Section ✅
- [x] Section title "◆ Round Phases" - @ `HowToPlayScene.java:368-373`
- [x] Warm-up badge (green): "ROUNDS 1-2 WARM-UP" - @ `HowToPlayScene.java:378-383`
- [x] Elimination badge (red): "ROUND 3+ ELIMINATION" - @ `HowToPlayScene.java:386-391`
- [x] Descriptions with styled text - @ `HowToPlayScene.java:398-443`

### 4.6 Chaos Events Section ✅
- [x] Section title "◆ Chaos Events" - @ `HowToPlayScene.java:445-450`
- [x] 3 chaos cards in grid - @ `HowToPlayScene.java:452-470`
  - Event 01: Reversed Controls
  - Event 02: Control Swap
  - Event 03: Decoy Zones
- [x] Purple left border accent - @ `HowToPlayScene.java:475-482`
- [x] Event icon labels - Press Start 2P - @ `HowToPlayScene.java:484-491`
- [x] Card titles - Cinzel - @ `HowToPlayScene.java:493-499`
- [x] Card descriptions - Crimson Pro - @ `HowToPlayScene.java:501-507`

### 4.7 Carry Mechanics Section ✅
- [x] Section title "◆ Carry Mechanics" - @ `HowToPlayScene.java:513-518`
- [x] 3 numbered mechanic items - @ `HowToPlayScene.java:523-525`
- [x] Number badges - Press Start 2P - @ `HowToPlayScene.java:541-547`
- [x] Mechanic text - Crimson Pro - @ `HowToPlayScene.java:558-566`

### 4.8 Fullscreen Button ✅
- [x] Top-right fullscreen toggle button - @ `HowToPlayScene.java:604-648`

---

## STEP 5: LobbyScene (Loading/Waiting Room) Implementation ✅

### 5.1 Background ✅
- [x] Stone tile pattern background - @ `LobbyScene.java:99-109`
- [x] 4 corner torch glows with pulse animation - @ `LobbyScene.java:111-143`
- [x] Scanlines overlay - @ `LobbyScene.java:373-381`

### 5.2 Animated Crest ✅
- [x] SVG crest with rotating animation (8s infinite) - @ `LobbyScene.java:200-285`
- [x] Gold stroke circles and diamond marks - @ `LobbyScene.java:203-246`
- [x] Center mask icon - @ `LobbyScene.java:248-275`

### 5.3 Title Section ✅
- [x] "Identity Crisis" - Cinzel Decorative, 48px - @ `LobbyScene.java:156-165`
- [x] "Loading Arena..." subtitle - Press Start 2P, 8px - @ `LobbyScene.java:167-176`

### 5.4 Loading Bar ✅
- [x] Progress track: 400px wide, 20px tall - @ `LobbyScene.java:288-342`
- [x] Gradient fill (gold-dark to gold-light) - @ `LobbyScene.java:335-336`
- [x] Tick marks at 25%, 50%, 75% - @ `LobbyScene.java:326-332`
- [x] Percentage label - Press Start 2P - @ `LobbyScene.java:304-310`

### 5.5 Status Text ✅
- [x] Rotating status messages - Press Start 2P, 8px - @ `LobbyScene.java:183-190`
- [x] Messages: "Connecting to server...", "Loading arena tiles...", etc. - @ `LobbyScene.java:45-53`

### 5.6 Tip Box ✅
- [x] Border top with gold color - @ `LobbyScene.java:344-349`
- [x] "◆ TIP ◆" label - Press Start 2P, 6px - @ `LobbyScene.java:351-358`
- [x] Tip text - Crimson Pro italic, 15px - @ `LobbyScene.java:360-367`
- [x] Rotating tips every 4 seconds - @ `LobbyScene.java:464-477`

### 5.7 Fullscreen Button ✅
- [x] Top-right fullscreen toggle button - @ `LobbyScene.java:383-427`

### 5.8 onEnter() Method ✅
- [x] Start loading animation and tip rotation - @ `LobbyScene.java:433-478`

---

## STEP 6: GameScene Implementation ✅

### 6.1 Canvas Setup ✅
- [x] Full-size Canvas (1280x720) - @ `GameScene.java:42-45`
- [x] Dark background (#0a0a0c) - @ `GameScene.java:217-218`

### 6.2 HUD Elements ✅
- [x] Round timer - Press Start 2P - @ `GameScene.java:68-74`
- [x] Player count - Press Start 2P - @ `GameScene.java:76-82`
- [x] Round info - Cinzel - @ `GameScene.java:84-92`

### 6.3 Fullscreen Button ✅
- [x] Top-right corner placement - @ `GameScene.java:126-169`

### 6.4 onEnter() / onExit() Methods ✅
- [x] Start/stop game loop - @ `GameScene.java:176-213`

---

## STEP 7: ResultScene Implementation ✅

### 7.1 Winner Display ✅
- [x] Crown icon (👑) - @ `ResultScene.java:107-110`
- [x] "WINNER" label - Press Start 2P - @ `ResultScene.java:112-121`
- [x] Player name styling - Cinzel Decorative - @ `ResultScene.java:123-134`
- [x] Gold text shadow glow effect - @ `ResultScene.java:130-134`
- [x] Stats line - Crimson Pro italic - @ `ResultScene.java:145-151`

### 7.2 Action Buttons ✅
- [x] Play Again button (gold variant) - @ `ResultScene.java:184-185`
- [x] Main Menu button - @ `ResultScene.java:188-189`
- [x] Quit button - @ `ResultScene.java:192-193`
- [x] Same styling as MenuScene buttons - @ `ResultScene.java:199-261`

### 7.3 Fullscreen Button ✅
- [x] Top-right fullscreen toggle button - @ `ResultScene.java:274-318`

---

## STEP 8: Fullscreen Toggle Implementation ✅

### 8.1 Global Fullscreen Support ✅
- [x] Add to SceneManager: `toggleFullscreen()` method - @ `SceneManager.java:85-88`
- [x] Add to SceneManager: `isFullscreen()` getter - @ `SceneManager.java:93-95`
- [x] F11 key binding in each scene - @ `SceneManager.java:100-118`
- [x] Fullscreen button component reusable across all scenes

### 8.2 Fullscreen Button UI ✅
- [x] Icon: ⛶ (fullscreen symbol)
- [x] Position: Top-right (20px from edges)
- [x] Size: 32x32px
- [x] Style: Stone panel background, gold border

---

## Color Reference Summary

| Purpose | Color |
|---------|-------|
| Gold primary | #c9a84c |
| Gold light | #e8c86a |
| Gold dark | #8a6a1a |
| Stone dark | #0d0d10 |
| Stone panel | #1c1c26 |
| Stone border | #2a2a36 |
| Torch orange | #e8743c |
| Text parchment | #e8dfc4 |
| Text muted | #7a7060 |
| Text body | #b0a890 |
| Button idle | #3b4663 |
| Chaos purple | #7c5cbf |
| Safe green | #4a8c5c |
| Danger red | #8c3a3a |

## Font Reference Summary

| Element | Font | Size |
|---------|------|------|
| Logo | Cinzel Decorative | 48-72px |
| Buttons | Cinzel | 14px |
| Headers | Cinzel | 18px |
| Section titles | Cinzel | 11px |
| Body text | Crimson Pro | 14-16px |
| Badges/HUD | Press Start 2P | 6-9px |
| Tips | Crimson Pro italic | 15px |
