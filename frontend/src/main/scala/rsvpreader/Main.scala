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

  // Load settings early so savedWpm is available for RsvpConfig initialization
  AppState.loadSettings()
  AppState.registerPositionSaver()

  renderOnDomContentLoaded(dom.document.getElementById("app"), Layout.app(onTextLoaded))

  // ─────────────────────────────────────────────────────────────────────────────
  // Kyo Runtime Entry Point
  // All async Kyo effects must run inside this `run` block.
  // Effects triggered from DOM callbacks won't execute properly.
  // ─────────────────────────────────────────────────────────────────────────────
  private val initialWpm = AppState.savedWpm.getOrElse(300)

  run {
    direct {
      // Initialize config ref and channels (unscoped to prevent closure when run block's scope ends)
      // - configRef: shared RSVP config, readable mid-playback for dynamic settings
      // - commandCh: UI sends commands (pause/resume/back) to PlaybackEngine
      // - tokensCh: UI sends tokenized text to engineLoop
      val configRef = AtomicRef.init(RsvpConfig(baseWpm = initialWpm, wordLengthFactor = .3)).now
      val commandCh = Channel.initUnscoped[Command](1).now
      val tokensCh = Channel.initUnscoped[kyo.Span[Token]](1).now
      AppState.setConfigRef(configRef)
      AppState.setCommandChannel(commandCh)
      AppState.setTokensChannel(tokensCh)
      Console.printLine("Channels initialized, waiting for text...").now

      // Start the engine loop - it will wait for tokens and run playback
      engineLoop(commandCh, tokensCh, configRef).now
    }
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Engine Loop - Bridges DOM events to Kyo async world
  // ─────────────────────────────────────────────────────────────────────────────
  /** Session manager loop that waits for text and runs playback.
    *
    * Flow: Wait for tokens → Start consumer fiber → Run engine → Close channel → Repeat
    *
    * This loop exists because DOM callbacks can't run Kyo async effects.
    * Instead, callbacks send tokens via channel, and this loop (running
    * inside KyoApp's runtime) receives them and runs the PlaybackEngine.
    */
  private def engineLoop(
    commandCh: Channel[Command],
    tokensCh: Channel[kyo.Span[Token]],
    configRef: AtomicRef[RsvpConfig]
  ): Unit < Async =
    // Wrap in Abort.run to handle channel closure (e.g., if app shuts down)
    Abort.run[Closed] {
      Loop(()): _ =>
        // Read mutable field outside direct block (Kyo disallows mutable access inside direct)
        val startIndex = AppState.savedPosition.map(_._2).getOrElse(0)
        direct:
          val tokens = tokensCh.take.now // Block until user loads text
          Console.printLine(s"Received ${tokens.length} tokens, starting playback").now

          // Create state channel for this playback session
          // Using initUnscoped since we're inside a loop and need to manage lifetime manually
          val stateCh = Channel.initUnscoped[ViewState](1).now
          val engine = PlaybackEngine(commandCh, stateCh, configRef)

          // Start consumer in background — it takes states and updates the UI.
          // Handles its own Abort[Closed] so it exits cleanly when channel is closed.
          val consumerFiber = Fiber.init(
            Abort.run[Closed](stateConsumerLoop(stateCh))
          ).now

          // Run engine — blocks until playback finishes (resume from saved position if any)
          engine.run(tokens, startIndex).now

          // Close state channel: returns any buffered states the consumer hasn't taken,
          // and causes the consumer's next take to abort with Closed.
          // This avoids the race condition where Async.race could interrupt the consumer
          // after it dequeued a state but before the continuation (UI update) ran.
          val remaining = stateCh.close.now
          remaining.foreach(_.foreach(s => AppState.viewState.set(s)))
          AppState.savePosition()

          Console.printLine("Playback finished, waiting for next text...").now
          Loop.continue(()).now // Loop back to wait for next text
    }.unit

  /** Consumes state updates from PlaybackEngine and updates Laminar reactive state.
    *
    * Runs in a background fiber alongside engine.run(). Exits when the state channel
    * is closed (after engine completes), which triggers Abort[Closed] on take.
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
    val textHash = text.hashCode
    val startIndex = AppState.savedPosition match
      case Some((hash, idx)) if hash == textHash => idx
      case _ => 0
    AppState.savedPosition = Some((textHash, startIndex))
    println(s"Loaded ${tokens.length} tokens (resuming at $startIndex), sending to engine")
    // unsafe.offer is non-blocking - returns false if channel is full
    AppState.unsafeGetTokensChannel.unsafe.offer(tokens)
    ()
