# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

RSVP Reader is a fullstack Scala 3 web application for speed reading (Rapid Serial Visual Presentation). It uses a three-module sbt build where shared code cross-compiles to JVM and JavaScript.

## Tech Stack

- **Scala 3.7.4** with **sbt 1.10.7**
- **Backend:** Kyo 1.0-RC1 effect system with kyo-tapir (Tapir + Netty)
- **Frontend:** Laminar 17.2.1 on ScalaJS 1.19.0, Kyo for async effects
- **Shared:** Cross-compiled via sbt-crossproject (CrossType.Pure)
- **Testing:** MUnit

## Build & Run Commands

```bash
# Compile everything
sbt compile

# Compile frontend to JS (required before running backend)
sbt frontend/fastLinkJS

# Run backend server (serves on 127.0.0.1:8080)
sbt backend/run

# Watch mode for frontend development
sbt ~frontend/fastLinkJS

# Run all tests
sbt test

# Run specific test suite
sbt "sharedJVM/testOnly rsvpreader.TokenizerSuite"

# Cross-compile shared module
sbt sharedJVM/compile sharedJS/compile
```

Dev workflow: compile frontend JS first, then start backend. The backend serves `frontend/index.html` and compiled JS directly from the filesystem.

## Architecture

```
shared (JVM + JS)  ←── backend (JVM only)
                   ←── frontend (JS only)
```

### Module Responsibilities

**shared/** — Platform-independent RSVP domain types and logic:
- `Token`, `Tokenizer` — word units with ORP index, punctuation, sentence/paragraph indices
- `Command`, `PlaybackEngine`, `ViewState` — playback loop with async command handling
- `RsvpConfig`, `Delay` — timing configuration and delay calculation
- `viewmodel/` — pure view model computations (OrpLayout, SentenceWindow, KeyDispatch)
- `state/` — domain model, actions, reducer, persistence trait (`DomainModel`, `Action`, `Reducer`, `Persistence`)

**backend/** — Kyo `KyoApp` serving static assets via Tapir routes

**frontend/** — Laminar UI with Kyo async playback:
- `Main` — KyoApp entry point: state manager fiber, engine loop, channel wiring
- `LocalStoragePersistence` — browser localStorage impl of `Persistence` trait
- `ui/DomainContext` — read-only domain state signal + dispatch/command closures
- `ui/UiState` — transient UI-only state (modals, input text, capturing key)
- `ui/Components` — reusable UI elements using view models from shared/
- `ui/Layout` — page structure composition
- `ui/Settings` — settings modal dispatching `Action` variants

### State Management

State is split into two categories:
- **DomainModel** — playback state, settings (centerMode, keyBindings, contextSentences). Flows through `actionCh → Reducer → modelVar`. Pure reducer function, testable on JVM.
- **UiState** — transient UI concerns (modal visibility, input text, key capture). Plain `LaminarVar` instances, synchronous.

Components receive `DomainContext` (read-only signal + dispatch) and `UiState` as explicit parameters. No global mutable state.

### Key Data Flow

1. `Tokenizer.tokenize(text)` produces `Span[Token]` with pre-computed ORP indices
2. `PlaybackEngine.run(tokens)` emits `ViewState` snapshots to a state channel
3. State consumer fiber dispatches `Action.EngineStateUpdate` → reducer → `modelVar`
4. UI sends commands through `Channel[Command]` (capacity 1, bounded)
5. `Async.race` between sleep and channel take enables instant response to pause/resume

## Kyo Patterns

- Entry point: `object Main extends KyoApp` with `run { direct { ... } }`
- Direct syntax: `direct { expr.now }` for imperative-looking effectful code
- Channel-based commands: `Channel.init[Command](1)` for backpressured communication
- Unsafe escape for UI callbacks: `import AllowUnsafe.embrace.danger` then `(using AllowUnsafe)`
- Effect types: `Unit < (Async & Abort[Closed])` for channel operations

### Prefer Kyo Data Types

Use Kyo's allocation-free data types over Scala stdlib equivalents:

| Stdlib | Kyo | Notes |
|--------|-----|-------|
| `Option[A]` / `Some` / `None` | `Maybe[A]` / `Present` / `Absent` | Unboxed opaque type. Use `Maybe(nullableValue)` for null-safety. Convert at stdlib boundaries: `maybe.toOption`, `Maybe.fromOption(opt)` |
| `Seq[A]` / `List[A]` / `Array[A]` | `Span[A]` or `Chunk[A]` | See Span vs Chunk guidance below |

refer to the /kyo skill.

## Laminar Patterns

- Reactive state: `LaminarVar[T]` with `.signal` for read-only access
- DOM binding: `child.text <-- signal`, `cls <-- signal.map(...)`
- Import disambiguation: `import com.raquo.laminar.api.L.{Var as LaminarVar, Signal as LaminarSignal, *}` to avoid collision with Kyo's `Signal`
- Render: `renderOnDomContentLoaded(container, rootElement)`

## RSVP Features

- ORP (Optimal Recognition Point) alignment at ~1/3 of word length
- Punctuation pauses: periods (300ms), commas (150ms), paragraphs (500ms)
- Word length adjustment: longer words get proportionally more display time
- Controls: play/pause, restart sentence/paragraph, speed adjustment (100-1000 WPM)
- Full-text view with current word highlighted when paused or finished
