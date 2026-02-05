# RSVP Reader Effects & Abstractions Design

**Date:** 2026-02-05
**Status:** Approved

## Overview

Design for the core abstractions and Kyo effects needed to support RSVP reader features. This document defines the data types, pure functions, and effect composition without implementing the full UI.

## Features Covered

1. Punctuation pauses — delay after periods (longer), commas (shorter), paragraph breaks (longest)
2. ORP (Optimal Recognition Point) alignment — focus letter at ~1/3 into word, highlighted in red
3. Basic controls — pause, restart sentence, go back 10 words, show full paragraph
4. Speed adjustment per word length — longer display time for longer words
5. Delay before starting — brief pause after clicking play
6. Low-contrast design — soft colors to prevent afterimage
7. Paragraph pause — auto-pause or longer delay between paragraphs
8. Text trail/context view — previously read words shown below focus area

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        SHARED MODULE                         │
├─────────────────────────────────────────────────────────────┤
│  Types:      Token, Punctuation, Command, PlayStatus,       │
│              RsvpConfig                                      │
│                                                              │
│  Functions:  Tokenizer.tokenize(text): Span[Token]          │
│              calculateDelay(token, config): Duration         │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                       FRONTEND MODULE                        │
├─────────────────────────────────────────────────────────────┤
│  PlaybackEngine:                                             │
│    - commands: Channel[Command] (bounded, capacity 1)       │
│    - onStateChange: (ViewState) => Unit                     │
│    - run(tokens, config): Unit < Async                      │
│                                                              │
│  ViewState:                                                  │
│    - currentToken, index, tokens, status, wpm               │
│    - trailTokens(count), currentParagraphTokens             │
│                                                              │
│  Laminar UI:                                                 │
│    - stateVar: Var[ViewState]                               │
│    - Buttons → channel.offer(Command)                       │
└─────────────────────────────────────────────────────────────┘
```

## Shared Module Types

### Punctuation

```scala
enum Punctuation:
  case None
  case Comma      // includes ; :
  case Period     // includes ! ?
  case Paragraph  // end of paragraph
```

### Token

```scala
case class Token(
  text: String,
  focusIndex: Int,         // ORP position (~length / 3)
  punctuation: Punctuation,
  sentenceIndex: Int,
  paragraphIndex: Int
):
  def isEndOfParagraph(next: Maybe[Token]): Boolean =
    next.fold(true)(_.paragraphIndex != this.paragraphIndex)

  def isEndOfSentence(next: Maybe[Token]): Boolean =
    next.fold(true)(_.sentenceIndex != this.sentenceIndex)
```

### Command

```scala
enum Command:
  case Pause
  case Resume
  case Back(words: Int)
  case RestartSentence
  case SetSpeed(wpm: Int)
  case Stop
```

### PlayStatus

```scala
enum PlayStatus:
  case Playing
  case Paused
  case Stopped
```

### RsvpConfig

```scala
case class RsvpConfig(
  // Base speed
  baseWpm: Int = 300,

  // Feature #5: Start delay
  startDelay: Duration = 500.millis,

  // Feature #1: Punctuation pauses
  commaDelay: Duration = 150.millis,
  periodDelay: Duration = 300.millis,

  // Feature #7: Paragraph handling
  paragraphDelay: Duration = 500.millis,
  paragraphAutoPause: Boolean = false,

  // Feature #4: Word length adjustment
  wordLengthEnabled: Boolean = true,
  wordLengthBaseline: Int = 5,
  wordLengthFactor: Double = 0.1,

  // Feature #2: ORP alignment
  orpEnabled: Boolean = true,

  // Feature #8: Text trail
  trailWordCount: Int = 5
)
```

## Shared Module Functions

### Tokenizer

```scala
object Tokenizer:
  def tokenize(text: String): Span[Token] =
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

### Delay Calculation

```scala
def calculateDelay(token: Token, config: RsvpConfig): Duration =
  val base = (60000.0 / config.baseWpm).millis

  val lengthBonus =
    if config.wordLengthEnabled && token.text.length > config.wordLengthBaseline then
      base * (token.text.length - config.wordLengthBaseline) * config.wordLengthFactor
    else Duration.Zero

  val punctPause = token.punctuation match
    case Punctuation.Period    => config.periodDelay
    case Punctuation.Comma     => config.commaDelay
    case Punctuation.Paragraph => config.periodDelay + config.paragraphDelay
    case Punctuation.None      => Duration.Zero

  base + lengthBonus + punctPause
```

