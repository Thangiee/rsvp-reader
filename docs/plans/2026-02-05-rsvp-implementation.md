# RSVP Effects Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement the RSVP effects and abstractions from the design document, enabling configurable speed reading with playback controls.

**Architecture:** Shared module contains pure types (`Token`, `Punctuation`, `Command`, `PlayStatus`, `RsvpConfig`) and functions (`Tokenizer`, `calculateDelay`). Frontend module contains `ViewState` and `PlaybackEngine` using Kyo's `Channel` and `Async` effects.

**Tech Stack:** Scala 3.7.4, Kyo 1.0-RC1, Laminar 17.2.1, ScalaJS 1.19.0, munit for testing

---

## Task 1: Add Test Dependencies

**Files:**
- Modify: `build.sbt`

**Step 1: Add munit test dependency to shared module**

Edit `build.sbt` to add test dependencies:

```scala
ThisBuild / scalaVersion := "3.7.4"
ThisBuild / version      := "0.1.0-SNAPSHOT"

lazy val kyoVersion    = "1.0-RC1"
lazy val laminarVersion = "17.2.1"
lazy val munitVersion   = "1.0.0"

lazy val shared = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("shared"))
  .settings(
    libraryDependencies ++= Seq(
      "io.getkyo" %%% "kyo-prelude" % kyoVersion,
      "org.scalameta" %%% "munit" % munitVersion % Test
    )
  )

lazy val sharedJVM = shared.jvm
lazy val sharedJS  = shared.js

lazy val backend = project
  .in(file("backend"))
  .dependsOn(sharedJVM)
  .settings(
    libraryDependencies ++= Seq(
      "io.getkyo" %% "kyo-tapir" % kyoVersion
    )
  )

lazy val frontend = project
  .in(file("frontend"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(sharedJS)
  .settings(
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies ++= Seq(
      "com.raquo"  %%% "laminar"    % laminarVersion,
      "io.getkyo"  %%% "kyo-core"   % kyoVersion,
      "io.getkyo"  %%% "kyo-direct" % kyoVersion
    )
  )
```

**Step 2: Verify compilation**

Run: `sbt compile`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add build.sbt
git commit -m "build: add munit test framework and kyo-prelude to shared module"
```

---

## Task 2: Implement Punctuation Enum

**Files:**
- Create: `shared/src/main/scala/rsvpreader/Punctuation.scala`
- Create: `shared/src/test/scala/rsvpreader/PunctuationSuite.scala`

**Step 1: Write the failing test**

Create `shared/src/test/scala/rsvpreader/PunctuationSuite.scala`:

```scala
package rsvpreader

import munit.FunSuite

class PunctuationSuite extends FunSuite:

  test("Punctuation enum has all expected cases"):
    val cases = Punctuation.values.toSet
    assertEquals(cases.size, 4)
    assert(cases.contains(Punctuation.None))
    assert(cases.contains(Punctuation.Comma))
    assert(cases.contains(Punctuation.Period))
    assert(cases.contains(Punctuation.Paragraph))
```

**Step 2: Run test to verify it fails**

Run: `sbt sharedJVM/test`
Expected: FAIL - `Punctuation` not found

**Step 3: Write minimal implementation**

Create `shared/src/main/scala/rsvpreader/Punctuation.scala`:

```scala
package rsvpreader

enum Punctuation:
  case None
  case Comma      // includes ; :
  case Period     // includes ! ?
  case Paragraph  // end of paragraph
