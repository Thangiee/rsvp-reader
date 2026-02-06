package rsvpreader

import kyo.*
import munit.FunSuite

class PlaybackEngineSuite extends FunSuite:

  // Instant config — Int.MaxValue WPM means near-zero delays, fully command-driven
  val instantConfig: RsvpConfig = RsvpConfig(
    baseWpm = Int.MaxValue,
    startDelay = Duration.Zero,
    commaDelay = Duration.Zero,
    periodDelay = Duration.Zero,
    paragraphDelay = Duration.Zero
  )

  // Fast config — 6000 WPM = ~10ms per word, enough for command interleaving
  val fastConfig: RsvpConfig = RsvpConfig(
    baseWpm = 6000,
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

  val moreTokens: Span[Token] = Span.from(Seq(
    Token("a", 0, Punctuation.None, 0, 0),
    Token("b", 0, Punctuation.None, 0, 0),
    Token("c", 0, Punctuation.None, 0, 0),
    Token("d", 0, Punctuation.None, 0, 0),
    Token("e", 1, Punctuation.Period, 0, 0)
  ))

  /** Test harness that manages channels, engine lifecycle, and state collection. */
  class Harness(
    val commandCh: Channel[Command],
    val stateCh: Channel[ViewState],
    val configRef: AtomicRef[RsvpConfig],
    val engine: PlaybackEngine
  ):
    def start(t: Span[Token] = tokens): Fiber[Unit, Async & Abort[Closed]] < Sync =
      Fiber.init(engine.run(t))

    def send(cmd: Command): Unit < (Async & Abort[Closed]) =
      commandCh.put(cmd)

    /** Collect all states until Stopped. Blocks on each take. */
    def collectUntilStopped: Seq[ViewState] < (Async & Abort[Closed]) =
      Loop(Seq.empty[ViewState]) { acc =>
        stateCh.take.map { state =>
          val updated = acc :+ state
          if state.status == PlayStatus.Stopped then Loop.done(updated)
          else Loop.continue(updated)
        }
      }

    /** Drain all currently available states without blocking. */
    def drainAvailable: Seq[ViewState] < (Sync & Abort[Closed]) =
      Loop(Seq.empty[ViewState]) { acc =>
        stateCh.poll.map {
          case Absent     => Loop.done(acc)
          case Present(s) => Loop.continue(acc :+ s)
        }
      }

  end Harness

  object Harness:
    def init(config: RsvpConfig = instantConfig, stateBuffer: Int = 20): Harness < (Sync & Scope) =
      direct {
        val commandCh = Channel.init[Command](1).now
        val stateCh   = Channel.init[ViewState](stateBuffer).now
        val configRef = AtomicRef.init(config).now
        val engine    = PlaybackEngine(commandCh, stateCh, configRef)
        Harness(commandCh, stateCh, configRef, engine)
      }

  def runTest[A](effect: A < (Async & Abort[Closed] & Scope)): A =
    import AllowUnsafe.embrace.danger
    val handled = Abort.run(Scope.run(effect))
    KyoApp.Unsafe.runAndBlock(5.seconds)(handled).getOrThrow.getOrThrow

  /** Yield to scheduler. Uses Thread.sleep to bypass Clock.withTimeControl. */
  private val tick: Unit < Sync = Sync.defer(Thread.sleep(10))

  /** Advance virtual time in steps until fiber completes. */
  private def advanceUntilDone(
    control: Clock.TimeControl,
    fiber: Fiber[Unit, Async & Abort[Closed]],
    step: Duration = 250.millis,
    maxSteps: Int = 20
  ): Unit < (Async & Abort[Closed]) =
    Loop(0) { i =>
      if i >= maxSteps then Loop.done(())
      else
        control.advance(step).andThen(tick).andThen {
          fiber.done.map { isDone =>
            if isDone then Loop.done(())
            else Loop.continue(i + 1)
          }
        }
    }

  // -- State emission tests --

  test("emits initial paused state then progresses through all tokens to stopped"):
    runTest {
      direct {
        val h     = Harness.init().now
        val fiber = h.start().now
        h.send(Command.Resume).now
        fiber.get.now
        val states = h.collectUntilStopped.now

        assert(states.length >= 4, s"Expected at least 4 states, got ${states.length}")
        assertEquals(states.head.index, 0)
        assertEquals(states.head.status, PlayStatus.Paused)
        assertEquals(states.last.status, PlayStatus.Stopped)
        val indices = states.map(_.index).distinct
        assert(indices.contains(0) && indices.contains(1) && indices.contains(2),
          s"Expected indices 0, 1, 2 — got $indices")
      }
    }

  // -- Command tests (Async.sleep for simple interleaving) --

  test("pause command stops progression"):
    runTest {
      direct {
        val h     = Harness.init(fastConfig).now
        val fiber = h.start(moreTokens).now
        h.send(Command.Resume).now
        Async.sleep(15.millis).now
        h.send(Command.Pause).now
        Async.sleep(20.millis).now
        h.drainAvailable.now
        // No new states should appear while paused
        Async.sleep(50.millis).now
        val statesWhilePaused = h.drainAvailable.now
        h.send(Command.Resume).now
        fiber.get.now

        assertEquals(statesWhilePaused.length, 0,
          s"Expected no states while paused, got: ${statesWhilePaused.map(_.status)}")
      }
    }

  test("resume after pause continues to completion"):
    runTest {
      direct {
        val h     = Harness.init().now
        val fiber = h.start().now
        h.send(Command.Resume).now
        fiber.get.now
        val states = h.collectUntilStopped.now

        assertEquals(states.last.status, PlayStatus.Stopped)
      }
    }

  test("back command rewinds index during pause"):
    runTest {
      direct {
        val h     = Harness.init(fastConfig).now
        val fiber = h.start(moreTokens).now
        h.send(Command.Resume).now
        Async.sleep(25.millis).now
        h.send(Command.Pause).now
        Async.sleep(10.millis).now
        h.send(Command.Back(10)).now
        Async.sleep(10.millis).now
        val statesAfterBack = h.drainAvailable.now
        h.send(Command.Resume).now
        fiber.get.now
        val finalStates = h.collectUntilStopped.now

        val allStates = statesAfterBack ++ finalStates
        assert(allStates.exists(_.index == 0),
          s"Expected index 0 after Back(10), got indices: ${allStates.map(_.index)}")
      }
    }

  test("setSpeed command changes WPM"):
    runTest {
      direct {
        val h     = Harness.init().now
        val fiber = h.start().now
        h.send(Command.SetSpeed(500)).now
        h.send(Command.Resume).now
        fiber.get.now
        val states = h.collectUntilStopped.now

        assert(states.exists(_.wpm == 500),
          s"Expected state with wpm=500, got: ${states.map(_.wpm)}")
      }
    }

  test("stop command resets to index 0 and pauses"):
    runTest {
      direct {
        val h     = Harness.init(fastConfig).now
        val fiber = h.start().now
        h.send(Command.Resume).now
        Async.sleep(25.millis).now
        h.send(Command.Stop).now
        Async.sleep(10.millis).now
        val statesAfterStop = h.drainAvailable.now
        h.send(Command.Resume).now
        fiber.get.now
        val finalStates = h.collectUntilStopped.now

        val allStates = statesAfterStop ++ finalStates
        val stoppedAtZero = allStates.exists(s => s.index == 0 && s.status == PlayStatus.Paused)
        assert(stoppedAtZero,
          s"Expected Paused at index 0 after Stop, got: ${allStates.map(s => (s.index, s.status))}")
      }
    }

  // -- Timing tests (Clock.withTimeControl for precise assertions) --

  test("respects startDelay before entering paused state"):
    runTest {
      Clock.withTimeControl { control =>
        direct {
          val config = RsvpConfig(baseWpm = Int.MaxValue, startDelay = 500.millis)
          val h      = Harness.init(config).now
          val fiber  = h.start().now
          tick.now
          val initialStates = h.drainAvailable.now
          assertEquals(initialStates.length, 1, s"Expected 1 initial state, got: $initialStates")
          assertEquals(initialStates.head.status, PlayStatus.Paused)

          control.advance(600.millis).now
          tick.now
          h.send(Command.Resume).now
          advanceUntilDone(control, fiber).now
          val finalStates = h.collectUntilStopped.now
          assertEquals(finalStates.last.status, PlayStatus.Stopped)
        }
      }
    }

  // Two-paragraph tokens: paragraph 0 has "hello world.", paragraph 1 has "new para."
  val twoParagraphTokens: Span[Token] = Span.from(Seq(
    Token("hello", 1, Punctuation.None, 0, 0),
    Token("world.", 1, Punctuation.Paragraph, 0, 0),
    Token("new", 0, Punctuation.None, 1, 1),
    Token("para.", 1, Punctuation.Period, 1, 1)
  ))

  test("WPM-based delay prevents instant completion"):
    // 300 WPM = 200ms per word
    val timedConfig = RsvpConfig(
      baseWpm = 300,
      startDelay = Duration.Zero,
      commaDelay = Duration.Zero,
      periodDelay = Duration.Zero,
      paragraphDelay = Duration.Zero,
      wordLengthEnabled = false
    )
    val singleToken = Span.from(Seq(Token("test", 1, Punctuation.None, 0, 0)))
    runTest {
      Clock.withTimeControl { control =>
        direct {
          val h     = Harness.init(timedConfig).now
          val fiber = h.start(singleToken).now
          h.send(Command.Resume).now
          tick.now
          control.advance(150.millis).now
          tick.now
          val isDoneBefore = fiber.done.now

          control.advance(100.millis).now
          tick.now
          fiber.get.now
          val states = h.collectUntilStopped.now
          assert(!isDoneBefore, "Should not be done before 200ms delay elapses")
          assertEquals(states.last.status, PlayStatus.Stopped)
        }
      }
    }

  // -- Dynamic config tests (AtomicRef mutation mid-playback) --

  test("enabling paragraphAutoPause mid-playback causes pause at paragraph boundary"):
    runTest {
      direct {
        // Start with paragraphAutoPause=false, fast config
        val h     = Harness.init(fastConfig).now
        val fiber = h.start(twoParagraphTokens).now
        h.send(Command.Resume).now
        Async.sleep(5.millis).now

        // Enable paragraphAutoPause while playing
        h.configRef.set(fastConfig.copy(paragraphAutoPause = true)).now
        Async.sleep(50.millis).now

        // Engine should have auto-paused at the paragraph boundary
        val states = h.drainAvailable.now
        // Last emitted Playing state should be at idx=1 ("world.") — engine paused before idx=2
        val playingIndices = states.filter(_.status == PlayStatus.Playing).map(_.index)
        assert(playingIndices.nonEmpty && playingIndices.max <= 1,
          s"Expected engine to pause at paragraph boundary (idx <= 1), got playing indices: $playingIndices")

        // Resume to finish
        h.send(Command.Resume).now
        fiber.get.now
        val finalStates = h.collectUntilStopped.now
        assertEquals(finalStates.last.status, PlayStatus.Stopped)
      }
    }

  test("disabling paragraphAutoPause mid-playback skips paragraph pause"):
    runTest {
      direct {
        // Start with paragraphAutoPause=true, fast config
        val configWithAutoPause = fastConfig.copy(paragraphAutoPause = true)
        val h     = Harness.init(configWithAutoPause).now
        val fiber = h.start(twoParagraphTokens).now
        h.send(Command.Resume).now
        Async.sleep(5.millis).now

        // Disable paragraphAutoPause while playing
        h.configRef.set(fastConfig.copy(paragraphAutoPause = false)).now

        // Engine should run to completion without pausing at paragraph boundary
        fiber.get.now
        val states = h.collectUntilStopped.now

        // Should only see the initial Paused state (from start), no mid-playback pause
        val pausedAfterStart = states.drop(1).filter(_.status == PlayStatus.Paused)
        assertEquals(pausedAfterStart.length, 0,
          s"Expected no auto-pause after disabling, got: ${states.map(s => (s.index, s.status))}")
      }
    }

  test("changing periodDelay mid-playback affects subsequent token timing"):
    runTest {
      Clock.withTimeControl { control =>
        direct {
          // Start with 200ms period delay, fast base WPM
          val slowPeriodConfig = RsvpConfig(
            baseWpm = Int.MaxValue,
            startDelay = Duration.Zero,
            commaDelay = Duration.Zero,
            periodDelay = 200.millis,
            paragraphDelay = Duration.Zero,
            wordLengthEnabled = false
          )
          val periodToken = Span.from(Seq(
            Token("end.", 1, Punctuation.Period, 0, 0),
            Token("next", 1, Punctuation.None, 1, 0)
          ))
          val h     = Harness.init(slowPeriodConfig).now
          val fiber = h.start(periodToken).now
          h.send(Command.Resume).now
          tick.now

          // Advance 150ms — should NOT be done (period delay is 200ms)
          control.advance(150.millis).now
          tick.now
          val doneAt150 = fiber.done.now
          assert(!doneAt150, "Should not be done at 150ms with 200ms period delay")

          // Now change period delay to 0ms
          h.configRef.set(slowPeriodConfig.copy(periodDelay = Duration.Zero)).now

          // Advance enough to finish with the old pending sleep
          control.advance(100.millis).now
          tick.now
          // Advance a bit more for the second token (near-zero delay)
          control.advance(50.millis).now
          tick.now
          advanceUntilDone(control, fiber).now

          val states = h.collectUntilStopped.now
          assertEquals(states.last.status, PlayStatus.Stopped)
        }
      }
    }
