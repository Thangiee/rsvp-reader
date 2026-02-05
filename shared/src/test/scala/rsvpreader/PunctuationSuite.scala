package rsvpreader

import munit.FunSuite

class PunctuationSuite extends FunSuite:

  test("Punctuation enum has all expected cases"):
    val cases = Punctuation.values.toSet
    assertEquals(cases.size, 4)
    assert(cases.contains(Punctuation.None), "should contain None")
    assert(cases.contains(Punctuation.Comma), "should contain Comma")
    assert(cases.contains(Punctuation.Period), "should contain Period")
    assert(cases.contains(Punctuation.Paragraph), "should contain Paragraph")
