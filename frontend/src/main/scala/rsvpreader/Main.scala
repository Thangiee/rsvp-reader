package rsvpreader

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import kyo.*
import rsvpreader.ui.*

/** RSVP Reader Application Entry Point
  *
  * Architecture: Two-loop design with channel-based communication
  *
  * The app uses two distinct loops that serve different purposes:
  *
  * 1. ENGINE LOOP (in Main) - "Session Manager"
  *    - Runs inside KyoApp's `run` block where Kyo async effects work
  *    - Waits for tokens to arrive via channel (blocks on `tokensCh.take`)
  *    - Creates a new PlaybackEngine for each text loaded
  *    - After playback finishes, loops back to wait for the next text
  *    - Enables loading multiple texts in one session
  *
  * 2. PLAYBACK LOOP (in PlaybackEngine) - "Word Iterator"
  *    - Iterates through tokens word-by-word at configured speed
  *    - Handles commands (pause/resume/back/speed) via command channel
  *    - Uses Async.race to respond instantly to commands during sleep
  *    - Exits when all tokens are displayed or Stop command received
  *
  * Why this design?
  *   - DOM callbacks (like onTextLoaded) CANNOT run Kyo async effects
  *   - KyoApp.run() called from a callback returns immediately without executing
  *   - Solution: Keep the engine loop inside the initial `run` block, use channels
  *     to communicate from DOM callbacks to the Kyo async world
  */
object Main extends KyoApp:
  import AllowUnsafe.embrace.danger

  renderOnDomContentLoaded(dom.document.getElementById("app"), Layout.app(onTextLoaded))

  // ─────────────────────────────────────────────────────────────────────────────
  // Kyo Runtime Entry Point
  // All async Kyo effects must run inside this `run` block.
  // Effects triggered from DOM callbacks won't execute properly.
  // ─────────────────────────────────────────────────────────────────────────────
  run {
    direct {
      // Initialize channels (unscoped to prevent closure when run block's scope ends)
      // - commandCh: UI sends commands (pause/resume/back) to PlaybackEngine
      // - tokensCh: UI sends tokenized text to engineLoop
      val commandCh = Channel.initUnscoped[Command](1).now
      val tokensCh = Channel.initUnscoped[kyo.Span[Token]](1).now
      AppState.setCommandChannel(commandCh)
      AppState.setTokensChannel(tokensCh)
      Console.printLine("Channels initialized, waiting for text...").now

      // Start the engine loop - it will wait for tokens and run playback
      engineLoop(commandCh, tokensCh).now
    }
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Engine Loop - Bridges DOM events to Kyo async world
  // ─────────────────────────────────────────────────────────────────────────────
  /** Session manager loop that waits for text and runs playback.
    *
    * Flow: Wait for tokens → Create engine + state channel → Run both in parallel → Repeat
    *
    * This loop exists because DOM callbacks can't run Kyo async effects.
    * Instead, callbacks send tokens via channel, and this loop (running
    * inside KyoApp's runtime) receives them and runs the PlaybackEngine.
    */
  private def engineLoop(
    commandCh: Channel[Command],
    tokensCh: Channel[kyo.Span[Token]]
  ): Unit < Async =
    // Wrap in Abort.run to handle channel closure (e.g., if app shuts down)
    Abort.run[Closed] {
      Loop(()): _ =>
        direct:
          val tokens = tokensCh.take.now // Block until user loads text
          Console.printLine(s"Received ${tokens.length} tokens, starting playback").now

          // Create state channel for this playback session
          // Using initUnscoped since we're inside a loop and need to manage lifetime manually
          val stateCh = Channel.initUnscoped[ViewState](1).now
          val engine = PlaybackEngine(commandCh, stateCh, AppState.config)

          // Run engine and state consumer in parallel
          // Race ensures both stop when engine completes
          Async.race(
            engine.run(tokens),
            stateConsumerLoop(stateCh)
          ).now

          Console.printLine("Playback finished, waiting for next text...").now
          Loop.continue(()).now // Loop back to wait for next text
    }.unit

  /** Consumes state updates from PlaybackEngine and updates Laminar reactive state.
    *
    * Runs in parallel with engine.run(). When engine finishes and closes the channel,
    * this loop exits via Abort[Closed].
    */
  private def stateConsumerLoop(
    stateCh: Channel[ViewState]
  ): Unit < (Async & Abort[Closed]) =
    Loop(()) { _ =>
      stateCh.take.map { state =>
        AppState.viewState.set(state)
        Loop.continue(())
      }
    }

  // ─────────────────────────────────────────────────────────────────────────────
  // DOM Callback - Sends tokens to engine loop via channel
  // ─────────────────────────────────────────────────────────────────────────────
  /** Called when user clicks "Start Reading" in the UI.
    *
    * This is a DOM callback - it CANNOT run Kyo async effects directly.
    * Instead, it tokenizes the text and sends tokens through the channel.
    * The engineLoop (running in Kyo's runtime) will receive and process them.
    */
  private def onTextLoaded(text: String): Unit =
    val tokens = Tokenizer.tokenize(text)
    println(s"Loaded ${tokens.length} tokens, sending to engine")
    // unsafe.offer is non-blocking - returns false if channel is full
    AppState.unsafeGetTokensChannel.unsafe.offer(tokens)
    ()
