## Plan: Port Rummikub Tracker to Native Android (Material 3)

**TL;DR:** Port the React PWA to a native Android app using Kotlin + Jetpack Compose + Material 3. The app has 7 views, complex timer logic with extensions, audio/TTS, localization (3 languages), and localStorage persistence — all must be recreated natively. Recommended approach: single-activity architecture with Jetpack Navigation Compose, Room database for persistence, and ViewModels per screen.

---

### Phase 1: Project Scaffolding & Architecture

**Step 1** — Create Android project with Kotlin, Jetpack Compose, Material 3
- Min SDK 26, Target SDK 34, Kotlin 2.0+
- Add dependencies: Compose BOM, Navigation Compose, Room, DataStore, CameraX, Hilt (DI)
- Configure Material 3 theme with dynamic colors (Material You) and the Indigo/Purple color scheme as fallback
- Set up Hilt DI modules

**Step 2** — Define data layer (Room + DataStore)
- Room entities: `GameEntity`, `PlayerEntity`, `RoundEntity`, `GamePlayerCrossRef`
- Room DAOs with all CRUD operations (active game, history, saved players)
- DataStore for preferences: uiLanguage, ttsLanguage, theme, timerDuration, scrollLock, extensionReplenishRounds, gameNumberSeq, preferredSettings
- Repository classes wrapping DAOs + DataStore

**Step 3** — Set up navigation graph (Jetpack Navigation Compose)
- 7 destinations: Home, NewGame, PlayerSelection, ActiveGame, ManagePlayers, GameHistory, Settings
- NavHost with type-safe navigation arguments (game ID, etc.)

---

### Phase 2: Core Business Logic (Domain Layer)

**Step 4** — Timer engine (`TimerEngine.kt`)
- `StateFlow<Long>` for remaining milliseconds, counting down every second
- States: Running, Paused, Stopped
- Callbacks/integration: tick sound at ≤10s, auto-advance at 0s
- Extension logic: add 30,000ms on extend, track used extensions per player
- Extension replenishment: every N rounds, increment maxExtensions for all players

**Step 5** — Audio engine (`AudioEngine.kt`)
- `MediaPlayer` / `SoundPool` for tick-tock, turn notification, extend sound, victory melody (pre-generated WAV/OGG resources)
- `TextToSpeech` wrapper for player name announcements with language selection
- Audio focus handling for interruptions

**Step 6** — Game logic domain classes
- `GameManager` use case: start game, save round, end game, cancel game, delete game
- `PlayerManager` use case: CRUD saved players, add to game, reorder, image compression (max 200x200 JPEG)
- `ScoreValidator`: enforce all-scores-entered, exactly-one-zero-per-round rules
- `WinnerCalculator`: lowest cumulative total wins

---

### Phase 3: UI — Screen by Screen

**Step 7** — HomeScreen (`HomeScreen.kt`)
- Material 3 `TopAppBar` with app title
- Conditional "Game in Progress" card (resume → ActiveGame, cancel → confirmation dialog)
- 4 action buttons in a grid: New Game, Manage Players, Game History, Settings
- *Parallel with Step 8*

**Step 8** — NewGameScreen (`NewGameScreen.kt`)
- Game name `TextField` (optional, auto-generated with unique suffix)
- Timer duration: `ExposedDropdownMenu` with presets (30s, 45s, 1m, 1.5m, 2m, 3m, 5m)
- Max extensions: `Slider` or dropdown (0-10)
- Extension replenishment: toggle + every-N-rounds selector (3, 4, 5, 6)
- TTS language dropdown (10 languages)
- Player roster: `LazyColumn` with drag-to-reorder (using `detectDragGestures`), image upload (CameraX / gallery), quick-add from saved players
- "Start Game" → validates min 2 players → opens PlayerSelectionScreen
- *Parallel with Step 7*

**Step 9** — PlayerSelectionScreen (`PlayerSelectionScreen.kt`)
- Modal/bottom sheet with player grid
- Tap to select starting player → transitions to ActiveGameScreen

**Step 10** — ActiveGameScreen (`ActiveGameScreen.kt`) — most complex screen
- Top bar: game name, round number, elapsed time, close/cancel button
- **Timer section (non-winner state):**
  - `AnalogClockComposable`: Canvas-based SVG-like clock with color zones (blue >15s, yellow 10-15s, red ≤10s, glow effect), hand, tick marks, digital time text, "PAUSED" indicator
  - Current player card (indigo/purple gradient) with avatar, name, Trophy button, Skip button
  - Extension button with remaining-count badge, pulsating animation (≤15s), green flash on extend
  - Play/Pause, Reset, Duration dropdown controls
- **Winner declaration state:**
  - Full-screen celebration: Canvas-based confetti animation (falling + sway), trophy icon, player avatar + name, yellow/amber gradient card
  - Score entry form: per-player `TextField` with camera scan button (CameraX → external API → auto-fill)
  - "Save Round" button (enabled only when all scores entered)
