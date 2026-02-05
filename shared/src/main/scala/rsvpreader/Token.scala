package rsvpreader

import kyo.*

case class Token(
  text: String,
  focusIndex: Int,
  punctuation: Punctuation,
  sentenceIndex: Int,
  paragraphIndex: Int
):
  def isEndOfParagraph(next: Maybe[Token]): Boolean =
    next.fold(true)(_.paragraphIndex != this.paragraphIndex)

  def isEndOfSentence(next: Maybe[Token]): Boolean =
    next.fold(true)(_.sentenceIndex != this.sentenceIndex)
