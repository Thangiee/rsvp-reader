package rsvpreader

import kyo.*
import munit.FunSuite

class TokenizerSuite extends FunSuite:

  test("tokenize splits text into words"):
    val tokens = Tokenizer.tokenize("hello world")
    assertEquals(tokens.length, 2)
    assertEquals(tokens(0).text, "hello")
    assertEquals(tokens(1).text, "world")

  test("tokenize extracts period punctuation"):
    val tokens = Tokenizer.tokenize("hello.")
    assertEquals(tokens(0).text, "hello")
    assertEquals(tokens(0).punctuation, Punctuation.Period("."))

  test("tokenize extracts comma punctuation"):
    val tokens = Tokenizer.tokenize("hello, world")
    assertEquals(tokens(0).text, "hello")
    assertEquals(tokens(0).punctuation, Punctuation.Comma(","))
    assertEquals(tokens(1).punctuation, Punctuation.None)

  test("tokenize extracts exclamation and question marks as Period"):
    val tokens = Tokenizer.tokenize("what? wow!")
    assertEquals(tokens(0).punctuation, Punctuation.Period("?"))
    assertEquals(tokens(1).punctuation, Punctuation.Period("!"))

  test("tokenize extracts semicolon and colon as Comma"):
    val tokens = Tokenizer.tokenize("first; second: third")
    assertEquals(tokens(0).punctuation, Punctuation.Comma(";"))
    assertEquals(tokens(1).punctuation, Punctuation.Comma(":"))
    assertEquals(tokens(2).punctuation, Punctuation.None)

  test("tokenize calculates ORP focus index via lookup table"):
    val tokens = Tokenizer.tokenize("a ab abc abcd abcde abcdef abcdefghi abcdefghij")
    // length 1-3 => 1, 4-5 => 2, 6-9 => 3, 10+ => 4
    assertEquals(tokens(0).focusIndex, 1)  // "a": len 1 => 1
    assertEquals(tokens(1).focusIndex, 1)  // "ab": len 2 => 1
    assertEquals(tokens(2).focusIndex, 1)  // "abc": len 3 => 1
    assertEquals(tokens(3).focusIndex, 2)  // "abcd": len 4 => 2
    assertEquals(tokens(4).focusIndex, 2)  // "abcde": len 5 => 2
    assertEquals(tokens(5).focusIndex, 3)  // "abcdef": len 6 => 3
    assertEquals(tokens(6).focusIndex, 3)  // "abcdefghi": len 9 => 3
    assertEquals(tokens(7).focusIndex, 4)  // "abcdefghij": len 10 => 4

  test("tokenize tracks sentence indices"):
    val tokens = Tokenizer.tokenize("First sentence. Second sentence.")
    assertEquals(tokens(0).sentenceIndex, 0)
    assertEquals(tokens(1).sentenceIndex, 0)
    assertEquals(tokens(2).sentenceIndex, 1)
    assertEquals(tokens(3).sentenceIndex, 1)

  test("tokenize tracks paragraph indices"):
    val tokens = Tokenizer.tokenize("Para one.\n\nPara two.")
    assertEquals(tokens(0).paragraphIndex, 0)
    assertEquals(tokens(1).paragraphIndex, 0)
    assertEquals(tokens(2).paragraphIndex, 1)
    assertEquals(tokens(3).paragraphIndex, 1)

  test("tokenize handles empty input"):
    val tokens = Tokenizer.tokenize("")
    assertEquals(tokens.length, 0)

  test("tokenize handles multiple spaces"):
    val tokens = Tokenizer.tokenize("hello    world")
    assertEquals(tokens.length, 2)

  test("tokenize handles multiple newlines as single paragraph break"):
    val tokens = Tokenizer.tokenize("one\n\n\n\ntwo")
    assertEquals(tokens.length, 2)
    assertEquals(tokens(0).paragraphIndex, 0)
    assertEquals(tokens(1).paragraphIndex, 1)
