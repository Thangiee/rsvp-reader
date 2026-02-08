# RSVP Reader Architecture

## What This Is

A fullstack Scala 3 speed-reading app (Rapid Serial Visual Presentation). Words are flashed one at a time at a configurable WPM, with the eye's optimal recognition point highlighted.

## Tech Stack

- **Scala 3.7.4**, sbt 1.10.7
- **Backend:** Kyo 1.0-RC1 (`kyo-tapir`) — serves static assets via Netty
- **Frontend:** ScalaJS 1.19.0 + Laminar 17.2.1 for reactive UI, Kyo for async effects
- **Shared:** Cross-compiled module (`CrossType.Pure`) — domain types used by both JVM and JS
- **Testing:** MUnit (JVM only, uses `kyo-direct` + `kyo-combinators` test deps)

## Module Layout

```
rsvp-reader/
├── build.sbt
├── shared/                        # Cross-compiled (JVM + JS)
│   └── src/main/scala/rsvpreader/
│       ├── token/
│       │   ├── Token.scala        # Word unit with ORP index, punctuation, sentence/paragraph indices
│       │   ├── Tokenizer.scala    # Parses raw text → Span[Token]
│       │   └── Punctuation.scala  # Enum: None, Comma, Period, Paragraph
│       ├── playback/
│       │   ├── PlaybackEngine.scala # Async playback loop with command handling
│       │   ├── Command.scala      # Enum: Pause, Resume, RestartSentence, RestartParagraph, SetSpeed(wpm)
│       │   ├── ViewState.scala    # Immutable UI snapshot: tokens, index, status, wpm
│       │   ├── PlayStatus.scala   # Enum: Playing, Paused, Finished
│       │   └── Delay.scala        # calculateDelay(token, config) → Duration
│       ├── config/
│       │   ├── RsvpConfig.scala   # Timing config: WPM, delays, feature flags
│       │   ├── CenterMode.scala   # Enum: ORP, First, None
│       │   └── KeyBindings.scala  # Customizable keyboard shortcut map
│       ├── viewmodel/
│       │   ├── OrpLayout.scala    # Pure ORP offset/split computation
│       │   ├── WordDisplay.scala  # Word with CSS class and current flag
│       │   ├── SentenceWindow.scala # Sentence context paging logic
│       │   └── KeyDispatch.scala  # Key → action resolution (modal/capture aware)
│       └── state/
│           ├── DomainModel.scala  # App state record + derived computations
│           ├── Action.scala       # Enum: all state transitions
│           ├── Reducer.scala      # Pure (DomainModel, Action) → DomainModel
│           └── Persistence.scala  # Trait + InMemoryPersistence for tests
├── backend/
│   └── src/main/scala/rsvpreader/
│       └── Main.scala             # KyoApp serving static files via Tapir
└── frontend/
    └── src/main/scala/rsvpreader/
        ├── Main.scala             # KyoApp entry point — state manager + engine loop + DOM wiring
        ├── LocalStoragePersistence.scala  # Browser localStorage Persistence impl
        └── ui/
            ├── DomainContext.scala # Read-only domain signal + dispatch/command closures
            ├── UiState.scala      # Transient UI state (modals, input text, key capture)
            ├── Components.scala   # UI elements using shared view models
            ├── Layout.scala       # Page structure composition
            └── Settings.scala     # Settings modal dispatching Actions
```

## State Management

### Two State Categories

**DomainModel** — persisted, reduced state:
- `viewState: ViewState` — current playback snapshot (driven by engine)
- `centerMode: CenterMode` — ORP/First/None alignment
- `keyBindings: KeyBindings` — customizable shortcuts
- `contextSentences: Int` — sentence context window size

Flows through: `Action → actionCh → state manager fiber → Reducer → modelVar`

**UiState** — transient, synchronous state:
- `showTextInputModal`, `showSettingsModal` — modal visibility
- `inputText` — text area content
- `loadError` — tokenization error
- `capturingKeyFor` — which key binding is being captured

Plain `LaminarVar` instances, no channels or reducer.

### Dependency Passing

Components receive explicit parameters instead of accessing globals:
- `DomainContext` — `model: Signal[DomainModel]`, `dispatch: Action => Unit`, `sendCommand`, `togglePlayPause`, `adjustSpeed`
- `UiState` — plain case class with `LaminarVar` fields

No global mutable state. `DomainContext` is created once in `Main` and threaded to all components.

## Data Flow

### 1. Text Input → Tokenization

```
User pastes text → "Start Reading" button
  → onTextLoaded(text: String)           [DOM callback in frontend/Main.scala]
    → Tokenizer.tokenize(text)           [shared — pure function]
    → Span[Token]                        [immutable array of word units]
    → tokensCh.unsafe.offer(tokens)      [non-blocking put into channel]
```

`Tokenizer.tokenize` produces a `Span[Token]` where each token has:
- `text` — the word
- `focusIndex` — ORP at ~1/3 of word length
- `punctuation` — trailing punctuation type (affects delay)
- `sentenceIndex` / `paragraphIndex` — for context display and navigation

