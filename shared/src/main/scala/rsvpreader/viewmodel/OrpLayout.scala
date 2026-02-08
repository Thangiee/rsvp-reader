package rsvpreader.viewmodel

import rsvpreader.token.*
import rsvpreader.config.*

case class OrpLayout(before: String, focus: String, after: String, offset: Double)

object OrpLayout:
  def compute(token: Token, centerMode: CenterMode): OrpLayout =
    val text = token.text
    val halfLen = text.length / 2.0
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
