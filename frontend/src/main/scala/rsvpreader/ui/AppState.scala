package rsvpreader.ui

import com.raquo.laminar.api.L.{Var as LaminarVar, Signal as LaminarSignal, *}
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

  val config: RsvpConfig = RsvpConfig()

  // Channel reference - set by Main during initialization
  private var _channel: Maybe[Channel[Command]] = Absent

  def setChannel(ch: Channel[Command]): Unit =
    _channel = Maybe(ch)

  // ─────────────────────────────────────────────────────────────────────────
  // Command Dispatch
  // ─────────────────────────────────────────────────────────────────────────

  def sendCommand(cmd: Command)(using AllowUnsafe): Unit =
    _channel.foreach(_.unsafe.offer(cmd))

  def togglePlayPause()(using AllowUnsafe): Unit =
    viewState.now().status match
      case PlayStatus.Playing => sendCommand(Command.Pause)
      case _                  => sendCommand(Command.Resume)

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
      else (s.index.toDouble / s.tokens.length) * 100.0
    }

  val focusContainerCls: LaminarSignal[String] =
    viewState.signal.map { s =>
      val base = "focus-container"
      val playing = if s.status == PlayStatus.Playing then " playing" else ""
      val expanded = if s.status == PlayStatus.Paused && s.tokens.length > 0 then " expanded" else ""
      base + playing + expanded
    }

  val statusDotCls: LaminarSignal[String] =
    viewState.signal.map { s =>
      s.status match
        case PlayStatus.Playing => "status-dot playing"
        case PlayStatus.Paused  => "status-dot paused"
        case PlayStatus.Stopped => "status-dot"
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
    viewState.signal.map(s => s"${s.index + 1} / ${s.tokens.length}")

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

  def saveSettings(): Unit =
    import org.scalajs.dom.window.localStorage
    localStorage.setItem("rsvp-centerMode", currentCenterMode.now().toString.toLowerCase)
    // Save keybindings
    val bindings = currentKeyBindings.now()
    KeyAction.values.foreach { action =>
      localStorage.setItem(s"rsvp-key-${action.toString}", bindings.keyFor(action))
    }