### 2. Engine Loop (Session Manager)

Lives in `frontend/Main.scala`. Runs inside `KyoApp.run { }` where Kyo async effects work.

```
KyoApp.run {
  direct {
    init configRef, commandCh, tokensCh
    Fiber.init(stateManagerLoop)        [state manager fiber in background]
    engineLoop:
      Loop forever:
        tokens ← tokensCh.take          [blocks until user loads text]
        stateCh = new Channel[ViewState]
        engine = PlaybackEngine(commandCh, stateCh, configRef)
        consumerFiber = Fiber.init(stateConsumerLoop(stateCh))
        engine.run(tokens)               [blocks until playback finishes]
        stateCh.close → flush remaining  [ensures final state reaches UI]
        loop back
  }
}
```

**Why this exists:** DOM callbacks cannot run Kyo async effects. `KyoApp.run()` called from a callback returns immediately without executing. The solution is keeping the engine loop inside the initial `run` block and using channels to bridge from DOM callbacks to the Kyo async world.

### 3. Playback Engine (Word Iterator)

Lives in `shared/playback/PlaybackEngine.scala`. Runs as an async loop using `kyo.Loop`.

```
State machine:  Playing ←→ Paused
                Playing → Finished (all tokens done)

run(tokens):
  emit(initial Paused state)
  optional startDelay sleep
  playbackLoop:
    Loop(state):
      match state.status:
        Finished → exit
        Paused  → emit(state), commands.take, apply command, continue
        Playing →
          if no more tokens → emit(Finished, index=last), exit
          emit(state)
          read config from AtomicRef (enables mid-playback config changes)
          Async.race(sleep(delay), commands.take)
            sleep wins  → advance index, check paragraphAutoPause, continue
            command wins → apply command, continue
```

**Key design:** `Async.race(sleep, commands.take)` makes the engine instantly responsive to commands mid-word. Without this, the user would have to wait for the current word's delay to finish before pause/back takes effect.

### 4. State Updates → UI

```
PlaybackEngine               stateConsumerLoop            State Manager Fiber        Laminar UI
──────────────               ─────────────────            ───────────────────        ──────────
stateCh.put(ViewState) ──→ Action.EngineStateUpdate ──→ actionCh ──→ Reducer ──→ modelVar.set(newModel)
                                                                                       │
                                                                                model.signal ──→ reactive DOM
```

The state manager fiber (`stateManagerLoop`) is the single writer to `modelVar`. It:
1. Takes `Action` from `actionCh`
2. Applies `Reducer(model, action)` — pure function
3. Sets `modelVar` — single write point
4. Side effects: forwards `PlaybackCmd` to `commandCh`, persists settings changes via `Persistence`

### 5. Commands (UI → Engine)

```
User clicks button / presses key
  → domain.sendCommand(cmd)             [unsafe.offer — non-blocking]
    → commandCh ──→ PlaybackEngine picks up in Async.race or handlePaused
```

Command dispatch uses `Channel.unsafe.offer` (non-blocking, drops if full). This is acceptable because the engine consumes commands quickly.

`togglePlayPause()` checks `modelVar.now().viewState.status` to decide whether to send `Pause` or `Resume`. When status is `Finished`, it re-sends the current tokens through `tokensCh` to start a fresh playback session.

## Channels

| Channel | Capacity | Direction | Purpose |
|---------|----------|-----------|---------|
| `actionCh: Channel[Action]` | 8 | Components → State Manager | All state transitions |
| `commandCh: Channel[Command]` | 1 | State Manager → PlaybackEngine | Playback commands |
| `tokensCh: Channel[Span[Token]]` | 1 | UI → Engine Loop | Sends tokenized text |
| `stateCh: Channel[ViewState]` | 1 | PlaybackEngine → Consumer | State snapshots (per session) |

`actionCh`, `commandCh`, `tokensCh` are created outside `run` via `Channel.Unsafe.init[T](n).safe`. `stateCh` is created per playback session inside `engineLoop` and closed explicitly after the engine finishes.

## Shared Config via AtomicRef

`AtomicRef[RsvpConfig]` is shared between the UI and engine. The engine reads it each tick (`configRef.get`), so config changes (WPM, delays, paragraph auto-pause) take effect mid-playback without restarting.

**Gotcha:** `AtomicRef.init(v)` returns `AtomicRef[T] < Sync` (effectful). `AtomicRef#get` returns `T < Sync` — not pure.

## Pure View Models (shared/viewmodel/)

Computation extracted from frontend Components into testable pure functions:

| View Model | Purpose | Replaces |
|------------|---------|----------|
| `OrpLayout.compute(token, centerMode)` | ORP offset/split for focus word | Inline math in Components.orpWordView |
| `SentenceWindow.compute(tokens, index, n)` | Sentence context paging | 30+ lines in Components.sentenceContext |
| `KeyDispatch.resolve(key, bindings, modals, capturing)` | Key → action mapping | Inline logic in Components.keyboardHandler |

