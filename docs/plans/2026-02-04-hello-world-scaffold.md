# Hello World Scaffold Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Scaffold a ScalaJS project with frontend (Laminar), backend (Kyo-Tapir), and shared module that displays "Hello, World!".

**Architecture:** Three sbt modules — `shared` cross-compiles to JVM/JS, `backend` serves a Tapir endpoint via Kyo Routes, `frontend` is a Laminar app compiled to JS. Backend serves both the API and the static frontend assets.

**Tech Stack:** Scala 3.7.4, sbt 1.10.7, ScalaJS 1.17.0, Kyo 1.0-RC1, Laminar 17.2.1, sbt-crossproject 1.3.2

---

### Task 1: Create sbt build configuration

**Files:**
- Create: `project/build.properties`
- Create: `project/plugins.sbt`
- Create: `build.sbt`

**Step 1: Create `project/build.properties`**

```
sbt.version=1.10.7
```

**Step 2: Create `project/plugins.sbt`**

```scala
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.17.0")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.3.2")
```

**Step 3: Create `build.sbt`**

```scala
ThisBuild / scalaVersion := "3.7.4"
ThisBuild / version      := "0.1.0-SNAPSHOT"

lazy val kyoVersion    = "1.0-RC1"
lazy val laminarVersion = "17.2.1"

lazy val shared = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("shared"))

lazy val sharedJVM = shared.jvm
lazy val sharedJS  = shared.js

lazy val backend = project
  .in(file("backend"))
  .dependsOn(sharedJVM)
  .settings(
    libraryDependencies ++= Seq(
      "io.getkyo" %% "kyo-tapir" % kyoVersion
    )
  )

lazy val frontend = project
  .in(file("frontend"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(sharedJS)
  .settings(
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies ++= Seq(
      "com.raquo" %%% "laminar" % laminarVersion
    )
  )
```

**Step 4: Verify sbt loads without errors**

Run: `sbt compile`
Expected: Compilation succeeds (no source files yet, so nothing to compile — but the build should resolve dependencies)

**Step 5: Commit**

```bash
git init
git add project/build.properties project/plugins.sbt build.sbt
git commit -m "chore: initial sbt build with shared/backend/frontend modules"
```

---

### Task 2: Create shared module

**Files:**
- Create: `shared/src/main/scala/rsvpreader/Greeting.scala`

**Step 1: Create the shared types**

```scala
package rsvpreader

case class Greeting(message: String)

object SharedMessage:
  val hello: Greeting = Greeting("Hello, World!")
```

**Step 2: Verify it compiles on both platforms**

Run: `sbt sharedJVM/compile sharedJS/compile`
Expected: Both compile successfully

**Step 3: Commit**

```bash
git add shared/
git commit -m "feat: add shared Greeting type"
```

---

### Task 3: Create backend server

**Files:**
- Create: `backend/src/main/scala/rsvpreader/Main.scala`

**Step 1: Create the backend entry point**

```scala
package rsvpreader

import kyo.*
import sttp.tapir.*

object Main extends KyoApp:

  val helloRoute: Unit < Routes = Routes.add(
    _.get.in("api" / "hello").out(stringBody)
  )(_ => SharedMessage.hello.message)

  run {
    helloRoute.pipe(Routes.run())
  }
```

Note: The exact `Routes` API should be verified against the Kyo source. The pattern from the Kyo reference is:
- `Routes.add(endpointBuilder)(handler)` to define a route
- `Routes.run()` or `Routes.run(NettyKyoServer().port(8080))` to start the server

**Step 2: Verify it compiles**

Run: `sbt backend/compile`
Expected: Compiles successfully

**Step 3: Verify it runs**

Run: `sbt backend/run`
Expected: Server starts on default port (8080). Test with: `curl http://localhost:8080/api/hello` — should return `Hello, World!`

**Step 4: Commit**

```bash
git add backend/
git commit -m "feat: add backend with hello endpoint"
```

---

### Task 4: Create frontend app

**Files:**
- Create: `frontend/src/main/scala/rsvpreader/Main.scala`
- Create: `frontend/index.html`

**Step 1: Create the Laminar app**

