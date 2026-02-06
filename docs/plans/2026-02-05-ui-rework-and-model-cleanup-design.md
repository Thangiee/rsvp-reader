# UI Rework & Model Cleanup Design

> **For Claude:** REQUIRED SUB-SKILLS: Use `/kyo` for Scala code changes, `/frontend-design` for UI component and CSS changes.

**Goal:** Simplify the architecture by splitting engine config from UI config, fix center mode alignment, and rework the pause view to show full scrollable text with the current word highlighted.

**Tech Stack:** Scala 3, Kyo effects, Laminar (ScalaJS), CSS-in-HTML

---

## Summary of Changes

| # | Area | Change |
|---|------|--------|
| 1 | Model cleanup | Remove 5 unused RsvpConfig fields, 5 ViewState methods, 1 Token method |
| 2 | Center mode fix | Correct CSS offset math so ORP letter stays fixed at screen center |
| 3 | Pause view | Replace expanded-sentence with full scrollable text, highlighted current word |
| 4 | Sentence context | Show during playback, hide on pause |
| 5 | Resume transition | Smooth collapse from text view to ORP word + delay before advancing |

---

## Phase 1: Model Cleanup

### 1.1 RsvpConfig — Remove UI fields

**File:** `shared/src/main/scala/rsvpreader/RsvpConfig.scala`

Remove these 5 fields (all unused by PlaybackEngine and Delay):

```
- centerMode: CenterMode          → lives in AppState.currentCenterMode
- keyBindings: KeyBindings         → lives in AppState.currentKeyBindings
- showSentenceContext: Boolean     → dead, sentence context always shown
- trailWordCount: Int              → dead, trail feature removed
- orpEnabled: Boolean              → dead, ORP always active
```

Resulting RsvpConfig (9 fields):

```scala
case class RsvpConfig(
  baseWpm: Int = 300,
  startDelay: Duration = 500.millis,
  commaDelay: Duration = 150.millis,
  periodDelay: Duration = 300.millis,
  paragraphDelay: Duration = 500.millis,
  paragraphAutoPause: Boolean = false,
  wordLengthEnabled: Boolean = true,
  wordLengthBaseline: Int = 5,
  wordLengthFactor: Double = 0.1
)
```

### 1.2 ViewState — Strip to pure data

**File:** `shared/src/main/scala/rsvpreader/ViewState.scala`

Remove these methods:

```
- trailTokens(count: Int)           → unused in production
- currentSentenceTokens             → reimplemented inline in Components
- currentSentenceWithHighlight      → reimplemented inline in Components
- currentParagraphTokens            → move inline to Components where used
```

Keep:

```scala
case class ViewState(
  tokens: Span[Token],
  index: Int,
  status: PlayStatus,
  wpm: Int
):
  def currentToken: Maybe[Token] = ...  // keep — used everywhere

object ViewState:
  def initial(tokens: Span[Token], config: RsvpConfig): ViewState = ...  // keep
```

### 1.3 Token — Remove unused method

**File:** `shared/src/main/scala/rsvpreader/Token.scala`

Remove `isEndOfSentence` (unused in production). Keep `isEndOfParagraph` (used by PlaybackEngine for auto-pause).

### 1.4 Test cleanup

- Delete tests for removed ViewState methods from `ViewStateSuite.scala`
- Delete tests for removed RsvpConfig fields from `RsvpConfigSuite.scala`
- Delete test for `Token.isEndOfSentence` from `TokenSuite.scala` (if exists)
- Verify all remaining tests pass: `sbt sharedJVM/test`

---

## Phase 2: Center Mode CSS Fix

**Files:** `frontend/src/main/scala/rsvpreader/ui/Components.scala`, `frontend/index.html`

### Problem

Current offset calculation: `--orp-offset = focusIndex`

This shifts the word left by `focusIndex * charWidth` from its flex-centered position. But flex centering already places the word's midpoint at screen center. The result: the highlighted ORP letter's position shifts between words.

### Fix

Calculate offset relative to the word's center:

```
offset = focusIndex - (wordLength / 2.0)
```

Examples:
- "reading" (7 chars, ORP=2): offset = 2 - 3.5 = -1.5 → shift right 1.5 chars
- "the" (3 chars, ORP=1): offset = 1 - 1.5 = -0.5 → shift right 0.5 chars
- "international" (13 chars, ORP=4): offset = 4 - 6.5 = -2.5 → shift right 2.5 chars

For each CenterMode:
- **ORP:** `offset = focusIndex - (text.length / 2.0)`
- **First:** `offset = 0 - (text.length / 2.0)` (pin first char at center)
- **None:** no transform applied

In Components.scala `focusWord`:

