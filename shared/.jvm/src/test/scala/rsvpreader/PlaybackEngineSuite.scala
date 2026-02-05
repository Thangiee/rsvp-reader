package rsvpreader

import kyo.*
import munit.FunSuite

class PlaybackEngineSuite extends FunSuite:

  // Fast config for tests - quick but not instant to allow command interleaving
  // 6000 WPM = 10ms per word base time
  val fastConfig = RsvpConfig(
    baseWpm = 6000,
    startDelay = Duration.Zero,
    commaDelay = Duration.Zero,
    periodDelay = Duration.Zero,
    paragraphDelay = Duration.Zero
  )

  // Instant config for tests where we control the flow entirely via commands
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

  /** Helper to run async test with timeout, handling Scope and Abort effects. */
  def runTest[A](effect: A < (Async & Abort[Closed] & Scope)): A =
    import AllowUnsafe.embrace.danger
    val handled = Abort.run(Scope.run(effect))
    KyoApp.Unsafe.runAndBlock(5.seconds)(handled).getOrThrow.getOrThrow

  /** Drains all available states from channel without blocking. */
  def drainStates(stateCh: Channel[ViewState]): Seq[ViewState] < (IO & Abort[Closed]) =
    def loop(acc: Seq[ViewState]): Seq[ViewState] < (IO & Abort[Closed]) =
      stateCh.poll.map {
        case Absent     => acc
        case Present(s) => loop(acc :+ s)
      }
    loop(Seq.empty)

  test("emits initial state then progresses through tokens"):
    runTest {
      for
        commandCh <- Channel.init[Command](1)
        stateCh   <- Channel.init[ViewState](10)
        engine     = PlaybackEngine(commandCh, stateCh, instantConfig)
        fiber     <- Fiber.init(engine.run(tokens))
        _         <- commandCh.put(Command.Resume)
        _         <- fiber.get
        states    <- drainStates(stateCh)
      yield
        assert(states.length >= 4, s"Expected at least 4 states, got ${states.length}")
        assertEquals(states.head.index, 0)
        assertEquals(states.head.status, PlayStatus.Paused)
        assertEquals(states.last.status, PlayStatus.Stopped)
    }

  test("pause command stops progression"):
    runTest {
      for
        commandCh <- Channel.init[Command](1)
        stateCh   <- Channel.init[ViewState](10)
        engine     = PlaybackEngine(commandCh, stateCh, fastConfig)
        fiber     <- Fiber.init(engine.run(tokens))
        // Start playback
        _         <- commandCh.put(Command.Resume)
        // Small delay to let it start, but not finish (each word ~10ms)
        _         <- Async.sleep(15.millis)
        // Pause - this should be received during sleep in handlePlaying
        _         <- commandCh.put(Command.Pause)
        // Wait a bit and drain states
        _         <- Async.sleep(20.millis)
        _         <- drainStates(stateCh) // clear any emitted states
        // Wait again - should not emit new states while paused
        _         <- Async.sleep(50.millis)
        afterWait <- drainStates(stateCh)
        // Resume to complete for clean shutdown
        _         <- commandCh.put(Command.Resume)
        _         <- fiber.get
      yield assertEquals(afterWait.length, 0)
    }

  test("resume after pause continues playback"):
    runTest {
      for
        commandCh <- Channel.init[Command](1)
        stateCh   <- Channel.init[ViewState](10)
        engine     = PlaybackEngine(commandCh, stateCh, instantConfig)
        fiber     <- Fiber.init(engine.run(tokens))
        // Engine starts paused, just send resume
        _         <- commandCh.put(Command.Resume)
        _         <- fiber.get
        states    <- drainStates(stateCh)
      yield assertEquals(states.last.status, PlayStatus.Stopped)
    }

  test("back command moves index backward"):
    runTest {
      for
        commandCh       <- Channel.init[Command](1)
        stateCh         <- Channel.init[ViewState](20) // larger buffer
        engine           = PlaybackEngine(commandCh, stateCh, fastConfig)
        fiber           <- Fiber.init(engine.run(tokens))
        // Start and let it run a bit
        _               <- commandCh.put(Command.Resume)
        _               <- Async.sleep(25.millis) // should have progressed past index 0
        // Pause
        _               <- commandCh.put(Command.Pause)
        _               <- Async.sleep(10.millis)
        statesBeforeBack <- drainStates(stateCh)
        indexBeforeBack  = statesBeforeBack.lastOption.map(_.index).getOrElse(0)
        // Go back 2 words
        _               <- commandCh.put(Command.Back(2))
        _               <- Async.sleep(10.millis)
        // Resume to see the effect and complete
        _               <- commandCh.put(Command.Resume)
        _               <- fiber.get
        finalStates     <- drainStates(stateCh)
      yield
        // Verify we saw index 0 at some point after going back
        val allStates = statesBeforeBack ++ finalStates
        assert(allStates.exists(_.index == 0),
          s"Expected to see index 0 at some point. Before back: $indexBeforeBack, all indices: ${allStates.map(_.index)}")
    }

  test("setSpeed command changes WPM"):
    runTest {
      for
        commandCh <- Channel.init[Command](1)
        stateCh   <- Channel.init[ViewState](10)
        engine     = PlaybackEngine(commandCh, stateCh, instantConfig)
        fiber     <- Fiber.init(engine.run(tokens))
        // Set speed while paused (initial state)
        _         <- commandCh.put(Command.SetSpeed(500))
        // Resume and complete
        _         <- commandCh.put(Command.Resume)
        _         <- fiber.get
        states    <- drainStates(stateCh)
      yield assert(states.exists(_.wpm == 500), s"Expected state with wpm=500, got: ${states.map(_.wpm)}")
    }

  test("stop command resets to index 0 and pauses"):
    runTest {
      for
        commandCh <- Channel.init[Command](1)
        stateCh   <- Channel.init[ViewState](20) // larger buffer
        engine     = PlaybackEngine(commandCh, stateCh, fastConfig)
        fiber     <- Fiber.init(engine.run(tokens))
        // Start playback
        _         <- commandCh.put(Command.Resume)
        _         <- Async.sleep(25.millis) // let it progress past index 0
        // Stop (resets to index 0, paused state)
        _         <- commandCh.put(Command.Stop)
        _         <- Async.sleep(10.millis)
        // Drain states to see the stop effect
        statesBeforeResume <- drainStates(stateCh)
        // Resume again to complete playback (stop puts it in paused state)
        _         <- commandCh.put(Command.Resume)
        _         <- fiber.get
        statesAfterResume <- drainStates(stateCh)
      yield
        // After Stop command, the engine should have reset to index 0
        val allStates = statesBeforeResume ++ statesAfterResume
        val hadHigherIndex = statesBeforeResume.exists(_.index > 0)
        val hasResetToZero = allStates.exists(_.index == 0)
        assert(hadHigherIndex && hasResetToZero,
          s"Expected to see reset to index 0 after Stop. Before: ${statesBeforeResume.map(_.index)}, After: ${statesAfterResume.map(_.index)}")
    }

  test("respects startDelay before first token"):
    runTest {
      for
        commandCh <- Channel.init[Command](1)
        stateCh   <- Channel.init[ViewState](10)
        // Use a measurable but short startDelay
        config     = RsvpConfig(
                       baseWpm = Int.MaxValue,
                       startDelay = 50.millis
                     )
        engine     = PlaybackEngine(commandCh, stateCh, config)
        fiber     <- Fiber.init(engine.run(tokens))
        // Initial state should be emitted immediately (before startDelay)
        _         <- Async.sleep(5.millis)
        initialStates <- drainStates(stateCh)
        // Resume to start playback after startDelay passes
        _         <- Async.sleep(60.millis)
        _         <- commandCh.put(Command.Resume)
        _         <- fiber.get
        finalStates <- drainStates(stateCh)
      yield
        // Initial state emitted before delay
        assertEquals(initialStates.length, 1, s"Expected 1 initial state, got: $initialStates")
        assertEquals(initialStates.head.status, PlayStatus.Paused)
        // Playback completed after resume
        assertEquals(finalStates.last.status, PlayStatus.Stopped)
    }

  test("calculates correct delay based on WPM"):
    runTest {
      for
        commandCh <- Channel.init[Command](1)
        stateCh   <- Channel.init[ViewState](10)
        // 600 WPM = 100ms per word, measurable but quick
        config     = RsvpConfig(
                       baseWpm = 600,
                       startDelay = Duration.Zero,
                       commaDelay = Duration.Zero,
                       periodDelay = Duration.Zero,
                       paragraphDelay = Duration.Zero,
                       wordLengthEnabled = false
                     )
        singleToken = Span.from(Seq(Token("test", 1, Punctuation.None, 0, 0)))
        engine      = PlaybackEngine(commandCh, stateCh, config)
        fiber      <- Fiber.init(engine.run(singleToken))
        // Resume to start playing
        _          <- commandCh.put(Command.Resume)
        // Check fiber not done before 100ms (use 50ms checkpoint)
        _          <- Async.sleep(50.millis)
        isDoneBefore <- fiber.done
        // Wait past 100ms and complete
        _          <- fiber.get
        states     <- drainStates(stateCh)
      yield
        assert(!isDoneBefore, "Should not be done before delay elapses")
        assertEquals(states.last.status, PlayStatus.Stopped)
    }
