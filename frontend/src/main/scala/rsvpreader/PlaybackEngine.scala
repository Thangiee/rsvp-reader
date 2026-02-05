package rsvpreader

import kyo.*

/** Async playback engine that displays tokens at configured speed with command handling.
  * Uses Kyo Channel for responsive command processing (pause/resume interrupts sleep).
  *
  * @param commands      Channel for receiving playback commands
  * @param config        RSVP timing configuration
  * @param onStateChange Callback invoked on each state update for UI rendering
  */
class PlaybackEngine(
  commands: Channel[Command],
  config: RsvpConfig,
  onStateChange: ViewState => Unit
):

  private def notify(state: ViewState): Unit < Sync =
    Sync.Unsafe(onStateChange(state))

  def run(tokens: Span[Token]): Unit < (Async & Abort[Closed]) =
    val initial = ViewState.initial(tokens, config)
    notify(initial).andThen {
      if config.startDelay > Duration.Zero then
        Async.sleep(config.startDelay).andThen(loop(initial))
      else
        loop(initial)
    }

  private def loop(state: ViewState): Unit < (Async & Abort[Closed]) =
    state.status match
      case PlayStatus.Stopped => ()
      case PlayStatus.Paused  => handlePaused(state)
      case PlayStatus.Playing => handlePlaying(state)

  private def handlePlaying(state: ViewState): Unit < (Async & Abort[Closed]) =
    state.currentToken match
      case Absent =>
        val done = state.copy(status = PlayStatus.Stopped)
        notify(done)

      case Present(token) =>
        notify(state).andThen {
          val delay = calculateDelay(token, config.copy(baseWpm = state.wpm))

          Async.race(
            Async.sleep(delay).map(_ => Absent),
            commands.take.map(cmd => Maybe(cmd))
          ).flatMap {
            case Absent =>
              val next = state.copy(index = state.index + 1)
              checkParagraphAutoPause(next, token)
            case Present(cmd) =>
              processCommand(cmd, state)
          }
        }

  private def handlePaused(state: ViewState): Unit < (Async & Abort[Closed]) =
    commands.take.flatMap(cmd => processCommand(cmd, state))

  private def checkParagraphAutoPause(state: ViewState, prevToken: Token): Unit < (Async & Abort[Closed]) =
    val shouldPause = config.paragraphAutoPause &&
      prevToken.isEndOfParagraph(state.currentToken)
    if shouldPause then loop(state.copy(status = PlayStatus.Paused))
    else loop(state)

  private def processCommand(cmd: Command, state: ViewState): Unit < (Async & Abort[Closed]) =
    cmd match
      case Command.Pause =>
        val paused = state.copy(status = PlayStatus.Paused)
        notify(paused).andThen(loop(paused))

      case Command.Resume =>
        loop(state.copy(status = PlayStatus.Playing))

      case Command.Back(n) =>
        val rewound = state.copy(index = Math.max(0, state.index - n))
        notify(rewound).andThen(loop(rewound))

      case Command.RestartSentence =>
        val currentSentence = state.currentToken.fold(-1)(_.sentenceIndex)
        val sentenceStart = findSentenceStart(state.tokens, state.index, currentSentence)
        val restarted = state.copy(index = sentenceStart)
        notify(restarted).andThen(loop(restarted))

      case Command.SetSpeed(wpm) =>
        loop(state.copy(wpm = wpm))

      case Command.Stop =>
        val stopped = state.copy(status = PlayStatus.Stopped)
        notify(stopped)

  private def findSentenceStart(tokens: Span[Token], current: Int, sentenceIdx: Int): Int =
    var i = current
    while i > 0 && tokens(i - 1).sentenceIndex == sentenceIdx do
      i -= 1
    i
