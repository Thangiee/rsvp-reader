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

**shared/** — Platform-independent RSVP domain types:
- `Token` — word with ORP focus index, punctuation, sentence/paragraph indices
- `Tokenizer` — parses text into `Span[Token]`, calculates ORP at ~1/3 of word length
- `Command` — playback commands (Pause, Resume, Back, RestartSentence, SetSpeed, Stop)
- `RsvpConfig` — timing configuration (WPM, delays for punctuation/paragraphs)
- `calculateDelay` — computes display duration based on word length and punctuation

**backend/** — Kyo `KyoApp` serving static assets via Tapir routes

**frontend/** — Laminar UI with Kyo async playback:
- `PlaybackEngine` — async loop using `Async.race(sleep, channel.take)` for responsive pause
- `ViewState` — immutable snapshot for UI rendering
- `ui/AppState` — centralized reactive state with `LaminarVar`, command dispatch
- `ui/Components` — reusable UI elements (buttons, progress bar, focus word display)
- `ui/Layout` — page structure composition

### Key Data Flow

1. `Tokenizer.tokenize(text)` produces `Span[Token]` with pre-computed ORP indices
2. `PlaybackEngine.run(tokens)` starts async loop, notifies UI via callback
3. UI sends commands through `Channel[Command]` (capacity 1, bounded)
4. `Async.race` between sleep and channel take enables instant response to pause/resume

## Kyo Patterns

- Entry point: `object Main extends KyoApp` with `run { direct { ... } }`
- Direct syntax: `direct { expr.now }` for imperative-looking effectful code
- Channel-based commands: `Channel.init[Command](1)` for backpressured communication
- Unsafe escape for UI callbacks: `import AllowUnsafe.embrace.danger` then `(using AllowUnsafe)`
- Effect types: `Unit < (Async & Abort[Closed])` for channel operations

## Laminar Patterns

- Reactive state: `LaminarVar[T]` with `.signal` for read-only access
- DOM binding: `child.text <-- signal`, `cls <-- signal.map(...)`
- Import disambiguation: `import com.raquo.laminar.api.L.{Var as LaminarVar, Signal as LaminarSignal, *}` to avoid collision with Kyo's `Signal`
- Render: `renderOnDomContentLoaded(container, rootElement)`

## RSVP Features

- ORP (Optimal Recognition Point) alignment at ~1/3 of word length
- Punctuation pauses: periods (300ms), commas (150ms), paragraphs (500ms)
- Word length adjustment: longer words get proportionally more display time
- Controls: play/pause, back 10 words, restart sentence, speed adjustment (100-1000 WPM)
- Paragraph view modal showing full context with current word highlighted
