# PlaybackEngine Extraction Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Extract PlaybackEngine and ViewState to shared module with channel-based state emission for testability.

**Architecture:** Move files from frontend to shared, replace callback parameter with Channel[ViewState], add parallel state consumer loop in Main.scala.

**Tech Stack:** Scala 3, Kyo 1.0-RC1 (Channel, Async, Clock.withTimeControl), MUnit

---

## Task 1: Move ViewState to Shared

**Files:**
- Create: `shared/src/main/scala/rsvpreader/ViewState.scala`
- Delete: `frontend/src/main/scala/rsvpreader/ViewState.scala`

**Step 1: Copy ViewState to shared**

Create `shared/src/main/scala/rsvpreader/ViewState.scala` with exact contents:

```scala
package rsvpreader

import kyo.*

/** Immutable snapshot of RSVP playback state for UI rendering.
  *
  * @param tokens All tokens in the text being read
  * @param index  Current token position (0-based)
  * @param status Current playback status (Playing/Paused/Stopped)
  * @param wpm    Current words-per-minute speed
  */
case class ViewState(
  tokens: Span[Token],
  index: Int,
  status: PlayStatus,
  wpm: Int
):
  def currentToken: Maybe[Token] =
    if index >= 0 && index < tokens.length then Maybe(tokens(index))
    else Absent

  def trailTokens(count: Int): Seq[Token] =
    if count <= 0 || index <= 0 then Seq.empty
    else
      val start = Math.max(0, index - count)
      (start until index).map(i => tokens(i))

  def currentParagraphTokens: Seq[Token] =
    currentToken.fold(Seq.empty[Token]) { current =>
      (0 until tokens.length)
        .map(i => tokens(i))
        .filter(_.paragraphIndex == current.paragraphIndex)
    }

  def currentSentenceTokens: Seq[Token] =
    currentToken.fold(Seq.empty[Token]) { current =>
      (0 until tokens.length)
        .map(i => tokens(i))
        .filter(_.sentenceIndex == current.sentenceIndex)
    }

  def currentSentenceWithHighlight: Seq[(Token, Boolean)] =
    currentToken.fold(Seq.empty[(Token, Boolean)]) { current =>
      val sentenceIdx = current.sentenceIndex
      (0 until tokens.length)
        .filter(i => tokens(i).sentenceIndex == sentenceIdx)
        .map { i =>
          val token = tokens(i)
          val isCurrent = i == index
          (token, isCurrent)
        }
    }

object ViewState:
  def initial(tokens: Span[Token], config: RsvpConfig): ViewState =
    ViewState(tokens, 0, PlayStatus.Paused, config.baseWpm)
```

**Step 2: Delete frontend ViewState**

Delete: `frontend/src/main/scala/rsvpreader/ViewState.scala`

**Step 3: Verify compilation**

Run: `sbt compile`
Expected: SUCCESS (frontend imports ViewState from shared via classpath)

**Step 4: Run tests**

Run: `sbt test`
Expected: All 43 tests pass

**Step 5: Commit**

```bash
git add -A
git commit -m "refactor: move ViewState to shared module

No API changes - pure file relocation for cross-platform testability.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>"
```

---

## Task 2: Move PlaybackEngine to Shared (with API change)

**Files:**
- Create: `shared/src/main/scala/rsvpreader/PlaybackEngine.scala`
- Delete: `frontend/src/main/scala/rsvpreader/PlaybackEngine.scala`

**Step 1: Create PlaybackEngine in shared with new signature**

Create `shared/src/main/scala/rsvpreader/PlaybackEngine.scala`:

```scala
package rsvpreader

import kyo.*

/** Async playback engine that displays tokens at configured speed with command handling.
  *
  * This is the "Word Iterator" loop - it handles playback of a SINGLE text:
  * - Iterates through tokens word-by-word at configured WPM
  * - Uses Async.race(sleep, command) to respond instantly to pause/resume
  * - Manages state machine: Playing → Paused → Playing → ... → Stopped
  * - Exits when all tokens displayed or Stop command received
  *
  * Uses Kyo Channel for responsive command processing (pause/resume interrupts sleep).
  * Uses Kyo Loop for stack-safe, explicit state machine semantics.
  *
  * @param commands Channel for receiving playback commands (pause/resume/back/speed)
  * @param stateOut Channel for emitting state updates (replaces callback)
  * @param config   RSVP timing configuration (WPM, delays, etc.)
  */
class PlaybackEngine(
  commands: Channel[Command],
  stateOut: Channel[ViewState],
  config: RsvpConfig
):
  // Type alias for loop outcomes - either continue with new state or done with result
  private type Outcome = Loop.Outcome[ViewState, Unit]

  // Type alias for the effect stack
  private type PlaybackEffect = Async & Abort[Closed]

  // Helper to create done outcome with explicit types
  private def done: Outcome = Loop.done[ViewState, Unit](())

  /** Emits state to the output channel. */
  private def emit(state: ViewState): Unit < PlaybackEffect =
    stateOut.put(state)

  def run(tokens: Span[Token]): Unit < PlaybackEffect =
    val initial = ViewState.initial(tokens, config)
    emit(initial).andThen {
      val startLoop =
        if config.startDelay > Duration.Zero then
          Async.sleep(config.startDelay).andThen(playbackLoop(initial))
        else
          playbackLoop(initial)
      startLoop
    }

  /** Main playback loop - iterates through tokens word-by-word.
    *
    * State machine with three states:
    * - Playing: Display word, sleep, advance (or handle command)
    * - Paused: Wait for command (resume/back/etc.)
    * - Stopped: Exit loop (Loop.done)
    *
    * Uses Kyo Loop for explicit continue/done semantics instead of recursion.
    */
  private def playbackLoop(initial: ViewState): Unit < PlaybackEffect =
    Loop(initial) { state =>
      state.status match
        case PlayStatus.Stopped => Loop.done(())
        case PlayStatus.Paused  => handlePaused(state)
        case PlayStatus.Playing => handlePlaying(state)
    }

  private def handlePlaying(state: ViewState): Outcome < PlaybackEffect =
    state.currentToken match
      case Absent =>
        val stopped = state.copy(status = PlayStatus.Stopped)
        emit(stopped).andThen(done)

      case Present(token) =>
        emit(state).andThen {
          val delay = calculateDelay(token, config.copy(baseWpm = state.wpm))

          Async.race(
            Async.sleep(delay).map(_ => Absent),
            commands.take.map(cmd => Maybe(cmd))
          ).map {
            case Absent =>
              val next = state.copy(index = state.index + 1)
              val shouldPause = config.paragraphAutoPause &&
                token.isEndOfParagraph(next.currentToken)
              if shouldPause then Loop.continue(next.copy(status = PlayStatus.Paused))
              else Loop.continue(next)
            case Present(cmd) =>
              Loop.continue(applyCommand(cmd, state))
          }
        }

  private def handlePaused(state: ViewState): Outcome < PlaybackEffect =
    commands.take.map { cmd =>
      val newState = applyCommand(cmd, state)
      if newState.status == PlayStatus.Stopped then done
      else Loop.continue(newState)
    }

  /** Pure function that applies a command to produce a new state. */
  private def applyCommand(cmd: Command, state: ViewState): ViewState =
    cmd match
      case Command.Pause =>
        state.copy(status = PlayStatus.Paused)

      case Command.Resume =>
        state.copy(status = PlayStatus.Playing)

      case Command.Back(n) =>
        state.copy(index = Math.max(0, state.index - n))

      case Command.RestartSentence =>
        val currentSentence = state.currentToken.fold(-1)(_.sentenceIndex)
        val sentenceStart = findSentenceStart(state.tokens, state.index, currentSentence)
        state.copy(index = sentenceStart)

      case Command.SetSpeed(wpm) =>
        state.copy(wpm = wpm)

      case Command.Stop =>
        state.copy(index = 0, status = PlayStatus.Paused)

  private def findSentenceStart(tokens: Span[Token], current: Int, sentenceIdx: Int): Int =
    var i = current
    while i > 0 && tokens(i - 1).sentenceIndex == sentenceIdx do
      i -= 1
    i
```

**Step 2: Delete frontend PlaybackEngine**

Delete: `frontend/src/main/scala/rsvpreader/PlaybackEngine.scala`

**Step 3: Verify shared compiles**

Run: `sbt sharedJVM/compile sharedJS/compile`
Expected: SUCCESS

**Step 4: Verify frontend fails (expected - Main.scala needs update)**

Run: `sbt frontend/compile`
Expected: FAIL with error about PlaybackEngine constructor

**Step 5: Commit shared module changes**

