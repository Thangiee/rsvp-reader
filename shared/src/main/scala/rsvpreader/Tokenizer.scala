package rsvpreader

import kyo.*

/** Converts raw text into a sequence of Tokens for RSVP display.
  * Handles punctuation extraction, ORP calculation, and sentence/paragraph tracking.
  */
object Tokenizer:

  def tokenize(text: String): Span[Token] =
    if text.isEmpty then return Span.empty

    val paragraphs = text.split("\n\n+")
    var sentenceIdx = 0
    var paragraphIdx = 0

    val tokens = paragraphs.flatMap { paragraph =>
      val words = paragraph.split("\\s+").filter(_.nonEmpty)
      val result = words.map { raw =>
        val (word, punct) = extractPunctuation(raw)
        val token = Token(
          text = word,
          focusIndex = calculateFocusIndex(word),
          punctuation = punct,
          sentenceIndex = sentenceIdx,
          paragraphIndex = paragraphIdx
        )
        if punct == Punctuation.Period then sentenceIdx += 1
        token
      }
      paragraphIdx += 1
      sentenceIdx += 1
      result
    }
    Span.from(tokens)

  private def extractPunctuation(word: String): (String, Punctuation) =
    word.lastOption match
      case Some(c) if ".!?".contains(c) => (word.init, Punctuation.Period)
      case Some(c) if ",;:".contains(c) => (word.init, Punctuation.Comma)
      case _ => (word, Punctuation.None)

  private def calculateFocusIndex(word: String): Int =
    if word.isEmpty then 0
    else (word.length - 1) / 3
