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
  * Note: This loop handles ONE reading session. The engineLoop in Main.scala
  * handles MULTIPLE sessions by waiting for new text after this loop exits.
  *
  * Uses Kyo Channel for responsive command processing (pause/resume interrupts sleep).
  * Uses Kyo Loop for stack-safe, explicit state machine semantics.
  *
  * @param commands      Channel for receiving playback commands (pause/resume/back/speed)
  * @param config        RSVP timing configuration (WPM, delays, etc.)
  * @param onStateChange Callback invoked on each state update for UI rendering
  */
class PlaybackEngine(
  commands: Channel[Command],
  config: RsvpConfig,
  onStateChange: ViewState => Unit
):
  // Type alias for loop outcomes - either continue with new state or done with result
  private type Outcome = Loop.Outcome[ViewState, Unit]

  // Type alias for the effect stack
  private type PlaybackEffect = Async & Abort[Closed]

  // Helper to create done outcome with explicit types
  private def done: Outcome = Loop.done[ViewState, Unit](())

  /** Notifies the UI of a state change. */
  private def notify(state: ViewState): Unit < Sync =
    Sync.Unsafe(onStateChange(state))

  def run(tokens: Span[Token]): Unit < PlaybackEffect =
    val initial = ViewState.initial(tokens, config)
    println(s"PlaybackEngine.run: ${tokens.length} tokens, initial status=${initial.status}")
    notify(initial).andThen {
      println("PlaybackEngine: notify done, starting loop")
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
        notify(stopped).andThen(done)

      case Present(token) =>
        notify(state).andThen {
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
