# Control Simplification Design

## Goal

Simplify the RSVP reader controls by removing redundant buttons and replacing "Back 10 words" with "Restart Paragraph."

## Changes

### 1. Remove buttons and features

- **Remove "Show Paragraph" chip button** — redundant with the pause text view which already shows full text with current word highlighted and auto-scroll
- **Remove "Reset" chip button** — "Load Text" covers starting fresh, and the restart icon (⟳) on play/pause covers replaying the same text
- **Remove paragraph modal** — `paragraphModal` in Layout, `paragraphContent` in Components, `showParagraphView` in AppState

### 2. Replace "Back 10 words" with "Restart Paragraph"

- Replace the `⏪` button (Back 10) with a Restart Paragraph button
- Add `Command.RestartParagraph` to the Command enum
- Implement in `PlaybackEngine.applyCommand`: jump to the first token of the current paragraph (find by `paragraphIndex`)
- Add `findParagraphStart` helper similar to existing `findSentenceStart`

### 3. Remove unused commands

- Remove `Command.Back(n)` — no longer sent from UI
- Remove `Command.Stop` — no longer sent from UI (engine loop exit uses `PlayStatus.Stopped`, not the command)

### 4. Update KeyAction enum

Remove:
- `Back`
- `ShowParagraph`
- `CloseParagraph`
- `Reset`

Add:
- `RestartParagraph`

### 5. Update default keybindings

| Action           | Key      |
|------------------|----------|
| PlayPause        | Space    |
| RestartSentence  | `s`      |
| RestartParagraph | `r`      |
| SpeedUp          | ArrowUp  |
| SpeedDown        | ArrowDown|

### 6. Update keyboard hints

Show: Play/Pause, Sentence, Paragraph, Speed

### 7. Update keyboard handler

- Remove cases for `Back`, `ShowParagraph`, `CloseParagraph`, `Reset`
- Add case for `RestartParagraph` → sends `Command.RestartParagraph`

### 8. Update tests

- Remove `Command.Back` and `Command.Stop` tests in `CommandSuite`
- Update `PlaybackEngineSuite` tests that use `Command.Back` and `Command.Stop`
- Add test for `Command.RestartParagraph`

## Files affected

- `shared/src/main/scala/rsvpreader/Command.scala` — remove Back/Stop, add RestartParagraph
- `shared/src/main/scala/rsvpreader/KeyBindings.scala` — update KeyAction enum and defaults
- `shared/src/main/scala/rsvpreader/PlaybackEngine.scala` — add RestartParagraph handling, remove Back/Stop
- `shared/src/test/scala/rsvpreader/CommandSuite.scala` — update tests
- `shared/.jvm/src/test/scala/rsvpreader/PlaybackEngineSuite.scala` — update tests
- `frontend/src/main/scala/rsvpreader/ui/Components.scala` — update buttons, keyboard handler/hints, remove paragraphContent
- `frontend/src/main/scala/rsvpreader/ui/AppState.scala` — remove showParagraphView
- `frontend/src/main/scala/rsvpreader/ui/Layout.scala` — remove paragraphModal
