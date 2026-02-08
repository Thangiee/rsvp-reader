package rsvpreader.playback

import kyo.*
import rsvpreader.token.*
import rsvpreader.config.*
import munit.FunSuite

class ViewStateSuite extends FunSuite:

  val tokens: Span[Token] = Span(
    Token("Hello", 1, Punctuation.None, 0, 0),
    Token("world", 1, Punctuation.Period("."), 0, 0),
    Token("New", 0, Punctuation.None, 1, 1),
    Token("sentence", 2, Punctuation.Period("."), 1, 1)
  )

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

  test("initial with startIndex resumes at given position"):
    val state = ViewState.initial(tokens, config, startIndex = 2)
    assertEquals(state.index, 2)
    assertEquals(state.status, PlayStatus.Paused)

  test("initial clamps startIndex to valid range"):
    val state = ViewState.initial(tokens, config, startIndex = 100)
    assertEquals(state.index, 3) // tokens.length - 1

  test("initial clamps negative startIndex to 0"):
    val state = ViewState.initial(tokens, config, startIndex = -5)
    assertEquals(state.index, 0)

