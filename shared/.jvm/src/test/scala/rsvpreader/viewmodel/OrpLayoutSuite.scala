package rsvpreader.viewmodel

import munit.FunSuite
import rsvpreader.token.*
import rsvpreader.config.*

class OrpLayoutSuite extends FunSuite:

  test("ORP mode: splits word at focusIndex with correct offset"):
    val token = Token("hello", 1, Punctuation.None, 0, 0)
    val result = OrpLayout.compute(token, CenterMode.ORP)
    assertEquals(result.before, "h")
    assertEquals(result.focus, "e")
    assertEquals(result.after, "llo")
    // offset = -halfLen + 0.5 + focusIndex = -2.5 + 0.5 + 1 = -1.0
    assertEqualsDouble(result.offset, -1.0, 0.001)

  test("First mode: splits at index 0 with correct offset"):
    val token = Token("hello", 1, Punctuation.None, 0, 0)
    val result = OrpLayout.compute(token, CenterMode.First)
    assertEquals(result.before, "")
    assertEquals(result.focus, "h")
    assertEquals(result.after, "ello")
    // offset = -halfLen + 0.5 = -2.5 + 0.5 = -2.0
    assertEqualsDouble(result.offset, -2.0, 0.001)

  test("None mode: no focus letter, zero offset"):
    val token = Token("hello", 1, Punctuation.None, 0, 0)
    val result = OrpLayout.compute(token, CenterMode.None)
    assertEquals(result.focus, "")
    assertEqualsDouble(result.offset, 0.0, 0.001)

  test("includes punctuation text in after with correct offset"):
    val token = Token("end", 1, Punctuation.Period("."), 0, 0)
    val result = OrpLayout.compute(token, CenterMode.ORP)
    assertEquals(result.after, "d.")
    // totalLen = 3 + 1 = 4, halfLen = 2.0
    // offset = -2.0 + 0.5 + 1 = -0.5
    assertEqualsDouble(result.offset, -0.5, 0.001)

  test("single character word"):
    val token = Token("I", 0, Punctuation.None, 0, 0)
    val result = OrpLayout.compute(token, CenterMode.ORP)
    assertEquals(result.before, "")
    assertEquals(result.focus, "I")
    assertEquals(result.after, "")
