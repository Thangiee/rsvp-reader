# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

RSVP Reader is a fullstack Scala 3 web application for speed reading (Rapid Serial Visual Presentation). It uses a three-module sbt build: shared code cross-compiles to JVM and JavaScript.

## Tech Stack

- **Scala 3.7.4** with **sbt 1.10.7**
- **Backend:** Kyo 1.0-RC1 effect system with kyo-tapir (Tapir + Netty)
- **Frontend:** Laminar 17.2.1 on ScalaJS 1.19.0
- **Shared:** Cross-compiled via sbt-crossproject 1.3.2 (CrossType.Pure)

## Build & Run Commands

```bash
# Compile everything
sbt compile

# Compile frontend to JS (required before running backend)
sbt frontend/fastLinkJS

# Run backend server (serves API + frontend assets on 127.0.0.1:8080)
sbt backend/run

# Watch mode for frontend development
sbt ~frontend/fastLinkJS

# Cross-compile shared module
sbt sharedJVM/compile sharedJS/compile
```

The dev workflow is: compile frontend JS first, then start backend. The backend serves `frontend/index.html` and `frontend/target/scala-3.7.4/frontend-fastopt/main.js` directly from the filesystem.

## Architecture

Three sbt modules with a shared dependency:

```
shared (JVM + JS)  ←── backend (JVM only)
                   ←── frontend (JS only)
```

- **shared/** — Platform-independent types and logic used by both sides. Currently `Greeting` case class and `SharedMessage` constants.
- **backend/** — Kyo `KyoApp` entry point with Tapir `Routes`. Serves the API (`GET /api/hello`) and static frontend assets. Uses `Routes.add` for endpoint definitions and `Routes.run` to start Netty.
- **frontend/** — Laminar app compiled to JS. Uses reactive `Var`/`Signal` for state management. Fetches data from backend API via `dom.fetch`.

## Kyo Patterns

- Entry point: `object Main extends KyoApp` with `run { ... }`
- Routes: `Routes.add(_.get.in("path").out(stringBody))(handler)` returns `Unit < Routes`
- Compose routes: `route1.andThen(route2)`
- Start server: `Routes.run(composedRoutes)` returns `NettyKyoServerBinding < Async`
- Keep server alive: `Fiber.never` followed by `Fiber.get`
- KyoApp handles: Sync, Async, Scope, Clock, Console, Random

## Laminar Patterns

- Reactive state: `Var("initial")` with `.signal` for read-only access
- DOM binding: `child.text <-- someVar.signal`
- Render: `renderOnDomContentLoaded(container, rootElement)`