```

**Step 4: Run test to verify it passes**

Run: `sbt sharedJVM/test`
Expected: PASS

**Step 5: Commit**

```bash
git add shared/src/main/scala/rsvpreader/Punctuation.scala shared/src/test/scala/rsvpreader/PunctuationSuite.scala
git commit -m "feat(shared): add Punctuation enum"
```

---

## Task 3: Implement Token Case Class

**Files:**
- Create: `shared/src/main/scala/rsvpreader/Token.scala`
- Create: `shared/src/test/scala/rsvpreader/TokenSuite.scala`

**Step 1: Write the failing test**

Create `shared/src/test/scala/rsvpreader/TokenSuite.scala`:

```scala
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
    assert(current.isEndOfParagraph(Maybe(next)))

  test("isEndOfParagraph returns false when next token has same paragraph"):
    val current = Token("hello", 1, Punctuation.None, 0, 0)
    val next = Token("world", 1, Punctuation.None, 0, 0)
    assert(!current.isEndOfParagraph(Maybe(next)))

  test("isEndOfParagraph returns true when next is Absent"):
    val current = Token("hello", 1, Punctuation.None, 0, 0)
    assert(current.isEndOfParagraph(Absent))

  test("isEndOfSentence returns true when next token has different sentence"):
    val current = Token("hello", 1, Punctuation.Period, 0, 0)
    val next = Token("world", 1, Punctuation.None, 1, 0)
    assert(current.isEndOfSentence(Maybe(next)))

  test("isEndOfSentence returns false when next token has same sentence"):
    val current = Token("hello", 1, Punctuation.None, 0, 0)
    val next = Token("world", 1, Punctuation.None, 0, 0)
    assert(!current.isEndOfSentence(Maybe(next)))

  test("isEndOfSentence returns true when next is Absent"):
    val current = Token("hello", 1, Punctuation.None, 0, 0)
    assert(current.isEndOfSentence(Absent))
```

**Step 2: Run test to verify it fails**

Run: `sbt sharedJVM/test`
Expected: FAIL - `Token` not found

**Step 3: Write minimal implementation**

Create `shared/src/main/scala/rsvpreader/Token.scala`:

```scala
package rsvpreader

import kyo.*

case class Token(
  text: String,
  focusIndex: Int,
  punctuation: Punctuation,
  sentenceIndex: Int,
  paragraphIndex: Int
):
  def isEndOfParagraph(next: Maybe[Token]): Boolean =
    next.fold(true)(_.paragraphIndex != this.paragraphIndex)

  def isEndOfSentence(next: Maybe[Token]): Boolean =
    next.fold(true)(_.sentenceIndex != this.sentenceIndex)
```

**Step 4: Run test to verify it passes**

Run: `sbt sharedJVM/test`
Expected: PASS

**Step 5: Commit**

```bash
git add shared/src/main/scala/rsvpreader/Token.scala shared/src/test/scala/rsvpreader/TokenSuite.scala
git commit -m "feat(shared): add Token case class with sentence/paragraph boundary detection"
```

---

## Task 4: Implement Command and PlayStatus Enums

**Files:**
- Create: `shared/src/main/scala/rsvpreader/Command.scala`
- Create: `shared/src/main/scala/rsvpreader/PlayStatus.scala`
- Create: `shared/src/test/scala/rsvpreader/CommandSuite.scala`

**Step 1: Write the failing test**

Create `shared/src/test/scala/rsvpreader/CommandSuite.scala`:

```scala
package rsvpreader

import munit.FunSuite

class CommandSuite extends FunSuite:

  test("Command.Back stores word count"):
    val cmd = Command.Back(10)
    assertEquals(cmd.words, 10)

  test("Command.SetSpeed stores wpm"):
    val cmd = Command.SetSpeed(500)
    assertEquals(cmd.wpm, 500)

  test("PlayStatus has all expected cases"):
    val cases = PlayStatus.values.toSet
    assertEquals(cases.size, 3)
    assert(cases.contains(PlayStatus.Playing))
    assert(cases.contains(PlayStatus.Paused))
    assert(cases.contains(PlayStatus.Stopped))
```

**Step 2: Run test to verify it fails**

Run: `sbt sharedJVM/test`
Expected: FAIL - `Command` not found

**Step 3: Write minimal implementation**

Create `shared/src/main/scala/rsvpreader/Command.scala`:

```scala
package rsvpreader

enum Command:
  case Pause
  case Resume
  case Back(words: Int)
  case RestartSentence
  case SetSpeed(wpm: Int)
  case Stop
```

Create `shared/src/main/scala/rsvpreader/PlayStatus.scala`:

```scala
package rsvpreader

enum PlayStatus:
  case Playing
  case Paused
  case Stopped
