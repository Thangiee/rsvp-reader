package rsvpreader.ui

import com.raquo.laminar.api.L.*
import org.scalajs.dom
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
      s.status match
        case PlayStatus.Playing  => "⏸"
        case PlayStatus.Finished => "⟳"
        case _                   => "▶"
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
          if s.status == PlayStatus.Paused || s.status == PlayStatus.Finished then
            pauseTextView(s)
          else
            orpWordView(token, s.index)
    }
  )

  private def orpWordView(token: Token, index: Int): HtmlElement =
    val text = token.text
    val centerMode = AppState.currentCenterMode.now()
    val halfLen = text.length / 2.0
    val (focus, offset) = centerMode match
      case CenterMode.ORP   => (token.focusIndex, -halfLen + .5 + token.focusIndex)
      case CenterMode.First => (0, -halfLen + .5)
      case CenterMode.None  => (-1, 0.0)

    span(
      cls := "orp-word",
      styleAttr := s"--orp-offset: $offset",
      if focus >= 0 then
        div(
          span(cls := "orp-before", text.take(focus)),
          span(cls := "orp-focus", text.lift(focus).fold("")(_.toString)),
          span(cls := "orp-after", text.drop(focus + 1))
        )
      else
        span(cls := "orp-before", text)
    )

  private def pauseTextView(s: ViewState): HtmlElement =
    val tokens = s.tokens
    val currentIdx = s.index

    div(
      cls := "pause-text-view",
      // Render all tokens as flowing text with paragraph breaks
      (0 until tokens.length).map { i =>
        val token = tokens(i)
        val isCurrent = i == currentIdx
        val isParagraphBreak = i > 0 && tokens(i).paragraphIndex != tokens(i - 1).paragraphIndex

        val wordSpan = span(
          cls := (if isCurrent then "pause-word current" else "pause-word"),
          // Use data attribute to find the current word for scrolling
          (if isCurrent then Some(dataAttr("current") := "true") else None).toSeq,
          s"${token.text}${token.punctuation.text}",
          " "
        )

        if isParagraphBreak then
          Seq(div(cls := "pause-paragraph-break"), wordSpan)
        else
          Seq(wordSpan)
      }.flatten,
      // Auto-scroll to current word on mount
      onMountCallback { ctx =>
        val el = ctx.thisNode.ref.asInstanceOf[dom.html.Element]
        val currentWord = el.querySelector("[data-current='true']")
        if currentWord != null then
          val options = scalajs.js.Dynamic.literal(
            block = "center",
            behavior = "smooth"
          )
          currentWord.asInstanceOf[scalajs.js.Dynamic].scrollIntoView(options)
      }
    )

  def sentenceContext: HtmlElement = div(
    cls <-- AppState.viewState.signal.map { s =>
      val base = "sentence-context"
      if s.status == PlayStatus.Paused || s.status == PlayStatus.Finished then s"$base hidden" else base
    },
    children <-- AppState.viewState.signal.combineWith(AppState.contextSentences.signal).map { (s, numSentences) =>
      if s.tokens.isEmpty || s.status == PlayStatus.Paused || s.status == PlayStatus.Finished then Seq.empty
      else
        val currentSentenceIdx = s.currentToken.fold(-1)(_.sentenceIndex)
        if currentSentenceIdx < 0 then Seq.empty
        else
          // Compute paged window of sentence indices
          val page = currentSentenceIdx / numSentences
          val minSentence = page * numSentences
          val maxSentence = minSentence + numSentences - 1

          (0 until s.tokens.length)
            .filter { i =>
              val si = s.tokens(i).sentenceIndex
              si >= minSentence && si <= maxSentence
            }
            .map { i =>
              val token = s.tokens(i)
              val isCurrent = i == s.index
              val isCurrentSentence = token.sentenceIndex == currentSentenceIdx
              val cls0 = if isCurrent then "sentence-word current"
                          else if !isCurrentSentence then "sentence-word dim"
                          else "sentence-word"
              span(
                cls := cls0,
                s"${token.text}${token.punctuation.text}",
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

  def primaryControls(using AllowUnsafe): HtmlElement = div(
    cls := "primary-controls",
    button(
      cls := "control-btn medium",
      "¶",
      title := "Restart paragraph",
      onClick --> (_ => AppState.sendCommand(Command.RestartParagraph))
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
      span(cls := "icon", "⚙"),
      "Settings",
      onClick --> (_ => AppState.showSettingsModal.set(true))
    )
  )

  def keyboardHandler(using AllowUnsafe): Modifier[HtmlElement] =
    onKeyDown --> { event =>
      // Don't handle if capturing key, settings modal, or text input modal is open
      if AppState.capturingKeyFor.now().isEmpty && !AppState.showSettingsModal.now() && !AppState.showTextInputModal.now() then
        val bindings = AppState.currentKeyBindings.now()
        bindings.actionFor(event.key).foreach { action =>
          action match
            case KeyAction.PlayPause =>
              event.preventDefault()
              AppState.togglePlayPause()
            case KeyAction.RestartSentence =>
              AppState.sendCommand(Command.RestartSentence)
            case KeyAction.RestartParagraph =>
              AppState.sendCommand(Command.RestartParagraph)
            case KeyAction.SpeedUp =>
              AppState.adjustSpeed(50)
            case KeyAction.SpeedDown =>
              AppState.adjustSpeed(-50)
        }
    }

  def keyboardHints: HtmlElement = div(
    cls := "keyboard-hints",
    children <-- AppState.currentKeyBindings.signal.map { bindings =>
      Seq(
        keyHint(bindings.keyFor(KeyAction.PlayPause), "Play/Pause"),
        keyHint(bindings.keyFor(KeyAction.RestartSentence), "Sentence"),
        keyHint(bindings.keyFor(KeyAction.RestartParagraph), "Paragraph")
      )
    }
  )

  private def keyHint(key: String, labelText: String): HtmlElement = div(
    cls := "key-hint",
    span(cls := "key", if key == " " then "Space" else key),
    labelText
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
            AppState.saveSettings()
        }
      )
    )
  )