```scala
package rsvpreader

import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.scalajs.js.Thenable.Implicits.*

object Main:
  def main(args: Array[String]): Unit =
    val messageVar = Var("Loading...")

    for
      response <- dom.fetch("/api/hello").toFuture
      text     <- response.text().toFuture
    do
      messageVar.set(text)

    val app = div(
      h1("RSVP Reader"),
      p(child.text <-- messageVar.signal)
    )

    renderOnDomContentLoaded(dom.document.getElementById("app"), app)
```

**Step 2: Create `frontend/index.html`**

```html
<!DOCTYPE html>
<html>
  <head>
    <meta charset="UTF-8">
    <title>RSVP Reader</title>
  </head>
  <body>
    <div id="app"></div>
    <script src="/assets/frontend-fastopt/main.js"></script>
  </body>
</html>
```

Note: The exact JS output path depends on `scalaJSLinkerOutputDirectory` settings. Default fastOptJS output goes to `target/scala-3.7.4/frontend-fastopt/main.js`. We'll adjust the asset serving path when wiring static files in Task 5.

**Step 3: Verify frontend compiles to JS**

Run: `sbt frontend/fastLinkJS`
Expected: Produces JS output in `frontend/target/scala-3.7.4/frontend-fastopt/`

**Step 4: Commit**

```bash
git add frontend/
git commit -m "feat: add frontend Laminar hello world"
```

---

### Task 5: Wire static asset serving in backend

**Files:**
- Modify: `backend/src/main/scala/rsvpreader/Main.scala`

**Step 1: Add static file serving to backend**

Update the backend Main to serve `index.html` and the compiled JS. Using Tapir's static file endpoints:

```scala
package rsvpreader

import kyo.*
import sttp.tapir.*
import sttp.tapir.files.*

object Main extends KyoApp:

  val helloRoute: Unit < Routes = Routes.add(
    _.get.in("api" / "hello").out(stringBody)
  )(_ => SharedMessage.hello.message)

  // Serve compiled frontend JS from the frontend target directory
  val staticRoutes: Unit < Routes = Routes.add(
    staticResourcesGetServerEndpoint("assets")(
      classOf[Main.type].getClassLoader,
      "public"
    )
  )

  // Serve index.html at root
  val indexRoute: Unit < Routes = Routes.add(
    _.get.in("").out(htmlBodyUtf8)
  )(_ => scala.io.Source.fromResource("public/index.html").mkString)

  run {
    (helloRoute *> staticRoutes *> indexRoute).pipe(Routes.run())
  }
```

Note: Static asset serving strategy needs verification during implementation. The simplest approach for hello world may be:
1. Copy `frontend/index.html` and compiled JS into `backend/src/main/resources/public/` as a build step
2. Or use Tapir's `staticFilesGetServerEndpoint` to serve from the filesystem directly

The implementer should pick the simpler approach that works.

**Step 2: Verify the full stack works**

Run:
```bash
sbt frontend/fastLinkJS
sbt backend/run
```

Then open `http://localhost:8080` in a browser.
Expected: Page loads, shows "RSVP Reader" heading, then "Hello, World!" appears after the fetch completes.

**Step 3: Commit**

```bash
git add backend/
git commit -m "feat: serve frontend static assets from backend"
```

---

### Task 6: Add .gitignore and finalize

**Files:**
- Create: `.gitignore`

**Step 1: Create `.gitignore`**

```
# sbt
target/
project/target/
project/project/

# IDE
.idea/
.bsp/
.metals/
.bloop/
.vscode/

# OS
.DS_Store
```

**Step 2: Commit**

```bash
git add .gitignore
git commit -m "chore: add .gitignore"
```

---

## Version Reference

| Dependency | Version | Artifact |
|------------|---------|----------|
| Scala | 3.7.4 | - |
| sbt | 1.10.7 | - |
| ScalaJS | 1.17.0 | `sbt-scalajs` |
| sbt-crossproject | 1.3.2 | `sbt-scalajs-crossproject` |
| Kyo | 1.0-RC1 | `kyo-tapir` |
| Laminar | 17.2.1 | `laminar` |
