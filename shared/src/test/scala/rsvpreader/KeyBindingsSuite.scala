package rsvpreader

import munit.FunSuite

class KeyBindingsSuite extends FunSuite:
  test("KeyAction.values contains all actions"):
    val expected = Set(
      KeyAction.PlayPause, KeyAction.Back, KeyAction.RestartSentence,
      KeyAction.ShowParagraph, KeyAction.SpeedUp, KeyAction.SpeedDown,
      KeyAction.Reset, KeyAction.CloseParagraph
    )
    assertEquals(KeyAction.values.toSet, expected)

  test("KeyBindings.default provides sensible defaults"):
    val defaults = KeyBindings.default
    assertEquals(defaults.bindings(KeyAction.PlayPause), " ")
    assertEquals(defaults.bindings(KeyAction.Back), "ArrowLeft")
    assertEquals(defaults.bindings(KeyAction.SpeedUp), "ArrowUp")
    assertEquals(defaults.bindings(KeyAction.Reset), "s")

  test("KeyBindings.keyFor returns correct key"):
    val bindings = KeyBindings.default
    assertEquals(bindings.keyFor(KeyAction.PlayPause), " ")

  test("KeyBindings.actionFor returns correct action"):
    val bindings = KeyBindings.default
    assertEquals(bindings.actionFor(" "), Some(KeyAction.PlayPause))
    assertEquals(bindings.actionFor("ArrowLeft"), Some(KeyAction.Back))
    assertEquals(bindings.actionFor("unknown"), None)

  test("KeyBindings.withBinding updates binding"):
    val bindings = KeyBindings.default.withBinding(KeyAction.PlayPause, "Enter")
    assertEquals(bindings.keyFor(KeyAction.PlayPause), "Enter")
