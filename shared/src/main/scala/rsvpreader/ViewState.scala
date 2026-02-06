package rsvpreader

import kyo.*

/** Immutable snapshot of RSVP playback state for UI rendering.
  *
  * @param tokens All tokens in the text being read
  * @param index  Current token position (0-based)
  * @param status Current playback status (Playing/Paused/Finished)
  * @param wpm    Current words-per-minute speed
  */
case class ViewState(
  tokens: Span[Token],
  index: Int,
  status: PlayStatus,
  wpm: Int
):
  def currentToken: Maybe[Token] =
    if index >= 0 && index < tokens.length then Maybe(tokens(index))
    else Absent

object ViewState:
  def initial(tokens: Span[Token], config: RsvpConfig, startIndex: Int = 0): ViewState =
    val clamped = if tokens.isEmpty then 0 else Math.min(startIndex, tokens.length - 1).max(0)
    ViewState(tokens, clamped, PlayStatus.Paused, config.baseWpm)