## Frontend Module Types

### ViewState

```scala
case class ViewState(
  tokens: Span[Token],
  index: Int,
  status: PlayStatus,
  wpm: Int
):
  def currentToken: Maybe[Token] =
    if index >= 0 && index < tokens.length then Maybe(tokens(index))
    else Maybe.empty

  def trailTokens(count: Int): Span[Token] =
    val start = Math.max(0, index - count)
    tokens.slice(start, index)

  def currentParagraphTokens: Span[Token] =
    currentToken.fold(Span.empty[Token]) { current =>
      tokens.filter(_.paragraphIndex == current.paragraphIndex)
    }
```

## Frontend Module: PlaybackEngine

```scala
class PlaybackEngine(
  commands: Channel[Command],
  config: RsvpConfig,
  onStateChange: ViewState => Unit
):
  def run(tokens: Span[Token]): Unit < Async =
    val initial = ViewState(tokens, 0, PlayStatus.Paused, config.baseWpm)
    onStateChange(initial)
    Async.sleep(config.startDelay).andThen(loop(initial))

  private def loop(state: ViewState): Unit < Async =
    state.status match
      case PlayStatus.Stopped => ()
      case PlayStatus.Paused  => handlePaused(state)
      case PlayStatus.Playing => handlePlaying(state)

  private def handlePlaying(state: ViewState): Unit < Async =
    state.currentToken match
      case Absent =>
        val done = state.copy(status = PlayStatus.Stopped)
        onStateChange(done)

      case Present(token) =>
        onStateChange(state)
        val delay = calculateDelay(token, config.copy(baseWpm = state.wpm))

        Async.race(
          Async.sleep(delay).map(_ => Maybe.empty[Command]),
          commands.take.map(cmd => Maybe(cmd))
        ).map {
          case Absent =>
            val next = state.copy(index = state.index + 1)
            checkParagraphAutoPause(next, token)
          case Present(cmd) =>
            processCommand(cmd, state)
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
        onStateChange(paused)
        loop(paused)

      case Command.Resume =>
        loop(state.copy(status = PlayStatus.Playing))

      case Command.Back(n) =>
        val rewound = state.copy(index = Math.max(0, state.index - n))
        onStateChange(rewound)
        loop(rewound)

      case Command.RestartSentence =>
        val currentSentence = state.currentToken.fold(-1)(_.sentenceIndex)
        val sentenceStart = findSentenceStart(state.tokens, state.index, currentSentence)
        val restarted = state.copy(index = sentenceStart)
        onStateChange(restarted)
        loop(restarted)

      case Command.SetSpeed(wpm) =>
        loop(state.copy(wpm = wpm))

      case Command.Stop =>
        val stopped = state.copy(status = PlayStatus.Stopped)
        onStateChange(stopped)

  private def findSentenceStart(tokens: Span[Token], current: Int, sentenceIdx: Int): Int =
    var i = current
    while i > 0 && tokens(i - 1).sentenceIndex == sentenceIdx do
      i -= 1
    i
```

## Kyo Effects Used

| Effect | Purpose |
|--------|---------|
| `Async` | Sleep between words, race for responsive pause |
| `Channel` | Command communication (bounded, capacity 1) |
| `Loop` | Stack-safe iteration through tokens |
| `Maybe` | Safe token access |
| `Span` | Efficient token storage |

## Key Design Decisions

1. **Timing in frontend only** — all delay calculations happen where they execute
2. **Channel-based commands** — responsive control via `Async.race(sleep, take)`
3. **Pre-processed tokens** — parse once, read many; separates concerns
4. **Index-based trail** — derive history from position, no extra state
5. **Sentence/paragraph indices on Token** — enables navigation features
6. **Runtime delay calculation** — allows speed changes without re-parsing
7. **All features configurable** — `RsvpConfig` with sensible defaults

## Visual Design Notes

Feature #6 (low-contrast design) is CSS-only:
- Warm off-white background (`#f5f5f0`)
- Soft dark gray text (`#3a3a3a`), not black
- Muted red for ORP highlight (`#c44`)
- Faded trail text with reduced opacity

Full UI implementation will use `/frontend-design` skill.

## Next Steps

1. Implement shared module types and functions
2. Implement `PlaybackEngine` in frontend
3. Use `/frontend-design` for Laminar UI components
4. Add unit tests for tokenizer and delay calculation