- **Score summary table:**
  - Horizontally scrollable `LazyRow` with sticky first column (Round #)
  - Editable cells (tap to edit past scores)
  - Cumulative totals row at bottom
- **Player cards grid:** 2-column `LazyVerticalGrid`, reorder buttons (▲/▼)
- **End Game button:** confirmation dialog → saves to history → navigates to Home

**Step 11** — ManagePlayersScreen (`ManagePlayersScreen.kt`)
- `LazyColumn` of saved players with avatars
- Swipe-to-delete or trash icon
- Empty state with message

**Step 12** — GameHistoryScreen (`GameHistoryScreen.kt`)
- `LazyColumn` of completed games, expandable cards
- Expanded view: full score table, round winners highlighted, winner name, date, rounds count
- Screenshot capture button (render view to bitmap → share/save)
- Delete game with confirmation

**Step 13** — SettingsScreen (`SettingsScreen.kt`)
- UI Language selector (English, Deutsch, Français)
- Voice Language selector (10 languages)
- Dark/Light theme toggle (or system default)
- "Clear All Data" destructive button with confirmation dialog

---

### Phase 4: Cross-Cutting Concerns

**Step 14** — Localization system
- String resources for all 108+ translation keys in `values/strings.xml` (en), `values-de/`, `values-fr/`
- `Strings.kt` helper for parameterized strings with `{{key}}` replacement
- *Parallel with Steps 7-13*

**Step 15** — Image handling
- Camera integration: `ActivityResultContracts.TakePicture` + gallery picker
- Image compression: resize to max 200x200, JPEG quality 0.8, convert to Base64 for API upload
- Player avatar composable with fallback to initial letter

**Step 16** — Tile recognition API integration
- `OkHttp` + `Retrofit` for `POST https://rummikub.lorus.org/api/analyze`
- Multipart form-data with image file
- Parse `total_score` from JSON response, auto-fill score field
- Loading/error states per player row

**Step 17** — Fullscreen & scroll lock (Android-specific)
- `WindowInsetsController` for immersive mode toggle
- Scroll lock within ActiveGame view

---

### Phase 5: Polish & Testing

**Step 18** — Animations
- Confetti: `Canvas` with particle system (position, velocity, rotation, opacity)
- Clock glow: `drawCircle` with `BlurMaskFilter` when time is low
- Pulsate extension button: `InfiniteTransition` with scale animation
- Extend flash: green overlay `Animatable` color

**Step 19** — Unit tests
- `TimerEngineTest`: countdown, pause/resume, extend, auto-advance
- `GameManagerTest`: start, save round, end game, validation
- `WinnerCalculatorTest`: edge cases

**Step 20** — UI tests (Compose testing)
- Screen-level tests for navigation, form validation, timer display

---

### Relevant Files (Web → Android mapping)

| Web File | Android Equivalent |
|---|---|
| `src/hooks/useGameData.js` | `GameRepository.kt` + `GameViewModel.kt` |
| `src/hooks/useGameFlow.js` | `GameManager.kt` (use case) |
| `src/hooks/useTimer.js` + `useTimerControl.js` | `TimerEngine.kt` |
| `src/hooks/useAudio.js` | `AudioEngine.kt` |
| `src/hooks/useLocalization.js` | `strings.xml` + `LocaleHelper.kt` |
| `src/hooks/useTheme.jsx` | Material 3 dynamic colors |
| `src/constants/config.js` | `Config.kt` constants object |
| `src/utils/helpers.js` | `Extensions.kt` utility functions |
| `src/locales/en.js` | `values/strings.xml` |
| `src/components/AnalogClock.jsx` | `AnalogClock.kt` composable |
| `src/components/Confetti.jsx` | `ConfettiEffect.kt` composable |
| `src/components/PlayerCard.jsx` | `PlayerCard.kt` composable |
| `src/components/PlayerAvatar.jsx` | `PlayerAvatar.kt` composable |
| `src/components/views/ActiveGameView.jsx` | `ActiveGameScreen.kt` |
| `src/components/views/HomeView.jsx` | `HomeScreen.kt` |
| `src/components/views/NewGameView.jsx` | `NewGameScreen.kt` |
| `src/components/views/GameHistoryView.jsx` | `GameHistoryScreen.kt` |
| `src/components/views/ManagePlayersView.jsx` | `ManagePlayersScreen.kt` |
| `src/components/views/SettingsView.jsx` | `SettingsScreen.kt` |

### Data Models (Room)

```
GameEntity: id (Long, PK), name (String), status (String), startTime (Long),
  endTime (Long?), winner (String?), timerDuration (Int), originalTimerDuration (Int),
  maxExtensions (Int), ttsLanguage (String), currentPlayerIndex (Int)

PlayerEntity: name (String, PK), imagePath (String?)

RoundEntity: id (Long, PK, auto), gameId (Long, FK), roundNumber (Int),
  timestamp (Long)

RoundScoreEntity: roundId (Long, FK), playerName (String), score (Int)
```

### Verification

1. Run all unit tests: `./gradlew test`
2. Run instrumented tests: `./gradlew connectedAndroidTest`
3. Manual test: start game → play rounds with timer, extensions, winner declaration, score editing
4. Manual test: end game → verify history entry, resume works, cancel works
5. Manual test: all 3 languages, dark/light theme, TTS audio, sound effects
6. Manual test: camera tile scanning with API

### Decisions

- **Database**: Room (SQLite) instead of localStorage — provides type safety, migrations, and query power
- **DI**: Hilt — standard for Android, good Compose integration
- **Navigation**: Jetpack Navigation Compose — type-safe, supports deep links
- **Audio**: Pre-bundled OGG files for sounds (tick, turn, extend, victory) instead of Web Audio API oscillators — simpler, more reliable
- **TTS**: Android `TextToSpeech` API — direct equivalent to Web Speech API
- **Image storage**: Internal storage files (not Room BLOBs) with path references in DB — better performance for large images
- **Min SDK 26**: Allows `java.time` usage, good device coverage (~95%)
- **Scope excluded**: Cloud sync, statistics dashboard, tournament mode, CSV export (match web roadmap)

### Further Considerations

1. The analog clock is the most complex UI component — recommend implementing with Compose `Canvas` drawing primitives rather than trying to use a library
2. Confetti animation should be a standalone composable with `LaunchedEffect` and `Animatable` — the web version uses CSS keyframes which need a different approach on Android
3. Camera tile scanning requires the external API — ensure network permissions and error handling are robust, as the camera integration adds complexity (permissions, file handling)
