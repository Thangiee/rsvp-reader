# Shared Module Reorganization

## Motivation

The shared module's root package (`rsvpreader.*`) has 11 files with mixed concerns — domain types, engine logic, and configuration. Reorganizing into sub-packages by domain concern improves navigability and makes dependency relationships explicit.

## Target Structure

### Source (`shared/src/main/scala/rsvpreader/`)

```
rsvpreader/
├── token/
│   ├── Token.scala
│   ├── Tokenizer.scala
│   └── Punctuation.scala
├── playback/
│   ├── PlaybackEngine.scala
│   ├── Command.scala
│   ├── ViewState.scala
│   ├── PlayStatus.scala
│   └── Delay.scala
├── config/
│   ├── RsvpConfig.scala
│   ├── CenterMode.scala
│   └── KeyBindings.scala
├── state/            (unchanged)
│   ├── Action.scala
│   ├── DomainModel.scala
│   ├── Persistence.scala
│   └── Reducer.scala
└── viewmodel/        (unchanged)
    ├── KeyDispatch.scala
    ├── OrpLayout.scala
    ├── SentenceWindow.scala
    └── WordDisplay.scala
```

### Tests (`shared/.jvm/src/test/scala/rsvpreader/`)

```
rsvpreader/
├── token/
│   ├── TokenSuite.scala
│   ├── TokenizerSuite.scala
│   └── PunctuationSuite.scala
├── playback/
│   ├── PlaybackEngineSuite.scala
│   ├── CommandSuite.scala
│   ├── ViewStateSuite.scala
│   └── DelaySuite.scala
├── config/
│   ├── RsvpConfigSuite.scala
│   ├── CenterModeSuite.scala
│   └── KeyBindingsSuite.scala
├── state/            (unchanged)
│   ├── PersistenceSuite.scala
│   └── ReducerSuite.scala
└── viewmodel/        (unchanged)
    ├── KeyDispatchSuite.scala
    ├── OrpLayoutSuite.scala
    └── SentenceWindowSuite.scala
```

Test resources (`Douglass_essay.txt`) stay in place.

## Package Dependencies

```
config  <--  playback  -->  token
  ^            ^              ^
  |            |              |
  +--state-----+--------------+
  |            |
  +--viewmodel-+
```

- `token` depends on nothing (within rsvpreader)
- `config` depends on nothing
- `playback` imports from `token` and `config`
- `state` imports from `token`, `playback`, and `config`
- `viewmodel` imports from `token`, `playback`, and `config`

No circular dependencies.

## Import Changes

Every downstream file currently uses `import rsvpreader.*`. After the move, each file switches to explicit sub-package imports matching only what it uses.

### shared/state/

| File | New imports |
|------|-------------|
| `Action.scala` | `rsvpreader.playback.*`, `rsvpreader.config.*` |
| `DomainModel.scala` | `rsvpreader.token.*`, `rsvpreader.playback.*`, `rsvpreader.config.*` |
| `Persistence.scala` | `rsvpreader.config.*` |
| `Reducer.scala` | `rsvpreader.token.*`, `rsvpreader.playback.*`, `rsvpreader.config.*` |

### shared/viewmodel/

| File | New imports |
|------|-------------|
| `KeyDispatch.scala` | `rsvpreader.playback.*`, `rsvpreader.config.*` |
| `OrpLayout.scala` | `rsvpreader.config.*` |
| `SentenceWindow.scala` | `rsvpreader.token.*`, `rsvpreader.playback.*` |
| `WordDisplay.scala` | No changes (no rsvpreader imports) |

### frontend/

| File | New imports |
|------|-------------|
| `Main.scala` | `rsvpreader.token.*`, `rsvpreader.playback.*`, `rsvpreader.config.*` |
| `LocalStoragePersistence.scala` | `rsvpreader.config.*` |
| `ui/Components.scala` | `rsvpreader.token.*`, `rsvpreader.playback.*`, `rsvpreader.config.*` |
| `ui/DomainContext.scala` | `rsvpreader.playback.*` |
| `ui/Settings.scala` | `rsvpreader.config.*` |
| `ui/UiState.scala` | `rsvpreader.config.*` |
| `ui/Layout.scala` | No changes (only imports `rsvpreader.state.*`) |

### backend/

No changes — `backend/Main.scala` has no rsvpreader imports.

### Test files

Same pattern: each test's `import rsvpreader.*` becomes the specific sub-package import matching its source file's package.

## Implementation Steps

1. Create `token/`, `playback/`, `config/` directories under both source and test trees
2. Move source files into new directories, updating `package` declarations
3. Move test files into new directories, updating `package` declarations
4. Update imports in `state/`, `viewmodel/`, `frontend/`, and test files
5. Compile (`sbt compile`) and fix any missed imports
6. Run tests (`sbt test`) to verify nothing broke
7. Delete empty old files/directories

## What Does NOT Change

- `state/` and `viewmodel/` packages stay in place
- `frontend/ui/` package stays in place
- `backend/` stays in place
- No type renames, no API changes, no behavior changes
- `build.sbt` — no changes needed (sbt picks up packages automatically)
