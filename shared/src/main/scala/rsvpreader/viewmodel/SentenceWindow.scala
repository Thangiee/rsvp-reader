package rsvpreader.viewmodel

import kyo.*
import rsvpreader.*

object SentenceWindow:
  def compute(tokens: Span[Token], currentIndex: Int, numSentences: Int): Seq[WordDisplay] =
    if tokens.isEmpty || currentIndex < 0 || currentIndex >= tokens.length then return Seq.empty

    val currentToken = tokens(currentIndex)
    val currentSentenceIdx = currentToken.sentenceIndex
    val currentParagraphIdx = currentToken.paragraphIndex

    // Filter to current paragraph
    val paraTokenIndices = (0 until tokens.length).filter(i => tokens(i).paragraphIndex == currentParagraphIdx)
    if paraTokenIndices.isEmpty then return Seq.empty

    // Page computation: find first sentence in paragraph, then page based on numSentences
    val firstSentenceInPara = tokens(paraTokenIndices.head).sentenceIndex
    val relativeSentence = currentSentenceIdx - firstSentenceInPara
    val page = relativeSentence / numSentences
    val minSentence = firstSentenceInPara + page * numSentences
    val maxSentence = minSentence + numSentences - 1

    paraTokenIndices
      .filter { i =>
        val si = tokens(i).sentenceIndex
        si >= minSentence && si <= maxSentence
      }
      .map { i =>
        val token = tokens(i)
        val isCurrent = i == currentIndex
        val isCurrentSentence = token.sentenceIndex == currentSentenceIdx
        val cls = if isCurrent then "sentence-word current"
                  else if !isCurrentSentence then "sentence-word dim"
                  else "sentence-word"
        WordDisplay(
          text = s"${token.text}${token.punctuation.text}",
          cssClass = cls,
          isCurrent = isCurrent
        )
      }