All are pure functions taking domain types, returning value objects. Tested on JVM with MUnit.

## UI Layer (Laminar)

**Components** are pure Laminar elements that receive `DomainContext` and `UiState` as parameters. They bind to `domain.model` signal for reactive rendering and call `domain.dispatch`, `domain.sendCommand`, etc. for user interactions.

The focus word display switches between:
- **Playing:** Single word with ORP highlighting (before/focus/after spans) + sentence context below
- **Paused:** Scrollable full-text view with current word highlighted, sentence context hidden
- **Finished:** Same as Paused — full-text view with last word highlighted. Pressing play restarts from beginning.
- **Paused (no tokens):** "READY TO READ" placeholder

**Keyboard handling:** `Components.keyboardHandler` uses `KeyDispatch.resolve` from shared/ to map key presses to actions. Guards against capturing when settings modal is open.

**Import disambiguation:** Laminar's `Var`, `Signal`, `Span` collide with Kyo's. The frontend uses:
```scala
import com.raquo.laminar.api.L.{Var as LaminarVar, Signal as LaminarSignal, Span as _, *}
```

## Kyo Patterns Used

| Pattern | Where | Why |
|---------|-------|-----|
| `direct { expr.now }` | Main.scala, tests | Imperative-looking effectful code |
| `Async.race(a, b)` | PlaybackEngine handlePlaying | Instant command response during word sleep |
| `Channel` (capacity 1) | Everywhere | Backpressured communication between fibers |
| `Channel.close` | engineLoop | Clean shutdown + flush remaining states |
| `AtomicRef` | Config sharing | Lock-free concurrent config reads |
| `Fiber.init` + `Abort.run[Closed]` | engineLoop, stateManagerLoop | Background fibers with clean exit on channel close |
| `Loop(state) { ... continue/done }` | PlaybackEngine, engineLoop, stateManagerLoop | Stack-safe state machine loops |
| `KyoApp.run { }` | Main entry point | Bootstrap Kyo runtime (handles Async, Sync, Scope, etc.) |
| `Channel.Unsafe.init[T](n).safe` | Main bootstrap | Create channels outside `run` block for DOM callback access |
| `AllowUnsafe.embrace.danger` | Frontend Main | Required for `unsafe.offer` from DOM callbacks |

## Persistence

`Persistence` trait (in shared/state/) defines load/save operations with `Sync` effect:

```scala
trait Persistence:
  def load: DomainModel < Sync
  def save(model: DomainModel): Unit < Sync
  def savePosition(textHash: Int, index: Int): Unit < Sync
  def loadPosition: Option[(Int, Int)] < Sync
```

Implementations:
- `LocalStoragePersistence` (frontend/) — browser localStorage, reads/writes JSON-like key-value pairs
- `InMemoryPersistence` (shared/) — `mutable.Map`-backed, used in tests

The state manager fiber calls `persistence.save(model)` for settings changes. Position is saved on pause, playback finish, and `beforeunload`.

## Testing

Tests live in `shared/.jvm/src/test/scala/rsvpreader/` (mirroring source sub-packages) and use MUnit:

| Suite | Tests | What it covers |
|-------|-------|----------------|
| `playback/PlaybackEngineSuite` | 14 | Engine state machine, commands, timing |
| `state/ReducerSuite` | 12 | All action types, state transitions |
| `viewmodel/OrpLayoutSuite` | 5 | ORP offset/split computation |
| `viewmodel/SentenceWindowSuite` | 6 | Sentence context paging |
| `viewmodel/KeyDispatchSuite` | 6 | Key resolution with modal/capture guards |
| `state/PersistenceSuite` | 4 | InMemoryPersistence round-trips |
| `token/TokenizerSuite` | Various | Tokenization edge cases |
| Other domain suites | Various | Token, Delay, Punctuation, Config |

**Two config profiles for PlaybackEngine:**
- `instantConfig` — `Int.MaxValue` WPM, zero delays. Fully command-driven, no timing.
- `fastConfig` — 6000 WPM (~10ms/word). Allows `Async.sleep` interleaving for command tests.

**Time control:** Tests needing precise timing use `Clock.withTimeControl` to advance virtual time. Key gotcha: `Async.sleep` inside `withTimeControl` uses the virtual clock — use `Sync.defer(Thread.sleep(10))` as a real-time tick to yield to the scheduler.

**Test runner:**
```bash
sbt "sharedJVM/test"                                    # all tests
sbt "sharedJVM/testOnly rsvpreader.playback.PlaybackEngineSuite"  # specific suite
```

## Build & Run

```bash
sbt frontend/fastLinkJS    # compile frontend to JS (required first)
sbt backend/run             # start server on 127.0.0.1:8080
sbt ~frontend/fastLinkJS    # watch mode for frontend dev
sbt test                    # all tests
sbt compile                 # compile everything
```
