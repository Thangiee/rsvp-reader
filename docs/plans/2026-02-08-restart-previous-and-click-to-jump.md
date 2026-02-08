# Restart Previous & Click-to-Jump Design

## Feature 1: Restart Goes to Previous Sentence/Paragraph

### Current Behavior
`RestartSentence` and `RestartParagraph` commands search backward from the current index to find the start of the current sentence/paragraph. If already at the start, they're no-ops.

### New Behavior
When the current token is already at the start of its sentence (or paragraph), the command jumps to the start of the previous sentence (or paragraph). At the very first sentence/paragraph (index 0), it does nothing.

### Implementation
Pure logic change in two places — no new commands, no frontend changes:

1. **`PlaybackEngine.applyCommand`** — modify `RestartSentence` case:
   - Call `findSentenceStart` as today to get `sentenceStart`
   - If `state.index == sentenceStart` and `sentenceIdx > 0`, search backward from `sentenceStart - 1` for the start of `sentenceIdx - 1`
   - Same pattern for `RestartParagraph` with `paragraphIndex`

2. **`Reducer.applyCommand`** — same logic change (duplicate command handling for optimistic UI updates)

## Feature 2: Click Word to Jump

### New Command
Add `JumpToIndex(index: Int)` to the `Command` enum.

### Engine & Reducer Handling
`JumpToIndex(i)` sets `state.index = i` and `status = Paused`. The engine picks it up via `Async.race` and re-renders at the new position.

### Frontend Changes
- `pauseTextView` gets a `sendCommand` callback parameter
- Each word `span` gets an `onClick` handler sending `Command.JumpToIndex(i)`
- Add `cursor: pointer` CSS to `.pause-word` spans

### Data Flow
1. User clicks word `i` in `pauseTextView`
2. `onClick` sends `Command.JumpToIndex(i)` to the command channel
3. `PlaybackEngine` picks it up, calls `applyCommand` → `ViewState(index = i, status = Paused)`
4. Engine emits updated `ViewState` to the state channel
5. Reducer receives `Action.EngineStateUpdate(viewState)` → updates `modelVar`
6. Laminar re-renders `pauseTextView` — status is still `Paused`, so the view stays open
7. Highlight applies `"pause-word current"` to the new index `i`

### Edge Cases
- Clicking the already-highlighted word: no-op (index unchanged)
- Clicking while playing: not possible (pause text view only shows when paused/finished)
- `JumpToIndex` with out-of-bounds index: clamp to valid range

## Files to Modify
- `shared/.../playback/Command.scala` — add `JumpToIndex(index: Int)`
- `shared/.../playback/PlaybackEngine.scala` — restart-previous logic + JumpToIndex handling
- `shared/.../state/Reducer.scala` — same logic changes
- `frontend/.../ui/Components.scala` — `pauseTextView` click handler + signature change
- `frontend/index.html` or CSS — `cursor: pointer` on `.pause-word`
