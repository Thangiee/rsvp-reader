package rsvpreader.viewmodel

import rsvpreader.*

object KeyDispatch:
  def resolve(
    key: String,
    bindings: KeyBindings,
    modalsOpen: Boolean,
    capturing: Boolean
  ): Option[KeyAction] =
    if modalsOpen || capturing then None
    else bindings.actionFor(key)