```

**Step 4: Run test to verify it passes**

Run: `sbt sharedJVM/test`
Expected: PASS

**Step 5: Commit**

```bash
git add shared/src/main/scala/rsvpreader/Command.scala shared/src/main/scala/rsvpreader/PlayStatus.scala shared/src/test/scala/rsvpreader/CommandSuite.scala
git commit -m "feat(shared): add Command and PlayStatus enums"
```

---

## Task 5: Implement RsvpConfig

**Files:**
- Create: `shared/src/main/scala/rsvpreader/RsvpConfig.scala`
- Create: `shared/src/test/scala/rsvpreader/RsvpConfigSuite.scala`

**Step 1: Write the failing test**

Create `shared/src/test/scala/rsvpreader/RsvpConfigSuite.scala`:

```scala
package rsvpreader

import kyo.*
import munit.FunSuite

class RsvpConfigSuite extends FunSuite:

  test("RsvpConfig has sensible defaults"):
    val config = RsvpConfig()
    assertEquals(config.baseWpm, 300)
    assertEquals(config.startDelay, 500.millis)
    assertEquals(config.commaDelay, 150.millis)
    assertEquals(config.periodDelay, 300.millis)
    assertEquals(config.paragraphDelay, 500.millis)
    assertEquals(config.paragraphAutoPause, false)
    assertEquals(config.wordLengthEnabled, true)
    assertEquals(config.wordLengthBaseline, 5)
    assertEquals(config.wordLengthFactor, 0.1)
    assertEquals(config.orpEnabled, true)
    assertEquals(config.trailWordCount, 5)

  test("RsvpConfig can be customized"):
    val config = RsvpConfig(
      baseWpm = 500,
      startDelay = Duration.Zero,
      commaDelay = Duration.Zero,
      wordLengthEnabled = false
    )
    assertEquals(config.baseWpm, 500)
    assertEquals(config.startDelay, Duration.Zero)
    assertEquals(config.commaDelay, Duration.Zero)
    assertEquals(config.wordLengthEnabled, false)
    // Others remain default
    assertEquals(config.periodDelay, 300.millis)
```

**Step 2: Run test to verify it fails**

Run: `sbt sharedJVM/test`
Expected: FAIL - `RsvpConfig` not found

**Step 3: Write minimal implementation**

Create `shared/src/main/scala/rsvpreader/RsvpConfig.scala`:

```scala
package rsvpreader

import kyo.*

case class RsvpConfig(
  baseWpm: Int = 300,
  startDelay: Duration = 500.millis,
  commaDelay: Duration = 150.millis,
  periodDelay: Duration = 300.millis,
  paragraphDelay: Duration = 500.millis,
  paragraphAutoPause: Boolean = false,
  wordLengthEnabled: Boolean = true,
  wordLengthBaseline: Int = 5,
  wordLengthFactor: Double = 0.1,
  orpEnabled: Boolean = true,
  trailWordCount: Int = 5
)
```

**Step 4: Run test to verify it passes**

Run: `sbt sharedJVM/test`
Expected: PASS

**Step 5: Commit**

```bash
git add shared/src/main/scala/rsvpreader/RsvpConfig.scala shared/src/test/scala/rsvpreader/RsvpConfigSuite.scala
git commit -m "feat(shared): add RsvpConfig with configurable feature settings"
```

---

## Task 6: Implement Tokenizer

**Files:**
- Create: `shared/src/main/scala/rsvpreader/Tokenizer.scala`
- Create: `shared/src/test/scala/rsvpreader/TokenizerSuite.scala`

**Step 1: Write the failing tests**

Create `shared/src/test/scala/rsvpreader/TokenizerSuite.scala`:

```scala
package rsvpreader

import kyo.*
import munit.FunSuite

