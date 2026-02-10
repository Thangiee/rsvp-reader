package rsvpreader.viewmodel

import rsvpreader.token.*
import rsvpreader.config.*

/** Layout for a word split around its focus character for ORP-aligned display.
  *
  * @param before Text before the focus character
  * @param focus  The focus character (ORP or first letter, depending on CenterMode)
  * @param after  Text after the focus character, including punctuation
  * @param offset Character offset to center the focus character in the display
  */
case class OrpLayout(before: String, focus: String, after: String, offset: Double)

/** Computes OrpLayout from a Token and CenterMode. */
object OrpLayout:
  def compute(token: Token, centerMode: CenterMode): OrpLayout =
    val text = token.text
    val totalLen = text.length + token.punctuation.text.length
    val halfLen = totalLen / 2.0
    centerMode match
      case CenterMode.ORP =>
        val fi = token.focusIndex
        OrpLayout(
          before = text.take(fi),
          focus = text.lift(fi).fold("")(_.toString),
          after = text.drop(fi + 1) + token.punctuation.text,
          offset = -halfLen + 0.5 + fi
        )
      case CenterMode.First =>
        OrpLayout(
          before = "",
          focus = text.take(1),
          after = text.drop(1) + token.punctuation.text,
          offset = -halfLen + 0.5
        )
      case CenterMode.None =>
        OrpLayout(
          before = text + token.punctuation.text,
          focus = "",
          after = "",
          offset = 0.0
        )
