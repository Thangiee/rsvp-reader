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
│       ├── Token.scala            # Word unit with ORP index, punctuation, sentence/paragraph indices
│       ├── Tokenizer.scala        # Parses raw text → Span[Token]
│       ├── Punctuation.scala      # Enum: None, Comma, Period, Paragraph
│       ├── PlayStatus.scala       # Enum: Playing, Paused, Stopped
│       ├── Command.scala          # Enum: Pause, Resume, Back(n), RestartSentence, SetSpeed(wpm), Stop
│       ├── ViewState.scala        # Immutable UI snapshot: tokens, index, status, wpm
│       ├── RsvpConfig.scala       # Timing config: WPM, delays, feature flags
│       ├── Delay.scala            # calculateDelay(token, config) → Duration
│       ├── PlaybackEngine.scala   # Async playback loop with command handling
│       ├── CenterMode.scala       # Enum: ORP, First, None
│       └── KeyBindings.scala      # Customizable keyboard shortcut map
├── backend/
│   └── src/main/scala/rsvpreader/
│       └── Main.scala             # KyoApp serving static files via Tapir
└── frontend/
    └── src/main/scala/rsvpreader/
        ├── Main.scala             # KyoApp entry point — engine loop + DOM wiring
        └── ui/
            ├── AppState.scala     # Centralized reactive state (LaminarVar) + command dispatch
            ├── Components.scala   # Reusable UI elements (buttons, focus word, progress bar)
            ├── Layout.scala       # Page structure composition
            └── Settings.scala     # Settings modal (keybindings, center mode)
```

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

Lives in `shared/PlaybackEngine.scala`. Runs as an async loop using `kyo.Loop`.

```
State machine:  Playing ←→ Paused → Stopped
                              ↑          ↑
                          (commands)  (all tokens done)

run(tokens):
  emit(initial Paused state)
  optional startDelay sleep
  playbackLoop:
    Loop(state):
      match state.status:
        Stopped → exit
        Paused  → emit(state), commands.take, apply command, continue
        Playing →
          if no more tokens → emit(Stopped), exit
          emit(state)
          read config from AtomicRef (enables mid-playback config changes)
          Async.race(sleep(delay), commands.take)
            sleep wins  → advance index, check paragraphAutoPause, continue
            command wins → apply command, continue
```

**Key design:** `Async.race(sleep, commands.take)` makes the engine instantly responsive to commands mid-word. Without this, the user would have to wait for the current word's delay to finish before pause/back takes effect.

### 4. State Updates → UI

```
PlaybackEngine                     frontend/Main.scala              Laminar UI
──────────────                     ───────────────────              ──────────
stateOut.put(ViewState) ──→ stateCh ──→ stateConsumerLoop ──→ AppState.viewState.set(state)
                                                                     │
                                                              viewState.signal ──→ reactive DOM
```

The `stateConsumerLoop` runs as a background fiber. When the engine finishes, `stateCh.close` ensures any buffered final state (like Stopped) reaches the UI and signals the consumer to exit via `Abort[Closed]`.

### 5. Commands (UI → Engine)

```
User clicks button / presses key
  → AppState.sendCommand(cmd)        [unsafe.offer — non-blocking]
    → commandCh ──→ PlaybackEngine picks up in Async.race or handlePaused
```

All command dispatch goes through `AppState.sendCommand` which uses `Channel.unsafe.offer`. This is non-blocking and returns `false` if the channel is full (capacity 1), silently dropping the command. This is acceptable because the engine consumes commands quickly.

`togglePlayPause()` checks `viewState.now().status` to decide whether to send `Pause` or `Resume` — this is why the engine must emit state when entering Paused, so the UI has the correct status.

## Channels (Capacity 1, All Unscoped)

| Channel | Direction | Purpose |
|---------|-----------|---------|
| `tokensCh: Channel[Span[Token]]` | UI → Engine Loop | Sends tokenized text to start playback |
| `commandCh: Channel[Command]` | UI → PlaybackEngine | Sends pause/resume/back/speed commands |
| `stateCh: Channel[ViewState]` | PlaybackEngine → UI | Emits state snapshots for reactive rendering |

All created with `Channel.initUnscoped` to avoid being closed when `KyoApp.run`'s scope ends. `stateCh` is created per-playback session and closed explicitly after the engine finishes.

## Shared Config via AtomicRef

`AtomicRef[RsvpConfig]` is shared between the UI and engine. The engine reads it each tick (`configRef.get`), so config changes (WPM, delays, paragraph auto-pause) take effect mid-playback without restarting.

**Gotcha:** `AtomicRef.init(v)` returns `AtomicRef[T] < Sync` (effectful). `AtomicRef#get` returns `T < Sync` — not pure.

## UI Layer (Laminar)

**Reactive state** is in `AppState` using `LaminarVar[T]`:
- `viewState` — current playback snapshot (driven by engine via state channel)
- `showParagraphView`, `showTextInputModal`, `showSettingsModal` — modal visibility
- `currentKeyBindings`, `currentCenterMode` — persisted to localStorage

**Derived signals** in `AppState`:
- `progressPercent`, `timeRemaining`, `wordProgress` — computed from viewState
- `focusContainerCls`, `statusDotCls`, `statusText` — CSS class bindings

**Components** are pure Laminar elements that bind to signals. The focus word display switches between:
- **Playing:** Single word with ORP highlighting (before/focus/after spans) + sentence context below
- **Paused:** Scrollable full-text view with current word highlighted, sentence context hidden

**Keyboard handling:** `Components.keyboardHandler` maps key presses to actions via configurable `KeyBindings`. Guards against capturing when settings modal is open.

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
| `Fiber.init` + `Abort.run[Closed]` | engineLoop consumer | Background fiber with clean exit on channel close |
| `Loop(state) { ... continue/done }` | PlaybackEngine, engineLoop | Stack-safe state machine loops |
| `KyoApp.run { }` | Main entry point | Bootstrap Kyo runtime (handles Async, Sync, Scope, etc.) |
| `AllowUnsafe.embrace.danger` | Frontend Main | Required for `unsafe.offer` from DOM callbacks |

## Testing

Tests live in `shared/.jvm/src/test/scala/rsvpreader/PlaybackEngineSuite.scala` and use a `Harness` that wires up channels + engine for isolated testing.

**Two config profiles:**
- `instantConfig` — `Int.MaxValue` WPM, zero delays. Fully command-driven, no timing.
- `fastConfig` — 6000 WPM (~10ms/word). Allows `Async.sleep` interleaving for command tests.

**Time control:** Tests needing precise timing use `Clock.withTimeControl` to advance virtual time. Key gotcha: `Async.sleep` inside `withTimeControl` uses the virtual clock — use `Sync.defer(Thread.sleep(10))` as a real-time tick to yield to the scheduler.

**Test runner:**
```bash
sbt "sharedJVM/test"                                    # all tests
sbt "sharedJVM/testOnly rsvpreader.PlaybackEngineSuite"  # specific suite
```

## Build & Run

```bash
sbt frontend/fastLinkJS    # compile frontend to JS (required first)
sbt backend/run             # start server on 127.0.0.1:8080
sbt ~frontend/fastLinkJS    # watch mode for frontend dev
sbt test                    # all tests
sbt compile                 # compile everything
```