class TokenizerSuite extends FunSuite:

  test("tokenize splits text into words"):
    val tokens = Tokenizer.tokenize("hello world")
    assertEquals(tokens.length, 2)
    assertEquals(tokens(0).text, "hello")
    assertEquals(tokens(1).text, "world")

  test("tokenize extracts period punctuation"):
    val tokens = Tokenizer.tokenize("hello.")
    assertEquals(tokens(0).text, "hello")
    assertEquals(tokens(0).punctuation, Punctuation.Period)

  test("tokenize extracts comma punctuation"):
    val tokens = Tokenizer.tokenize("hello, world")
    assertEquals(tokens(0).text, "hello")
    assertEquals(tokens(0).punctuation, Punctuation.Comma)
    assertEquals(tokens(1).punctuation, Punctuation.None)

  test("tokenize extracts exclamation and question marks as Period"):
    val tokens = Tokenizer.tokenize("what? wow!")
    assertEquals(tokens(0).punctuation, Punctuation.Period)
    assertEquals(tokens(1).punctuation, Punctuation.Period)

  test("tokenize extracts semicolon and colon as Comma"):
    val tokens = Tokenizer.tokenize("first; second: third")
    assertEquals(tokens(0).punctuation, Punctuation.Comma)
    assertEquals(tokens(1).punctuation, Punctuation.Comma)
    assertEquals(tokens(2).punctuation, Punctuation.None)

  test("tokenize calculates ORP focus index at ~1/3"):
    val tokens = Tokenizer.tokenize("a ab abc abcd abcde abcdef")
    // focusIndex = (length - 1) / 3
    assertEquals(tokens(0).focusIndex, 0)  // "a": (1-1)/3 = 0
    assertEquals(tokens(1).focusIndex, 0)  // "ab": (2-1)/3 = 0
    assertEquals(tokens(2).focusIndex, 0)  // "abc": (3-1)/3 = 0
    assertEquals(tokens(3).focusIndex, 1)  // "abcd": (4-1)/3 = 1
    assertEquals(tokens(4).focusIndex, 1)  // "abcde": (5-1)/3 = 1
    assertEquals(tokens(5).focusIndex, 1)  // "abcdef": (6-1)/3 = 1

  test("tokenize tracks sentence indices"):
    val tokens = Tokenizer.tokenize("First sentence. Second sentence.")
    assertEquals(tokens(0).sentenceIndex, 0)
    assertEquals(tokens(1).sentenceIndex, 0)
    assertEquals(tokens(2).sentenceIndex, 1)
    assertEquals(tokens(3).sentenceIndex, 1)

  test("tokenize tracks paragraph indices"):
    val tokens = Tokenizer.tokenize("Para one.\n\nPara two.")
    assertEquals(tokens(0).paragraphIndex, 0)
    assertEquals(tokens(1).paragraphIndex, 0)
    assertEquals(tokens(2).paragraphIndex, 1)
    assertEquals(tokens(3).paragraphIndex, 1)

  test("tokenize handles empty input"):
    val tokens = Tokenizer.tokenize("")
    assertEquals(tokens.length, 0)

  test("tokenize handles multiple spaces"):
    val tokens = Tokenizer.tokenize("hello    world")
    assertEquals(tokens.length, 2)

  test("tokenize handles multiple newlines as single paragraph break"):
    val tokens = Tokenizer.tokenize("one\n\n\n\ntwo")
    assertEquals(tokens.length, 2)
    assertEquals(tokens(0).paragraphIndex, 0)
    assertEquals(tokens(1).paragraphIndex, 1)
```

**Step 2: Run test to verify it fails**

Run: `sbt sharedJVM/test`
Expected: FAIL - `Tokenizer` not found

**Step 3: Write minimal implementation**

Create `shared/src/main/scala/rsvpreader/Tokenizer.scala`:

```scala
package rsvpreader

import kyo.*

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
```

**Step 4: Run test to verify it passes**

Run: `sbt sharedJVM/test`
Expected: PASS

**Step 5: Commit**

```bash
git add shared/src/main/scala/rsvpreader/Tokenizer.scala shared/src/test/scala/rsvpreader/TokenizerSuite.scala
git commit -m "feat(shared): add Tokenizer with punctuation extraction and ORP calculation"
```

---

## Task 7: Implement calculateDelay Function

**Files:**
- Create: `shared/src/main/scala/rsvpreader/Delay.scala`
- Create: `shared/src/test/scala/rsvpreader/DelaySuite.scala`

**Step 1: Write the failing tests**

Create `shared/src/test/scala/rsvpreader/DelaySuite.scala`:

```scala
package rsvpreader

import kyo.*
import munit.FunSuite

