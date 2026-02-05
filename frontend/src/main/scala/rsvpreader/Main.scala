package rsvpreader

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import kyo.*
import rsvpreader.ui.*

object Main extends KyoApp:
  import AllowUnsafe.embrace.danger

  renderOnDomContentLoaded(dom.document.getElementById("app"), Layout.app(onTextLoaded))

  // Initialize channel at startup
  run {
    direct {
      val ch = Channel.init[Command](1).now
      AppState.setChannel(ch)
    }
  }

  private def onTextLoaded(text: String): Unit =
    val tokens = Tokenizer.tokenize(text)
    val ch = AppState.getChannel
    val effect: Unit < (Async & Abort[Closed]) = direct {
      val engine = PlaybackEngine(ch, AppState.config, state => AppState.viewState.set(state))
      engine.run(tokens).now
    }
    // Start the async effect as a fiber (fire-and-forget)
    val fiber = Fiber.init(Abort.run[Closed](effect))
    val _ = Sync.Unsafe.run(fiber)