```scala
val offset = centerMode match
  case CenterMode.ORP   => focus - text.length / 2.0
  case CenterMode.First => 0 - text.length / 2.0
  case CenterMode.None  => 0.0 // no shift

styleAttr := s"--orp-offset: $offset"
```

CSS stays the same:

```css
.orp-word {
  --char-width: 0.6em;
  --orp-offset: 0;
  transform: translateX(calc(var(--orp-offset) * var(--char-width) * -1));
}
```

For `CenterMode.None`, offset is 0, so no transform.

---

## Phase 3: Pause View Rework

**Files:** `frontend/src/main/scala/rsvpreader/ui/Components.scala`, `frontend/index.html`

### Current behavior

When paused, `focusWord` switches to an expanded sentence view showing the current sentence with the current word highlighted. Limited to one sentence, no scrolling.

### New behavior

When paused, the focus area is replaced by a **scrollable text container** showing all tokens as flowing text, grouped by paragraph, with the current word highlighted.

### Component: `pauseTextView`

New component in Components.scala:

```
pauseTextView:
  - Container: fixed max-height (~60vh), overflow-y: auto, scrollable
  - Renders ALL tokens as flowing inline spans
  - Paragraph breaks: when token's paragraphIndex differs from previous, insert a visual break (margin/padding)
  - Current word (matching ViewState.index): highlighted class with distinct background + color
  - On mount/update: auto-scroll to keep highlighted word centered via scrollIntoView({ block: 'center', behavior: 'smooth' })
```

### Integration with focusWord

The `focusWord` component switches between two modes (same structure as today):

```
if status == Paused && tokens.nonEmpty:
  render pauseTextView
else:
  render ORP single-word display (with corrected center mode)
```

### Sentence context visibility

The `sentenceContext` component below the focus word:
- **Playing:** visible (shows current sentence with highlighted word)
- **Paused:** hidden (redundant — full text already visible)
- Controlled by CSS class or signal-based `display: none`

---

## Phase 4: Resume Transition

**Files:** `shared/src/main/scala/rsvpreader/PlaybackEngine.scala`, `frontend/src/main/scala/rsvpreader/ui/Components.scala`, `frontend/index.html`

### Smooth collapse (UI)

When resuming from pause:
1. The `expanded` class is removed from the focus container
2. CSS transitions animate: opacity fade out on text view (~200ms), height collapse back to single-line, font-size change
3. Sentence context fades back in

CSS transitions on `.focus-container`:
```css
transition: all 0.3s ease;
```

Text view opacity driven by expanded state:
```css
.pause-text-view {
  opacity: 1;
  transition: opacity 0.2s ease;
}
.focus-container:not(.expanded) .pause-text-view {
  opacity: 0;
}
```

### Resume delay (Engine)

When PlaybackEngine receives `Resume` while paused:
1. Transition to `Playing` status
2. Emit state (triggers UI collapse)
3. Sleep for `startDelay` (default 500ms) — reuses existing config field
4. Begin word advancement loop

This mirrors the existing `startDelay` at playback start. Same concept, same config, no new fields.

**Change in PlaybackEngine.scala** `handlePaused`:

```
case Command.Resume =>
  val playing = state.copy(status = Playing)
  emit(playing)
  sleep(config.startDelay)  // resume delay
  continue(playing)
```

---

## Implementation Order

| Step | Phase | Description | Skill |
|------|-------|-------------|-------|
| 1 | 1.1 | Remove 5 fields from RsvpConfig | `/kyo` |
| 2 | 1.2 | Strip ViewState to pure data | `/kyo` |
| 3 | 1.3 | Remove Token.isEndOfSentence | `/kyo` |
| 4 | 1.4 | Clean up tests, verify `sbt sharedJVM/test` | `/kyo` |
| 5 | 2 | Fix center mode offset math | `/frontend-design` |
| 6 | 3 | Build pauseTextView component | `/frontend-design` |
| 7 | 3 | Integrate into focusWord (swap on pause) | `/frontend-design` |
| 8 | 3 | Hide sentence context on pause | `/frontend-design` |
| 9 | 4 | Add resume delay to PlaybackEngine | `/kyo` |
| 10 | 4 | Add CSS collapse transitions | `/frontend-design` |
| 11 | — | Integration test: all features together | manual |

---

## What's NOT Changing

- Token fields (text, focusIndex, punctuation, sentenceIndex, paragraphIndex) — all used
- Token.isEndOfParagraph — used by engine
- PlaybackEngine core loop — only change is resume delay
- Command enum — no changes
- PlayStatus enum — no changes
- Paragraph modal — untouched
- Settings modal — untouched (centerMode/keyBindings already live in AppState)
- KeyBindings / CenterMode types — stay in shared module, still used by frontend
