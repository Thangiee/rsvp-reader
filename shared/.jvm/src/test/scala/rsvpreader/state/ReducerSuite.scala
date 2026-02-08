package rsvpreader.state

import kyo.*
import munit.FunSuite
import rsvpreader.*

class ReducerSuite extends FunSuite:

  val tokens: Span[Token] = Span(
    Token("one", 0, Punctuation.None, 0, 0),
    Token("two", 0, Punctuation.None, 0, 0),
    Token("three", 1, Punctuation.Period("."), 0, 0)
  )

  val initial = DomainModel(
    viewState = ViewState(tokens, 1, PlayStatus.Playing, 300),
    centerMode = CenterMode.ORP,
    keyBindings = KeyBindings.default,
    contextSentences = 1
  )

  test("EngineStateUpdate replaces viewState"):
    val newVs = ViewState(tokens, 2, PlayStatus.Finished, 300)
    val result = Reducer(initial, Action.EngineStateUpdate(newVs))
    assertEquals(result.viewState, newVs)

  test("PlaybackCmd Pause sets status to Paused"):
    val result = Reducer(initial, Action.PlaybackCmd(Command.Pause))
    assertEquals(result.viewState.status, PlayStatus.Paused)

  test("PlaybackCmd SetSpeed updates wpm"):
    val result = Reducer(initial, Action.PlaybackCmd(Command.SetSpeed(500)))
    assertEquals(result.viewState.wpm, 500)

  test("PlaybackCmd RestartSentence rewinds index to sentence start"):
    val result = Reducer(initial, Action.PlaybackCmd(Command.RestartSentence))
    assertEquals(result.viewState.index, 0) // all tokens are sentence 0

  test("SetCenterMode updates centerMode"):
    val result = Reducer(initial, Action.SetCenterMode(CenterMode.First))
    assertEquals(result.centerMode, CenterMode.First)

  test("SetContextSentences updates contextSentences"):
    val result = Reducer(initial, Action.SetContextSentences(3))
    assertEquals(result.contextSentences, 3)

  test("UpdateKeyBinding updates single binding"):
    val result = Reducer(initial, Action.UpdateKeyBinding(KeyAction.PlayPause, "p"))
    assertEquals(result.keyBindings.keyFor(KeyAction.PlayPause), "p")
    // Other bindings unchanged
    assertEquals(result.keyBindings.keyFor(KeyAction.SpeedUp), "ArrowUp")

  // Derived computation tests
  test("progressPercent at start"):
    val m = initial.copy(viewState = initial.viewState.copy(index = 0))
    assertEqualsDouble(DomainModel.progressPercent(m), 0.0, 0.001)

  test("progressPercent at end"):
    val m = initial.copy(viewState = initial.viewState.copy(index = 2))
    assertEqualsDouble(DomainModel.progressPercent(m), 100.0, 0.001)

  test("progressPercent with empty tokens"):
    val m = initial.copy(viewState = ViewState(Span.empty, 0, PlayStatus.Paused, 300))
    assertEqualsDouble(DomainModel.progressPercent(m), 0.0, 0.001)

  test("timeRemaining with 300 WPM and 300 remaining"):
    val bigTokens = Span.from((1 to 300).map(i => Token(s"w$i", 0, Punctuation.None, 0, 0)))
    val m = initial.copy(viewState = ViewState(bigTokens, 0, PlayStatus.Playing, 300))
    assertEquals(DomainModel.timeRemaining(m), "~1 min")

  test("wordProgress format"):
    val m = initial.copy(viewState = initial.viewState.copy(index = 1))
    assertEquals(DomainModel.wordProgress(m), "2 / 3")
