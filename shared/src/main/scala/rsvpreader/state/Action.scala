package rsvpreader.state

import rsvpreader.playback.*
import rsvpreader.config.*

/** Actions dispatched to the state manager via the action channel.
  *
  * The Reducer applies these to produce a new DomainModel. PlaybackCmd actions
  * also forward the command to the engine via the command channel.
  */
enum Action:
  case PlaybackCmd(cmd: Command)
  case EngineStateUpdate(viewState: ViewState)
  case SetCenterMode(mode: CenterMode)
  case SetContextSentences(n: Int)
  case UpdateKeyBinding(action: KeyAction, key: String)
