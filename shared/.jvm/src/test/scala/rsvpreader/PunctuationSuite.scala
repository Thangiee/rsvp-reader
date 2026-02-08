package rsvpreader

import munit.FunSuite

class PunctuationSuite extends FunSuite:

  test("Punctuation text extension returns correct characters"):
    assertEquals(Punctuation.None.text, "")
    assertEquals(Punctuation.Comma(",").text, ",")
    assertEquals(Punctuation.Comma(";").text, ";")
    assertEquals(Punctuation.Period(".").text, ".")
    assertEquals(Punctuation.Period("!").text, "!")
    assertEquals(Punctuation.Period("?").text, "?")
    assertEquals(Punctuation.Paragraph.text, "")
