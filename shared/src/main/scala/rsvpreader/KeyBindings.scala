package rsvpreader

import kyo.Maybe

/** Actions that can be bound to keyboard keys. */
enum KeyAction:
  case PlayPause
  case RestartSentence
  case RestartParagraph
  case SpeedUp
  case SpeedDown

/** Configurable keyboard bindings mapping actions to key codes.
  *
  * @param bindings Map from KeyAction to key code string (e.g., "ArrowLeft", " ", "r")
  */
case class KeyBindings(bindings: Map[KeyAction, String]):
  def keyFor(action: KeyAction): String = bindings.getOrElse(action, "")

  def actionFor(key: String): Maybe[KeyAction] =
    Maybe.fromOption(bindings.find(_._2 == key).map(_._1))

  def withBinding(action: KeyAction, key: String): KeyBindings =
    copy(bindings = bindings + (action -> key))

object KeyBindings:
  val default: KeyBindings = KeyBindings(Map(
    KeyAction.PlayPause        -> " ",
    KeyAction.RestartSentence  -> "s",
    KeyAction.RestartParagraph -> "r",
    KeyAction.SpeedUp          -> "ArrowUp",
    KeyAction.SpeedDown        -> "ArrowDown"
  ))
