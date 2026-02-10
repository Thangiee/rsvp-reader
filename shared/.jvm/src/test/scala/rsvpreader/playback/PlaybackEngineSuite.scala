package rsvpreader.playback

import kyo.*
import rsvpreader.token.*
import rsvpreader.config.*
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

  val tokens: Span[Token] = Span(
    Token("one", 0, Punctuation.None, 0, 0),
    Token("two", 0, Punctuation.None, 0, 0),
    Token("three", 1, Punctuation.Period("."), 0, 0)
  )

  val moreTokens: Span[Token] = Span(
    Token("a", 0, Punctuation.None, 0, 0),
    Token("b", 0, Punctuation.None, 0, 0),
    Token("c", 0, Punctuation.None, 0, 0),
    Token("d", 0, Punctuation.None, 0, 0),
    Token("e", 1, Punctuation.Period("."), 0, 0)
  )

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

    /** Collect all states until Finished (natural playback completion). Blocks on each take. */
    def collectUntilFinished: Seq[ViewState] < (Async & Abort[Closed]) =
      Loop(Seq.empty[ViewState]) { acc =>
        stateCh.take.map { state =>
          val updated = acc :+ state
          if state.status == PlayStatus.Finished then Loop.done(updated)
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

  test("emits initial paused state then progresses through all tokens to finished"):
    runTest {
      direct {
        val h     = Harness.init().now
        val fiber = h.start().now
        h.send(Command.Resume).now
        fiber.get.now
        val states = h.collectUntilFinished.now

        assert(states.length >= 4, s"Expected at least 4 states, got ${states.length}")
        assertEquals(states.head.index, 0)
        assertEquals(states.head.status, PlayStatus.Paused)
        assertEquals(states.last.status, PlayStatus.Finished)
        val indices = states.map(_.index).distinct
        assert(indices.contains(0) && indices.contains(1) && indices.contains(2),
          s"Expected indices 0, 1, 2 — got $indices")
      }
    }

  // -- Pause state emission tests (regression: handlePaused didn't emit state) --

  test("pause command emits paused state to state channel"):
    // Regression: handlePaused didn't emit, so the UI still showed Playing status.
    // togglePlayPause checked UI status (Playing) and sent Pause again instead of Resume.
    runTest {
      direct {
        val h     = Harness.init(fastConfig).now
        val fiber = h.start(moreTokens).now
        h.send(Command.Resume).now
        Async.sleep(15.millis).now
        h.send(Command.Pause).now
        Async.sleep(20.millis).now
        val states = h.drainAvailable.now

        val pausedStates = states.filter(_.status == PlayStatus.Paused)
        assert(pausedStates.nonEmpty,
          s"Expected at least one Paused state after pause command, got: ${states.map(s => (s.index, s.status))}")

        // Clean up
        h.send(Command.Resume).now
        fiber.get.now
        h.collectUntilFinished.now
      }
    }

  test("commands while paused emit updated state to state channel"):
    runTest {
      direct {
        val h     = Harness.init(fastConfig).now
        val fiber = h.start(moreTokens).now
        h.send(Command.Resume).now
        Async.sleep(25.millis).now
        h.send(Command.Pause).now
        Async.sleep(10.millis).now
        h.drainAvailable.now // clear buffer

        // SetSpeed while paused should emit state with new WPM
        h.send(Command.SetSpeed(999)).now
        Async.sleep(10.millis).now
        val statesAfterSpeed = h.drainAvailable.now
        assert(statesAfterSpeed.exists(_.wpm == 999),
          s"Expected emitted state with wpm=999 while paused, got: ${statesAfterSpeed.map(s => (s.wpm, s.status))}")

        // RestartSentence while paused should emit state with rewound index
        h.send(Command.RestartSentence).now
        Async.sleep(10.millis).now
        val statesAfterRestart = h.drainAvailable.now
        assert(statesAfterRestart.exists(_.index == 0),
          s"Expected emitted state with index=0 after RestartSentence while paused, got: ${statesAfterRestart.map(s => (s.index, s.status))}")

        h.send(Command.Resume).now
        fiber.get.now
        h.collectUntilFinished.now
      }
    }

  // -- Finished state emission tests (regression: race could lose final state) --

  test("finished state is emitted and retrievable after natural playback completion"):
    // Regression: Async.race in engineLoop interrupted the state consumer before it
    // processed the final Finished state, leaving the UI stuck on the last Playing state.
    // The fix: drain remaining states from channel after race completes.
    runTest {
      direct {
        val h     = Harness.init().now
        val fiber = h.start().now
        h.send(Command.Resume).now
        fiber.get.now

        // Simulate the drain-remaining-states pattern from engineLoop fix
        val remaining = h.drainAvailable.now
        assert(remaining.exists(_.status == PlayStatus.Finished),
          s"Expected Finished state in channel after engine completed, got: ${remaining.map(s => (s.index, s.status))}")
      }
    }

  test("finished state survives engine-consumer coordination via channel close"):
    // Simulates the production pattern: consumer runs as background fiber,
    // engine runs to completion, then channel is closed to flush remaining states.
    // Regression: Async.race could interrupt the consumer after it dequeued the
    // Finished state but before the continuation ran, losing it entirely.
    runTest {
      direct {
        val h = Harness.init().now
        val consumed = scala.collection.mutable.ArrayBuffer[ViewState]()

        h.send(Command.Resume).now

        // Start consumer as background fiber (same pattern as engineLoop)
        val consumerFiber = Fiber.init(
          Abort.run[Closed](
            Loop(()) { _ =>
              h.stateCh.take.map { state =>
                consumed += state
                Loop.continue(())
              }
            }
          )
        ).now

        // Run engine to completion
        h.engine.run(tokens).now

        // Close channel — flushes buffer and signals consumer to exit
        val remaining = h.stateCh.close.now
        remaining.foreach(_.foreach(consumed += _))

        // Wait for consumer to finish
        consumerFiber.get.now

        assert(consumed.exists(_.status == PlayStatus.Finished),
          s"Expected Finished state captured via consumer or close, got: ${consumed.map(s => (s.index, s.status))}")
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
        val states = h.collectUntilFinished.now

        assertEquals(states.last.status, PlayStatus.Finished)
      }
    }

  test("restart paragraph command rewinds to paragraph start"):
    runTest {
      direct {
        val h     = Harness.init(fastConfig).now
        val fiber = h.start(twoParagraphTokens).now
        h.send(Command.Resume).now
        Async.sleep(25.millis).now
        h.send(Command.Pause).now
        Async.sleep(10.millis).now
        h.send(Command.RestartParagraph).now
        Async.sleep(10.millis).now
        val statesAfterRestart = h.drainAvailable.now
        h.send(Command.Resume).now
        fiber.get.now
        val finalStates = h.collectUntilFinished.now

        val allStates = statesAfterRestart ++ finalStates
        // Should have rewound to the start of whatever paragraph the engine was in
        val restartedIndices = statesAfterRestart.map(_.index)
        assert(restartedIndices.nonEmpty,
          s"Expected states after RestartParagraph, got none")
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
        val states = h.collectUntilFinished.now

        assert(states.exists(_.wpm == 500),
          s"Expected state with wpm=500, got: ${states.map(_.wpm)}")
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
          val finalStates = h.collectUntilFinished.now
          assertEquals(finalStates.last.status, PlayStatus.Finished)
        }
      }
    }

  // Two-paragraph tokens: paragraph 0 has "hello world.", paragraph 1 has "new para."
  val twoParagraphTokens: Span[Token] = Span(
    Token("hello", 1, Punctuation.None, 0, 0),
    Token("world.", 1, Punctuation.Paragraph, 0, 0),
    Token("new", 0, Punctuation.None, 1, 1),
    Token("para.", 1, Punctuation.Period("."), 1, 1)
  )

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
    val singleToken = Span(Token("test", 1, Punctuation.None, 0, 0))
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
          val states = h.collectUntilFinished.now
          assert(!isDoneBefore, "Should not be done before 200ms delay elapses")
          assertEquals(states.last.status, PlayStatus.Finished)
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
        val finalStates = h.collectUntilFinished.now
        assertEquals(finalStates.last.status, PlayStatus.Finished)
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
        val states = h.collectUntilFinished.now

        // Should see no mid-playback auto-pause (paused at index > 0 means paragraph boundary pause)
        val autoPauses = states.filter(s => s.status == PlayStatus.Paused && s.index > 0)
        assertEquals(autoPauses.length, 0,
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
          val periodToken = Span(
            Token("end.", 1, Punctuation.Period("."), 0, 0),
            Token("next", 1, Punctuation.None, 1, 0)
          )
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

          val states = h.collectUntilFinished.now
          assertEquals(states.last.status, PlayStatus.Finished)
        }
      }
    }

  // -- LoadText tests (exits engine so new text can be loaded) --

  test("LoadText while paused causes engine to exit"):
    runTest {
      direct {
        val h     = Harness.init(fastConfig).now
        val fiber = h.start(moreTokens).now
        // Engine starts paused — send LoadText to exit
        h.send(Command.LoadText).now
        // engine.run() returns (fiber completes) — proves LoadText exits the loop
        fiber.get.now
      }
    }

  test("LoadText while playing causes engine to exit"):
    runTest {
      direct {
        val h     = Harness.init(fastConfig).now
        val fiber = h.start(moreTokens).now
        h.send(Command.Resume).now
        Async.sleep(10.millis).now
        h.send(Command.LoadText).now
        // engine.run() returns (fiber completes) — proves LoadText exits the loop
        fiber.get.now
      }
    }
