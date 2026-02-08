package rsvpreader.config

import munit.FunSuite

class CenterModeSuite extends FunSuite:
  test("CenterMode.values contains all modes"):
    assertEquals(CenterMode.values.toSet, Set(CenterMode.ORP, CenterMode.First, CenterMode.None))

  test("CenterMode.fromString parses valid values"):
    assertEquals(CenterMode.fromString("orp"), CenterMode.ORP)
    assertEquals(CenterMode.fromString("first"), CenterMode.First)
    assertEquals(CenterMode.fromString("none"), CenterMode.None)

  test("CenterMode.fromString defaults to ORP for invalid input"):
    assertEquals(CenterMode.fromString("invalid"), CenterMode.ORP)
    assertEquals(CenterMode.fromString(""), CenterMode.ORP)
