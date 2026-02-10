package rsvpreader.ui

import com.raquo.laminar.api.L.{Signal as LaminarSignal, *}
import rsvpreader.playback.*
import rsvpreader.state.*

/** Provides read-only access to application state and a dispatch function for actions.
  *
  * Components use `state` signal for reactive rendering and `dispatch` to send
  * actions to the state manager fiber. Helper functions (`sendCommand`,
  * `togglePlayPause`, `adjustSpeed`) encapsulate common command patterns that
  * need unsafe channel access.
  */
case class AppContext(
  state: LaminarSignal[AppState],
  dispatch: Action => Unit,
  sendCommand: Command => Unit,
  togglePlayPause: () => Unit,
  adjustSpeed: Int => Unit
)