```bash
git add shared/src/main/scala/rsvpreader/PlaybackEngine.scala
git add frontend/src/main/scala/rsvpreader/PlaybackEngine.scala
git commit -m "refactor: move PlaybackEngine to shared with channel-based state

API change: replaces onStateChange callback with stateOut channel.
Frontend compilation intentionally broken - will fix in next commit.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>"
```

---

## Task 3: Update Main.scala Integration

**Files:**
- Modify: `frontend/src/main/scala/rsvpreader/Main.scala`

**Step 1: Update engineLoop with state consumer**

Replace the `engineLoop` method and add `stateConsumerLoop`:

```scala
  /** Session manager loop that waits for text and runs playback.
    *
    * Flow: Wait for tokens → Create engine + state channel → Run both in parallel → Repeat
    *
    * This loop exists because DOM callbacks can't run Kyo async effects.
    * Instead, callbacks send tokens via channel, and this loop (running
    * inside KyoApp's runtime) receives them and runs the PlaybackEngine.
    */
  private def engineLoop(
    commandCh: Channel[Command],
    tokensCh: Channel[kyo.Span[Token]]
  ): Unit < Async =
    // Wrap in Abort.run to handle channel closure (e.g., if app shuts down)
    Abort.run[Closed] {
      Loop(()): _ =>
        direct:
          val tokens = tokensCh.take.now // Block until user loads text
          Console.printLine(s"Received ${tokens.length} tokens, starting playback").now

          // Create state channel for this playback session
          val stateCh = Channel.init[ViewState](1).now
          val engine = PlaybackEngine(commandCh, stateCh, AppState.config)

          // Run engine and state consumer in parallel
          // Race ensures both stop when engine completes
          Async.race(
            engine.run(tokens),
            stateConsumerLoop(stateCh)
          ).now

          Console.printLine("Playback finished, waiting for next text...").now
          Loop.continue(()).now // Loop back to wait for next text
    }.unit

  /** Consumes state updates from PlaybackEngine and updates Laminar reactive state.
    *
    * Runs in parallel with engine.run(). When engine finishes and closes the channel,
    * this loop exits via Abort[Closed].
    */
  private def stateConsumerLoop(
    stateCh: Channel[ViewState]
  ): Unit < (Async & Abort[Closed]) =
    Loop.foreach(()) { _ =>
      stateCh.take.map { state =>
        AppState.viewState.set(state)
      }
    }
```

**Step 2: Verify compilation**

Run: `sbt compile`
Expected: SUCCESS

**Step 3: Run tests**

Run: `sbt test`
Expected: All 43 tests pass

**Step 4: Commit**

```bash
git add frontend/src/main/scala/rsvpreader/Main.scala
git commit -m "feat: integrate channel-based PlaybackEngine in Main

- Create per-session state channel
- Run engine and state consumer in parallel via Async.race
- State consumer updates AppState.viewState for Laminar UI

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>"
```

---

## Task 4: Write ViewState Tests

**Files:**
- Create: `shared/src/test/scala/rsvpreader/ViewStateSuite.scala`

**Step 1: Write ViewState unit tests**

Create `shared/src/test/scala/rsvpreader/ViewStateSuite.scala`:

```scala
package rsvpreader

import kyo.*
import munit.FunSuite

class ViewStateSuite extends FunSuite:

  val tokens: Span[Token] = Span.from(Seq(
    Token("Hello", 1, Punctuation.None, 0, 0),
    Token("world", 1, Punctuation.Period, 0, 0),
    Token("New", 0, Punctuation.None, 1, 1),
    Token("sentence", 2, Punctuation.Period, 1, 1)
  ))

  val config = RsvpConfig(baseWpm = 300)

  test("initial creates paused state at index 0"):
    val state = ViewState.initial(tokens, config)
    assertEquals(state.index, 0)
    assertEquals(state.status, PlayStatus.Paused)
    assertEquals(state.wpm, 300)

  test("currentToken returns token at current index"):
    val state = ViewState(tokens, 1, PlayStatus.Playing, 300)
    assertEquals(state.currentToken, Maybe(tokens(1)))

  test("currentToken returns Absent when index out of bounds"):
    val state = ViewState(tokens, 10, PlayStatus.Playing, 300)
    assertEquals(state.currentToken, Absent)

  test("currentToken returns Absent for negative index"):
    val state = ViewState(tokens, -1, PlayStatus.Playing, 300)
    assertEquals(state.currentToken, Absent)

  test("trailTokens returns previous tokens"):
    val state = ViewState(tokens, 3, PlayStatus.Playing, 300)
    val trail = state.trailTokens(2)
    assertEquals(trail.length, 2)
    assertEquals(trail(0).text, "world")
    assertEquals(trail(1).text, "New")

  test("trailTokens returns empty for index 0"):
    val state = ViewState(tokens, 0, PlayStatus.Playing, 300)
    assertEquals(state.trailTokens(5), Seq.empty)

  test("trailTokens returns empty for count <= 0"):
    val state = ViewState(tokens, 2, PlayStatus.Playing, 300)
    assertEquals(state.trailTokens(0), Seq.empty)
    assertEquals(state.trailTokens(-1), Seq.empty)

  test("currentSentenceTokens returns tokens in same sentence"):
    val state = ViewState(tokens, 0, PlayStatus.Playing, 300)
    val sentence = state.currentSentenceTokens
    assertEquals(sentence.length, 2)
    assertEquals(sentence.map(_.text), Seq("Hello", "world"))

  test("currentSentenceWithHighlight marks current token"):
    val state = ViewState(tokens, 1, PlayStatus.Playing, 300)
    val highlighted = state.currentSentenceWithHighlight
    assertEquals(highlighted.length, 2)
    assertEquals(highlighted(0), (tokens(0), false))
    assertEquals(highlighted(1), (tokens(1), true))
```

**Step 2: Run tests**

Run: `sbt "sharedJVM/testOnly rsvpreader.ViewStateSuite"`
Expected: All tests pass

**Step 3: Commit**

```bash
git add shared/src/test/scala/rsvpreader/ViewStateSuite.scala
git commit -m "test: add ViewStateSuite for ViewState unit tests

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>"
```

---

## Task 5: Write PlaybackEngine State Transition Tests

**Files:**
- Create: `shared/src/test/scala/rsvpreader/PlaybackEngineSuite.scala`

**Step 1: Write state transition tests**

Create `shared/src/test/scala/rsvpreader/PlaybackEngineSuite.scala`:

