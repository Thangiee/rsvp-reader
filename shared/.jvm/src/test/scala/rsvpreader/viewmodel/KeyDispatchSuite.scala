package rsvpreader.viewmodel

import munit.FunSuite
import rsvpreader.*

class KeyDispatchSuite extends FunSuite:

  val bindings = KeyBindings.default

  test("space key resolves to PlayPause"):
    val result = KeyDispatch.resolve(" ", bindings, modalsOpen = false, capturing = false)
    assertEquals(result, Some(KeyAction.PlayPause))

  test("returns None when modals are open"):
    val result = KeyDispatch.resolve(" ", bindings, modalsOpen = true, capturing = false)
    assertEquals(result, None)

  test("returns None when capturing key"):
    val result = KeyDispatch.resolve(" ", bindings, modalsOpen = false, capturing = true)
    assertEquals(result, None)

  test("unbound key returns None"):
    val result = KeyDispatch.resolve("z", bindings, modalsOpen = false, capturing = false)
    assertEquals(result, None)

  test("ArrowUp resolves to SpeedUp"):
    val result = KeyDispatch.resolve("ArrowUp", bindings, modalsOpen = false, capturing = false)
    assertEquals(result, Some(KeyAction.SpeedUp))

  test("custom binding works"):
    val custom = bindings.withBinding(KeyAction.PlayPause, "p")
    val result = KeyDispatch.resolve("p", custom, modalsOpen = false, capturing = false)
    assertEquals(result, Some(KeyAction.PlayPause))
