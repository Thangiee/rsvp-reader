package rsvpreader

import kyo.*
import munit.FunSuite
import munit.ScalaCheckSuite
import org.scalacheck.Gen
import org.scalacheck.Prop.*

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
    // length 1-3 => 0, 4-5 => 1, 6-9 => 2, 10+ => 3
    assertEquals(tokens(0).focusIndex, 0)  // "a": len 1 => 0
    assertEquals(tokens(1).focusIndex, 0)  // "ab": len 2 => 0
    assertEquals(tokens(2).focusIndex, 0)  // "abc": len 3 => 0
    assertEquals(tokens(3).focusIndex, 1)  // "abcd": len 4 => 1
    assertEquals(tokens(4).focusIndex, 1)  // "abcde": len 5 => 1
    assertEquals(tokens(5).focusIndex, 2)  // "abcdef": len 6 => 2
    assertEquals(tokens(6).focusIndex, 2)  // "abcdefghi": len 9 => 2
    assertEquals(tokens(7).focusIndex, 3)  // "abcdefghij": len 10 => 3

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

  test("tokenize strips citation brackets and detects sentence boundary"):
    val tokens = Tokenizer.tokenize("feedback.[4] Claude,")
    assertEquals(tokens(0).text, "feedback")
    assertEquals(tokens(0).punctuation, Punctuation.Period("."))
    assertEquals(tokens(0).sentenceIndex, 0)
    assertEquals(tokens(1).text, "Claude")
    assertEquals(tokens(1).punctuation, Punctuation.Comma(","))
    assertEquals(tokens(1).sentenceIndex, 1)

  test("tokenize strips multiple citation brackets"):
    val tokens = Tokenizer.tokenize("rights.[5][4]")
    assertEquals(tokens(0).text, "rights")
    assertEquals(tokens(0).punctuation, Punctuation.Period("."))

  test("tokenize strips citation without punctuation"):
    val tokens = Tokenizer.tokenize("model[4] next")
    assertEquals(tokens(0).text, "model")
    assertEquals(tokens(0).punctuation, Punctuation.None)

  test("tokenize strips trailing close-wrappers"):
    val tokens = Tokenizer.tokenize("world.\")")
    assertEquals(tokens(0).text, "world")
    assertEquals(tokens(0).punctuation, Punctuation.Period("."))

  test("tokenize focus index uses cleaned word length"):
    val tokens = Tokenizer.tokenize("feedback.[4]")
    // "feedback" is 8 chars => focusIndex 2
    assertEquals(tokens(0).focusIndex, 2)

  test("tokenize handles multiple newlines as single paragraph break"):
    val tokens = Tokenizer.tokenize("one\n\n\n\ntwo")
    assertEquals(tokens.length, 2)
    assertEquals(tokens(0).paragraphIndex, 0)
    assertEquals(tokens(1).paragraphIndex, 1)

  // ---------------------------------------------------------------------------
  // Large text tests — spot-check key tokens + global invariants
  // ---------------------------------------------------------------------------

  private def assertGlobalInvariants(tokens: Span[Token], clue: String = ""): Unit =
    val prefix = if clue.nonEmpty then s"[$clue] " else ""
    var i = 0
    while i < tokens.length do
      val t = tokens(i)
      // No empty text
      assert(t.text.nonEmpty, s"${prefix}token $i has empty text")
      // Focus index in bounds
      assert(t.focusIndex >= 0 && t.focusIndex < t.text.length,
        s"${prefix}token $i '${t.text}' focusIndex ${t.focusIndex} out of bounds [0, ${t.text.length})")
      // Monotonic sentence/paragraph indices
      if i > 0 then
        val prev = tokens(i - 1)
        assert(t.sentenceIndex >= prev.sentenceIndex,
          s"${prefix}sentence index decreased from ${prev.sentenceIndex} to ${t.sentenceIndex} at '${t.text}'")
        assert(t.paragraphIndex >= prev.paragraphIndex,
          s"${prefix}paragraph index decreased from ${prev.paragraphIndex} to ${t.paragraphIndex} at '${t.text}'")
      i += 1

  test("large text: literary prose (Moby Dick ~55 words)"):
    val text =
      """Call me Ishmael. Some years ago -- never mind how long precisely -- having little or no money in my purse, and nothing particular to interest me on shore, I thought I would sail about a little and see the watery part of the world. It is a way I have of driving off the spleen and regulating the circulation.""".stripMargin

    val tokens = Tokenizer.tokenize(text)

    // Total counts (60 whitespace-separated segments)
    assertEquals(tokens.length, 60)
    // Sentences: "Call me Ishmael." (0), "Some ... world." (1), "It ... circulation." (2)
    assertEquals(tokens(tokens.length - 1).sentenceIndex, 2)
    // Single paragraph
    assert(tokens.forall(_.paragraphIndex == 0), "expected single paragraph")

    // Spot checks
    // First token
    assertEquals(tokens(0).text, "Call")
    assertEquals(tokens(0).punctuation, Punctuation.None)
    assertEquals(tokens(0).sentenceIndex, 0)
    // "Ishmael" ends sentence 0 (index 2)
    assertEquals(tokens(2).text, "Ishmael")
    assertEquals(tokens(2).punctuation, Punctuation.Period("."))
    assertEquals(tokens(2).sentenceIndex, 0)
    // "Some" starts sentence 1 (index 3)
    assertEquals(tokens(3).text, "Some")
    assertEquals(tokens(3).sentenceIndex, 1)
    // Em dash tokens (index 6)
    assertEquals(tokens(6).text, "--")
    assertEquals(tokens(6).punctuation, Punctuation.None)
    // "purse" has comma (index 20)
    assertEquals(tokens(20).text, "purse")
    assertEquals(tokens(20).punctuation, Punctuation.Comma(","))
    // "shore" has comma (index 28)
    assertEquals(tokens(28).text, "shore")
    assertEquals(tokens(28).punctuation, Punctuation.Comma(","))
    // "world" ends sentence 1 (index 44)
    assertEquals(tokens(44).text, "world")
    assertEquals(tokens(44).punctuation, Punctuation.Period("."))
    assertEquals(tokens(44).sentenceIndex, 1)
    // "It" starts sentence 2 (index 45)
    assertEquals(tokens(45).text, "It")
    assertEquals(tokens(45).sentenceIndex, 2)
    // Last token "circulation" ends sentence 2 (index 59)
    assertEquals(tokens(59).text, "circulation")
    assertEquals(tokens(59).punctuation, Punctuation.Period("."))
    assertEquals(tokens(59).sentenceIndex, 2)

    assertGlobalInvariants(tokens, "Moby Dick")

  test("large text: Wikipedia/technical (Apollo 11 ~74 words)"):
    val text =
      """Apollo 11 was the American spaceflight that first landed humans on the Moon.[1] Commander Neil Armstrong and lunar module pilot Buzz Aldrin landed the Apollo Lunar Module Eagle on July 20, 1969, at 20:17 UTC,[2][3] and Armstrong became the first person to step onto the lunar surface six hours and 39 minutes later.[4] Aldrin joined him about nineteen minutes after that; they spent a total of two hours and fifteen minutes outside the spacecraft."""

    val tokens = Tokenizer.tokenize(text)

    // Total counts
    assertEquals(tokens.length, 74)
    // 3 sentences: "Apollo ... Moon." (0), "Commander ... later." (1), "Aldrin ... spacecraft." (2)
    assertEquals(tokens(tokens.length - 1).sentenceIndex, 2)
    // Single paragraph
    assert(tokens.forall(_.paragraphIndex == 0), "expected single paragraph")

    // Spot checks
    // Citation stripped: "Moon.[1]" → text="Moon", Period(".")
    assertEquals(tokens(12).text, "Moon")
    assertEquals(tokens(12).punctuation, Punctuation.Period("."))
    assertEquals(tokens(12).sentenceIndex, 0)
    // "Commander" starts sentence 1
    assertEquals(tokens(13).text, "Commander")
    assertEquals(tokens(13).sentenceIndex, 1)
    // "20," → comma on number
    assertEquals(tokens(30).text, "20")
    assertEquals(tokens(30).punctuation, Punctuation.Comma(","))
    // "1969," → comma on year
    assertEquals(tokens(31).text, "1969")
    assertEquals(tokens(31).punctuation, Punctuation.Comma(","))
    // "20:17" → colon inside word, last char is '7', no punct extracted
    assertEquals(tokens(33).text, "20:17")
    assertEquals(tokens(33).punctuation, Punctuation.None)
    // "UTC,[2][3]" → stripped to "UTC," → Comma
    assertEquals(tokens(34).text, "UTC")
    assertEquals(tokens(34).punctuation, Punctuation.Comma(","))
    // "later.[4]" → stripped to "later." → Period, ends sentence 1
    assertEquals(tokens(52).text, "later")
    assertEquals(tokens(52).punctuation, Punctuation.Period("."))
    assertEquals(tokens(52).sentenceIndex, 1)
    // "Aldrin" starts sentence 2
    assertEquals(tokens(53).text, "Aldrin")
    assertEquals(tokens(53).sentenceIndex, 2)
    // "that;" → semicolon as Comma
    assertEquals(tokens(60).text, "that")
    assertEquals(tokens(60).punctuation, Punctuation.Comma(";"))
    // Last token
    assertEquals(tokens(73).text, "spacecraft")
    assertEquals(tokens(73).punctuation, Punctuation.Period("."))

    assertGlobalInvariants(tokens, "Apollo 11")

  test("large text: dialogue (~25 words, 3 paragraphs)"):
    val text =
      "\"Don't you think we should leave?\" asked Mary.\n\n" +
      "\"Of course not!\" John laughed. \"It's only just begun.\"\n\n" +
      "She sighed. \"Fine, but we leave at midnight.\""

    val tokens = Tokenizer.tokenize(text)

    // Total counts
    assertEquals(tokens.length, 25)
    // 3 paragraphs
    assertEquals(tokens(0).paragraphIndex, 0)
    assertEquals(tokens(8).paragraphIndex, 1)
    assertEquals(tokens(17).paragraphIndex, 2)

    // Spot checks
    // Contraction preserved with leading quote
    assertEquals(tokens(0).text, "\"Don't")
    assertEquals(tokens(0).punctuation, Punctuation.None)
    // "leave?" with close-wrapper stripped: leave + Period("?")
    assertEquals(tokens(5).text, "leave")
    assertEquals(tokens(5).punctuation, Punctuation.Period("?"))
    assertEquals(tokens(5).sentenceIndex, 0)
    // "Mary." ends sentence
    assertEquals(tokens(7).text, "Mary")
    assertEquals(tokens(7).punctuation, Punctuation.Period("."))
    // "not!" with close-wrapper stripped
    assertEquals(tokens(10).text, "not")
    assertEquals(tokens(10).punctuation, Punctuation.Period("!"))
    // "begun." with close-wrapper stripped
    assertEquals(tokens(16).text, "begun")
    assertEquals(tokens(16).punctuation, Punctuation.Period("."))
    assertEquals(tokens(16).paragraphIndex, 1)
    // "Fine," keeps leading quote, comma extracted
    assertEquals(tokens(19).text, "\"Fine")
    assertEquals(tokens(19).punctuation, Punctuation.Comma(","))
    // Last token: "midnight." with close-wrapper stripped
    assertEquals(tokens(24).text, "midnight")
    assertEquals(tokens(24).punctuation, Punctuation.Period("."))
    assertEquals(tokens(24).paragraphIndex, 2)

    assertGlobalInvariants(tokens, "dialogue")

  test("large text: Douglass essay (2700+ words, 18 paragraphs)"):
    val text = scala.io.Source.fromResource("Douglass_essay.txt").mkString
    val tokens = Tokenizer.tokenize(text)

    // Total counts
    assertEquals(tokens.length, 2703)
    assertEquals(tokens(tokens.length - 1).sentenceIndex, 114)
    assertEquals(tokens(tokens.length - 1).paragraphIndex, 17)

    // First token
    assertEquals(tokens(0).text, "THE")
    assertEquals(tokens(0).punctuation, Punctuation.None)
    assertEquals(tokens(0).sentenceIndex, 0)
    assertEquals(tokens(0).paragraphIndex, 0)

    // Last token of paragraph 0: "reconstruction."
    assertEquals(tokens(28).text, "reconstruction")
    assertEquals(tokens(28).punctuation, Punctuation.Period("."))
    assertEquals(tokens(28).sentenceIndex, 0)
    assertEquals(tokens(28).paragraphIndex, 0)

    // First token of paragraph 1: sentence index jumps by 2 (Period + para boundary)
    assertEquals(tokens(29).text, "Seldom")
    assertEquals(tokens(29).sentenceIndex, 2)
    assertEquals(tokens(29).paragraphIndex, 1)

    // Em-dash embedded in word — no punctuation extracted
    assertEquals(tokens(108).text, "results,\u2014a")
    assertEquals(tokens(108).punctuation, Punctuation.None)

    // Semicolon → Comma
    assertEquals(tokens(196).text, "them")
    assertEquals(tokens(196).punctuation, Punctuation.Comma(";"))

    // Question mark → Period
    assertEquals(tokens(974).text, "prosperity")
    assertEquals(tokens(974).punctuation, Punctuation.Period("?"))

    // Second question mark
    assertEquals(tokens(991).text, "end")
    assertEquals(tokens(991).punctuation, Punctuation.Period("?"))

    // Last token: "States."
    assertEquals(tokens(2702).text, "States")
    assertEquals(tokens(2702).punctuation, Punctuation.Period("."))
    assertEquals(tokens(2702).paragraphIndex, 17)

    // Paragraph boundary spot checks
    assertEquals(tokens(487).text, "Slavery")
    assertEquals(tokens(487).paragraphIndex, 4)
    assertEquals(tokens(683).text, "One")
    assertEquals(tokens(683).paragraphIndex, 5)
    assertEquals(tokens(2524).text, "Fortunately")
    assertEquals(tokens(2524).paragraphIndex, 17)
    assertEquals(tokens(2524).sentenceIndex, 109)

    assertGlobalInvariants(tokens, "Douglass essay")

