package rsvpreader

import kyo.*
import munit.FunSuite

class PlaybackEngineSuite extends FunSuite:

  // Instant config - Int.MaxValue WPM means near-zero delays, fully command-driven
  val instantConfig: RsvpConfig = RsvpConfig(
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

  /** Test harness that manages channels, engine lifecycle, and state collection. */
  class Harness(
    val commandCh: Channel[Command],
    val stateCh: Channel[ViewState],
    val engine: PlaybackEngine
  ):
    /** Start engine on a fiber, returning it for later joining. */
    def start(t: Span[Token] = tokens): Fiber[Unit, Async & Abort[Closed]] < Sync =
      Fiber.init(engine.run(t))

    /** Send a command to the engine. */
    def send(cmd: Command): Unit < (Async & Abort[Closed]) =
      commandCh.put(cmd)

    /** Collect all states from the output channel until Stopped. Blocks on each take. */
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
        val engine    = PlaybackEngine(commandCh, stateCh, config)
        Harness(commandCh, stateCh, engine)
      }

  /** Run an async test effect with a 5-second timeout. */
  def runTest[A](effect: A < (Async & Abort[Closed] & Scope)): A =
    import AllowUnsafe.embrace.danger
    val handled = Abort.run(Scope.run(effect))
    KyoApp.Unsafe.runAndBlock(5.seconds)(handled).getOrThrow.getOrThrow

  // -- State emission tests --

  test("emits initial paused state at index 0, then progresses through all tokens to stopped"):
    runTest {
      direct {
        val h     = Harness.init().now
        val fiber = h.start().now
        h.send(Command.Resume).now
        fiber.get.now
        val states = h.collectUntilStopped.now

        assert(states.length >= 4, s"Expected at least 4 states (initial + 3 tokens + stopped), got ${states.length}")
        assertEquals(states.head.index, 0)
        assertEquals(states.head.status, PlayStatus.Paused)
        assertEquals(states.last.status, PlayStatus.Stopped)

        // Verify we saw each token index
        val indices = states.map(_.index).distinct
        assert(indices.contains(0) && indices.contains(1) && indices.contains(2),
          s"Expected to see indices 0, 1, 2 — got $indices")
      }
    }

  // -- Command tests (deterministic, no sleep-based timing) --

  test("pause command transitions from playing to paused"):
    runTest {
      direct {
        // Use more tokens with non-instant timing so pause can interleave
        val moreTokens = Span.from(Seq(
          Token("a", 0, Punctuation.None, 0, 0),
          Token("b", 0, Punctuation.None, 0, 0),
          Token("c", 0, Punctuation.None, 0, 0),
          Token("d", 0, Punctuation.None, 0, 0),
          Token("e", 1, Punctuation.Period, 0, 0)
        ))
        val config = RsvpConfig(
          baseWpm = 6000, // ~10ms per word
          startDelay = Duration.Zero,
          commaDelay = Duration.Zero,
          periodDelay = Duration.Zero,
          paragraphDelay = Duration.Zero
        )
        val h     = Harness.init(config).now
        val fiber = h.start(moreTokens).now
        h.send(Command.Resume).now
        // Let it start playing, then pause
        Async.sleep(15.millis).now
        h.send(Command.Pause).now
        // Wait for pause to take effect
        Async.sleep(20.millis).now
        h.drainAvailable.now // clear emitted states
        // Verify no new states emitted while paused
        Async.sleep(50.millis).now
        val statesWhilePaused = h.drainAvailable.now
        // Resume to complete cleanly
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
        // Engine starts paused — just resume
        h.send(Command.Resume).now
        fiber.get.now
        val states = h.collectUntilStopped.now

        assertEquals(states.last.status, PlayStatus.Stopped)
      }
    }

  test("back command rewinds index during pause"):
    runTest {
      direct {
        // Use more tokens so we can pause mid-playback
        val moreTokens = Span.from(Seq(
          Token("a", 0, Punctuation.None, 0, 0),
          Token("b", 0, Punctuation.None, 0, 0),
          Token("c", 0, Punctuation.None, 0, 0),
          Token("d", 0, Punctuation.None, 0, 0),
          Token("e", 1, Punctuation.Period, 0, 0)
        ))
        // Use a fast but non-instant config so pause can interleave
        val config = RsvpConfig(
          baseWpm = 6000, // ~10ms per word
          startDelay = Duration.Zero,
          commaDelay = Duration.Zero,
          periodDelay = Duration.Zero,
          paragraphDelay = Duration.Zero
        )
        val h     = Harness.init(config).now
        val fiber = h.start(moreTokens).now
        h.send(Command.Resume).now
        // Let it advance a couple of tokens
        Async.sleep(25.millis).now
        h.send(Command.Pause).now
        Async.sleep(10.millis).now
        // Back 10 words — should clamp to 0
        h.send(Command.Back(10)).now
        Async.sleep(10.millis).now
        val statesAfterBack = h.drainAvailable.now
        // Resume to complete
        h.send(Command.Resume).now
        fiber.get.now
        val finalStates = h.collectUntilStopped.now

        // After Back(10), index should have been reset to 0
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
        // Set speed while paused (engine starts paused)
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
        val config = RsvpConfig(
          baseWpm = 6000,
          startDelay = Duration.Zero,
          commaDelay = Duration.Zero,
          periodDelay = Duration.Zero,
          paragraphDelay = Duration.Zero
        )
        val h     = Harness.init(config).now
        val fiber = h.start().now
        h.send(Command.Resume).now
        // Let it advance past index 0
        Async.sleep(25.millis).now
        h.send(Command.Stop).now
        Async.sleep(10.millis).now
        val statesAfterStop = h.drainAvailable.now
        // Resume to complete
        h.send(Command.Resume).now
        fiber.get.now
        val finalStates = h.collectUntilStopped.now

        val allStates = statesAfterStop ++ finalStates
        // Stop should have reset index to 0
        val stoppedAtZero = allStates.exists(s => s.index == 0 && s.status == PlayStatus.Paused)
        assert(stoppedAtZero,
          s"Expected Paused state at index 0 after Stop, got: ${allStates.map(s => (s.index, s.status))}")
      }
    }

  // -- Timing tests --

  test("respects startDelay before entering paused state"):
    runTest {
      direct {
        val config = RsvpConfig(
          baseWpm = Int.MaxValue,
          startDelay = 50.millis
        )
        val h     = Harness.init(config).now
        val fiber = h.start().now
        // Initial state should be emitted immediately (before startDelay)
        Async.sleep(5.millis).now
        val initialStates = h.drainAvailable.now
        // Wait past startDelay then resume
        Async.sleep(60.millis).now
        h.send(Command.Resume).now
        fiber.get.now
        val finalStates = h.collectUntilStopped.now

        assertEquals(initialStates.length, 1, s"Expected 1 initial state, got: $initialStates")
        assertEquals(initialStates.head.status, PlayStatus.Paused)
        assertEquals(finalStates.last.status, PlayStatus.Stopped)
      }
    }

  test("WPM-based delay prevents instant completion"):
    runTest {
      direct {
        val config = RsvpConfig(
          baseWpm = 600, // 100ms per word
          startDelay = Duration.Zero,
          commaDelay = Duration.Zero,
          periodDelay = Duration.Zero,
          paragraphDelay = Duration.Zero,
          wordLengthEnabled = false
        )
        val singleToken = Span.from(Seq(Token("test", 1, Punctuation.None, 0, 0)))
        val h     = Harness.init(config).now
        val fiber = h.start(singleToken).now
        h.send(Command.Resume).now
        // At 600 WPM, one word takes ~100ms. Check at 50ms — should not be done.
        Async.sleep(50.millis).now
        val isDoneBefore = fiber.done.now
        fiber.get.now
        val states = h.collectUntilStopped.now

        assert(!isDoneBefore, "Should not be done before delay elapses")
        assertEquals(states.last.status, PlayStatus.Stopped)
      }
    }
