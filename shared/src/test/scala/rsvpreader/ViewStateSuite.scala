package rsvpreader

import kyo.*
import munit.FunSuite

class ViewStateSuite extends FunSuite:

  val tokens: Span[Token] = Span.from(Seq(
    Token("Hello", 1, Punctuation.None, 0, 0),
    Token("world", 1, Punctuation.Period, 0, 0),
    Token("New", 0, Punctuation.None, 1, 1),
    Token("sentence", 2, Punctuation.Period, 1, 1)
  ))

  val config = RsvpConfig(baseWpm = 300)

  test("initial creates paused state at index 0"):
    val state = ViewState.initial(tokens, config)
    assertEquals(state.index, 0)
    assertEquals(state.status, PlayStatus.Paused)
    assertEquals(state.wpm, 300)

  test("currentToken returns token at current index"):
    val state = ViewState(tokens, 1, PlayStatus.Playing, 300)
    assertEquals(state.currentToken, Maybe(tokens(1)))

  test("currentToken returns Absent when index out of bounds"):
    val state = ViewState(tokens, 10, PlayStatus.Playing, 300)
    assertEquals(state.currentToken, Absent)

  test("currentToken returns Absent for negative index"):
    val state = ViewState(tokens, -1, PlayStatus.Playing, 300)
    assertEquals(state.currentToken, Absent)

  test("trailTokens returns previous tokens"):
    val state = ViewState(tokens, 3, PlayStatus.Playing, 300)
    val trail = state.trailTokens(2)
    assertEquals(trail.length, 2)
    assertEquals(trail(0).text, "world")
    assertEquals(trail(1).text, "New")

  test("trailTokens returns empty for index 0"):
    val state = ViewState(tokens, 0, PlayStatus.Playing, 300)
    assertEquals(state.trailTokens(5), Seq.empty)

  test("trailTokens returns empty for count <= 0"):
    val state = ViewState(tokens, 2, PlayStatus.Playing, 300)
    assertEquals(state.trailTokens(0), Seq.empty)
    assertEquals(state.trailTokens(-1), Seq.empty)

  test("currentSentenceTokens returns tokens in same sentence"):
    val state = ViewState(tokens, 0, PlayStatus.Playing, 300)
    val sentence = state.currentSentenceTokens
    assertEquals(sentence.length, 2)
    assertEquals(sentence.map(_.text), Seq("Hello", "world"))

  test("currentSentenceWithHighlight marks current token"):
    val state = ViewState(tokens, 1, PlayStatus.Playing, 300)
    val highlighted = state.currentSentenceWithHighlight
    assertEquals(highlighted.length, 2)
    assertEquals(highlighted(0), (tokens(0), false))
    assertEquals(highlighted(1), (tokens(1), true))
