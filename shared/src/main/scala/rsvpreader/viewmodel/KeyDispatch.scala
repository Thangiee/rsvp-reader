package rsvpreader.viewmodel

import kyo.*
import rsvpreader.*

object KeyDispatch:
  def resolve(
    key: String,
    bindings: KeyBindings,
    modalsOpen: Boolean,
    capturing: Boolean
  ): Maybe[KeyAction] =
    if modalsOpen || capturing then Absent
    else bindings.actionFor(key)
