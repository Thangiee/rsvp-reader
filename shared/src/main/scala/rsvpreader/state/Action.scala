package rsvpreader.state

import rsvpreader.*

enum Action:
  case PlaybackCmd(cmd: Command)
  case EngineStateUpdate(viewState: ViewState)
  case SetCenterMode(mode: CenterMode)
  case SetContextSentences(n: Int)
  case UpdateKeyBinding(action: KeyAction, key: String)
