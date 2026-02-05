package rsvpreader.ui

import com.raquo.laminar.api.L.*
import kyo.*
import rsvpreader.*

/** Reusable UI components for the RSVP reader. */
object Components:

  def playPauseButton(using AllowUnsafe): HtmlElement = button(
    cls <-- AppState.viewState.signal.map { s =>
      val base = "control-btn large"
      s.status match
        case PlayStatus.Playing => s"$base playing"
        case _                  => s"$base primary"
    },
    child.text <-- AppState.viewState.signal.map { s =>
      if s.status == PlayStatus.Playing then "⏸" else "▶"
    },
    onClick --> (_ => AppState.togglePlayPause())
  )

  def speedControls(using AllowUnsafe): HtmlElement = div(
    cls := "speed-control",
    button(
      cls := "control-btn small",
      "−",
      onClick --> (_ => AppState.adjustSpeed(-50))
    ),
    div(
      cls := "speed-display",
      child <-- AppState.viewState.signal.map(s => span(s"${s.wpm}")),
      " wpm"
    ),
    button(
      cls := "control-btn small",
      "+",
      onClick --> (_ => AppState.adjustSpeed(50))
    )
  )

  def focusWord: HtmlElement = div(
    cls := "focus-area",
    child <-- AppState.viewState.signal.map { s =>
      s.currentToken match
        case Absent => span(cls := "focus-placeholder", "READY TO READ")
        case Present(token) =>
          val isPaused = s.status == PlayStatus.Paused

          if isPaused then
            // Expanded view: show full sentence with current word highlighted
            div(
              cls := "expanded-sentence",
              (0 until s.tokens.length)
                .filter(i => s.tokens(i).sentenceIndex == token.sentenceIndex)
                .map { i =>
                  val t = s.tokens(i)
                  val isCurrent = i == s.index
                  span(
                    cls := (if isCurrent then "expanded-word current" else "expanded-word"),
                    t.text,
                    " "
                  )
                }
            )
          else
            // Normal view: single ORP word
            val text = token.text
            val focus = token.focusIndex
            val centerMode = AppState.config.centerMode
            val offsetChars = centerMode match
              case CenterMode.ORP   => focus
              case CenterMode.First => 0
              case CenterMode.None  => -1

            span(
              cls := "orp-word",
              styleAttr := (if offsetChars >= 0 then s"--orp-offset: $offsetChars" else ""),
              span(cls := "orp-before", text.take(focus)),
              span(cls := "orp-focus", text.lift(focus).fold("")(_.toString)),
              span(cls := "orp-after", text.drop(focus + 1))
            )
    }
  )

  def sentenceContext: HtmlElement = div(
    cls := "sentence-context",
    children <-- AppState.viewState.signal.map { s =>
      if s.tokens.isEmpty then Seq.empty
      else
        val currentSentenceIdx = s.currentToken.fold(-1)(_.sentenceIndex)
        (0 until s.tokens.length)
          .filter(i => s.tokens(i).sentenceIndex == currentSentenceIdx)
          .map { i =>
            val token = s.tokens(i)
            val isCurrent = i == s.index
            span(
              cls := (if isCurrent then "sentence-word current" else "sentence-word"),
              token.text,
              " "
            )
          }
    }
  )

  def progressBar: HtmlElement = div(
    cls := "progress-container",
    div(
      cls := "progress-bar",
      div(
        cls := "progress-fill",
        styleAttr <-- AppState.progressPercent.map(p => s"width: $p%")
      )
    ),
    div(
      cls := "progress-stats",
      span(child.text <-- AppState.wordProgress),
      span(child.text <-- AppState.timeRemaining)
    )
  )

  def paragraphContent: HtmlElement = div(
    cls := "paragraph-content",
    children <-- AppState.viewState.signal.map { s =>
      s.currentParagraphTokens.zipWithIndex.map { case (token, _) =>
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

  def primaryControls(using AllowUnsafe): HtmlElement = div(
    cls := "primary-controls",
    button(
      cls := "control-btn medium",
      "⏪",
      title := "Back 10 words",
      onClick --> (_ => AppState.sendCommand(Command.Back(10)))
    ),
    button(
      cls := "control-btn medium",
      "↩",
      title := "Restart sentence",
      onClick --> (_ => AppState.sendCommand(Command.RestartSentence))
    ),
    playPauseButton,
    speedControls
  )

  def secondaryControls(using AllowUnsafe): HtmlElement = div(
    cls := "secondary-controls",
    button(
      cls := "control-chip",
      span(cls := "icon", "📝"),
      "Load Text",
      onClick --> (_ => AppState.showTextInputModal.set(true))
    ),
    button(
      cls := "control-chip",
      span(cls := "icon", "¶"),
      "Show Paragraph",
      onClick --> { _ =>
        AppState.sendCommand(Command.Pause)
        AppState.showParagraphView.set(true)
      }
    ),
    button(
      cls := "control-chip",
      span(cls := "icon", "⏹"),
      "Reset",
      onClick --> (_ => AppState.sendCommand(Command.Stop))
    ),
    button(
      cls := "control-chip",
      span(cls := "icon", "⚙"),
      "Settings",
      onClick --> (_ => AppState.showSettingsModal.set(true))
    )
  )

  def keyboardHandler(using AllowUnsafe): Modifier[HtmlElement] =
    onKeyDown --> { event =>
      // Don't handle if capturing key or settings modal is open
      if AppState.capturingKeyFor.now().isEmpty && !AppState.showSettingsModal.now() then
        val bindings = AppState.currentKeyBindings.now()
        bindings.actionFor(event.key).foreach { action =>
          action match
            case KeyAction.PlayPause =>
              event.preventDefault()
              AppState.togglePlayPause()
            case KeyAction.Back =>
              AppState.sendCommand(Command.Back(10))
            case KeyAction.RestartSentence =>
              AppState.sendCommand(Command.RestartSentence)
            case KeyAction.ShowParagraph =>
              AppState.sendCommand(Command.Pause)
              AppState.showParagraphView.update(!_)
            case KeyAction.CloseParagraph =>
              AppState.showParagraphView.set(false)
            case KeyAction.SpeedUp =>
              AppState.adjustSpeed(50)
            case KeyAction.SpeedDown =>
              AppState.adjustSpeed(-50)
            case KeyAction.Reset =>
              AppState.sendCommand(Command.Stop)
        }
    }

  def keyboardHints: HtmlElement = div(
    cls := "keyboard-hints",
    div(cls := "key-hint", span(cls := "key", "Space"), "Play/Pause"),
    div(cls := "key-hint", span(cls := "key", "←"), "Back"),
    div(cls := "key-hint", span(cls := "key", "R"), "Restart"),
    div(cls := "key-hint", span(cls := "key", "P"), "Paragraph")
  )

  def textInputModal(onStart: String => Unit)(using AllowUnsafe): HtmlElement = div(
    cls <-- AppState.showTextInputModal.signal.map { show =>
      if show then "text-input-modal visible" else "text-input-modal"
    },
    div(
      cls := "text-input-content",
      h2("Load Text"),
      textArea(
        cls := "text-input-area",
        placeholder := "Paste or type your text here...",
        controlled(
          value <-- AppState.inputText.signal,
          onInput.mapToValue --> AppState.inputText.writer
        )
      ),
      button(
        cls := "start-reading-btn",
        "Start Reading",
        onClick --> { _ =>
          val text = AppState.inputText.now()
          if text.trim.nonEmpty then
            onStart(text)
            AppState.showTextInputModal.set(false)
        }
      )
    )
  )