class DelaySuite extends FunSuite:

  val baseConfig = RsvpConfig(
    baseWpm = 300,
    commaDelay = 150.millis,
    periodDelay = 300.millis,
    paragraphDelay = 500.millis,
    wordLengthEnabled = true,
    wordLengthBaseline = 5,
    wordLengthFactor = 0.1
  )

  // At 300 WPM, base delay = 60000 / 300 = 200ms
  val baseDelay = 200.millis

  test("calculateDelay returns base delay for short word with no punctuation"):
    val token = Token("cat", 0, Punctuation.None, 0, 0)
    val delay = calculateDelay(token, baseConfig)
    assertEquals(delay, baseDelay)

  test("calculateDelay adds comma delay"):
    val token = Token("hello", 1, Punctuation.Comma, 0, 0)
    val delay = calculateDelay(token, baseConfig)
    assertEquals(delay, baseDelay + 150.millis)

  test("calculateDelay adds period delay"):
    val token = Token("hello", 1, Punctuation.Period, 0, 0)
    val delay = calculateDelay(token, baseConfig)
    assertEquals(delay, baseDelay + 300.millis)

  test("calculateDelay adds period + paragraph delay"):
    val token = Token("hello", 1, Punctuation.Paragraph, 0, 0)
    val delay = calculateDelay(token, baseConfig)
    assertEquals(delay, baseDelay + 300.millis + 500.millis)

  test("calculateDelay adds length bonus for long words"):
    // Word "beautiful" has 9 chars, baseline is 5
    // Extra chars = 9 - 5 = 4
    // Bonus = base * 4 * 0.1 = 200 * 0.4 = 80ms
    val token = Token("beautiful", 2, Punctuation.None, 0, 0)
    val delay = calculateDelay(token, baseConfig)
    assertEquals(delay, baseDelay + 80.millis)

  test("calculateDelay skips length bonus when disabled"):
    val config = baseConfig.copy(wordLengthEnabled = false)
    val token = Token("beautiful", 2, Punctuation.None, 0, 0)
    val delay = calculateDelay(token, config)
    assertEquals(delay, baseDelay)

  test("calculateDelay adjusts for different WPM"):
    // At 600 WPM, base delay = 60000 / 600 = 100ms
    val config = baseConfig.copy(baseWpm = 600)
    val token = Token("cat", 0, Punctuation.None, 0, 0)
    val delay = calculateDelay(token, config)
    assertEquals(delay, 100.millis)

  test("calculateDelay combines all factors"):
    // Long word with punctuation
    // Base: 200ms, comma: 150ms, length bonus for 7-char word: 200 * 2 * 0.1 = 40ms
    val token = Token("example", 2, Punctuation.Comma, 0, 0)
    val delay = calculateDelay(token, baseConfig)
    assertEquals(delay, 200.millis + 40.millis + 150.millis)
```

**Step 2: Run test to verify it fails**

Run: `sbt sharedJVM/test`
Expected: FAIL - `calculateDelay` not found

**Step 3: Write minimal implementation**

Create `shared/src/main/scala/rsvpreader/Delay.scala`:

```scala
package rsvpreader

import kyo.*

def calculateDelay(token: Token, config: RsvpConfig): Duration =
  val base = (60000.0 / config.baseWpm).millis

  val lengthBonus =
    if config.wordLengthEnabled && token.text.length > config.wordLengthBaseline then
      val extraChars = token.text.length - config.wordLengthBaseline
      Duration.fromNanos((base.toNanos * extraChars * config.wordLengthFactor).toLong)
    else Duration.Zero

  val punctPause = token.punctuation match
    case Punctuation.Period    => config.periodDelay
    case Punctuation.Comma     => config.commaDelay
    case Punctuation.Paragraph => config.periodDelay + config.paragraphDelay
    case Punctuation.None      => Duration.Zero

  base + lengthBonus + punctPause
```

**Step 4: Run test to verify it passes**

Run: `sbt sharedJVM/test`
Expected: PASS

**Step 5: Commit**

```bash
git add shared/src/main/scala/rsvpreader/Delay.scala shared/src/test/scala/rsvpreader/DelaySuite.scala
git commit -m "feat(shared): add calculateDelay function with WPM, length, and punctuation factors"
```

---

## Task 8: Implement ViewState

**Files:**
- Create: `frontend/src/main/scala/rsvpreader/ViewState.scala`

**Step 1: Write the implementation**

Create `frontend/src/main/scala/rsvpreader/ViewState.scala`:

```scala
package rsvpreader

