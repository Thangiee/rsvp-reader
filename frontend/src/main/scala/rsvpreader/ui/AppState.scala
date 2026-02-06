package rsvpreader.ui

import com.raquo.laminar.api.L.{Var as LaminarVar, Signal as LaminarSignal, Span as _, *}
import kyo.*
import rsvpreader.*

/** Centralized reactive state for the RSVP reader UI. */
object AppState:
  // ─────────────────────────────────────────────────────────────────────────
  // Core State
  // ─────────────────────────────────────────────────────────────────────────

  val viewState: LaminarVar[ViewState] = LaminarVar(ViewState(
    tokens = Span.empty,
    index = 0,
    status = PlayStatus.Stopped,
    wpm = 300
  ))

  val showParagraphView: LaminarVar[Boolean] = LaminarVar(false)
  val showTextInputModal: LaminarVar[Boolean] = LaminarVar(true) // Show on start
  val showSettingsModal: LaminarVar[Boolean] = LaminarVar(false)
  val inputText: LaminarVar[String] = LaminarVar("")

  // Settings state
  val currentKeyBindings: LaminarVar[KeyBindings] = LaminarVar(KeyBindings.default)
  val currentCenterMode: LaminarVar[CenterMode] = LaminarVar(CenterMode.ORP)
  val capturingKeyFor: LaminarVar[Option[KeyAction]] = LaminarVar(None)
  val contextSentences: LaminarVar[Int] = LaminarVar(1)

  // Config and channel references - set by Main during initialization
  private var _configRef: Result[String, AtomicRef[RsvpConfig]] = Result.fail("Config ref not initialized")
  private var _commandChannel: Result[String, Channel[Command]] = Result.fail("Command channel not initialized")
  private var _tokensChannel: Result[String, Channel[Span[Token]]] = Result.fail("Tokens channel not initialized")

  def setConfigRef(ref: AtomicRef[RsvpConfig]): Unit =
    _configRef = Result.succeed(ref)

  def setCommandChannel(ch: Channel[Command]): Unit =
    _commandChannel = Result.succeed(ch)

  def setTokensChannel(ch: Channel[Span[Token]]): Unit =
    _tokensChannel = Result.succeed(ch)

  /** Returns the command channel, or fails with Abort if not initialized. */
  def getCommandChannel: Channel[Command] < Abort[String] =
    _commandChannel.fold(
      onSuccess = ch => ch,
      onFailure = err => Abort.fail(err),
      onPanic = ex => Abort.fail(ex.getMessage)
    )

  /** Returns the tokens channel, or fails with Abort if not initialized. */
  def getTokensChannel: Channel[Span[Token]] < Abort[String] =
    _tokensChannel.fold(
      onSuccess = ch => ch,
      onFailure = err => Abort.fail(err),
      onPanic = ex => Abort.fail(ex.getMessage)
    )

  /** Returns the config ref, or fails with Abort if not initialized. */
  def getConfigRef: AtomicRef[RsvpConfig] < Abort[String] =
    _configRef.fold(
      onSuccess = ref => ref,
      onFailure = err => Abort.fail(err),
      onPanic = ex => Abort.fail(ex.getMessage)
    )

  /** Unsafe access to command channel - throws if not initialized. */
  def unsafeGetCommandChannel(using AllowUnsafe): Channel[Command] =
    _commandChannel.fold(
      onSuccess = identity,
      onFailure = err => throw new IllegalStateException(err),
      onPanic = ex => throw ex
    )

  /** Unsafe access to tokens channel - throws if not initialized. */
  def unsafeGetTokensChannel(using AllowUnsafe): Channel[Span[Token]] =
    _tokensChannel.fold(
      onSuccess = identity,
      onFailure = err => throw new IllegalStateException(err),
      onPanic = ex => throw ex
    )

  // ─────────────────────────────────────────────────────────────────────────
  // Command Dispatch
  // ─────────────────────────────────────────────────────────────────────────

  def sendCommand(cmd: Command)(using AllowUnsafe): Unit =
    _commandChannel.foreach(_.unsafe.offer(cmd))

  def togglePlayPause()(using AllowUnsafe): Unit =
    viewState.now().status match
      case PlayStatus.Playing  => sendCommand(Command.Pause)
      case PlayStatus.Finished =>
        // Re-send current tokens to start a fresh playback session
        val tokens = viewState.now().tokens
        _tokensChannel.foreach(_.unsafe.offer(tokens))
      case _ => sendCommand(Command.Resume)

  def adjustSpeed(delta: Int)(using AllowUnsafe): Unit =
    val current = viewState.now().wpm
    val clamped = Math.max(100, Math.min(1000, current + delta))
    sendCommand(Command.SetSpeed(clamped))

  // ─────────────────────────────────────────────────────────────────────────
  // Derived Signals
  // ─────────────────────────────────────────────────────────────────────────

  val progressPercent: LaminarSignal[Double] =
    viewState.signal.map { s =>
      if s.tokens.length == 0 then 0.0
      else if s.tokens.length <= 1 then 100.0
      else (s.index.toDouble / (s.tokens.length - 1)) * 100.0
    }

  val focusContainerCls: LaminarSignal[String] =
    viewState.signal.map { s =>
      val base = "focus-container"
      val playing = if s.status == PlayStatus.Playing then " playing" else ""
      val expanded = if (s.status == PlayStatus.Paused || s.status == PlayStatus.Finished) && s.tokens.length > 0 then " expanded" else ""
      base + playing + expanded
    }

  val statusDotCls: LaminarSignal[String] =
    viewState.signal.map { s =>
      s.status match
        case PlayStatus.Playing  => "status-dot playing"
        case PlayStatus.Paused   => "status-dot paused"
        case PlayStatus.Finished => "status-dot paused"
        case PlayStatus.Stopped  => "status-dot"
    }

  val statusText: LaminarSignal[String] =
    viewState.signal.map(_.status.toString)

  val timeRemaining: LaminarSignal[String] =
    viewState.signal.map { s =>
      val remaining = s.tokens.length - s.index
      val minutes = remaining.toDouble / s.wpm
      if minutes < 1 then "< 1 min" else s"~${minutes.toInt} min"
    }

  val wordProgress: LaminarSignal[String] =
    viewState.signal.map { s =>
      val display = Math.min(s.index + 1, s.tokens.length)
      s"$display / ${s.tokens.length}"
    }

  // ─────────────────────────────────────────────────────────────────────────
  // Settings Persistence
  // ─────────────────────────────────────────────────────────────────────────

  def loadSettings(): Unit =
    import org.scalajs.dom.window.localStorage
    Option(localStorage.getItem("rsvp-centerMode"))
      .foreach(s => currentCenterMode.set(CenterMode.fromString(s)))
    // Load keybindings
    KeyAction.values.foreach { action =>
      Option(localStorage.getItem(s"rsvp-key-${action.toString}"))
        .foreach { key =>
          currentKeyBindings.update(_.withBinding(action, key))
        }
    }
    Option(localStorage.getItem("rsvp-contextSentences"))
      .flatMap(s => scala.util.Try(s.toInt).toOption)
      .filter(n => n >= 1 && n <= 4)
      .foreach(n => contextSentences.set(n))

  def saveSettings(): Unit =
    import org.scalajs.dom.window.localStorage
    localStorage.setItem("rsvp-centerMode", currentCenterMode.now().toString.toLowerCase)
    // Save keybindings
    val bindings = currentKeyBindings.now()
    KeyAction.values.foreach { action =>
      localStorage.setItem(s"rsvp-key-${action.toString}", bindings.keyFor(action))
    }
    localStorage.setItem("rsvp-contextSentences", contextSentences.now().toString)
