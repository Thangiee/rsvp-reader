package rsvpreader.playback

import kyo.*
import rsvpreader.token.*
import rsvpreader.config.*
import munit.FunSuite

class DelaySuite extends FunSuite:

  val baseConfig = RsvpConfig(
    baseWpm = 300,
    commaDelay = 150.millis,
    periodDelay = 300.millis,
    paragraphDelay = 500.millis,
    wordLengthEnabled = true,
    wordLengthBaseline = 5,
    wordLengthFactor = 0.1
  )

  // At 300 WPM, base delay = 60000 / 300 = 200ms
  val baseDelay = 200.millis

  test("calculateDelay returns base delay for short word with no punctuation"):
    val token = Token("cat", 0, Punctuation.None, 0, 0)
    val delay = calculateDelay(token, baseConfig)
    assertEquals(delay, baseDelay)

  test("calculateDelay adds comma delay"):
    val token = Token("hello", 1, Punctuation.Comma(","), 0, 0)
    val delay = calculateDelay(token, baseConfig)
    assertEquals(delay, baseDelay + 150.millis)

  test("calculateDelay adds period delay"):
    val token = Token("hello", 1, Punctuation.Period("."), 0, 0)
    val delay = calculateDelay(token, baseConfig)
    assertEquals(delay, baseDelay + 300.millis)

  test("calculateDelay adds period + paragraph delay"):
    val token = Token("hello", 1, Punctuation.Paragraph, 0, 0)
    val delay = calculateDelay(token, baseConfig)
    assertEquals(delay, baseDelay + 300.millis + 500.millis)

  test("calculateDelay adds length bonus for long words"):
    // Word "beautiful" has 9 chars, baseline is 5
    // Extra chars = 9 - 5 = 4
    // Bonus = base * 4 * 0.1 = 200 * 0.4 = 80ms
    val token = Token("beautiful", 2, Punctuation.None, 0, 0)
    val delay = calculateDelay(token, baseConfig)
    assertEquals(delay, baseDelay + 80.millis)

  test("calculateDelay skips length bonus when disabled"):
    val config = baseConfig.copy(wordLengthEnabled = false)
    val token = Token("beautiful", 2, Punctuation.None, 0, 0)
    val delay = calculateDelay(token, config)
    assertEquals(delay, baseDelay)

  test("calculateDelay adjusts for different WPM"):
    // At 600 WPM, base delay = 60000 / 600 = 100ms
    val config = baseConfig.copy(baseWpm = 600)
    val token = Token("cat", 0, Punctuation.None, 0, 0)
    val delay = calculateDelay(token, config)
    assertEquals(delay, 100.millis)

  test("calculateDelay combines all factors"):
    // Long word with punctuation
    // Base: 200ms, comma: 150ms, length bonus for 7-char word: 200 * 2 * 0.1 = 40ms
    val token = Token("example", 2, Punctuation.Comma(","), 0, 0)
    val delay = calculateDelay(token, baseConfig)
    assertEquals(delay, 200.millis + 40.millis + 150.millis)
