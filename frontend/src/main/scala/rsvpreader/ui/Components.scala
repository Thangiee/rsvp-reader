package rsvpreader.ui

import com.raquo.laminar.api.L.{Var as LaminarVar, Signal as LaminarSignal, *}
import org.scalajs.dom
import kyo.*
import rsvpreader.token.*
import rsvpreader.playback.*
import rsvpreader.config.*
import rsvpreader.state.*
import rsvpreader.viewmodel.*

/** Reusable UI components for the RSVP reader. */
object Components:

  def playPauseButton(domain: DomainContext): HtmlElement = button(
    cls <-- domain.model.map { m =>
      val base = "control-btn large"
      m.viewState.status match
        case PlayStatus.Playing => s"$base playing"
        case _                  => s"$base primary"
    },
    child.text <-- domain.model.map { m =>
      m.viewState.status match
        case PlayStatus.Playing  => "\u23f8"
        case PlayStatus.Finished => "\u27f3"
        case _                   => "\u25b6"
    },
    onClick --> (_ => domain.togglePlayPause())
  )

  def speedControls(domain: DomainContext): HtmlElement = div(
    cls := "speed-control",
    button(
      cls := "control-btn small",
      "\u2212",
      onClick --> (_ => domain.adjustSpeed(-50))
    ),
    div(
      cls := "speed-display",
      child <-- domain.model.map(m => span(s"${m.viewState.wpm}")),
      " wpm"
    ),
    button(
      cls := "control-btn small",
      "+",
      onClick --> (_ => domain.adjustSpeed(50))
    )
  )

  def focusWord(domain: DomainContext): HtmlElement = div(
    cls := "focus-area",
    child <-- domain.model.map { m =>
      val s = m.viewState
      s.currentToken match
        case Absent => span(cls := "focus-placeholder", "READY TO READ")
        case Present(token) =>
          if s.status == PlayStatus.Paused || s.status == PlayStatus.Finished then
            pauseTextView(s)
          else
            orpWordView(OrpLayout.compute(token, m.centerMode))
    }
  )

  private def orpWordView(layout: OrpLayout): HtmlElement =
    span(
      cls := "orp-word",
      styleAttr := s"--orp-offset: ${layout.offset}",
      if layout.focus.nonEmpty then
        div(
          span(cls := "orp-before", layout.before),
          span(cls := "orp-focus", layout.focus),
          span(cls := "orp-after", layout.after)
        )
      else
        span(cls := "orp-before", layout.before)
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
          (if isCurrent then Maybe(dataAttr("current") := "true") else Absent).toOption.toSeq,
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

  def sentenceContext(domain: DomainContext): HtmlElement = div(
    cls <-- domain.model.map { m =>
      val base = "sentence-context"
      if m.viewState.status == PlayStatus.Paused || m.viewState.status == PlayStatus.Finished then s"$base hidden" else base
    },
    children <-- domain.model.map { m =>
      val s = m.viewState
      if s.tokens.isEmpty || s.status == PlayStatus.Paused || s.status == PlayStatus.Finished then Seq.empty
      else
        SentenceWindow.compute(s.tokens, s.index, m.contextSentences).map { wd =>
          span(
            cls := wd.cssClass,
            wd.text,
            " "
          )
        }
    }
  )

  def progressBar(domain: DomainContext): HtmlElement = div(
    cls := "progress-container",
    div(
      cls := "progress-bar",
      div(
        cls := "progress-fill",
        styleAttr <-- domain.model.map(m => s"width: ${DomainModel.progressPercent(m)}%")
      )
    ),
    div(
      cls := "progress-stats",
      span(child.text <-- domain.model.map(DomainModel.wordProgress)),
      span(child.text <-- domain.model.map(DomainModel.timeRemaining))
    )
  )

  def primaryControls(domain: DomainContext): HtmlElement = div(
    cls := "primary-controls",
    button(
      cls := "control-btn medium",
      "\u00b6",
      title := "Restart paragraph",
      onClick --> (_ => domain.sendCommand(Command.RestartParagraph))
    ),
    button(
      cls := "control-btn medium",
      "\u21a9",
      title := "Restart sentence",
      onClick --> (_ => domain.sendCommand(Command.RestartSentence))
    ),
    playPauseButton(domain),
    speedControls(domain)
  )

  def secondaryControls(domain: DomainContext, ui: UiState): HtmlElement = div(
    cls := "secondary-controls",
    button(
      cls := "control-chip",
      span(cls := "icon", "\ud83d\udcdd"),
      "Load Text",
      onClick --> (_ => ui.showTextInputModal.set(true))
    ),
    button(
      cls := "control-chip",
      span(cls := "icon", "\u2699"),
      "Settings",
      onClick --> (_ => ui.showSettingsModal.set(true))
    )
  )

  def keyboardHandler(domain: DomainContext, ui: UiState): Modifier[HtmlElement] =
    onKeyDown.compose(_.withCurrentValueOf(domain.model)) --> { (event, model) =>
      ui.capturingKeyFor.now() match
        case Present(action) =>
          // Capture the key for remapping
          event.preventDefault()
          domain.dispatch(Action.UpdateKeyBinding(action, event.key))
          ui.capturingKeyFor.set(Absent)
        case Absent =>
          val modalsOpen = ui.showSettingsModal.now() || ui.showTextInputModal.now()
          val capturing = ui.capturingKeyFor.now().isDefined
          val bindings = model.keyBindings
          KeyDispatch.resolve(event.key, bindings, modalsOpen, capturing).foreach { action =>
            action match
              case KeyAction.PlayPause =>
                event.preventDefault()
                domain.togglePlayPause()
              case KeyAction.RestartSentence =>
                domain.sendCommand(Command.RestartSentence)
              case KeyAction.RestartParagraph =>
                domain.sendCommand(Command.RestartParagraph)
              case KeyAction.SpeedUp =>
                domain.adjustSpeed(50)
              case KeyAction.SpeedDown =>
                domain.adjustSpeed(-50)
          }
    }

  def keyboardHints(domain: DomainContext): HtmlElement = div(
    cls := "keyboard-hints",
    children <-- domain.model.map { m =>
      val bindings = m.keyBindings
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

  def textInputModal(domain: DomainContext, ui: UiState, onStart: String => Unit): HtmlElement = div(
    cls <-- ui.showTextInputModal.signal.map { show =>
      if show then "text-input-modal visible" else "text-input-modal"
    },
    div(
      cls := "text-input-content",
      h2("Load Text"),
      textArea(
        cls := "text-input-area",
        placeholder := "Paste or type your text here...",
        controlled(
          value <-- ui.inputText.signal,
          onInput.mapToValue --> ui.inputText.writer
        ),
        onInput --> (_ => ui.loadError.set(Absent))
      ),
      child.maybe <-- ui.loadError.signal.map {
        case Present(msg) => Some(div(
          cls := "load-error",
          msg
        ))
        case Absent => None
      },
      button(
        cls := "start-reading-btn",
        "Start Reading",
        onClick --> { _ =>
          val text = ui.inputText.now()
          if text.trim.nonEmpty then
            onStart(text)
            if ui.loadError.now().isEmpty then
              ui.showTextInputModal.set(false)
        }
      )
    )
  )
