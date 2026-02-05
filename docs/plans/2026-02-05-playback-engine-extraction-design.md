# PlaybackEngine Extraction to Shared Module

**Date:** 2026-02-05
**Status:** Ready for implementation

## Goal

Extract `PlaybackEngine` and `ViewState` from frontend to the shared module to enable cross-platform testing. Replace the callback-based state notification with a channel-based approach.

## Current State

- `PlaybackEngine` lives in `frontend/`, untestable on JVM
- Uses callback `onStateChange: ViewState => Unit` for UI updates
- `ViewState` is a pure data class but also in frontend

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| State emission mechanism | Bounded `Channel[ViewState](1)` | Simple, consistent with command channel pattern |
| Channel ownership | Passed as parameter | Symmetric API, testable, caller controls lifecycle |
| Frontend consumption | Polling loop in `engineLoop` | Keeps all Kyo async in existing run block |
| Test timing | `Clock.withTimeControl` | Precise, deterministic, instant execution |

## API Changes

### PlaybackEngine (new signature)

```scala
class PlaybackEngine(
  commands: Channel[Command],
  stateOut: Channel[ViewState],  // replaces onStateChange callback
  config: RsvpConfig
):
  def run(tokens: Span[Token]): Unit < (Async & Abort[Closed])
```

### ViewState

No changes - moves as-is to shared.

## Frontend Integration

### Modified engineLoop in Main.scala

```scala
private def engineLoop(
  commandCh: Channel[Command],
  tokensCh: Channel[Span[Token]]
): Unit < Async =
  Abort.run[Closed] {
    Loop(()): _ =>
      direct:
        val tokens = tokensCh.take.now

        // Create state channel for this playback session
        val stateCh = Channel.init[ViewState](1).now
        val engine = PlaybackEngine(commandCh, stateCh, AppState.config)

        // Run engine and state consumer in parallel
        Async.race(
          engine.run(tokens),
          stateConsumerLoop(stateCh)
        ).now

        Loop.continue(())
  }.unit

private def stateConsumerLoop(
  stateCh: Channel[ViewState]
): Unit < (Async & Abort[Closed]) =
  Loop.foreach(()) { _ =>
    stateCh.take.map { state =>
      AppState.viewState.set(state)
    }
  }
```

## Testing Strategy

### Test Location

`shared/src/test/scala/rsvpreader/PlaybackEngineSuite.scala`

### Test Helpers

```scala
// Instant config for fast tests
val instantConfig = RsvpConfig(
  baseWpm = Int.MaxValue,
  startDelay = Duration.Zero,
  commaDelay = Duration.Zero,
  periodDelay = Duration.Zero,
  paragraphDelay = Duration.Zero
)

// Collect emitted states
def collectStates(stateCh: Channel[ViewState]): Seq[ViewState] < Async
```

### Test Categories

1. **State transitions** - Play emits states, pause stops, resume continues
2. **Command handling** - Back, RestartSentence, SetSpeed, Stop
3. **Config behavior** - Different WPM, paragraph auto-pause
4. **Timing verification** - `Clock.withTimeControl` to verify delays

### Example: Timing Test

```scala
test("respects WPM timing"):
  Clock.withTimeControl { control =>
    direct:
      val commandCh = Channel.init[Command](1).now
      val stateCh = Channel.init[ViewState](1).now
      val engine = PlaybackEngine(commandCh, stateCh, config)

      val fiber = Fiber.init(engine.run(tokens)).now

      // Verify engine waiting
      assert(fiber.done.now == false)

      // Advance time
      control.advance(250.millis).now

      // Verify progress
      val state = stateCh.poll.now
      assert(state.isDefined)
  }
```

## Implementation Order

1. Move `ViewState.scala` to `shared/src/main/scala/rsvpreader/`
2. Move `PlaybackEngine.scala` to shared, update signature
3. Replace `notify(state)` calls with `stateOut.put(state)`
4. Update `Main.scala` with parallel state consumer loop
5. Write tests for state transitions and command handling
6. Write timing tests with `Clock.withTimeControl`

## Files Changed

| File | Action |
|------|--------|
| `frontend/.../ViewState.scala` | Delete |
| `frontend/.../PlaybackEngine.scala` | Delete |
| `shared/.../ViewState.scala` | Create (moved) |
| `shared/.../PlaybackEngine.scala` | Create (moved + modified) |
| `frontend/.../Main.scala` | Modify integration |
| `shared/.../PlaybackEngineSuite.scala` | Create (new tests) |

## Risks

**Low risk** - mostly moving code with one parameter change. Manual verification needed for frontend integration to ensure UI updates correctly.
