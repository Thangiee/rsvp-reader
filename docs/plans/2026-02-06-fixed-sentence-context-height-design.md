# Fixed-Height Sentence Context

## Problem

On mobile, when the sentence context area changes content (different sentence lengths), the variable height causes:
1. Controls dock shifts up/down as context grows/shrinks
2. If context gets too long, controls are pushed off-screen

## Solution

Give `.sentence-context` a fixed height so the layout slot is constant regardless of content length.

### CSS Changes (index.html only)

**`.sentence-context` base styles — add:**
- `height: 3.5rem` (2 lines at 1.1rem/1.6 line-height)
- `overflow: hidden`
- `mask-image: linear-gradient(to bottom, black 60%, transparent 100%)` (fade overflow)
- `-webkit-mask-image` (Safari prefix)

**`@media (max-width: 600px)` — add:**
- `.sentence-context { height: 3rem }`

### Why fade instead of scroll

Sentence context is passive during playback — the user watches the focus word, not this area. A gentle fade on overflow is cleaner than a scrollbar and avoids scroll jank.

### What stays the same

- `.sentence-context.hidden` still uses `opacity: 0` / `pointer-events: none` (paused/finished state)
- The hidden element still occupies its fixed height, preventing layout shift on pause/resume
- No Scala code changes needed