import kyo.*

case class ViewState(
  tokens: Span[Token],
  index: Int,
  status: PlayStatus,
  wpm: Int
):
  def currentToken: Maybe[Token] =
    if index >= 0 && index < tokens.length then Maybe(tokens(index))
    else Absent

  def trailTokens(count: Int): Span[Token] =
    if count <= 0 || index <= 0 then Span.empty
    else
      val start = Math.max(0, index - count)
      tokens.slice(start, index)

  def currentParagraphTokens: Span[Token] =
    currentToken.fold(Span.empty[Token]) { current =>
      tokens.filter(_.paragraphIndex == current.paragraphIndex)
    }

object ViewState:
  def initial(tokens: Span[Token], config: RsvpConfig): ViewState =
    ViewState(tokens, 0, PlayStatus.Paused, config.baseWpm)
```

**Step 2: Verify compilation**

Run: `sbt frontend/compile`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add frontend/src/main/scala/rsvpreader/ViewState.scala
git commit -m "feat(frontend): add ViewState with trail and paragraph context derivation"
```

---

## Task 9: Implement PlaybackEngine

**Files:**
- Create: `frontend/src/main/scala/rsvpreader/PlaybackEngine.scala`

**Step 1: Write the implementation**

Create `frontend/src/main/scala/rsvpreader/PlaybackEngine.scala`:

```scala
package rsvpreader

import kyo.*

class PlaybackEngine(
  commands: Channel[Command],
  config: RsvpConfig,
  onStateChange: ViewState => Unit
):

  def run(tokens: Span[Token]): Unit < Async =
    val initial = ViewState.initial(tokens, config)
    Sync.defer(onStateChange(initial)).andThen {
      if config.startDelay > Duration.Zero then
        Async.sleep(config.startDelay).andThen(loop(initial))
      else
        loop(initial)
    }

  private def loop(state: ViewState): Unit < Async =
    state.status match
      case PlayStatus.Stopped => Sync.defer(())
      case PlayStatus.Paused  => handlePaused(state)
      case PlayStatus.Playing => handlePlaying(state)

  private def handlePlaying(state: ViewState): Unit < Async =
    state.currentToken match
      case Absent =>
        val done = state.copy(status = PlayStatus.Stopped)
        Sync.defer(onStateChange(done))

      case Present(token) =>
        Sync.defer(onStateChange(state)).andThen {
          val delay = calculateDelay(token, config.copy(baseWpm = state.wpm))

          Async.race(
            Async.sleep(delay).map(_ => Absent),
            commands.take.map(cmd => Maybe(cmd))
          ).map {
            case Absent =>
              val next = state.copy(index = state.index + 1)
              checkParagraphAutoPause(next, token)
            case Present(cmd) =>
              processCommand(cmd, state)
          }
        }

  private def handlePaused(state: ViewState): Unit < Async =
    commands.take.map(cmd => processCommand(cmd, state))

  private def checkParagraphAutoPause(state: ViewState, prevToken: Token): Unit < Async =
    val shouldPause = config.paragraphAutoPause &&
      prevToken.isEndOfParagraph(state.currentToken)
    if shouldPause then loop(state.copy(status = PlayStatus.Paused))
    else loop(state)

  private def processCommand(cmd: Command, state: ViewState): Unit < Async =
    cmd match
      case Command.Pause =>
        val paused = state.copy(status = PlayStatus.Paused)
        Sync.defer(onStateChange(paused)).andThen(loop(paused))

      case Command.Resume =>
        loop(state.copy(status = PlayStatus.Playing))

      case Command.Back(n) =>
        val rewound = state.copy(index = Math.max(0, state.index - n))
        Sync.defer(onStateChange(rewound)).andThen(loop(rewound))

      case Command.RestartSentence =>
        val currentSentence = state.currentToken.fold(-1)(_.sentenceIndex)
        val sentenceStart = findSentenceStart(state.tokens, state.index, currentSentence)
        val restarted = state.copy(index = sentenceStart)
        Sync.defer(onStateChange(restarted)).andThen(loop(restarted))

      case Command.SetSpeed(wpm) =>
        loop(state.copy(wpm = wpm))

      case Command.Stop =>
        val stopped = state.copy(status = PlayStatus.Stopped)
        Sync.defer(onStateChange(stopped))

  private def findSentenceStart(tokens: Span[Token], current: Int, sentenceIdx: Int): Int =
    var i = current
    while i > 0 && tokens(i - 1).sentenceIndex == sentenceIdx do
      i -= 1
    i
```

