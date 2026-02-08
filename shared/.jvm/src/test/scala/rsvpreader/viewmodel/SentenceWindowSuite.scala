package rsvpreader.viewmodel

import kyo.*
import munit.FunSuite
import rsvpreader.token.*

class SentenceWindowSuite extends FunSuite:

  // Two sentences in one paragraph: "Hello world. Foo bar."
  val tokens: Span[Token] = Span(
    Token("Hello", 1, Punctuation.None, 0, 0),
    Token("world", 1, Punctuation.Period("."), 0, 0),
    Token("Foo", 0, Punctuation.None, 1, 0),
    Token("bar", 0, Punctuation.Period("."), 1, 0)
  )

  test("shows current sentence page with correct current marker"):
    val result = SentenceWindow.compute(tokens, currentIndex = 0, numSentences = 1)
    assertEquals(result.length, 2) // "Hello" and "world"
    assertEquals(result(0).text, "Hello")
    assert(result(0).isCurrent)
    assertEquals(result(0).cssClass, "sentence-word current")
    assertEquals(result(1).text, "world.")
    assert(!result(1).isCurrent)
    assertEquals(result(1).cssClass, "sentence-word")

  test("advances to next page when current sentence changes"):
    val result = SentenceWindow.compute(tokens, currentIndex = 2, numSentences = 1)
    assertEquals(result.length, 2) // "Foo" and "bar"
    assertEquals(result(0).text, "Foo")
    assert(result(0).isCurrent)

  test("shows multiple sentences per page"):
    val result = SentenceWindow.compute(tokens, currentIndex = 0, numSentences = 2)
    assertEquals(result.length, 4) // all tokens (both sentences in page)
    // Second sentence tokens should be dimmed
    assertEquals(result(2).cssClass, "sentence-word dim")

  test("dims non-current sentences in same page"):
    val result = SentenceWindow.compute(tokens, currentIndex = 2, numSentences = 2)
    assertEquals(result.length, 4)
    // First sentence should be dimmed, second is current
    assertEquals(result(0).cssClass, "sentence-word dim")
    assertEquals(result(2).cssClass, "sentence-word current")

  test("returns empty for empty tokens"):
    val result = SentenceWindow.compute(Span.empty, currentIndex = 0, numSentences = 1)
    assertEquals(result.length, 0)

  // Two paragraphs: "Hello." (para 0) and "World." (para 1)
  val twoParagraphs: Span[Token] = Span(
    Token("Hello", 1, Punctuation.Period("."), 0, 0),
    Token("World", 1, Punctuation.Period("."), 1, 1)
  )

  test("filters to current paragraph only"):
    val result = SentenceWindow.compute(twoParagraphs, currentIndex = 0, numSentences = 1)
    assertEquals(result.length, 1) // only "Hello" from paragraph 0
    assertEquals(result(0).text, "Hello.")
