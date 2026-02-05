package rsvpreader

import com.raquo.laminar.api.L.{Var as LaminarVar, Signal as LaminarSignal, *}
import org.scalajs.dom
import kyo.*

object Main extends KyoApp:
  import AllowUnsafe.embrace.danger

  // ─────────────────────────────────────────────────────────────────────────
  // State
  // ─────────────────────────────────────────────────────────────────────────

  private val stateVar = LaminarVar(ViewState(
    tokens = Span.empty,
    index = 0,
    status = PlayStatus.Stopped,
    wpm = 300
  ))

  private val showParagraphView = LaminarVar(false)
  private val config = RsvpConfig()

  // Channel reference - initialized in run block
  private var channel: Maybe[Channel[Command]] = Absent

  // ─────────────────────────────────────────────────────────────────────────
  // Sample Text
  // ─────────────────────────────────────────────────────────────────────────

  private val sampleText = """The quick brown fox jumps over the lazy dog. This is a test sentence with some longer words like extraordinary and magnificent.

Second paragraph begins here. It contains multiple sentences. Each sentence should be trackable. Reading fast improves comprehension when done correctly.

A third paragraph demonstrates the paragraph pause feature. Notice how the reader handles punctuation, commas, and periods differently. The ORP alignment keeps your eye focused."""

  // ─────────────────────────────────────────────────────────────────────────
  // Command Dispatch
  // ─────────────────────────────────────────────────────────────────────────

  private def sendCommand(cmd: Command): Unit =
    channel.foreach(_.unsafe.offer(cmd))

  private def togglePlayPause(): Unit =
    stateVar.now().status match
      case PlayStatus.Playing => sendCommand(Command.Pause)
      case _                  => sendCommand(Command.Resume)

  private def adjustSpeed(delta: Int): Unit =
    val current = stateVar.now().wpm
    val clamped = Math.max(100, Math.min(1000, current + delta))
    sendCommand(Command.SetSpeed(clamped))

  // ─────────────────────────────────────────────────────────────────────────
  // Derived Signals
  // ─────────────────────────────────────────────────────────────────────────

  private val progressPercent: LaminarSignal[Double] =
    stateVar.signal.map { s =>
      if s.tokens.length == 0 then 0.0
      else (s.index.toDouble / s.tokens.length) * 100.0
    }

  private val focusContainerCls: LaminarSignal[String] =
    stateVar.signal.map { s =>
      if s.status == PlayStatus.Playing then "focus-container playing"
      else "focus-container"
    }

  private val statusDotCls: LaminarSignal[String] =
    stateVar.signal.map { s =>
      s.status match
        case PlayStatus.Playing => "status-dot playing"
        case PlayStatus.Paused  => "status-dot paused"
        case PlayStatus.Stopped => "status-dot"
    }

  private val statusText: LaminarSignal[String] =
    stateVar.signal.map(_.status.toString)

  private val timeRemaining: LaminarSignal[String] =
    stateVar.signal.map { s =>
      val remaining = s.tokens.length - s.index
      val minutes = remaining.toDouble / s.wpm
      if minutes < 1 then "< 1 min" else s"~${minutes.toInt} min"
    }

  private val wordProgress: LaminarSignal[String] =
    stateVar.signal.map(s => s"${s.index + 1} / ${s.tokens.length}")

  // ─────────────────────────────────────────────────────────────────────────
  // UI Components
  // ─────────────────────────────────────────────────────────────────────────

  private def playPauseButton: HtmlElement = button(
    cls <-- stateVar.signal.map { s =>
      val base = "control-btn large"
      s.status match
        case PlayStatus.Playing => s"$base playing"
        case _                  => s"$base primary"
    },
    child.text <-- stateVar.signal.map { s =>
      if s.status == PlayStatus.Playing then "⏸" else "▶"
    },
    onClick --> (_ => togglePlayPause())
  )

  private def speedControls: HtmlElement = div(
    cls := "speed-control",
    button(
      cls := "control-btn small",
      "−",
      onClick --> (_ => adjustSpeed(-50))
    ),
    div(
      cls := "speed-display",
      child <-- stateVar.signal.map(s => span(s"${s.wpm}")),
      " wpm"
    ),
    button(
      cls := "control-btn small",
      "+",
      onClick --> (_ => adjustSpeed(50))
    )
  )

  private def focusWord: HtmlElement = div(
    cls := "focus-area",
    child <-- stateVar.signal.map { s =>
      s.currentToken match
        case Absent => span(cls := "focus-placeholder", "READY TO READ")
        case Present(token) =>
          val text = token.text
          val focus = token.focusIndex
          span(
            cls := "orp-word",
            span(cls := "orp-before", text.take(focus)),
            span(cls := "orp-focus", text.lift(focus).fold("")(_.toString)),
            span(cls := "orp-after", text.drop(focus + 1))
          )
    }
  )

  private def trailArea: HtmlElement = div(
    cls := "trail-area",
    children <-- stateVar.signal.map { s =>
      s.trailTokens(config.trailWordCount).map(t => span(cls := "trail-word", t.text))
    }
  )

  private def progressBar: HtmlElement = div(
    cls := "progress-container",
    div(
      cls := "progress-bar",
      div(
        cls := "progress-fill",
        styleAttr <-- progressPercent.map(p => s"width: ${p}%")
      )
    ),
    div(
      cls := "progress-stats",
      span(child.text <-- wordProgress),
      span(child.text <-- timeRemaining)
    )
  )

  private def paragraphContent: HtmlElement = div(
    cls := "paragraph-content",
    children <-- stateVar.signal.map { s =>
      val currentIdx = s.index
      s.currentParagraphTokens.zipWithIndex.map { case (token, idx) =>
        val isCurrent = s.currentToken.fold(false) { t =>
          t.text == token.text && t.sentenceIndex == token.sentenceIndex
        }
        span(
          cls := (if isCurrent then "current-word" else ""),
          token.text,
          " "
        )
      }
    }
  )

  private def primaryControls: HtmlElement = div(
    cls := "primary-controls",
    button(
      cls := "control-btn medium",
      "⏪",
      title := "Back 10 words",
      onClick --> (_ => sendCommand(Command.Back(10)))
    ),
    button(
      cls := "control-btn medium",
      "↩",
      title := "Restart sentence",
      onClick --> (_ => sendCommand(Command.RestartSentence))
    ),
    playPauseButton,
    speedControls
  )

  private def secondaryControls: HtmlElement = div(
    cls := "secondary-controls",
    button(
      cls := "control-chip",
      span(cls := "icon", "¶"),
      "Show Paragraph",
      onClick --> { _ =>
        sendCommand(Command.Pause)
        showParagraphView.set(true)
      }
    ),
    button(
      cls := "control-chip",
      span(cls := "icon", "⏹"),
      "Stop",
      onClick --> (_ => sendCommand(Command.Stop))
    )
  )

  private def keyboardHandler: Modifier[HtmlElement] =
    onKeyDown --> { event =>
      event.key match
        case " " =>
          event.preventDefault()
          togglePlayPause()
        case "ArrowLeft"  => sendCommand(Command.Back(10))
        case "r" | "R"    => sendCommand(Command.RestartSentence)
        case "p" | "P"    =>
          sendCommand(Command.Pause)
          showParagraphView.update(!_)
        case "Escape"     => showParagraphView.set(false)
        case "ArrowUp"    => adjustSpeed(50)
        case "ArrowDown"  => adjustSpeed(-50)
        case _            => ()
    }

  // ─────────────────────────────────────────────────────────────────────────
  // App Layout
  // ─────────────────────────────────────────────────────────────────────────

  private val app: HtmlElement = div(
    // Header
    div(
      cls := "header",
      div(cls := "logo", "RSVP ", span("Reader")),
      div(
        cls := "status-indicator",
        div(cls <-- statusDotCls),
        child.text <-- statusText
      )
    ),

    // Reading theater
    div(
      cls := "reading-theater",
      div(
        cls <-- focusContainerCls,
        div(cls := "orp-guides"),
        focusWord
      ),
      trailArea,
      progressBar
    ),

    // Controls dock
    div(
      cls := "controls-dock",
      primaryControls,
      secondaryControls
    ),

    // Paragraph view modal
    div(
      cls <-- showParagraphView.signal.map { show =>
        if show then "paragraph-view visible" else "paragraph-view"
      },
      button(
        cls := "close-paragraph",
        "×",
        onClick --> (_ => showParagraphView.set(false))
      ),
      paragraphContent
    ),

    // Keyboard hints
    div(
      cls := "keyboard-hints",
      div(cls := "key-hint", span(cls := "key", "Space"), "Play/Pause"),
      div(cls := "key-hint", span(cls := "key", "←"), "Back"),
      div(cls := "key-hint", span(cls := "key", "R"), "Restart"),
      div(cls := "key-hint", span(cls := "key", "P"), "Paragraph")
    ),

    // Keyboard support
    keyboardHandler,
    tabIndex := 0,
    onMountCallback(ctx => ctx.thisNode.ref.asInstanceOf[dom.html.Element].focus())
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Entry Point
  // ─────────────────────────────────────────────────────────────────────────

  renderOnDomContentLoaded(dom.document.getElementById("app"), app)

  run {
    direct {
      val ch = Channel.init[Command](1).now
      channel = Maybe(ch)

      val tokens = Tokenizer.tokenize(sampleText)
      val engine = PlaybackEngine(ch, config, state => stateVar.set(state))

      engine.run(tokens).now
    }
  }