**Step 2: Verify compilation**

Run: `sbt frontend/compile`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add frontend/src/main/scala/rsvpreader/PlaybackEngine.scala
git commit -m "feat(frontend): add PlaybackEngine with Channel-based commands and responsive pause"
```

---

## Task 10: Update Frontend Main to Use New Architecture

**Files:**
- Modify: `frontend/src/main/scala/rsvpreader/Main.scala`

**Step 1: Update Main.scala with basic integration**

Replace `frontend/src/main/scala/rsvpreader/Main.scala`:

```scala
package rsvpreader

import com.raquo.laminar.api.L.{Var as LaminarVar, *}
import org.scalajs.dom
import kyo.*

import scala.concurrent.ExecutionContext.Implicits.global

object Main extends KyoApp:
  // Reactive state for UI
  val stateVar = LaminarVar(ViewState(
    tokens = Span.empty,
    index = 0,
    status = PlayStatus.Stopped,
    wpm = 300
  ))

  val config = RsvpConfig()

  // Sample text for testing
  val sampleText = """The quick brown fox jumps over the lazy dog. This is a test sentence with some longer words like extraordinary and magnificent.

Second paragraph begins here. It contains multiple sentences. Each sentence should be trackable."""

  val app = div(
    h1("RSVP Reader"),

    // Focus word display
    div(
      cls := "focus-area",
      child <-- stateVar.signal.map { state =>
        state.currentToken match
          case Absent => span("—")
          case Present(token) =>
            val text = token.text
            val focus = token.focusIndex
            span(
              cls := "orp-word",
              span(cls := "orp-before", text.take(focus)),
              span(cls := "orp-focus", text.lift(focus).map(_.toString).getOrElse("")),
              span(cls := "orp-after", text.drop(focus + 1))
            )
      }
    ),

    // Trail display
    div(
      cls := "trail-area",
      children <-- stateVar.signal.map { state =>
        state.trailTokens(config.trailWordCount).toSeq.map { token =>
          span(cls := "trail-word", token.text, " ")
        }
      }
    ),

    // Status display
    div(
      cls := "status",
      child.text <-- stateVar.signal.map { state =>
        s"Status: ${state.status} | Word: ${state.index + 1}/${state.tokens.length} | WPM: ${state.wpm}"
      }
    ),

    // Controls placeholder (will be enhanced with /frontend-design)
    div(
      cls := "controls",
      p("Controls will be added with /frontend-design skill")
    )
  )

  renderOnDomContentLoaded(dom.document.getElementById("app"), app)

  run {
    direct {
      val ch = Channel.init[Command](1).now
      val tokens = Tokenizer.tokenize(sampleText)
      val engine = PlaybackEngine(ch, config, state => stateVar.set(state))

      // Auto-start after a moment
      Async.sleep(1.second).now
      ch.offer(Command.Resume).now

      engine.run(tokens).now
    }
  }
