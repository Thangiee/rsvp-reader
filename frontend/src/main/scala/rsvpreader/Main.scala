package rsvpreader

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import kyo.*
import rsvpreader.ui.*

object Main extends KyoApp:
  import AllowUnsafe.embrace.danger

  renderOnDomContentLoaded(dom.document.getElementById("app"), Layout.app(onTextLoaded))

  // Initialize channels and start playback engine at startup
  run {
    direct {
      // Initialize channels (unscoped to prevent closure)
      val commandCh = Channel.initUnscoped[Command](1).now
      val tokensCh = Channel.initUnscoped[kyo.Span[Token]](1).now
      AppState.setCommandChannel(commandCh)
      AppState.setTokensChannel(tokensCh)
      Console.printLine("Channels initialized, waiting for text...").now

      // Engine loop: wait for tokens, run playback, repeat
      engineLoop(commandCh, tokensCh).now
    }
  }

  // Separate method for the engine loop to avoid .now inside Loop body
  private def engineLoop(
    commandCh: Channel[Command],
    tokensCh: Channel[kyo.Span[Token]]
  ): Unit < Async =
    // Wrap the whole loop in Abort.run to handle channel closure
    Abort.run[Closed] {
      Loop(()) { _ =>
        for
          tokens <- tokensCh.take
          _      <- Console.printLine(s"Received ${tokens.length} tokens, starting playback")
          engine  = PlaybackEngine(commandCh, AppState.config, state => AppState.viewState.set(state))
          _      <- engine.run(tokens)
          _      <- Console.printLine("Playback finished, waiting for next text...")
        yield Loop.continue(())
      }
    }.unit

  // Just send tokens through the channel - the engine loop will pick them up
  private def onTextLoaded(text: String): Unit =
    val tokens = Tokenizer.tokenize(text)
    println(s"Loaded ${tokens.length} tokens, sending to engine")
    AppState.unsafeGetTokensChannel.unsafe.offer(tokens)
    ()
