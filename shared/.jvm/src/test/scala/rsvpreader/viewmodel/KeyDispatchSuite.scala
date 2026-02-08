package rsvpreader.viewmodel

import kyo.*
import munit.FunSuite
import rsvpreader.config.*

class KeyDispatchSuite extends FunSuite:

  val bindings = KeyBindings.default

  test("space key resolves to PlayPause"):
    val result = KeyDispatch.resolve(" ", bindings, modalsOpen = false, capturing = false)
    assertEquals(result, Present(KeyAction.PlayPause))

  test("returns Absent when modals are open"):
    val result = KeyDispatch.resolve(" ", bindings, modalsOpen = true, capturing = false)
    assertEquals(result, Absent)

  test("returns Absent when capturing key"):
    val result = KeyDispatch.resolve(" ", bindings, modalsOpen = false, capturing = true)
    assertEquals(result, Absent)

  test("unbound key returns Absent"):
    val result = KeyDispatch.resolve("z", bindings, modalsOpen = false, capturing = false)
    assertEquals(result, Absent)

  test("ArrowUp resolves to SpeedUp"):
    val result = KeyDispatch.resolve("ArrowUp", bindings, modalsOpen = false, capturing = false)
    assertEquals(result, Present(KeyAction.SpeedUp))

  test("custom binding works"):
    val custom = bindings.withBinding(KeyAction.PlayPause, "p")
    val result = KeyDispatch.resolve("p", custom, modalsOpen = false, capturing = false)
    assertEquals(result, Present(KeyAction.PlayPause))
