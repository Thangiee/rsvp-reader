package rsvpreader

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import kyo.*
import rsvpreader.ui.*

object Main extends KyoApp:
  import AllowUnsafe.embrace.danger

  private val sampleText = """The quick brown fox jumps over the lazy dog. This is a test sentence with some longer words like extraordinary and magnificent.

Second paragraph begins here. It contains multiple sentences. Each sentence should be trackable. Reading fast improves comprehension when done correctly.

A third paragraph demonstrates the paragraph pause feature. Notice how the reader handles punctuation, commas, and periods differently. The ORP alignment keeps your eye focused."""

  renderOnDomContentLoaded(dom.document.getElementById("app"), Layout.app)

  run {
    direct {
      val ch = Channel.init[Command](1).now
      AppState.setChannel(ch)

      val tokens = Tokenizer.tokenize(sampleText)
      val engine = PlaybackEngine(ch, AppState.config, state => AppState.viewState.set(state))

      engine.run(tokens).now
    }
  }