```

**Step 2: Verify compilation and run**

Run: `sbt frontend/fastLinkJS && sbt backend/run`
Expected: Server starts, frontend displays words one at a time

**Step 3: Commit**

```bash
git add frontend/src/main/scala/rsvpreader/Main.scala
git commit -m "feat(frontend): integrate PlaybackEngine with basic UI scaffolding"
```

---

## Task 11: Add Basic CSS Styles

**Files:**
- Modify: `frontend/index.html`

**Step 1: Add inline styles for basic layout**

Replace `frontend/index.html`:

```html
<!DOCTYPE html>
<html>
  <head>
    <meta charset="UTF-8">
    <title>RSVP Reader</title>
    <style>
      :root {
        --bg-color: #f5f5f0;
        --text-color: #3a3a3a;
        --focus-color: #c44;
        --trail-color: #8a8a8a;
      }

      body {
        background: var(--bg-color);
        color: var(--text-color);
        font-family: Georgia, serif;
        margin: 0;
        padding: 20px;
        min-height: 100vh;
      }

      h1 {
        text-align: center;
        margin-bottom: 2rem;
      }

      .focus-area {
        font-size: 2.5rem;
        text-align: center;
        padding: 2rem;
        min-height: 4rem;
      }

      .orp-focus {
        color: var(--focus-color);
        font-weight: bold;
      }

      .trail-area {
        color: var(--trail-color);
        font-size: 0.9rem;
        text-align: center;
        opacity: 0.7;
        min-height: 2rem;
      }

      .trail-word {
        margin: 0 2px;
      }

      .status {
        text-align: center;
        font-size: 0.8rem;
        color: var(--trail-color);
        margin-top: 2rem;
      }

      .controls {
        text-align: center;
        margin-top: 2rem;
      }
    </style>
  </head>
  <body>
    <div id="app"></div>
    <script src="/assets/main.js"></script>
  </body>
</html>
```

**Step 2: Verify styling**

Run: `sbt frontend/fastLinkJS && sbt backend/run`
Open: http://127.0.0.1:8080
Expected: Low-contrast design with ORP highlighting visible

**Step 3: Commit**

```bash
git add frontend/index.html
git commit -m "style: add low-contrast CSS with ORP highlighting"
```

---

## Task 12: Clean Up Old Code

**Files:**
- Delete: `shared/src/main/scala/rsvpreader/Greeting.scala` (no longer needed)
- Modify: `backend/src/main/scala/rsvpreader/Main.scala` (remove hello endpoint)

**Step 1: Remove Greeting.scala**

```bash
rm shared/src/main/scala/rsvpreader/Greeting.scala
```

**Step 2: Simplify backend Main.scala**

Replace `backend/src/main/scala/rsvpreader/Main.scala`:

```scala
package rsvpreader

import kyo.*
import sttp.tapir.*
import sttp.tapir.server.netty.*

import java.nio.file.{Files, Paths}

object Main extends KyoApp:

  val jsRoute = Routes.add(
    _.get
      .in("assets" / "main.js")
      .out(stringBody)
  ) { _ =>
    val path = Paths.get("frontend/target/scala-3.7.4/frontend-fastopt/main.js")
    scala.io.Source.fromFile(path.toFile).mkString
  }

  val indexRoute = Routes.add(
    _.get
      .in("")
      .out(htmlBodyUtf8)
  ) { _ =>
    val path = Paths.get("frontend/index.html")
    scala.io.Source.fromFile(path.toFile).mkString
  }

  run {
    direct {
      jsRoute.now
      indexRoute.now
      val binding = Routes.run.now
      Console.printLine(s"Server running at http://${binding.hostName}:${binding.port}").now
      Fiber.never.get.now
    }
  }
```

**Step 3: Verify everything still works**

Run: `sbt compile && sbt frontend/fastLinkJS && sbt backend/run`
Expected: BUILD SUCCESS, server starts, frontend works

**Step 4: Commit**

```bash
git add -A
git commit -m "refactor: remove unused Greeting code and simplify backend"
```

---

## Summary

This plan implements the RSVP effects design in 12 tasks:

| Task | Component | Description |
|------|-----------|-------------|
| 1 | Build | Add munit test framework |
| 2 | Shared | Punctuation enum |
| 3 | Shared | Token with boundary detection |
| 4 | Shared | Command and PlayStatus enums |
| 5 | Shared | RsvpConfig with defaults |
| 6 | Shared | Tokenizer with ORP calculation |
| 7 | Shared | calculateDelay function |
| 8 | Frontend | ViewState with derived views |
| 9 | Frontend | PlaybackEngine with Channel |
| 10 | Frontend | Main integration |
| 11 | Frontend | Basic CSS styles |
| 12 | Cleanup | Remove old code |

**After completion:** Use `/frontend-design` skill to enhance the UI with proper controls, paragraph context view, and polished visual design.
