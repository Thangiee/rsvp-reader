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
        val cleaned = stripTrailingSuffixes(raw)
        val (word, punct) = extractPunctuation(cleaned)
        val token = Token(
          text = word,
          focusIndex = calculateFocusIndex(word),
          punctuation = punct,
          sentenceIndex = sentenceIdx,
          paragraphIndex = paragraphIdx
        )
        if punct.isInstanceOf[Punctuation.Period] then sentenceIdx += 1
        token
      }
      paragraphIdx += 1
      sentenceIdx += 1
      result
    }
    Span.from(tokens)

  private def stripTrailingSuffixes(word: String): String =
    word
      .replaceAll("(\\[\\d+\\])+$", "")
      .replaceAll("[)\"'\\u201d\\u2019]+$", "")

  private def extractPunctuation(word: String): (String, Punctuation) =
    Maybe.fromOption(word.lastOption) match
      case Present(c) if ".!?".contains(c) => (word.init, Punctuation.Period(c.toString))
      case Present(c) if ",;:".contains(c) => (word.init, Punctuation.Comma(c.toString))
      case _ => (word, Punctuation.None)

  private def calculateFocusIndex(word: String): Int =
    word.length match
      case 0          => 0
      case 1 | 2 | 3 => 0
      case 4 | 5     => 1
      case 6 | 7 | 8 | 9 => 2
      case _          => 3
