# Bugfixes: Restart, Tokenizer Errors, Mobile Pause View

## 1. Restart from Beginning After Finish

**Problem:** After text finishes, clicking play resumes from the last saved position instead of the beginning.

**Cause:** `togglePlayPause()` re-sends tokens on `PlayStatus.Finished`, but `savedPosition` in localStorage still holds the last paused index. The engine loop uses this to set `startIndex`.

**Fix:** Clear `savedPosition` in the `Finished` branch of `togglePlayPause()` before re-sending tokens. One-line change in `AppState.scala`.

## 2. Tokenizer Error Handling

**Problem:** `Tokenizer.tokenize()` is called with no error handling. Failures go to the JS console with no user feedback.

**Fix:**
- Wrap `Tokenizer.tokenize()` in a try-catch in `Main.scala`'s `onTextLoaded`
- Add an error state (`LaminarVar[Option[String]]`) to `AppState`
- On failure, set error state with a user-friendly message
- Render error message near the text input area / load button
- Clear error when user loads new text

## 3. Mobile Pause Text View

**Problem:** `.pause-text-view` has `max-height: 50vh`, which on small screens pushes controls off the viewport.

**Fix:** Add mobile override in the `@media (max-width: 600px)` block: set `.pause-text-view { max-height: 25vh; }`. Text remains scrollable via existing `overflow-y: auto`.
