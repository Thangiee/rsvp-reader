package rsvpreader.book

import kyo.*

case class Chapter(
  title: String,
  text: String
)

case class Book(
  title: String,
  author: String,
  chapters: Span[Chapter]
)

object Book:
  private val TitleMaxLen = 30

  def fromPlainText(text: String): Book =
    val title = deriveTitle(text)
    Book(title, "", Span(Chapter("", text)))

  private def deriveTitle(text: String): String =
    val trimmed = text.trim
    if trimmed.length <= TitleMaxLen then trimmed
    else
      val truncated = trimmed.take(TitleMaxLen)
      val lastSpace = truncated.lastIndexOf(' ')
      val cutoff = if lastSpace > 0 then lastSpace else TitleMaxLen
      trimmed.take(cutoff) + "..."
