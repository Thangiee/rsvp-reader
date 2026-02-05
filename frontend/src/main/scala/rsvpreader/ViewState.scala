package rsvpreader

import kyo.*

case class ViewState(
  tokens: Span[Token],
  index: Int,
  status: PlayStatus,
  wpm: Int
):
  def currentToken: Maybe[Token] =
    if index >= 0 && index < tokens.length then Maybe(tokens(index))
    else Absent

  def trailTokens(count: Int): Seq[Token] =
    if count <= 0 || index <= 0 then Seq.empty
    else
      val start = Math.max(0, index - count)
      (start until index).map(i => tokens(i))

  def currentParagraphTokens: Seq[Token] =
    currentToken.fold(Seq.empty[Token]) { current =>
      (0 until tokens.length)
        .map(i => tokens(i))
        .filter(_.paragraphIndex == current.paragraphIndex)
    }

object ViewState:
  def initial(tokens: Span[Token], config: RsvpConfig): ViewState =
    ViewState(tokens, 0, PlayStatus.Paused, config.baseWpm)
