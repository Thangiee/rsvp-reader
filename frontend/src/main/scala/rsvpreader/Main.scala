package rsvpreader

import com.raquo.laminar.api.L.{Var as LaminarVar, *}
import org.scalajs.dom
import kyo.*

object Main extends KyoApp:
  // Reactive state for UI
  val stateVar = LaminarVar(ViewState(
    tokens = Span.empty,
    index = 0,
    status = PlayStatus.Stopped,
    wpm = 300
  ))

  val config = RsvpConfig()

  // Sample text for testing
  val sampleText = """The quick brown fox jumps over the lazy dog. This is a test sentence with some longer words like extraordinary and magnificent.

Second paragraph begins here. It contains multiple sentences. Each sentence should be trackable."""

  val app = div(
    h1("RSVP Reader"),

    // Focus word display
    div(
      cls := "focus-area",
      child <-- stateVar.signal.map { state =>
        state.currentToken match
          case Absent => span("—")
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
    ),

    // Trail display
    div(
      cls := "trail-area",
      children <-- stateVar.signal.map { state =>
        state.trailTokens(config.trailWordCount).map { token =>
          span(cls := "trail-word", token.text, " ")
        }
      }
    ),

    // Status display
    div(
      cls := "status",
      child.text <-- stateVar.signal.map { state =>
        s"Status: ${state.status} | Word: ${state.index + 1}/${state.tokens.length} | WPM: ${state.wpm}"
      }
    ),

    // Controls placeholder (will be enhanced with /frontend-design)
    div(
      cls := "controls",
      p("Controls will be added with /frontend-design skill")
    )
  )

  renderOnDomContentLoaded(dom.document.getElementById("app"), app)

  run {
    direct {
      val ch = Channel.init[Command](1).now
      val tokens = Tokenizer.tokenize(sampleText)
      val engine = PlaybackEngine(ch, config, state => stateVar.set(state))

      // Auto-start after a moment
      Async.sleep(1.second).now
      ch.offer(Command.Resume).now

      engine.run(tokens).now
    }
  }
