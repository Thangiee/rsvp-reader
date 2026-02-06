# Finished Reading State

## Problem

When playback finishes all tokens, the UI shows "READY TO READ" — identical to the initial empty state. The user can't tell they finished reading; it feels like the text disappeared.

## Solution

Add a `Finished` status that shows the full text view (same as pause view) with the last word highlighted. Pressing play restarts from the beginning.

## Changes

- `PlayStatus.scala`: Add `Finished` case
- `PlaybackEngine.scala`: Emit `Finished` (index = last token) instead of `Stopped` when tokens exhausted; exit loop on `Finished`
- `Components.scala`: Treat `Finished` like `Paused` in `focusWord` (show full text view)
- `AppState.scala`: `togglePlayPause` re-sends tokens on `Finished`; `focusContainerCls` adds `expanded` for `Finished`; `statusDotCls` handles `Finished`
