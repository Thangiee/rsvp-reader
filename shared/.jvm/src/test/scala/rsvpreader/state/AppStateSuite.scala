package rsvpreader.state

import kyo.*
import munit.FunSuite
import rsvpreader.token.*
import rsvpreader.playback.*
import rsvpreader.config.*
import rsvpreader.book.*

class AppStateSuite extends FunSuite:

  val tokens: Span[Token] = Span(
    Token("one", 0, Punctuation.None, 0, 0),
    Token("two", 0, Punctuation.None, 0, 0),
    Token("three", 1, Punctuation.Period("."), 0, 0)
  )

  val initial = AppState(
    viewState = ViewState(tokens, 1, PlayStatus.Playing, 300),
    centerMode = CenterMode.ORP,
    keyBindings = KeyBindings.default,
    contextSentences = 1,
    book = Book.fromPlainText(""),
    chapterIndex = 0
  )

  test("EngineStateUpdate replaces viewState"):
    val newVs = ViewState(tokens, 2, PlayStatus.Finished, 300)
    val result = initial.transform(Action.EngineStateUpdate(newVs))
    assertEquals(result.viewState, newVs)

  test("PlaybackCmd Pause sets status to Paused"):
    val result = initial.transform(Action.PlaybackCmd(Command.Pause))
    assertEquals(result.viewState.status, PlayStatus.Paused)

  test("PlaybackCmd SetSpeed updates wpm"):
    val result = initial.transform(Action.PlaybackCmd(Command.SetSpeed(500)))
    assertEquals(result.viewState.wpm, 500)

  test("PlaybackCmd RestartSentence rewinds index to sentence start"):
    val result = initial.transform(Action.PlaybackCmd(Command.RestartSentence))
    assertEquals(result.viewState.index, 0) // all tokens are sentence 0

  test("SetCenterMode updates centerMode"):
    val result = initial.transform(Action.SetCenterMode(CenterMode.First))
    assertEquals(result.centerMode, CenterMode.First)

  test("SetContextSentences updates contextSentences"):
    val result = initial.transform(Action.SetContextSentences(3))
    assertEquals(result.contextSentences, 3)

  test("UpdateKeyBinding updates single binding"):
    val result = initial.transform(Action.UpdateKeyBinding(KeyAction.PlayPause, "p"))
    assertEquals(result.keyBindings.keyFor(KeyAction.PlayPause), "p")
    // Other bindings unchanged
    assertEquals(result.keyBindings.keyFor(KeyAction.SpeedUp), "ArrowUp")

  // Derived computation tests
  test("progressPercent at start"):
    val m = initial.copy(viewState = initial.viewState.copy(index = 0))
    assertEqualsDouble(AppState.progressPercent(m), 0.0, 0.001)

  test("progressPercent at end"):
    val m = initial.copy(viewState = initial.viewState.copy(index = 2))
    assertEqualsDouble(AppState.progressPercent(m), 100.0, 0.001)

  test("progressPercent with empty tokens"):
    val m = initial.copy(viewState = ViewState(Span.empty, 0, PlayStatus.Paused, 300))
    assertEqualsDouble(AppState.progressPercent(m), 0.0, 0.001)

  test("timeRemaining with 300 WPM and 300 remaining"):
    val bigTokens = Span.from((1 to 300).map(i => Token(s"w$i", 0, Punctuation.None, 0, 0)))
    val m = initial.copy(viewState = ViewState(bigTokens, 0, PlayStatus.Playing, 300))
    assertEquals(AppState.timeRemaining(m), "~1 min")

  test("wordProgress format"):
    val m = initial.copy(viewState = initial.viewState.copy(index = 1))
    assertEquals(AppState.wordProgress(m), "2 / 3")

  // Multi-sentence tokens for restart-previous tests
  val multiSentenceTokens: Span[Token] = Span(
    Token("The",   0, Punctuation.None,       0, 0),
    Token("two",   0, Punctuation.None,       0, 0),
    Token("sat",   0, Punctuation.Period("."), 0, 0),
    Token("A",     0, Punctuation.None,       1, 0),
    Token("dog",   0, Punctuation.None,       1, 0),
    Token("ran",   0, Punctuation.Period("."), 1, 0),
    Token("New",   0, Punctuation.None,       2, 1),
    Token("day",   0, Punctuation.Period("."), 2, 1)
  )

  def modelAt(idx: Int): AppState =
    initial.copy(viewState = ViewState(multiSentenceTokens, idx, PlayStatus.Paused, 300))

  test("RestartSentence mid-sentence goes to sentence start"):
    val result = modelAt(4).transform(Action.PlaybackCmd(Command.RestartSentence))
    assertEquals(result.viewState.index, 3)

  test("RestartSentence at sentence start goes to previous sentence start"):
    val result = modelAt(3).transform(Action.PlaybackCmd(Command.RestartSentence))
    assertEquals(result.viewState.index, 0)

  test("RestartSentence at first sentence start does nothing"):
    val result = modelAt(0).transform(Action.PlaybackCmd(Command.RestartSentence))
    assertEquals(result.viewState.index, 0)

  test("RestartParagraph mid-paragraph goes to paragraph start"):
    val result = modelAt(7).transform(Action.PlaybackCmd(Command.RestartParagraph))
    assertEquals(result.viewState.index, 6)

  test("RestartParagraph at paragraph start goes to previous paragraph start"):
    val result = modelAt(6).transform(Action.PlaybackCmd(Command.RestartParagraph))
    assertEquals(result.viewState.index, 0)

  test("RestartParagraph at first paragraph start does nothing"):
    val result = modelAt(0).transform(Action.PlaybackCmd(Command.RestartParagraph))
    assertEquals(result.viewState.index, 0)

  // Tokens with non-contiguous sentence indices (matching real Tokenizer output).
  // Tokenizer increments sentenceIdx at paragraph boundaries AND after periods,
  // so sentence indices can skip (e.g., 0 → 2 when a paragraph ends with a period).
  val gappedSentenceTokens: Span[Token] = Span(
    Token("Hello", 0, Punctuation.None,       0, 0),  // sent 0, para 0
    Token("world", 0, Punctuation.Period("."), 0, 0),  // sent 0, para 0 (period → sentIdx++)
    // paragraph break → sentIdx++ again, so sentence jumps from 0 to 2
    Token("New",   0, Punctuation.None,       2, 1),   // sent 2, para 1
    Token("day",   0, Punctuation.Period("."), 2, 1)    // sent 2, para 1
  )

  def gappedModelAt(idx: Int): AppState =
    initial.copy(viewState = ViewState(gappedSentenceTokens, idx, PlayStatus.Paused, 300))

  test("RestartSentence at paragraph start with non-contiguous sentence indices goes to previous sentence start"):
    val result = gappedModelAt(2).transform(Action.PlaybackCmd(Command.RestartSentence))
    assertEquals(result.viewState.index, 0)

  test("JumpToIndex sets index and keeps Paused status"):
    val result = modelAt(0).transform(Action.PlaybackCmd(Command.JumpToIndex(5)))
    assertEquals(result.viewState.index, 5)
    assertEquals(result.viewState.status, PlayStatus.Paused)

  test("JumpToIndex clamps to last token when out of bounds"):
    val result = modelAt(0).transform(Action.PlaybackCmd(Command.JumpToIndex(100)))
    assertEquals(result.viewState.index, multiSentenceTokens.length - 1)

  test("JumpToIndex clamps negative to 0"):
    val result = modelAt(3).transform(Action.PlaybackCmd(Command.JumpToIndex(-5)))
    assertEquals(result.viewState.index, 0)

  test("LoadText is a no-op in transform"):
    val result = modelAt(4).transform(Action.PlaybackCmd(Command.LoadText))
    assertEquals(result, modelAt(4))

  test("LoadBook sets book and resets chapterIndex to 0"):
    val book = Book("Test", "Author", Span(Chapter("Ch1", "text1"), Chapter("Ch2", "text2")))
    val withChapter = initial.copy(chapterIndex = 5)
    val result = withChapter.transform(Action.LoadBook(book))
    assertEquals(result.book.title, "Test")
    assertEquals(result.chapterIndex, 0)
    assertEquals(result.book.chapters.length, 2)

  test("LoadChapter updates chapterIndex"):
    val book = Book("Test", "Author", Span(Chapter("Ch1", "t1"), Chapter("Ch2", "t2"), Chapter("Ch3", "t3")))
    val m = initial.copy(book = book, chapterIndex = 0)
    val result = m.transform(Action.LoadChapter(2))
    assertEquals(result.chapterIndex, 2)

  test("LoadChapter clamps to valid range"):
    val book = Book("Test", "Author", Span(Chapter("Ch1", "t1"), Chapter("Ch2", "t2")))
    val m = initial.copy(book = book, chapterIndex = 0)
    val result = m.transform(Action.LoadChapter(99))
    assertEquals(result.chapterIndex, 1)

  test("hasNextChapter returns true when not on last chapter"):
    val book = Book("Test", "", Span(Chapter("Ch1", "t1"), Chapter("Ch2", "t2")))
    val m = initial.copy(book = book, chapterIndex = 0)
    assert(AppState.hasNextChapter(m))

  test("hasNextChapter returns false on last chapter"):
    val book = Book("Test", "", Span(Chapter("Ch1", "t1"), Chapter("Ch2", "t2")))
    val m = initial.copy(book = book, chapterIndex = 1)
    assert(!AppState.hasNextChapter(m))
