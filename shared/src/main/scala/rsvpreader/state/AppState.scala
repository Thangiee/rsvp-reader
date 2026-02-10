package rsvpreader.state

import kyo.*
import rsvpreader.token.*
import rsvpreader.playback.*
import rsvpreader.config.*
import rsvpreader.book.*

/** Single source of truth for application state.
  *
  * @param viewState         Current playback snapshot (tokens, index, status, wpm)
  * @param centerMode        ORP letter centering mode for the focus display
  * @param keyBindings       User-configured keyboard shortcuts
  * @param contextSentences  Number of sentences shown in the context window
  */
case class AppState(
  viewState: ViewState,
  centerMode: CenterMode,
  keyBindings: KeyBindings,
  contextSentences: Int,
  book: Book,
  chapterIndex: Int
):
  /** Pure state transition: applies an Action to produce a new AppState.
    *
    * Handles playback commands (with index rewinding for restart),
    * engine state updates, and settings changes.
    */
  def transform(action: Action): AppState =
    action match
      case Action.EngineStateUpdate(vs) =>
        copy(viewState = vs)

      case Action.PlaybackCmd(cmd) =>
        copy(viewState = AppState.applyCommand(cmd, viewState))

      case Action.SetCenterMode(mode) =>
        copy(centerMode = mode)

      case Action.SetContextSentences(n) =>
        copy(contextSentences = n)

      case Action.UpdateKeyBinding(action, key) =>
        copy(keyBindings = keyBindings.withBinding(action, key))

      case Action.LoadBook(newBook) =>
        copy(book = newBook, chapterIndex = 0)

      case Action.LoadChapter(index) =>
        val clamped = Math.max(0, Math.min(index, book.chapters.length - 1))
        copy(chapterIndex = clamped)

/** Derived view computations and state transition helpers. */
object AppState:
  def initial: AppState = AppState(
    viewState = ViewState(Span.empty, 0, PlayStatus.Paused, 300),
    centerMode = CenterMode.ORP,
    keyBindings = KeyBindings.default,
    contextSentences = 1,
    book = Book.fromPlainText(""),
    chapterIndex = 0
  )

  def hasNextChapter(m: AppState): Boolean =
    m.chapterIndex < m.book.chapters.length - 1

  def progressPercent(m: AppState): Double =
    val s = m.viewState
    if s.tokens.length == 0 then 0.0
    else if s.tokens.length <= 1 then 100.0
    else (s.index.toDouble / (s.tokens.length - 1)) * 100.0

  def timeRemaining(m: AppState): String =
    val s = m.viewState
    val remaining = s.tokens.length - s.index
    val minutes = remaining.toDouble / s.wpm
    if minutes < 1 then "< 1 min" else s"~${minutes.toInt} min"

  def wordProgress(m: AppState): String =
    val s = m.viewState
    val display = Math.min(s.index + 1, s.tokens.length)
    s"$display / ${s.tokens.length}"

  def statusDotCls(m: AppState): String =
    m.viewState.status match
      case PlayStatus.Playing  => "status-dot playing"
      case PlayStatus.Paused   => "status-dot paused"
      case PlayStatus.Finished => "status-dot paused"

  def focusContainerCls(m: AppState): String =
    val s = m.viewState
    val base = "focus-container"
    val playing = if s.status == PlayStatus.Playing then " playing" else ""
    val expanded = if (s.status == PlayStatus.Paused || s.status == PlayStatus.Finished) && s.tokens.length > 0 then " expanded" else ""
    base + playing + expanded

  def statusText(m: AppState): String =
    m.viewState.status.toString

  private[state] def applyCommand(cmd: Command, state: ViewState): ViewState =
    cmd match
      case Command.Pause =>
        state.copy(status = PlayStatus.Paused)
      case Command.Resume =>
        state.copy(status = PlayStatus.Playing)
      case Command.SetSpeed(wpm) =>
        state.copy(wpm = wpm)
      case Command.RestartSentence =>
        val si = state.currentToken.fold(-1)(_.sentenceIndex)
        val start = findStart(state.tokens, state.index, _.sentenceIndex == si)
        if state.index == start && start > 0 then
          val prevSi = state.tokens(start - 1).sentenceIndex
          val prev = findStart(state.tokens, start - 1, _.sentenceIndex == prevSi)
          state.copy(index = prev)
        else
          state.copy(index = start)
      case Command.RestartParagraph =>
        val pi = state.currentToken.fold(-1)(_.paragraphIndex)
        val start = findStart(state.tokens, state.index, _.paragraphIndex == pi)
        if state.index == start && start > 0 then
          val prevPi = state.tokens(start - 1).paragraphIndex
          val prev = findStart(state.tokens, start - 1, _.paragraphIndex == prevPi)
          state.copy(index = prev)
        else
          state.copy(index = start)
      case Command.JumpToIndex(i) =>
        val clamped = Math.max(0, Math.min(i, state.tokens.length - 1))
        state.copy(index = clamped, status = PlayStatus.Paused)
      case Command.LoadText => state

  private def findStart(tokens: Span[Token], current: Int, inGroup: Token => Boolean): Int =
    var i = current
    while i > 0 && inGroup(tokens(i - 1)) do i -= 1
    i
