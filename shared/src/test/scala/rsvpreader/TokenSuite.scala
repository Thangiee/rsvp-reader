package rsvpreader

import kyo.*
import munit.FunSuite

class TokenSuite extends FunSuite:

  test("Token stores all fields correctly"):
    val token = Token(
      text = "hello",
      focusIndex = 1,
      punctuation = Punctuation.None,
      sentenceIndex = 0,
      paragraphIndex = 0
    )
    assertEquals(token.text, "hello")
    assertEquals(token.focusIndex, 1)
    assertEquals(token.punctuation, Punctuation.None)
    assertEquals(token.sentenceIndex, 0)
    assertEquals(token.paragraphIndex, 0)

  test("isEndOfParagraph returns true when next token has different paragraph"):
    val current = Token("hello", 1, Punctuation.Period, 0, 0)
    val next = Token("world", 1, Punctuation.None, 1, 1)
    assert(current.isEndOfParagraph(Maybe(next)), "should be end of paragraph")

  test("isEndOfParagraph returns false when next token has same paragraph"):
    val current = Token("hello", 1, Punctuation.None, 0, 0)
    val next = Token("world", 1, Punctuation.None, 0, 0)
    assert(!current.isEndOfParagraph(Maybe(next)), "should not be end of paragraph")

  test("isEndOfParagraph returns true when next is Absent"):
    val current = Token("hello", 1, Punctuation.None, 0, 0)
    assert(current.isEndOfParagraph(Absent), "should be end of paragraph when no next")

  test("isEndOfSentence returns true when next token has different sentence"):
    val current = Token("hello", 1, Punctuation.Period, 0, 0)
    val next = Token("world", 1, Punctuation.None, 1, 0)
    assert(current.isEndOfSentence(Maybe(next)), "should be end of sentence")

  test("isEndOfSentence returns false when next token has same sentence"):
    val current = Token("hello", 1, Punctuation.None, 0, 0)
    val next = Token("world", 1, Punctuation.None, 0, 0)
    assert(!current.isEndOfSentence(Maybe(next)), "should not be end of sentence")

  test("isEndOfSentence returns true when next is Absent"):
    val current = Token("hello", 1, Punctuation.None, 0, 0)
    assert(current.isEndOfSentence(Absent), "should be end of sentence when no next")
