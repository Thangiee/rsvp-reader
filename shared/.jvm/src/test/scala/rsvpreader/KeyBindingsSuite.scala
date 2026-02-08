package rsvpreader

import kyo.*
import munit.FunSuite

class KeyBindingsSuite extends FunSuite:
  test("KeyAction.values contains all actions"):
    val expected = Set(
      KeyAction.PlayPause, KeyAction.RestartSentence, KeyAction.RestartParagraph,
      KeyAction.SpeedUp, KeyAction.SpeedDown
    )
    assertEquals(KeyAction.values.toSet, expected)

  test("KeyBindings.default provides sensible defaults"):
    val defaults = KeyBindings.default
    assertEquals(defaults.bindings(KeyAction.PlayPause), " ")
    assertEquals(defaults.bindings(KeyAction.RestartSentence), "s")
    assertEquals(defaults.bindings(KeyAction.RestartParagraph), "r")
    assertEquals(defaults.bindings(KeyAction.SpeedUp), "ArrowUp")

  test("KeyBindings.keyFor returns correct key"):
    val bindings = KeyBindings.default
    assertEquals(bindings.keyFor(KeyAction.PlayPause), " ")

  test("KeyBindings.actionFor returns correct action"):
    val bindings = KeyBindings.default
    assertEquals(bindings.actionFor(" "), Present(KeyAction.PlayPause))
    assertEquals(bindings.actionFor("r"), Present(KeyAction.RestartParagraph))
    assertEquals(bindings.actionFor("unknown"), Absent)

  test("KeyBindings.withBinding updates binding"):
    val bindings = KeyBindings.default.withBinding(KeyAction.PlayPause, "Enter")
    assertEquals(bindings.keyFor(KeyAction.PlayPause), "Enter")
