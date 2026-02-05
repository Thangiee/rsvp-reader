package rsvpreader

import com.raquo.laminar.api.L.{Var as LaminarVar, *}
import org.scalajs.dom
import kyo.*

object Main extends KyoApp:
  import AllowUnsafe.embrace.danger

  // Reactive state for UI
  private val stateVar = LaminarVar(ViewState(
    tokens = Span.empty,
    index = 0,
    status = PlayStatus.Stopped,
    wpm = 300
  ))

  // Paragraph view visibility
  private val showParagraphView = LaminarVar(false)

  private val config = RsvpConfig()

  // Channel for commands - will be initialized in run block
  private var commandChannel: Option[Channel[Command]] = None

  // Sample text for testing
  private val sampleText = """The quick brown fox jumps over the lazy dog. This is a test sentence with some longer words like extraordinary and magnificent.

Second paragraph begins here. It contains multiple sentences. Each sentence should be trackable. Reading fast improves comprehension when done correctly.

A third paragraph demonstrates the paragraph pause feature. Notice how the reader handles punctuation, commas, and periods differently. The ORP alignment keeps your eye focused."""

  // Helper to send commands
  private def sendCommand(cmd: Command): Unit =
    commandChannel.foreach { ch =>
      // Use offer which returns Boolean < (IO & Abort[Closed])
      // Discard the result as we just want fire-and-forget
      ch.unsafe.offer(cmd)
    }

  // Calculate progress percentage
  private def progressPercent(state: ViewState): Double =
    if state.tokens.length == 0 then 0.0
    else (state.index.toDouble / state.tokens.length.toDouble) * 100.0

  // Play/Pause button rendering
  private def playPauseButton = button(
    cls <-- stateVar.signal.map { state =>
      val base = "control-btn large"
      state.status match
        case PlayStatus.Playing => s"$base playing"
        case PlayStatus.Paused  => s"$base primary"
        case PlayStatus.Stopped => s"$base primary"
    },
    child.text <-- stateVar.signal.map { state =>
      state.status match
        case PlayStatus.Playing => "⏸"
        case _                  => "▶"
    },
    onClick --> { _ =>
      val state = stateVar.now()
      state.status match
        case PlayStatus.Playing => sendCommand(Command.Pause)
        case _                  => sendCommand(Command.Resume)
    }
  )

  // Speed control buttons
  private def speedControls = div(
    cls := "speed-control",
    button(
      cls := "control-btn small",
      "−",
      onClick --> { _ =>
        val current = stateVar.now().wpm
        val newWpm = Math.max(100, current - 50)
        sendCommand(Command.SetSpeed(newWpm))
      }
    ),
    div(
      cls := "speed-display",
      child <-- stateVar.signal.map(s => span(s"${s.wpm}")),
      " wpm"
    ),
    button(
      cls := "control-btn small",
      "+",
      onClick --> { _ =>
        val current = stateVar.now().wpm
        val newWpm = Math.min(1000, current + 50)
        sendCommand(Command.SetSpeed(newWpm))
      }
    )
  )

  // Focus container class based on status
  private def focusContainerCls = stateVar.signal.map { state =>
    state.status match
      case PlayStatus.Playing => "focus-container playing"
      case _                  => "focus-container"
  }

  // Status dot class
  private def statusDotCls = stateVar.signal.map { state =>
    state.status match
      case PlayStatus.Playing => "status-dot playing"
      case PlayStatus.Paused  => "status-dot paused"
      case PlayStatus.Stopped => "status-dot"
  }

  // Status text
  private def statusText = stateVar.signal.map { state =>
    state.status match
      case PlayStatus.Playing => "Playing"
      case PlayStatus.Paused  => "Paused"
      case PlayStatus.Stopped => "Stopped"
  }

  // Paragraph view content
  private def paragraphContent = div(
    cls := "paragraph-content",
    children <-- stateVar.signal.map { state =>
      state.currentParagraphTokens.zipWithIndex.map { case (token, idx) =>
        val isCurrentWord = state.currentToken.fold(false)(t =>
          t.text == token.text && t.sentenceIndex == token.sentenceIndex
        )
        span(
          cls := (if isCurrentWord then "current-word" else ""),
          token.text,
          " "
        )
      }
    }
  )

  private val app = div(
    // Header
    div(
      cls := "header",
      div(
        cls := "logo",
        "RSVP ",
        span("Reader")
      ),
      div(
        cls := "status-indicator",
        div(cls <-- statusDotCls),
        child.text <-- statusText
      )
    ),

    // Main reading theater
    div(
      cls := "reading-theater",

      // Focus word display with ORP alignment
      div(
        cls <-- focusContainerCls,
        div(cls := "orp-guides"),
        div(
          cls := "focus-area",
          child <-- stateVar.signal.map { state =>
            state.currentToken match
              case Absent => span(cls := "focus-placeholder", "READY TO READ")
              case Present(token) =>
                val text = token.text
                val focus = token.focusIndex
                span(
                  cls := "orp-word",
                  span(cls := "orp-before", text.take(focus)),
                  span(cls := "orp-focus", text.lift(focus).map(_.toString).getOrElse("")),
                  span(cls := "orp-after", text.drop(focus + 1))
                )
          }
        )
      ),

      // Trail display
      div(
        cls := "trail-area",
        children <-- stateVar.signal.map { state =>
          state.trailTokens(config.trailWordCount).map { token =>
            span(cls := "trail-word", token.text)
          }
        }
      ),

      // Progress bar
      div(
        cls := "progress-container",
        div(
          cls := "progress-bar",
          div(
            cls := "progress-fill",
            styleAttr <-- stateVar.signal.map { state =>
              s"width: ${progressPercent(state)}%"
            }
          )
        ),
        div(
          cls := "progress-stats",
          span(
            child.text <-- stateVar.signal.map { state =>
              s"${state.index + 1} / ${state.tokens.length}"
            }
          ),
          span(
            child.text <-- stateVar.signal.map { state =>
              val remaining = state.tokens.length - state.index
              val minutes = remaining.toDouble / state.wpm
              if minutes < 1 then s"< 1 min"
              else s"~${minutes.toInt} min"
            }
          )
        )
      )
    ),

    // Controls dock
    div(
      cls := "controls-dock",

      // Primary controls row
      div(
        cls := "primary-controls",

        // Back 10 words
        button(
          cls := "control-btn medium",
          "⏪",
          title := "Back 10 words",
          onClick --> { _ => sendCommand(Command.Back(10)) }
        ),

        // Restart sentence
        button(
          cls := "control-btn medium",
          "↩",
          title := "Restart sentence",
          onClick --> { _ => sendCommand(Command.RestartSentence) }
        ),

        // Play/Pause (main)
        playPauseButton,

        // Speed controls
        speedControls
      ),

      // Secondary controls row
      div(
        cls := "secondary-controls",

        // Show paragraph
        button(
          cls := "control-chip",
          span(cls := "icon", "¶"),
          "Show Paragraph",
          onClick --> { _ =>
            sendCommand(Command.Pause)
            showParagraphView.set(true)
          }
        ),

        // Stop/Reset
        button(
          cls := "control-chip",
          span(cls := "icon", "⏹"),
          "Stop",
          onClick --> { _ => sendCommand(Command.Stop) }
        )
      )
    ),

    // Paragraph view modal
    div(
      cls <-- showParagraphView.signal.map { show =>
        if show then "paragraph-view visible" else "paragraph-view"
      },
      button(
        cls := "close-paragraph",
        "×",
        onClick --> { _ => showParagraphView.set(false) }
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

    // Keyboard event handler
    onKeyDown --> { event =>
      event.key match
        case " " =>
          event.preventDefault()
          val state = stateVar.now()
          if state.status == PlayStatus.Playing then sendCommand(Command.Pause)
          else sendCommand(Command.Resume)
        case "ArrowLeft" =>
          sendCommand(Command.Back(10))
        case "r" | "R" =>
          sendCommand(Command.RestartSentence)
        case "p" | "P" =>
          sendCommand(Command.Pause)
          showParagraphView.update(!_)
        case "Escape" =>
          showParagraphView.set(false)
        case "ArrowUp" =>
          val current = stateVar.now().wpm
          sendCommand(Command.SetSpeed(Math.min(1000, current + 50)))
        case "ArrowDown" =>
          val current = stateVar.now().wpm
          sendCommand(Command.SetSpeed(Math.max(100, current - 50)))
        case _ => ()
    },

    // Focus the app for keyboard events
    tabIndex := 0,
    onMountCallback { ctx =>
      ctx.thisNode.ref.asInstanceOf[dom.html.Element].focus()
    }
  )

  renderOnDomContentLoaded(dom.document.getElementById("app"), app)

  run {
    direct {
      val ch = Channel.init[Command](1).now
      commandChannel = Some(ch)
      val tokens = Tokenizer.tokenize(sampleText)
      val engine = PlaybackEngine(ch, config, state => stateVar.set(state))

      // Start in paused state, ready to go
      engine.run(tokens).now
    }
  }
