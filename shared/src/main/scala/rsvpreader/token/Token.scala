package rsvpreader.token

import kyo.*

/** A single word unit for RSVP display with positioning and context metadata.
  *
  * @param text           The word text without trailing punctuation
  * @param focusIndex     ORP (Optimal Recognition Point) character index for eye fixation
  * @param punctuation    Trailing punctuation type affecting display delay
  * @param sentenceIndex  Zero-based sentence number within the text
  * @param paragraphIndex Zero-based paragraph number within the text
  */
case class Token(
  text: String,
  focusIndex: Int,
  punctuation: Punctuation,
  sentenceIndex: Int,
  paragraphIndex: Int
):
  def isEndOfParagraph(next: Maybe[Token]): Boolean =
    next.fold(true)(_.paragraphIndex != this.paragraphIndex)
