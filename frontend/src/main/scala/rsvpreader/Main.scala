package rsvpreader

import com.raquo.laminar.api.L.{Var as LaminarVar, *}
import org.scalajs.dom
import kyo.*
import rsvpreader.token.*
import rsvpreader.playback.*
import rsvpreader.config.*
import rsvpreader.state.*
import rsvpreader.book.*
import rsvpreader.ui.*

/** RSVP Reader Application Entry Point
  *
  * Architecture: State manager fiber + engine loop with channel-based communication
  *
  * The app uses three concurrent fibers inside KyoApp's `run` block:
  *
  * 1. STATE MANAGER FIBER - "Single Writer"
  *    - Takes Actions from the action channel and applies AppState.transform
  *    - Updates the reactive stateVar (single writer, many readers)
  *    - Forwards playback commands to the command channel
  *    - Persists settings changes to localStorage
  *
  * 2. ENGINE LOOP - "Session Manager"
  *    - Waits for tokens to arrive via channel (blocks on `tokensCh.take`)
  *    - Creates a new PlaybackEngine for each text loaded
  *    - After playback finishes, loops back to wait for the next text
  *
  * 3. STATE CONSUMER LOOP - "Bridge"
  *    - Takes ViewState snapshots from PlaybackEngine and dispatches
  *      EngineStateUpdate actions to the state manager
  */