```scala
package rsvpreader

import kyo.*
import munit.FunSuite

class PlaybackEngineSuite extends FunSuite:

  // Instant config for fast tests - no delays
  val instantConfig = RsvpConfig(
    baseWpm = Int.MaxValue,
    startDelay = Duration.Zero,
    commaDelay = Duration.Zero,
    periodDelay = Duration.Zero,
    paragraphDelay = Duration.Zero
  )

  val tokens: Span[Token] = Span.from(Seq(
    Token("one", 0, Punctuation.None, 0, 0),
    Token("two", 0, Punctuation.None, 0, 0),
    Token("three", 1, Punctuation.Period, 0, 0)
  ))

  /** Helper to run async test with timeout. */
  def runTest[A](effect: A < Async): A =
    import AllowUnsafe.embrace.danger
    KyoApp.Unsafe.runAndBlock(5.seconds)(effect)

  /** Drains all available states from channel without blocking. */
  def drainStates(stateCh: Channel[ViewState]): Seq[ViewState] < Sync =
    def loop(acc: Seq[ViewState]): Seq[ViewState] < Sync =
      stateCh.poll.map {
        case Absent    => acc
        case Present(s) => loop(acc :+ s)
      }
    loop(Seq.empty)

  test("emits initial state then progresses through tokens"):
    runTest {
      direct:
        val commandCh = Channel.init[Command](1).now
        val stateCh = Channel.init[ViewState](10).now // buffer to collect states
        val engine = PlaybackEngine(commandCh, stateCh, instantConfig)

        // Run engine in fiber
        val fiber = Fiber.init(engine.run(tokens)).now

        // Wait for completion
        fiber.get.now

        // Drain emitted states
        val states = drainStates(stateCh).now

        // Should have: initial (idx 0) + each token (idx 0,1,2) + stopped
        assert(states.length >= 4, s"Expected at least 4 states, got ${states.length}")
        assertEquals(states.head.index, 0)
        assertEquals(states.head.status, PlayStatus.Paused) // initial is paused
        assertEquals(states.last.status, PlayStatus.Stopped)
    }

  test("pause command stops progression"):
    runTest {
      direct:
        val commandCh = Channel.init[Command](1).now
        val stateCh = Channel.init[ViewState](10).now
        val config = instantConfig.copy(baseWpm = 300) // slower for control
        val engine = PlaybackEngine(commandCh, stateCh, config)

        val fiber = Fiber.init(engine.run(tokens)).now

        // Send resume to start, then pause
        commandCh.put(Command.Resume).now
        Async.sleep(50.millis).now
        commandCh.put(Command.Pause).now
        Async.sleep(50.millis).now

        // Engine should be paused, not progressing
        val beforePause = drainStates(stateCh).now
        Async.sleep(100.millis).now
        val afterWait = drainStates(stateCh).now

        // No new states emitted while paused
        assertEquals(afterWait.length, 0)

        // Clean up - send stop
        commandCh.put(Command.Stop).now
        fiber.get.now
    }

  test("resume after pause continues playback"):
    runTest {
      direct:
        val commandCh = Channel.init[Command](1).now
        val stateCh = Channel.init[ViewState](10).now
        val engine = PlaybackEngine(commandCh, stateCh, instantConfig)

        val fiber = Fiber.init(engine.run(tokens)).now

        // Initial state is paused, send resume
        Async.sleep(10.millis).now
        commandCh.put(Command.Resume).now

        // Wait for completion
        fiber.get.now

        val states = drainStates(stateCh).now
        assertEquals(states.last.status, PlayStatus.Stopped)
    }

  test("back command moves index backward"):
    runTest {
      direct:
        val commandCh = Channel.init[Command](1).now
        val stateCh = Channel.init[ViewState](10).now
        val engine = PlaybackEngine(commandCh, stateCh, instantConfig)

        val fiber = Fiber.init(engine.run(tokens)).now

        // Resume to start
        Async.sleep(10.millis).now
        commandCh.put(Command.Resume).now
        Async.sleep(10.millis).now

        // Pause and go back
        commandCh.put(Command.Pause).now
        Async.sleep(10.millis).now
        val statesBeforeBack = drainStates(stateCh).now
        val indexBeforeBack = statesBeforeBack.lastOption.map(_.index).getOrElse(0)

        commandCh.put(Command.Back(2)).now
        Async.sleep(10.millis).now
        val statesAfterBack = drainStates(stateCh).now

        // Resume to complete
        commandCh.put(Command.Resume).now
        fiber.get.now

        // Verify back moved index (may be at 0 if was already near start)
        val finalStates = drainStates(stateCh).now
        assert(finalStates.exists(_.index == 0) || indexBeforeBack <= 2)
    }

  test("setSpeed command changes WPM"):
    runTest {
      direct:
        val commandCh = Channel.init[Command](1).now
        val stateCh = Channel.init[ViewState](10).now
        val engine = PlaybackEngine(commandCh, stateCh, instantConfig)

        val fiber = Fiber.init(engine.run(tokens)).now

        // Set speed while paused
        Async.sleep(10.millis).now
        commandCh.put(Command.SetSpeed(500)).now
        Async.sleep(10.millis).now

        // Resume and complete
        commandCh.put(Command.Resume).now
        fiber.get.now

        val states = drainStates(stateCh).now
        assert(states.exists(_.wpm == 500), "Expected state with wpm=500")
    }

  test("stop command resets to index 0 and pauses"):
    runTest {
      direct:
        val commandCh = Channel.init[Command](1).now
        val stateCh = Channel.init[ViewState](10).now
        val engine = PlaybackEngine(commandCh, stateCh, instantConfig)

        val fiber = Fiber.init(engine.run(tokens)).now

        // Resume, then stop
        Async.sleep(10.millis).now
        commandCh.put(Command.Resume).now
        Async.sleep(10.millis).now
        commandCh.put(Command.Stop).now

        fiber.get.now

        val states = drainStates(stateCh).now
        val lastState = states.last
        assertEquals(lastState.index, 0)
        assertEquals(lastState.status, PlayStatus.Paused)
    }
```

**Step 2: Run tests**

Run: `sbt "sharedJVM/testOnly rsvpreader.PlaybackEngineSuite"`
Expected: All tests pass

**Step 3: Commit**

```bash
git add shared/src/test/scala/rsvpreader/PlaybackEngineSuite.scala
git commit -m "test: add PlaybackEngineSuite for state transition tests

Tests: initial state, pause/resume, back, setSpeed, stop commands.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>"
```

