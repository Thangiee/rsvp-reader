package rsvpreader.ui

import com.raquo.laminar.api.L.{Var as LaminarVar, Signal as LaminarSignal, *}
import org.scalajs.dom
import kyo.*
import rsvpreader.token.*
import rsvpreader.playback.*
import rsvpreader.config.*
import rsvpreader.state.*
import rsvpreader.viewmodel.*
import rsvpreader.book.*
import rsvpreader.EpubParser

/** Reusable UI components for the RSVP reader. */
object Components:

  def playPauseButton(domain: AppContext): HtmlElement = button(
    cls <-- domain.state.map { m =>
      val base = "control-btn large"
      m.viewState.status match
        case PlayStatus.Playing => s"$base playing"
        case _                  => s"$base primary"
    },
    child.text <-- domain.state.map { m =>
      m.viewState.status match
        case PlayStatus.Playing  => "\u23f8"
        case PlayStatus.Finished => "\u27f3"
        case _                   => "\u25b6"
    },
    onClick --> (_ => domain.togglePlayPause())
  )

  def speedControls(domain: AppContext): HtmlElement = div(
    cls := "speed-control",
    button(
      cls := "control-btn small",
      "\u2212",
      onClick --> (_ => domain.adjustSpeed(-50))
    ),
    div(
      cls := "speed-display",
      child <-- domain.state.map(m => span(s"${m.viewState.wpm}")),
      " wpm"
    ),
    button(
      cls := "control-btn small",
      "+",
      onClick --> (_ => domain.adjustSpeed(50))
    )
  )

  def focusWord(domain: AppContext): HtmlElement =
    var pauseViewOpened = false
    div(
      cls := "focus-area",
      child <-- domain.state.map { m =>
        val s = m.viewState
        s.currentToken match
          case Absent =>
            pauseViewOpened = false
            span(cls := "focus-placeholder", "READY TO READ")
          case Present(token) =>
            if s.status == PlayStatus.Finished && AppState.hasNextChapter(m) then
              pauseViewOpened = false
              val nextIdx = m.chapterIndex + 1
              val nextTitle = m.book.chapters(nextIdx).title match
                case t if t.nonEmpty => t
                case _ => s"Chapter ${nextIdx + 1}"
              div(
                cls := "next-chapter-prompt",
                div(cls := "next-chapter-label", "Chapter complete"),
                button(
                  cls := "next-chapter-btn",
                  s"Next: $nextTitle \u2192",
                  onClick --> (_ => domain.dispatch(Action.LoadChapter(nextIdx)))
                )
              )
            else if s.status == PlayStatus.Paused || s.status == PlayStatus.Finished then
              val scrollOnMount = !pauseViewOpened
              pauseViewOpened = true
              pauseTextView(s, domain.sendCommand, scrollOnMount)
            else
              pauseViewOpened = false
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

  private def pauseTextView(s: ViewState, sendCommand: Command => Unit, scrollOnMount: Boolean): HtmlElement =
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
          " ",
          onClick --> (_ => sendCommand(Command.JumpToIndex(i)))
        )

        if isParagraphBreak then
          Seq(div(cls := "pause-paragraph-break"), wordSpan)
        else
          Seq(wordSpan)
      }.flatten,
      // Scroll to current word: smooth on first open, instant on re-renders
      onMountCallback { ctx =>
        val el = ctx.thisNode.ref.asInstanceOf[dom.html.Element]
        val currentWord = el.querySelector("[data-current='true']")
        if currentWord != null then
          val options = scalajs.js.Dynamic.literal(
            block = "center",
            behavior = if scrollOnMount then "smooth" else "instant"
          )
          currentWord.asInstanceOf[scalajs.js.Dynamic].scrollIntoView(options)
      }
    )

  def sentenceContext(domain: AppContext): HtmlElement = div(
    cls <-- domain.state.map { m =>
      val base = "sentence-context"
      if m.viewState.status == PlayStatus.Paused || m.viewState.status == PlayStatus.Finished then s"$base hidden" else base
    },
    children <-- domain.state.map { m =>
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

  def progressBar(domain: AppContext): HtmlElement = div(
    cls := "progress-container",
    div(
      cls := "progress-bar",
      div(
        cls := "progress-fill",
        styleAttr <-- domain.state.map(m => s"width: ${AppState.progressPercent(m)}%")
      )
    ),
    div(
      cls := "progress-stats",
      span(child.text <-- domain.state.map(AppState.wordProgress)),
      span(child.text <-- domain.state.map(AppState.timeRemaining))
    )
  )

  def primaryControls(domain: AppContext): HtmlElement = div(
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

  def chapterDropdown(domain: AppContext): HtmlElement = div(
    cls := "chapter-nav",
    display <-- domain.state.map { m =>
      if m.book.chapters.length > 1 then "flex" else "none"
    },
    select(
      cls := "chapter-select",
      children <-- domain.state.map { m =>
        (0 until m.book.chapters.length).map { i =>
          val ch = m.book.chapters(i)
          val label = if ch.title.nonEmpty then ch.title else s"Chapter ${i + 1}"
          option(value := i.toString, label, selected := (i == m.chapterIndex))
        }
      },
      onChange.mapToValue --> { value =>
        domain.dispatch(Action.LoadChapter(value.toInt))
      }
    )
  )

  def secondaryControls(domain: AppContext, ui: UiState): HtmlElement = div(
    cls := "secondary-controls",
    button(
      cls := "control-chip",
      span(cls := "icon", "\ud83d\udcdd"),
      "Load Text",
      onClick --> (_ => ui.showTextInputModal.set(true))
    ),
    // Hidden file input for EPUB
    input(
      typ := "file",
      cls := "epub-file-input",
      accept := ".epub",
      display := "none",
      idAttr := "epub-file-input",
      onChange --> { event =>
        val inp = event.target.asInstanceOf[dom.html.Input]
        val files = inp.files
        if files.length > 0 then
          val file = files(0)
          val reader = new dom.FileReader()
          reader.onload = { _ =>
            val arrayBuffer = reader.result.asInstanceOf[scala.scalajs.js.typedarray.ArrayBuffer]
            EpubParser.parse(arrayBuffer).`then`[Unit] { book =>
              domain.onLoadBook(book)
            }
          }
          reader.readAsArrayBuffer(file)
          // Reset input so same file can be re-selected
          inp.value = ""
      }
    ),
    button(
      cls := "control-chip",
      span(cls := "icon", "\ud83d\udcd6"),
      "Load EPUB",
      onClick --> { _ =>
        dom.document.getElementById("epub-file-input").asInstanceOf[dom.html.Input].click()
      }
    ),
    button(
      cls := "control-chip",
      span(cls := "icon", "\u2699"),
      "Settings",
      onClick --> (_ => ui.showSettingsModal.set(true))
    )
  )

  def keyboardHandler(domain: AppContext, ui: UiState): Modifier[HtmlElement] =
    onKeyDown.compose(_.withCurrentValueOf(domain.state)) --> { (event, model) =>
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

  def keyboardHints(domain: AppContext): HtmlElement = div(
    cls := "keyboard-hints",
    children <-- domain.state.map { m =>
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

  def textInputModal(domain: AppContext, ui: UiState, onStart: String => Unit): HtmlElement = div(
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
