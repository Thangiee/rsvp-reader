package rsvpreader.viewmodel

import kyo.*
import rsvpreader.config.*

/** Resolves a keyboard key press to a KeyAction, respecting modal state.
  *
  * Returns Absent when modals are open or a key capture is in progress,
  * preventing playback commands from firing during settings interaction.
  */
object KeyDispatch:
  def resolve(
    key: String,
    bindings: KeyBindings,
    modalsOpen: Boolean,
    capturing: Boolean
  ): Maybe[KeyAction] =
    if modalsOpen || capturing then Absent
    else bindings.actionFor(key)