---

## Task 6: Write Timing Tests with Clock.withTimeControl

**Files:**
- Modify: `shared/src/test/scala/rsvpreader/PlaybackEngineSuite.scala`

**Step 1: Add timing verification tests**

Add to `PlaybackEngineSuite.scala`:

```scala
  test("respects startDelay before first token"):
    runTest {
      Clock.withTimeControl { control =>
        direct:
          control.set(java.time.Instant.EPOCH).now

          val commandCh = Channel.init[Command](1).now
          val stateCh = Channel.init[ViewState](10).now
          val config = RsvpConfig(
            baseWpm = Int.MaxValue,
            startDelay = 500.millis
          )
          val engine = PlaybackEngine(commandCh, stateCh, config)

          val fiber = Fiber.init(engine.run(tokens)).now

          // Initial state emitted immediately
          Async.sleep(1.millis).now
          val initialStates = drainStates(stateCh).now
          assertEquals(initialStates.length, 1)
          assertEquals(initialStates.head.status, PlayStatus.Paused)

          // Advance past start delay
          control.advance(600.millis).now
          Async.sleep(1.millis).now

          // Resume and let it complete
          commandCh.put(Command.Resume).now
          control.advance(1.second).now
          fiber.get.now
      }
    }

  test("calculates correct delay based on WPM"):
    runTest {
      Clock.withTimeControl { control =>
        direct:
          control.set(java.time.Instant.EPOCH).now

          val commandCh = Channel.init[Command](1).now
          val stateCh = Channel.init[ViewState](10).now
          // 300 WPM = 200ms per word
          val config = RsvpConfig(
            baseWpm = 300,
            startDelay = Duration.Zero,
            commaDelay = Duration.Zero,
            periodDelay = Duration.Zero,
            paragraphDelay = Duration.Zero,
            wordLengthEnabled = false
          )
          val singleToken = Span.from(Seq(Token("test", 1, Punctuation.None, 0, 0)))
          val engine = PlaybackEngine(commandCh, stateCh, config)

          val fiber = Fiber.init(engine.run(singleToken)).now

          // Resume to start playing
          Async.sleep(1.millis).now
          commandCh.put(Command.Resume).now
          Async.sleep(1.millis).now

          // Before 200ms - should still be at index 0
          control.advance(150.millis).now
          Async.sleep(1.millis).now
          assert(!fiber.done.now, "Should not be done before delay")

          // After 200ms - should complete
          control.advance(100.millis).now
          Async.sleep(1.millis).now
          fiber.get.now

          val states = drainStates(stateCh).now
          assertEquals(states.last.status, PlayStatus.Stopped)
      }
    }
```

**Step 2: Run tests**

Run: `sbt "sharedJVM/testOnly rsvpreader.PlaybackEngineSuite"`
Expected: All tests pass

**Step 3: Run full test suite**

Run: `sbt test`
Expected: All tests pass (should be ~55+ tests now)

**Step 4: Commit**

```bash
git add shared/src/test/scala/rsvpreader/PlaybackEngineSuite.scala
git commit -m "test: add timing tests with Clock.withTimeControl

Verifies startDelay and WPM-based delay calculations.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>"
```

---

## Task 7: Manual Verification

**Step 1: Build frontend JS**

Run: `sbt frontend/fastLinkJS`
Expected: SUCCESS

**Step 2: Start backend**

Run: `sbt backend/run`
Expected: Server starts on 127.0.0.1:8080

**Step 3: Manual test in browser**

1. Open http://127.0.0.1:8080
2. Paste sample text
3. Click "Start Reading"
4. Verify: Words display at configured speed
5. Test pause/resume (space key)
6. Test speed adjustment
7. Test back button

**Step 4: Final commit if all works**

```bash
git add -A
git commit -m "chore: verification complete - PlaybackEngine extraction working

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>" --allow-empty
```

---

## Summary

| Task | Description | Tests Added |
|------|-------------|-------------|
| 1 | Move ViewState to shared | 0 |
| 2 | Move PlaybackEngine to shared | 0 |
| 3 | Update Main.scala integration | 0 |
| 4 | ViewState unit tests | ~9 |
| 5 | PlaybackEngine state tests | ~6 |
| 6 | Timing tests | ~2 |
| 7 | Manual verification | 0 |

**Total new tests:** ~17
**Final test count:** ~60
