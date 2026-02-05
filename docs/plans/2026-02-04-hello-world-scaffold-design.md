# RSVP Reader - Hello World Scaffold Design

## Overview

ScalaJS project with frontend, backend, and shared module. Starting with a hello world to validate the cross-compilation setup before building the RSVP speed-reading features.

## Tech Stack

| Concern | Choice |
|---------|--------|
| Scala | 3.7.4 |
| Build | sbt 1.10.x + sbt-crossproject |
| Kyo | 1.0-RC1 |
| Backend | kyo-tapir (Routes) |
| Frontend | Laminar + ScalaJS |
| Shared | Pure Scala, cross-compiled JVM/JS |

## Project Structure

```
rsvp-reader/
├── build.sbt
├── project/
│   ├── build.properties
│   └── plugins.sbt
├── shared/
│   └── src/main/scala/rsvpreader/
│       └── SharedMessage.scala
├── backend/
│   └── src/main/scala/rsvpreader/
│       └── Main.scala
├── frontend/
│   ├── index.html
│   └── src/main/scala/rsvpreader/
│       └── Main.scala
```

## Modules

### shared (crossProject JVM/JS)

Pure Scala case class and constant, no dependencies:

```scala
package rsvpreader

case class Greeting(message: String)

object SharedMessage:
  val hello = Greeting("Hello, World!")
```

### backend (JVM, depends on shared.jvm)

Single Tapir endpoint using Kyo Routes:

- `GET /api/hello` returns `"Hello, World!"`
- Serves frontend static assets (compiled JS + index.html)
- Dependencies: `kyo-tapir` 1.0-RC1

### frontend (ScalaJS, depends on shared.js)

Laminar app:

- Fetches `/api/hello` via `dom.fetch`
- Displays the greeting in a simple page
- Dependencies: `laminar`, `scalajs-dom`

## Excluded (YAGNI)

- No JSON serialization (plain string for now)
- No Kyo effects on the frontend
- No CSS/styling
- No tests
- No Docker/deployment config
