package rsvpreader.state

import kyo.*
import rsvpreader.token.*
import rsvpreader.playback.*

object Reducer:
  def apply(model: DomainModel, action: Action): DomainModel =
    action match
      case Action.EngineStateUpdate(vs) =>
        model.copy(viewState = vs)

      case Action.PlaybackCmd(cmd) =>
        model.copy(viewState = applyCommand(cmd, model.viewState))

      case Action.SetCenterMode(mode) =>
        model.copy(centerMode = mode)

      case Action.SetContextSentences(n) =>
        model.copy(contextSentences = n)

      case Action.UpdateKeyBinding(action, key) =>
        model.copy(keyBindings = model.keyBindings.withBinding(action, key))

  private def applyCommand(cmd: Command, state: ViewState): ViewState =
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
        if state.index == start && si > 0 then
          val prev = findStart(state.tokens, start - 1, _.sentenceIndex == si - 1)
          state.copy(index = prev)
        else
          state.copy(index = start)
      case Command.RestartParagraph =>
        val pi = state.currentToken.fold(-1)(_.paragraphIndex)
        val start = findStart(state.tokens, state.index, _.paragraphIndex == pi)
        if state.index == start && pi > 0 then
          val prev = findStart(state.tokens, start - 1, _.paragraphIndex == pi - 1)
          state.copy(index = prev)
        else
          state.copy(index = start)

  private def findStart(tokens: Span[Token], current: Int, inGroup: Token => Boolean): Int =
    var i = current
    while i > 0 && inGroup(tokens(i - 1)) do i -= 1
    i
