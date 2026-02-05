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
          val text = token.text
          val focus = token.focusIndex
          val centerMode = AppState.config.centerMode

          // Calculate offset for centering
          val offsetChars = centerMode match
            case CenterMode.ORP   => focus
            case CenterMode.First => 0
            case CenterMode.None  => -1 // Signal no centering

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
    )
  )

  def keyboardHandler(using AllowUnsafe): Modifier[HtmlElement] =
    onKeyDown --> { event =>
      event.key match
        case " " =>
          event.preventDefault()
          AppState.togglePlayPause()
        case "ArrowLeft"  => AppState.sendCommand(Command.Back(10))
        case "r" | "R"    => AppState.sendCommand(Command.RestartSentence)
        case "p" | "P"    =>
          AppState.sendCommand(Command.Pause)
          AppState.showParagraphView.update(!_)
        case "Escape"     => AppState.showParagraphView.set(false)
        case "ArrowUp"    => AppState.adjustSpeed(50)
        case "ArrowDown"  => AppState.adjustSpeed(-50)
        case _            => ()
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