class TokenizerPropertySuite extends ScalaCheckSuite:

  private val genWord: Gen[String] =
    Gen.chooseNum(1, 15).flatMap(n => Gen.listOfN(n, Gen.alphaChar).map(_.mkString))

  private val genPunctuatedWord: Gen[String] =
    for
      word  <- genWord
      punct <- Gen.oneOf(Gen.const(""), Gen.oneOf(".", "!", "?", ",", ";", ":"))
    yield word + punct

  private val genSentence: Gen[String] =
    for
      n     <- Gen.chooseNum(3, 15)
      words <- Gen.listOfN(n, genWord)
    yield words.mkString(" ") + "."

  private val genParagraph: Gen[String] =
    for
      n         <- Gen.chooseNum(1, 5)
      sentences <- Gen.listOfN(n, genSentence)
    yield sentences.mkString(" ")

  private val genText: Gen[String] =
    for
      n    <- Gen.chooseNum(1, 6)
      paras <- Gen.listOfN(n, genParagraph)
    yield paras.mkString("\n\n")

  property("no empty tokens"):
    forAll(genText) { text =>
      val tokens = Tokenizer.tokenize(text)
      tokens.forall(_.text.nonEmpty)
    }

  property("focus index in bounds"):
    forAll(genText) { text =>
      val tokens = Tokenizer.tokenize(text)
      tokens.forall(t => t.focusIndex >= 0 && t.focusIndex < t.text.length)
    }

  property("sentence indices monotonically non-decreasing"):
    forAll(genText) { text =>
      val tokens = Tokenizer.tokenize(text)
      var ok = true
      var i = 1
      while i < tokens.length do
        if tokens(i).sentenceIndex < tokens(i - 1).sentenceIndex then ok = false
        i += 1
      ok
    }

  property("paragraph indices monotonically non-decreasing"):
    forAll(genText) { text =>
      val tokens = Tokenizer.tokenize(text)
      var ok = true
      var i = 1
      while i < tokens.length do
        if tokens(i).paragraphIndex < tokens(i - 1).paragraphIndex then ok = false
        i += 1
      ok
    }

  property("paragraph count matches input paragraph blocks"):
    forAll(genText) { text =>
      val tokens = Tokenizer.tokenize(text)
      val expectedParas = text.split("\n\n+").length
      val actualParas = tokens(tokens.length - 1).paragraphIndex + 1
      expectedParas == actualParas
    }

  property("token count matches whitespace-split segment count"):
    forAll(genText) { text =>
      val tokens = Tokenizer.tokenize(text)
      val expectedCount = text.split("\n\n+").flatMap(_.split("\\s+").filter(_.nonEmpty)).length
      tokens.length == expectedCount
    }

  property("period chars yield Period punctuation"):
    forAll(genText) { text =>
      val tokens = Tokenizer.tokenize(text)
      tokens.forall { t =>
        t.punctuation match
          case Punctuation.Period(c) => ".!?".contains(c)
          case _                     => true
      }
    }

  property("comma chars yield Comma punctuation"):
    forAll(genText) { text =>
      val tokens = Tokenizer.tokenize(text)
      tokens.forall { t =>
        t.punctuation match
          case Punctuation.Comma(c) => ",;:".contains(c)
          case _                    => true
      }
    }
