package rsvpreader

import kyo.*
import munit.FunSuite

class RsvpConfigSuite extends FunSuite:

  test("RsvpConfig has sensible defaults"):
    val config = RsvpConfig()
    assertEquals(config.baseWpm, 300)
    assertEquals(config.startDelay, 500.millis)
    assertEquals(config.commaDelay, 150.millis)
    assertEquals(config.periodDelay, 300.millis)
    assertEquals(config.paragraphDelay, 500.millis)
    assertEquals(config.paragraphAutoPause, false)
    assertEquals(config.wordLengthEnabled, true)
    assertEquals(config.wordLengthBaseline, 5)
    assertEquals(config.wordLengthFactor, 0.1)
    assertEquals(config.orpEnabled, true)
    assertEquals(config.trailWordCount, 5)

  test("RsvpConfig can be customized"):
    val config = RsvpConfig(
      baseWpm = 500,
      startDelay = Duration.Zero,
      commaDelay = Duration.Zero,
      wordLengthEnabled = false
    )
    assertEquals(config.baseWpm, 500)
    assertEquals(config.startDelay, Duration.Zero)
    assertEquals(config.commaDelay, Duration.Zero)
    assertEquals(config.wordLengthEnabled, false)
    // Others remain default
    assertEquals(config.periodDelay, 300.millis)