object Main extends KyoApp:
  import AllowUnsafe.embrace.danger

  // ─────────────────────────────────────────────────────────────────────────
  // Bootstrap: load persisted state synchronously before DOM render
  // ─────────────────────────────────────────────────────────────────────────

  private val persistence: Persistence = LocalStoragePersistence

  private val initialState: AppState = LocalStoragePersistence.loadSync

  private val savedPosition: Maybe[(Int, Int, Int)] = LocalStoragePersistence.loadPositionSync

  // Mutable position tracker — read by engineLoop, updated by onTextLoaded
  @volatile private var currentPosition: Maybe[(Int, Int, Int)] = savedPosition

  // Load saved input text from localStorage
  private val savedInputText: String =
    Maybe(dom.window.localStorage.getItem("rsvp-inputText"))
      .filter(_.trim.nonEmpty)
      .getOrElse("")

  // ─────────────────────────────────────────────────────────────────────────
  // Reactive state: stateVar is the single source of truth
  // ─────────────────────────────────────────────────────────────────────────

  private val stateVar: LaminarVar[AppState] = LaminarVar(initialState)

  // Action channel — Components dispatch actions here, state manager consumes
  private val actionCh: Channel[Action] = Channel.Unsafe.init[Action](8).safe

  // Command channel — state manager forwards playback commands here
  private val commandCh: Channel[Command] = Channel.Unsafe.init[Command](1).safe

  // Tokens channel — onTextLoaded sends tokenized text here for engineLoop
  private val tokensCh: Channel[kyo.Span[Token]] = Channel.Unsafe.init[kyo.Span[Token]](1).safe

  private val dispatch: Action => Unit = action => actionCh.unsafe.offer(action)

  private val sendCommand: Command => Unit = cmd => {
    commandCh.unsafe.offer(cmd)
    if cmd == Command.Pause then savePosition()
  }

  private val togglePlayPause: () => Unit = () => {
    stateVar.now().viewState.status match
      case PlayStatus.Playing  => sendCommand(Command.Pause)
      case PlayStatus.Finished =>
        // Clear saved position so engine restarts from beginning
        currentPosition = Absent
        dom.window.localStorage.removeItem("rsvp-position")
        // Re-send current tokens to start a fresh playback session
        val tokens = stateVar.now().viewState.tokens
        tokensCh.unsafe.offer(tokens)
        ()
      case _ => sendCommand(Command.Resume)
  }

  private val adjustSpeed: Int => Unit = delta => {
    val current = stateVar.now().viewState.wpm
    val clamped = Math.max(100, Math.min(1000, current + delta))
    sendCommand(Command.SetSpeed(clamped))
    dom.window.localStorage.setItem("rsvp-wpm", clamped.toString)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Build AppContext + UiState and render
  // ─────────────────────────────────────────────────────────────────────────

  private val domain: AppContext = AppContext(
    state = stateVar.signal,
    dispatch = dispatch,
    sendCommand = sendCommand,
    togglePlayPause = togglePlayPause,
    adjustSpeed = adjustSpeed,
    onLoadBook = book => onLoadBook(book)
  )

  private val ui: UiState = UiState.initial(
    inputText = savedInputText,
    showTextInput = savedInputText.trim.isEmpty
  )

  renderOnDomContentLoaded(
    dom.document.getElementById("app"),
    Layout.app(domain, ui, text => onTextLoaded(text, ui, tokensCh, persistence))
  )

  // Save position + input text on page unload
  dom.window.addEventListener("beforeunload", (_: dom.Event) => {
    savePosition()
    dom.window.localStorage.setItem("rsvp-inputText", ui.inputText.now())
  })

  // Auto-load saved text on startup
  if savedInputText.trim.nonEmpty then
    try
      val tokens = Tokenizer.tokenize(savedInputText)
      tokensCh.unsafe.offer(tokens)
      ()
    catch case _: Exception => ()

  // ─────────────────────────────────────────────────────────────────────────
  // Kyo Runtime Entry Point
  // All async Kyo effects must run inside this `run` block.
  // ─────────────────────────────────────────────────────────────────────────

  /** State manager effect — single writer to stateVar.
    *
    * Takes actions from the action channel, applies transform, and updates stateVar.
    * Side effects: forwards playback commands to the command channel, persists settings.
    */
  private def stateManagerLoop: Unit < (Async & Abort[Closed]) =
    Loop(initialState) { model =>
      actionCh.take.map { action =>
        val newModel = model.transform(action)
        stateVar.set(newModel)
        // Side effects: forward commands to engine, persist settings changes
        val sideEffect: Unit < (Async & Abort[Closed]) = action match
          case Action.PlaybackCmd(cmd) => commandCh.put(cmd)
          case _: Action.SetCenterMode | _: Action.SetContextSentences | _: Action.UpdateKeyBinding =>
            persistence.save(newModel)
          case _ => ()
        sideEffect.andThen(Loop.continue(newModel))
      }
    }

  run {
    direct {
      val configRef = AtomicRef.init(RsvpConfig(
        baseWpm = initialState.viewState.wpm,
        wordLengthFactor = .3
      )).now

      Console.printLine("Channels initialized, waiting for text...").now

      // Start state manager fiber in background
      Fiber.init(Abort.run[Closed](stateManagerLoop)).now

      // Start the engine loop — waits for tokens and runs playback
      engineLoop(commandCh, tokensCh, configRef).now
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Engine Loop - Bridges DOM events to Kyo async world
  // ─────────────────────────────────────────────────────────────────────────

  /** Session manager loop that waits for text and runs playback.
    *
    * Flow: Wait for tokens -> Start consumer fiber -> Run engine -> Close channel -> Repeat
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
    Abort.run[Closed] {
      Loop(()): _ =>
        // Take tokens first (blocks until user loads text), then read mutable savedPosition.
        // savedPosition must be read AFTER take returns so that togglePlayPause's
        // clearing of savedPosition (on restart after finish) is visible.
        tokensCh.take.map { tokens =>
          val startIndex = currentPosition.map(_._3).getOrElse(0)
          (tokens, startIndex)
        }.map { (tokens, startIndex) =>
          direct:
            Console.printLine(s"Received ${tokens.length} tokens, starting playback").now

            // Create state channel for this playback session
            val stateCh = Channel.initUnscoped[ViewState](1).now
            val engine = PlaybackEngine(commandCh, stateCh, configRef)

            // Start consumer in background — takes states and dispatches to state manager
            val consumerFiber = Fiber.init(
              Abort.run[Closed](stateConsumerLoop(stateCh))
            ).now

            // Run engine — blocks until playback finishes
            engine.run(tokens, startIndex).now

            // Close state channel: returns any buffered states the consumer hasn't taken
            val remaining = stateCh.close.now
            remaining.foreach(_.foreach { viewState =>
              dispatch(Action.EngineStateUpdate(viewState))
            })
            savePosition()

            Console.printLine("Playback finished, waiting for next text...").now
            Loop.continue(()).now
        }
    }.unit

  /** Consumes state updates from PlaybackEngine and dispatches them as actions.
    *
    * Runs in a background fiber alongside engine.run(). Exits when the state channel
    * is closed (after engine completes), which triggers Abort[Closed] on take.
    */
  private def stateConsumerLoop(
    stateCh: Channel[ViewState]
  ): Unit < (Async & Abort[Closed]) =
    Loop(()) { _ =>
      stateCh.take.map { viewState =>
        dispatch(Action.EngineStateUpdate(viewState))
        Loop.continue(())
      }
    }

  // ─────────────────────────────────────────────────────────────────────────
  // DOM Callbacks
  // ─────────────────────────────────────────────────────────────────────────

  /** Called when user clicks "Start Reading" in the UI.
    *
    * This is a DOM callback — it CANNOT run Kyo async effects directly.
    * Instead, it tokenizes the text and sends tokens through the channel.
    * The engineLoop (running in Kyo's runtime) will receive and process them.
    *
    * Sends LoadText command first to exit any active playback session,
    * then offers the new tokens for the engine loop to pick up.
    */
  private def onTextLoaded(
    text: String,
    ui: UiState,
    tokensCh: Channel[kyo.Span[Token]],
    persistence: Persistence
  ): Unit =
    ui.loadError.set(Absent)
    try
      val tokens = Tokenizer.tokenize(text)
      val textHash = text.hashCode
      val startIndex = currentPosition match
        case Present((hash, _, idx)) if hash == textHash => idx
        case _ => 0
      currentPosition = Present((textHash, 0, startIndex))
      // Save input text so it persists across reloads
      dom.window.localStorage.setItem("rsvp-inputText", text)
      println(s"Loaded ${tokens.length} tokens (resuming at $startIndex), sending to engine")
      commandCh.unsafe.offer(Command.LoadText)
      tokensCh.unsafe.offer(tokens)
      ()
    catch
      case ex: Exception =>
        ui.loadError.set(Maybe(s"Failed to process text: ${ex.getMessage}"))

  /** Called when user loads an EPUB book via file picker. */
  private def onLoadBook(book: Book): Unit =
    dispatch(Action.LoadBook(book))
    try
      val firstChapter = book.chapters(0)
      val tokens = Tokenizer.tokenize(firstChapter.text)
      currentPosition = Absent
      commandCh.unsafe.offer(Command.LoadText)
      tokensCh.unsafe.offer(tokens)
      LocalStoragePersistence.saveBookSync(book)
      ()
    catch case ex: Exception =>
      println(s"Failed to load book: ${ex.getMessage}")

  /** Saves the current playback position to localStorage. */
  private def savePosition(): Unit =
    val m = stateVar.now()
    if m.book.chapters.length > 0 then
      LocalStoragePersistence.savePositionSync(m.book.hashCode, m.chapterIndex, m.viewState.index)
